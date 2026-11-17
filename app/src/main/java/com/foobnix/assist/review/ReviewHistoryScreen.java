package com.foobnix.assist.review;

import android.app.Activity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.assist.useit.UseDates;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.Collections;

/**
 * One idea's memory, full screen (canvas ReviewHistory): the estimated chance of recalling it
 * today, when it comes back, its forgetting curve with the past reviews and the next two, the
 * history, and "Review it now" (a session of this one card). The chance and the curve come from a
 * model ({@link ReviewCurve}), not from a measurement: the screen says "estimated" at both.
 */
final class ReviewHistoryScreen {

    private ReviewHistoryScreen() {
    }

    /**
     * @param dc        the open book, or null
     * @param reviewNow whether "Review it now" is offered (not from inside a session)
     * @param onChanged runs when a session started from here closes (main thread), or null
     */
    static void show(final Activity a, final DocumentController dc, final ReviewQueue.Entry entry, boolean reviewNow,
                     final Runnable onChanged) {
        if (!Assist.REVIEW.isOn() || a == null || Apps.isDestroyedActivity(a) || entry == null) {
            return;
        }
        final ReviewItem item = entry.item;
        final String today = ReviewStore.today();
        final ReviewCurve.Model model = ReviewCurve.model(item, today);
        final String where = item.chapterTitle.isEmpty() ? entry.bookTitle : entry.bookTitle + " · " + item.chapterTitle;
        final ReviewPage page = new ReviewPage(a, a.getString(R.string.canvas_t25_memory_title), where, false);

        final TextView label = ReviewPage.label(a, a.getString(R.string.canvas_t25_idea), true);
        label.setPadding(dp(4), 0, dp(4), 0);
        page.add(label, 4);
        final TextView idea = ReviewPage.text(a, item.idea, 22, 600, CanvasLook.ink());
        idea.setLineSpacing(dp(4), 1f);
        idea.setPadding(dp(4), 0, dp(4), 0);
        page.add(idea, 4);

        // "70% · estimated chance you'd recall it today" and "Today · next review, then 3 weeks"
        final LinearLayout tiles = new LinearLayout(a);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(tile(a, model.percent + "%", a.getString(R.string.canvas_t25_chance), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        final String when = model.nextIn <= 0 ? a.getString(R.string.canvas_t24_today_word)
                : capital(ReviewWords.in(a, model.nextIn));
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        tp.leftMargin = dp(8);
        tiles.addView(tile(a, when, a.getString(R.string.canvas_t25_next_then, ReviewWords.gap(a, model.nextGap)), true), tp);
        page.add(tiles, 14);

        // the curve
        final LinearLayout chart = new LinearLayout(a);
        chart.setOrientation(LinearLayout.VERTICAL);
        chart.setPadding(dp(12), dp(12), dp(12), dp(12));
        chart.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(16)));
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(ReviewPage.text(a, a.getString(R.string.canvas_t25_how_well), 13, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(ReviewPage.text(a, a.getString(R.string.canvas_t25_estimated), 11, 400, CanvasLook.muted()));
        chart.addView(head);
        final int near = (int) Math.round(ReviewCurve.TARGET * 100);
        final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(8);
        chart.addView(new ReviewCurveView(a, model, a.getString(R.string.canvas_t25_missed),
                a.getString(R.string.canvas_t25_review_near, near),
                a.getString(model.nextIn <= 0 ? R.string.canvas_t25_if_today : R.string.canvas_t25_if_then),
                a.getString(R.string.canvas_t25_curve_says)), cp);
        final LinearLayout days = new LinearLayout(a);
        days.setOrientation(LinearLayout.HORIZONTAL);
        final String first = model.firstDay.isEmpty() ? today : model.firstDay;
        days.addView(ReviewPage.text(a, ReviewWords.day(first), 11, 400, CanvasLook.muted()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView now = ReviewPage.text(a, a.getString(R.string.canvas_t25_today_is, ReviewWords.day(today)), 11, 600,
                CanvasLook.ink());
        now.setGravity(Gravity.CENTER);
        days.addView(now, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView last = ReviewPage.text(a, ReviewWords.day(UseDates.addDays(first, model.end)), 11, 400, CanvasLook.muted());
        last.setGravity(Gravity.END);
        days.addView(last, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        chart.addView(days);
        page.add(chart, 14);

        // the history, latest first
        final TextView history = ReviewPage.label(a, a.getString(R.string.canvas_t25_history), false);
        history.setPadding(dp(4), 0, dp(4), dp(6));
        page.add(history, 14);
        final java.util.List<ReviewItem.Past> past = new java.util.ArrayList<>(item.history);
        Collections.reverse(past);
        for (int i = 0; i < past.size(); i++) {
            page.add(pastRow(a, item, past.get(i), i < past.size() - 1), 0);
        }

        if (reviewNow) {
            final TextView button = ReviewPage.primary(a, a.getString(R.string.canvas_t25_review_now));
            button.setOnClickListener(v -> {
                page.dialog.dismiss();
                ReviewSessionScreen.start(a, dc, Collections.singletonList(entry), a.getString(R.string.canvas_t25_one_idea),
                        onChanged, null);
            });
            page.actions.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
        page.dialog.show();
    }

    private static View tile(Activity a, String big, String small, boolean accent) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(CanvasParts.round(accent ? CanvasLook.accentTint() : CanvasLook.surface2(), 0, dp(14)));
        box.addView(ReviewPage.text(a, big, 22, 700, accent ? CanvasLook.accent() : CanvasLook.ink()));
        final TextView t = ReviewPage.text(a, small, 12, 400, CanvasLook.ink2());
        t.setPadding(0, dp(2), 0, 0);
        box.addView(t);
        return box;
    }

    /** "Oct 30   Review · right · back in 8 days", "Oct 26   Blank-page recall · missed". */
    private static View pastRow(Activity a, ReviewItem item, ReviewItem.Past p, boolean divider) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.target());
        row.addView(ReviewPage.text(a, ReviewWords.day(p.day), 12, 400, CanvasLook.muted()),
                new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT));
        final SpannableStringBuilder s = new SpannableStringBuilder();
        if (ReviewItem.ADDED.equals(p.kind)) {
            s.append(a.getString(ReviewItem.NOTE.equals(item.reason) ? R.string.canvas_t25_past_note
                    : R.string.canvas_t25_past_added));
        } else {
            final boolean recall = ReviewItem.RECALL.equals(p.kind);
            s.append(a.getString(recall ? R.string.canvas_t25_past_recall : R.string.canvas_t25_past_review)).append(" · ");
            final int from = s.length();
            s.append(a.getString(p.right ? (recall ? R.string.canvas_t25_remembered : R.string.canvas_t25_right)
                    : (recall ? R.string.canvas_t25_missed : R.string.canvas_t25_not_right)));
            s.setSpan(new ForegroundColorSpan(p.right ? CanvasLook.accent() : CanvasLook.sensor()), from, s.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (p.gap > 0) {
            s.append(" · ").append(a.getString(R.string.canvas_t25_back, ReviewWords.in(a, p.gap)));
        }
        final TextView text = ReviewPage.text(a, s, 13, 400, CanvasLook.ink());
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(row);
        if (divider) {
            final View rule = new View(a);
            rule.setBackgroundColor(CanvasLook.surface2());
            box.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }
        return box;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(java.util.Locale.getDefault()) + s.substring(1);
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
