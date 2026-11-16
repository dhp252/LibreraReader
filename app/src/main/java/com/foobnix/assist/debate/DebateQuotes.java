package com.foobnix.assist.debate;

import com.foobnix.ai.QuoteMatcher;
import com.foobnix.assist.xray.XrayIdea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which words of the author's reply are the book's own. Everything the model put in quotation
 * marks is looked for in the book ({@link QuoteMatcher}): words that are found keep their marks
 * (and are shown as the book has them), words that are not found lose them, so "words in quotes
 * are the author's" stays true. Plain Java, no Android.
 */
public final class DebateQuotes {

    /** Pages outside the chapter a quote is looked for on at most. */
    public static final int MAX_OTHER_PAGES = 400;

    /** Where the book's words are looked up. */
    public interface Finder {
        /** The quote as the book has it and its page, or null when the book does not have it. */
        Found find(String quote) throws Exception;
    }

    /** The words of a book's pages, for {@link #scan}. */
    public interface Pages {
        int pageCount();

        /** The words of a 0-based page, in reading order. */
        List<String> words(int page) throws Exception;
    }

    /** A quote found in the book. */
    public static final class Found {
        /** The words as the book has them. */
        public final String text;
        /** 0-based page they start on (a hint for the jump), or -1. */
        public final int page;

        public Found(String text, int page) {
            this.text = text;
            this.page = page;
        }
    }

    /** A piece of the reply: plain words in the author's voice, or the book's own words. */
    public static final class Part {
        public final String text;
        public final boolean quoted;

        Part(String text, boolean quoted) {
            this.text = text;
            this.quoted = quoted;
        }
    }

    /** A reply after its quotes were checked. */
    public static final class Checked {
        public final List<Part> parts;
        /** The first quote found in the book, as the book has it; null when the reply has none. */
        public final String quote;
        /** 0-based page of {@link #quote}, or -1. */
        public final int page;

        Checked(List<Part> parts, String quote, int page) {
            this.parts = parts;
            this.quote = quote;
            this.page = page;
        }

        /** The reply as one text; only the book's own words stand in quotation marks. */
        public String plain() {
            final StringBuilder out = new StringBuilder();
            for (Part p : parts) {
                out.append(p.quoted ? "“" + p.text + "”" : p.text);
            }
            return out.toString();
        }
    }

    private static final String OPEN = "\"“„«";
    private static final String CLOSE = "\"”“»";

    private DebateQuotes() {
    }

    /**
     * Checks every quoted span of {@code reply} with {@code finder}.
     *
     * @param quoteField the quote the model named beside its reply (it may have left the marks out
     *                   of the reply), or empty
     */
    public static Checked check(String reply, String quoteField, Finder finder) throws Exception {
        String text = reply == null ? "" : reply.trim();
        final String named = quoteField == null ? "" : strip(quoteField).trim();
        if (!named.isEmpty() && !hasSpan(text, named)) {
            final int at = text.indexOf(named);
            if (at >= 0) {
                text = text.substring(0, at) + "“" + named + "”" + text.substring(at + named.length());
            }
        }
        final List<Part> parts = new ArrayList<>();
        String first = null;
        int page = -1;
        for (Part span : split(text)) {
            if (!span.quoted) {
                add(parts, span.text, false);
                continue;
            }
            final Found found = finder == null ? null : finder.find(span.text);
            if (found == null || found.text == null || found.text.trim().isEmpty()) {
                // not the book's words: no quotation marks
                add(parts, span.text, false);
                continue;
            }
            final String words = found.text.trim();
            add(parts, words, true);
            if (first == null) {
                first = words;
                page = found.page;
            }
        }
        return new Checked(parts, first, page);
    }

    /**
     * The reply cut at its double quotation marks (“ ” „ “ « » and the straight one): spans inside
     * a pair are quoted, the rest is plain. The marks themselves are dropped, also a stray one.
     */
    static List<Part> split(String text) {
        final List<Part> out = new ArrayList<>();
        final StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (OPEN.indexOf(c) >= 0) {
                final int end = closing(text, i);
                if (end > i) {
                    final String inner = text.substring(i + 1, end).trim();
                    if (!inner.isEmpty()) {
                        if (plain.length() > 0) {
                            out.add(new Part(plain.toString(), false));
                            plain.setLength(0);
                        }
                        out.add(new Part(inner, true));
                    }
                    i = end + 1;
                    continue;
                }
                // a mark without its pair
                i++;
                continue;
            }
            if (CLOSE.indexOf(c) >= 0) {
                i++;
                continue;
            }
            plain.append(c);
            i++;
        }
        if (plain.length() > 0) {
            out.add(new Part(plain.toString(), false));
        }
        return out;
    }

    /** The mark closing the one at {@code open}, or -1. */
    private static int closing(String text, int open) {
        final char o = text.charAt(open);
        final String ends = o == '"' ? "\"" : o == '«' ? "»" : o == '„' ? "“”\"" : "”\"";
        for (int i = open + 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (ends.indexOf(c) >= 0) {
                return i;
            }
            if (c != o && OPEN.indexOf(c) >= 0 && CLOSE.indexOf(c) < 0) {
                // another quote opens before this one closed
                return -1;
            }
        }
        return -1;
    }

    private static boolean hasSpan(String text, String quote) {
        final String key = letters(quote);
        for (Part p : split(text)) {
            if (p.quoted && (letters(p.text).contains(key) || key.contains(letters(p.text)))) {
                return true;
            }
        }
        return false;
    }

    /** Without any double quotation mark. */
    static String strip(String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (OPEN.indexOf(c) < 0 && CLOSE.indexOf(c) < 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String letters(String s) {
        return com.foobnix.tts.TtsTextLocator.normalize(s);
    }

    private static void add(List<Part> parts, String text, boolean quoted) {
        if (text.isEmpty()) {
            return;
        }
        if (!quoted && !parts.isEmpty() && !parts.get(parts.size() - 1).quoted) {
            final Part last = parts.remove(parts.size() - 1);
            parts.add(new Part(last.text + text, false));
            return;
        }
        parts.add(new Part(text, quoted));
    }

    /**
     * A quote among the marked passages of the chapter's ideas (the words that were sent to the
     * model): the passage's own words and the page of the idea's first mark, or null.
     */
    public static Found inIdeas(List<XrayIdea> ideas, String quote) {
        if (QuoteMatcher.key(quote) == null || ideas == null) {
            return null;
        }
        for (XrayIdea idea : ideas) {
            for (String passage : idea.quotes) {
                if (passage == null) {
                    continue;
                }
                final List<String> words = words(passage);
                final int[] range = QuoteMatcher.locate(new QuoteMatcher.Words(words), quote);
                if (range != null) {
                    return new Found(QuoteMatcher.join(words.subList(range[0], range[1] + 1)), idea.firstPage);
                }
            }
        }
        return null;
    }

    /**
     * A quote in the book's pages: the chapter's pages first ({@code from} to {@code to} - 1),
     * then outwards from it, on at most {@code maxOther} other pages. A quote may run over a page
     * edge, so two pages are read together. Too short a quote is never found
     * ({@link QuoteMatcher#key}).
     */
    public static Found scan(Pages pages, int from, int to, int maxOther, String quote) throws Exception {
        if (QuoteMatcher.key(quote) == null) {
            return null;
        }
        final int count = pages.pageCount();
        if (count <= 0) {
            return null;
        }
        final int first = Math.max(0, Math.min(from, count - 1));
        final int end = Math.max(first + 1, Math.min(to, count));
        final Cache cache = new Cache(pages);
        for (int p = first; p < end; p++) {
            final Found found = at(cache, p, count, quote);
            if (found != null) {
                return found;
            }
        }
        int after = end;
        int before = first - 1;
        for (int n = 0; n < maxOther && (after < count || before >= 0); ) {
            if (after < count) {
                final Found found = at(cache, after++, count, quote);
                if (found != null) {
                    return found;
                }
                n++;
            }
            if (before >= 0 && n < maxOther) {
                final Found found = at(cache, before--, count, quote);
                if (found != null) {
                    return found;
                }
                n++;
            }
        }
        return null;
    }

    /** The quote starting on page {@code p}, or on {@code p} + 1 when read together with it. */
    private static Found at(Cache cache, int p, int count, String quote) throws Exception {
        final List<String> here = cache.words(p);
        final List<String> both = new ArrayList<>(here);
        if (p + 1 < count) {
            both.addAll(cache.words(p + 1));
        }
        if (both.isEmpty()) {
            return null;
        }
        final int[] range = QuoteMatcher.locate(new QuoteMatcher.Words(both), quote);
        if (range == null) {
            return null;
        }
        return new Found(QuoteMatcher.join(both.subList(range[0], range[1] + 1)), range[0] < here.size() ? p : p + 1);
    }

    /** The last few pages read, so a page is not read twice in a row. */
    private static final class Cache {
        private static final int KEPT = 6;
        private final Pages pages;
        private final Map<Integer, List<String>> kept = new LinkedHashMap<Integer, List<String>>(KEPT * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<String>> eldest) {
                return size() > KEPT;
            }
        };

        Cache(Pages pages) {
            this.pages = pages;
        }

        List<String> words(int page) throws Exception {
            List<String> words = kept.get(page);
            if (words == null) {
                words = pages.words(page);
                if (words == null) {
                    words = new ArrayList<>();
                }
                kept.put(page, words);
            }
            return words;
        }
    }

    static List<String> words(String text) {
        final String t = text.trim();
        return t.isEmpty() ? new ArrayList<String>() : Arrays.asList(t.split("\\s+"));
    }
}
