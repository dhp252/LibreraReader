package com.foobnix.assist.recall;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A chapter as blank-page recall and teach-back see it: its key ideas (the stored key passages
 * when it has them, else the ideas an earlier request found), its text for the request that has
 * none, and its item in the assist store ({@code recall:<chapter key>}).
 */
public final class RecallChapter {

    private RecallChapter() {
    }

    /** The chapter's text as a request and the quote matcher need it. */
    public static final class Text {
        public final List<String> words = new ArrayList<>();
        /** The 0-based page of each word. */
        public final List<Integer> wordPage = new ArrayList<>();
        public final StringBuilder plain = new StringBuilder();
    }

    /**
     * The key ideas of the open book's chapter from its stored key passages (F-004), each with its
     * marks' words in page order; empty when the chapter has none. Reads memory only.
     */
    public static List<Recall.Idea> fromPassages(String bookName, String chapterKey) {
        final List<Recall.Idea> all = new ArrayList<>();
        final AiHighlights highlights = AiHighlights.get();
        if (bookName == null || !bookName.equals(highlights.getBook()) || highlights.data() == null) {
            return all;
        }
        final AiStore.Chapter chapter = highlights.data().chapter(chapterKey);
        if (chapter == null) {
            return all;
        }
        for (AiStore.Idea idea : chapter.ideas) {
            if (idea.text == null || idea.text.trim().isEmpty()) {
                continue;
            }
            final Recall.Idea i = new Recall.Idea(idea.id, idea.text);
            i.parent = idea.parent;
            final List<AiStore.Quote> marks = new ArrayList<>();
            for (List<AiStore.Quote> list : Arrays.asList(chapter.quotes, chapter.paragraphs)) {
                for (AiStore.Quote q : list) {
                    if (q.idea == idea.id && q.text != null) {
                        marks.add(q);
                    }
                }
            }
            java.util.Collections.sort(marks, (a, b) -> Integer.compare(a.page, b.page));
            for (AiStore.Quote q : marks) {
                i.add(q.text, q.page);
            }
            if (i.quotes.isEmpty() && idea.page >= 0) {
                // no mark found in the book, but the page it was picked on
                i.quotes.add("");
                i.pages.add(idea.page);
            }
            all.add(i);
        }
        return Recall.keyIdeas(all);
    }

    /**
     * The ideas to check a chapter against: its key passages, else the ideas kept in its recall
     * item (from an earlier check or nudge, also of key passages removed since), else none (the
     * request then reads the chapter's text).
     */
    public static List<Recall.Idea> ideas(String bookName, String chapterKey, Recall.Attempt stored) {
        final List<Recall.Idea> passages = fromPassages(bookName, chapterKey);
        if (!passages.isEmpty()) {
            return passages;
        }
        final List<Recall.Idea> own = new ArrayList<>();
        if (stored != null) {
            for (Recall.Idea i : stored.ideas) {
                final Recall.Idea copy = i.copy();
                copy.state = "";
                own.add(copy);
            }
        }
        return own;
    }

    /** The chapter's recall item, or null. Off the main thread. */
    public static Recall.Attempt load(String bookName, String chapterKey) {
        try {
            return Recall.Attempt.fromJson(AssistStore.get().item(bookName, Recall.key(chapterKey)));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    /** Off the main thread. */
    public static void save(String bookName, String chapterKey, Recall.Attempt attempt) throws IOException {
        try {
            AssistStore.get().put(bookName, Recall.key(chapterKey), attempt.toJson());
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** The chapter's text, {@code maxChars} at most (whole pages). Off the main thread. */
    public static Text read(BookText text, BookChapters.Range range, int maxChars) {
        final Text out = new Text();
        final int from = Math.max(0, range.from);
        final int to = Math.min(text.pageCount(), range.to);
        for (int p = from; p < to && out.plain.length() < maxChars; p++) {
            final TextWord[][] page = text.body(p);
            for (String w : BookText.words(page)) {
                out.words.add(w);
                out.wordPage.add(p);
            }
            final String s = BookText.plain(page);
            if (!s.isEmpty()) {
                if (out.plain.length() > 0) {
                    out.plain.append("\n\n");
                }
                out.plain.append(s);
            }
        }
        return out;
    }
}
