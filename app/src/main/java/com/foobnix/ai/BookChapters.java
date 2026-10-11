package com.foobnix.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chapters from the table of contents: where the chapter holding a page starts and ends.
 * Chapters come from the TOC, not from guessing headings in the text.
 */
public final class BookChapters {

    /** Key of the pages before the first chapter. */
    public static final String FRONT = "@front";
    /** Pages per part of a book without a table of contents. */
    public static final int BLOCK_PAGES = 20;

    /** One TOC entry; {@code page} is 0-based. */
    public static class Entry {
        public final String title;
        public final int level;
        public final int page;

        public Entry(String title, int level, int page) {
            this.title = title;
            this.level = level;
            this.page = page;
        }
    }

    /** Pages {@code from} (inclusive) to {@code to} (exclusive), and the title of the chapter. */
    public static class Range {
        public final int from;
        public final int to;
        public final String title;
        /**
         * Names the chapter in stored data, the same on every device and in every layout: its TOC
         * title and how many chapters before it have the same title. Page blocks of a book without
         * a TOC are named by their first page.
         */
        public final String key;

        public Range(int from, int to, String title) {
            this(from, to, title, "");
        }

        public Range(int from, int to, String title, String key) {
            this.from = from;
            this.to = to;
            this.title = title;
            this.key = key;
        }

        public int pages() {
            return to - from;
        }
    }

    private BookChapters() {
    }

    /**
     * The chapters of the book in page order: the top TOC level, or the level below when the top
     * holds a single entry (often just the book title). Entries sharing a page count once.
     */
    public static List<Range> chapters(List<Entry> toc, int pageCount) {
        final List<Entry> valid = new ArrayList<>();
        if (toc != null) {
            for (Entry e : toc) {
                if (e != null && e.page >= 0 && e.page < pageCount) {
                    valid.add(e);
                }
            }
        }
        final List<Range> chapters = new ArrayList<>();
        if (valid.isEmpty()) {
            return chapters;
        }
        int top = Integer.MAX_VALUE;
        for (Entry e : valid) {
            top = Math.min(top, e.level);
        }
        int atTop = 0;
        boolean deeper = false;
        for (Entry e : valid) {
            if (e.level == top) {
                atTop++;
            } else {
                deeper = true;
            }
        }
        final int level = atTop < 2 && deeper ? top + 1 : top;

        final List<Entry> picked = new ArrayList<>();
        for (Entry e : valid) {
            if (e.level <= level) {
                picked.add(e);
            }
        }
        Collections.sort(picked, (a, b) -> Integer.compare(a.page, b.page));

        final List<Entry> starts = new ArrayList<>();
        for (Entry e : picked) {
            if (starts.isEmpty() || starts.get(starts.size() - 1).page != e.page) {
                starts.add(e);
            }
        }
        if (starts.get(0).page > 0) {
            // pages before the first chapter (cover, preface) form their own part
            chapters.add(new Range(0, starts.get(0).page, "", FRONT));
        }
        final Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            final int to = i + 1 < starts.size() ? starts.get(i + 1).page : pageCount;
            final String title = starts.get(i).title == null ? "" : starts.get(i).title.trim();
            final String name = title.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            final Integer before = seen.get(name);
            final int n = before == null ? 0 : before + 1;
            seen.put(name, n);
            chapters.add(new Range(starts.get(i).page, to, title, name + "#" + n));
        }
        return chapters;
    }

    /** A book without a table of contents in parts of {@code blockPages} pages. */
    public static List<Range> blocks(int pageCount, int blockPages) {
        final List<Range> blocks = new ArrayList<>();
        final int size = Math.max(1, blockPages);
        for (int from = 0; from < pageCount; from += size) {
            blocks.add(new Range(from, Math.min(pageCount, from + size), "", "@pages:" + from));
        }
        return blocks;
    }

    /** The chapters from the TOC, or page blocks when the book has none. */
    public static List<Range> chaptersOrBlocks(List<Entry> toc, int pageCount, int blockPages) {
        final List<Range> chapters = chapters(toc, pageCount);
        return chapters.isEmpty() ? blocks(pageCount, blockPages) : chapters;
    }

    /**
     * The chapter holding {@code page}; without a table of contents, the block of
     * {@code blockPages} pages holding it.
     */
    public static Range chapterAt(List<Entry> toc, int page, int pageCount, int blockPages) {
        final List<Range> all = chaptersOrBlocks(toc, pageCount, blockPages);
        final int p = Math.max(0, Math.min(page, pageCount - 1));
        for (Range r : all) {
            if (p >= r.from && p < r.to) {
                return r;
            }
        }
        return all.isEmpty() ? new Range(0, 0, "", "@pages:0") : all.get(all.size() - 1);
    }

    /**
     * Parts for a text too long for one request: consecutive chapters joined while they fit in
     * {@code maxChars} characters; a chapter longer than that is cut between pages.
     *
     * @param pageLength characters of text on each page of the book
     */
    public static List<Range> parts(List<Range> chapters, int[] pageLength, int maxChars) {
        final List<Range> parts = new ArrayList<>();
        Range current = null;
        long currentLength = 0;
        for (Range chapter : chapters) {
            final long length = length(pageLength, chapter.from, chapter.to);
            if (length > maxChars) {
                // cut the chapter between pages
                int from = chapter.from;
                long piece = 0;
                for (int p = chapter.from; p < chapter.to; p++) {
                    final int pageChars = p < pageLength.length ? pageLength[p] : 0;
                    if (p > from && piece + pageChars > maxChars) {
                        parts.add(new Range(from, p, chapter.title));
                        from = p;
                        piece = 0;
                    }
                    piece += pageChars;
                }
                parts.add(new Range(from, chapter.to, chapter.title));
                current = null;
                currentLength = 0;
            } else if (current != null && currentLength + length <= maxChars) {
                current = new Range(current.from, chapter.to, current.title.isEmpty() ? chapter.title : current.title);
                parts.set(parts.size() - 1, current);
                currentLength += length;
            } else {
                current = chapter;
                currentLength = length;
                parts.add(current);
            }
        }
        return parts;
    }

    /** Characters on pages {@code from} to {@code to} (exclusive). */
    public static long length(int[] pageLength, int from, int to) {
        long sum = 0;
        for (int p = Math.max(0, from); p < to && p < pageLength.length; p++) {
            sum += pageLength[p];
        }
        return sum;
    }
}
