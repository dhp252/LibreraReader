package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.foobnix.eye.PresenceDetector.State;

import org.junit.Test;

/** Presence states on made-up frame streams at 5 frames a second. */
public class PresenceDetectorTest {

    static final long STEP = 200;
    static final float LIT = 0.35f;
    static final float DARK = 0.03f;

    long t = 1_000;
    final PresenceDetector d = new PresenceDetector();

    /** Frames for {@code ms}: a face looking straight unless told otherwise. */
    State run(long ms, boolean face, float yaw, boolean closed, float luma) {
        State s = d.state();
        for (long end = t + ms; t < end; t += STEP) {
            s = d.update(t, face, yaw, 0, closed, luma);
        }
        return s;
    }

    State looking(long ms, float luma) {
        return run(ms, true, 0, false, luma);
    }

    State nobody(long ms, float luma) {
        return run(ms, false, 0, false, luma);
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
        looking(1_000, LIT);
        assertEquals(State.SEES, nobody(1_800, LIT));
        assertEquals(State.AWAY, nobody(400, LIT));
    }

    @Test
    public void goneInTheDarkIsCantSee() {
        looking(1_000, DARK);
        assertEquals(State.CANT_SEE, nobody(3_000, DARK));
        assertTrue(d.isDark());
    }

    @Test
    public void headTurnedAwayIsAway() {
        looking(1_000, LIT);
        assertEquals(State.AWAY, run(2_400, true, 60, false, LIT));
        assertEquals(State.SEES, run(2_400, true, 30, false, LIT));
    }

    @Test
    public void eyesClosedCountOnlyInLight() {
        looking(1_000, LIT);
        assertEquals(State.AWAY, run(2_400, true, 0, true, LIT));

        final PresenceDetector dark = new PresenceDetector();
        long at = 0;
        State s = State.CANT_SEE;
        for (; at < 1_000; at += STEP) {
            s = dark.update(at, true, 0, 0, false, DARK);
        }
        for (; at < 4_000; at += STEP) {
            s = dark.update(at, true, 0, 0, true, DARK);
        }
        assertEquals(State.SEES, s);
    }

    @Test
    public void backNeedsHalfASecond() {
        looking(1_000, LIT);
        nobody(3_000, LIT);
        assertEquals(State.AWAY, looking(400, LIT));
        assertEquals(State.SEES, looking(400, LIT));
    }

    @Test
    public void lightsOutWhileAwayIsCantSee() {
        looking(1_000, LIT);
        assertEquals(State.AWAY, nobody(3_000, LIT));
        assertEquals(State.CANT_SEE, nobody(3_000, DARK));
        assertEquals(State.AWAY, nobody(3_000, LIT));
    }

    @Test
    public void busyCameraIsCantSeeUntilAFaceIsBack() {
        looking(1_000, LIT);
        assertEquals(State.CANT_SEE, d.cameraBusy(t));
        assertEquals(State.SEES, looking(800, LIT));
    }

    @Test
    public void lumaHysteresisDoesNotFlicker() {
        looking(1_000, LIT);
        nobody(3_000, 0.05f);
        assertTrue(d.isDark());
        // between the two thresholds: stays dark
        assertEquals(State.CANT_SEE, nobody(3_000, 0.095f));
        assertTrue(d.isDark());
        assertEquals(State.AWAY, nobody(3_000, 0.2f));
    }
}
