package com.foobnix.assist.useit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

public class UseDatesTest {

    @Test
    public void twoWeeksOnIsTheCanvasCheckIn() {
        // canvas: Oct 26 - Nov 9
        assertEquals("2026-11-09", UseDates.addDays("2026-10-26", 14));
        assertEquals(14, UseDates.daysBetween("2026-10-26", "2026-11-09"));
        assertEquals(0, UseDates.weekday("2026-11-09"));
    }

    @Test
    public void daysCrossMonthsYearsAndLeapDays() {
        assertEquals("2027-01-04", UseDates.addDays("2026-12-21", 14));
        assertEquals("2028-02-29", UseDates.addDays("2028-02-22", 7));
        assertEquals("2028-03-01", UseDates.addDays("2028-02-29", 1));
        assertEquals("2026-03-01", UseDates.addDays("2026-02-22", 7));
        assertEquals("2026-10-19", UseDates.addDays("2026-10-26", -7));
        assertEquals(-7, UseDates.daysBetween("2026-10-26", "2026-10-19"));
        assertEquals(366, UseDates.daysBetween("2028-01-01", "2029-01-01"));
    }

    @Test
    public void epochDaysRoundTrip() {
        assertEquals(0, UseDates.epochDay("1970-01-01"));
        assertEquals("1970-01-01", UseDates.ofEpochDay(0));
        assertEquals("1969-12-31", UseDates.ofEpochDay(-1));
        for (long d = 19000; d < 22000; d += 13) {
            assertEquals(d, UseDates.epochDay(UseDates.ofEpochDay(d)));
        }
    }

    @Test
    public void weekdays() {
        assertEquals(3, UseDates.weekday("1970-01-01"));
        assertEquals(0, UseDates.weekday("2026-11-16"));
        assertEquals(6, UseDates.weekday("2026-11-15"));
        assertEquals(-1, UseDates.weekday("soon"));
    }

    @Test
    public void whatIsNotADay() {
        assertFalse(UseDates.isDay(null));
        assertFalse(UseDates.isDay(""));
        assertFalse(UseDates.isDay("2026-13-01"));
        assertFalse(UseDates.isDay("2026-02-30"));
        assertFalse(UseDates.isDay("2026-2-3"));
        assertFalse(UseDates.isDay("2026/02/03"));
        assertTrue(UseDates.isDay("2028-02-29"));
        assertFalse(UseDates.isDay("2026-02-29"));
        assertNull(UseDates.addDays("nope", 3));
        assertEquals(0, UseDates.daysBetween("nope", "2026-01-01"));
    }

    @Test
    public void reachedFromTheDayOn() {
        assertFalse(UseDates.reached("2026-11-08", "2026-11-09"));
        assertTrue(UseDates.reached("2026-11-09", "2026-11-09"));
        assertTrue(UseDates.reached("2027-01-01", "2026-11-09"));
        assertFalse(UseDates.reached("2026-11-10", ""));
        assertFalse(UseDates.reached(null, "2026-11-09"));
    }

    @Test
    public void theLocalDayOfAMomentFollowsTheZone() {
        // 2026-11-09 23:30 UTC
        final long millis = (UseDates.epochDay("2026-11-09") * 24L + 23) * 3600_000L + 30 * 60_000L;
        assertEquals("2026-11-09", UseDates.of(millis, TimeZone.getTimeZone("UTC")));
        assertEquals("2026-11-10", UseDates.of(millis, TimeZone.getTimeZone("Asia/Ho_Chi_Minh")));
        assertEquals("2026-11-09", UseDates.of(millis, TimeZone.getTimeZone("America/New_York")));
    }
}
