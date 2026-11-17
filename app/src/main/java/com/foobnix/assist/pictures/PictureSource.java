package com.foobnix.assist.pictures;

import com.foobnix.ai.QuoteMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The text one picture request sent, as the answer is checked against it: the book's own words
 * (a chapter's pages, the marks of its key ideas, or a selected passage) where a picture's quote
 * must be found, and the numbers of everything sent, which are the only numbers a picture may
 * show. Plain Java, no Android.
 */
public final class PictureSource {

    /** A run of the book's words that stand together in the book: a quote never spans two of them. */
    private static final class Run {
        final List<String> words;
        final List<Integer> pages;
        final QuoteMatcher.Words index;

        Run(List<String> words, List<Integer> pages) {
            this.words = words;
            this.pages = pages;
            this.index = new QuoteMatcher.Words(words);
        }
    }

    /** A quote found among the book's words. */
    public static final class Found {
        /** The words as the book has them. */
        public final String text;
        /** 0-based page they start on, or -1. */
        public final int page;

        Found(String text, int page) {
            this.text = text;
            this.page = page;
        }
    }

    private final List<Run> runs = new ArrayList<>();
    private final Set<String> numbers;
    /** The request was made of the chapter's stored ideas and their marks, not of its text. */
    public final boolean fromIdeas;

    private PictureSource(String sent, boolean fromIdeas) {
        this.numbers = PictureNumbers.of(sent);
        this.fromIdeas = fromIdeas;
    }

    /**
     * A chapter's text (or the part of it that was sent).
     *
     * @param words    its words in reading order
     * @param wordPage the 0-based page of each word
     * @param sent     the text as it was sent
     */
    public static PictureSource ofText(List<String> words, List<Integer> wordPage, String sent) {
        final PictureSource s = new PictureSource(sent, false);
        s.runs.add(new Run(words, wordPage));
        return s;
    }

    /** A passage the reader selected, on one page. */
    public static PictureSource ofPassage(String passage, int page) {
        final PictureSource s = new PictureSource(passage, false);
        s.add(passage, page);
        return s;
    }

    /**
     * A chapter's key ideas: each mark is a run of the book's words; the ideas' own lines are sent
     * but are not the book's words.
     *
     * @param sent the ideas and marks as they were sent ({@link PicturePrompts#ideasBody})
     */
    public static PictureSource ofIdeas(List<PictureIdea> ideas, String sent) {
        final PictureSource s = new PictureSource(sent, true);
        for (PictureIdea idea : ideas) {
            for (int i = 0; i < idea.quotes.size(); i++) {
                s.add(idea.quotes.get(i), idea.pages.get(i));
            }
        }
        return s;
    }

    private void add(String text, int page) {
        final List<String> words = new ArrayList<>();
        final List<Integer> pages = new ArrayList<>();
        for (String w : (text == null ? "" : text).trim().split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
                pages.add(page);
            }
        }
        if (!words.isEmpty()) {
            runs.add(new Run(words, pages));
        }
    }

    /** The numbers of the text sent ({@link PictureNumbers#of}). */
    public Set<String> numbers() {
        return numbers;
    }

    /**
     * Where a quote of the model is among the book's words; null when it is not there. A quote the
     * model shortened with "…" is looked for by its pieces, in their order, and the first piece
     * found stands for it.
     */
    public Found locate(String quote) {
        if (quote == null) {
            return null;
        }
        final String whole = strip(quote);
        final Found all = find(whole);
        if (all != null) {
            return all;
        }
        final String[] pieces = whole.split("\\s*(?:…|\\.{3,})\\s*");
        if (pieces.length < 2) {
            return null;
        }
        for (String piece : pieces) {
            final Found part = find(piece);
            if (part != null) {
                return part;
            }
        }
        return null;
    }

    private Found find(String quote) {
        if (QuoteMatcher.key(quote) == null) {
            return null;
        }
        for (Run run : runs) {
            final int[] at = QuoteMatcher.locate(run.index, quote);
            if (at != null) {
                return new Found(QuoteMatcher.join(run.words.subList(at[0], at[1] + 1)), run.pages.get(at[0]));
            }
        }
        return null;
    }

    /** Without the quotation marks a model puts around a quote. */
    static String strip(String quote) {
        String s = quote.trim();
        while (!s.isEmpty() && "\"“”„«»'‘’".indexOf(s.charAt(0)) >= 0) {
            s = s.substring(1).trim();
        }
        while (!s.isEmpty() && "\"“”„«»'‘’".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }
}
