package com.foobnix.canvas.dialogs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.TintUtil;

/**
 * The canvas's colour tokens for the surface a dialog or panel actually stands on.
 * <p>
 * {@link CanvasLook} picks day or night by the app's theme; a dialog takes the theme of its
 * window, which in the page-at-a-time reader follows the book's day/night instead. Text the
 * platform draws in a dialog (messages, list rows, custom views) follows that window theme, so
 * the restyle asks the same surface: night tokens on a dark window (Dark, Dark OLED, a night
 * book), day tokens on a light one. Night on the Dark OLED theme is black.
 */
final class Tone {

    final boolean night;

    private Tone(boolean night) {
        this.night = night;
    }

    static Tone of(Context c) {
        if (c == null) {
            return new Tone(CanvasLook.isNight());
        }
        return new Tone(!TintUtil.isLightSurface(c, android.R.attr.colorBackground));
    }

    int bg() {
        if (!night) {
            return CanvasLook.BG;
        }
        return AppState.get().appTheme == AppState.THEME_DARK_OLED ? 0xFF000000 : CanvasLook.BG_N;
    }

    int surface() {
        return night ? CanvasLook.SURFACE_N : CanvasLook.SURFACE;
    }

    int surface2() {
        return night ? CanvasLook.SURFACE_2_N : CanvasLook.SURFACE_2;
    }

    int line() {
        return night ? CanvasLook.LINE_N : CanvasLook.LINE;
    }

    int lineStrong() {
        return night ? CanvasLook.LINE_STRONG_N : CanvasLook.LINE_STRONG;
    }

    int ink() {
        return night ? CanvasLook.INK_N : CanvasLook.INK;
    }

    int ink2() {
        return night ? CanvasLook.INK_2_N : CanvasLook.INK_2;
    }

    int muted() {
        return night ? CanvasLook.MUTED_N : CanvasLook.MUTED;
    }

    int accent() {
        return night ? CanvasLook.ACCENT_N : CanvasLook.ACCENT;
    }

    int accentPressed() {
        return night ? CanvasLook.ACCENT_PRESSED_N : CanvasLook.ACCENT_PRESSED;
    }

    int accentTint() {
        return night ? CanvasLook.ACCENT_TINT_N : CanvasLook.ACCENT_TINT;
    }

    int accentLine() {
        return night ? CanvasLook.ACCENT_LINE_N : CanvasLook.ACCENT_LINE;
    }

    /** Text on an accent fill. */
    int onAccent() {
        return night ? CanvasLook.INK : 0xFFFFFFFF;
    }

    // drawables

    Drawable card() {
        return CanvasParts.round(surface(), line(), CanvasLook.radius());
    }

    Drawable accentCard() {
        return CanvasParts.round(accentTint(), 0, CanvasLook.radius());
    }

    Drawable sheet() {
        return CanvasParts.round(bg(), night ? line() : 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP + 2));
    }

    Drawable field() {
        return CanvasParts.round(surface2(), 0, CanvasLook.radius());
    }

    /** Accent fill; pressed darker; disabled a quiet grey. */
    Drawable primary() {
        final StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, CanvasParts.round(lineStrong(), 0, CanvasLook.radius()));
        s.addState(new int[]{android.R.attr.state_pressed}, CanvasParts.round(accentPressed(), 0, CanvasLook.radius()));
        s.addState(new int[]{}, CanvasParts.round(accent(), 0, CanvasLook.radius()));
        return s;
    }

    /** Outlined on the page colour, the canvas's secondary button and speed chip. */
    Drawable outlined() {
        final StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, CanvasParts.round(surface2(), lineStrong(), CanvasLook.radius()));
        s.addState(new int[]{}, CanvasParts.round(bg(), lineStrong(), CanvasLook.radius()));
        return s;
    }

    /** A ripple in ink over nothing: text buttons and plain rows. */
    Drawable ripple() {
        return ripple(null, CanvasLook.radius());
    }

    Drawable ripple(Drawable content, int radius) {
        final int color = (ink() & 0x00FFFFFF) | 0x1F000000;
        return new RippleDrawable(ColorStateList.valueOf(color), content, CanvasParts.round(0xFF000000, 0, radius));
    }

    static GradientDrawable oval(int fill) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** A round ripple button: {@code fill} 0 for a transparent one. */
    Drawable roundButton(int fill) {
        final int color = fill == 0 ? (ink() & 0x00FFFFFF) | 0x1F000000 : (onAccent() & 0x00FFFFFF) | 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(color), fill == 0 ? null : oval(fill), oval(0xFF000000));
    }

    // text

    /** Instrument Sans at a size and weight, in a colour. */
    void text(TextView v, float sp, int weight, int color) {
        if (v == null) {
            return;
        }
        v.setTypeface(CanvasLook.ui(v.getContext(), weight));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setAllCaps(false);
        v.setLetterSpacing(0f);
    }

    /** The JetBrains Mono uppercase label, accent or muted. */
    void label(TextView v, boolean accent) {
        if (v == null) {
            return;
        }
        v.setTypeface(CanvasLook.mono(v.getContext(), 500));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        v.setAllCaps(true);
        v.setLetterSpacing(0.04f);
        v.setTextColor(accent ? accent() : muted());
    }

    /** JetBrains Mono for numbers that tick: times, speeds. */
    void mono(TextView v, float sp, int color) {
        if (v == null) {
            return;
        }
        v.setTypeface(CanvasLook.mono(v.getContext(), 400));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
    }

    /** Text colours with a disabled state. */
    ColorStateList textColors(int color) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{muted(), color});
    }
}
