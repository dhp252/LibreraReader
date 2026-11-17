package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FictionMapModelTest {

    @Test
    public void threePlacesWithCoordinatesMakeAMap() {
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final FictionMapModel m = FictionMapModel.of(t.places, t, true, true);
        assertTrue(m.geographic);
        // London, Suez, Bombay are drawn; invented Pillaji and the unlocated place are not
        assertEquals("London", m.dots.get(0).name);
        assertEquals("Suez", m.dots.get(1).name);
        assertEquals("Bombay", m.dots.get(2).name);
        assertEquals(3, m.route.size());
        // the story is now in a place without coordinates: the map does not mark it at the last one that has
        assertEquals(-1, m.here);
        assertEquals(2, FictionMapModel.of(t.places.subList(0, 3), t, true, true).here);
        for (FictionMapModel.Dot d : m.dots) {
            assertFalse("Pillaji".equals(d.name));
        }
        // the plan's legs between places with coordinates, the stops ahead added as plan-only dots
        assertEquals(5, m.plan.size());
        assertEquals(6, m.dots.size());
        assertEquals("Calcutta", m.dots.get(3).name);
        assertFalse(m.dots.get(3).visited);
        assertEquals("Yokohama", m.dots.get(5).name);
        assertEquals(0, m.plan.get(0)[0]);
        assertEquals(1, m.plan.get(0)[1]);
    }

    @Test
    public void withoutThePlanOrHere() {
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final FictionMapModel m = FictionMapModel.of(t.places, t, false, false);
        assertTrue(m.plan.isEmpty());
        assertEquals(3, m.dots.size());
        assertEquals(-1, m.here);
    }

    @Test
    public void fewerThanThreeLocatedPlacesMakeASchematicRouteOfAllPlaces() {
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final List<FictionTimeline.Place> two = new ArrayList<>(t.places.subList(0, 2));
        two.add(t.place("Pillaji"));
        final FictionMapModel m = FictionMapModel.of(two, t, true, true);
        assertFalse(m.geographic);
        assertEquals(3, m.dots.size());
        assertEquals("Pillaji", m.dots.get(2).name);
        assertEquals(3, m.route.size());
        assertEquals(2, m.here);
        assertTrue(m.plan.isEmpty());
        final FictionMapModel none = FictionMapModel.of(new ArrayList<FictionTimeline.Place>(), t, true, true);
        assertFalse(none.geographic);
        assertEquals(-1, none.here);
    }

    @Test
    public void aPlaceComeBackToIsOneDotOnTheMap() {
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final List<FictionTimeline.Place> round = new ArrayList<>(t.places.subList(0, 3));
        round.add(t.places.get(0));
        final FictionMapModel m = FictionMapModel.of(round, t, false, true);
        assertEquals(3, m.dots.size());
        assertEquals(4, m.route.size());
        assertEquals(0, (int) m.route.get(3));
        assertEquals(0, m.here);
    }
}
