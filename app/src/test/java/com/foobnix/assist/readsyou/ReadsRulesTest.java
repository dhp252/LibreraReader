package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReadsRulesTest {

    /** 250 ms per word is 240 words a minute. */
    private static final double PACE = 250;

    @Test
    public void expectedTimeNeedsWordsAndPace() {
        assertEquals(75_000, ReadsRules.expectedMs(300, PACE));
        assertEquals(-1, ReadsRules.expectedMs(300, 0));
        assertEquals(-1, ReadsRules.expectedMs(ReadsRules.MIN_WORDS - 1, PACE));
    }

    @Test
    public void dwellIsMuchLongerThanTheReadersPace() {
        // 300 words at 250 ms: 75 s expected, a dwell from 187.5 s
        assertFalse(ReadsRules.isDwell(180_000, 300, PACE));
        assertTrue(ReadsRules.isDwell(188_000, 300, PACE));
    }

    @Test
    public void dwellHasAFloorForShortPages() {
        // 60 words: 15 s expected, 2.5 times that is under the floor
        assertFalse(ReadsRules.isDwell(38_000, 60, PACE));
        assertTrue(ReadsRules.isDwell(ReadsRules.DWELL_MIN_MS, 60, PACE));
    }

    @Test
    public void noDwellWithoutPaceOrWords() {
        assertFalse(ReadsRules.isDwell(600_000, 300, 0));
        assertFalse(ReadsRules.isDwell(600_000, -1, PACE));
        assertFalse(ReadsRules.isDwell(600_000, 10, PACE));
    }

    @Test
    public void aPageLeftAloneIsNotADwell() {
        // 75 s expected: left after 8 times that (10 min)
        assertEquals(600_000, ReadsRules.awayLimit(300, PACE));
        assertTrue(ReadsRules.isDwell(599_000, 300, PACE));
        assertFalse(ReadsRules.isDwell(600_000, 300, PACE));
        // a short page still gets the least time, an unknown one the fixed time
        assertEquals(ReadsRules.AWAY_MIN_MS, ReadsRules.awayLimit(60, PACE));
        assertEquals(ReadsRules.AWAY_UNKNOWN_MS, ReadsRules.awayLimit(-1, PACE));
        assertEquals(ReadsRules.AWAY_UNKNOWN_MS, ReadsRules.awayLimit(300, 0));
    }

    @Test
    public void aSampleIsAPaceThatCanBeReading() {
        assertTrue(ReadsRules.isSample(75_000, 300));
        // 10 ms a word is a flick through, 5 s a word is not reading
        assertFalse(ReadsRules.isSample(3_000, 300));
        assertFalse(ReadsRules.isSample(1_500_000, 300));
        assertFalse(ReadsRules.isSample(75_000, 20));
        assertFalse(ReadsRules.isSample(0, 300));
    }

    @Test
    public void median() {
        assertEquals(0, ReadsRules.median(new ArrayList<Double>()), 0);
        assertEquals(3, ReadsRules.median(Arrays.asList(5.0, 1.0, 3.0)), 0);
        assertEquals(2.5, ReadsRules.median(Arrays.asList(4.0, 1.0, 2.0, 3.0)), 0);
    }

    @Test
    public void slowdownComparesTheLastPagesWithTheFirst() {
        final List<Double> session = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            session.add(210.0);
        }
        // not enough pages for two separate groups
        assertEquals(0, ReadsRules.slowdown(session), 0);
        for (int i = 0; i < 5; i++) {
            session.add(300.0);
        }
        // 210 ms a word, then 300: the speed fell to 70 %
        assertEquals(0.3, ReadsRules.slowdown(session), 0.0001);
        assertEquals(30, ReadsRules.percent(ReadsRules.slowdown(session)));
        assertTrue(ReadsRules.isSlowdown(ReadsRules.slowdown(session)));
    }

    @Test
    public void readingFasterIsNoSlowdown() {
        final List<Double> session = Arrays.asList(300.0, 300.0, 300.0, 300.0, 300.0, 200.0, 200.0, 200.0, 200.0, 200.0);
        assertEquals(0, ReadsRules.slowdown(session), 0);
    }

    @Test
    public void aSmallSlowdownIsNotReal() {
        final List<Double> session = Arrays.asList(250.0, 250.0, 250.0, 250.0, 250.0, 280.0, 280.0, 280.0, 280.0, 280.0);
        assertFalse(ReadsRules.isSlowdown(ReadsRules.slowdown(session)));
    }

    @Test
    public void oneSlowPageDoesNotMakeASlowdown() {
        final List<Double> session = Arrays.asList(250.0, 250.0, 250.0, 250.0, 250.0, 250.0, 250.0, 900.0, 250.0, 250.0);
        assertEquals(0, ReadsRules.slowdown(session), 0);
    }

    @Test
    public void breakNeedsALongSessionAndARealSlowdown() {
        final long now = 1_000_000_000L;
        assertTrue(ReadsRules.wantsBreak(ReadsRules.FATIGUE_MIN_MS, 0.3, now, 0));
        assertFalse(ReadsRules.wantsBreak(ReadsRules.FATIGUE_MIN_MS - 1, 0.3, now, 0));
        assertFalse(ReadsRules.wantsBreak(ReadsRules.FATIGUE_MIN_MS, 0.1, now, 0));
    }

    @Test
    public void keepReadingHoldsTheBreakCardBack() {
        final long now = 1_000_000_000L;
        assertFalse(ReadsRules.wantsBreak(ReadsRules.FATIGUE_MIN_MS, 0.3, now, now + 1));
        assertTrue(ReadsRules.wantsBreak(ReadsRules.FATIGUE_MIN_MS, 0.3, now + ReadsRules.SNOOZE_MS, now + ReadsRules.SNOOZE_MS));
    }

    @Test
    public void minutesAreRoundedDown() {
        assertEquals(52, ReadsRules.minutes(52 * 60_000L + 59_000));
        assertEquals(0, ReadsRules.minutes(-5));
        assertEquals(240, ReadsRules.wpm(250));
        assertEquals(0, ReadsRules.wpm(0));
    }
}
