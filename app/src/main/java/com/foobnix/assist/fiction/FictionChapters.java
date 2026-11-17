package com.foobnix.assist.fiction;

import com.foobnix.ai.BookChapters;
import com.foobnix.ai.SideMatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chapters of a story for the fiction companion, from the table of contents alone: every page
 * of the book is in one of them, in page order. A top entry with two or more entries one level
 * below it (Book the First, Part II) is cut into those; a book without a table of contents is cut
 * into page blocks ({@link BookChapters#blocks}).
 * <p>
 * A chapter is named to the model by its place in this list ({@link #n}, from 1) and stored with
 * that number and its key (the same in every layout); the reader sees {@link #label}: "Ch. 14"
 * where the book numbers its chapters, the chapter's own title where it does not, "p. 41–60" for
 * a page block. Titles are never changed.
 */
public final class FictionChapters {

    /** "Chapter 14", "CHAPTER XIV.", "Chương 3", "Chapter Twelve": the word for a chapter, then its number. */
    private static final Pattern WORDED = Pattern.compile(
            "^\\s*(?:(?:chapter|chapitre|cap[ií]tulo|capitolo|kapitel|hoofdstuk|глава|rozdzia[łl]|ch[uư][oơ]ng)\\s+"
                    + "|(?:chap|ch)\\.?\\s+|(?:chap|ch)\\.)([0-9]{1,4}|[\\p{L}\\-]+)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "14. The rescue", "XIV", "IV: ...": the number alone at the start, then the end or a separator. */
    private static final Pattern LEADING = Pattern.compile("^\\s*([0-9]{1,4}|[IVXLCDM]+)\\s*(?:$|[.:)\\-–—])");
    /** "Book II: The Golden Thread": a word and a number, the short name of a part. */
    private static final Pattern PART = Pattern.compile("^\\s*(\\p{L}{3,}\\s+(?:[IVXLCDM]+|\\d{1,3}|(?i:the\\s+)?\\p{L}+))\\s*(?:$|[.:\\-–—])");

    /** The largest chapter number read from a Roman numeral. */
    private static final int MAX_ROMAN = 300;

    private static final String[] WORDS = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
    private static final String[] TENS = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    private final List<BookChapters.Range> list = new ArrayList<>();
    /** The title of the part a chapter is in, or null. */
    private final List<String> parts = new ArrayList<>();
    private final boolean toc;
    private final int[] number;
    private final boolean numbered;
    private final int pages;

    private FictionChapters(List<BookChapters.Entry> entries, int pageCount) {
        pages = Math.max(0, pageCount);
        final List<BookChapters.Entry> valid = new ArrayList<>();
        if (entries != null) {
            for (BookChapters.Entry e : entries) {
                if (e != null && e.page >= 0 && e.page < pages) {
                    valid.add(e);
                }
            }
        }
        final List<BookChapters.Range> top = BookChapters.chapters(valid, pages);
        toc = !top.isEmpty();
        if (!toc) {
            for (BookChapters.Range r : BookChapters.blocks(pages, BookChapters.BLOCK_PAGES)) {
                list.add(r);
                parts.add(null);
            }
        } else {
            for (BookChapters.Range range : top) {
                final List<BookChapters.Entry> kids = BookChapters.FRONT.equals(range.key)
                        ? Collections.<BookChapters.Entry>emptyList() : children(valid, range);
                if (kids.size() < 2) {
                    list.add(range);
                    parts.add(null);
                    continue;
                }
                final Map<String, Integer> seen = new HashMap<>();
                for (int i = 0; i < kids.size(); i++) {
                    // the part's own first pages (its title page) go with its first chapter
                    final int from = i == 0 ? range.from : kids.get(i).page;
                    final int to = i + 1 < kids.size() ? kids.get(i + 1).page : range.to;
                    final String title = kids.get(i).title == null ? "" : kids.get(i).title.trim();
                    final String name = title.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                    final Integer before = seen.get(name);
                    final int n = before == null ? 0 : before + 1;
                    seen.put(name, n);
                    list.add(new BookChapters.Range(from, to, title, range.key + "/" + name + "#" + n));
                    parts.add(range.title);
                }
            }
        }
        number = new int[list.size()];
        int withNumber = 0;
        int counted = 0;
        for (int i = 0; i < list.size(); i++) {
            final BookChapters.Range r = list.get(i);
            if (!toc || BookChapters.FRONT.equals(r.key) || SideMatter.isSideTitle(r.title)) {
                continue;
            }
            counted++;
            number[i] = number(r.title);
            if (number[i] > 0) {
                withNumber++;
            }
        }
        numbered = withNumber >= 2 && withNumber * 5 >= counted * 3;
    }

    public static FictionChapters of(List<BookChapters.Entry> toc, int pageCount) {
        return new FictionChapters(toc, pageCount);
    }

    public int size() {
        return list.size();
    }

    /** Page count of the layout these chapters are in. */
    public int pages() {
        return pages;
    }

    /** Whether the book has a table of contents (else the chapters are page blocks). */
    public boolean hasToc() {
        return toc;
    }

    public BookChapters.Range get(int index) {
        return list.get(index);
    }

    /** The number the model is given for a chapter: its place in the list, from 1. */
    public static int n(int index) {
        return index + 1;
    }

    /** The key of chapter number {@code n} (from 1), or "" when there is none. */
    public String key(int n) {
        return n >= 1 && n <= list.size() ? list.get(n - 1).key : "";
    }

    /** The index of the chapter holding a page (0-based); -1 for a book without pages. */
    public int indexAt(int page) {
        if (list.isEmpty()) {
            return -1;
        }
        final int p = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < list.size(); i++) {
            final BookChapters.Range r = list.get(i);
            if (p >= r.from && p < r.to) {
                return i;
            }
        }
        return list.size() - 1;
    }

    /**
     * The chapter stored data names, in this list: by its key; else (a book without a table of
     * contents in another layout, a changed book) by its number, scaled when the number of
     * chapters differs. -1 when it names none.
     *
     * @param n            its number when stored (from 1)
     * @param storedCount  how many chapters the book had then
     */
    public int indexOf(String key, int n, int storedCount) {
        if (key != null && !key.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (key.equals(list.get(i).key)) {
                    return i;
                }
            }
        }
        if (n < 1 || list.isEmpty()) {
            return -1;
        }
        if (storedCount <= 0 || storedCount == list.size()) {
            return Math.min(n, list.size()) - 1;
        }
        // rounded up: a person is then hidden a little longer rather than shown too early
        final int index = (int) Math.ceil((n - 1) * (double) list.size() / storedCount);
        return Math.max(0, Math.min(list.size() - 1, index));
    }

    /**
     * How many chapters start after a page: what the spoiler shield hides. Side matter named by
     * the table of contents (index, about the author) does not count.
     */
    public int hiddenAfter(int page) {
        int hidden = 0;
        for (BookChapters.Range r : list) {
            if (r.from > page && !(toc && SideMatter.isSideTitle(r.title))) {
                hidden++;
            }
        }
        return hidden;
    }

    /** What the reader sees for a chapter: "Ch. 14", "Book II, Ch. 3", its own title, or "p. 41–60". */
    public String label(int index) {
        if (index < 0 || index >= list.size()) {
            return "";
        }
        final BookChapters.Range r = list.get(index);
        if (!toc) {
            return pagesLabel(r);
        }
        if (BookChapters.FRONT.equals(r.key)) {
            return "the opening pages";
        }
        final String title = r.title == null ? "" : r.title.trim();
        if (numbered) {
            if (number[index] > 0) {
                final String ch = "Ch. " + number[index];
                final String part = repeats(number[index]) ? partShort(parts.get(index)) : "";
                return part.isEmpty() ? ch : part + ", " + ch;
            }
            return title.isEmpty() ? pagesLabel(r) : title;
        }
        // the book does not number its chapters: their place among the chapters of the contents
        int place = 0;
        for (int i = 0; i <= index; i++) {
            if (!BookChapters.FRONT.equals(list.get(i).key)) {
                place++;
            }
        }
        return "Ch. " + place;
    }

    /** "Ch. 15–37" for a run of numbered chapters, else the two labels with a dash; one label when they are the same. */
    public String range(int first, int last) {
        final String a = label(first);
        final String b = label(last);
        if (a.isEmpty() || first == last || a.equals(b)) {
            return a.isEmpty() ? b : a;
        }
        if (b.isEmpty()) {
            return a;
        }
        if (a.startsWith("Ch. ") && b.startsWith("Ch. ")) {
            return a + "–" + b.substring(4);
        }
        if (a.startsWith("p. ") && b.startsWith("p. ")) {
            return "p. " + (list.get(first).from + 1) + "–" + list.get(last).to;
        }
        return a + " – " + b;
    }

    private static String pagesLabel(BookChapters.Range r) {
        return r.to - r.from <= 1 ? "p. " + (r.from + 1) : "p. " + (r.from + 1) + "–" + r.to;
    }

    /** Whether another chapter of the book carries the same number (chapters numbered anew in each part). */
    private boolean repeats(int n) {
        int count = 0;
        for (int v : number) {
            if (v == n) {
                count++;
            }
        }
        return count > 1;
    }

    /** "Book II" of "Book II: The Golden Thread"; a short part title as it is; "" for none or a long one. */
    static String partShort(String title) {
        if (title == null) {
            return "";
        }
        final String t = title.trim().replaceAll("\\s+", " ");
        final Matcher m = PART.matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        return t.length() <= 24 ? t : "";
    }

    /** The number a chapter title carries ("CHAPTER XIV. IN WHICH…" = 14), or 0. */
    static int number(String title) {
        if (title == null) {
            return 0;
        }
        final Matcher worded = WORDED.matcher(title);
        if (worded.find()) {
            final String token = worded.group(1);
            int n = arabic(token);
            if (n == 0) {
                n = roman(token.toUpperCase(Locale.ROOT));
            }
            if (n == 0) {
                n = word(token.toLowerCase(Locale.ROOT));
            }
            return n;
        }
        final Matcher lead = LEADING.matcher(title);
        if (lead.find()) {
            final int n = arabic(lead.group(1));
            return n > 0 ? n : roman(lead.group(1));
        }
        return 0;
    }

    private static int arabic(String s) {
        if (!s.matches("[0-9]{1,4}")) {
            return 0;
        }
        return Integer.parseInt(s);
    }

    /** A Roman numeral written the usual way ("XIV" = 14); 0 for anything else ("MIX", "CIVIL"). */
    static int roman(String s) {
        if (s == null || !s.matches("[IVXLCDM]{1,12}")) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            final int v = value(s.charAt(i));
            final int next = i + 1 < s.length() ? value(s.charAt(i + 1)) : 0;
            total += v < next ? -v : v;
        }
        // a chapter number, not a word that happens to be a numeral ("MIX", "DIV")
        return total > 0 && total <= MAX_ROMAN && toRoman(total).equals(s) ? total : 0;
    }

    private static int value(char c) {
        switch (c) {
            case 'I':
                return 1;
            case 'V':
                return 5;
            case 'X':
                return 10;
            case 'L':
                return 50;
            case 'C':
                return 100;
            case 'D':
                return 500;
            case 'M':
                return 1000;
            default:
                return 0;
        }
    }

    private static String toRoman(int n) {
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] marks = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        final StringBuilder out = new StringBuilder();
        int left = n;
        for (int i = 0; i < values.length && left > 0; i++) {
            while (left >= values[i]) {
                out.append(marks[i]);
                left -= values[i];
            }
        }
        return out.toString();
    }

    /** "twelve" = 12, "twenty-one" = 21; 0 for other words. */
    private static int word(String s) {
        for (int i = 0; i < WORDS.length; i++) {
            if (WORDS[i].equals(s)) {
                return i + 1;
            }
        }
        final String[] two = s.split("-");
        for (int t = 0; t < TENS.length; t++) {
            if (TENS[t].equals(two[0])) {
                final int tens = (t + 2) * 10;
                if (two.length == 1) {
                    return tens;
                }
                for (int i = 0; i < 9 && two.length == 2; i++) {
                    if (WORDS[i].equals(two[1])) {
                        return tens + i + 1;
                    }
                }
            }
        }
        return 0;
    }

    /** The entries one level below the range's own inside it, in page order, one per page. */
    private static List<BookChapters.Entry> children(List<BookChapters.Entry> toc, BookChapters.Range range) {
        int own = Integer.MAX_VALUE;
        for (BookChapters.Entry e : toc) {
            if (e.page >= range.from && e.page < range.to) {
                own = Math.min(own, e.level);
            }
        }
        int level = Integer.MAX_VALUE;
        for (BookChapters.Entry e : toc) {
            if (e.page >= range.from && e.page < range.to && e.level > own) {
                level = Math.min(level, e.level);
            }
        }
        final List<BookChapters.Entry> kids = new ArrayList<>();
        if (level == Integer.MAX_VALUE) {
            return kids;
        }
        for (BookChapters.Entry e : toc) {
            if (e.page >= range.from && e.page < range.to && e.level == level) {
                kids.add(e);
            }
        }
        Collections.sort(kids, (a, b) -> Integer.compare(a.page, b.page));
        final List<BookChapters.Entry> onePerPage = new ArrayList<>();
        for (BookChapters.Entry e : kids) {
            if (onePerPage.isEmpty() || onePerPage.get(onePerPage.size() - 1).page != e.page) {
                onePerPage.add(e);
            }
        }
        return onePerPage;
    }
}
