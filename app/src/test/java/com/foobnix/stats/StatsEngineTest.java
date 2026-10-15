package com.foobnix.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/** The measurement rules of F-007 on made-up event streams. */
public class StatsEngineTest {

    /** 2026-10-12 08:00 UTC, a Monday. */
    static final long T0 = Days.of(2026, 10, 12) * 86_400_000L + 8 * 3_600_000L;
    static final long MIN = 60_000L;
    static final long SEC = 1_000L;

    static StatsEvent open(long ts, String book) {
        final StatsEvent e = new StatsEvent(StatsEvent.OPEN, ts, 0, book);
        e.title = "Title " + book;
        e.lang = "en";
        e.ext = "epub";
        e.pages = 100;
        e.percent = 0;
        return e;
    }

    static StatsEvent page(long leftAt, String book, int page, int words, long raw) {
        return page(leftAt, book, page, words, raw, StatsEvent.MODE_READ);
    }

    static StatsEvent page(long leftAt, String book, int page, int words, long raw, String mode) {
        final StatsEvent e = new StatsEvent(StatsEvent.PAGE, leftAt, 0, book);
        e.page = page;
        e.pages = 100;
        e.words = words;
        e.raw = raw;
        e.mode = mode;
        e.chapter = page <= 50 ? "One" : "Two";
        return e;
    }

    static StatsEvent simple(String type, long ts, String book) {
        return new StatsEvent(type, ts, 0, book);
    }

    static StatsData all(StatsEngine engine) {
        return engine.merged(null);
    }

    static int today() {
        return StatsData.dayOf(T0, 0);
    }

    @Test
    public void readPageCountsTimeWordsAndSpeed() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN));
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + MIN, "b.epub"));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(MIN, s.readMs);
        assertEquals(250, s.words);
        assertEquals(1f, s.pages, 0.001f);
        assertEquals(250, s.wpm, StatsRules.WPM_BUCKET);
        assertEquals(1, s.sessions);
        assertEquals(1, s.opens);
        assertEquals(0, s.bounces);
    }

    @Test
    public void idlePageIsCappedAndLeftOutOfSpeed() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + 20 * MIN, "b.epub", 1, 250, 20 * MIN));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(StatsRules.PAGE_CAP_MS, s.readMs);
        assertEquals(250, s.words);
        assertEquals(0, s.wpm);
    }

    @Test
    public void skimIsNotReading() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + SEC, "b.epub", 1, 250, SEC));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(1, s.skims);
        assertEquals(0, s.words);
        assertEquals(0, s.wpm);
    }

    @Test
    public void fixedLayoutCountsRealPagesReflowableVirtualOnes() {
        final StatsEngine engine = new StatsEngine();
        final StatsEvent pdf = open(T0, "b.pdf");
        pdf.ext = "pdf";
        engine.feed("a", pdf);
        engine.feed("a", page(T0 + MIN, "b.pdf", 1, 500, MIN));
        engine.feed("a", open(T0 + 2 * MIN, "c.epub"));
        engine.feed("a", page(T0 + 3 * MIN, "c.epub", 1, 500, MIN));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(1f + 500f / StatsRules.WORDS_PER_VIRTUAL_PAGE, s.pages, 0.001f);
    }

    @Test
    public void uncountedPageUsesTheBookAverage() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 200, MIN));
        engine.feed("a", page(T0 + 2 * MIN, "b.epub", 2, -1, MIN));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(400, s.words);
        // the uncounted page has no words of its own: not in speed
        assertEquals(1, all(engine).books.get("b.epub").wpm.total);
    }

    @Test
    public void gapStartsANewSession() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN));
        engine.feed("a", page(T0 + 16 * MIN, "b.epub", 2, 250, MIN));
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + 16 * MIN, "b.epub"));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(2, s.sessions);
        assertEquals(MIN, s.longestSessionMs);
    }

    @Test
    public void shortTripAwayKeepsTheSessionLongOneEndsIt() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN));
        engine.feed("a", simple(StatsEvent.PAUSE, T0 + MIN, "b.epub"));
        engine.feed("a", simple(StatsEvent.RESUME, T0 + MIN + 30 * SEC, "b.epub"));
        engine.feed("a", page(T0 + 2 * MIN + 30 * SEC, "b.epub", 2, 250, MIN));
        engine.feed("a", simple(StatsEvent.PAUSE, T0 + 3 * MIN, "b.epub"));
        engine.feed("a", simple(StatsEvent.RESUME, T0 + 8 * MIN, "b.epub"));
        engine.feed("a", page(T0 + 9 * MIN, "b.epub", 3, 250, MIN));
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + 9 * MIN, "b.epub"));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(2, s.sessions);
        assertEquals(0, s.bounces);
    }

    @Test
    public void openAndLeaveIsABounce() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + SEC, "b.epub", 1, 250, SEC));
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + 10 * SEC, "b.epub"));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(1, s.bounces);
        assertEquals(0, s.sessions);
    }

    @Test
    public void listeningIsItsOwnTimeAndNotSpeed() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        final StatsEvent on = simple(StatsEvent.TTS, T0 + SEC, "b.epub");
        on.on = true;
        engine.feed("a", on);
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN, StatsEvent.MODE_TTS));
        engine.feed("a", page(T0 + 2 * MIN, "b.epub", 2, 250, MIN, StatsEvent.MODE_TTS));
        final StatsEvent off = simple(StatsEvent.TTS, T0 + 10 * MIN + SEC, "b.epub");
        engine.feed("a", off);
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + 11 * MIN, "b.epub"));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(10 * MIN, s.listenMs);
        assertEquals(0, s.readMs);
        assertEquals(0, s.wpm);
        assertEquals(1, s.sessions);
    }

    @Test
    public void autoScrollCountsTimeButNotSpeed() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN, StatsEvent.MODE_AUTO));
        final StatsReport.Summary s = StatsReport.summary(all(engine), today(), today());
        assertEquals(MIN, s.readMs);
        assertEquals(250, s.words);
        assertEquals(0, s.wpm);
    }

    @Test
    public void devicesMergeAndFilter() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("phone", open(T0, "b.epub"));
        engine.feed("phone", page(T0 + MIN, "b.epub", 1, 250, MIN));
        engine.feed("tablet", open(T0 + 2 * MIN, "b.epub"));
        engine.feed("tablet", page(T0 + 4 * MIN, "b.epub", 2, 250, 2 * MIN));
        assertEquals(3 * MIN, StatsReport.summary(all(engine), today(), today()).readMs);
        assertEquals(2 * MIN, StatsReport.summary(engine.merged(Collections.singleton("tablet")), today(), today()).readMs);
        final StatsData.Book b = all(engine).books.get("b.epub");
        assertEquals(2, b.opens);
        assertEquals(500, b.words);
    }

    @Test
    public void localTimeZonePicksTheDay() {
        final StatsEngine engine = new StatsEngine();
        // 23:30 UTC is 00:30 the next day at UTC+1
        final long late = Days.of(2026, 10, 12) * 86_400_000L + 23 * 3_600_000L + 30 * MIN;
        final StatsEvent o = open(late - MIN, "b.epub");
        o.tz = 60;
        engine.feed("a", o);
        final StatsEvent p = page(late, "b.epub", 1, 250, MIN);
        p.tz = 60;
        engine.feed("a", p);
        assertEquals(MIN, StatsReport.summary(all(engine), Days.of(2026, 10, 13), Days.of(2026, 10, 13)).readMs);
        assertEquals(0, all(engine).day(Days.of(2026, 10, 12)).readMs);
    }

    @Test
    public void finishingAndReadingAgain() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 99, 250, MIN));
        engine.feed("a", page(T0 + 2 * MIN, "b.epub", 100, 250, MIN));
        StatsData.Book b = all(engine).books.get("b.epub");
        assertEquals(1, b.finishes());
        assertTrue(b.finished);
        // from the start again: a second pass, re-reading what was read
        engine.feed("a", page(T0 + 3 * MIN, "b.epub", 1, 250, MIN));
        engine.feed("a", page(T0 + 4 * MIN, "b.epub", 99, 250, MIN));
        engine.feed("a", page(T0 + 5 * MIN, "b.epub", 100, 250, MIN));
        b = all(engine).books.get("b.epub");
        assertEquals(2, b.finishes());
    }

    @Test
    public void goingBackCountsUnlessFollowingAnotherDevice() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 10, 250, MIN));
        engine.feed("a", page(T0 + 2 * MIN, "b.epub", 9, 250, MIN));
        final StatsEvent sync = simple(StatsEvent.JUMP, T0 + 2 * MIN, "b.epub");
        sync.from = 9;
        sync.to = 2;
        sync.via = StatsEvent.VIA_SYNC;
        engine.feed("a", sync);
        final StatsEvent toc = simple(StatsEvent.JUMP, T0 + 3 * MIN, "b.epub");
        toc.from = 40;
        toc.to = 5;
        engine.feed("a", toc);
        assertEquals(2, StatsReport.summary(all(engine), today(), today()).backtracks);
    }

    @Test
    public void rereadPagesAreCounted() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 10, 250, MIN));
        engine.feed("a", page(T0 + 2 * MIN, "b.epub", 11, 250, MIN));
        engine.feed("a", page(T0 + 3 * MIN, "b.epub", 10, 250, MIN));
        assertEquals(1, StatsReport.summary(all(engine), today(), today()).rereads);
    }

    @Test
    public void countersAndChapters() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", simple(StatsEvent.HIGHLIGHT, T0 + SEC, "b.epub"));
        engine.feed("a", simple(StatsEvent.LOOKUP, T0 + SEC, "b.epub"));
        engine.feed("a", simple(StatsEvent.NOTE, T0 + SEC, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 10, 250, MIN));
        engine.feed("a", page(T0 + 3 * MIN, "b.epub", 60, 250, 2 * MIN));
        final StatsData d = all(engine);
        final StatsReport.Summary s = StatsReport.summary(d, today(), today());
        assertEquals(1, s.highlights);
        assertEquals(1, s.lookups);
        assertEquals(1, s.notes);
        assertEquals(2, d.books.get("b.epub").chapters.size());
        assertEquals("One", StatsReport.chapters(d.books.get("b.epub")).get(0).title);
        assertEquals(2 * MIN, d.books.get("b.epub").chapters.get("Two").ms);
    }

    @Test
    public void switchingBooks() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 1, 250, MIN));
        engine.feed("a", open(T0 + 2 * MIN, "c.epub"));
        engine.feed("a", page(T0 + 3 * MIN, "c.epub", 1, 250, MIN));
        engine.feed("a", open(T0 + 4 * MIN, "b.epub"));
        engine.feed("a", page(T0 + 5 * MIN, "b.epub", 2, 250, MIN));
        engine.feed("a", simple(StatsEvent.CLOSE, T0 + 5 * MIN, "b.epub"));
        assertEquals(2, StatsReport.summary(all(engine), today(), today()).switches);
    }

    @Test
    public void eventRoundTrip() {
        final StatsEvent e = page(T0, "b.epub", 3, 120, 45 * SEC);
        e.tz = -300;
        final StatsEvent back = StatsEvent.fromJson(e.toJson());
        assertEquals(StatsEvent.PAGE, back.type);
        assertEquals(T0, back.ts);
        assertEquals(-300, back.tz);
        assertEquals("b.epub", back.book);
        assertEquals(3, back.page);
        assertEquals(120, back.words);
        assertEquals(45 * SEC, back.raw);
        assertEquals("One", back.chapter);
        assertNull(StatsEvent.fromJson("{not json"));
        assertNull(StatsEvent.fromJson("{\"e\":\"page\"}"));
        assertFalse(StatsEvent.fromJson(simple(StatsEvent.TTS, T0, "b").toJson()).on);
    }
}
