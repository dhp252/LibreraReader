package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReadsBlinkTest {

    /**
     * Feeds {@code minutes} of watched frames, one every {@code step} ms, with one closed frame
     * every {@code every} ms; returns the time after.
     */
    private static long watch(ReadsBlink b, long from, int minutes, long step, long every) {
        long t = from;
        final long end = from + minutes * 60_000L;
        long nextBlink = from + every;
        for (; t < end; t += step) {
            boolean closed = false;
            if (t >= nextBlink) {
                closed = true;
                nextBlink += every;
            }
            b.frame(t, true, closed);
        }
        return t;
    }

    @Test
    public void aSteadyBlinkRateIsNotMore() {
        final ReadsBlink b = new ReadsBlink();
        // a blink caught every 20 s for 15 minutes
        watch(b, 0, 15, 200, 20_000);
        assertTrue(b.comparable());
        assertEquals(15, b.baseline(), 1);
        assertEquals(15, b.recent(), 1);
        assertFalse(b.more());
    }

    @Test
    public void clearlyMoreBlinksThanInTheFirstMinutesIsMore() {
        final ReadsBlink b = new ReadsBlink();
        long t = watch(b, 0, 5, 200, 20_000);
        assertFalse("the baseline alone is nothing to compare", b.more());
        t = watch(b, t, 5, 200, 20_000);
        assertFalse(b.more());
        // twice as often in the last five minutes
        watch(b, t, 5, 200, 10_000);
        assertTrue(b.more());
        assertEquals(15, b.baseline(), 1);
        assertEquals(30, b.recent(), 1);
    }

    @Test
    public void aThinBaselineIsNotComparedAgainst() {
        final ReadsBlink b = new ReadsBlink();
        // two blinks caught in the first five minutes
        long t = watch(b, 0, 5, 200, 140_000);
        t = watch(b, t, 10, 200, 10_000);
        assertTrue(b.baseline() < ReadsRules.BLINK_MIN);
        assertFalse(b.comparable());
        assertFalse(b.more());
    }

    @Test
    public void theTwoFrameRatesCountAlike() {
        final ReadsBlink five = new ReadsBlink();
        final ReadsBlink ten = new ReadsBlink();
        // the same eyes: closed for 300 ms every 15 s, seen at 5 and at 10 frames a second
        for (long t = 0; t < 12 * 60_000L; t += 100) {
            final boolean closed = t % 15_000 >= 14_600 && t % 15_000 < 14_900;
            if (t % 200 == 0) {
                five.frame(t, true, closed);
            }
            ten.frame(t, true, closed);
        }
        assertEquals(five.baseline(), ten.baseline());
        assertEquals(five.recent(), ten.recent());
        assertEquals(five.watchedMs(), ten.watchedMs(), 400);
    }

    @Test
    public void onlyWatchedTimeCountsAndAResetStartsAgain() {
        final ReadsBlink b = new ReadsBlink();
        long t = watch(b, 0, 3, 200, 20_000);
        // the reader looked away for ten minutes: closed eyes then are not blinks
        for (long end = t + 10 * 60_000L; t < end; t += 200) {
            b.frame(t, false, true);
        }
        assertEquals(3 * 60_000L, b.watchedMs(), 1_000);
        final int before = b.baseline();
        // the first frame after looking back is not compared with one before the gap
        b.frame(t, true, true);
        assertEquals(before, b.baseline());
        b.reset();
        assertEquals(0, b.watchedMs());
        assertEquals(0, b.baseline());
        assertEquals(0, b.recent());
        assertFalse(b.comparable());
    }
}
