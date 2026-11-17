package com.foobnix.assist.review;

import android.content.Context;

import com.foobnix.assist.useit.UseDates;
import com.foobnix.pdf.info.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** The review screens' counts, waits and days in words. */
final class ReviewWords {

    private ReviewWords() {
    }

    /** "1 idea", "6 ideas". */
    static String ideas(Context c, int n) {
        return n == 1 ? c.getString(R.string.canvas_t24_idea_one) : c.getString(R.string.canvas_t24_idea_many, n);
    }

    /** "about 4 min" for that many cards. */
    static String about(Context c, int cards) {
        return c.getString(R.string.canvas_t24_about_min, ReviewQueue.minutes(cards));
    }

    /** A wait: "tomorrow", "3 days", "3 weeks". */
    static String gap(Context c, int days) {
        if (days <= 1) {
            return c.getString(R.string.canvas_t24_tomorrow);
        }
        return ReviewText.inWeeks(days) ? c.getString(R.string.canvas_t24_weeks, days / 7)
                : c.getString(R.string.canvas_t24_days, days);
    }

    /** A wait after "comes back": "tomorrow", "in 3 days", "in 3 weeks". */
    static String in(Context c, int days) {
        return days <= 1 ? c.getString(R.string.canvas_t24_tomorrow) : c.getString(R.string.canvas_t24_in, gap(c, days));
    }

    /** Why an idea is in the queue, as a row and a chip say it. */
    static String reason(Context c, String reason) {
        if (ReviewItem.MISSED.equals(reason)) {
            return c.getString(R.string.canvas_t24_reason_missed);
        }
        if (ReviewItem.UNSURE.equals(reason)) {
            return c.getString(R.string.canvas_t24_reason_unsure);
        }
        return ReviewItem.NOTE.equals(reason) ? c.getString(R.string.canvas_t24_reason_note)
                : c.getString(R.string.canvas_t24_reason_fading);
    }

    /** "due today", "due 3 days ago". */
    static String due(Context c, String due, String today) {
        final int late = UseDates.daysBetween(due, today);
        return late <= 0 ? c.getString(R.string.canvas_t24_due_today)
                : late == 1 ? c.getString(R.string.canvas_t24_due_yesterday)
                : c.getString(R.string.canvas_t24_due_ago, late);
    }

    /** "Next: 3 tomorrow · 11 this week". */
    static String next(Context c, int[] next) {
        return c.getString(R.string.canvas_t24_next, next[0], next[1]);
    }

    /** A local day as "Oct 26"; the day itself when it cannot be read. */
    static String day(String day) {
        final int[] p = UseDates.parts(day);
        if (p == null) {
            return day == null ? "" : day;
        }
        final Calendar c = Calendar.getInstance();
        c.clear();
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0);
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(c.getTime());
    }
}
