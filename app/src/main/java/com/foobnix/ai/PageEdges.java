package com.foobnix.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Running heads and page numbers of a fixed layout (PDF, DjVu): lines at the top or bottom edge of
 * a page that are a page number, or that come back at the same edge of nearby pages (the book's or
 * the chapter's title, its page number aside). They are not the book's text: a quote or paragraph
 * running over a page break would take them in and mark them.
 */
public final class PageEdges {

    /** Pages on each side of a page whose edges are compared with its own. */
    public static final int NEAR = 6;
    /** Lines looked at from each edge: a head can have two. */
    static final int EDGE_LINES = 2;
    /** A line with more words is text, never a head. */
    static final int MAX_HEAD_WORDS = 15;
    /** Nearby pages that must have a line to be a running head. */
    static final int REPEATS = 2;
    /** Letters a repeated line needs (digits aside) to be told apart from chance. */
    static final int MIN_HEAD_LETTERS = 3;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ROMAN = Pattern.compile("^m{0,4}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,5}$");
    /** Words that go with a page number: "Page 12", "12 of 300", "Trang 12". */
    private static final Set<String> PAGE_WORDS = new HashSet<>(java.util.Arrays.asList("page", "pg", "p", "of", "trang"));

    /** A word and where it is on its page, from 0 (top) to 1 (bottom). */
    public static final class Word {
        final String text;
        final float left;
        final float top;
        final float bottom;

        public Word(String text, float left, float top, float bottom) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.top = top;
            this.bottom = bottom;
        }

        boolean placed() {
            return !text.trim().isEmpty() && top >= 0 && left >= 0 && bottom > top;
        }

        float center() {
            return (top + bottom) / 2;
        }
    }

    /** Words at one height, left to right: indexes into the page's words. */
    public static final class Line {
        final List<Integer> words = new ArrayList<>();
        float top;
        float bottom;
        String text = "";

        float height() {
            return bottom - top;
        }
    }

    /** The lines of a page at its edges, and what they read as. */
    public static final class Page {
        final List<Line> top = new ArrayList<>();
        final List<Line> bottom = new ArrayList<>();
        final List<String> topKeys = new ArrayList<>();
        final List<String> bottomKeys = new ArrayList<>();
        /** Space between the outermost line and the next one, top and bottom; -1 without one. */
        float topGap = -1;
        float bottomGap = -1;
    }

    private PageEdges() {
    }

    /** The page's lines from top to bottom; words at the same height (columns too) form one line. */
    public static List<Line> lines(List<Word> words) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).placed()) {
                order.add(i);
            }
        }
        Collections.sort(order, (a, b) -> Float.compare(words.get(a).center(), words.get(b).center()));
        final List<Line> lines = new ArrayList<>();
        Line line = null;
        for (int i : order) {
            final Word w = words.get(i);
            if (line == null || w.center() > line.bottom) {
                line = new Line();
                line.top = w.top;
                line.bottom = w.bottom;
                lines.add(line);
            } else {
                line.top = Math.min(line.top, w.top);
                line.bottom = Math.max(line.bottom, w.bottom);
            }
            line.words.add(i);
        }
        for (Line l : lines) {
            Collections.sort(l.words, (a, b) -> Float.compare(words.get(a).left, words.get(b).left));
            final StringBuilder text = new StringBuilder();
            for (int i : l.words) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(words.get(i).text.trim());
            }
            l.text = text.toString();
        }
        return lines;
    }

    /** The lines a head may be: up to {@link #EDGE_LINES} from each edge, outermost first. */
    public static Page page(List<Line> lines) {
        final Page page = new Page();
        final int n = lines.size();
        for (int k = 0; k < EDGE_LINES && k < n; k++) {
            page.top.add(lines.get(k));
            page.topKeys.add(key(lines.get(k).text));
            page.bottom.add(lines.get(n - 1 - k));
            page.bottomKeys.add(key(lines.get(n - 1 - k).text));
        }
        if (n >= 2) {
            page.topGap = lines.get(1).top - lines.get(0).bottom;
            page.bottomGap = lines.get(n - 1).top - lines.get(n - 2).bottom;
        }
        return page;
    }

    /**
     * The words of the page's running heads and page numbers: from each edge inwards, lines that
     * are a page number or come back on {@link #REPEATS} nearby pages, until one that is neither.
     *
     * @param nearby the edges of pages around it (not itself)
     */
    public static Set<Integer> heads(Page page, List<Page> nearby) {
        final Set<Integer> drop = new HashSet<>();
        final Set<Line> dropped = new HashSet<>();
        for (int side = 0; side < 2; side++) {
            final boolean top = side == 0;
            final List<Line> edge = top ? page.top : page.bottom;
            final List<String> keys = top ? page.topKeys : page.bottomKeys;
            for (int k = 0; k < edge.size(); k++) {
                final Line line = edge.get(k);
                if (dropped.contains(line) || line.words.size() > MAX_HEAD_WORDS) {
                    break;
                }
                final float gap = k > 0 ? -1 : top ? page.topGap : page.bottomGap;
                final boolean number = isPageNumber(line.text, gap >= 0.8f * line.height());
                if (!number && !repeats(keys.get(k), top, nearby)) {
                    break;
                }
                dropped.add(line);
                drop.addAll(line.words);
            }
        }
        return drop;
    }

    /**
     * A line that is only a page number ("12", "- 12 -", "Page 12 of 300", "xii"). A Roman one
     * counts only set apart from the text: a short last line of a paragraph can spell one ("mix").
     */
    static boolean isPageNumber(String text, boolean apart) {
        final String[] tokens = SPACES.split(text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim());
        if (tokens.length == 0 || tokens.length > 4 || tokens[0].isEmpty()) {
            return false;
        }
        boolean digits = false;
        boolean roman = false;
        for (String t : tokens) {
            if (DIGITS.matcher(t).matches()) {
                digits = true;
            } else if (t.length() <= 7 && ROMAN.matcher(t).matches()) {
                roman = true;
            } else if (!PAGE_WORDS.contains(t)) {
                return false;
            }
        }
        return digits || roman && apart;
    }

    /** What a line reads as on every page: lower-case letters, any number as '#'. */
    static String key(String text) {
        final StringBuilder out = new StringBuilder();
        for (String t : SPACES.split(text.toLowerCase(Locale.ROOT).trim())) {
            final String word = t.replaceAll("[^\\p{L}\\p{N}]+", "");
            if (word.isEmpty()) {
                continue;
            }
            if (ROMAN.matcher(word).matches() || word.matches("\\p{N}+")) {
                out.append('#');
                continue;
            }
            boolean inNumber = false;
            for (int i = 0; i < word.length(); i++) {
                final char c = word.charAt(i);
                if (Character.isDigit(c)) {
                    if (!inNumber) {
                        out.append('#');
                    }
                    inNumber = true;
                } else {
                    out.append(c);
                    inNumber = false;
                }
            }
        }
        return out.toString();
    }

    private static boolean repeats(String key, boolean top, List<Page> nearby) {
        if (letters(key) < MIN_HEAD_LETTERS || nearby == null) {
            return false;
        }
        int seen = 0;
        for (Page other : nearby) {
            if (other != null && (top ? other.topKeys : other.bottomKeys).contains(key) && ++seen >= REPEATS) {
                return true;
            }
        }
        return false;
    }

    private static int letters(String key) {
        int n = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) != '#') {
                n++;
            }
        }
        return n;
    }
}
