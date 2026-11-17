package com.foobnix.assist.fiction;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Timeline and Map tabs show of a stored timeline for a reader's place, free of Android.
 * At the place it was built for, or after it, everything shows (under its "as of Ch. N"). When
 * the reader went back ({@link FictionWhere#BEFORE}) only the moments and places of the chapters
 * up to the reader's show, and nothing that was written with later chapters in mind: not the
 * plan's progress and legs, not where the story "is now", not what comes next.
 */
public final class FictionStoryView {

    /** Where a tap on a moment or a place goes: the book's own words and the page to look around. */
    public static final class Jump {
        public final String quote;
        /** 0-based page in the layout at hand; -1 when unknown. */
        public final int page;

        Jump(String quote, int page) {
            this.quote = quote;
            this.page = page;
        }
    }

    public final List<FictionTimeline.Moment> moments = new ArrayList<>();
    /** The places the reader may see, in order of visit. */
    public final List<FictionTimeline.Place> places = new ArrayList<>();
    /** Moments and places left out because the reader went back. */
    public int hidden;
    /** The plan, the place the story is at and what comes next may show. */
    public final boolean progress;

    private final int markPages;
    private final int pages;

    private FictionStoryView(boolean progress, int markPages, int pages) {
        this.progress = progress;
        this.markPages = markPages;
        this.pages = pages;
    }

    public static FictionStoryView build(FictionTimeline t, FictionChapters chapters, FictionWhere where) {
        final FictionStoryView v = new FictionStoryView(where.state != FictionWhere.BEFORE, t.mark.pages, chapters.pages());
        for (FictionTimeline.Moment m : t.moments) {
            if (where.sees(chapters.indexOf(m.key, m.ch, t.mark.chapters))) {
                v.moments.add(m);
            } else {
                v.hidden++;
            }
        }
        for (FictionTimeline.Place p : t.places) {
            if (where.sees(chapters.indexOf(p.key, p.ch, t.mark.chapters))) {
                v.places.add(p);
            } else {
                v.hidden++;
            }
        }
        return v;
    }

    /** Where a moment's tap goes; null when its quote was not found in the book. */
    public Jump jump(FictionTimeline.Moment m) {
        return m == null || m.quote.isEmpty() ? null
                : new Jump(m.quote, FictionTimeline.scaled(m.page, markPages, pages));
    }

    /**
     * Where a place's chip goes: the words the story reaches it with, else the first moment the
     * reader may see at that place that has a found quote; null when there is neither.
     */
    public Jump jump(FictionTimeline.Place p) {
        if (p == null) {
            return null;
        }
        if (!p.quote.isEmpty()) {
            return new Jump(p.quote, FictionTimeline.scaled(p.page, markPages, pages));
        }
        for (FictionTimeline.Moment m : moments) {
            if (!m.quote.isEmpty() && FictionCast.same(m.place, p.name)) {
                return jump(m);
            }
        }
        return null;
    }

    /** The chips: each place the reader may see once, in order of first visit. */
    public List<FictionTimeline.Place> chips() {
        final List<FictionTimeline.Place> out = new ArrayList<>();
        for (FictionTimeline.Place p : places) {
            boolean have = false;
            for (FictionTimeline.Place o : out) {
                if (FictionCast.same(o.name, p.name)) {
                    have = true;
                    break;
                }
            }
            if (!have) {
                out.add(p);
            }
        }
        return out;
    }

    /** The longest amount among a plan's legs, planned or taken: what a full bar stands for. 0 for none. */
    public static double longest(FictionTimeline.Plan plan) {
        double max = 0;
        if (plan != null) {
            for (FictionTimeline.Leg l : plan.legs) {
                max = Math.max(max, Math.max(l.plan, l.actual));
            }
        }
        return max;
    }

    /** How far the plan's count is, 0 to 1; 0 when the plan has no total or no count. */
    public static float done(FictionTimeline.Plan plan) {
        if (plan == null || plan.total <= 0 || plan.now <= 0) {
            return 0;
        }
        return (float) Math.max(0, Math.min(1, plan.now / plan.total));
    }
}
