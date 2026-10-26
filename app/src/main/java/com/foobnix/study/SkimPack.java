package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import java.util.List;

/**
 * What the preview sends instead of the book: what a skilled reader skims. For every chapter its
 * title, its length in words and its opening and closing paragraphs; the first and last chapters
 * (introduction, conclusion) get a larger share. The shares shrink until the whole fits in one
 * request; a chapter short enough goes whole.
 */
public final class SkimPack {

    /** The first and last chapters count this many times as much as the others. */
    static final int ENDS_WEIGHT = 3;
    /** Characters of tags and title per chapter, kept out of the shares. */
    static final int OVERHEAD = 120;
    /** Below this a chapter still shows its first words. */
    static final int MIN_SHARE = 160;

    private SkimPack() {
    }

    /**
     * @param chapters the study chapters (front matter is left out)
     * @param pageText plain text of each page of the book (null = no text)
     * @param budget   characters the pack may hold
     */
    public static String build(List<BookChapters.Range> chapters, String[] pageText, int budget) {
        int first = -1;
        int last = -1;
        int count = 0;
        for (int i = 0; i < chapters.size(); i++) {
            if (!StudyChapters.isOutside(chapters.get(i))) {
                if (first < 0) {
                    first = i;
                }
                last = i;
                count++;
            }
        }
        if (count == 0) {
            return "";
        }
        final int weights = count + (ENDS_WEIGHT - 1) * (first == last ? 1 : 2);
        final long free = Math.max(0, budget - (long) OVERHEAD * count);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) {
            final BookChapters.Range chapter = chapters.get(i);
            if (StudyChapters.isOutside(chapter)) {
                continue;
            }
            final String text = text(pageText, chapter.from, chapter.to);
            final int weight = i == first || i == last ? ENDS_WEIGHT : 1;
            final int share = (int) Math.max(MIN_SHARE, free * weight / weights);
            out.append("<chapter n=\"").append(i).append("\" title=\"").append(attr(chapter.title))
                    .append("\" words=\"").append(words(text)).append("\">\n");
            if (text.length() <= share) {
                if (!text.isEmpty()) {
                    out.append(text).append('\n');
                }
            } else {
                final int opening = share * 3 / 5;
                out.append("<opening>\n").append(opening(text, opening)).append("\n</opening>\n");
                out.append("<ending>\n").append(ending(text, share - opening)).append("\n</ending>\n");
            }
            out.append("</chapter>\n");
        }
        return out.toString();
    }

    /** The text of pages from..to (exclusive), pages apart as paragraphs. */
    public static String text(String[] pageText, int from, int to) {
        final StringBuilder out = new StringBuilder();
        for (int p = Math.max(0, from); p < to && p < pageText.length; p++) {
            final String page = pageText[p];
            if (page != null && !page.trim().isEmpty()) {
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                out.append(page.trim());
            }
        }
        return out.toString();
    }

    /** Words separated by white space. */
    public static long words(String text) {
        long n = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            final boolean space = Character.isWhitespace(text.charAt(i));
            if (!space && !in) {
                n++;
            }
            in = !space;
        }
        return n;
    }

    /** Whole paragraphs from the start while they fit; a first paragraph too long is cut at a word. */
    static String opening(String text, int max) {
        final String[] paragraphs = text.split("\n\n");
        final StringBuilder out = new StringBuilder();
        for (String p : paragraphs) {
            final String para = p.trim();
            if (para.isEmpty()) {
                continue;
            }
            if (out.length() == 0 && para.length() > max) {
                return headWords(para, max);
            }
            if (out.length() + 2 + para.length() > max) {
                break;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(para);
        }
        return out.toString();
    }

    /** Whole paragraphs up to the end while they fit; a last paragraph too long keeps its last words. */
    static String ending(String text, int max) {
        final String[] paragraphs = text.split("\n\n");
        final StringBuilder out = new StringBuilder();
        for (int i = paragraphs.length - 1; i >= 0; i--) {
            final String para = paragraphs[i].trim();
            if (para.isEmpty()) {
                continue;
            }
            if (out.length() == 0 && para.length() > max) {
                return tailWords(para, max);
            }
            if (out.length() + 2 + para.length() > max) {
                break;
            }
            if (out.length() > 0) {
                out.insert(0, "\n\n");
            }
            out.insert(0, para);
        }
        return out.toString();
    }

    private static String headWords(String text, int max) {
        int cut = text.lastIndexOf(' ', Math.max(0, max - 1));
        if (cut <= 0) {
            cut = Math.min(text.length(), max - 1);
        }
        return text.substring(0, cut).trim() + "…";
    }

    private static String tailWords(String text, int max) {
        final int from = Math.max(0, text.length() - (max - 1));
        // from a word's first letter, else from the next word
        int cut = from == 0 || Character.isWhitespace(text.charAt(from - 1)) ? from : text.indexOf(' ', from);
        if (cut < 0 || cut >= text.length() - 1) {
            cut = from;
        }
        return "…" + text.substring(cut).trim();
    }

    private static String attr(String s) {
        return s == null ? "" : s.replace("\"", "'").replaceAll("\\s+", " ").trim();
    }
}
