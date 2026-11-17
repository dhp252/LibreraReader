package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReadsEyeTest {

    private static final float NO_GAZE = Float.NaN;

    /** Frames every 200 ms (5 a second) from {@code from} for {@code ms}, all in one state; returns the time after. */
    private static long run(ReadsEye eye, long from, long ms, int state, float gazeY) {
        long t = from;
        for (; t < from + ms; t += 200) {
            eye.frame(t, state, gazeY);
        }
        return t;
    }

    @Test
    public void watchedAndAwayTimeAreCountedPerPage() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 1000, 10_000, ReadsEye.SEES, NO_GAZE);
        t = run(eye, t, 6_000, ReadsEye.AWAY, NO_GAZE);
        t = run(eye, t, 4_000, ReadsEye.SEES, NO_GAZE);
        eye.frame(t, ReadsEye.SEES, NO_GAZE);
        final ReadsEye.Page p = eye.turn();
        assertEquals(20_000, p.watchedMs);
        assertEquals(6_000, p.awayMs);
        assertEquals(6_000, p.longestAwayMs);
        assertEquals(0, p.passes);
        // the next page starts empty
        final ReadsEye.Page next = eye.turn();
        assertEquals(0, next.watchedMs);
        assertEquals(0, next.awayMs);
    }

    @Test
    public void aStretchOffThePageIsReportedOnceWhenTheReaderLooksBack() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 2_000, ReadsEye.SEES, NO_GAZE);
        t = run(eye, t, 25_000, ReadsEye.AWAY, NO_GAZE);
        assertEquals(0, eye.returned());
        assertTrue(eye.away(t));
        assertTrue(eye.awayRun() >= 24_000);
        eye.frame(t, ReadsEye.SEES, NO_GAZE);
        final long back = eye.returned();
        assertEquals(25_000, back);
        assertTrue(ReadsRules.isDriftAway(back));
        assertEquals(0, eye.returned());
        assertFalse(eye.away(t));
    }

    @Test
    public void theLongestStretchIsKeptAndADarkFrameIsNoLookBack() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 1_000, ReadsEye.SEES, NO_GAZE);
        t = run(eye, t, 3_000, ReadsEye.AWAY, NO_GAZE);
        t = run(eye, t, 1_000, ReadsEye.SEES, NO_GAZE);
        eye.returned();
        t = run(eye, t, 8_000, ReadsEye.AWAY, NO_GAZE);
        // it cannot tell any more: the stretch ends, but nobody looked back
        t = run(eye, t, 1_000, ReadsEye.CANT_SEE, NO_GAZE);
        assertEquals(0, eye.returned());
        final ReadsEye.Page p = eye.turn();
        assertEquals(8_000, p.longestAwayMs);
        assertEquals(11_000, p.awayMs);
        // time it could not tell is not watched time
        assertEquals(13_000, p.watchedMs);
    }

    @Test
    public void aGapInTheFramesIsNotCounted() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 2_000, ReadsEye.AWAY, NO_GAZE);
        // the camera was off for a minute
        t += 60_000;
        assertFalse(eye.live(t));
        t = run(eye, t, 2_000, ReadsEye.SEES, NO_GAZE);
        assertTrue(eye.live(t));
        assertEquals(0, eye.returned());
        final ReadsEye.Page p = eye.turn();
        assertEquals(1_800 + 1_800, p.watchedMs);
        assertEquals(1_800, p.awayMs);
    }

    @Test
    public void bottomThenTopWithThePageStillShownIsASecondPass() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 3_000, ReadsEye.SEES, 0.2f);
        t = run(eye, t, 5_000, ReadsEye.SEES, 0.5f);
        t = run(eye, t, 1_400, ReadsEye.SEES, 0.8f);
        assertFalse(eye.passed());
        // back at the top, and the page stays
        t = run(eye, t, 5_000, ReadsEye.SEES, 0.15f);
        assertTrue(eye.passed());
        assertFalse(eye.passed());
        assertEquals(1, eye.passes());
        // staying at the top is not another pass
        t = run(eye, t, 5_000, ReadsEye.SEES, 0.2f);
        assertEquals(1, eye.passes());
        assertEquals(1, eye.turn().passes);
        assertEquals(0, eye.passes());
    }

    @Test
    public void aLookUpJustBeforeThePageTurnIsNoSecondPass() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 4_000, ReadsEye.SEES, 0.8f);
        t = run(eye, t, 1_500, ReadsEye.SEES, 0.1f);
        // the page turns before the confirm time
        assertEquals(0, eye.turn().passes);
        // on the new page the gaze is at the top: nothing carries over
        t = run(eye, t, 6_000, ReadsEye.SEES, 0.1f);
        assertEquals(0, eye.passes());
    }

    @Test
    public void aGlanceUpAndDownAgainIsNoSecondPass() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 2_000, ReadsEye.SEES, 0.8f);
        t = run(eye, t, 1_000, ReadsEye.SEES, 0.2f);
        t = run(eye, t, 4_000, ReadsEye.SEES, 0.8f);
        assertEquals(0, eye.passes());
    }

    @Test
    public void withoutACalibratedGazeThereIsNoSecondPass() {
        final ReadsEye eye = new ReadsEye();
        long t = run(eye, 0, 4_000, ReadsEye.SEES, NO_GAZE);
        t = run(eye, t, 8_000, ReadsEye.SEES, NO_GAZE);
        assertEquals(0, eye.passes());
        // and the top is not reached without the bottom first
        t = run(eye, t, 8_000, ReadsEye.SEES, 0.1f);
        assertEquals(0, eye.passes());
    }

    @Test
    public void driftRules() {
        assertFalse(ReadsRules.isDriftAway(19_000));
        assertTrue(ReadsRules.isDriftAway(20_000));
        // that long is leaving, not drifting
        assertFalse(ReadsRules.isDriftAway(ReadsRules.DRIFT_AWAY_MAX_MS));
        // 200 words at 300 ms a word: 60 s expected; under 18 s after a look-away of 5 s is too fast
        assertTrue(ReadsRules.isDriftFast(15_000, 200, 300, 6_000));
        assertFalse(ReadsRules.isDriftFast(20_000, 200, 300, 6_000));
        assertFalse(ReadsRules.isDriftFast(15_000, 200, 300, 4_000));
        assertFalse(ReadsRules.isDriftFast(15_000, 200, 0, 6_000));
        assertFalse(ReadsRules.isDriftFast(1_000, 10, 300, 6_000));
    }

    @Test
    public void theBreakCardWithTheCamera() {
        final long longSession = ReadsRules.FATIGUE_MIN_MS;
        assertTrue(ReadsRules.wantsBreak(longSession, 0, true, 1000, 0));
        assertTrue(ReadsRules.wantsBreak(longSession, 0.3, false, 1000, 0));
        assertFalse(ReadsRules.wantsBreak(longSession, 0, false, 1000, 0));
        assertFalse(ReadsRules.wantsBreak(longSession - 1, 0.3, true, 1000, 0));
        assertFalse(ReadsRules.wantsBreak(longSession, 0.3, true, 1000, 2000));
    }

    @Test
    public void cameraCountersAreStoredAndASecondPassIsASpot() throws Exception {
        final ReadsSignals s = new ReadsSignals();
        s.pages = 4;
        final ReadsDelta d = new ReadsDelta(1);
        d.watched = 90;
        d.away = 12;
        d.passes = 1;
        assertFalse(d.isEmpty());
        s.apply(1, d);
        final ReadsSignals back = ReadsSignals.fromJson(new org.json.JSONObject(s.toJson().toString()));
        assertEquals(90, back.peek(1).watched);
        assertEquals(12, back.peek(1).away);
        assertEquals(1, back.peek(1).passes);
        assertTrue(back.peek(1).isSpot());
        assertEquals(1, back.spots());
        assertEquals(ReadsSections.Rating.STRUGGLED, ReadsSections.rating(back.peek(1), 300));
        // without the camera none of its fields is written
        final ReadsSignals plain = new ReadsSignals();
        plain.pages = 2;
        plain.at(0).seconds = 30;
        final String json = plain.toJson().toString();
        assertFalse(json.contains("\"e\"") || json.contains("\"a\"") || json.contains("\"g\""));
    }
}
