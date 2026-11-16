package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;
import com.foobnix.study.StudyChapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The book as parts and chapters, from the table of contents alone: the chapters of the AI
 * features ({@link BookChapters}), and a top-level entry with entries one level below inside it
 * (Book I, Part Two...) is a part whose chapters are those entries. A top-level entry without any
 * is a part of one chapter, itself. Front pages and side matter (contents, index, ads) are left
 * out.
 * <p>
 * Chapter keys are the ones study mode gives (a part's chapters are {@code <part key>/<name>#n},
 * see {@link StudyChapters#cut}), so a study preview's lines are found by key.
 */
public final class SkeletonParts {

    private SkeletonParts() {
    }

    /** A part of the book: its own range and its chapters, in page order. */
    public static final class Part {
        public final BookChapters.Range range;
        public final List<BookChapters.Range> chapters;

        Part(BookChapters.Range range, List<BookChapters.Range> chapters) {
            this.range = range;
            this.chapters = chapters;
        }

        /** The part has chapters of its own (not a lone chapter standing as a part). */
        public boolean isGroup() {
            return chapters.size() > 1 || (chapters.size() == 1 && !chapters.get(0).key.equals(range.key));
        }
    }

    /** The parts of the book, in page order. */
    public static List<Part> group(List<BookChapters.Entry> toc, int pageCount) {
        final List<BookChapters.Entry> valid = valid(toc, pageCount);
        final List<Part> parts = new ArrayList<>();
        for (BookChapters.Range range : BookChapters.chaptersOrBlocks(valid, pageCount, BookChapters.BLOCK_PAGES)) {
            if (StudyChapters.isOutside(range)) {
                continue;
            }
            final List<BookChapters.Entry> kids = children(valid, range);
            final List<BookChapters.Range> chapters = new ArrayList<>();
            if (kids.size() < 2) {
                chapters.add(range);
            } else {
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
                    final BookChapters.Range chapter = new BookChapters.Range(from, to, title, range.key + "/" + name + "#" + n);
                    if (!StudyChapters.isOutside(chapter)) {
                        chapters.add(chapter);
                    }
                }
                if (chapters.isEmpty()) {
                    chapters.add(range);
                }
            }
            parts.add(new Part(range, chapters));
        }
        return parts;
    }

    /** Whether any part holds chapters of its own; else the book is a plain list of chapters. */
    public static boolean hasGroups(List<Part> parts) {
        for (Part p : parts) {
            if (p.isGroup()) {
                return true;
            }
        }
        return false;
    }

    /** Every chapter of every part, in order. */
    public static List<BookChapters.Range> chapters(List<Part> parts) {
        final List<BookChapters.Range> out = new ArrayList<>();
        for (Part p : parts) {
            out.addAll(p.chapters);
        }
        return out;
    }

    /** The index of the part holding a page (0-based), or -1 when none does (front pages, side matter). */
    public static int partAt(List<Part> parts, int page) {
        for (int i = 0; i < parts.size(); i++) {
            final BookChapters.Range r = parts.get(i).range;
            if (page >= r.from && page < r.to) {
                return i;
            }
        }
        return -1;
    }

    /** The chapter of a part holding a page, or null. */
    public static BookChapters.Range chapterAt(Part part, int page) {
        for (BookChapters.Range r : part.chapters) {
            if (page >= r.from && page < r.to) {
                return r;
            }
        }
        return null;
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

    /** The entries one level below the range's own inside it, in page order, one per page. */
    static List<BookChapters.Entry> children(List<BookChapters.Entry> toc, BookChapters.Range range) {
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
