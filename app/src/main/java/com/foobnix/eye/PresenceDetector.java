package com.foobnix.eye;

/**
 * Whether the reader is looking at the screen, from analysed frames (design 5.1, D-83, D-98):
 * <ul>
 *     <li>{@link State#SEES}: a face looking at the screen for {@link EyeRules#BACK_MS};</li>
 *     <li>{@link State#AWAY}: nobody looking for {@link EyeRules#AWAY_MS}, in light the camera
 *     can tell by: a lit room, or a dim one where a face was found at nearly this brightness;</li>
 *     <li>{@link State#CANT_SEE}: the same in a frame darker than that (lights out, never seen a
 *     face in this light), a black frame, the camera busy, or no decision yet. The features then
 *     leave the reader as it is without Eye mode.</li>
 * </ul>
 * Looking means a face whose gaze (head and eyes together) is close to the reader's own reading
 * gaze ({@link ReadingPose}: learned at the start, followed slowly, re-learned on a touch), never turned more than
 * {@link EyeRules#LOOK_YAW} / {@link EyeRules#LOOK_PITCH} from the camera, eyes open. Closed eyes
 * only count in a lit room: in the dark the eye scores are guesses. Pure: times are the frames'
 * own.
 */
public final class PresenceDetector {

    public enum State {CANT_SEE, SEES, AWAY}

    private State state = State.CANT_SEE;
    private float luma = -1;
    private boolean dark;
    /** The smoothed luminance at the last frame someone looked, -1 before that. */
    private float seenLuma = -1;
    /** Start of the current run of looking frames, -1 when the last frame was not looking. */
    private long lookingSince = -1;
    /**
     * Since when nobody has looked, -1 while someone does. A look of less than
     * {@link EyeRules#BACK_MS} does not reset it: a look away is not cut short by one frame that
     * happens to point at the page.
     */
    private long goneSince = -1;
    private final ReadingPose pose = new ReadingPose();

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

    /** Whether the camera could tell someone looking from no one at the current brightness. */
    public boolean canTell() {
        if (luma < EyeRules.BLACK_LUMA) {
            return false;
        }
        return !dark || (seenLuma > 0 && luma >= seenLuma * EyeRules.SEEN_LUMA_RATIO);
    }

    public ReadingPose pose() {
        return pose;
    }

    /** The reader touched the page at {@code t} (uptime, as the frames): they are reading. */
    public void reading(long t) {
        pose.reading(t);
    }

    public State update(FaceFrame f) {
        final long t = f.t;
        final float frameLuma = f.luma;
        luma = luma < 0 ? frameLuma : luma + EyeRules.LUMA_SMOOTH * (frameLuma - luma);
        if (dark && luma > EyeRules.BRIGHT_LUMA) {
            dark = false;
        } else if (!dark && luma < EyeRules.DARK_LUMA) {
            dark = true;
        }

        final boolean closed = f.eyesClosed() && !dark;
        boolean looking = false;
        if (f.face && !closed && Math.abs(f.yaw) <= EyeRules.LOOK_YAW && Math.abs(f.pitch) <= EyeRules.LOOK_PITCH) {
            looking = pose.observe(f);
        }
        if (looking) {
            seenLuma = luma;
            if (lookingSince < 0) {
                lookingSince = t;
            }
            if (t - lookingSince >= EyeRules.BACK_MS) {
                state = State.SEES;
                goneSince = -1;
            }
        } else {
            lookingSince = -1;
            if (goneSince < 0) {
                goneSince = t;
            }
            if (t - goneSince >= EyeRules.AWAY_MS) {
                state = canTell() ? State.AWAY : State.CANT_SEE;
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
