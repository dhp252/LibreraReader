package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ReadsSignalsTest {

    private static ReadsDelta stay(int page, int seconds, int words) {
        final ReadsDelta d = new ReadsDelta(page);
        d.seconds = seconds;
        d.words = words;
        return d;
    }

    @Test
    public void keyNamesTheChapter() {
        assertEquals("reads:signals:of the division of labour#0", ReadsSignals.key("of the division of labour#0"));
    }

    @Test
    public void aStayAddsItsSecondsAndKeepsTheLatestWords() {
        final ReadsSignals s = new ReadsSignals();
        s.fit(10);
        s.apply(2, stay(12, 60, 300));
        s.apply(2, stay(12, 30, 310));
        final ReadsDelta reread = new ReadsDelta(12);
        reread.rereads = 1;
        s.apply(2, reread);
        final ReadsSignals.Page p = s.peek(2);
        assertEquals(90, p.seconds);
        assertEquals(310, p.words);
        assertEquals(1, p.rereads);
        assertTrue(p.isSpot());
        assertEquals(1, s.spots());
        assertNull(s.peek(3));
    }

    @Test
    public void aPagePastTheChaptersEndGoesToItsLastPage() {
        final ReadsSignals s = new ReadsSignals();
        s.fit(4);
        s.apply(9, stay(0, 10, 0));
        assertEquals(10, s.peek(3).seconds);
    }

    @Test
    public void jsonRoundTripKeepsEveryCounter() throws Exception {
        final ReadsSignals s = new ReadsSignals();
        s.title = "Of the Division of Labour";
        s.fit(12);
        final ReadsDelta d = stay(5, 95, 310);
        d.rereads = 2;
        d.longs = 1;
        d.offers = 3;
        d.helps = 1;
        d.breaks = 1;
        d.drifts = 2;
        s.apply(5, d);
        s.apply(0, stay(0, 40, 280));
        final ReadsSignals back = ReadsSignals.fromJson(new JSONObject(s.toJson().toString()));
        assertEquals("Of the Division of Labour", back.title);
        assertEquals(12, back.pages);
        assertEquals(2, back.byPage.size());
        final ReadsSignals.Page p = back.peek(5);
        assertEquals(95, p.seconds);
        assertEquals(310, p.words);
        assertEquals(2, p.rereads);
        assertEquals(1, p.longs);
        assertEquals(3, p.offers);
        assertEquals(1, p.helps);
        assertEquals(1, p.breaks);
        assertEquals(2, p.drifts);
        assertEquals(135, back.total().seconds);
    }

    @Test
    public void onlyCountersAreStored() throws Exception {
        final ReadsSignals s = new ReadsSignals();
        s.fit(3);
        s.apply(1, stay(1, 30, 200));
        final JSONObject o = s.toJson();
        assertEquals(3, o.length());
        assertTrue(o.has("title"));
        assertTrue(o.has("pages"));
        final JSONObject page = o.getJSONObject("p").getJSONObject("1");
        // zero counters are left out
        assertEquals(2, page.length());
        assertEquals(30, page.getInt("s"));
        assertEquals(200, page.getInt("w"));
    }

    @Test
    public void badlyFormedItemsGiveEmptySignals() throws Exception {
        assertTrue(ReadsSignals.fromJson(null).isEmpty());
        final JSONObject o = new JSONObject("{\"pages\":4,\"p\":{\"x\":{\"s\":5},\"9\":{\"s\":5},\"2\":{\"s\":-4},\"1\":{\"s\":7}}}");
        final ReadsSignals s = ReadsSignals.fromJson(o);
        assertEquals(1, s.byPage.size());
        assertEquals(7, s.peek(1).seconds);
    }

    @Test
    public void aCoarserLayoutAddsThePagesThatFallTogether() {
        final ReadsSignals s = new ReadsSignals();
        s.fit(8);
        for (int p = 0; p < 8; p++) {
            s.apply(p, stay(p, 10, 100));
        }
        final ReadsDelta d = new ReadsDelta(7);
        d.drifts = 1;
        s.apply(7, d);
        s.fit(4);
        assertEquals(4, s.pages);
        assertEquals(4, s.byPage.size());
        assertEquals(20, s.peek(0).seconds);
        assertEquals(200, s.peek(0).words);
        assertEquals(1, s.peek(3).drifts);
        assertEquals(80, s.total().seconds);
    }

    @Test
    public void aFinerLayoutKeepsEachPageAsFarIntoTheChapter() {
        final ReadsSignals s = new ReadsSignals();
        s.fit(4);
        s.apply(0, stay(0, 10, 100));
        s.apply(3, stay(3, 40, 100));
        s.fit(8);
        assertEquals(8, s.pages);
        assertEquals(10, s.peek(1).seconds);
        assertEquals(40, s.peek(7).seconds);
        assertEquals(50, s.total().seconds);
    }

    @Test
    public void theSameLayoutChangesNothing() {
        final ReadsSignals s = new ReadsSignals();
        s.fit(6);
        s.apply(5, stay(5, 10, 100));
        s.fit(6);
        s.fit(0);
        assertEquals(6, s.pages);
        assertEquals(10, s.peek(5).seconds);
    }

    @Test
    public void aVisitBecomesADelta() {
        final ReadsModel m = new ReadsModel(250);
        m.tick(0, true);
        m.page(0, 7, 6, 300);
        m.tick(1000, true);
        m.tick(2400, true);
        m.page(2400, 8, 7, 300);
        final ReadsDelta d = ReadsDelta.of(m.drain().get(0));
        assertEquals(6, d.page);
        assertEquals(2, d.seconds);
        assertEquals(300, d.words);
        assertFalse(d.isEmpty());
        assertTrue(new ReadsDelta(1).isEmpty());
    }

    @Test
    public void stateRoundTrip() throws Exception {
        final ReadsState s = new ReadsState();
        s.pace = 251.337;
        s.paceSamples = 23;
        s.snoozeUntil = 1_800_000_000_000L;
        s.breaksOffered = 3;
        s.breaksTaken = 1;
        s.listens = 2;
        final ReadsState back = ReadsState.fromJson(new JSONObject(s.toJson().toString()));
        assertEquals(251.3, back.pace, 0.0001);
        assertEquals(23, back.paceSamples);
        assertEquals(1_800_000_000_000L, back.snoozeUntil);
        assertEquals(3, back.breaksOffered);
        assertEquals(1, back.breaksTaken);
        assertEquals(2, back.listens);
    }

    @Test
    public void aPaceThatCannotBeReadingIsDropped() throws Exception {
        assertEquals(0, ReadsState.fromJson(new JSONObject("{\"pace\":5}")).pace, 0);
        assertEquals(0, ReadsState.fromJson(new JSONObject("{\"pace\":\"x\"}")).pace, 0);
        assertEquals(0, ReadsState.fromJson(null).pace, 0);
    }
}
