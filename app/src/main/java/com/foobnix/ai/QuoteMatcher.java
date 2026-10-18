package com.foobnix.ai;

import com.foobnix.tts.TtsTextLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds AI-picked quotes among the words of a book. Quotes and words are compared by their
 * letters and digits only ({@link TtsTextLocator#normalize}): punctuation, spacing, accents and
 * words cut at line ends ("exam-" "ple") then do not matter.
 * <p>
 * A quote is located in its chapter once, when the model answers ({@link #locate}), and stored
 * with the words of the book. Pages find it again by text whenever they are drawn
 * ({@link #onPage}), so it survives a new layout of the book (EPUB font size, page size).
 */
public final class QuoteMatcher {

    /** Quotes shorter than this, in letters and digits, would turn up by chance. */
    static final int MIN_QUOTE = 16;
    /** Shortest piece of a quote trusted where the quote runs over a page edge. */
    static final int MIN_SPAN = 20;
    /** Letters allowed between a page edge and a quote running over it: running heads, page numbers. */
    static final int EDGE_SLACK = 60;
    /** Head and tail of a quote that are looked for when the model changed letters in between. */
    static final int ANCHOR = 30;

    /** Words prepared once, searched for many quotes. */
    public static final class Words {
        final String text;
        private final int[] owner;
        private final int count;

        public Words(List<String> words) {
            count = words == null ? 0 : words.size();
            final String[] normalized = new String[count];
            int total = 0;
            for (int i = 0; i < count; i++) {
                normalized[i] = TtsTextLocator.normalize(words.get(i));
                total += normalized[i].length();
            }
            final StringBuilder text = new StringBuilder(total);
            owner = new int[total];
            for (int i = 0; i < count; i++) {
                for (int k = 0; k < normalized[i].length(); k++) {
                    owner[text.length() + k] = i;
                }
                text.append(normalized[i]);
            }
            this.text = text.toString();
        }

        public int size() {
            return count;
        }

        /** The letters and digits of all the words, run together. */
        public String letters() {
            return text;
        }

        /** The words holding letters {@code from} to {@code to} - 1: {first, last}, inclusive. */
        public int[] range(int from, int to) {
            return new int[]{owner[from], owner[to - 1]};
        }

        /** Letter {@code at} is the first of its word. */
        public boolean startsWord(int at) {
            return at == 0 || (at < owner.length && owner[at - 1] != owner[at]);
        }

        /** Letter {@code at} - 1 is the last of its word. */
        public boolean endsWord(int at) {
            return at == owner.length || (at > 0 && at < owner.length && owner[at - 1] != owner[at]);
        }
    }

    private QuoteMatcher() {
    }

    /** The letters a quote is compared by, or null when it is too short to be found safely. */
    public static String key(String quote) {
        final String key = TtsTextLocator.normalize(quote);
        return key.length() < MIN_QUOTE ? null : key;
    }

    /**
     * Where a quote of the model is in a chapter: exactly, or by its head and its tail at about the
     * right distance from each other when the model changed a few letters in between.
     *
     * @return {first, last} word indexes, inclusive, or null when the quote is not in the text
     */
    public static int[] locate(Words words, String quote) {
        final String key = key(quote);
        if (key == null || words.text.isEmpty()) {
            return null;
        }
        final String text = words.text;
        final int at = text.indexOf(key);
        if (at >= 0) {
            return words.range(at, at + key.length());
        }
        if (key.length() < 2 * ANCHOR) {
            return null;
        }
        final String head = key.substring(0, ANCHOR);
        final String tail = key.substring(key.length() - ANCHOR);
        for (int h = text.indexOf(head); h >= 0; h = text.indexOf(head, h + 1)) {
            final int t = text.indexOf(tail, h + ANCHOR);
            if (t < 0) {
                // no tail after this head, so none after a later head either
                return null;
            }
            final int length = t + ANCHOR - h;
            if (length * 5 >= key.length() * 4 && length * 4 <= key.length() * 5) {
                return words.range(h, t + ANCHOR);
            }
        }
        return null;
    }

    /** A quote as the book has it: its words joined, the index of its first word, and which quote it was. */
    public static final class Found {
        public final String text;
        public final int firstWord;
        /** Its index in the quotes given to {@link #locateAll}. */
        public final int quote;

        Found(String text, int firstWord, int quote) {
            this.text = text;
            this.firstWord = firstWord;
            this.quote = quote;
        }
    }

    /**
     * The quotes of the model that are in the words, each once, in the words of the book: what is
     * stored is then exactly what the pages hold, also where the model changed a letter.
     */
    public static List<Found> locateAll(List<String> words, List<String> quotes) {
        final List<Found> found = new ArrayList<>();
        final Words all = new Words(words);
        final List<String> keys = new ArrayList<>();
        for (int q = 0; q < quotes.size(); q++) {
            final String quote = quotes.get(q);
            final int[] range = locate(all, quote);
            if (range == null) {
                continue;
            }
            final String text = join(words.subList(range[0], range[1] + 1));
            final String key = key(text);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
                found.add(new Found(text, range[0], q));
            }
        }
        return found;
    }

    /**
     * The words of a page covered by quotes: every whole occurrence, else the beginning of a quote
     * at the end of the page or the end of a quote at the start of the page, for quotes that run
     * over a page edge.
     *
     * @param keys quotes as {@link #key} returns them
     * @return word ranges {first, last}, inclusive
     */
    public static List<int[]> onPage(Words page, List<String> keys) {
        final List<int[]> ranges = new ArrayList<>();
        final String text = page.text;
        if (text.isEmpty() || keys == null) {
            return ranges;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            boolean whole = false;
            for (int at = text.indexOf(key); at >= 0; at = text.indexOf(key, at + key.length())) {
                ranges.add(page.range(at, at + key.length()));
                whole = true;
            }
            if (whole) {
                continue;
            }
            final int[] head = headAtEnd(page, key);
            if (head != null) {
                ranges.add(page.range(head[0], head[1]));
            }
            final int[] tail = tailAtStart(page, key);
            if (tail != null) {
                ranges.add(page.range(tail[0], tail[1]));
            }
        }
        return ranges;
    }

    /**
     * The longest beginning of {@code key} that starts a word and runs to near the end of the
     * page: {from, to} letters, cut back to whole words.
     */
    static int[] headAtEnd(Words page, String key) {
        final String text = page.text;
        final int n = text.length();
        int[] best = null;
        int bestLength = 0;
        for (int p = Math.max(0, n - key.length() - EDGE_SLACK); p + MIN_SPAN <= n; p++) {
            if (!page.startsWord(p) || !text.regionMatches(p, key, 0, MIN_SPAN)) {
                continue;
            }
            int m = MIN_SPAN;
            while (p + m < n && m < key.length() && text.charAt(p + m) == key.charAt(m)) {
                m++;
            }
            // a letter or two may match by chance after the quote's last word on the page
            int end = p + m;
            while (end > p && !page.endsWord(end)) {
                end--;
            }
            if (m < key.length() && p + m >= n - EDGE_SLACK && end > p && m > bestLength) {
                best = new int[]{p, end};
                bestLength = m;
            }
        }
        return best;
    }

    /**
     * The longest end of {@code key} that ends a word near the start of the page: {from, to}
     * letters, cut to whole words.
     */
    static int[] tailAtStart(Words page, String key) {
        final String text = page.text;
        final int n = text.length();
        final int k = key.length();
        int[] best = null;
        int bestLength = 0;
        for (int e = MIN_SPAN; e <= n && e <= k + EDGE_SLACK; e++) {
            if (!page.endsWord(e) || !text.regionMatches(e - MIN_SPAN, key, k - MIN_SPAN, MIN_SPAN)) {
                continue;
            }
            int m = MIN_SPAN;
            while (e - m - 1 >= 0 && m < k && text.charAt(e - m - 1) == key.charAt(k - m - 1)) {
                m++;
            }
            // a letter or two of a running head may match by chance before the quote's first word
            int start = e - m;
            while (start < e && !page.startsWord(start)) {
                start++;
            }
            if (m < k && e - m <= EDGE_SLACK && start < e && m > bestLength) {
                best = new int[]{start, e};
                bestLength = m;
            }
        }
        return best;
    }

    /**
     * Words as one line of text, as the book has them: a word cut at a line end ("exam-" "ple")
     * is put back together.
     */
    public static String join(List<String> words) {
        final StringBuilder out = new StringBuilder();
        boolean glue = false;
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i) == null ? "" : words.get(i).trim();
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0 && !glue) {
                out.append(' ');
            }
            final String next = nextWord(words, i + 1);
            glue = word.length() > 1 && word.endsWith("-") && next != null
                    && Character.isLowerCase(next.codePointAt(0));
            out.append(glue ? word.substring(0, word.length() - 1) : word);
        }
        return out.toString();
    }

    private static String nextWord(List<String> words, int from) {
        for (int i = from; i < words.size(); i++) {
            final String w = words.get(i) == null ? "" : words.get(i).trim();
            if (!w.isEmpty()) {
                return w;
            }
        }
        return null;
    }
}
