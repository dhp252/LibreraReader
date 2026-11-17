package com.foobnix.assist.review;

import com.foobnix.assist.useit.UseDates;

import java.util.ArrayList;
import java.util.List;

/**
 * The forgetting curve of one idea (canvas "One idea's memory"): an estimate, never a measurement.
 * Recall falls as {@code exp(-days / stability)} after each time the idea was seen; the stability
 * grows with the idea's strength and with the wait it last survived, and is set so that recall is
 * near {@link #TARGET} when the schedule asks again ("review near 70%"). The screen says
 * "estimated" beside everything that comes from here.
 */
public final class ReviewCurve {

    /** The recall the schedule aims to ask at. */
    public static final double TARGET = 0.7;
    /** The wait each strength stands for, in days. */
    private static final int[] NOMINAL = {1, 2, 3, 8, 21, 42};

    private ReviewCurve() {
    }

    /**
     * One stretch of the curve, in days from the first event: recall was whole at {@code origin}
     * and is drawn from {@code from} to {@code to}.
     */
    public static final class Segment {
        public final int origin;
        public final int from;
        public final int to;
        public final double stability;
        /** After today: what would happen, not what did. */
        public final boolean ahead;

        Segment(int origin, int from, int to, double stability, boolean ahead) {
            this.origin = origin;
            this.from = from;
            this.to = to;
            this.stability = stability;
            this.ahead = ahead;
        }

        /** Recall (0..1) {@code day} days from the first event, inside this stretch. */
        public double at(double day) {
            return recall(day - origin, stability);
        }
    }

    /** A dot on the curve. */
    public static final class Mark {
        public static final int RIGHT = 0;
        public static final int WRONG = 1;
        public static final int NEXT = 2;

        public final int day;
        public final int kind;
        /** Recall just before it (0..1): where a NEXT dot sits; a past one sits at the top or the foot. */
        public final double recall;

        Mark(int day, int kind, double recall) {
            this.day = day;
            this.kind = kind;
            this.recall = recall;
        }
    }

    /** Everything the drawing needs, in days counted from the first event. */
    public static final class Model {
        public final List<Segment> segments = new ArrayList<>();
        public final List<Mark> marks = new ArrayList<>();
        /** The first event's day, yyyy-MM-dd; empty when the idea has no history. */
        public String firstDay = "";
        public int today;
        /** The last day drawn. */
        public int end;
        /** The estimated recall today, 0..100. */
        public int percent;
        /** The waits after the next review and after the one that follows, if both are answered right. */
        public int nextGap;
        public int thenGap;
        /** Days from today to the next review (0: today or overdue). */
        public int nextIn;
    }

    /** The stability in days of an idea at {@code strength} that last waited {@code lastGap} days. */
    public static double stability(int strength, int lastGap) {
        final int nominal = NOMINAL[ReviewItem.strength(strength)];
        final double wait = lastGap > 0 ? (nominal + lastGap) / 2.0 : nominal;
        return Math.max(1, wait) / -Math.log(TARGET);
    }

    /** The share (0..1) recalled {@code days} after an idea was last seen. */
    public static double recall(double days, double stability) {
        return days <= 0 ? 1 : Math.exp(-days / Math.max(0.01, stability));
    }

    /** The estimated chance, in percent, of recalling an idea on {@code today}. */
    public static int percentToday(ReviewItem item, String today) {
        final String last = item.lastDay();
        if (!UseDates.isDay(last) || !UseDates.isDay(today)) {
            return 0;
        }
        final int days = Math.max(0, UseDates.daysBetween(last, today));
        return (int) Math.round(100 * recall(days, stability(item.strength, item.gap)));
    }

    /**
     * The strength an idea had after each of its past events, replayed from its history (the
     * item only keeps the latest): the first event sets it, each later answer moves it by one.
     */
    static int[] strengths(ReviewItem item) {
        final int[] out = new int[item.history.size()];
        int s = 0;
        for (int i = 0; i < out.length; i++) {
            final ReviewItem.Past p = item.history.get(i);
            if (i == 0) {
                s = ReviewItem.RECALL.equals(p.kind) ? (p.right ? 3 : 1) : ReviewItem.ADDED.equals(p.kind) ? 2
                        : (p.right ? 2 : 1);
            } else {
                s = ReviewItem.strength(s + (p.right ? 1 : -1));
            }
            out[i] = s;
        }
        if (out.length > 0) {
            // the latest is known for sure
            out[out.length - 1] = item.strength;
        }
        return out;
    }

    /** The curve of an idea as it stands on {@code today}: its past, and the next two reviews if answered right. */
    public static Model model(ReviewItem item, String today) {
        final Model m = new Model();
        if (item == null || item.history.isEmpty() || !UseDates.isDay(today)) {
            return m;
        }
        m.firstDay = item.history.get(0).day;
        m.today = Math.max(0, UseDates.daysBetween(m.firstDay, today));
        m.percent = percentToday(item, today);
        final int[] strengths = strengths(item);
        int prev = 0;
        for (int i = 0; i < item.history.size(); i++) {
            final ReviewItem.Past p = item.history.get(i);
            final int day = Math.max(prev, UseDates.daysBetween(m.firstDay, p.day));
            final boolean last = i == item.history.size() - 1;
            final int until = last ? Math.max(day, m.today)
                    : Math.max(day, UseDates.daysBetween(m.firstDay, item.history.get(i + 1).day));
            final double s = stability(strengths[i], last ? item.gap : p.gap > 0 ? p.gap : day - prev);
            m.marks.add(new Mark(day, p.right ? Mark.RIGHT : Mark.WRONG, 1));
            if (until > day) {
                m.segments.add(new Segment(day, day, until, s, false));
            }
            prev = day;
        }
        // ahead: the next review (today when it is due or overdue), then one more if it is right
        final int next = Math.max(m.today, UseDates.daysBetween(m.firstDay, item.due));
        m.nextIn = next - m.today;
        final double now = stability(item.strength, item.gap);
        if (next > m.today) {
            m.segments.add(new Segment(prev, m.today, next, now, true));
        }
        m.marks.add(new Mark(next, Mark.NEXT, recall(next - prev, now)));
        final ReviewItem after = after(item, UseDates.addDays(m.firstDay, next));
        m.nextGap = after.gap;
        m.thenGap = ReviewSchedule.ahead(after, 1)[1];
        m.end = next + m.nextGap;
        final double later = stability(after.strength, after.gap);
        m.segments.add(new Segment(next, next, m.end, later, true));
        m.marks.add(new Mark(m.end, Mark.NEXT, recall(m.nextGap, later)));
        return m;
    }

    /** The idea as it would be after a right answer on {@code day}, the day of its next review. */
    private static ReviewItem after(ReviewItem item, String day) {
        final ReviewItem copy = new ReviewItem();
        copy.strength = item.strength;
        copy.gap = item.gap;
        copy.due = item.due;
        ReviewSchedule.answer(copy, true, day);
        return copy;
    }
}
