package com.foobnix.eye;

/**
 * Book mode's gaze page turn (D-85, D-86, D-87): the eyes rest on the last lines (the bottom zone)
 * for {@link EyeRules#BOTTOM_DWELL_MS}, then jump to the top zone within
 * {@link EyeRules#JUMP_MS} of leaving it: the reader finished the page and looks for the next
 * one. Never in the first half of the page's expected time (a glance at the clock or the title
 * is not a finished page); in two-page view only the bottom of the second page counts. Pure.
 */
public final class BottomTopTurn {

    private long pageAt = -1;
    private long expectedMs = -1;
    private long bottomSince = -1;
    private boolean armed;
    private long leftBottomAt = -1;

    /** A page was shown at {@code t}; {@code expectedMs} how long it should take, -1 = not known. */
    public void pageShown(long t, long expectedMs) {
        pageAt = t;
        this.expectedMs = expectedMs;
        bottomSince = -1;
        armed = false;
        leftBottomAt = -1;
    }

    /** Whether the page has been shown long enough for a gaze turn. */
    public boolean guardPassed(long t) {
        final long need = expectedMs > 0 ? expectedMs / 2 : EyeRules.TURN_GUARD_FALLBACK_MS;
        return pageAt >= 0 && t - pageAt >= need;
    }

    public boolean isArmed() {
        return armed;
    }

    /**
     * @param y         the gaze's screen y (0 top, 1 bottom), NaN when not known
     * @param x         the gaze's screen x
     * @param secondPage in two-page view, whether x is on the page read second ({@code null}
     *                   outside two-page view)
     * @return true once, when the page should turn
     */
    public boolean update(long t, float y, float x, Boolean secondPage) {
        if (Float.isNaN(y)) {
            bottomSince = -1;
            return false;
        }
        final boolean bottom = y >= EyeRules.BOTTOM_ZONE && (secondPage == null || secondPage);
        if (bottom) {
            leftBottomAt = -1;
            if (bottomSince < 0) {
                bottomSince = t;
            }
            if (t - bottomSince >= EyeRules.BOTTOM_DWELL_MS) {
                armed = true;
            }
            return false;
        }
        bottomSince = -1;
        if (!armed) {
            return false;
        }
        if (leftBottomAt < 0) {
            leftBottomAt = t;
        }
        if (y <= EyeRules.TOP_ZONE) {
            armed = false;
            leftBottomAt = -1;
            return guardPassed(t);
        }
        if (t - leftBottomAt > EyeRules.JUMP_MS) {
            // a slow drift up is reading again, not the jump to the next page
            armed = false;
            leftBottomAt = -1;
        }
        return false;
    }
}
