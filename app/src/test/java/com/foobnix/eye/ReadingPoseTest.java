package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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

    @Test
    public void theFirstSecondsAreReadingAndSetThePose() {
        final ReadingPose p = new ReadingPose();
        for (long t = 0; t < 3_000; t += 200) {
            assertTrue(p.observe(f(t, 10, -5, 0.1f, -0.4f)));
        }
        assertFalse(p.isLearned());
        assertTrue(p.observe(f(3_000, 10, -5, 0.1f, -0.4f)));
        assertTrue(p.isLearned());
        assertEquals(0, p.dYaw(), 0.01);
        assertEquals(0, p.dEyesV(), 0.01);
    }

    @Test
    public void oneJitteryFrameStaysClose() {
        final ReadingPose p = learned();
        // one frame 30° off: smoothed to 15°
        assertTrue(p.observe(f(4_000, 30, 0, 0, -0.5f)));
        assertTrue(p.observe(f(4_200, 0, 0, 0, -0.5f)));
    }

    @Test
    public void eachDirectionCountsOnItsOwn() {
        assertFalse(settle(learned(), 0, 25, 0, -0.5f));
        assertFalse(settle(learned(), 0, 0, 0.5f, -0.5f));
        assertFalse(settle(learned(), 0, 0, 0, 0.1f));
        assertFalse(settle(learned(), 25, 0, 0, -0.5f));
        assertTrue(settle(learned(), 10, 8, 0.15f, -0.3f));
    }

    @Test
    public void aTouchSetsThePoseFromARecentFrameOnly() {
        final ReadingPose p = learned();
        settle(p, 30, 0, 0, -0.5f);
        p.reading(10_000); // the last frame is far older: nothing changes
        assertFalse(p.observe(f(10_200, 30, 0, 0, -0.5f)));
        p.reading(10_400);
        assertTrue(p.observe(f(10_400, 30, 0, 0, -0.5f)));
        assertEquals(0, p.dYaw(), 0.5);
    }

    @Test
    public void slowDriftIsFollowed() {
        final ReadingPose p = learned();
        long t = 4_000;
        float yaw = 0;
        // 1° every 2 s for a minute: 30° in all, never more than a few degrees off the pose
        for (int i = 0; i < 300; i++, t += 200) {
            yaw += 0.1f;
            assertTrue("at " + yaw, p.observe(f(t, yaw, 0, 0, -0.5f)));
        }
    }

    static ReadingPose learned() {
        final ReadingPose p = new ReadingPose();
        for (long t = 0; t <= 3_000; t += 200) {
            p.observe(f(t, 0, 0, 0, -0.5f));
        }
        return p;
    }

    /** Ten frames of one pose; @return whether the last was close. */
    static boolean settle(ReadingPose p, float yaw, float pitch, float eyesH, float eyesV) {
        boolean close = true;
        for (int i = 0; i < 10; i++) {
            close = p.observe(f(5_000 + i * 200L, yaw, pitch, eyesH, eyesV));
        }
        return close;
    }
}
