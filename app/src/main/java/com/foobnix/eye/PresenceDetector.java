package com.foobnix.eye;

/**
 * Whether the reader is looking at the screen, from analysed frames (design 5.1, D-83):
 * <ul>
 *     <li>{@link State#SEES}: a face looking at the screen for {@link EyeRules#BACK_MS};</li>
 *     <li>{@link State#AWAY}: nobody looking for {@link EyeRules#AWAY_MS} in a lit room;</li>
 *     <li>{@link State#CANT_SEE}: the same in the dark, the camera busy, or no decision yet. The
 *     features then leave the reader as it is without Eye mode.</li>
 * </ul>
 * Looking means a face turned at most {@link EyeRules#LOOK_YAW} / {@link EyeRules#LOOK_PITCH}
 * from the camera, eyes open. Closed eyes only count in a lit room: in the dark the eye scores
 * are guesses. Pure: times are the frames' own.
 */
public final class PresenceDetector {

    public enum State {CANT_SEE, SEES, AWAY}

    private State state = State.CANT_SEE;
    private float luma = -1;
    private boolean dark;
    /** Start of the current run of looking frames, -1 when the last frame was not looking. */
    private long lookingSince = -1;
    /** Start of the current run of frames without anyone looking, -1 while someone looks. */
    private long goneSince = -1;

    public State state() {
        return state;
    }

    /** The smoothed luminance, -1 before the first frame. */
    public float luma() {
        return luma;
    }

    public boolean isDark() {
        return dark;
    }

    public State update(FaceFrame f) {
        return update(f.t, f.face, f.yaw, f.pitch, f.eyesClosed(), f.luma);
    }

    public State update(long t, boolean face, float yaw, float pitch, boolean eyesClosed, float frameLuma) {
        luma = luma < 0 ? frameLuma : luma + EyeRules.LUMA_SMOOTH * (frameLuma - luma);
        if (dark && luma > EyeRules.BRIGHT_LUMA) {
            dark = false;
        } else if (!dark && luma < EyeRules.DARK_LUMA) {
            dark = true;
        }

        final boolean looking = face && Math.abs(yaw) <= EyeRules.LOOK_YAW && Math.abs(pitch) <= EyeRules.LOOK_PITCH
                && !(eyesClosed && !dark);
        if (looking) {
            goneSince = -1;
            if (lookingSince < 0) {
                lookingSince = t;
            }
            if (t - lookingSince >= EyeRules.BACK_MS) {
                state = State.SEES;
            }
        } else {
            lookingSince = -1;
            if (goneSince < 0) {
                goneSince = t;
            }
            if (t - goneSince >= EyeRules.AWAY_MS) {
                state = dark ? State.CANT_SEE : State.AWAY;
            }
        }
        return state;
    }

    /** The camera stopped giving frames (another app, an error): can't see until a face is back. */
    public State cameraBusy(long t) {
        state = State.CANT_SEE;
        lookingSince = -1;
        goneSince = t;
        return state;
    }
}
