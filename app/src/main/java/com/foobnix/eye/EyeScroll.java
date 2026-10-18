package com.foobnix.eye;

/**
 * The speed factor Eye mode gives scroll mode's auto-scroll ({@link ScrollZones}): read by the
 * auto-scroll thread of {@code VerticalModeController} on every step. 1 = the set speed, 0 = wait.
 * The set speed itself ({@code AppState.autoScrollSpeed}) is never changed (D-89).
 */
public final class EyeScroll {

    private static volatile float factor = 1;

    private EyeScroll() {
    }

    public static float factor() {
        return factor;
    }

    static void set(float f) {
        factor = f;
    }

    /** How long the auto-scroll thread sleeps between its 1-pixel steps with the factor applied. */
    public static long sleepMs(long baseMs) {
        final float f = factor;
        if (f <= 0) {
            return 50;
        }
        return Math.max(2, Math.round(baseMs / f));
    }
}
