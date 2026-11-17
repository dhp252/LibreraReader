package com.foobnix.assist.review;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ReviewScheduleTest {

    private static ReviewItem item(int strength, String due, String reason) {
        final ReviewItem i = new ReviewItem();
        i.chapterKey = "of the division of labour#0";
        i.id = "3";
        i.idea = "Time is lost switching between tasks";
        i.chapterTitle = "Of the Division of Labour";
        i.strength = strength;
        i.due = due;
        i.reason = reason;
        i.gap = 1;
        return i;
    }

    @Test
    public void rightAnswersClimbTheLadder() {
        // the canvas: missed in recall, memory 1 of 5: "comes back in 3 days, then 8"
        final ReviewItem i = item(1, "2026-10-27", ReviewItem.MISSED);
        ReviewSchedule.answer(i, true, "2026-10-27");
        assertEquals(2, i.strength);
        assertEquals("2026-10-30", i.due);
        assertEquals(ReviewItem.FADING, i.reason);
        assertArrayEquals(new int[]{3, 8, 21}, ReviewSchedule.ahead(i, 2));

        ReviewSchedule.answer(i, true, "2026-10-30");
        assertEquals(3, i.strength);
        assertEquals("2026-11-07", i.due);
        ReviewSchedule.answer(i, true, "2026-11-07");
        assertEquals(4, i.strength);
        assertEquals("2026-11-28", i.due);
        ReviewSchedule.answer(i, true, "2026-11-28");
        assertEquals(5, i.strength);
        assertEquals(42, i.gap);
        ReviewSchedule.answer(i, true, "2027-01-09");
        // doubling stops at 12 weeks, the strength at 5
        assertEquals(5, i.strength);
        assertEquals(84, i.gap);
        ReviewSchedule.answer(i, true, "2027-04-03");
        assertEquals(84, i.gap);
        assertEquals("2027-06-26", i.due);
        assertEquals(6, i.history.size());
        assertEquals(84, i.history.get(5).gap);
    }

    @Test
    public void wrongAnswerComesBackTomorrowAndLosesOne() {
        final ReviewItem i = item(3, "2026-11-07", ReviewItem.FADING);
        ReviewSchedule.answer(i, false, "2026-11-09");
        assertEquals(2, i.strength);
        assertEquals("2026-11-10", i.due);
        assertEquals(1, i.gap);
        assertFalse(i.history.get(0).right);
        assertEquals(ReviewItem.REVIEW, i.history.get(0).kind);
        assertArrayEquals(new int[]{1, 8, 21}, ReviewSchedule.ahead(i, 2));

        final ReviewItem low = item(0, "2026-11-07", ReviewItem.MISSED);
        ReviewSchedule.answer(low, false, "2026-11-07");
        assertEquals(0, low.strength);
        assertEquals(ReviewItem.MISSED, low.reason);
        // from nothing: tomorrow, then 2 days, then the ladder
        assertArrayEquals(new int[]{1, 2, 3, 8}, ReviewSchedule.ahead(low, 3));
    }

    @Test
    public void anEarlyRightAnswerDoesNotClimb() {
        final ReviewItem i = item(2, "2026-11-20", ReviewItem.FADING);
        i.gap = 8;
        ReviewSchedule.answer(i, true, "2026-11-15");
        assertEquals(2, i.strength);
        assertEquals("2026-11-20", i.due);
        assertEquals(1, i.history.size());
        // an early wrong one still counts
        ReviewSchedule.answer(i, false, "2026-11-15");
        assertEquals(1, i.strength);
        assertEquals("2026-11-16", i.due);
    }

    @Test
    public void aNoteKeepsItsReason() {
        final ReviewItem i = item(1, "2026-11-07", ReviewItem.NOTE);
        ReviewSchedule.answer(i, true, "2026-11-07");
        assertEquals(ReviewItem.NOTE, i.reason);
    }

    @Test
    public void gapsByStrength() {
        assertEquals(2, ReviewSchedule.gapAfterRight(0, 1));
        assertEquals(3, ReviewSchedule.gapAfterRight(1, 1));
        assertEquals(8, ReviewSchedule.gapAfterRight(2, 3));
        assertEquals(21, ReviewSchedule.gapAfterRight(3, 8));
        assertEquals(42, ReviewSchedule.gapAfterRight(4, 21));
        assertEquals(84, ReviewSchedule.gapAfterRight(5, 42));
        assertEquals(84, ReviewSchedule.gapAfterRight(5, 84));
        assertEquals(84, ReviewSchedule.gapAfterRight(9, 0));
    }

    @Test
    public void theStoredItemKeepsItsContract() throws Exception {
        final ReviewItem i = item(1, "2026-10-27", ReviewItem.MISSED);
        i.quote = "the time commonly lost in passing from one sort of work to another";
        i.page = 12;
        i.recall = 1234L;
        ReviewSchedule.answer(i, true, "2026-10-27");
        assertEquals("review:of the division of labour#0:3", i.key());
        final JSONObject o = i.toJson();
        // the names another feature reads
        assertEquals("Time is lost switching between tasks", o.getString("idea"));
        assertEquals("Of the Division of Labour", o.getString("chapterTitle"));
        assertEquals("fading", o.getString("reason"));
        assertEquals(2, o.getInt("strength"));
        assertEquals("2026-10-30", o.getString("due"));
        final JSONObject past = o.getJSONArray("history").getJSONObject(0);
        assertEquals("2026-10-27", past.getString("day"));
        assertEquals("review", past.getString("kind"));
        assertTrue(past.getBoolean("right"));

        final ReviewItem back = ReviewItem.fromJson(i.key(), new JSONObject(o.toString()));
        assertNotNull(back);
        assertEquals(i.chapterKey, back.chapterKey);
        assertEquals("3", back.id);
        assertEquals(i.idea, back.idea);
        assertEquals(2, back.strength);
        assertEquals(3, back.gap);
        assertEquals(12, back.page);
        assertEquals(1234L, back.recall);
        assertEquals(1, back.history.size());
        assertEquals(3, back.history.get(0).gap);
    }

    @Test
    public void keysWithColonsAndBadItems() throws Exception {
        // a chapter key may hold colons; the idea id is what follows the last one
        final JSONObject o = new JSONObject().put("idea", "An idea").put("due", "2026-11-07").put("strength", 9);
        final ReviewItem i = ReviewItem.fromJson("review:chapter 1: the pin factory#0:r2", o);
        assertEquals("chapter 1: the pin factory#0", i.chapterKey);
        assertEquals("r2", i.id);
        assertEquals(5, i.strength);
        assertEquals(ReviewItem.FADING, i.reason);
        assertEquals("review:@pages:20:n1f", ReviewItem.key("@pages:20", "n1f"));
        assertEquals("a_b", ReviewItem.id("a:b"));

        assertNull(ReviewItem.fromJson("review:x:1", new JSONObject().put("idea", "An idea").put("due", "soon")));
        assertNull(ReviewItem.fromJson("review:x:1", new JSONObject().put("due", "2026-11-07")));
        assertNull(ReviewItem.fromJson("recall:x", o));
        assertNull(ReviewItem.fromJson("review:x:1", null));
        assertTrue(i.isDue("2026-11-07"));
        assertTrue(i.isDue("2026-12-01"));
        assertFalse(i.isDue("2026-11-06"));
    }
}
