package com.foobnix.assist.fiction;

import com.foobnix.ai.QuoteMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the quotes of a timeline answer against the text that was sent: a quote counts only
 * when {@link QuoteMatcher} finds it in the pages of the block, and then the book's own words
 * and the page they are on are kept. A quote that is not found is no quote: the moment stays,
 * without quote marks.
 */
public final class FictionQuotes {

    /** A quote as the book has it. */
    public static final class Found {
        public final String text;
        /** The page (0-based) its first word is on. */
        public final int page;

        Found(String text, int page) {
            this.text = text;
            this.page = page;
        }
    }

    private final List<String> words = new ArrayList<>();
    private final List<Integer> pages = new ArrayList<>();
    private final QuoteMatcher.Words prepared;

    /**
     * @param plain the text of each page, by page
     * @param from  first page of the block that was sent
     * @param to    first page after it
     */
    public FictionQuotes(String[] plain, int from, int to) {
        for (int p = Math.max(0, from); p < to && p < plain.length; p++) {
            if (plain[p] == null) {
                continue;
            }
            for (String w : plain[p].split("\\s+")) {
                if (!w.isEmpty()) {
                    words.add(w);
                    pages.add(p);
                }
            }
        }
        prepared = new QuoteMatcher.Words(words);
    }

    /** The quote in the book's words with its page, or null when the text does not hold it. */
    public Found find(String quote) {
        if (quote == null || quote.trim().isEmpty() || words.isEmpty()) {
            return null;
        }
        final int[] range = QuoteMatcher.locate(prepared, quote);
        if (range == null) {
            return null;
        }
        return new Found(QuoteMatcher.join(words.subList(range[0], range[1] + 1)), pages.get(range[0]));
    }

    /** Keeps the quotes of an answer that are in the text, as the book has them; clears the others. */
    public void check(FictionTimeline answer) {
        for (FictionTimeline.Moment m : answer.moments) {
            final Found f = find(m.quote);
            m.quote = f == null ? "" : f.text;
            m.page = f == null ? -1 : f.page;
        }
        for (FictionTimeline.Place p : answer.places) {
            final Found f = find(p.quote);
            p.quote = f == null ? "" : f.text;
            p.page = f == null ? -1 : f.page;
        }
    }
}
