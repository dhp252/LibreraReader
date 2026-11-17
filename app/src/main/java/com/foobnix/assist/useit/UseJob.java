package com.foobnix.assist.useit;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The one request of "Use it", sent only when the reader taps "Design my experiment"
 * (Assist.USE_IT on): the chapter at the reader's page as its stored key-passage ideas, or
 * without them its text (40k characters at most), plus the reader's answer; back comes one
 * experiment ({@link UsePrompts}). A warning's quote keeps its quote marks only when
 * {@link QuoteMatcher} finds it in what was sent. A screen job: it stops with its screen.
 */
final class UseJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        /** The chapter the experiment is about, as soon as it is known. */
        void onChapter(String key, String title);

        void onDraft(UseExperiment draft);

        void onError(String message);

        void onNeedSettings();
    }

    private final String author;
    private final String answer;
    private final Screen screen;

    UseJob(Activity activity, AiJobs.Target target, String author, String answer, Screen screen) {
        super(activity, target);
        this.author = author;
        this.answer = answer;
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
        // what the warning's quote is looked for in: the book's words that were sent
        final List<String> words = new ArrayList<>();
        final AiStore.Book data = AiStore.get().load(target.name);
        // the chapter is known without reading the book when it was read before in this layout
        BookChapters.Range at = AiJobs.knownChapter(target);
        AiStore.Chapter stored = at == null ? null : data.chapter(at.key);
        String read = null;
        if (stored == null || stored.ideas.isEmpty()) {
            try (BookText text = open()) {
                at = BookChapters.chapterAt(AiJobs.toc(target, text), target.page, text.pageCount(),
                        BookChapters.BLOCK_PAGES);
                stored = at == null ? null : data.chapter(at.key);
                if (at != null && (stored == null || stored.ideas.isEmpty())) {
                    read = text(text, at, words);
                }
            }
        }
        if (at == null) {
            failWith(string(R.string.ai_cannot_open));
            return;
        }
        checkCancelled();
        final BookChapters.Range range = at;
        final String title = chapterTitle(range);
        ui(() -> screen.onChapter(range.key, title));

        final boolean ideas = read == null;
        final String material;
        if (ideas) {
            material = UsePrompts.ideas(lines(stored, words));
        } else {
            material = read;
            if (material.length() < UsePrompts.MIN_CHARS) {
                failWith(string(R.string.ai_no_text));
                return;
            }
        }
        final String system = AiPrompts.withMaster(settings.masterPrompt, UsePrompts.system(settings.answerLanguage));
        final String reply = ask(system, UsePrompts.user(target.title, author, title, material, ideas, answer), null);
        checkCancelled();
        final UseExperiment draft = UsePrompts.parse(reply);
        if (draft == null) {
            failWith(string(R.string.canvas_t23_unreadable));
            return;
        }
        draft.chapterKey = range.key;
        draft.chapterTitle = title;
        draft.bookTitle = target.title;
        draft.author = author == null ? "" : author;
        draft.answer = answer;
        UsePrompts.settleQuote(draft, find(words, draft.quote));
        ui(() -> screen.onDraft(draft));
    }

    /** The chapter's ideas with the words of their marks; the marks' words also go into {@code words}. */
    private static List<UsePrompts.IdeaLine> lines(AiStore.Chapter chapter, List<String> words) {
        final List<UsePrompts.IdeaLine> out = new ArrayList<>();
        for (AiStore.Idea idea : chapter.ideas) {
            final List<String> quotes = new ArrayList<>();
            for (List<AiStore.Quote> list : Arrays.asList(chapter.quotes, chapter.paragraphs)) {
                for (AiStore.Quote q : list) {
                    if (q.idea == idea.id && q.text != null) {
                        quotes.add(q.text);
                    }
                }
            }
            out.add(new UsePrompts.IdeaLine(idea.category == null ? "main" : idea.category.code, idea.text, quotes));
        }
        for (List<AiStore.Quote> list : Arrays.asList(chapter.quotes, chapter.paragraphs)) {
            for (AiStore.Quote q : list) {
                if (q.text != null) {
                    words.addAll(Arrays.asList(q.text.trim().split("\\s+")));
                }
            }
        }
        return out;
    }

    /** The chapter's text, up to the cap; its words go into {@code words}. */
    private String text(BookText text, BookChapters.Range range, List<String> words) throws Exception {
        final StringBuilder plain = new StringBuilder();
        final int max = Math.min(UsePrompts.MAX_CHARS, Math.max(UsePrompts.MIN_CHARS, settings.maxChars - 4000));
        final int from = Math.max(0, range.from);
        final int to = Math.min(text.pageCount(), range.to);
        for (int p = from; p < to && plain.length() < max; p++) {
            checkCancelled();
            final TextWord[][] page = text.body(p);
            words.addAll(BookText.words(page));
            final String s = BookText.plain(page);
            if (!s.isEmpty()) {
                if (plain.length() > 0) {
                    plain.append("\n\n");
                }
                plain.append(s);
            }
        }
        return plain.length() > max ? plain.substring(0, max) : plain.toString();
    }

    /** The quote in the book's own words, or null when it is not in them. */
    private static String find(List<String> words, String quote) {
        if (quote == null || quote.trim().isEmpty() || words.isEmpty()) {
            return null;
        }
        final int[] range = QuoteMatcher.locate(new QuoteMatcher.Words(words), quote);
        return range == null ? null : QuoteMatcher.join(words.subList(range[0], range[1] + 1));
    }
}
