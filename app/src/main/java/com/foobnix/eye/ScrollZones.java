package com.foobnix.eye;

/**
 * Scroll mode's gaze speed (D-89): reading in the top quarter means the text runs ahead, so the
 * auto-scroll waits (factor 0); in the middle it goes at the set speed (1); in the bottom quarter
 * the reader is ahead of it and it speeds up step by step to twice the set speed, easing back
 * once the eyes are in the middle again. Zone edges overlap so jitter on an edge does not
 * flicker. Pure.
 */
public final class ScrollZones {

    public enum Zone {TOP, MIDDLE, BOTTOM}

    private Zone zone = Zone.MIDDLE;
    private float factor = 1;

    /** @param y the gaze's screen y, NaN when not known (the factor goes back to 1) */
    public float update(float y, long dtMs) {
        final float dt = Math.max(0, dtMs) / 1000f;
        if (Float.isNaN(y)) {
            zone = Zone.MIDDLE;
            factor = 1;
            return factor;
        }
        switch (zone) {
            case TOP:
                if (y > EyeRules.SCROLL_TOP_LEAVE) {
                    zone = y >= EyeRules.SCROLL_BOTTOM_ENTER ? Zone.BOTTOM : Zone.MIDDLE;
                }
                break;
            case BOTTOM:
                if (y < EyeRules.SCROLL_BOTTOM_LEAVE) {
                    zone = y <= EyeRules.SCROLL_TOP_ENTER ? Zone.TOP : Zone.MIDDLE;
                }
                break;
            default:
                if (y <= EyeRules.SCROLL_TOP_ENTER) {
                    zone = Zone.TOP;
                } else if (y >= EyeRules.SCROLL_BOTTOM_ENTER) {
                    zone = Zone.BOTTOM;
                }
                break;
        }
        switch (zone) {
            case TOP:
                factor = 0;
                break;
            case BOTTOM:
                factor = Math.min(EyeRules.SCROLL_MAX_FACTOR, Math.max(1, factor) + EyeRules.SCROLL_RAMP_PER_S * dt);
                break;
            default:
                factor = factor < 1 ? 1 : Math.max(1, factor - EyeRules.SCROLL_EASE_PER_S * dt);
                break;
        }
        return factor;
    }

    public Zone zone() {
        return zone;
    }

    public float factor() {
        return factor;
    }
}
