package com.foobnix.assist.fiction;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/** The parts the fiction companion's screens and sheets share, in the canvas's tokens. */
final class FictionUi {

    private FictionUi() {
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
    static LinearLayout topBar(Context c, CharSequence titleText, CharSequence subtitleText, Runnable onBack, View right) {
        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text(c, "‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(c.getString(R.string.canvas_t29_back));
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

    static LinearLayout column(Context c) {
        final LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
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

    /** The book's own words: a serif face, as the canvas sets them in Literata. */
    static TextView serif(Context c, CharSequence s, float sp, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(Typeface.SERIF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /**
     * A small mono label. Give it text that is already in the case it should show: it does not
     * change case itself, so a chapter's own title in it stays as the book has it.
     */
    static TextView mono(Context c, CharSequence s, float sp, int color, boolean medium) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, medium ? 500 : 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.05f);
        t.setTextColor(color);
        return t;
    }

    /** A 14 sp weight 600 button, filled accent or outlined; give it a height of 48 or 52 dp. */
    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    /** A text button in the accent colour, 44 dp high. */
    static TextView link(Context c, CharSequence s) {
        final TextView t = text(c, s, 13, 600, CanvasLook.accent());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        t.setMinHeight(CanvasLook.target());
        t.setBackground(CanvasParts.ripple(null));
        return t;
    }

    /** A rounded fill with a 1 dp border, dashed when {@code dashed}. */
    static GradientDrawable outline(int fill, int stroke, int radiusDp, boolean dashed) {
        final GradientDrawable d = CanvasParts.round(fill, 0, CanvasLook.dp(radiusDp));
        final int width = Math.max(1, CanvasLook.dp(1));
        if (dashed) {
            d.setStroke(width, stroke, CanvasLook.dp(5), CanvasLook.dp(3));
        } else {
            d.setStroke(width, stroke);
        }
        return d;
    }

    /** The round avatar with a person's initials. */
    static TextView avatar(Context c, String initials, int sizeDp) {
        final TextView t = text(c, initials, sizeDp >= 44 ? 16 : 13, 600, CanvasLook.accent());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(CanvasLook.accentTint());
        d.setStroke(Math.max(1, CanvasLook.dp(1)), CanvasLook.accentLine());
        t.setBackground(d);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.setLayoutParams(new LinearLayout.LayoutParams(CanvasLook.dp(sizeDp), CanvasLook.dp(sizeDp)));
        return t;
    }

    /** A task's status with a spinner and Cancel, or (not busy) why it failed. */
    static View progressRow(Context c, boolean busy, CharSequence text, Runnable cancel) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (busy) {
            final ProgressBar spin = new ProgressBar(c);
            spin.setIndeterminateTintList(ColorStateList.valueOf(CanvasLook.accent()));
            row.addView(spin, new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20)));
        }
        final TextView t = text(c, text, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        t.setPadding(busy ? CanvasLook.dp(10) : 0, 0, CanvasLook.dp(8), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (busy && cancel != null) {
            final TextView stop = link(c, c.getString(R.string.cancel));
            stop.setOnClickListener(v -> cancel.run());
            row.addView(stop);
        }
        return row;
    }
}
