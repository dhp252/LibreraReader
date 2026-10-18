package com.foobnix.stats;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;

import java.io.File;
import java.util.List;

/** The Reading section of a book's info sheet (F-007): time, speed, marks, time left, chapters. */
public final class BookStatsView {

    /** Chapters read this much slower than the book's speed are marked. */
    private static final float SLOW = 0.85f;
    private static final int MAX_CHAPTERS = 40;

    private BookStatsView() {
    }

    public static void fill(final Activity a, final File file, final View label, final View box) {
        if (file == null || label == null || !(box instanceof LinearLayout)) {
            return;
        }
        StatsLog.refresh(() -> show(a, ExtUtils.getFileName(file.getPath()), label, (LinearLayout) box));
    }

    private static void show(Activity a, String key, View label, LinearLayout box) {
        if (a.isFinishing()) {
            return;
        }
        final StatsData d = ReadingSpeed.data();
        final StatsData.Book b = d == null ? null : d.books.get(key);
        if (b == null || b.totalMs() < 60_000L) {
            return;
        }
        box.removeAllViews();
        label.setVisibility(View.VISIBLE);
        box.setVisibility(View.VISIBLE);
        add(box, a.getString(R.string.stats_book_time, StatsFormat.duration(a, b.totalMs()), b.readDays.size(), b.opens, b.sessions));

        final int wpm = b.wpm.median();
        if (wpm > 0) {
            final int all = StatsReport.summary(d, d.days.firstKey(), d.days.lastKey()).wpm;
            final int skimmed = Math.round(b.skims * 100f / Math.max(1, b.skims + b.pageViews));
            add(box, a.getString(R.string.stats_book_speed, wpm, all, skimmed, b.finishes()));
        }
        add(box, a.getString(R.string.stats_book_marks, b.highlights, b.notes, b.lookups, StatsFormat.duration(a, b.listenMs)));
        if (b.eyeMs > 0) {
            add(box, a.getString(R.string.stats_book_looking, Math.round(100f * b.lookMs / b.eyeMs)));
        }

        if (!b.finished) {
            final int speed = ReadingSpeed.wpm(key);
            final long wordsLeft = StatsReport.wordsLeftEstimate(b);
            final int minutes = StatsReport.minutesFor(wordsLeft, speed);
            if (minutes >= 0) {
                final int today = Days.today();
                final int finish = StatsReport.finishDay(b, wordsLeft, today);
                final String left = StatsFormat.duration(a, minutes * 60_000L);
                add(box, finish < 0 ? left : a.getString(R.string.stats_book_left, left, StatsFormat.date(finish)));
            }
        }

        final List<StatsData.Chapter> chapters = StatsReport.chapters(b);
        if (chapters.size() > 1) {
            final TextView title = add(box, a.getString(R.string.stats_book_chapters));
            title.setPadding(0, Math.round(8 * a.getResources().getDisplayMetrics().density), 0, 0);
            int shown = 0;
            for (StatsData.Chapter c : chapters) {
                if (c.ms < 60_000L || shown++ == MAX_CHAPTERS) {
                    continue;
                }
                final int cw = c.speed.wpm();
                final boolean slow = cw > 0 && wpm > 0 && cw < wpm * SLOW;
                final String time = StatsFormat.duration(a, c.ms);
                add(box, (slow ? "▼ " : "") + c.title + " · "
                        + (cw > 0 ? a.getString(R.string.stats_chapter_row, time, cw) : time));
            }
        }
    }

    private static TextView add(LinearLayout box, String s) {
        final TextView t = new TextView(box.getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.addView(t);
        return t;
    }
}
