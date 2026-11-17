package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.List;

/**
 * The card of "The book reads you back" at the bottom of the reader (canvas "Fatigue: take a
 * break"): a mono label, a title, a line, chips with what was measured, buttons one under the
 * other, a link and ✕. It is laid over the page as the other features' cards are (a raised,
 * vertical, clickable box), so their "is another card showing" checks see it.
 * Filled anew each time: {@link #clear}, rows, then {@link #show}.
 */
final class ReadsCard {

    /** Room for the annotation toolbar when it is shown (as the study card). */
    private static final int TOOLBAR_DP = 76;

    private final Activity activity;
    private final LinearLayout card;
    private final LinearLayout body;
    private Runnable onClose;

    ReadsCard(Activity activity) {
        this.activity = activity;
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(16);
        card.setPadding(pad, CanvasLook.dp(12), CanvasLook.dp(6), pad);
        card.setElevation(CanvasLook.dp(6));
        card.setClickable(true);   // taps on the card do not turn the page
        card.setVisibility(View.GONE);
        card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(),
                CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP + 2)));

        final LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        top.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = ReadsUi.text(activity, "✕", 16, 400, CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(activity.getString(R.string.close));
        close.setOnClickListener(v -> close());
        top.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.dp(32)));
        card.addView(top);

        final FrameLayout content = activity.findViewById(android.R.id.content);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(com.foobnix.android.utils.Dips.screenWidth() - CanvasLook.dp(24), CanvasLook.dp(520)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = CanvasLook.dp(12);
        content.addView(card, params);
    }

    /** The card's own view, for the check whether another card shows. */
    View view() {
        return card;
    }

    void clear() {
        body.removeAllViews();
        onClose = null;
    }

    /** A mono uppercase label in the sensor colour ("READING YOU · LONG SESSION"). */
    void label(String text) {
        final TextView t = ReadsUi.mono(activity, text, CanvasLook.sensor());
        t.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(6));
        body.addView(t);
    }

    void title(String text) {
        final TextView t = ReadsUi.text(activity, text, 17, 600, CanvasLook.ink());
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(t);
    }

    void line(String text) {
        final TextView t = ReadsUi.text(activity, text, 13, 400, CanvasLook.ink2());
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        t.setPadding(0, CanvasLook.dp(6), CanvasLook.dp(10), 0);
        body.addView(t);
    }

    /** What was measured, one chip each; nothing for an empty list. */
    void chips(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String s : texts) {
            row.addView(ReadsUi.chip(activity, s), ReadsUi.wrap(0, 6));
        }
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        body.addView(scroll, ReadsUi.matchWrap(10));
    }

    /**
     * A button across the card.
     *
     * @param sub a second, smaller line inside it, or null
     */
    void button(String label, String sub, boolean primary, Runnable action) {
        final LinearLayout b = new LinearLayout(activity);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(CanvasLook.target());
        b.setPadding(CanvasLook.dp(14), CanvasLook.dp(6), CanvasLook.dp(14), CanvasLook.dp(6));
        b.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        final TextView t = ReadsUi.text(activity, label, CanvasLook.TEXT_TITLE, 600,
                primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        b.addView(t);
        if (sub != null && !sub.isEmpty()) {
            final TextView s = ReadsUi.text(activity, sub, CanvasLook.TEXT_SMALL, 400,
                    primary ? CanvasLook.onAccent() : CanvasLook.muted());
            s.setGravity(Gravity.CENTER);
            b.addView(s);
        }
        b.setOnClickListener(v -> action.run());
        final LinearLayout.LayoutParams lp = ReadsUi.matchWrap(8);
        lp.rightMargin = CanvasLook.dp(10);
        body.addView(b, lp);
    }

    /** A plain text button ("Keep reading", "Before you go: say one thing you remember"). */
    void link(String label, boolean accent, Runnable action) {
        final TextView t = ReadsUi.text(activity, label, CanvasLook.TEXT_TITLE, 600,
                accent ? CanvasLook.accent() : CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(CanvasLook.target());
        t.setBackground(CanvasParts.ripple(null));
        t.setOnClickListener(v -> action.run());
        final LinearLayout.LayoutParams lp = ReadsUi.matchWrap(4);
        lp.rightMargin = CanvasLook.dp(10);
        body.addView(t, lp);
    }

    /** A small muted note under everything. */
    void note(String text) {
        final TextView t = ReadsUi.text(activity, text, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        t.setPadding(0, CanvasLook.dp(8), CanvasLook.dp(10), 0);
        body.addView(t);
    }

    /** Runs when the reader closes the card with ✕ (not when it is hidden from code). */
    void onClose(Runnable r) {
        onClose = r;
    }

    void show(boolean aboveToolbar) {
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = CanvasLook.dp(12 + (aboveToolbar ? TOOLBAR_DP : 0));
        card.setLayoutParams(params);
        card.animate().cancel();
        card.setAlpha(0);
        card.setVisibility(View.VISIBLE);
        card.bringToFront();
        card.animate().alpha(1).setDuration(180).start();
    }

    boolean isShown() {
        return card.getVisibility() == View.VISIBLE;
    }

    void hide() {
        if (card.getVisibility() != View.VISIBLE) {
            return;
        }
        card.animate().cancel();
        card.animate().alpha(0).setDuration(150).withEndAction(() -> card.setVisibility(View.GONE)).start();
    }

    private void close() {
        final Runnable r = onClose;
        hide();
        if (r != null) {
            r.run();
        }
    }

    void remove() {
        final ViewGroup parent = (ViewGroup) card.getParent();
        if (parent != null) {
            parent.removeView(card);
        }
    }
}
