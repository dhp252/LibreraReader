package com.foobnix.canvas;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * The canvas's building blocks as drawables and small view helpers, so every screen draws its
 * cards, chips, buttons and sheets the same way. Callers check {@link CanvasLook#isOn()} first.
 */
public final class CanvasParts {

    private CanvasParts() {
    }

    /** A rounded fill, optionally with a 1 dp border (0 = none). */
    public static GradientDrawable round(int fill, int border, int radiusPx) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fill);
        if (border != 0) {
            d.setStroke(Math.max(1, CanvasLook.dp(1)), border);
        }
        return d;
    }

    /** Only the top corners rounded: a bottom sheet. */
    public static GradientDrawable sheet() {
        final GradientDrawable d = new GradientDrawable();
        final float r = CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP + 2);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        d.setColor(CanvasLook.bg());
        return d;
    }

    /** A card: the light surface with a hairline border. */
    public static Drawable card() {
        return round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.radius());
    }

    /** The tinted accent box ("This section answers"). */
    public static Drawable accentCard() {
        return round(CanvasLook.accentTint(), 0, CanvasLook.radius());
    }

    /** The track of a segmented control. */
    public static Drawable segmentTrack() {
        return round(CanvasLook.surface2(), 0, CanvasLook.dp(13));
    }

    /** The chosen segment of a segmented control. */
    public static Drawable segmentOn() {
        return round(CanvasLook.bg(), 0, CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP));
    }

    /** A filled accent button, with a pressed state. */
    public static Drawable primaryButton() {
        return pressable(round(CanvasLook.accent(), 0, CanvasLook.radius()),
                round(CanvasLook.accentPressed(), 0, CanvasLook.radius()));
    }

    /** An outlined button on the page colour. */
    public static Drawable secondaryButton() {
        return pressable(round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.radius()),
                round(CanvasLook.surface2(), CanvasLook.lineStrong(), CanvasLook.radius()));
    }

    /** A chip: on = accent tint with accent border, off = page colour with hairline. */
    public static Drawable chip(boolean on) {
        return on ? round(CanvasLook.accentTint(), CanvasLook.accentLine(), CanvasLook.dp(18))
                : round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(18));
    }

    /** A rounded search or text field. */
    public static Drawable field() {
        return round(CanvasLook.surface2(), 0, CanvasLook.radius());
    }

    /** A thin progress track (4 dp tall drawn by the caller's view). */
    public static Drawable progressTrack() {
        return round(CanvasLook.line(), 0, CanvasLook.dp(2));
    }

    /** A ripple in ink over {@code content}, for plain rows and icon buttons. */
    public static Drawable ripple(Drawable content) {
        final int ripple = (CanvasLook.ink() & 0x00FFFFFF) | 0x1F000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), content,
                round(0xFF000000, 0, CanvasLook.radius()));
    }

    private static Drawable pressable(Drawable normal, Drawable pressed) {
        final StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    // text helpers

    /** Interface text: Instrument Sans in ink. */
    public static void text(TextView v, float sp, int weight) {
        v.setTypeface(CanvasLook.ui(v.getContext(), weight));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(CanvasLook.ink());
    }

    /** Secondary text: smaller, muted. */
    public static void secondary(TextView v) {
        v.setTypeface(CanvasLook.ui(v.getContext(), 400));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
        v.setTextColor(CanvasLook.muted());
    }

    /** A JetBrains Mono uppercase label ("THIS SECTION ANSWERS"); accent or muted. */
    public static void label(TextView v, boolean accent) {
        v.setTypeface(CanvasLook.mono(v.getContext(), 500));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        v.setAllCaps(true);
        v.setLetterSpacing(0.04f);
        v.setTextColor(accent ? CanvasLook.accent() : CanvasLook.muted());
    }

    /** At least the 44 dp touch target. */
    public static void target(View v) {
        v.setMinimumHeight(CanvasLook.target());
        v.setMinimumWidth(CanvasLook.target());
    }
}
