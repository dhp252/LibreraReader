package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FictionStoryViewTest {

    /** The reader's first page after theirs when they are on page {@code p} of the book's chapter {@code ch}. */
    private static int endIn(int ch, int p) {
        return 4 + (ch - 1) * 10 + p + 1;
    }

    @Test
    public void atThePlaceItWasBuiltForEverythingShows() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final FictionStoryView v = FictionStoryView.build(t, c, FictionWhere.of(c, t.mark.end, t.mark));
        assertTrue(v.progress);
        assertEquals(3, v.moments.size());
        assertEquals(5, v.places.size());
        assertEquals(0, v.hidden);
        // and when the reader has read on: the same, behind the page
        final FictionStoryView on = FictionStoryView.build(t, c, FictionWhere.of(c, endIn(30, 2), t.mark));
        assertTrue(on.progress);
        assertEquals(3, on.moments.size());
    }

    @Test
    public void aReaderWhoWentBackSeesOnlyTheirChaptersAndNoProgress() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        // back in the book's chapter 8: Bombay (chapter 10 of the book) and what follows are later
        final FictionWhere where = FictionWhere.of(c, endIn(8, 3), t.mark);
        assertEquals(FictionWhere.BEFORE, where.state);
        final FictionStoryView v = FictionStoryView.build(t, c, where);
        assertFalse(v.progress);
        assertEquals(1, v.moments.size());
        assertEquals("Suez", v.moments.get(0).place);
        assertEquals(2, v.places.size());
        assertEquals("Suez", v.places.get(1).name);
        assertEquals(5, v.hidden);
    }

    @Test
    public void aChipJumpsOnlyWithWordsFoundInTheBook() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        for (FictionTimeline.Moment m : t.moments) {
            m.quote = "";
        }
        t.moments.get(0).quote = "the detective had no doubt";
        t.moments.get(0).page = 71;
        final FictionStoryView v = FictionStoryView.build(t, c, FictionWhere.of(c, t.mark.end, t.mark));
        // Suez: by the moment that happens there
        final FictionStoryView.Jump suez = v.jump(t.place("Suez"));
        assertNotNull(suez);
        assertEquals("the detective had no doubt", suez.quote);
        assertEquals(71, suez.page);
        // Bombay: a moment, but its quote was not found; London: no moment
        assertNull(v.jump(t.place("Bombay")));
        assertNull(v.jump(t.place("London")));
        assertNull(v.jump(t.moments.get(1)));
        // a place's own words come first
        t.place("Suez").quote = "The steamer Mongolia was due at Suez";
        t.place("Suez").page = 70;
        assertEquals(70, v.jump(t.place("Suez")).page);
    }

    @Test
    public void aPageFoundInAnotherLayoutIsScaled() {
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        t.moments.get(0).quote = "the detective had no doubt";
        t.moments.get(0).page = 71;
        // the same book in a layout with twice the pages
        final FictionChapters twice = FictionChapters.of(null, 748);
        final FictionStoryView v = FictionStoryView.build(t, twice, FictionWhere.of(twice, 300, t.mark));
        assertEquals(142, v.jump(t.moments.get(0)).page);
        assertEquals(-1, FictionTimeline.scaled(-1, 374, 748));
        assertEquals(747, FictionTimeline.scaled(373, 374, 748) + 1);
        t.rescale(374, 187);
        assertEquals(35, t.moments.get(0).page);
        assertEquals(-1, t.moments.get(1).page);
    }

    @Test
    public void aPlaceVisitedTwiceIsOneChip() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final FictionTimeline.Place again = new FictionTimeline.Place();
        again.name = "london";
        again.ch = 15;
        again.key = c.key(15);
        t.places.add(again);
        final FictionStoryView v = FictionStoryView.build(t, c, FictionWhere.of(c, t.mark.end, t.mark));
        assertEquals(6, v.places.size());
        assertEquals(5, v.chips().size());
        assertEquals("London", v.chips().get(0).name);
    }

    @Test
    public void theBarsOfThePlan() {
        final FictionTimeline.Plan plan = FictionTimelineTest.verneTimeline().plan;
        assertEquals(13, FictionStoryView.longest(plan), 0);
        assertEquals(0.275f, FictionStoryView.done(plan), 0.0001f);
        assertEquals(0, FictionStoryView.longest(null), 0);
        assertEquals(0, FictionStoryView.done(null), 0);
        plan.now = 95;
        assertEquals(1f, FictionStoryView.done(plan), 0);
    }
}
