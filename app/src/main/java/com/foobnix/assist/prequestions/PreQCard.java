package com.foobnix.assist.prequestions;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;

/**
 * The pre-questions card at the bottom of the reader, in the canvas look (canvas "Pre-question
 * answered"): the offer before a chapter, and the answer card when the reader reaches an answer.
 * Filled anew each time: {@link #clear}, rows, buttons, then {@link #show}.
 */
final class PreQCard {

    /** Room for the annotation toolbar when it is shown (as the study card). */
    private static final int TOOLBAR_DP = 76;

    private final Activity activity;
    private final LinearLayout card;
    private final LinearLayout body;
    private final TextView close;
    private LinearLayout buttons;
    private Runnable onClose;

    PreQCard(Activity activity) {
        this.activity = activity;
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(16);
        card.setPadding(pad, CanvasLook.dp(12), CanvasLook.dp(6), pad);
        card.setElevation(CanvasLook.dp(6));
        card.setClickable(true);   // taps on the card do not turn the page
        card.setVisibility(View.GONE);

        final LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        top.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        close = new TextView(activity);
        close.setText("✕");
        CanvasParts.text(close, 16, 400);
        close.setTextColor(CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(activity.getString(com.foobnix.pdf.info.R.string.close));
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

    /** Empties the card; {@code accent} gives it the accent border of an answer. */
    void clear(boolean accent) {
        body.removeAllViews();
        buttons = null;
        onClose = null;
        final android.graphics.drawable.GradientDrawable bg = CanvasParts.round(CanvasLook.bg(),
                accent ? CanvasLook.accent() : CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP + 2));
        if (accent) {
            // the answer card's 1.5 dp accent border
            bg.setStroke(Math.max(2, Math.round(1.5f * activity.getResources().getDisplayMetrics().density)), CanvasLook.accent());
        }
        card.setBackground(bg);
        close.setTextColor(CanvasLook.muted());
    }

    /** A mono uppercase label ("ANSWERS YOUR PRE-QUESTION 1"). */
    void label(String text, boolean accent) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.label(t, accent);
        t.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(6));
        body.addView(t);
    }

    void title(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.text(t, 17, 600);
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(t);
    }

    void line(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.text(t, 13, 400);
        t.setTextColor(CanvasLook.ink2());
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        t.setPadding(0, CanvasLook.dp(8), CanvasLook.dp(10), 0);
        body.addView(t);
    }

    /** One bar of the numbers: its name, its value, and a bar {@code share} (0..1) long. */
    void bar(String name, String value, float share, boolean accent) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, CanvasLook.dp(8), CanvasLook.dp(10), 0);
        final LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.HORIZONTAL);
        final TextView n = new TextView(activity);
        n.setText(name);
        CanvasParts.text(n, 12, 400);
        n.setTextColor(CanvasLook.ink2());
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView v = new TextView(activity);
        v.setText(value);
        CanvasParts.text(v, 12, 600);
        words.addView(v);
        row.addView(words);

        final LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(5)));
        final View fill = new View(activity);
        fill.setBackground(CanvasParts.round(accent ? CanvasLook.accent() : 0xFF8A909C, 0, CanvasLook.dp(5)));
        fill.setMinimumWidth(CanvasLook.dp(6));
        final float s = Math.max(0f, Math.min(1f, share));
        track.addView(fill, new LinearLayout.LayoutParams(0, CanvasLook.dp(10), s));
        track.addView(new View(activity), new LinearLayout.LayoutParams(0, CanvasLook.dp(10), 1f - s));
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(10));
        tp.topMargin = CanvasLook.dp(4);
        row.addView(track, tp);
        body.addView(row);
    }

    /** A filled button ({@code primary}) or a plain one, on the card's button row. */
    void button(String label, boolean primary, Runnable action) {
        if (buttons == null) {
            buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = CanvasLook.dp(12);
            body.addView(buttons, p);
        }
        final TextView b = new TextView(activity);
        b.setText(label);
        CanvasParts.text(b, 14, 600);
        b.setGravity(Gravity.CENTER);
        b.setPadding(CanvasLook.dp(18), 0, CanvasLook.dp(18), 0);
        b.setMinHeight(CanvasLook.target());
        if (primary) {
            b.setBackground(CanvasParts.primaryButton());
            b.setTextColor(CanvasLook.onAccent());
        } else {
            b.setBackground(CanvasParts.ripple(null));
            b.setTextColor(CanvasLook.ink2());
        }
        b.setOnClickListener(v -> action.run());
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target());
        p.rightMargin = CanvasLook.dp(6);
        buttons.addView(b, p);
    }

    /** A small mono status line under everything ("1 of 3 answers found"). */
    void status(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.label(t, false);
        t.setPadding(0, CanvasLook.dp(12), 0, 0);
        body.addView(t);
    }

    /** Runs when the reader closes the card with ✕ (not when a page turn hides it). */
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
