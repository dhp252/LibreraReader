package com.foobnix.assist.review;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.List;

/**
 * Today's review, full screen (canvas ReviewToday): how many ideas are due and about how long
 * they take, the counts by reason, one row per due idea (its book, why it is here, "memory n of
 * 5"), "Next: 3 tomorrow · 11 this week" and Start. Opened from the ✦ menu, from the main
 * screen's banner and from the card in a book; nothing is sent to the AI until Start.
 */
final class ReviewTodayScreen {

    private final Activity a;
    /** The open book, or null when opened from the main screen. */
    private final DocumentController dc;
    /** Runs when the screen closes (main thread), or null. */
    private final Runnable onClosed;
    private ReviewPage page;

    private ReviewTodayScreen(Activity a, DocumentController dc, Runnable onClosed) {
        this.a = a;
        this.dc = dc;
        this.onClosed = onClosed;
    }

    static void show(Activity a, DocumentController dc, Runnable onClosed) {
        if (!Assist.REVIEW.isOn() || a == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        final ReviewTodayScreen s = new ReviewTodayScreen(a, dc, onClosed);
        s.page = new ReviewPage(a, a.getString(R.string.canvas_t24_today), "", false);
        s.page.dialog.setOnDismissListener(d -> {
            if (onClosed != null) {
                onClosed.run();
            }
        });
        s.message(a.getString(R.string.canvas_t24_loading));
        s.page.dialog.show();
        s.load();
    }

    private void load() {
        final ReviewStore.Meta open = Review.meta(dc);
        final String openBook = dc == null || dc.getCurrentBook() == null ? null
                : ReviewStore.name(dc.getCurrentBook().getPath());
        ReviewStore.IO.execute(() -> {
            // what recall stored since the last look, in every book whose file changed
            ReviewStore.scan();
            if (openBook != null) {
                ReviewStore.sync(openBook, open);
            }
            final String today = ReviewStore.today();
            final List<ReviewQueue.Entry> all = ReviewStore.all();
            final List<ReviewQueue.Entry> due = ReviewQueue.due(all, today);
            final int answered = ReviewQueue.answered(all, today);
            final int[] next = ReviewQueue.next(all, today);
            ui(() -> fill(all, due, answered, next, today));
        });
    }

    private void message(String text) {
        page.clear();
        final TextView t = ReviewPage.text(a, text, 14, 400, CanvasLook.muted());
        t.setPadding(dp(4), 0, dp(4), 0);
        t.setLineSpacing(dp(3), 1f);
        page.add(t, 16);
    }

    private void fill(List<ReviewQueue.Entry> all, List<ReviewQueue.Entry> due, int answered, int[] next, String today) {
        page.clear();
        final int books = ReviewQueue.books(due);
        page.setSubtitle(books > 1 ? a.getString(R.string.canvas_t24_mixed, books)
                : books == 1 ? due.get(0).bookTitle : "");

        // the summary: "6 ideas due today   ~4 min" and the counts by reason
        final LinearLayout summary = new LinearLayout(a);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(14), dp(14), dp(14), dp(14));
        summary.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(16)));
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        final TextView count = ReviewPage.text(a, ReviewWords.ideas(a, due.size()), 26, 700, CanvasLook.ink());
        head.addView(count);
        final TextView dueToday = ReviewPage.text(a, a.getString(R.string.canvas_t24_due_today), 15, 500, CanvasLook.ink2());
        dueToday.setPadding(dp(6), 0, 0, 0);
        head.addView(dueToday, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!due.isEmpty()) {
            head.addView(ReviewPage.text(a, "~" + a.getString(R.string.canvas_t24_min, ReviewQueue.minutes(due.size())),
                    13, 400, CanvasLook.ink2()));
        }
        summary.addView(head);
        final List<View> chips = new ArrayList<>();
        final int missed = ReviewQueue.count(due, ReviewItem.MISSED) + ReviewQueue.count(due, ReviewItem.UNSURE);
        if (missed > 0) {
            chips.add(ReviewPage.chip(a, a.getString(R.string.canvas_t24_chip_missed, missed), ReviewPage.CHIP_MISSED));
        }
        final int fading = ReviewQueue.count(due, ReviewItem.FADING);
        if (fading > 0) {
            chips.add(ReviewPage.chip(a, a.getString(R.string.canvas_t24_chip_fading, fading), ReviewPage.CHIP_PLAIN));
        }
        final int notes = ReviewQueue.count(due, ReviewItem.NOTE);
        if (notes > 0) {
            chips.add(ReviewPage.chip(a, a.getString(R.string.canvas_t24_chip_notes, notes), ReviewPage.CHIP_PLAIN));
        }
        if (!chips.isEmpty()) {
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(10);
            summary.addView(ReviewPage.row(a, chips.toArray(new View[0])), p);
        }
        page.add(summary, 4);

        if (due.isEmpty()) {
            // nothing to do now: say why, and how ideas get here
            final String why = all.isEmpty() ? a.getString(R.string.canvas_t24_empty)
                    : answered >= ReviewQueue.MAX_A_DAY ? a.getString(R.string.canvas_t24_day_done, ReviewQueue.MAX_A_DAY)
                    : a.getString(R.string.canvas_t24_none_due);
            final TextView t = ReviewPage.text(a, why, 14, 400, CanvasLook.ink2());
            t.setPadding(dp(4), 0, dp(4), 0);
            t.setLineSpacing(dp(3), 1f);
            page.add(t, 16);
        }

        // one row per idea; a book keeps its square's tone
        final List<String> order = new ArrayList<>();
        for (int i = 0; i < due.size(); i++) {
            final ReviewQueue.Entry e = due.get(i);
            if (!order.contains(e.bookName)) {
                order.add(e.bookName);
            }
            page.add(row(e, order.indexOf(e.bookName), i < due.size() - 1), i == 0 ? 10 : 0);
        }

        if (!all.isEmpty()) {
            final LinearLayout foot = new LinearLayout(a);
            foot.setOrientation(LinearLayout.HORIZONTAL);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            foot.setPadding(dp(4), 0, dp(4), 0);
            foot.addView(ReviewViews.Bars.legend(a));
            final TextView legend = ReviewPage.text(a, a.getString(R.string.canvas_t24_strength), 12, 400, CanvasLook.ink2());
            legend.setPadding(dp(6), 0, 0, 0);
            foot.addView(legend, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            foot.addView(ReviewPage.text(a, ReviewWords.next(a, next), 12, 400, CanvasLook.ink2()));
            page.add(foot, 10);
        }

        if (!due.isEmpty()) {
            final TextView start = ReviewPage.primary(a, a.getString(R.string.canvas_t24_start, ReviewWords.about(a, due.size())));
            start.setOnClickListener(v -> ReviewSessionScreen.start(a, dc, due, a.getString(R.string.canvas_t24_today),
                    this::load, page.dialog));
            page.actions.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
    }

    /** A due idea: its book's square, the idea over "Smith · missed in recall", and its memory bars. */
    private View row(ReviewQueue.Entry e, int tone, boolean divider) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(ReviewPage.square(a, ReviewText.letter(e.author, e.bookTitle), tone, 28),
                new LinearLayout.LayoutParams(dp(28), dp(28)));

        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView idea = ReviewPage.text(a, e.item.idea, 14, 600, CanvasLook.ink());
        idea.setMaxLines(2);
        idea.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(idea);
        final TextView line = ReviewPage.text(a, line(e), 12, 400, CanvasLook.muted());
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setPadding(0, dp(2), 0, 0);
        texts.addView(line);
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = dp(12);
        tp.rightMargin = dp(12);
        row.addView(texts, tp);

        final boolean missed = ReviewItem.MISSED.equals(e.item.reason);
        final View bars = new ReviewViews.Bars(a, e.item.strength, missed);
        row.addView(bars);
        row.setContentDescription(e.item.idea + ". " + line.getText() + ". "
                + a.getString(R.string.canvas_t24_memory, e.item.strength, ReviewItem.MAX_STRENGTH));
        box.addView(row);
        if (divider) {
            final View rule = new View(a);
            rule.setBackgroundColor(CanvasLook.surface2());
            box.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }
        return box;
    }

    /** "Smith · missed in recall" (the reason in orange), "Smith · fading, <chapter>". */
    private CharSequence line(ReviewQueue.Entry e) {
        final String who = ReviewText.who(e.author, e.bookTitle);
        final String reason = ReviewWords.reason(a, e.item.reason);
        final boolean recall = ReviewItem.MISSED.equals(e.item.reason) || ReviewItem.UNSURE.equals(e.item.reason);
        final String tail = recall || e.item.chapterTitle.isEmpty() ? reason : reason + ", " + e.item.chapterTitle;
        final SpannableString s = new SpannableString(who + " · " + tail);
        if (recall) {
            s.setSpan(new ForegroundColorSpan(CanvasLook.sensor()), s.length() - tail.length(), s.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }

    private void ui(Runnable r) {
        a.runOnUiThread(() -> {
            if (!Apps.isDestroyedActivity(a) && page.dialog.isShowing()) {
                r.run();
            }
        });
    }
}
