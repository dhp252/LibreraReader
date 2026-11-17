package com.foobnix.assist.zoom;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;

/**
 * The parts the zoom view is built from: the book serif for the book's text (Literata from the
 * app's own fonts, the system serif when it cannot be read) and the canvas's text styles for the
 * interface around it.
 */
final class ZoomLook {

    /** The app's reading fonts in the assets (see FontExtractor). */
    private static final String FONTS = "fonts-fork/";

    private static Typeface serif;
    private static Typeface serifItalic;

    private ZoomLook() {
    }

    /** The book serif. */
    static synchronized Typeface serif(Context c, boolean italic) {
        if (serif == null) {
            serif = font(c, "Literata-Regular.ttf", Typeface.SERIF);
            serifItalic = font(c, "Literata-Italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        }
        return italic ? serifItalic : serif;
    }

    private static Typeface font(Context c, String name, Typeface fallback) {
        try {
            final Typeface t = Typeface.createFromAsset(c.getApplicationContext().getAssets(), FONTS + name);
            return t == null ? fallback : t;
        } catch (Exception e) {
            LOG.e(e);
            return fallback;
        }
    }

    /** Interface text in Instrument Sans. */
    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** Book text in the book serif; {@code lineHeight} is a multiple of the text size (the canvas's 28/17). */
    static TextView book(Context c, CharSequence s, float sp, float lineHeight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(serif(c, false));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, lineHeight);
        t.setTextColor(color);
        return t;
    }

    /** A small uppercase label in JetBrains Mono. */
    static TextView mono(Context c, CharSequence s, float sp, int color, boolean medium) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, medium ? 500 : 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.04f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** A 44 dp button: filled accent, or outlined on the page colour. */
    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(CanvasLook.target());
        t.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    /** A fill with a dashed border (the canvas's folded part). */
    static GradientDrawable dashed(int fill, int border, int radiusPx) {
        final GradientDrawable d = CanvasParts.round(fill, 0, radiusPx);
        d.setStroke(Math.max(1, CanvasLook.dp(1)), border, CanvasLook.dp(4), CanvasLook.dp(3));
        return d;
    }
}
