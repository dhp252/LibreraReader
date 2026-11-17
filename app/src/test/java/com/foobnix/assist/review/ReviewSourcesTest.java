package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.recall.Recall;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class ReviewSourcesTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    /** 2026-10-26 10:00 UTC. */
    private static final long OCT_26 = 1793008800000L;
    private static final long DAY = 24L * 3600 * 1000;

    private static Recall.Idea idea(int id, String text, String state) {
        final Recall.Idea i = new Recall.Idea(id, text);
        i.state = state;
        i.add("The book's own words for idea " + id, 10 + id);
        return i;
    }

    private static Recall.Attempt attempt(long date, boolean fromPassages, Recall.Idea... ideas) {
        final Recall.Attempt a = new Recall.Attempt();
        a.state = Recall.DONE;
        a.title = "Of the Division of Labour";
        a.date = date;
        a.fromPassages = fromPassages;
        a.ideas = Arrays.asList(ideas);
        return a;
    }

    private static Map<String, ReviewItem> byKey(List<ReviewItem> items) {
        final Map<String, ReviewItem> out = new HashMap<>();
        for (ReviewItem i : items) {
            out.put(i.key(), i);
        }
        return out;
    }

    @Test
    public void theDayOfTheAttempt() {
        assertEquals("2026-10-26", com.foobnix.assist.useit.UseDates.of(OCT_26, UTC));
    }

    @Test
    public void aRecallPutsItsIdeasInTheQueue() {
        final Recall.Attempt a = attempt(OCT_26, true, idea(1, "Dexterity grows with one task", Recall.REMEMBERED),
                idea(2, "Time is lost switching between tasks", Recall.MISSED),
                idea(3, "Narrow tasks invite machines", Recall.UNSURE), idea(4, "Not checked", ""));
        final List<ReviewItem> items = ReviewSources.fromRecall("labour#0", a, new HashMap<String, ReviewItem>(), UTC);
        assertEquals(3, items.size());
        final Map<String, ReviewItem> by = byKey(items);

        final ReviewItem missed = by.get("review:labour#0:2");
        assertEquals(ReviewItem.MISSED, missed.reason);
        assertEquals(1, missed.strength);
        // due the day after the recall
        assertEquals("2026-10-27", missed.due);
        assertEquals("Of the Division of Labour", missed.chapterTitle);
        assertEquals("The book's own words for idea 2", missed.quote);
        assertEquals(12, missed.page);
        assertEquals(OCT_26, missed.recall);
        assertEquals(1, missed.history.size());
        assertEquals(ReviewItem.RECALL, missed.history.get(0).kind);
        assertEquals("2026-10-26", missed.history.get(0).day);
        assertEquals(false, missed.history.get(0).right);

        final ReviewItem unsure = by.get("review:labour#0:3");
        assertEquals(ReviewItem.UNSURE, unsure.reason);
        assertEquals(2, unsure.strength);
        assertEquals("2026-10-27", unsure.due);

        // remembered: a longer first gap
        final ReviewItem remembered = by.get("review:labour#0:1");
        assertEquals(ReviewItem.FADING, remembered.reason);
        assertEquals(3, remembered.strength);
        assertEquals("2026-11-03", remembered.due);
        assertEquals(true, remembered.history.get(0).right);
    }

    @Test
    public void theSameAttemptIsReadOnce() {
        final Recall.Attempt a = attempt(OCT_26, true, idea(2, "Time is lost switching between tasks", Recall.MISSED));
        final Map<String, ReviewItem> existing = byKey(ReviewSources.fromRecall("labour#0", a, null, UTC));
        assertEquals(1, existing.size());
        assertTrue(ReviewSources.fromRecall("labour#0", a, existing, UTC).isEmpty());
        // an attempt not checked, or without a date, gives nothing
        final Recall.Attempt open = attempt(OCT_26, true, idea(2, "x", Recall.MISSED));
        open.state = Recall.IDEAS;
        assertTrue(ReviewSources.fromRecall("labour#0", open, null, UTC).isEmpty());
        assertTrue(ReviewSources.fromRecall("labour#0", attempt(0, true, idea(2, "x", Recall.MISSED)), null, UTC).isEmpty());
        assertTrue(ReviewSources.fromRecall("labour#0", null, null, UTC).isEmpty());
    }

    @Test
    public void aLaterAttemptMovesAnIdeaAlreadyThere() {
        final Map<String, ReviewItem> existing = byKey(ReviewSources.fromRecall("labour#0", attempt(OCT_26, true,
                idea(1, "Dexterity grows with one task", Recall.REMEMBERED),
                idea(2, "Time is lost switching between tasks", Recall.MISSED)), null, UTC));
        // the reader answered the missed one right twice since
        final ReviewItem two = existing.get("review:labour#0:2");
        ReviewSchedule.answer(two, true, "2026-10-27");
        ReviewSchedule.answer(two, true, "2026-10-30");
        assertEquals(3, two.strength);
        assertEquals("2026-11-07", two.due);

        // a second recall on Nov 1: the first idea missed now, the second remembered
        final List<ReviewItem> changed = ReviewSources.fromRecall("labour#0", attempt(OCT_26 + 6 * DAY, true,
                idea(1, "Dexterity grows with one task", Recall.MISSED),
                idea(2, "Time is lost switching between tasks", Recall.REMEMBERED)), existing, UTC);
        assertEquals(2, changed.size());
        final ReviewItem one = existing.get("review:labour#0:1");
        assertEquals(ReviewItem.MISSED, one.reason);
        assertEquals(2, one.strength);
        assertEquals("2026-11-02", one.due);
        assertEquals(2, one.history.size());
        // remembered: its history says so, its day and strength stay
        assertEquals(3, two.strength);
        assertEquals("2026-11-07", two.due);
        assertEquals(4, two.history.size());
        assertEquals(OCT_26 + 6 * DAY, two.recall);
    }

    @Test
    public void ideasOfTheChecksOwnGetTheirOwnIds() {
        final List<ReviewItem> items = ReviewSources.fromRecall("labour#0", attempt(OCT_26, false,
                idea(0, "An idea the check found", Recall.MISSED)), null, UTC);
        assertEquals("review:labour#0:r0", items.get(0).key());
    }

    @Test
    public void aChaptersIdeasAddedByHand() {
        final List<Recall.Idea> ideas = Arrays.asList(idea(1, "Dexterity grows with one task", ""),
                idea(2, "Time is lost switching between tasks", ""));
        final Map<String, ReviewItem> existing = byKey(ReviewSources.fromRecall("labour#0", attempt(OCT_26, true,
                idea(2, "Time is lost switching between tasks", Recall.MISSED)), null, UTC));
        final List<ReviewItem> added = ReviewSources.fromPassages("labour#0", "Of the Division of Labour", ideas,
                existing, "2026-11-07");
        // the one already in the queue keeps its history
        assertEquals(1, added.size());
        final ReviewItem i = added.get(0);
        assertEquals("review:labour#0:1", i.key());
        assertEquals("2026-11-07", i.due);
        assertEquals(ReviewItem.FADING, i.reason);
        assertEquals(2, i.strength);
        assertEquals(ReviewItem.ADDED, i.history.get(0).kind);
        assertEquals("The book's own words for idea 1", i.quote);
        assertTrue(ReviewSources.fromPassages("labour#0", "t", null, existing, "2026-11-07").isEmpty());
        assertTrue(ReviewSources.fromPassages("labour#0", "t", ideas, existing, "today").isEmpty());
    }

    @Test
    public void aNoteQuizzedLater() {
        final ReviewItem i = ReviewSources.fromNote("labour#0", "Of the Division of Labour",
                " Now called the Babbage principle ", "the division of labour", 14, null, "2026-11-07");
        assertNotNull(i);
        assertEquals(ReviewItem.NOTE, i.reason);
        assertEquals("Now called the Babbage principle", i.idea);
        assertEquals("2026-11-08", i.due);
        assertEquals(1, i.strength);
        assertTrue(i.id.startsWith("n"));
        assertTrue(i.key().startsWith("review:labour#0:n"));
        // the same note again: nothing new
        assertNull(ReviewSources.fromNote("labour#0", "t", "now called the Babbage principle!", "", -1, byKey(Arrays.asList(i)),
                "2026-11-09"));
        assertNull(ReviewSources.fromNote("labour#0", "t", "  ", "", -1, null, "2026-11-09"));
        assertEquals("nowcalled1832", ReviewSources.norm(" Now, called: 1832! "));
    }

    @Test
    public void shortNames() {
        assertEquals("Smith", ReviewText.shortAuthor("Adam Smith"));
        assertEquals("Smith", ReviewText.shortAuthor("Smith, Adam"));
        assertEquals("Verne", ReviewText.shortAuthor("Jules Verne; Someone Else"));
        assertEquals("", ReviewText.shortAuthor(null));
        assertEquals("Deep Work", ReviewText.who("", "Deep Work"));
        assertEquals("S", ReviewText.letter("Adam Smith", "The Wealth of Nations"));
        assertEquals("D", ReviewText.letter("", "\"Deep Work\""));
        assertEquals("?", ReviewText.letter("", ""));
        assertTrue(ReviewText.inWeeks(21));
        assertTrue(!ReviewText.inWeeks(8) && !ReviewText.inWeeks(7));
    }
}
