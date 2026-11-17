package com.foobnix.assist.zoom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ZoomGoalTest {

    @Test
    public void wordsLeftAsTheBooksReadingGoalAreNotKeptTwice() throws Exception {
        final ZoomGoal g = ZoomGoal.chosen(ZoomGoal.Kind.WORK, " negotiate  a raise ", "negotiate a raise");
        assertNull(g.text);
        assertFalse(g.toJson().has("text"));
        assertEquals("negotiate a raise", g.words("negotiate a raise"));
        // the book's goal changed later: the zoom goal follows it
        assertEquals("lead a team", g.words(" lead a team "));
        assertEquals("work|lead a team", g.id("lead a team"));
        assertEquals("work", g.id(null));
    }

    @Test
    public void wordsTypedForZoomAreKeptHere() throws Exception {
        final ZoomGoal g = ZoomGoal.chosen(ZoomGoal.Kind.DISCUSS, "our book club", "negotiate a raise");
        assertEquals("our book club", g.text);
        assertEquals("our book club", g.words("negotiate a raise"));
        final ZoomGoal back = ZoomGoal.fromJson(new JSONObject(g.toJson().toString()));
        assertEquals(ZoomGoal.Kind.DISCUSS, back.kind);
        assertEquals("our book club", back.text);
        assertEquals(g.id("x"), back.id("y"));
    }

    @Test
    public void clearedWordsStayClearedThoughTheBookHasAGoal() throws Exception {
        final ZoomGoal g = ZoomGoal.chosen(ZoomGoal.Kind.IDEAS, "  ", "negotiate a raise");
        assertEquals("", g.text);
        assertEquals("", g.words("negotiate a raise"));
        assertEquals("ideas", g.id("negotiate a raise"));
        final ZoomGoal back = ZoomGoal.fromJson(new JSONObject(g.toJson().toString()));
        assertEquals("", back.text);
        // no words anywhere: nothing typed, the book has no goal
        assertNull(ZoomGoal.chosen(ZoomGoal.Kind.IDEAS, "", null).text);
    }

    @Test
    public void theStoredGoal() throws Exception {
        assertNull(ZoomGoal.fromJson(null));
        assertNull(ZoomGoal.fromJson(new JSONObject("{\"t\": 5}")));
        final ZoomGoal follows = ZoomGoal.fromJson(new JSONObject("{\"kind\": \"study\", \"t\": 5}"));
        assertEquals(ZoomGoal.Kind.STUDY, follows.kind);
        assertNull(follows.text);
        assertEquals(ZoomGoal.Kind.IDEAS, ZoomGoal.fromJson(new JSONObject("{\"kind\": \"unknown\"}")).kind);
        assertNull(ZoomGoal.fromJson(new JSONObject("{\"kind\": \"work\", \"text\": null}")).text);
    }

    @Test
    public void theRequestStatesTheGoal() {
        for (ZoomGoal.Kind k : ZoomGoal.Kind.values()) {
            final String p = new ZoomGoal(k, null).prompt("");
            assertTrue(p.startsWith("The reader "));
            assertFalse(p.contains("own words"));
        }
        assertNotEquals(new ZoomGoal(ZoomGoal.Kind.IDEAS, null).prompt(""), new ZoomGoal(ZoomGoal.Kind.STUDY, null).prompt(""));
        assertTrue(new ZoomGoal(ZoomGoal.Kind.WORK, null).prompt("lead a team").endsWith("they read it for: lead a team"));
    }

    @Test
    public void longWordsAreCut() {
        final StringBuilder s = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            s.append("word ");
        }
        assertTrue(new ZoomGoal(ZoomGoal.Kind.IDEAS, s.toString()).text.length() <= ZoomGoal.MAX_WORDS);
    }
}
