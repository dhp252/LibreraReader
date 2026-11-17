package com.foobnix.assist.useit;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Local calendar days as {@code yyyy-MM-dd} text, and the arithmetic an experiment needs: a day
 * some days later, the days between two, which comes first. No clock is read here: "today" is
 * always passed in (see {@link #of(long, TimeZone)} for making it from the time), so the rules are
 * the same in tests and on every device. No {@code java.time} (minSdk 24).
 */
public final class UseDates {

    private UseDates() {
    }

    /** The local day of a moment in a time zone. */
    public static String of(long millis, TimeZone zone) {
        final Calendar c = Calendar.getInstance(zone == null ? TimeZone.getDefault() : zone);
        c.setTimeInMillis(millis);
        return of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** @param month 1 to 12 */
    public static String of(int year, int month, int day) {
        final StringBuilder out = new StringBuilder(10);
        pad(out, year, 4);
        out.append('-');
        pad(out, month, 2);
        out.append('-');
        pad(out, day, 2);
        return out.toString();
    }

    private static void pad(StringBuilder out, int value, int width) {
        final String s = Integer.toString(Math.max(0, value));
        for (int i = s.length(); i < width; i++) {
            out.append('0');
        }
        out.append(s);
    }

    /** {year, month 1-12, day} of a day, or null when the text is not a real day. */
    public static int[] parts(String day) {
        if (day == null || day.length() != 10 || day.charAt(4) != '-' || day.charAt(7) != '-') {
            return null;
        }
        for (int i = 0; i < 10; i++) {
            if (i != 4 && i != 7 && (day.charAt(i) < '0' || day.charAt(i) > '9')) {
                return null;
            }
        }
        final int y = Integer.parseInt(day.substring(0, 4));
        final int m = Integer.parseInt(day.substring(5, 7));
        final int d = Integer.parseInt(day.substring(8, 10));
        if (m < 1 || m > 12 || d < 1 || d > daysIn(y, m)) {
            return null;
        }
        return new int[]{y, m, d};
    }

    public static boolean isDay(String day) {
        return parts(day) != null;
    }

    static int daysIn(int year, int month) {
        switch (month) {
            case 2:
                return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /** Days since 1970-01-01 (the proleptic Gregorian calendar); the day must be {@link #isDay}. */
    static long epochDay(String day) {
        final int[] p = parts(day);
        if (p == null) {
            throw new IllegalArgumentException("not a day: " + day);
        }
        // days from civil (Howard Hinnant's algorithm)
        final long y = p[1] <= 2 ? p[0] - 1 : p[0];
        final long era = (y >= 0 ? y : y - 399) / 400;
        final long yoe = y - era * 400;
        final long doy = (153 * (p[1] + (p[1] > 2 ? -3 : 9)) + 2) / 5 + p[2] - 1;
        final long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    static String ofEpochDay(long epochDay) {
        final long z = epochDay + 719468;
        final long era = (z >= 0 ? z : z - 146096) / 146097;
        final long doe = z - era * 146097;
        final long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        final long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        final long mp = (5 * doy + 2) / 153;
        final int d = (int) (doy - (153 * mp + 2) / 5 + 1);
        final int m = (int) (mp < 10 ? mp + 3 : mp - 9);
        final int y = (int) (yoe + era * 400 + (m <= 2 ? 1 : 0));
        return of(y, m, d);
    }

    /** The day {@code days} after {@code day} (before it when negative); null when {@code day} is not a day. */
    public static String addDays(String day, int days) {
        return isDay(day) ? ofEpochDay(epochDay(day) + days) : null;
    }

    /** Days from {@code from} to {@code to}: 0 the same day, negative when {@code to} is earlier; 0 when either is not a day. */
    public static int daysBetween(String from, String to) {
        return isDay(from) && isDay(to) ? (int) (epochDay(to) - epochDay(from)) : 0;
    }

    /** {@code day} is {@code other} or later; false when either is not a day. */
    public static boolean reached(String day, String other) {
        return isDay(day) && isDay(other) && epochDay(day) >= epochDay(other);
    }

    /** 0 = Monday ... 6 = Sunday; -1 when it is not a day. */
    public static int weekday(String day) {
        if (!isDay(day)) {
            return -1;
        }
        // 1970-01-01 was a Thursday
        return (int) (((epochDay(day) + 3) % 7 + 7) % 7);
    }
}
