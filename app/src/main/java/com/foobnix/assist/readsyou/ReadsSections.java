package com.foobnix.assist.readsyou;

import com.foobnix.ai.BookChapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Attention, section by section" of "How you read it": a chapter's sections (the table of
 * contents' entries below the chapter, else blocks of pages) and how each was read, from the
 * stored page counters only. No Android.
 */
public final class ReadsSections {

    /** How a section was read. DRIFTED exists only with the camera's signal (drift marks). */
    public enum Rating {
        /** No page of it was timed. */
        NONE,
        STEADY,
        /** Much faster than the reader's pace for its words. */
        SKIMMED,
        /** A re-read (paging back, or with a calibrated gaze a second pass over a page) or a long page in it. */
        STRUGGLED,
        /** Eyes off the page in it (Eye mode's camera). */
        DRIFTED
    }

    public static final class Section {
        /** The sub-section's title as the table of contents has it; empty for a block of pages. */
        public final String title;
        /** Its pages in the chapter, from 0; {@code to} is the page after its last. */
        public final int from;
        public final int to;
        public Rating rating = Rating.NONE;
        public int seconds;
        /** Pages of it that are spots to review. */
        public int spots;

        public Section(String title, int from, int to) {
            this.title = title == null ? "" : title;
            this.from = from;
            this.to = to;
        }
    }

    private ReadsSections() {
    }

    /**
     * The sections of the chapter on the book's pages {@code chapterFrom} (0-based) up to
     * {@code chapterTo} (exclusive): the table of contents' entries below the chapter's own when
     * there are at least two, else blocks of pages. The pages before the first sub-section form
     * an untitled one.
     */
    public static List<Section> split(List<BookChapters.Entry> toc, int chapterFrom, int chapterTo) {
        final int pages = chapterTo - chapterFrom;
        final List<Section> out = new ArrayList<>();
        if (pages <= 0) {
            return out;
        }
        List<BookChapters.Entry> subs = subs(toc, chapterFrom, chapterTo);
        if (subs.size() > ReadsRules.SECTION_MAX) {
            int top = Integer.MAX_VALUE;
            for (BookChapters.Entry e : subs) {
                top = Math.min(top, e.level);
            }
            final List<BookChapters.Entry> first = new ArrayList<>();
            for (BookChapters.Entry e : subs) {
                if (e.level == top) {
                    first.add(e);
                }
            }
            subs = first;
        }
        if (subs.size() < 2 || subs.size() > ReadsRules.SECTION_MAX) {
            return blocks(pages);
        }
        if (subs.get(0).page > chapterFrom) {
            out.add(new Section("", 0, subs.get(0).page - chapterFrom));
        }
        for (int i = 0; i < subs.size(); ) {
            final int page = subs.get(i).page;
            // sub-sections that start on one page cannot be told apart by page times: one row
            final StringBuilder title = new StringBuilder();
            int j = i;
            for (; j < subs.size() && subs.get(j).page == page; j++) {
                final String t = subs.get(j).title == null ? "" : subs.get(j).title.trim();
                if (!t.isEmpty()) {
                    title.append(title.length() > 0 ? " / " : "").append(t);
                }
            }
            final int to = j < subs.size() ? subs.get(j).page : chapterTo;
            out.add(new Section(title.toString(), page - chapterFrom, to - chapterFrom));
            i = j;
        }
        return out;
    }

    /** The table of contents' entries inside the chapter that are below the chapter's own entry, in page order. */
    private static List<BookChapters.Entry> subs(List<BookChapters.Entry> toc, int chapterFrom, int chapterTo) {
        final List<BookChapters.Entry> subs = new ArrayList<>();
        if (toc == null) {
            return subs;
        }
        int own = Integer.MAX_VALUE;
        for (BookChapters.Entry e : toc) {
            if (e != null && e.page == chapterFrom) {
                own = Math.min(own, e.level);
            }
        }
        for (BookChapters.Entry e : toc) {
            if (e != null && e.page >= chapterFrom && e.page < chapterTo && (e.page > chapterFrom || e.level > own)) {
                subs.add(e);
            }
        }
        // a stable sort: entries of one page keep the table's order
        Collections.sort(subs, (a, b) -> Integer.compare(a.page, b.page));
        return subs;
    }

    /** A chapter of {@code pages} pages in equal blocks, {@link ReadsRules#SECTION_BLOCKS} at most. */
    static List<Section> blocks(int pages) {
        final List<Section> out = new ArrayList<>();
        final int n = Math.max(1, Math.min(ReadsRules.SECTION_BLOCKS, pages));
        final int size = (pages + n - 1) / n;
        for (int from = 0; from < pages; from += size) {
            out.add(new Section("", from, Math.min(pages, from + size)));
        }
        return out;
    }

    /**
     * Rates the sections from the chapter's stored counters.
     *
     * @param signals the chapter's signals, in the layout the sections count pages in
     * @param pace    the reader's pace in ms a word; 0 when not known (then nothing is "skimmed")
     */
    public static List<Section> rate(List<Section> sections, ReadsSignals signals, double pace) {
        for (Section s : sections) {
            final ReadsSignals.Page sum = new ReadsSignals.Page();
            s.spots = 0;
            for (int p = s.from; p < s.to; p++) {
                final ReadsSignals.Page page = signals == null ? null : signals.peek(p);
                if (page != null) {
                    sum.add(page);
                    if (page.isSpot()) {
                        s.spots++;
                    }
                }
            }
            s.seconds = sum.seconds;
            s.rating = rating(sum, pace);
        }
        return sections;
    }

    /** How pages with these summed counters were read. */
    public static Rating rating(ReadsSignals.Page sum, double pace) {
        if (sum == null || sum.isEmpty()) {
            return Rating.NONE;
        }
        final int struggles = sum.rereads + sum.longs + sum.passes;
        if (sum.drifts > 0 && sum.drifts >= struggles) {
            return Rating.DRIFTED;
        }
        if (struggles > 0) {
            return Rating.STRUGGLED;
        }
        if (sum.seconds == 0) {
            // only an offer or a card was counted on it, no reading time
            return Rating.NONE;
        }
        return ReadsRules.isSkim(sum.seconds, sum.words, pace) ? Rating.SKIMMED : Rating.STEADY;
    }
}
