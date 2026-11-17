package com.foobnix.canvas;

import android.content.Context;
import android.graphics.Typeface;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;

/**
 * The look of the "Book That Reads You Back" canvas for the whole app: its colour tokens (day and
 * night), shapes, sizes and fonts.
 * <p>
 * Every screen that takes this look asks {@link #isOn()} first and keeps its old code path when it
 * is off, so the switch ({@link AppState#isCanvasLook}, Preferences) brings back the app as it was.
 * Ink (e-ink) keeps the old look.
 */
public final class CanvasLook {

    // day
    public static final int BG = 0xFFFFFFFF;
    public static final int SURFACE = 0xFFFAFAFB;
    public static final int SURFACE_2 = 0xFFF1F2F5;
    public static final int LINE = 0xFFE3E5EA;
    public static final int LINE_STRONG = 0xFFD5D8DE;
    public static final int INK = 0xFF14161B;
    public static final int INK_2 = 0xFF464C57;
    public static final int MUTED = 0xFF5E6570;
    public static final int ACCENT = 0xFF2F45E0;
    public static final int ACCENT_PRESSED = 0xFF2337C4;
    public static final int ACCENT_TINT = 0xFFEEF0FE;
    public static final int ACCENT_LINE = 0xFFC9D0FB;
    public static final int CLAIM_TINT = 0xFFE3E7FD;
    public static final int SENSOR = 0xFFB4460A;
    public static final int SENSOR_TINT = 0xFFF7B98C;

    // night
    public static final int BG_N = 0xFF14161B;
    public static final int SURFACE_N = 0xFF1B1E24;
    public static final int SURFACE_2_N = 0xFF23262D;
    public static final int LINE_N = 0xFF2A2D35;
    public static final int LINE_STRONG_N = 0xFF3A3E48;
    public static final int INK_N = 0xFFE9EBEF;
    public static final int INK_2_N = 0xFFC3C7CF;
    public static final int MUTED_N = 0xFF9AA0AA;
    public static final int ACCENT_N = 0xFFAEB9FF;
    public static final int ACCENT_PRESSED_N = 0xFFC8D0FF;
    public static final int ACCENT_TINT_N = 0xFF23284A;
    public static final int ACCENT_LINE_N = 0xFF3A4180;
    public static final int CLAIM_TINT_N = 0xFF2A3060;
    public static final int SENSOR_N = 0xFFF2A46B;
    public static final int SENSOR_TINT_N = 0xFF5A3418;

    // shapes and sizes, dp
    public static final int RADIUS_DP = 12;
    public static final int RADIUS_LARGE_DP = 14;
    public static final int RADIUS_SMALL_DP = 10;
    public static final int TARGET_DP = 44;
    public static final int GUTTER_DP = 16;
    public static final int BAR_DP = 56;
    public static final int STRIP_DP = 52;

    // type, sp
    public static final float TEXT_TITLE = 14f;
    public static final float TEXT_BODY = 15f;
    public static final float TEXT_SMALL = 12f;
    public static final float TEXT_LABEL = 11f;
    public static final float TEXT_HEADING = 20f;

    public static final String FONTS = "fonts-ui/";

    private static Typeface ui, uiMedium, uiSemiBold, uiBold, mono, monoMedium;

    private CanvasLook() {
    }

    /** The canvas look is used: the switch is on and the theme is not Ink. */
    public static boolean isOn() {
        final AppState s = AppState.get();
        return s.isCanvasLook && s.appTheme != AppState.THEME_INK;
    }

    /** The app's interface is dark (Dark or Dark OLED theme). */
    public static boolean isNight() {
        final int t = AppState.get().appTheme;
        return t == AppState.THEME_DARK || t == AppState.THEME_DARK_OLED;
    }

    public static int bg() {
        if (!isNight()) {
            return BG;
        }
        return AppState.get().appTheme == AppState.THEME_DARK_OLED ? 0xFF000000 : BG_N;
    }

    public static int surface() {
        return isNight() ? SURFACE_N : SURFACE;
    }

    public static int surface2() {
        return isNight() ? SURFACE_2_N : SURFACE_2;
    }

    public static int line() {
        return isNight() ? LINE_N : LINE;
    }

    public static int lineStrong() {
        return isNight() ? LINE_STRONG_N : LINE_STRONG;
    }

    public static int ink() {
        return isNight() ? INK_N : INK;
    }

    public static int ink2() {
        return isNight() ? INK_2_N : INK_2;
    }

    public static int muted() {
        return isNight() ? MUTED_N : MUTED;
    }

    public static int accent() {
        return isNight() ? ACCENT_N : ACCENT;
    }

    public static int accentPressed() {
        return isNight() ? ACCENT_PRESSED_N : ACCENT_PRESSED;
    }

    public static int accentTint() {
        return isNight() ? ACCENT_TINT_N : ACCENT_TINT;
    }

    public static int accentLine() {
        return isNight() ? ACCENT_LINE_N : ACCENT_LINE;
    }

    public static int claimTint() {
        return isNight() ? CLAIM_TINT_N : CLAIM_TINT;
    }

    /** The "reading you" (sensor) colour. */
    public static int sensor() {
        return isNight() ? SENSOR_N : SENSOR;
    }

    public static int sensorTint() {
        return isNight() ? SENSOR_TINT_N : SENSOR_TINT;
    }

    /** Text on an accent fill. */
    public static int onAccent() {
        return isNight() ? INK : 0xFFFFFFFF;
    }

    public static int dp(int dp) {
        return Dips.dpToPx(dp);
    }

    public static int radius() {
        return dp(RADIUS_DP);
    }

    public static int target() {
        return dp(TARGET_DP);
    }

    // fonts: loaded once from assets, null-safe (the system font when a file cannot be read)

    /** Instrument Sans, the interface font; weight 400, 500, 600 or 700. */
    public static Typeface ui(Context c, int weight) {
        loadFonts(c);
        if (weight >= 700) {
            return uiBold;
        }
        if (weight >= 600) {
            return uiSemiBold;
        }
        if (weight >= 500) {
            return uiMedium;
        }
        return ui;
    }

    /** JetBrains Mono, the small uppercase labels; weight 400 or 500. */
    public static Typeface mono(Context c, int weight) {
        loadFonts(c);
        return weight >= 500 ? monoMedium : mono;
    }

    private static synchronized void loadFonts(Context c) {
        if (ui != null) {
            return;
        }
        ui = font(c, "InstrumentSans-Regular.ttf", Typeface.DEFAULT);
        uiMedium = font(c, "InstrumentSans-Medium.ttf", ui);
        uiSemiBold = font(c, "InstrumentSans-SemiBold.ttf", Typeface.DEFAULT_BOLD);
        uiBold = font(c, "InstrumentSans-Bold.ttf", Typeface.DEFAULT_BOLD);
        mono = font(c, "JetBrainsMono-Regular.ttf", Typeface.MONOSPACE);
        monoMedium = font(c, "JetBrainsMono-Medium.ttf", mono);
    }

    private static Typeface font(Context c, String name, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getApplicationContext().getAssets(), FONTS + name);
        } catch (Exception e) {
            LOG.e(e);
            return fallback;
        }
    }
}
