package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.review.ReviewItem;
import com.foobnix.assist.review.ReviewSchedule;
import com.foobnix.assist.review.ReviewSources;
import com.foobnix.assist.zoom.ZoomData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadsSpotsTest {

    private static ZoomData zoom() {
        final ZoomData z = new ZoomData();
        z.pages = 10;
        final ZoomData.Section a = new ZoomData.Section();
        a.line = "Splitting work raises output.";
        a.start = "The greatest improvement";
        a.page = 0;
        final ZoomData.Section b = new ZoomData.Section();
        b.line = "Ten workers made 48,000 pins a day.";
        b.start = "To take an example";
        b.page = 4;
        z.sections.add(a);
        z.sections.add(b);
        return z;
    }

    private static List<Recall.Idea> passages() {
        return new ArrayList<>(Arrays.asList(
                new Recall.Idea(3, "Dexterity grows with repetition").add("the increase of dexterity", 27),
                new Recall.Idea(5, "Machines come from specialists").add("the invention of machines", 29)));
    }

    @Test
    public void aKeyPassageOnThePageIsTheSpotsIdea() {
        // the chapter starts on the book's page 20: its page 7 is the book's 27
        final Recall.Idea idea = ReadsSpots.ideaAt(7, 10, 20, passages(), zoom());
        assertNotNull(idea);
        assertEquals(3, idea.id);
        assertEquals("Dexterity grows with repetition", idea.text);
        assertEquals("the increase of dexterity", idea.quote());
        assertEquals(27, idea.page());
    }

    @Test
    public void elseTheZoomSectionsLineIsUsed() {
        final Recall.Idea idea = ReadsSpots.ideaAt(5, 10, 20, passages(), zoom());
        assertNotNull(idea);
        assertEquals(-1, idea.id);
        assertEquals("Ten workers made 48,000 pins a day.", idea.text);
        assertEquals("To take an example", idea.quote());
        assertEquals(25, idea.page());
        // in another layout the same place in the chapter gives the same section
        assertEquals("Ten workers made 48,000 pins a day.", ReadsSpots.ideaAt(10, 20, 40, null, zoom()).text);
        assertEquals("Splitting work raises output.", ReadsSpots.ideaAt(6, 20, 40, null, zoom()).text);
    }

    @Test
    public void aPageWithNothingStoredHasNoIdea() {
        assertNull(ReadsSpots.ideaAt(5, 10, 20, passages(), null));
        assertNull(ReadsSpots.ideaAt(5, 10, 20, null, null));
        // a zoom section that only starts later says nothing about an earlier page
        final ZoomData late = new ZoomData();
        late.pages = 10;
        final ZoomData.Section s = new ZoomData.Section();
        s.line = "Later.";
        s.page = 6;
        late.sections.add(s);
        assertNull(ReadsSpots.ideaAt(2, 10, 0, null, late));
        assertNotNull(ReadsSpots.ideaAt(6, 10, 0, null, late));
    }

    @Test
    public void spotsGiveEachIdeaOnceAndCountThoseWithout() {
        final ReadsSignals s = new ReadsSignals();
        s.pages = 10;
        s.at(1).seconds = 40;
        s.at(4).rereads = 1;
        s.at(5).longs = 1;
        s.at(7).helps = 1;
        s.at(9).drifts = 1;
        final ReadsSpots.Result r = ReadsSpots.ideas(s, 20, passages(), zoom());
        assertEquals(4, r.spots);
        assertEquals(0, r.without);
        // pages 4 and 5 are in one zoom section: its line once; pages 7 and 9 have a key passage each
        assertEquals(3, r.ideas.size());
        assertEquals(-1, r.ideas.get(0).id);
        assertEquals(3, r.ideas.get(1).id);
        assertEquals(5, r.ideas.get(2).id);

        final ReadsSpots.Result none = ReadsSpots.ideas(s, 20, null, null);
        assertEquals(4, none.spots);
        assertEquals(4, none.without);
        assertTrue(none.ideas.isEmpty());
        assertEquals(0, ReadsSpots.ideas(null, 0, null, null).spots);
    }

    @Test
    public void spotIdeasEnterTheReviewQueueDueTomorrowOnce() {
        final ReadsSignals s = new ReadsSignals();
        s.pages = 10;
        s.at(5).longs = 1;
        s.at(7).helps = 1;
        final List<Recall.Idea> ideas = ReadsSpots.ideas(s, 20, passages(), zoom()).ideas;
        final Map<String, ReviewItem> existing = new HashMap<>();
        final List<ReviewItem> items = ReviewSources.fromSpots("ch#0", "Chapter", ideas, existing, "2026-11-17");
        assertEquals(2, items.size());
        final ReviewItem line = items.get(0);
        assertEquals("ch#0", line.chapterKey);
        assertTrue(line.id.startsWith("s"));
        assertEquals("Ten workers made 48,000 pins a day.", line.idea);
        assertEquals("To take an example", line.quote);
        assertEquals(25, line.page);
        assertEquals(ReviewItem.FADING, line.reason);
        assertEquals(1, line.strength);
        assertEquals("2026-11-18", line.due);
        assertEquals(ReviewSchedule.FIRST_GAP, line.gap);
        assertEquals(1, line.history.size());
        // a key passage keeps its own id: one already in the queue is not added again
        assertEquals("3", items.get(1).id);
        for (ReviewItem i : items) {
            existing.put(i.key(), i);
        }
        assertTrue(ReviewSources.fromSpots("ch#0", "Chapter", ideas, existing, "2026-11-18").isEmpty());
        assertTrue(ReviewSources.fromSpots("ch#0", "Chapter", ideas, existing, "not a day").isEmpty());
        assertTrue(ReviewSources.fromSpots(null, "Chapter", ideas, null, "2026-11-18").isEmpty());
    }

    @Test
    public void theSameIdeaTwiceInAListIsAddedOnce() {
        final List<Recall.Idea> twice = Arrays.asList(new Recall.Idea(-1, "One line."), new Recall.Idea(-1, "One line."),
                new Recall.Idea(-1, ""));
        assertEquals(1, ReviewSources.fromSpots("k#0", "", twice, null, "2026-11-17").size());
    }
}
