package com.foobnix.assist.pictures;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.List;

/**
 * The one request of "Prose to pictures": a chapter's stored key ideas with the book's words that
 * mark them, else its text (whole pages, 40,000 characters at most), or one selected passage.
 * The answer is checked against what was sent ({@link PictureSet#parse}); a chapter's pictures are
 * stored as {@code picture:<chapter key>}, a passage's are only shown. Sent only on the reader's
 * tap with Assist.PICTURES on; a screen job, cancelled with its screen.
 */
final class PictureJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        void onDone(PictureSet set);

        void onError(String message);

        void onNeedSettings();
    }

    /** The chapter, or null for a passage. */
    private final BookChapters.Range range;
    private final String passage;
    private final int passagePage;
    private final Screen screen;

    /** The pictures of a chapter. */
    PictureJob(Activity activity, AiJobs.Target target, BookChapters.Range range, Screen screen) {
        super(activity, target);
        this.range = range;
        this.passage = null;
        this.passagePage = -1;
        this.screen = screen;
    }

    /** The pictures of a selected passage ({@link PicturePrompts#passage}) on a 0-based page. */
    PictureJob(Activity activity, AiJobs.Target target, String passage, int page, Screen screen) {
        super(activity, target);
        this.range = null;
        this.passage = passage;
        this.passagePage = page;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.PICTURES.isOn()) {
            return;
        }
        // an answer an outside Claude wrote for the whole chapter needs no API (com.foobnix.claude)
        final boolean claude = claudeWaiting(com.foobnix.claude.ClaudeFeature.PICTURES, range == null ? null : range.key);
        if (!settings.isReady() && !claude) {
            ui(screen::onNeedSettings);
            return;
        }
        final int max = claude ? Integer.MAX_VALUE
                : Math.min(PicturePrompts.MAX_CHARS, Math.max(PicturePrompts.MIN_CHARS, settings.maxChars - 6000));
        final PicturePrompts.Sent sent;
        final String body;
        final PictureSource source;
        if (range == null) {
            sent = PicturePrompts.Sent.PASSAGE;
            body = passage == null ? "" : passage;
            if (body.length() < PicturePrompts.MIN_PASSAGE) {
                failWith(string(R.string.canvas_t27_too_short));
                return;
            }
            source = PictureSource.ofPassage(body, passagePage);
        } else {
            // Claude answers from the chapter's text, never from its ideas
            final List<PictureIdea> ideas = claude ? new ArrayList<PictureIdea>()
                    : PictureChapter.ideas(AiStore.get().load(target.name).chapter(range.key));
            while (ideas.size() > 1 && PicturePrompts.ideasBody(ideas).length() > max) {
                // the request keeps to the cap: the last ideas wait
                ideas.remove(ideas.size() - 1);
            }
            if (!ideas.isEmpty()) {
                sent = PicturePrompts.Sent.IDEAS;
                body = PicturePrompts.ideasBody(ideas);
                source = PictureSource.ofIdeas(ideas, body);
            } else {
                sent = PicturePrompts.Sent.TEXT;
                final List<String> words = new ArrayList<>();
                final List<Integer> wordPage = new ArrayList<>();
                final StringBuilder plain = new StringBuilder();
                try (BookText text = open()) {
                    final int from = Math.max(0, range.from);
                    final int to = Math.min(text.pageCount(), range.to);
                    for (int p = from; p < to; p++) {
                        checkCancelled();
                        final TextWord[][] page = text.body(p);
                        final String s = BookText.plain(page);
                        if (plain.length() > 0 && plain.length() + s.length() + 2 > max) {
                            // whole pages only, so every word a quote is looked for among was sent
                            break;
                        }
                        for (String w : BookText.words(page)) {
                            words.add(w);
                            wordPage.add(p);
                        }
                        if (!s.isEmpty()) {
                            if (plain.length() > 0) {
                                plain.append("\n\n");
                            }
                            plain.append(s);
                        }
                    }
                }
                if (plain.length() < PicturePrompts.MIN_CHARS) {
                    failWith(string(R.string.canvas_t27_too_short));
                    return;
                }
                if (plain.length() > max) {
                    // one page longer than the cap
                    plain.setLength(max);
                }
                body = plain.toString();
                source = PictureSource.ofText(words, wordPage, body);
            }
        }
        final String system = AiPrompts.withMaster(settings.masterPrompt, PicturePrompts.system(settings.answerLanguage, sent));
        final String answer = askFor(com.foobnix.claude.ClaudeFeature.PICTURES, range == null ? null : range.key, system,
                PicturePrompts.user(target.title, range == null ? null : range.title, sent, body), null);
        checkCancelled();
        final PictureSet set = PictureSet.parse(answer, source);
        if (set == null) {
            failWith(string(R.string.canvas_t27_unreadable));
            return;
        }
        // the chapter's title as the book has it
        set.title = range == null || range.title == null ? "" : range.title.trim();
        set.at = System.currentTimeMillis();
        set.model = settings.model;
        if (range != null) {
            try {
                PictureChapter.save(target.name, range.key, set);
            } catch (Exception e) {
                LOG.e(e);
                toast(string(R.string.canvas_t27_not_saved, String.valueOf(e.getMessage())));
            }
        }
        ui(() -> screen.onDone(set));
    }
}
