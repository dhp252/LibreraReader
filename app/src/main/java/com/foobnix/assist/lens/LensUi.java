package com.foobnix.assist.lens;

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
import com.foobnix.pdf.info.R;

/** The parts the lens's three screens and its sheet share, in the canvas's tokens. */
final class LensUi {

    private LensUi() {
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

    /** A vertical page for {@link #fullScreen}. */
    static LinearLayout page(Context c) {
        final LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        return page;
    }

    /**
     * The 56 dp top bar: back, a centred title over a muted subtitle, and {@code right} (a 44 dp
     * button) or an empty place of the same size. Add it with {@link #barParams()}.
     */
    static LinearLayout topBar(Context c, CharSequence titleText, CharSequence subtitleText, Runnable onBack, View right) {
        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text(c, "‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(c.getString(R.string.lens_back));
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> onBack.run());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
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

    static LinearLayout.LayoutParams barParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP));
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

    /** The author's words: a serif face, as the canvas sets them in Literata. */
    static TextView serif(Context c, CharSequence s, float sp, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(Typeface.SERIF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** A small uppercase mono label. Never give it a book or chapter title: it would change its case. */
    static TextView mono(Context c, CharSequence s, float sp, int color, boolean medium) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, medium ? 500 : 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.05f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** A 14 sp weight 600 button, filled accent or outlined; give it a height of 48 or 52 dp. */
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

    /** A rounded fill with a border of {@code strokeDp10} tenths of a dp, dashed when {@code dashed}. */
    static GradientDrawable outline(int fill, int stroke, int strokeDp10, int radiusDp, boolean dashed) {
        final GradientDrawable d = CanvasParts.round(fill, 0, CanvasLook.dp(radiusDp));
        final int width = Math.max(1, CanvasLook.dp(strokeDp10) / 10);
        if (dashed) {
            d.setStroke(width, stroke, CanvasLook.dp(5), CanvasLook.dp(3));
        } else {
            d.setStroke(width, stroke);
        }
        return d;
    }

    /** A 1 dp divider in the given colour. */
    static View line(Context c, int color) {
        final View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));
        return v;
    }
}
