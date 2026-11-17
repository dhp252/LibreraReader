package com.foobnix.assist.readsyou;

/**
 * What Eye mode's camera says about the page shown, from the per-frame results Eye mode already
 * computes (nothing of a frame is kept): how long it watched the reader on the page, how long
 * the eyes were off it and the longest such stretch, and, with a calibrated gaze, second passes
 * (the gaze at the bottom, then back at the top, with the page still shown). One per reading
 * session; times are the frames' own. No Android here.
 */
public final class ReadsEye {

    /** Eye mode's states of a frame: someone looking, nobody looking, or it cannot tell. */
    public static final int SEES = 0;
    public static final int AWAY = 1;
    public static final int CANT_SEE = 2;

    /** What the camera saw while one page was shown. */
    public static final class Page {
        public long watchedMs;
        public long awayMs;
        /** The longest stretch of eyes off the page that ended (the reader looked back or turned the page). */
        public long longestAwayMs;
        public int passes;
    }

    private long last = -1;
    private int lastState = CANT_SEE;
    private Page page = new Page();
    /** The running stretch of eyes off the page. */
    private long awayRun;
    /** The last stretch that ended with the reader looking back, not yet taken by {@link #returned()}. */
    private long returnedRun;

    private long bottomSince = -1;
    private boolean reachedBottom;
    private long topSince = -1;
    /** When the gaze was back at the top after the bottom; -1 when it was not. */
    private long passAt = -1;
    private boolean passNew;

    /**
     * One frame.
     *
     * @param t      the frame's time, ms
     * @param state  {@link #SEES}, {@link #AWAY} or {@link #CANT_SEE}
     * @param gazeY  where on the screen the gaze is, 0 (top) to 1 (bottom); NaN when the gaze is
     *               not calibrated, not usable in this frame, or the page scrolls
     */
    public void frame(long t, int state, float gazeY) {
        final long dt = last >= 0 && t > last && t - last <= ReadsRules.FRAME_GAP_MS ? t - last : 0;
        if (last >= 0 && t - last > ReadsRules.FRAME_GAP_MS) {
            // the camera was off or busy in between: what was running is not known to go on
            endAway(false);
            resetGaze();
        }
        if (lastState == SEES) {
            page.watchedMs += dt;
        } else if (lastState == AWAY) {
            page.watchedMs += dt;
            page.awayMs += dt;
            awayRun += dt;
        }
        if (state != AWAY && lastState == AWAY) {
            endAway(state == SEES);
        }
        last = t;
        lastState = state;
        gaze(t, state == SEES && !Float.isNaN(gazeY) ? gazeY : Float.NaN);
    }

    private void endAway(boolean lookedBack) {
        if (awayRun > 0) {
            page.longestAwayMs = Math.max(page.longestAwayMs, awayRun);
            if (lookedBack) {
                returnedRun = awayRun;
            }
        }
        awayRun = 0;
    }

    private void resetGaze() {
        bottomSince = -1;
        topSince = -1;
        reachedBottom = false;
        passAt = -1;
    }

    private void gaze(long t, float y) {
        if (Float.isNaN(y)) {
            // a blink or a glance away: the dwell starts again, what was reached stays
            bottomSince = -1;
            topSince = -1;
        } else if (y >= ReadsRules.PASS_BOTTOM) {
            topSince = -1;
            if (bottomSince < 0) {
                bottomSince = t;
            }
            if (t - bottomSince >= ReadsRules.PASS_BOTTOM_MS) {
                reachedBottom = true;
                // down again: a look up before it was only a glance
                passAt = -1;
            }
        } else if (y <= ReadsRules.PASS_TOP) {
            bottomSince = -1;
            if (topSince < 0) {
                topSince = t;
            }
            if (reachedBottom && passAt < 0 && t - topSince >= ReadsRules.PASS_TOP_MS) {
                passAt = t;
            }
        } else {
            bottomSince = -1;
            topSince = -1;
        }
        if (passAt >= 0 && t - passAt >= ReadsRules.PASS_CONFIRM_MS) {
            page.passes++;
            passNew = true;
            passAt = -1;
            reachedBottom = false;
        }
    }

    /** Whether frames are arriving: the last one is no older than {@link ReadsRules#FRAME_GAP_MS}. */
    public boolean live(long t) {
        return last >= 0 && t - last <= ReadsRules.FRAME_GAP_MS;
    }

    /** Whether the eyes are off the page in the last frame. */
    public boolean away(long t) {
        return live(t) && lastState == AWAY;
    }

    /** The running stretch of eyes off the page, ms; 0 while the reader looks. */
    public long awayRun() {
        return awayRun;
    }

    /** The length of a stretch of eyes off the page that just ended with the reader looking back; 0 when none, or taken already. */
    public long returned() {
        final long r = returnedRun;
        returnedRun = 0;
        return r;
    }

    /** Whether a second pass was counted since the last call. */
    public boolean passed() {
        final boolean p = passNew;
        passNew = false;
        return p;
    }

    /** Second passes over the page shown. */
    public int passes() {
        return page.passes;
    }

    /** The page was turned: what the camera saw on the one before; everything starts again. */
    public Page turn() {
        endAway(false);
        final Page done = page;
        page = new Page();
        returnedRun = 0;
        passNew = false;
        resetGaze();
        return done;
    }
}
