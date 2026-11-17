package com.foobnix.assist.pictures;

import java.util.ArrayList;
import java.util.List;

/**
 * One stored key idea of a chapter as a picture request sends it: its line, its kind, and the
 * book's words that mark it (cut to what is sent) with their pages. Plain Java, no Android.
 */
public final class PictureIdea {

    /** Marks sent per idea at most. */
    public static final int MAX_QUOTES = 3;
    /** Characters of one mark sent at most. */
    public static final int QUOTE_CHARS = 600;

    public final String text;
    /** The idea's kind as key passages name it ("main", "evidence", "example"...), or empty. */
    public final String kind;
    public final List<String> quotes = new ArrayList<>();
    /** The 0-based page of each quote. */
    public final List<Integer> pages = new ArrayList<>();

    public PictureIdea(String text, String kind) {
        this.text = PictureText.oneLine(text);
        this.kind = kind == null ? "" : kind;
    }

    /** Adds a mark's words (whole words, {@link #QUOTE_CHARS} at most); ignored past {@link #MAX_QUOTES}. */
    public void add(String quote, int page) {
        final String q = PictureText.cutWords(PictureText.oneLine(quote), QUOTE_CHARS);
        if (!q.isEmpty() && quotes.size() < MAX_QUOTES) {
            quotes.add(q);
            pages.add(page);
        }
    }

    /** The characters of the book's own words this idea carries. */
    public int markChars() {
        int n = 0;
        for (String q : quotes) {
            n += q.length();
        }
        return n;
    }
}
