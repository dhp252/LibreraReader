package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewCurveTest {

    /** The canvas's idea: missed on Oct 26, right on Oct 27 and Oct 30, due Nov 7. */
    private static ReviewItem canvas() {
        final ReviewItem i = new ReviewItem();
        i.chapterKey = "c#0";
        i.id = "1";
        i.idea = "Time lost switching tasks";
        i.strength = 1;
        i.gap = 1;
        i.due = "2026-10-27";
        i.reason = ReviewItem.MISSED;
        i.add(new ReviewItem.Past("2026-10-26", ReviewItem.RECALL, false, 1));
        ReviewSchedule.answer(i, true, "2026-10-27");
        ReviewSchedule.answer(i, true, "2026-10-30");
        return i;
    }

    @Test
    public void recallIsNearTheTargetOnTheDueDay() {
        final ReviewItem i = canvas();
        assertEquals("2026-11-07", i.due);
        assertEquals(3, i.strength);
        // "70% chance you'd recall it today"
        assertEquals(70, ReviewCurve.percentToday(i, "2026-11-07"));
        assertEquals(100, ReviewCurve.percentToday(i, "2026-10-30"));
        assertTrue(ReviewCurve.percentToday(i, "2026-11-03") > 70);
        assertTrue(ReviewCurve.percentToday(i, "2026-11-20") < 70);
        assertEquals(0, ReviewCurve.percentToday(new ReviewItem(), "2026-11-07"));
    }

    @Test
    public void stabilityGrowsWithStrengthAndTheLastGap() {
        double before = 0;
        for (int s = 0; s <= 5; s++) {
            final double now = ReviewCurve.stability(s, 0);
            assertTrue(now > before);
            before = now;
        }
        assertTrue(ReviewCurve.stability(3, 21) > ReviewCurve.stability(3, 8));
        assertEquals(1, ReviewCurve.recall(0, 5), 0.0001);
        assertEquals(1, ReviewCurve.recall(-3, 5), 0.0001);
        assertEquals(ReviewCurve.TARGET, ReviewCurve.recall(8, ReviewCurve.stability(3, 8)), 0.0001);
        assertTrue(ReviewCurve.recall(10, 5) < ReviewCurve.recall(5, 5));
    }

    @Test
    public void theModelHasThePastAndTheNextTwoReviews() {
        final ReviewCurve.Model m = ReviewCurve.model(canvas(), "2026-11-07");
        assertEquals("2026-10-26", m.firstDay);
        assertEquals(12, m.today);
        assertEquals(70, m.percent);
        assertEquals(0, m.nextIn);
        // "next review, then 3 weeks"
        assertEquals(21, m.nextGap);
        assertEquals(42, m.thenGap);
        assertEquals(33, m.end);
        // three past events, the next review (today) and the one after
        assertEquals(5, m.marks.size());
        assertEquals(ReviewCurve.Mark.WRONG, m.marks.get(0).kind);
        assertEquals(0, m.marks.get(0).day);
        assertEquals(ReviewCurve.Mark.RIGHT, m.marks.get(1).kind);
        assertEquals(1, m.marks.get(1).day);
        assertEquals(4, m.marks.get(2).day);
        assertEquals(ReviewCurve.Mark.NEXT, m.marks.get(3).kind);
        assertEquals(12, m.marks.get(3).day);
        assertEquals(0.7, m.marks.get(3).recall, 0.01);
        assertEquals(33, m.marks.get(4).day);
        // each fall is slower than the last
        assertEquals(4, m.segments.size());
        for (int s = 1; s < m.segments.size(); s++) {
            assertTrue(m.segments.get(s).stability > m.segments.get(s - 1).stability);
        }
        assertFalse(m.segments.get(2).ahead);
        assertTrue(m.segments.get(3).ahead);
        assertEquals(1, m.segments.get(3).at(12), 0.0001);
        assertEquals(0.7, m.segments.get(3).at(33), 0.01);
    }

    @Test
    public void anIdeaNotDueYetShowsTheWaitAhead() {
        final ReviewCurve.Model m = ReviewCurve.model(canvas(), "2026-11-02");
        assertEquals(7, m.today);
        assertEquals(5, m.nextIn);
        // the fall goes on, dashed, from today to the due day
        final ReviewCurve.Segment ahead = m.segments.get(3);
        assertTrue(ahead.ahead);
        assertEquals(4, ahead.origin);
        assertEquals(7, ahead.from);
        assertEquals(12, ahead.to);
        assertEquals(21, m.nextGap);
        assertEquals(33, m.end);
    }

    @Test
    public void anOverdueIdeaIsReviewedToday() {
        final ReviewCurve.Model m = ReviewCurve.model(canvas(), "2026-11-20");
        assertEquals(25, m.today);
        assertEquals(0, m.nextIn);
        assertTrue(m.percent < 70);
        assertEquals(46, m.end);
        assertTrue(ReviewCurve.model(new ReviewItem(), "2026-11-20").segments.isEmpty());
    }
}
