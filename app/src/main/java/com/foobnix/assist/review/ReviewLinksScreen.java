package com.foobnix.assist.review;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.List;

/**
 * Connections, full screen (the ✦ menu's row): the connections waiting for a look, those the
 * reader put on their map (each with their own sentence), and "Find connections", the one tap
 * that sends anything: this book's stored key ideas and the stored ideas or chapter lines of up
 * to 8 other books of the library, once ({@link ReviewLinkJob}). A tap on a connection opens its
 * card ({@link ReviewLinkScreen}).
 */
final class ReviewLinksScreen implements ReviewLinkJob.Screen {

    private final Activity a;
    private final DocumentController dc;
    private ReviewPage page;
    private ReviewLinkJob job;
    private List<ReviewLink> links = new ArrayList<>();
    /** What the last search said, shown above the button; or null. */
    private String note;

    private ReviewLinksScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
    }

    static void show(Activity a, DocumentController dc) {
        if (!Assist.REVIEW.isOn() || a == null || dc == null || dc.getCurrentBook() == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        final ReviewLinksScreen s = new ReviewLinksScreen(a, dc);
        s.page = new ReviewPage(a, a.getString(R.string.canvas_t25_connections), dc.getTitle(), false);
        s.page.dialog.setOnDismissListener(d -> {
            if (s.job != null) {
                s.job.cancel();
            }
        });
        s.page.dialog.show();
        s.load();
    }

    private void load() {
        ReviewStore.IO.execute(() -> {
            final List<ReviewLink> all = ReviewStore.links();
            ui(() -> {
                links = all;
                fill();
            });
        });
    }

    private void fill() {
        page.clear();
        final List<ReviewLink> waiting = new ArrayList<>();
        final List<ReviewLink> added = new ArrayList<>();
        for (ReviewLink l : links) {
            if (l.isNew()) {
                waiting.add(l);
            } else if (ReviewLink.ADDED.equals(l.state)) {
                added.add(l);
            }
        }
        if (!waiting.isEmpty()) {
            section(R.string.canvas_t25_section_new);
            for (ReviewLink l : waiting) {
                row(l, true);
            }
        }
        if (!added.isEmpty()) {
            section(R.string.canvas_t25_section_map);
            for (ReviewLink l : added) {
                row(l, false);
            }
        }
        if (waiting.isEmpty() && added.isEmpty()) {
            final TextView t = ReviewPage.text(a, a.getString(R.string.canvas_t25_empty), 14, 400, CanvasLook.ink2());
            t.setLineSpacing(dp(3), 1f);
            t.setPadding(dp(4), 0, dp(4), 0);
            page.add(t, 16);
        }

        final boolean busy = job != null;
        final TextView sends = ReviewPage.text(a, note != null && !busy ? note : a.getString(R.string.canvas_t25_sends), 12, 400,
                CanvasLook.muted());
        sends.setLineSpacing(dp(2), 1f);
        sends.setPadding(dp(4), 0, dp(4), dp(10));
        page.actions.addView(sends);
        final TextView find = ReviewPage.primary(a, a.getString(busy ? R.string.canvas_t25_finding : R.string.canvas_t25_find));
        ReviewPage.enable(find, !busy);
        find.setOnClickListener(v -> find());
        page.actions.addView(find, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    }

    // ------------------------------------------------------------------ the request

    private void find() {
        if (job != null || dc.getCurrentBook() == null) {
            return;
        }
        com.foobnix.dao2.FileMeta meta = null;
        try {
            meta = dc.getBookFileMeta();
        } catch (Exception e) {
            LOG.e(e);
        }
        note = null;
        job = new ReviewLinkJob(a, AiJobs.Target.of(dc), meta, this);
        job.start();
        fill();
    }

    @Override
    public void onLinks(List<ReviewLink> fresh, int found) {
        job = null;
        note = fresh.isEmpty() ? a.getString(found == 0 ? R.string.canvas_t25_none_found : R.string.canvas_t25_none_new)
                : null;
        if (!fresh.isEmpty()) {
            // the first new one opens at once; the list under it shows the rest
            ReviewLinkScreen.show(a, fresh.get(0), this::load);
        }
        load();
    }

    @Override
    public void onNothingToSend(boolean mine) {
        job = null;
        note = a.getString(mine ? R.string.canvas_t25_no_ideas_here : R.string.canvas_t25_no_other_books);
        fill();
    }

    @Override
    public void onNeedSettings() {
        job = null;
        note = a.getString(R.string.canvas_t25_no_ai);
        fill();
    }

    @Override
    public void onError(String message) {
        job = null;
        note = message;
        Toast.makeText(a, message, Toast.LENGTH_LONG).show();
        fill();
    }

    // ------------------------------------------------------------------ parts

    private void section(int name) {
        final TextView t = ReviewPage.label(a, a.getString(name), false);
        t.setPadding(dp(4), 0, 0, 0);
        page.add(t, 16);
    }

    /** A connection: what joins the two ideas, the ideas with their books, and the reader's sentence. */
    private void row(final ReviewLink l, boolean waiting) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = dp(14);
        card.setPadding(pad, dp(12), pad, dp(12));
        card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(),
                waiting ? CanvasLook.accentLine() : CanvasLook.line(), dp(14))));
        final boolean same = ReviewLink.SAME.equals(l.relation);
        final boolean disagrees = ReviewLink.DISAGREES.equals(l.relation);
        final TextView word = ReviewPage.text(a, a.getString(same ? R.string.canvas_t25_word_same : disagrees
                ? R.string.canvas_t25_word_disagrees : R.string.canvas_t25_word_extends), 12, 600,
                disagrees ? CanvasLook.sensor() : CanvasLook.accent());
        card.addView(word);
        card.addView(idea(l.a));
        card.addView(idea(l.b));
        if (!l.sentence.isEmpty()) {
            final TextView own = ReviewPage.text(a, l.sentence, 13, 400, CanvasLook.ink2());
            own.setLineSpacing(dp(2), 1f);
            own.setPadding(0, dp(6), 0, 0);
            card.addView(own);
        }
        card.setOnClickListener(v -> ReviewLinkScreen.show(a, l, this::load));
        page.add(card, 8);
    }

    private View idea(ReviewLink.Side s) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, 0);
        final TextView idea = ReviewPage.text(a, s.idea, 14, 600, CanvasLook.ink());
        idea.setMaxLines(3);
        idea.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(idea);
        final TextView book = ReviewPage.text(a, s.bookTitle, 12, 400, CanvasLook.muted());
        book.setSingleLine(true);
        book.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(book);
        return box;
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
