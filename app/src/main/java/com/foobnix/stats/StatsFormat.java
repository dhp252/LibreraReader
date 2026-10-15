package com.foobnix.stats;

import android.content.Context;

import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.OutlineHelper;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.TimeZone;

/** Numbers and times of the reading stats as the screens and the status bar show them. */
public final class StatsFormat {

    private StatsFormat() {
    }

    /** "< 1 min", "12 min", "4 h 10 min". */
    public static String duration(Context c, long ms) {
        final long minutes = ms / 60_000L;
        if (minutes < 1) {
            return c.getString(R.string.stats_under_a_minute);
        }
        if (minutes < 60) {
            return c.getString(R.string.stats_duration_min, (int) minutes);
        }
        return c.getString(R.string.stats_duration_h_min, (int) (minutes / 60), (int) (minutes % 60));
    }

    /** "12m", "4h 10m": for the status bar. */
    public static String shortDuration(Context c, int minutes) {
        if (minutes < 60) {
            return c.getString(R.string.stats_duration_short_min, Math.max(1, minutes));
        }
        return c.getString(R.string.stats_duration_short_h_min, minutes / 60, minutes % 60);
    }

    public static String number(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    public static String decimal(float n) {
        final NumberFormat f = NumberFormat.getNumberInstance();
        f.setMaximumFractionDigits(1);
        return f.format(n);
    }

    /** A day ({@link Days}) as a short date. */
    public static String date(int day) {
        final DateFormat f = DateFormat.getDateInstance(DateFormat.MEDIUM);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(day * 86_400_000L));
    }

    /** Short names of Monday..Sunday in the device's language. */
    public static String[] weekdayLetters() {
        final String[] out = new String[7];
        final java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        final java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEEEE", java.util.Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < 7; i++) {
            // 1970-01-05 was a Monday
            c.setTimeInMillis((4 + i) * 86_400_000L);
            out[i] = f.format(c.getTime());
        }
        return out;
    }

    /**
     * "Ch 12m · Book 4h 10m" for the reader's status bar: the words left at the reader's speed.
     * Null when it is off, or the speed or the words are not known yet.
     */
    public static String timeLeft(DocumentController dc) {
        try {
            if (!AppState.get().isShowTimeLeft || dc.getCurrentBook() == null) {
                return null;
            }
            final int wpm = ReadingSpeed.wpm(ExtUtils.getFileName(dc.getCurrentBook().getPath()));
            if (wpm <= 0) {
                return null;
            }
            final long bookWords = ReadingTracker.wordsLeft(dc, dc.getPageCount());
            if (bookWords < 0) {
                return null;
            }
            final Context c = dc.getActivity();
            final String book = shortDuration(c, StatsReport.minutesFor(bookWords, wpm));
            if (dc.getCurrentOutline() == null || dc.getCurrentOutline().isEmpty()) {
                return c.getString(R.string.stats_time_left_book, book);
            }
            final int lastOfChapter = dc.getCurentPageFirst1() + Math.max(1, OutlineHelper.getLeftPages(dc)) - 1;
            final long chapterWords = ReadingTracker.wordsLeft(dc, lastOfChapter);
            if (chapterWords < 0) {
                return c.getString(R.string.stats_time_left_book, book);
            }
            return c.getString(R.string.stats_time_left, shortDuration(c, StatsReport.minutesFor(chapterWords, wpm)), book);
        } catch (Exception e) {
            return null;
        }
    }
}
