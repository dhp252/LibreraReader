package com.foobnix.assist.edition;

import com.foobnix.assist.useit.UseDates;

import java.util.TimeZone;

/**
 * The days of "Your edition": local days as {@code yyyy-MM-dd} ({@link UseDates}), how they are
 * written on a page ("Oct 27", "Oct 26 – Nov 9"), and how far the next refresher is. No clock is
 * read except in {@link #today()}; nothing here schedules anything.
 */
public final class EditionDates {

    /** "Next refresher in 6 weeks." */
    public static final int REFRESH_DAYS = 42;

    /** How far a day is, for the cover's line. */
    public enum Unit {
        /** The day is today or passed. */
        NOW, TOMORROW, DAYS, WEEKS
    }

    /** A distance in a unit: {@code 6 WEEKS}, {@code 3 DAYS}. */
    public static final class Until {
        public final Unit unit;
        public final int n;

        Until(Unit unit, int n) {
            this.unit = unit;
            this.n = n;
        }
    }

    private EditionDates() {
    }

    /** Today as the device's local day. */
    public static String today() {
        return UseDates.of(System.currentTimeMillis(), TimeZone.getDefault());
    }

    /** The local day of a stored time in ms; empty for no time. */
    public static String dayOf(long millis, TimeZone zone) {
        return millis <= 0 ? "" : UseDates.of(millis, zone);
    }

    /** The day of the refresher after an edition opened (or refreshed) on {@code day}; empty when it is not a day. */
    public static String refresherAfter(String day) {
        final String next = UseDates.addDays(day, REFRESH_DAYS);
        return next == null ? "" : next;
    }

    /** How far {@code day} is from {@code today}: whole weeks from two weeks on, else days. */
    public static Until until(String today, String day) {
        if (!UseDates.isDay(today) || !UseDates.isDay(day)) {
            return new Until(Unit.NOW, 0);
        }
        final int days = UseDates.daysBetween(today, day);
        if (days <= 0) {
            return new Until(Unit.NOW, 0);
        }
        if (days == 1) {
            return new Until(Unit.TOMORROW, 1);
        }
        if (days < 14) {
            return new Until(Unit.DAYS, days);
        }
        return new Until(Unit.WEEKS, (days + 3) / 7);
    }

    /**
     * "Oct 27", with the year ("Oct 27, 2025") when it is not {@code today}'s; empty when
     * {@code day} is not a day.
     *
     * @param months twelve short month names
     */
    public static String shortDay(String day, String today, String[] months) {
        final int[] p = UseDates.parts(day);
        if (p == null || months == null || months.length < 12) {
            return "";
        }
        final int[] now = UseDates.parts(today);
        final String s = months[p[1] - 1] + " " + p[2];
        return now != null && now[0] != p[0] ? s + ", " + p[0] : s;
    }

    /** "Oct 26 – Nov 9"; one day alone when the other is missing or the same; empty with neither. */
    public static String range(String from, String to, String today, String[] months) {
        final String a = shortDay(from, today, months);
        final String b = shortDay(to, today, months);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return a.isEmpty() ? b : a;
        }
        return a + " – " + b;
    }
}
