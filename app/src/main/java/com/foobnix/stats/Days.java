package com.foobnix.stats;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Calendar arithmetic on epoch days ({@link StatsData#dayOf}) without java.time (minSdk 24):
 * a UTC calendar, since the days are already local.
 */
public final class Days {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private Days() {
    }

    /** Today on this device. */
    public static int today() {
        final long now = System.currentTimeMillis();
        return StatsData.dayOf(now, TimeZone.getDefault().getOffset(now) / 60_000);
    }

    public static Calendar calendar(int day) {
        final Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.setTimeInMillis(day * 86_400_000L);
        return c;
    }

    public static int dayOf(Calendar c) {
        return (int) Math.floorDiv(c.getTimeInMillis(), 86_400_000L);
    }

    public static int of(int year, int month1, int dayOfMonth) {
        final Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(year, month1 - 1, dayOfMonth);
        return dayOf(c);
    }

    public static int year(int day) {
        return calendar(day).get(Calendar.YEAR);
    }

    /** 1..12 */
    public static int month(int day) {
        return calendar(day).get(Calendar.MONTH) + 1;
    }

    public static int dayOfMonth(int day) {
        return calendar(day).get(Calendar.DAY_OF_MONTH);
    }

    /** The Monday of the day's week. */
    public static int weekStart(int day) {
        return day - StatsData.weekdayOf(day);
    }

    public static int monthStart(int day) {
        return of(year(day), month(day), 1);
    }

    public static int monthEnd(int day) {
        final Calendar c = calendar(monthStart(day));
        c.add(Calendar.MONTH, 1);
        return dayOf(c) - 1;
    }

    public static int yearStart(int day) {
        return of(year(day), 1, 1);
    }

    public static int yearEnd(int day) {
        return of(year(day), 12, 31);
    }

    /** "YYYY-MM" of the month the day is in. */
    public static String monthKey(int day) {
        return String.format(java.util.Locale.US, "%04d-%02d", year(day), month(day));
    }
}
