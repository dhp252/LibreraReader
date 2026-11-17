package com.foobnix.assist.readsyou;

import com.foobnix.assist.zoom.ZoomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-zoom: for a chapter with stored zoom levels, the level each of its sections is shown at,
 * from the stored page counters of the section's pages: read much faster than the reader's pace,
 * its one line; at the pace, the lean version; slowly, with a long page or a re-read, its full
 * text. A section whose pages were not timed stays lean. No Android here.
 */
public final class AutoZoom {

    public static final int LINE = 0;
    public static final int LEAN = 1;
    public static final int FULL = 2;

    /** Why a section is not lean. */
    public static final int NONE = 0;
    public static final int FAST = 1;
    public static final int REREAD = 2;
    public static final int LONG = 3;
    public static final int SLOW = 4;

    public static final class Choice {
        public int level = LEAN;
        public int why = NONE;
        /** The section's pages in the chapter in the reader's layout, from 0; {@code to} exclusive. -1 when not known. */
        public int from = -1;
        public int to = -1;

        public boolean changed() {
            return level != LEAN;
        }
    }

    private AutoZoom() {
    }

    /**
     * One choice per section of {@code data}, in order.
     *
     * @param signals      the chapter's counters, in the reader's layout (of {@code chapterPages} pages)
     * @param chapterPages the chapter's pages in the reader's layout
     * @param pace         the reader's pace, ms a word; 0 when not known (then only re-reads and long pages open a section)
     */
    public static List<Choice> choose(ZoomData data, ReadsSignals signals, int chapterPages, double pace) {
        final List<Choice> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        final int n = data.sections.size();
        for (int i = 0; i < n; i++) {
            final ZoomData.Section s = data.sections.get(i);
            final Choice c = new Choice();
            out.add(c);
            if (s.page < 0 || data.pages <= 0 || chapterPages <= 0 || signals == null) {
                continue;
            }
            c.from = data.pageIn(s.page, chapterPages);
            int to = chapterPages;
            for (int j = i + 1; j < n; j++) {
                if (data.sections.get(j).page >= 0) {
                    to = data.pageIn(data.sections.get(j).page, chapterPages);
                    break;
                }
            }
            c.to = Math.max(c.from + 1, to);
            final ReadsSignals.Page sum = new ReadsSignals.Page();
            for (int p = c.from; p < c.to; p++) {
                final ReadsSignals.Page page = signals.peek(p);
                if (page != null) {
                    sum.add(page);
                }
            }
            if (sum.rereads + sum.passes > 0) {
                c.level = FULL;
                c.why = REREAD;
            } else if (sum.longs > 0) {
                c.level = FULL;
                c.why = LONG;
            } else if (sum.seconds > 0 && ReadsRules.isSlow(sum.seconds, sum.words, pace)) {
                c.level = FULL;
                c.why = SLOW;
            } else if (sum.seconds > 0 && ReadsRules.isSkim(sum.seconds, sum.words, pace)
                    && s.line != null && !s.line.trim().isEmpty()) {
                c.level = LINE;
                c.why = FAST;
            }
        }
        return out;
    }

    /** How many sections are not lean. */
    public static int changes(List<Choice> choices) {
        int n = 0;
        for (Choice c : choices) {
            if (c.changed()) {
                n++;
            }
        }
        return n;
    }
}
