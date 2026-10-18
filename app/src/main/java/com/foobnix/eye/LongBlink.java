package com.foobnix.eye;

/**
 * A deliberate long blink: both eyes closed for {@link EyeRules#LONG_BLINK_MIN_MS} to
 * {@link EyeRules#LONG_BLINK_MAX_MS}, told on the first open frame after it (D-103). Natural blinks
 * are shorter; longer is resting the eyes.
 * <p>
 * A frame is closed when both eyeBlink scores are high <b>and</b> both eye aspect ratios fell
 * well below the reader's own open ones ({@link EyeRules#EAR_CLOSED}): in the owner's trace
 * looking down at the bottom lines scored the same eyeBlink as a closed eye, but it only narrows
 * the eyes. Until enough open frames taught the open ratios, a higher eyeBlink alone counts.
 * One open frame inside a closure ({@link EyeRules#BLINK_GAP_MS}) does not split it. Pure.
 */
public final class LongBlink {

    /** What the last closure was, for the corner panel and the trace. */
    public enum Result {NONE, SHORT, LONG, TOO_LONG, COOLDOWN}

    private float openL;
    private float openR;
    private int openFrames;

    private long closedFrom = -1;
    private long lastClosed = -1;
    /** The first open frame after the last closed one, -1 while closed or none. */
    private long openAfter = -1;
    private long firedAt = -1;
    private long lastMs;
    private Result last = Result.NONE;

    /** Whether the frame's eyes count as closed (also teaches the open ratios on open frames). */
    public boolean isClosed(FaceFrame f) {
        if (!f.face) {
            return false;
        }
        final boolean blink = f.blinkL >= EyeRules.BLINK_CLOSED && f.blinkR >= EyeRules.BLINK_CLOSED;
        if (!knowsOpen()) {
            return f.blinkL >= EyeRules.BLINK_CLOSED_ALONE && f.blinkR >= EyeRules.BLINK_CLOSED_ALONE;
        }
        return blink && f.earL <= openL * EyeRules.EAR_CLOSED && f.earR <= openR * EyeRules.EAR_CLOSED;
    }

    /**
     * @param looking whether the presence detector has the reader looking (open frames teach the
     *                open ratios only then)
     * @return true once, on the frame that ends a long blink
     */
    public boolean update(FaceFrame f, boolean looking) {
        final boolean closed = isClosed(f);
        if (f.face && !closed && looking && f.blinkL < EyeRules.BLINK_OPEN && f.blinkR < EyeRules.BLINK_OPEN
                && f.earL > 0 && f.earR > 0) {
            openFrames++;
            final float a = openFrames < EyeRules.EAR_LEARN_FRAMES ? 1f / openFrames : 0.05f;
            openL += a * (f.earL - openL);
            openR += a * (f.earR - openR);
        }
        if (closed) {
            if (closedFrom < 0) {
                closedFrom = f.t;
            }
            lastClosed = f.t;
            openAfter = -1;
            return false;
        }
        if (closedFrom < 0) {
            return false;
        }
        if (openAfter < 0) {
            // the first open frame: the eyes were closed until about now
            openAfter = f.t;
            if (f.face && f.t - lastClosed <= EyeRules.BLINK_GAP_MS) {
                // or it is one open frame inside the closure: the next frame tells
                return false;
            }
        }
        lastMs = openAfter - closedFrom;
        closedFrom = -1;
        openAfter = -1;
        if (lastMs < EyeRules.LONG_BLINK_MIN_MS) {
            last = Result.SHORT;
            return false;
        }
        if (lastMs > EyeRules.LONG_BLINK_MAX_MS) {
            last = Result.TOO_LONG;
            return false;
        }
        if (firedAt >= 0 && f.t - firedAt < EyeRules.BLINK_COOLDOWN_MS) {
            last = Result.COOLDOWN;
            return false;
        }
        firedAt = f.t;
        last = Result.LONG;
        return true;
    }

    public boolean knowsOpen() {
        return openFrames >= EyeRules.EAR_LEARN_FRAMES;
    }

    /** The frame's eye aspect ratios as a share of the open ones, -1 while not known. */
    public float openShareL(FaceFrame f) {
        return knowsOpen() && openL > 0 ? f.earL / openL : -1;
    }

    public float openShareR(FaceFrame f) {
        return knowsOpen() && openR > 0 ? f.earR / openR : -1;
    }

    public Result last() {
        return last;
    }

    /** How long the last closure was, ms. */
    public long lastMs() {
        return lastMs;
    }

    /** Whether a closure is going on now. */
    public boolean isClosing() {
        return closedFrom >= 0;
    }
}
