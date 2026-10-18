package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The reading gaze, with numbers shaped like the owner's trace (2026-10-19). */
public class ReadingPoseTest {

    static FaceFrame f(long t, float yaw, float pitch, float eyesH, float eyesV) {
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.face = true;
        f.yaw = yaw;
        f.pitch = pitch;
        f.eyesH = eyesH;
        f.eyesV = eyesV;
        return f;
    }

    /** Reading as in the trace: head -14° / -10°, eyes +0.45 across, -0.35 down. */
    static FaceFrame reading(long t) {
        return f(t, -14, -10, 0.45f, -0.35f);
    }

    static ReadingPose learned() {
        final ReadingPose p = new ReadingPose();
        for (long t = 0; t <= 3_000; t += 200) {
            p.observe(reading(t));
        }
        return p;
    }

    /** Ten frames of one pose; @return whether the last was on the reading gaze. */
    static boolean settle(ReadingPose p, float yaw, float pitch, float eyesH, float eyesV) {
        boolean on = true;
        for (int i = 0; i < 10; i++) {
            on = p.observe(f(5_000 + i * 200L, yaw, pitch, eyesH, eyesV));
        }
        return on;
    }

    @Test
    public void theFirstSecondsAreReadingAndSetThePose() {
        final ReadingPose p = new ReadingPose();
        for (long t = 0; t < 3_000; t += 200) {
            assertTrue(p.observe(reading(t)));
        }
        assertFalse(p.isLearned());
        assertTrue(p.observe(reading(3_000)));
        assertTrue(p.isLearned());
        assertEquals(0, p.dH(), 0.01);
        assertEquals(0, p.dV(), 0.01);
    }

    @Test
    public void gazeIsHeadPlusEyes() {
        assertEquals(-14 + 27 * 0.45f, ReadingPose.gazeH(reading(0)), 0.01);
        assertEquals(-10 + 40 * 0.35f, ReadingPose.gazeV(reading(0)), 0.01);
    }

    @Test
    public void headTurnWithTheEyesTurningBackStaysOnThePage() {
        // the trace at 75 s: head 9° toward the phone, eyes -0.33 across: the gaze did not move
        assertTrue(settle(learned(), -5, -10, 0.12f, -0.35f));
    }

    @Test
    public void lookingAwayByEyesHeadOrBoth() {
        // eyes to the side (the trace at 28 s)
        assertFalse(settle(learned(), -15, -9, -0.2f, -0.2f));
        // head turned, eyes kept (the trace at 87 s)
        assertFalse(settle(learned(), -24, -20, -0.07f, -0.54f));
        // eyes up from the page to the room
        assertFalse(settle(learned(), -14, -10, 0.45f, 0.05f));
    }

    @Test
    public void readingJitterStaysOn() {
        // the spread of the trace's reading frames
        assertTrue(settle(learned(), -12, -11, 0.36f, -0.32f));
        assertTrue(settle(learned(), -17, -12, 0.52f, -0.45f));
    }

    @Test
    public void oneJitteryFrameStaysOn() {
        final ReadingPose p = learned();
        // the next frame (a frame after a long gap rightly counts almost whole)
        assertTrue(p.observe(f(3_200, -2, -10, 0.45f, -0.35f)));
        assertTrue(p.observe(reading(3_400)));
    }

    @Test
    public void aSlowLookAwayIsNotTakenIn() {
        final ReadingPose p = learned();
        long t = 4_000;
        boolean on = true;
        // the eyes drift to the side over 4 s, as at 85-89 s in the trace
        for (int i = 0; i <= 20; i++, t += 200) {
            on = p.observe(f(t, -14 - i * 0.5f, -10, 0.45f - i * 0.03f, -0.35f));
        }
        assertFalse(on);
        assertTrue(p.dH() < -EyeRules.DEV_GAZE_H);
    }

    @Test
    public void slowPostureDriftIsFollowed() {
        final ReadingPose p = learned();
        long t = 4_000;
        float yaw = -14;
        // the head 0.5° a second for a minute, eyes on the page
        for (int i = 0; i < 300; i++, t += 200) {
            yaw += 0.1f;
            assertTrue("at " + yaw, p.observe(f(t, yaw, -10, 0.45f, -0.35f)));
        }
    }

    @Test
    public void aTouchSetsTheGazeFromARecentFrameOnly() {
        final ReadingPose p = learned();
        settle(p, 15, -10, 0.45f, -0.35f);
        p.reading(10_000); // the last frame is far older: nothing changes
        assertFalse(p.observe(f(10_200, 15, -10, 0.45f, -0.35f)));
        p.reading(10_400);
        assertTrue(p.observe(f(10_400, 15, -10, 0.45f, -0.35f)));
        assertEquals(0, p.dH(), 0.5);
    }
}
