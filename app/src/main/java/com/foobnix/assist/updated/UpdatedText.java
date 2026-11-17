package com.foobnix.assist.updated;

import java.util.Locale;

/** Small text rules of "The book, updated": cleaning what a model wrote, and the lines the screens show. */
public final class UpdatedText {

    private UpdatedText() {
    }

    /** One line of text without the spaces a model leaves around it. */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** {@link #clean}, and nothing for the words a model writes in place of "I have none". */
    static String given(String s) {
        final String c = clean(s);
        final String k = c.toLowerCase(Locale.ROOT);
        if (k.equals("null") || k.equals("none") || k.equals("n/a") || k.equals("na") || k.equals("unknown")
                || k.equals("-") || k.equals("—") || k.equals("not known") || k.equals("no source")) {
            return "";
        }
        return c;
    }

    /** A year as a model gives it: a number, "1832", "c. 1755", "1755–65"; empty when it holds no digit. */
    static String year(Object y) {
        if (y instanceof Number) {
            final long n = ((Number) y).longValue();
            return n == 0 ? "" : String.valueOf(n);
        }
        if (!(y instanceof String)) {
            return "";
        }
        final String s = given((String) y);
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return s.length() > 24 ? s.substring(0, 24).trim() : s;
            }
        }
        return "";
    }

    /** A quotation without the quotation marks a model put around it. */
    static String unquote(String s) {
        String q = s == null ? "" : s.trim();
        while (q.length() > 1 && "\"“”«»'‘’".indexOf(q.charAt(0)) >= 0) {
            q = q.substring(1).trim();
        }
        while (q.length() > 1 && "\"“”«»'‘’".indexOf(q.charAt(q.length() - 1)) >= 0) {
            q = q.substring(0, q.length() - 1).trim();
        }
        return q;
    }

    /** At most {@code max} characters, cut at a space, with "…". */
    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        int end = s.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return s.substring(0, end).trim() + "…";
    }

    /** "Work, Author, Year" with the parts there are; the work comes first (the screen sets it in italics). */
    public static String sourceLine(UpdatedNote.Source s) {
        if (s == null || s.work.isEmpty()) {
            return "";
        }
        return join(", ", s.work, s.author, s.year);
    }

    /** "Marx, Capital, 1867" for the "Pushed further" label. */
    public static String pushedLine(UpdatedObjection o) {
        return o == null ? "" : join(", ", o.pushedWho, o.pushedWork, o.pushedYear);
    }

    static String join(String separator, String... parts) {
        final StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                if (out.length() > 0) {
                    out.append(separator);
                }
                out.append(p);
            }
        }
        return out.toString();
    }

    /**
     * The author's name for a label ("ADAM SMITH'S CLAIM"): the name as the library has it when it
     * is one short name, else empty (the label then says "the author").
     */
    public static String labelAuthor(String author) {
        final String a = clean(author);
        if (a.isEmpty() || a.length() > 28 || a.contains(",") || a.contains(";") || a.contains("&")
                || a.contains("/") || a.toLowerCase(Locale.ROOT).contains(" and ")) {
            return "";
        }
        return a;
    }
}
