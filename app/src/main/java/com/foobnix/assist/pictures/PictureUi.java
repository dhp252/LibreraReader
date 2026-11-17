package com.foobnix.assist.pictures;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;

/** The parts the picture screen and its views share, in the canvas's tokens (day and night with the app). */
final class PictureUi {

    /** The canvas's grey block (TRY IT, THE POINT, WHAT HOLDS IT BACK), day. */
    private static final int BLOCK = 0xFFF6F7F9;
    /** The canvas's arrow grey between step boxes, day. */
    private static final int ARROW = 0xFFA5ABB6;
    /** The canvas's dashed footnote border, day. */
    private static final int DASH = 0xFFC4C8D0;

    private PictureUi() {
    }

    static int block() {
        return CanvasLook.isNight() ? CanvasLook.surface() : BLOCK;
    }

    static int arrow() {
        return CanvasLook.isNight() ? CanvasLook.muted() : ARROW;
    }

    static int dash() {
        return CanvasLook.isNight() ? CanvasLook.lineStrong() : DASH;
    }

    /** A full-screen dialog on the page colour, day or night with the app, fitted inside the bars. */
    static Dialog fullScreen(Activity a, View page) {
        final boolean dark = CanvasLook.isNight();
        final Dialog dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        page.setBackgroundColor(CanvasLook.bg());
        dialog.setContentView(page);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        return dialog;
    }

    /**
     * The 56 dp top bar: back, a centred title over a muted subtitle, and {@code right} (a 44 dp
     * button) or an empty place of the same size.
     */
    static LinearLayout topBar(Context c, CharSequence titleText, CharSequence subtitleText, CharSequence backLabel,
                               Runnable onBack, View right) {
        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text(c, "‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(backLabel);
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> onBack.run());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        // titles as the book has them
        final TextView title = text(c, titleText, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title);
        final TextView sub = text(c, subtitleText, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sub.setVisibility(TextUtils.isEmpty(subtitleText) ? View.GONE : View.VISIBLE);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(right != null ? right : new View(c), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    /** Full width, wrapping its height, with margins in dp. */
    static LinearLayout.LayoutParams row(int left, int top, int right, int bottom) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(CanvasLook.dp(left), CanvasLook.dp(top), CanvasLook.dp(right), CanvasLook.dp(bottom));
        return lp;
    }

    /** Interface text (Instrument Sans). */
    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** The author's words: a serif italic, as the canvas sets "FROM THE TEXT" in Literata. */
    static TextView quote(Context c, CharSequence s) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(0, 1.3f);
        t.setTextColor(CanvasLook.ink2());
        return t;
    }

    /** A small uppercase mono label. Never give it a book or chapter title: it would change its case. */
    static TextView mono(Context c, CharSequence s, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        t.setLetterSpacing(0.05f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** A 14 sp weight 600 button, filled accent or outlined; give it a height of 48 dp. */
    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        style(t, primary);
        return t;
    }

    static void style(TextView button, boolean primary) {
        button.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        button.setTextColor(primary ? CanvasLook.onAccent() : CanvasLook.ink());
    }

    /** Dims a button that does nothing now and stops its taps. */
    static void enable(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
    }

    /** A rounded fill with a dashed 1 dp border. */
    static GradientDrawable dashed(int fill, int stroke, int radiusDp) {
        final GradientDrawable d = CanvasParts.round(fill, 0, CanvasLook.dp(radiusDp));
        d.setStroke(Math.max(1, CanvasLook.dp(1)), stroke, CanvasLook.dp(5), CanvasLook.dp(3));
        return d;
    }

    /** An empty vertical block. */
    static LinearLayout column(Context c) {
        final LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
}
