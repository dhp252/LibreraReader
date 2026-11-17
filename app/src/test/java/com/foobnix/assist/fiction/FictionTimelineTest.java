package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class FictionTimelineTest {

    static final String ANSWER = "{\"story\": true, \"moments\": ["
            + "{\"ch\": 7, \"time\": \"Day 7\", \"place\": \"Suez\", \"line\": \"Fix spots Fogg and starts the chase\", "
            + "\"quote\": \"the detective had no doubt that this was his man\"},"
            + "{\"ch\": 11, \"time\": \"Day 18\", \"place\": \"Bombay\", \"line\": \"Passepartout's trouble at the pagoda\", \"quote\": \"invented words that are not there at all\"},"
            + "{\"ch\": 14, \"time\": \"\", \"place\": \"Pillaji\", \"line\": \"Aouda rescued at dawn\"},"
            + "{\"ch\": 33, \"time\": \"Day 75\", \"place\": \"Atlantic\", \"line\": \"Fogg buys the Henrietta\"}],"
            + "\"places\": ["
            + "{\"name\": \"London\", \"ch\": 2, \"real\": true, \"lat\": 51.5, \"lon\": -0.13},"
            + "{\"name\": \"Suez\", \"ch\": 7, \"real\": true, \"lat\": \"29.97\", \"lon\": \"32.55\"},"
            + "{\"name\": \"Bombay\", \"ch\": 10, \"real\": \"true\", \"lat\": 18.97, \"lon\": 72.83},"
            + "{\"name\": \"Pillaji\", \"ch\": 13, \"real\": false, \"lat\": 24.0, \"lon\": 80.0},"
            + "{\"name\": \"Nowhere\", \"ch\": 13, \"real\": true, \"lat\": 124.0, \"lon\": 80.0},"
            + "{\"name\": \"Hong Kong\", \"ch\": 19, \"real\": true, \"lat\": 22.3, \"lon\": 114.2}],"
            + "\"here\": {\"time\": \"Day 22\", \"line\": \"On the train from Benares to Calcutta\"},"
            + "\"plan\": {\"what\": \"Round the world in eighty days\", \"source\": \"the Morning Chronicle's estimate\", "
            + "\"source_ch\": 4, \"unit\": \"Day\", \"total\": 80, \"now\": 22, \"status\": \"On schedule\", "
            + "\"note\": \"Two days gained by Bombay were spent crossing India.\", \"legs\": ["
            + "{\"from\": \"London\", \"to\": \"Suez\", \"plan\": 7, \"actual\": 6.5, \"state\": \"done\"},"
            + "{\"from\": \"Suez\", \"to\": \"Bombay\", \"plan\": 13, \"actual\": \"11\"},"
            + "{\"from\": \"Bombay\", \"to\": \"Calcutta\", \"plan\": 3, \"actual\": 4.5, \"state\": \"here\", \"note\": \"elephant\"},"
            + "{\"from\": \"Calcutta\", \"to\": \"Hong Kong\", \"plan\": 13, \"actual\": 12, \"state\": \"done\", \"note\": \"a storm\"},"
            + "{\"from\": \"Hong Kong\", \"to\": \"Yokohama\", \"plan\": 6}]},"
            + "\"plan_places\": [{\"name\": \"Calcutta\", \"real\": true, \"lat\": 22.57, \"lon\": 88.36},"
            + "{\"name\": \"Hong Kong\", \"real\": true, \"lat\": 22.3, \"lon\": 114.2},"
            + "{\"name\": \"Yokohama\", \"real\": true, \"lat\": 35.44, \"lon\": 139.64}],"
            + "\"next\": \"Next, by the plan: steamer from Calcutta to Hong Kong, 13 days.\"}";

    static FictionTimeline verneTimeline() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = new FictionTimeline();
        t.merge(FictionTimeline.parse(ANSWER, c, 15));
        t.mark = FictionMark.at(c, 4 + 13 * 10 + 4);
        return t;
    }

    @Test
    public void whatLiesAfterTheTextIsDropped() {
        final FictionTimeline t = FictionTimeline.parse(ANSWER, FictionChaptersTest.verne(), 15);
        assertNotNull(t);
        // the Henrietta (chapter 33) and Hong Kong (19) are after chapter 15
        assertEquals(3, t.moments.size());
        assertEquals("Aouda rescued at dawn", t.moments.get(2).line);
        assertEquals(5, t.places.size());
        assertNull(t.place("Hong Kong") == null ? null : (t.places.contains(t.place("Hong Kong")) ? "visited" : null));
        assertEquals("Day 7", t.moments.get(0).time);
        assertEquals("Suez", t.moments.get(0).place);
        assertEquals(FictionChaptersTest.verne().key(7), t.moments.get(0).key);
    }

    @Test
    public void legsAfterTheOneTheStoryIsOnCarryNoActual() {
        final FictionTimeline.Plan plan = FictionTimeline.parse(ANSWER, FictionChaptersTest.verne(), 15).plan;
        assertNotNull(plan);
        assertEquals("day", plan.unit);
        assertEquals(80, plan.total, 0);
        assertEquals(22, plan.now, 0);
        assertEquals(5, plan.legs.size());
        assertEquals(FictionTimeline.DONE, plan.legs.get(0).state);
        assertEquals(6.5, plan.legs.get(0).actual, 0);
        // no state given, an actual given: done
        assertEquals(FictionTimeline.DONE, plan.legs.get(1).state);
        assertEquals(11, plan.legs.get(1).actual, 0);
        assertEquals(FictionTimeline.HERE, plan.legs.get(2).state);
        assertEquals("elephant", plan.legs.get(2).note);
        // the model says the next leg took 12 days in a storm: that is after the page
        assertEquals(FictionTimeline.AHEAD, plan.legs.get(3).state);
        assertEquals(-1, plan.legs.get(3).actual, 0);
        assertEquals("", plan.legs.get(3).note);
        assertEquals(13, plan.legs.get(3).plan, 0);
        assertEquals(FictionTimeline.AHEAD, plan.legs.get(4).state);
    }

    @Test
    public void aPlanStatedAfterTheTextIsNoPlan() {
        final FictionTimeline t = FictionTimeline.parse(ANSWER.replace("\"source_ch\": 4", "\"source_ch\": 20"),
                FictionChaptersTest.verne(), 15);
        assertNull(t.plan);
        assertTrue(t.stops.isEmpty());
        assertEquals("", t.next);
        // and a book without one has only moments and places
        final FictionTimeline none = FictionTimeline.parse("{\"moments\": [{\"ch\": 1, \"line\": \"x\"}], \"plan\": null, "
                + "\"next\": \"Next, by the plan: nothing\"}", FictionChaptersTest.verne(), 15);
        assertNull(none.plan);
        assertEquals("", none.next);
        assertEquals(1, none.moments.size());
    }

    @Test
    public void onlyRealPlacesWithSaneNumbersGetCoordinates() {
        final FictionTimeline t = FictionTimeline.parse(ANSWER, FictionChaptersTest.verne(), 15);
        assertTrue(t.place("London").located);
        assertEquals(51.5, t.place("London").lat, 0);
        assertTrue(t.place("Suez").located);
        assertEquals(32.55, t.place("Suez").lon, 0);
        assertTrue(t.place("Bombay").real);
        // invented: no coordinates, whatever the model sent
        assertFalse(t.place("Pillaji").located);
        assertEquals(0, t.place("Pillaji").lat, 0);
        // a latitude of 124 is no place on the globe
        assertFalse(t.place("Nowhere").located);
        assertTrue(t.place("Yokohama").located);
        assertFalse(t.places.contains(t.place("Yokohama")));
    }

    @Test
    public void theStoredItemReadsBackTheSame() throws Exception {
        final FictionTimeline t = verneTimeline();
        t.moments.get(0).quote = "the detective had no doubt";
        t.moments.get(0).page = 71;
        final FictionTimeline back = FictionTimeline.fromJson(new JSONObject(t.toJson().toString()));
        assertEquals(3, back.moments.size());
        assertEquals("the detective had no doubt", back.moments.get(0).quote);
        assertEquals(71, back.moments.get(0).page);
        assertEquals(5, back.places.size());
        assertTrue(back.place("London").located);
        assertFalse(back.place("Pillaji").located);
        assertEquals(3, back.stops.size());
        assertEquals(5, back.plan.legs.size());
        assertEquals(4.5, back.plan.legs.get(2).actual, 0);
        assertEquals(-1, back.plan.legs.get(4).actual, 0);
        assertEquals("the Morning Chronicle's estimate", back.plan.source);
        assertEquals("On the train from Benares to Calcutta", back.here);
        assertEquals("Day 22", back.hereTime);
        assertTrue(back.next.startsWith("Next, by the plan"));
        assertEquals(15, back.mark.ch);
        assertTrue(FictionTimeline.fromJson(null).isEmpty());
    }

    @Test
    public void anUpdateAddsMomentsAndPlacesAndReplacesThePlanAndHere() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionTimeline t = verneTimeline();
        final String update = "{\"moments\": [{\"ch\": 16, \"time\": \"Day 23\", \"place\": \"Calcutta\", \"line\": \"Arrested at the station\"},"
                + "{\"ch\": 14, \"line\": \"Aouda rescued at dawn\"}],"
                + "\"places\": [{\"name\": \"nowhere\", \"ch\": 15, \"real\": false},"
                + "{\"name\": \"Calcutta\", \"ch\": 16, \"real\": true, \"lat\": 22.57, \"lon\": 88.36}],"
                + "\"here\": \"In court at Calcutta\","
                + "\"plan\": {\"what\": \"Round the world in eighty days\", \"source_ch\": 4, \"unit\": \"day\", \"total\": 80, \"now\": 23,"
                + "\"legs\": [{\"from\": \"London\", \"to\": \"Suez\", \"plan\": 7, \"actual\": 6.5, \"state\": \"done\"}]},"
                + "\"plan_places\": [{\"name\": \"hong kong\", \"real\": true, \"lat\": 22.31, \"lon\": 114.17}]}";
        t.merge(FictionTimeline.parse(update, c, 17));
        assertEquals(4, t.moments.size());
        assertEquals("Arrested at the station", t.moments.get(3).line);
        // the place the story was last in, named again, is one stay; Calcutta is new
        assertEquals(6, t.places.size());
        assertEquals("Calcutta", t.places.get(5).name);
        assertEquals(23, t.plan.now, 0);
        assertEquals(1, t.plan.legs.size());
        // a stop of the plan given again takes the new numbers; the others stay
        assertEquals(3, t.stops.size());
        assertEquals(114.17, t.stops.get(1).lon, 0);
        assertEquals("In court at Calcutta", t.here);
        assertEquals("", t.hereTime);
        assertEquals("", t.next);
        // an update without a plan keeps the plan
        t.merge(FictionTimeline.parse("{\"moments\": [{\"ch\": 17, \"line\": \"Bail is paid\"}]}", c, 17));
        assertNotNull(t.plan);
        assertEquals("", t.here);
    }

    @Test
    public void amountsAndLegTexts() {
        assertEquals("6½", FictionTimeline.amount(6.5));
        assertEquals("7", FictionTimeline.amount(7));
        assertEquals("½", FictionTimeline.amount(0.5));
        assertEquals("4.3", FictionTimeline.amount(4.3));
        final FictionTimeline.Plan plan = verneTimeline().plan;
        assertEquals("6½ of 7 d", FictionTimeline.legText(plan.legs.get(0), plan.unit));
        assertEquals("4½ of 3 d", FictionTimeline.legText(plan.legs.get(2), plan.unit));
        assertEquals("13 d", FictionTimeline.legText(plan.legs.get(3), plan.unit));
        assertEquals("Day 22", FictionTimeline.count(plan));
        assertEquals("", FictionTimeline.count(null));
        assertEquals("h", FictionTimeline.unitShort("Hours"));
        assertEquals("moons", FictionTimeline.unitShort("moons"));
    }

    @Test
    public void numbersAreReadFromWords() throws Exception {
        assertEquals(6.5, FictionTimeline.number(new JSONObject("{\"a\": \"6½ days\"}"), "a"), 0);
        assertEquals(6.5, FictionTimeline.number(new JSONObject("{\"a\": \"6,5\"}"), "a"), 0);
        assertEquals(-0.13, FictionTimeline.number(new JSONObject("{\"a\": \"-0.13\"}"), "a"), 1e-9);
        assertTrue(Double.isNaN(FictionTimeline.number(new JSONObject("{\"a\": \"soon\"}"), "a", "b")));
    }

    @Test
    public void anUnreadableAnswerIsNullAndANonStorySaysSo() {
        assertNull(FictionTimeline.parse("no", FictionChaptersTest.verne(), 15));
        assertFalse(FictionTimeline.parse("{\"story\": false}", FictionChaptersTest.verne(), 15).story);
    }
}
