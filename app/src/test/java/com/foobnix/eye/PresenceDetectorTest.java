package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.eye.PresenceDetector.State;

import org.junit.Test;

/** Presence states on made-up frame streams at 5 frames a second. */
public class PresenceDetectorTest {

    static final long STEP = 200;
    static final float LIT = 0.35f;
    static final float DIM = 0.05f;

    long t = 1_000;
    final PresenceDetector d = new PresenceDetector();

    static FaceFrame frame(long t, boolean face, float yaw, float eyesV, boolean closed, float luma) {
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.face = face;
        f.yaw = yaw;
        f.eyesV = eyesV;
        f.luma = luma;
        if (closed) {
            f.blinkL = 0.9f;
            f.blinkR = 0.9f;
        }
        return f;
    }

    /** Frames for {@code ms}. */
    State run(long ms, boolean face, float yaw, float eyesV, boolean closed, float luma) {
        State s = d.state();
        for (long end = t + ms; t < end; t += STEP) {
            s = d.update(frame(t, face, yaw, eyesV, closed, luma));
        }
        return s;
    }

    /** Reading: the pose of the first frames (eyes down at a phone held low). */
    State looking(long ms, float luma) {
        return run(ms, true, 0, -0.5f, false, luma);
    }

    State nobody(long ms, float luma) {
        return run(ms, false, 0, 0, false, luma);
    }

    @Test
    public void startsCantSee() {
        assertEquals(State.CANT_SEE, d.state());
        assertEquals(State.CANT_SEE, looking(400, LIT));
    }

    @Test
    public void faceForHalfASecondSeesYou() {
        assertEquals(State.SEES, looking(800, LIT));
    }

    @Test
    public void goneTwoSecondsInLightIsAway() {
        looking(4_000, LIT);
        assertEquals(State.SEES, nobody(1_800, LIT));
        assertEquals(State.AWAY, nobody(400, LIT));
    }

    @Test
    public void nobodyEverSeenInTheDarkIsCantSee() {
        assertEquals(State.CANT_SEE, nobody(3_000, DIM));
        assertTrue(d.isDark());
        assertFalse(d.canTell());
    }

    @Test
    public void faceSeenInThisDimLightMeansGoneIsAway() {
        // the owner's case: a dark room where the camera still finds the face
        looking(4_000, DIM);
        assertEquals(State.AWAY, nobody(3_000, 0.04f));
        assertTrue(d.isDark());
        assertTrue(d.canTell());
    }

    @Test
    public void muchDarkerThanWhereTheFaceWasSeenIsCantSee() {
        looking(4_000, DIM);
        assertEquals(State.CANT_SEE, nobody(3_000, 0.025f));
    }

    @Test
    public void blackFrameIsCantSee() {
        looking(4_000, 0.02f);
        assertEquals(State.CANT_SEE, nobody(3_000, 0.005f));
    }

    @Test
    public void headTurnedFarIsAway() {
        looking(4_000, LIT);
        assertEquals(State.AWAY, run(2_400, true, 60, -0.5f, false, LIT));
        assertEquals(State.SEES, looking(800, LIT));
    }

    @Test
    public void headTurnedOffTheReadingPoseIsAway() {
        looking(4_000, LIT);
        // 25° is inside the camera limit but well off the reading pose
        assertEquals(State.AWAY, run(3_000, true, 25, -0.5f, false, LIT));
        // back: the smoothed gaze needs a frame to come inside the limit, then 0.5 s
        assertEquals(State.SEES, looking(1_000, LIT));
    }

    @Test
    public void eyesUpFromThePageIsAway() {
        // the owner's look away: head still, eyes up from a phone held low
        looking(4_000, DIM);
        assertEquals(State.AWAY, run(3_000, true, 3, 0f, false, DIM));
        assertEquals(State.SEES, looking(800, DIM));
    }

    @Test
    public void smallMovesStayOnThePage() {
        looking(4_000, LIT);
        assertEquals(State.SEES, run(5_000, true, 5, -0.4f, false, LIT));
    }

    @Test
    public void headTurnWithTheEyesTurningBackStaysOnThePage() {
        looking(4_000, LIT);
        State s = d.state();
        for (long end = t + 5_000; t < end; t += STEP) {
            final FaceFrame f = frame(t, true, 9, -0.5f, false, LIT);
            f.eyesH = -9 / EyeRules.EYE_DEG_H;
            s = d.update(f);
        }
        assertEquals(State.SEES, s);
    }

    @Test
    public void oneFrameBackDoesNotRestartTheAwayTimer() {
        looking(4_000, LIT);
        final long gone = t;
        nobody(1_000, LIT);
        looking(200, LIT);
        assertEquals(State.SEES, nobody(600, LIT));
        assertEquals(State.AWAY, nobody(400, LIT));
        assertTrue(t - gone <= 2_200);
    }

    @Test
    public void aTouchMakesTheNewPoseTheReadingOne() {
        looking(4_000, LIT);
        // turned over in bed: a new pose, read as away
        assertEquals(State.AWAY, run(3_000, true, 25, -0.1f, false, LIT));
        d.reading(t - STEP);
        assertEquals(State.SEES, run(800, true, 25, -0.1f, false, LIT));
        // and the old pose is now off it
        assertEquals(State.AWAY, looking(3_000, LIT));
    }

    @Test
    public void eyesClosedCountOnlyInLight() {
        looking(4_000, LIT);
        assertEquals(State.AWAY, run(2_400, true, 0, -0.5f, true, LIT));

        final PresenceDetector dark = new PresenceDetector();
        long at = 0;
        State s = State.CANT_SEE;
        for (; at < 4_000; at += STEP) {
            s = dark.update(frame(at, true, 0, -0.5f, false, DIM));
        }
        for (; at < 7_000; at += STEP) {
            s = dark.update(frame(at, true, 0, -0.5f, true, DIM));
        }
        assertEquals(State.SEES, s);
    }

    @Test
    public void backNeedsHalfASecond() {
        looking(4_000, LIT);
        nobody(3_000, LIT);
        assertEquals(State.AWAY, looking(400, LIT));
        assertEquals(State.SEES, looking(400, LIT));
    }

    @Test
    public void lightsOutWhileAwayIsCantSee() {
        looking(4_000, LIT);
        assertEquals(State.AWAY, nobody(3_000, LIT));
        assertEquals(State.CANT_SEE, nobody(3_000, 0.03f));
        assertEquals(State.AWAY, nobody(3_000, LIT));
    }

    @Test
    public void busyCameraIsCantSeeUntilAFaceIsBack() {
        looking(4_000, LIT);
        assertEquals(State.CANT_SEE, d.cameraBusy(t));
        assertEquals(State.SEES, looking(800, LIT));
    }

    @Test
    public void lumaHysteresisDoesNotFlicker() {
        looking(4_000, LIT);
        nobody(3_000, 0.05f);
        assertTrue(d.isDark());
        // between the two thresholds: stays dark (and far below where the face was seen)
        assertEquals(State.CANT_SEE, nobody(3_000, 0.095f));
        assertTrue(d.isDark());
        assertEquals(State.AWAY, nobody(3_000, 0.2f));
    }
}
