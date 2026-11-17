package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * The parts the screens of "The book reads you back" are built from, all in the canvas's tokens:
 * interface text, mono labels, the book serif, buttons, chips and the full-screen page with its
 * top bar.
 */
final class ReadsUi {

    /** The app's reading fonts in the assets (as Semantic zoom's book text). */
    private static final String SERIF = "fonts-fork/Literata-Regular.ttf";
    private static Typeface serif;

    private ReadsUi() {
    }

    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** A small uppercase label in JetBrains Mono. */
    static TextView mono(Context c, CharSequence s, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        t.setLetterSpacing(0.04f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** The book's own words, in the book serif. */
    static TextView book(Context c, CharSequence s, float sp, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(serif(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.4f);
        t.setTextColor(color);
        return t;
    }

    private static synchronized Typeface serif(Context c) {
        if (serif == null) {
            try {
                serif = Typeface.createFromAsset(c.getApplicationContext().getAssets(), SERIF);
            } catch (Exception e) {
                LOG.e(e);
            }
            if (serif == null) {
                serif = Typeface.SERIF;
            }
        }
        return serif;
    }

    /** A 44 dp button: filled accent, or outlined. */
    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(CanvasLook.target());
        t.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    /** A small chip that only says something (not a button). */
    static TextView chip(Context c, CharSequence s) {
        final TextView t = text(c, s, CanvasLook.TEXT_SMALL, 500, CanvasLook.ink2());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(CanvasLook.dp(28));
        t.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        t.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(14)));
        return t;
    }

    static LinearLayout.LayoutParams matchWrap(int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }

    static LinearLayout.LayoutParams wrap(int topDp, int rightDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        lp.rightMargin = CanvasLook.dp(rightDp);
        return lp;
    }

    /** A full-screen dialog in the canvas look around {@code page} (not shown yet). */
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

    /** The top bar of a full-screen page: back, a title and a subtitle. */
    static LinearLayout topBar(Context c, CharSequence title, CharSequence subtitle, Runnable onBack) {
        final LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setMinimumHeight(CanvasLook.dp(CanvasLook.BAR_DP));
        top.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text(c, "‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(c.getString(R.string.canvas_t31_back));
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> onBack.run());
        top.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView t = text(c, title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(t);
        if (!TextUtils.isEmpty(subtitle)) {
            final TextView s = text(c, subtitle, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            s.setGravity(Gravity.CENTER);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            titles.addView(s);
        }
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(new View(c), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return top;
    }

    /** "0:41", "12:05". */
    static String clock(long ms) {
        final long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
