package com.foobnix.assist.zoom;

import com.foobnix.ai.QuoteMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * A chapter's text as Semantic zoom reads it: its paragraphs in order (numbered from 1, the "¶"
 * of the canvas), its words, and for each word the paragraph and the page it is on. Quotes of the
 * model are found in it with {@link QuoteMatcher}, so a claim or a condensed sentence can name its
 * paragraph and show the author's own words. No Android here.
 */
public final class ZoomText {

    /** Characters of a passage shown at most; a longer one ends with "…". */
    static final int PASSAGE_CHARS = 1600;
    /** Letters and digits the stored beginning of a passage has at least, so it is found again. */
    static final int HEAD_LETTERS = 40;

    /** Where a quote is in the chapter. */
    public static final class Place {
        /** Its first and last word, inclusive. */
        public final int first;
        public final int last;
        /** The paragraphs it starts and ends in, numbered from 1. */
        public final int para;
        public final int paraTo;
        /** The page it starts on, as an index into the pages the text was made of. */
        public final int page;

        Place(int first, int last, int para, int paraTo, int page) {
            this.first = first;
            this.last = last;
            this.para = para;
            this.paraTo = paraTo;
            this.page = page;
        }

        public int words() {
            return last - first + 1;
        }
    }

    private final List<String> paragraphs = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final List<Integer> wordPara = new ArrayList<>();
    private final List<Integer> wordPage = new ArrayList<>();
    private final int pages;
    private QuoteMatcher.Words all;

    private ZoomText(int pages) {
        this.pages = pages;
    }

    /**
     * The chapter from its pages as plain text (paragraphs apart by an empty line, as
     * {@code BookText.plain} gives them). A paragraph that runs over a page edge stays one: the
     * first paragraph of a page joins the last of the page before unless that one ended a sentence.
     */
    public static ZoomText of(List<String> pageTexts) {
        final ZoomText t = new ZoomText(pageTexts == null ? 0 : pageTexts.size());
        final List<StringBuilder> paras = new ArrayList<>();
        for (int p = 0; p < t.pages; p++) {
            final String page = pageTexts.get(p) == null ? "" : pageTexts.get(p);
            boolean firstOfPage = true;
            for (String raw : page.split("\\n\\s*\\n")) {
                final String para = raw.trim().replaceAll("\\s+", " ");
                if (para.isEmpty()) {
                    continue;
                }
                final boolean joins = firstOfPage && !paras.isEmpty() && !endsSentence(paras.get(paras.size() - 1));
                firstOfPage = false;
                if (joins) {
                    paras.get(paras.size() - 1).append(' ').append(para);
                } else {
                    paras.add(new StringBuilder(para));
                }
                final int index = paras.size() - 1;
                for (String w : para.split(" ")) {
                    if (!w.isEmpty()) {
                        t.words.add(w);
                        t.wordPara.add(index);
                        t.wordPage.add(p);
                    }
                }
            }
        }
        for (StringBuilder b : paras) {
            t.paragraphs.add(b.toString());
        }
        return t;
    }

    private static boolean endsSentence(CharSequence para) {
        for (int i = para.length() - 1; i >= 0; i--) {
            final char c = para.charAt(i);
            if (c == '"' || c == '”' || c == '’' || c == '\'' || c == ')' || c == ']' || c == '»') {
                continue;
            }
            return c == '.' || c == '!' || c == '?' || c == '…' || c == ':' || c == '。' || c == '！' || c == '？';
        }
        return true;
    }

    /** The text as sent to the model: the paragraphs, an empty line between them. */
    public String plain() {
        final StringBuilder out = new StringBuilder();
        for (String p : paragraphs) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(p);
        }
        return out.toString();
    }

    public int chars() {
        int n = 0;
        for (String p : paragraphs) {
            n += p.length();
        }
        return n + 2 * Math.max(0, paragraphs.size() - 1);
    }

    public int paragraphCount() {
        return paragraphs.size();
    }

    public int wordCount() {
        return words.size();
    }

    /** How many pages the text was made of. */
    public int pageCount() {
        return pages;
    }

    private QuoteMatcher.Words all() {
        if (all == null) {
            all = new QuoteMatcher.Words(words);
        }
        return all;
    }

    /** Where a quote is, or null when it is not in the chapter (or too short to be found safely). */
    public Place locate(String quote) {
        return place(quote == null ? null : QuoteMatcher.locate(all(), quote));
    }

    /**
     * Where a passage is, given as its first words and its last words; with no last words, where
     * its first words are.
     */
    public Place locateSpan(String start, String end) {
        if (start == null) {
            return null;
        }
        if (end == null || end.trim().isEmpty()) {
            return locate(start);
        }
        return place(QuoteMatcher.locateSpan(all(), start, end));
    }

    /**
     * A passage of {@code count} words from where {@code head} is: how a stored sentence finds the
     * author's words again in any layout.
     */
    public Place locateFrom(String head, int count) {
        final Place p = locate(head);
        if (p == null) {
            return null;
        }
        final int last = Math.min(words.size() - 1, Math.max(p.last, p.first + Math.max(1, count) - 1));
        return place(new int[]{p.first, last});
    }

    private Place place(int[] range) {
        if (range == null || range[0] < 0 || range[1] >= words.size() || range[1] < range[0]) {
            return null;
        }
        return new Place(range[0], range[1], wordPara.get(range[0]) + 1, wordPara.get(range[1]) + 1, wordPage.get(range[0]));
    }

    /** The words of a place as the book has them. */
    public String text(Place p) {
        return p == null ? "" : QuoteMatcher.join(words.subList(p.first, p.last + 1));
    }

    /** As {@link #text}, cut at a word after {@link #PASSAGE_CHARS} characters. */
    public String passage(Place p) {
        final String s = text(p);
        if (s.length() <= PASSAGE_CHARS) {
            return s;
        }
        final int cut = s.lastIndexOf(' ', PASSAGE_CHARS);
        return s.substring(0, cut > 0 ? cut : PASSAGE_CHARS) + "…";
    }

    /**
     * The beginning of a place, long enough to be found again ({@link #HEAD_LETTERS} letters and
     * digits) and no longer than the place.
     */
    public String head(Place p) {
        if (p == null) {
            return "";
        }
        int letters = 0;
        int to = p.first;
        while (to <= p.last) {
            for (int i = 0; i < words.get(to).length(); i++) {
                if (Character.isLetterOrDigit(words.get(to).charAt(i))) {
                    letters++;
                }
            }
            if (letters >= HEAD_LETTERS) {
                break;
            }
            to++;
        }
        return QuoteMatcher.join(words.subList(p.first, Math.min(p.last, to) + 1));
    }
}
