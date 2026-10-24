package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** GazeModel, Calibration, BottomTopTurn, ScrollZones, EyeScroll. */
public class GazeTest {

    static final float E = 1e-3f;

    /** A reader whose gaze up/down is 20° at the top of the screen and -20° at the bottom. */
    static float gazeVAt(float y) {
        return 20 - 40 * y;
    }

    static float gazeHAt(float x) {
        return -10 + 20 * x;
    }

    /** A frame whose eyes give the wanted gaze; the head turns and nods with the noise. */
    static FaceFrame looking(long t, float x, float y, float noise) {
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.face = true;
        f.eyesH = gazeHAt(x) / EyeRules.EYE_DEG_H;
        f.eyesV = -gazeVAt(y) / EyeRules.EYE_DEG_V;
        f.yaw = 10 * noise;
        f.pitch = -10 * noise;
        return f;
    }

    // ------------------------------------------------------------------ model

    @Test
    public void fitMapsTheGazeBackToTheScreen() {
        final List<float[]> dots = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            dots.add(new float[]{s[0], s[1], gazeHAt(s[0]), gazeVAt(s[1])});
        }
        final GazeModel m = GazeModel.fit(dots);
        assertNotNull(m);
        assertTrue(m.isGood());
        assertEquals(0.3f, m.y(gazeVAt(0.3f)), E);
        assertEquals(0.8f, m.x(gazeHAt(0.8f)), E);
    }

    @Test
    public void noSpreadOrBadFitIsPoor() {
        // the eyes did not move: all dots the same gaze
        final List<float[]> flat = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            flat.add(new float[]{s[0], s[1], 0, 1});
        }
        assertNull(GazeModel.fit(flat));
        // a tiny spread
        final List<float[]> small = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            small.add(new float[]{s[0], s[1], 0, 1 - 2 * s[1]});
        }
        assertFalse(GazeModel.fit(small).isGood());
        // scattered
        final List<float[]> wild = Arrays.asList(new float[]{0.5f, 0.1f, 0, 10}, new float[]{0.5f, 0.5f, 0, -10},
                new float[]{0.5f, 0.9f, 0, 9}, new float[]{0.5f, 0.3f, 0, -9}, new float[]{0.5f, 0.7f, 0, 0});
        assertFalse(GazeModel.fit(wild).isGood());
    }

    @Test
    public void rezeroAndNudgeMoveTheWholeMap() {
        final List<float[]> dots = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            dots.add(new float[]{s[0], s[1], gazeHAt(s[0]), gazeVAt(s[1])});
        }
        final GazeModel m = GazeModel.fit(dots);
        // the phone moved: looking at the centre now gives the gaze of y 0.6
        m.rezero(0.5f, 0.5f, gazeHAt(0.5f), gazeVAt(0.6f));
        assertEquals(0.5f, m.y(gazeVAt(0.6f)), E);
        m.nudgeY(1);
        // the offset was -0.1; a big nudge stops at the bound
        assertEquals(0.6f + EyeRules.NUDGE_MAX, m.y(gazeVAt(0.6f)), E);
        final GazeModel back = GazeModel.fromJson(m.toJson());
        assertEquals(m.y(3), back.y(3), E);
        assertEquals(m.x(3), back.x(3), E);
    }

    @Test
    public void theHeadDoesNotMoveTheGaze() {
        final FaceFrame still = looking(0, 0.3f, 0.7f, 0);
        final FaceFrame turned = looking(0, 0.3f, 0.7f, 1.5f);
        assertEquals(GazeModel.eyesH(still), GazeModel.eyesH(turned), E);
        assertEquals(GazeModel.eyesV(still), GazeModel.eyesV(turned), E);
        assertEquals(gazeVAt(0.7f), GazeModel.eyesV(still), E);
        // looking or away still takes the head
        assertTrue(Math.abs(ReadingPose.gazeH(still) - ReadingPose.gazeH(turned)) > 10);
    }

    @Test
    public void aLineFittedWithTheHeadIsNotReused() throws Exception {
        final org.json.JSONObject o = new org.json.JSONObject();
        o.put("aY", 0.5).put("bY", -0.025).put("aX", 0.5).put("bX", 0.05).put("rmsY", 0.03).put("spreadV", 20);
        o.put("eyes", new EyeProfile(0.22f, 0.2f, 0.10f, 0.07f, 0.05f, 0.03f).toJson());
        final GazeModel old = GazeModel.fromJson(o);
        assertNotNull(old);
        assertFalse(old.isGood());
        // the reader's eyes stay for long blink
        assertNotNull(old.eyes);
        o.put("v", GazeModel.VERSION);
        assertTrue(GazeModel.fromJson(o).isGood());
    }

    // ------------------------------------------------------------------ calibration

    static Calibration.Result runCalibration(Calibration c, long start, float noise, boolean face) {
        Calibration.Result r = Calibration.Result.RUNNING;
        for (long t = start; r == Calibration.Result.RUNNING && t < start + 20_000; t += 100) {
            final float[] spot = c.spotAt(t);
            final FaceFrame f = spot == null ? looking(t, 0.5f, 0.5f, 0) : looking(t, spot[0], spot[1], noise * (float) Math.sin(t));
            f.face = face;
            r = c.frame(f, false);
        }
        return r;
    }

    @Test
    public void fullCalibrationFitsTheReader() {
        final Calibration c = Calibration.full(0);
        assertEquals(Calibration.Result.OK, runCalibration(c, 0, 1.5f, true));
        assertEquals(7, c.goodDots());
        assertEquals(0.71f, c.model().y(gazeVAt(0.71f)), 0.03);
    }

    @Test
    public void closingStepLearnsTheReadersEyes() {
        final Calibration c = Calibration.full(0);
        Calibration.Result r = Calibration.Result.RUNNING;
        for (long t = 0; r == Calibration.Result.RUNNING && t < 30_000; t += 100) {
            final float[] spot = c.spotAt(t);
            final FaceFrame f = spot == null ? looking(t, 0.5f, 0.5f, 0) : looking(t, spot[0], spot[1], 0);
            // small eyes: open 0.22, at the bottom dot 0.09, shut 0.04
            final float ear = c.phase(t) == Calibration.Phase.CLOSE ? 0.04f : spot != null && spot[1] > 0.9f ? 0.09f : 0.22f;
            f.earL = ear;
            f.earR = ear * 0.7f;
            r = c.frame(f, false);
        }
        assertEquals(Calibration.Result.OK, r);
        final EyeProfile p = c.profile();
        assertNotNull(p);
        assertEquals(0.09f, p.lowL, E);
        assertEquals(0.04f, p.closedL, E);
        assertTrue(p.separable());
        // the limit sits between the reader's closed eyes and their bottom gaze
        final float closed = (0.04f + 0.04f * 0.7f) / 2;
        final float low = (0.09f + 0.09f * 0.7f) / 2;
        assertTrue(p.limit(false) > closed && p.limit(true) < low);
        assertNotNull(GazeModel.fromJson(c.model().toJson()).eyes);
    }

    @Test
    public void eyesTooAlikeAreNotSeparable() {
        assertFalse(new EyeProfile(0.22f, 0.2f, 0.06f, 0.05f, 0.055f, 0.048f).separable());
        assertTrue(new EyeProfile(0.22f, 0.2f, 0.10f, 0.07f, 0.05f, 0.03f).separable());
    }

    @Test
    public void noFaceFails() {
        assertEquals(Calibration.Result.NO_FACE, runCalibration(Calibration.full(0), 0, 0, false));
    }

    @Test
    public void rezeroGivesTheCentreGaze() {
        final Calibration c = Calibration.rezero(0);
        assertEquals(Calibration.Result.OK, runCalibration(c, 0, 0, true));
        assertEquals(gazeVAt(0.5f), c.rezeroGaze()[1], E);
    }

    @Test
    public void timeAloneEndsTheRun() {
        final Calibration c = Calibration.full(0);
        assertEquals(Calibration.Result.NO_FACE, c.tick(30_000));
        assertNull(c.spotAt(30_000));
    }

    // ------------------------------------------------------------------ book mode turn

    long t = 0;
    final BottomTopTurn turn = new BottomTopTurn();

    int gaze(long ms, float y) {
        int fired = 0;
        for (long end = t + ms; t < end; t += 100) {
            if (turn.update(t, y, 0.5f, null)) {
                fired++;
            }
        }
        return fired;
    }

    @Test
    public void lastLinesThenUpTurns() {
        turn.pageShown(0, 20_000);
        gaze(10_000, 0.5f);
        assertEquals(0, gaze(1_200, 0.85f));
        assertTrue(turn.isArmed());
        assertEquals(1, gaze(300, 0.1f));
    }

    @Test
    public void tooSoonAfterThePageCameDoesNotTurn() {
        turn.pageShown(0, 20_000);
        gaze(2_000, 0.85f);
        assertEquals(0, gaze(300, 0.1f));
    }

    @Test
    public void unknownExpectedTimeWaitsTheFallback() {
        turn.pageShown(0, -1);
        gaze(EyeRules.TURN_GUARD_FALLBACK_MS - 1_500, 0.85f);
        assertEquals(0, gaze(300, 0.1f));
        gaze(2_000, 0.85f);
        assertEquals(1, gaze(300, 0.1f));
    }

    @Test
    public void aGlanceAtTheBottomOrASlowDriftUpDoesNotTurn() {
        turn.pageShown(0, 4_000);
        gaze(5_000, 0.5f);
        // only half a second on the last lines
        gaze(500, 0.85f);
        assertEquals(0, gaze(300, 0.1f));
        // armed, then reading up the page slowly
        gaze(1_200, 0.85f);
        gaze(1_000, 0.5f);
        assertEquals(0, gaze(300, 0.1f));
    }

    @Test
    public void twoPageViewCountsOnlyTheSecondPage() {
        turn.pageShown(0, 4_000);
        t = 5_000;
        for (long end = t + 1_200; t < end; t += 100) {
            turn.update(t, 0.85f, 0.25f, false);
        }
        assertFalse(turn.isArmed());
        for (long end = t + 1_200; t < end; t += 100) {
            turn.update(t, 0.85f, 0.75f, true);
        }
        assertTrue(turn.isArmed());
    }

    // ------------------------------------------------------------------ scroll mode zones

    @Test
    public void scrollZones() {
        final ScrollZones z = new ScrollZones();
        assertEquals(1, z.update(0.5f, 100), E);
        // a glance up does not stop it; staying in the top zone does
        assertEquals(1, z.update(0.2f, 100), E);
        assertEquals(1, z.update(0.2f, 400), E);
        assertEquals(0, z.update(0.2f, 200), E);
        // the edge overlap: 0.27 is still the top
        assertEquals(0, z.update(0.27f, 100), E);
        assertEquals(1, z.update(0.4f, 100), E);
        float f = 1;
        for (int i = 0; i < 50; i++) {
            f = z.update(0.85f, 100);
        }
        assertEquals(1.5f, f, 0.01);
        for (int i = 0; i < 200; i++) {
            f = z.update(0.85f, 100);
        }
        assertEquals(EyeRules.SCROLL_MAX_FACTOR, f, E);
        // back in the middle: easing down, not at once
        f = z.update(0.5f, 1_000);
        assertEquals(1.5f, f, 0.01);
        assertEquals(1, z.update(Float.NaN, 100), E);
    }

    @Test
    public void scrollSleepFollowsTheFactor() {
        EyeScroll.set(2);
        assertEquals(10, EyeScroll.sleepMs(20));
        EyeScroll.set(0);
        assertEquals(50, EyeScroll.sleepMs(20));
        EyeScroll.set(1);
        assertEquals(20, EyeScroll.sleepMs(20));
    }
}
