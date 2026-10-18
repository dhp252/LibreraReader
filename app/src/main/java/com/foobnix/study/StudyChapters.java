package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The chapters a book is studied in: the chapters of the AI summaries ({@link BookChapters}), but
 * a long top-level part with chapters inside (Part I, II...) is studied chapter by chapter, so a
 * card comes at every real chapter. Which parts are split is decided once, from their text, and
 * kept: the reader cuts the book the same way from the TOC alone.
 */
public final class StudyChapters {

    private StudyChapters() {
    }

    /** Keys of the top-level ranges with more than {@link StudyRules#LONG_CHARS} and chapters inside. */
    public static Set<String> longParts(List<BookChapters.Entry> toc, int pageCount, int[] pageChars) {
        final Set<String> split = new LinkedHashSet<>();
        final List<BookChapters.Entry> valid = valid(toc, pageCount);
        if (valid.isEmpty()) {
            return split;
        }
        for (BookChapters.Range range : BookChapters.chapters(valid, pageCount)) {
            if (!isFront(range) && BookChapters.length(pageChars, range.from, range.to) > StudyRules.LONG_CHARS
                    && !children(valid, range).isEmpty()) {
                split.add(range.key);
            }
        }
        return split;
    }

    /** The chapters to study, in page order, with the parts in {@code split} replaced by their chapters. */
    public static List<BookChapters.Range> cut(List<BookChapters.Entry> toc, int pageCount, Set<String> split) {
        final List<BookChapters.Entry> valid = valid(toc, pageCount);
        final List<BookChapters.Range> top = BookChapters.chaptersOrBlocks(valid, pageCount, BookChapters.BLOCK_PAGES);
        if (split == null || split.isEmpty() || valid.isEmpty()) {
            return top;
        }
        final List<BookChapters.Range> out = new ArrayList<>();
        for (BookChapters.Range range : top) {
            final List<BookChapters.Entry> kids = split.contains(range.key) ? children(valid, range)
                    : Collections.<BookChapters.Entry>emptyList();
            if (kids.isEmpty()) {
                out.add(range);
                continue;
            }
            final Map<String, Integer> seen = new HashMap<>();
            for (int i = 0; i < kids.size(); i++) {
                // the part's own first pages (its title page, an intro) go with its first chapter
                final int from = i == 0 ? range.from : kids.get(i).page;
                final int to = i + 1 < kids.size() ? kids.get(i + 1).page : range.to;
                final String title = kids.get(i).title == null ? "" : kids.get(i).title.trim();
                final String name = title.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                final Integer before = seen.get(name);
                final int n = before == null ? 0 : before + 1;
                seen.put(name, n);
                out.add(new BookChapters.Range(from, to, title, range.key + "/" + name + "#" + n));
            }
        }
        return out;
    }

    /** Pages before the first TOC entry (cover, copyright): no cards. */
    public static boolean isFront(BookChapters.Range range) {
        return range != null && BookChapters.FRONT.equals(range.key);
    }

    /** The chapter holding a page (clamped into the book), or null when there is none. */
    public static BookChapters.Range at(List<BookChapters.Range> chapters, int page) {
        final int i = indexAt(chapters, page);
        return i < 0 ? null : chapters.get(i);
    }

    public static int indexAt(List<BookChapters.Range> chapters, int page) {
        if (chapters == null || chapters.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < chapters.size(); i++) {
            if (page < chapters.get(i).to) {
                return i;
            }
        }
        return chapters.size() - 1;
    }

    private static List<BookChapters.Entry> valid(List<BookChapters.Entry> toc, int pageCount) {
        final List<BookChapters.Entry> valid = new ArrayList<>();
        if (toc != null) {
            for (BookChapters.Entry e : toc) {
                if (e != null && e.page >= 0 && e.page < pageCount) {
                    valid.add(e);
                }
            }
        }
        return valid;
    }

    /**
     * The entries one level below the range's own inside it, in page order, one per page; empty
     * when the range has none.
     */
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
