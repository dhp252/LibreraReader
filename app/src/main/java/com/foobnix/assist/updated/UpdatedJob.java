package com.foobnix.assist.updated;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.List;

/**
 * The one request of "The book, updated" for a chapter: its text (40,000 characters at most) with
 * the book's title, author and year, answered with notes and the strongest objection
 * ({@link UpdatedPrompts}). Only notes whose sentence is found in the chapter are kept; the result
 * is stored as {@code updated:<chapter key>}. Sent only on the reader's tap (Assist.UPDATED on); a
 * screen job, cancelled with its screen.
 */
final class UpdatedJob extends AiJobs.Job {

    /** Characters of a chapter sent at most: notes attach to the part sent. */
    static final int MAX_CHARS = 40_000;
    /** Less text than this is not worth a request. */
    static final int MIN_CHARS = 600;

    interface Screen {
        void onDone(UpdatedChapter chapter);

        void onError(String message);

        void onNeedSettings();
    }

    private final BookChapters.Range range;
    private final String layout;
    private final Screen screen;

    UpdatedJob(Activity activity, AiJobs.Target target, BookChapters.Range range, String layout, Screen screen) {
        super(activity, target);
        this.range = range;
        this.layout = layout;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final List<String> words = new ArrayList<>();
        final List<Integer> wordPage = new ArrayList<>();
        final StringBuilder plain = new StringBuilder();
        final int max = Math.min(MAX_CHARS, Math.max(MIN_CHARS, settings.maxChars - 6000));
        try (BookText text = open()) {
            final int from = Math.max(0, range.from);
            final int to = Math.min(text.pageCount(), range.to);
            for (int p = from; p < to && plain.length() < max; p++) {
                checkCancelled();
                final TextWord[][] page = text.body(p);
                for (String w : BookText.words(page)) {
                    words.add(w);
                    wordPage.add(p);
                }
                final String s = BookText.plain(page);
                if (!s.isEmpty()) {
                    if (plain.length() > 0) {
                        plain.append("\n\n");
                    }
                    plain.append(s);
                }
            }
        }
        if (plain.length() < MIN_CHARS) {
            failWith(string(R.string.updated_too_short));
            return;
        }
        if (plain.length() > max) {
            // the last page read ran over: the request keeps to the cap
            plain.setLength(max);
        }
        String author = "";
        int year = 0;
        try {
            final com.foobnix.dao2.FileMeta meta = com.foobnix.ui2.AppDB.get().load(target.path);
            if (meta != null) {
                author = meta.getAuthor() == null ? "" : meta.getAuthor().trim();
                final Integer y = meta.getYear();
                year = y == null ? 0 : y;
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        final List<String> lens = UpdatedSession.lensFields();
        final String system = AiPrompts.withMaster(settings.masterPrompt, UpdatedPrompts.system(settings.answerLanguage, lens));
        final String answer = ask(system, UpdatedPrompts.user(target.title, author, year, chapterTitle(range), plain.toString()), null);
        checkCancelled();
        final UpdatedChapter chapter = UpdatedChapter.parse(answer);
        if (chapter == null) {
            failWith(string(R.string.updated_unreadable));
            return;
        }
        chapter.place(words, wordPage);
        if (lens.isEmpty()) {
            // no lens was sent: nothing may pass as "in your terms"
            for (UpdatedNote n : chapter.notes) {
                n.terms = "";
            }
        }
        chapter.title = range.title == null ? "" : range.title.trim();
        chapter.layout = layout;
        chapter.at = System.currentTimeMillis();
        chapter.model = settings.model;
        chapter.author = author;
        try {
            AssistStore.get().put(target.name, UpdatedChapter.key(range.key), chapter.toJson());
        } catch (Exception e) {
            LOG.e(e);
            toast(string(R.string.updated_not_saved, String.valueOf(e.getMessage())));
        }
        ui(() -> screen.onDone(chapter));
    }
}
