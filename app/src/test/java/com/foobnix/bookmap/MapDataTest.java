package com.foobnix.bookmap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapDataTest {

    private static MapBook.Chapter chapter(MapBook b, int n, int from, MapBook.Moment... moments) {
        final MapBook.Chapter c = b.chapterOrNew("c" + n);
        c.n = n;
        c.title = "Chapter " + n;
        c.from = from;
        c.to = from + 10;
        c.chars = 6000;
        c.threads.add(new MapBook.Thread("Gatsby", Collections.singletonList("Jay"), "host"));
        c.threads.add(new MapBook.Thread("Daisy", null, "cousin"));
        c.moments.addAll(Arrays.asList(moments));
        c.t = 10;
        return c;
    }

    private static MapBook.Moment m(String kind, String... threads) {
        return new MapBook.Moment(Arrays.asList(threads), kind, "label", "quote", 0, 0.5);
    }

    private static MapBook sample() {
        final MapBook b = new MapBook();
        chapter(b, 1, 0, m(MapBook.INTRODUCE, "Gatsby"), m(MapBook.DEVELOP, "jay ", "Daisy"), m(MapBook.DEVELOP, "Owl Eyes"));
        chapter(b, 2, 10, m(MapBook.TURN, "Daisy"), m(MapBook.DEVELOP, "Gatsby"));
        b.chapterOrNew("pics").noText = true;
        b.premise = "A man wants the past back.";
        b.lanes.add(new MapBook.Lane("gatsby", "Gatsby", "the dreamer", Arrays.asList("Gatsby", "Jay Gatsby")));
        b.lanes.add(new MapBook.Lane("daisy", "Daisy", "the girl", Collections.singletonList("DAISY")));
        b.arcs.add(new MapBook.Arc("1.1", "2.1", "pays_off", "why"));
        b.arcs.add(new MapBook.Arc("1.1", "9.9", "pays_off", "a dropped moment"));
        b.key.addAll(Arrays.asList("2.1", "7.7"));
        b.seen.addAll(Arrays.asList("c1", "c2"));
        b.t = 20;
        return b;
    }

    @Test
    public void placesMomentsOnTheLanesOfTheNamesTheyMerged() throws Exception {
        final JSONObject d = MapData.join("Gatsby", sample(), 2, false);
        assertEquals("Gatsby", d.getString("title"));
        assertEquals(2, d.getInt("reader"));
        assertFalse(d.getBoolean("whole"));
        assertEquals(2, d.getJSONArray("lanes").length());
        assertEquals("the chapter without text is left out", 2, d.getJSONArray("chapters").length());
        assertEquals(1000, d.getJSONArray("chapters").getJSONObject(0).getLong("words"));
        final JSONArray moments = d.getJSONArray("moments");
        assertEquals(5, moments.length());
        assertEquals("1.2", moments.getJSONObject(1).getString("id"));
        // "jay " is an alias of the chapter's Gatsby, merged by the lane; Daisy matched in any case
        assertEquals(Arrays.asList("gatsby", "daisy"), list(moments.getJSONObject(1).getJSONArray("lanes")));
        // a thread no lane merged is Other
        assertEquals(0, moments.getJSONObject(2).getJSONArray("lanes").length());
        assertEquals("Owl Eyes", moments.getJSONObject(2).getJSONArray("others").getString(0));
        assertEquals(1, d.getJSONArray("arcs").length());
        assertEquals(Collections.singletonList("2.1"), list(d.getJSONArray("key")));
        assertFalse(d.getBoolean("needsUpdate"));
    }

    @Test
    public void withoutKeyMomentsFirstTurnsAndPayoffsStandIn() throws Exception {
        final MapBook b = sample();
        b.key.clear();
        assertEquals(Arrays.asList("1.1", "2.1"), list(MapData.join("G", b, 0, true).getJSONArray("key")));
    }

    @Test
    public void spoilerCutFollowsTheReaderUnlessTheBookIsStudiedOrTheReaderChose() {
        final MapBook b = new MapBook();
        assertFalse(MapData.startWhole(b, false));
        assertTrue(MapData.startWhole(b, true));
        b.whole = false;
        assertFalse(MapData.startWhole(b, true));
        b.whole = true;
        assertTrue(MapData.startWhole(b, false));
        assertEquals(4, MapData.cut(4, false));
        assertEquals(0, MapData.cut(4, true));
        assertEquals(0, MapData.cut(0, false));
    }

    @Test
    public void findsTheReadersChapterByKeyThenByPage() {
        final MapBook b = sample();
        assertEquals(2, MapData.chapterAt(b, "c2", 0));
        assertEquals(1, MapData.chapterAt(b, null, 5));
        assertEquals(2, MapData.chapterAt(b, "unknown", 15));
        assertEquals(2, MapData.chapterAt(b, null, 500));
        assertEquals(0, MapData.chapterAt(new MapBook(), null, 3));
    }

    @Test
    public void newOrChangedChaptersAskForAnUpdate() {
        final MapBook b = sample();
        assertFalse(MapData.needsUpdate(b));
        chapter(b, 3, 20, m(MapBook.DEVELOP, "Gatsby"));
        assertTrue(MapData.needsUpdate(b));
        b.seen.add("c3");
        assertFalse(MapData.needsUpdate(b));
        b.chapter("c1").t = 30;
        assertTrue(MapData.needsUpdate(b));
        assertFalse("without the whole-book step there is nothing to update", MapData.needsUpdate(new MapBook()));
    }

    @Test
    public void countsAndEstimates() {
        final MapBook b = sample();
        b.chapter("c1").dropped = 2;
        b.chapter("c2").dropped = 1;
        assertEquals(3, MapData.dropped(b));
        assertEquals(5, MapData.moments(b));
        // 300 pages of about 1,800 characters in parts of 20,000: 27 requests and the whole-book one
        assertEquals(28, MapData.estimate(300, 20000));
        assertEquals(2, MapData.estimate(0, 20000));
    }

    private static java.util.List<String> list(JSONArray a) throws Exception {
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getString(i));
        }
        return out;
    }
}
