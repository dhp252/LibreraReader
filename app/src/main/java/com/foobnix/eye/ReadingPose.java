package com.foobnix.eye;

/**
 * Where the reader looks while reading: one gaze direction made of the head pose and the eyes,
 * learned per session (D-101, D-102). Looking away is a clear move of that gaze away from it.
 * <p>
 * Head and eyes are combined because people turn the head while reading and the eyes turn back
 * to stay on the page: in the owner's trace a 9° head turn came with the eyes moving the other
 * way by 0.33, and the gaze did not move. So gaze across = yaw + {@link EyeRules#EYE_DEG_H} ×
 * eyesH and up/down = pitch − {@link EyeRules#EYE_DEG_V} × eyesV (degrees; the factors came out
 * of that trace and fit how far eyes turn).
 * <p>
 * Only looking or away uses this gaze. Where on the screen ({@link GazeModel}: calibration, page
 * turn, scroll speed) takes the eyes alone (D-130).
 * <ul>
 *     <li>The first {@link EyeRules#POSE_LEARN_MS} of face frames are taken as reading and set it.</li>
 *     <li>While the gaze stays well inside the limits it follows slowly ({@link EyeRules#POSE_FOLLOW}):
 *     posture drifts. Between half the limit and the limit it does not follow, so a slow look away
 *     is not taken in.</li>
 *     <li>A touch on the reader ({@link #reading}) sets it to the gaze of that moment.</li>
 * </ul>
 * Each frame is smoothed first, so one jittery frame does not count. Pure.
 */
public final class ReadingPose {

    private boolean learned;
    private long learnFrom = -1;
    private int learnCount;
    /** The reading gaze, degrees. */
    private float gazeH;
    private float gazeV;
    // the smoothed gaze of the last frames
    private boolean hasFrame;
    private long frameAt = -1;
    private float sH;
    private float sV;

    public static float gazeH(FaceFrame f) {
        return f.yaw + EyeRules.EYE_DEG_H * f.eyesH;
    }

    public static float gazeV(FaceFrame f) {
        return f.pitch - EyeRules.EYE_DEG_V * f.eyesV;
    }

    /** Takes a frame with a face; @return whether its gaze is on the reading one. */
    public boolean observe(FaceFrame f) {
        final float h = gazeH(f);
        final float v = gazeV(f);
        final long dt = frameAt < 0 ? 0 : f.t - frameAt;
        if (!hasFrame) {
            sH = h;
            sV = v;
            hasFrame = true;
        } else {
            final float a = EyeRules.alpha(EyeRules.POSE_SMOOTH, dt);
            sH += a * (h - sH);
            sV += a * (v - sV);
        }
        frameAt = f.t;

        if (!learned) {
            if (learnFrom < 0) {
                learnFrom = f.t;
            }
            learnCount++;
            // the running mean of the frames so far
            gazeH += (sH - gazeH) / learnCount;
            gazeV += (sV - gazeV) / learnCount;
            if (f.t - learnFrom >= EyeRules.POSE_LEARN_MS) {
                learned = true;
            }
            return true;
        }
        final float dh = Math.abs(dH());
        final float dv = Math.abs(dV());
        if (dh <= EyeRules.DEV_GAZE_H / 2 && dv <= EyeRules.DEV_GAZE_V / 2) {
            final float a = EyeRules.alpha(EyeRules.POSE_FOLLOW, dt);
            gazeH += a * (sH - gazeH);
            gazeV += a * (sV - gazeV);
        }
        return dh <= EyeRules.DEV_GAZE_H && dv <= EyeRules.DEV_GAZE_V;
    }

    /** The reader touched the page at {@code t}: the gaze of the last frame, if it is that recent, is reading. */
    public void reading(long t) {
        if (hasFrame && frameAt >= 0 && Math.abs(t - frameAt) <= EyeRules.TOUCH_FRAME_MS) {
            gazeH = sH;
            gazeV = sV;
            learned = true;
        }
    }

    public boolean isLearned() {
        return learned;
    }

    /** How far the gaze is from the reading one, degrees: across, and up (+) / down. */
    public float dH() {
        return sH - gazeH;
    }

    public float dV() {
        return sV - gazeV;
    }
}
