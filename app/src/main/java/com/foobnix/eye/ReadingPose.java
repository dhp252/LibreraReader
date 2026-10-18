package com.foobnix.eye;

/**
 * The reader's own reading pose: head yaw / pitch and eye direction while they read (D-101).
 * Looking away is a clear move away from it, whatever the pose is (a phone held low has the eyes
 * looking down; a tablet on a stand does not), so no fixed angle has to fit everybody.
 * <ul>
 *     <li>The first {@link EyeRules#POSE_LEARN_MS} of face frames are taken as reading and set it.</li>
 *     <li>While the reader looks, it follows slowly ({@link EyeRules#POSE_FOLLOW}): posture drifts.</li>
 *     <li>A touch on the reader ({@link #reading}) sets it to the pose of that moment: whoever
 *     touches the page is reading it, also after turning over in bed.</li>
 * </ul>
 * Each frame's numbers are smoothed first, so one jittery frame does not count. Pure.
 */
public final class ReadingPose {

    private boolean learned;
    private long learnFrom = -1;
    private int learnCount;
    // the pose
    private float yaw;
    private float pitch;
    private float eyesH;
    private float eyesV;
    // the smoothed frame
    private boolean hasFrame;
    private long frameAt = -1;
    private float sYaw;
    private float sPitch;
    private float sEyesH;
    private float sEyesV;

    /** Takes a frame with a face; @return whether it is close to the reading pose. */
    public boolean observe(FaceFrame f) {
        if (!hasFrame) {
            sYaw = f.yaw;
            sPitch = f.pitch;
            sEyesH = f.eyesH;
            sEyesV = f.eyesV;
            hasFrame = true;
        } else {
            final float a = EyeRules.POSE_SMOOTH;
            sYaw += a * (f.yaw - sYaw);
            sPitch += a * (f.pitch - sPitch);
            sEyesH += a * (f.eyesH - sEyesH);
            sEyesV += a * (f.eyesV - sEyesV);
        }
        frameAt = f.t;

        if (!learned) {
            if (learnFrom < 0) {
                learnFrom = f.t;
            }
            learnCount++;
            // the running mean of the frames so far
            yaw += (sYaw - yaw) / learnCount;
            pitch += (sPitch - pitch) / learnCount;
            eyesH += (sEyesH - eyesH) / learnCount;
            eyesV += (sEyesV - eyesV) / learnCount;
            if (f.t - learnFrom >= EyeRules.POSE_LEARN_MS) {
                learned = true;
            }
            return true;
        }
        final boolean close = Math.abs(dYaw()) <= EyeRules.DEV_YAW && Math.abs(dPitch()) <= EyeRules.DEV_PITCH
                && Math.abs(dEyesH()) <= EyeRules.DEV_EYES_H && Math.abs(dEyesV()) <= EyeRules.DEV_EYES_V;
        if (close) {
            final float a = EyeRules.POSE_FOLLOW;
            yaw += a * (sYaw - yaw);
            pitch += a * (sPitch - pitch);
            eyesH += a * (sEyesH - eyesH);
            eyesV += a * (sEyesV - eyesV);
        }
        return close;
    }

    /** The reader touched the page at {@code t}: the pose of the last frame, if it is that recent, is reading. */
    public void reading(long t) {
        if (hasFrame && frameAt >= 0 && Math.abs(t - frameAt) <= EyeRules.TOUCH_FRAME_MS) {
            yaw = sYaw;
            pitch = sPitch;
            eyesH = sEyesH;
            eyesV = sEyesV;
            learned = true;
        }
    }

    public boolean isLearned() {
        return learned;
    }

    public float dYaw() {
        return sYaw - yaw;
    }

    public float dPitch() {
        return sPitch - pitch;
    }

    public float dEyesH() {
        return sEyesH - eyesH;
    }

    public float dEyesV() {
        return sEyesV - eyesV;
    }
}
