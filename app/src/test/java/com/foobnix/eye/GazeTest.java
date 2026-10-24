package com.foobnix.eye;

import static org.junit.Assert.assertArrayEquals;
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

    /** A phone's screen, 70 × 150 mm, upright, camera above its top edge. */
    static final GazeGeometry G = GazeGeometry.of(70, 150, 0);
    static final float FOCAL = 500;
    static final int FW = 480;
    static final int FH = 640;
    /** The reader's head: eyes 60 mm below the camera's axis, 300 mm away, straight. */
    static final float[] H0 = {0, 60, 300, 0, 0, 0};

    /**
     * A frame of a reader with this head looking at (x, y): the eyes turn in the head as far as
     * that needs; their eye scores give it in degrees, their irises 0.002 / 0.004 per degree.
     */
    static FaceFrame looking(long t, float x, float y, float[] head) {
        final float[] a = G.eyeAngles(x, y, head);
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.face = true;
        f.w = FW;
        f.h = FH;
        f.focal = FOCAL;
        final double mmPerPx = head[GazeGeometry.Z] / FOCAL;
        f.faceSize = (float) (GazeGeometry.IPD_MM / mmPerPx * Math.cos(Math.toRadians(head[GazeGeometry.YAW])) / FW);
        f.faceX = (float) (0.5 + head[GazeGeometry.EX] / mmPerPx / FW);
        f.faceY = (float) (0.5 + head[GazeGeometry.EY] / mmPerPx / FH);
        f.yaw = head[GazeGeometry.YAW];
        f.pitch = head[GazeGeometry.PITCH];
        f.tilt = head[GazeGeometry.TILT];
        f.eyesH = a[0] / EyeRules.EYE_DEG_H;
        f.eyesV = -a[1] / EyeRules.EYE_DEG_V;
        f.irisRX = 0.5f + 0.002f * a[0];
        f.irisLX = 0.5f - 0.002f * a[0];
        f.irisLD = 0.004f * a[1];
        f.irisRD = 0.004f * a[1];
        return f;
    }

    /** {@link #H0} turned a little by {@code noise}: yaw, pitch and tilt. */
    static float[] jitter(float noise) {
        final float[] h = H0.clone();
        h[GazeGeometry.YAW] += noise;
        h[GazeGeometry.PITCH] -= noise;
        h[GazeGeometry.TILT] += noise / 2;
        return h;
    }

    static float[] signalsAt(float x, float y) {
        return GazeModel.features(looking(0, x, y, H0));
    }

    /** A dot with these frames' signals, the head {@link #H0}. */
    static GazeModel.Dot dot(float x, float y, float[]... frames) {
        final List<float[]> heads = new ArrayList<>();
        for (float[] ignored : frames) {
            heads.add(H0);
        }
        return new GazeModel.Dot(x, y, Arrays.asList(frames), heads, G);
    }

    static List<GazeModel.Dot> grid() {
        final List<GazeModel.Dot> dots = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            dots.add(dot(s[0], s[1], signalsAt(s[0], s[1])));
        }
        return dots;
    }

    static float[] at(GazeModel m, float x, float y, float[] head) {
        final FaceFrame f = looking(0, x, y, head);
        return m.point(GazeModel.features(f), GazeGeometry.head(f), G);
    }

    // ------------------------------------------------------------------ geometry

    @Test
    public void eyeAnglesAndPointAreOneAnother() {
        final float[] head = {12, 40, 280, -6, 4, 9};
        final float[] a = G.eyeAngles(0.3f, 0.8f, head);
        final float[] p = G.point(a[0], a[1], head);
        assertEquals(0.3f, p[0], E);
        assertEquals(0.8f, p[1], E);
        // looking down the screen is looking down in the head
        assertTrue(G.eyeAngles(0.5f, 0.9f, H0)[1] > G.eyeAngles(0.5f, 0.1f, H0)[1]);
        // the screen's right is the frame's left (not mirrored): the eyes turn toward the frame's left
        assertTrue(G.eyeAngles(0.9f, 0.5f, H0)[0] < G.eyeAngles(0.1f, 0.5f, H0)[0]);
    }

    @Test
    public void theHeadComesFromTheFacesPlaceAndSize() {
        final float[] head = {15, -20, 350, 10, -5, 3};
        final float[] back = GazeGeometry.head(looking(0, 0.5f, 0.5f, head));
        assertArrayEquals(head, back, 0.01f);
        final FaceFrame none = new FaceFrame();
        assertNull(GazeGeometry.head(none));
    }

    @Test
    public void screenSizeDistrustsAMadeUpDpi() {
        // 1080 × 2400 at 401 dpi: about 68 × 152 mm
        final float[] real = GazeGeometry.screenMm(1080, 2400, 401, 402, 440);
        assertEquals(68.4f, real[0], 0.2);
        // the same phone saying 160: the density's 440
        final float[] made = GazeGeometry.screenMm(1080, 2400, 160, 160, 440);
        assertEquals(1080 / 440f * 25.4f, made[0], 0.01);
    }

    // ------------------------------------------------------------------ model

    @Test
    public void fitMapsTheGazeBackToTheScreen() {
        final GazeModel m = GazeModel.fit(grid());
        assertNotNull(m);
        assertTrue(m.isGood());
        assertEquals(0.3f, at(m, 0.5f, 0.3f, H0)[1], 0.01);
        assertEquals(0.8f, at(m, 0.8f, 0.5f, H0)[0], 0.01);
    }

    @Test
    public void aMovedHeadOrPhoneKeepsThePoint() {
        final GazeModel m = GazeModel.fit(grid());
        final float[][] moves = {
                {0, 60, 300, 0, 7, 0},      // a nod
                {0, 60, 300, -6, 0, 0},     // a turn
                {0, 60, 300, 0, 0, 12},     // a tilt to the side
                {18, 45, 300, 0, 0, 0},     // the phone slid a little
                {0, 60, 240, 0, 0, 0},      // brought nearer
                {-10, 75, 330, 4, -5, -8}}; // all of it
        for (float[] head : moves) {
            final float[] p = at(m, 0.3f, 0.7f, head);
            assertEquals(0.3f, p[0], 0.01);
            assertEquals(0.7f, p[1], 0.01);
        }
        // the eyes alone (the head taken as it was at the calibration) would have read the nod as a look up
        final FaceFrame nod = looking(0, 0.3f, 0.7f, moves[0]);
        assertTrue(m.point(GazeModel.features(nod), H0, G)[1] < 0.6f);
    }

    @Test
    public void theSignalThatFollowsTheEyesIsPicked() {
        // eye scores that do not follow the dots (a face the model reads badly), irises that do
        final List<GazeModel.Dot> irisOnly = new ArrayList<>();
        int i = 0;
        for (float[] s : Calibration.SPOTS) {
            final float[] f = signalsAt(s[0], s[1]);
            f[GazeModel.EYE_V] = 8 * (float) Math.sin(7.3 * i);
            f[GazeModel.EYE_H] = 8 * (float) Math.cos(5.1 * i++);
            irisOnly.add(dot(s[0], s[1], f));
        }
        final GazeModel m = GazeModel.fit(irisOnly);
        assertTrue(m.isGood());
        assertEquals("iris", m.signal(true));
        assertEquals("iris", m.signal(false));
        assertEquals(0.71f, m.point(signalsAt(0.5f, 0.71f), H0, G)[1], 0.02);
        // no iris points (the plain face mesh): the eye scores
        final List<GazeModel.Dot> scores = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            final float[] f = signalsAt(s[0], s[1]);
            f[GazeModel.IRIS_H] = 0;
            f[GazeModel.IRIS_V] = 0;
            scores.add(dot(s[0], s[1], f));
        }
        assertEquals("eyes", GazeModel.fit(scores).signal(true));
    }

    @Test
    public void noSpreadOrBadFitIsPoor() {
        // the eyes did not move: all dots the same signals
        final List<GazeModel.Dot> flat = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            flat.add(dot(s[0], s[1], new float[]{0, 1, 0, 0}));
        }
        assertNull(GazeModel.fit(flat));
        // a tiny move, smaller than the frames' own scatter
        final List<GazeModel.Dot> small = new ArrayList<>();
        for (float[] s : Calibration.SPOTS) {
            final float v = 2 * s[1] - 1;
            small.add(dot(s[0], s[1], new float[]{0, v - 1, 0, 0}, new float[]{0, v, 0, 0}, new float[]{0, v + 1, 0, 0}));
        }
        assertFalse(GazeModel.fit(small).isGood());
        // scattered
        final List<GazeModel.Dot> wild = Arrays.asList(dot(0.5f, 0.1f, new float[]{0, 10, 0, 0}),
                dot(0.5f, 0.5f, new float[]{0, -10, 0, 0}), dot(0.5f, 0.9f, new float[]{0, 9, 0, 0}),
                dot(0.5f, 0.3f, new float[]{0, -9, 0, 0}), dot(0.5f, 0.7f, new float[]{0, 0, 0, 0}));
        assertFalse(GazeModel.fit(wild).isGood());
    }

    @Test
    public void rezeroAndNudgeMoveTheWholeMap() {
        final GazeModel m = GazeModel.fit(grid());
        // the phone moved: looking at the centre now gives the signals of y 0.6
        final float[] at = signalsAt(0.5f, 0.6f);
        m.rezero(0.5f, 0.5f, at, H0, G);
        assertEquals(0.5f, m.point(at, H0, G)[1], E);
        assertEquals(0.5f, m.point(at, H0, G)[0], E);
        m.nudgeY(1);
        // the offset was about -0.1; a big nudge stops at the bound
        assertEquals(0.6f + EyeRules.NUDGE_MAX, m.point(at, H0, G)[1], 0.01);
        final GazeModel back = GazeModel.fromJson(m.toJson());
        final float[] other = signalsAt(0.2f, 0.9f);
        assertArrayEquals(m.point(other, H0, G), back.point(other, H0, G), E);
        assertEquals(m.signal(true), back.signal(true));
    }

    @Test
    public void lookingOrAwayStillTakesTheHead() {
        final FaceFrame still = looking(0, 0.3f, 0.7f, H0);
        // the whole head turned away, the eyes not turning back
        final FaceFrame away = looking(0, 0.3f, 0.7f, H0);
        away.yaw += 15;
        assertTrue(Math.abs(ReadingPose.gazeH(still) - ReadingPose.gazeH(away)) > 10);
        // and the screen point leaves the page's middle with it
        final GazeModel m = GazeModel.fit(grid());
        assertTrue(Math.abs(m.point(GazeModel.features(away), GazeGeometry.head(away), G)[0] - 0.3f) > 0.3f);
    }

    @Test
    public void anOlderLineIsNotReused() throws Exception {
        final org.json.JSONObject o = new org.json.JSONObject();
        o.put("aY", 0.5).put("bY", -0.025).put("aX", 0.5).put("bX", 0.05).put("rmsY", 0.03).put("spreadV", 20);
        o.put("eyes", new EyeProfile(0.22f, 0.2f, 0.10f, 0.07f, 0.05f, 0.03f).toJson());
        // head + eyes (no "v"), eye scores judged by their spread (2), screen lines of picked signals (3)
        for (int v : new int[]{1, 2, 3}) {
            o.put("v", v);
            final GazeModel old = GazeModel.fromJson(o);
            assertNotNull(old);
            assertFalse(old.isGood());
            // the reader's eyes stay for long blink
            assertNotNull(old.eyes);
        }
        o.put("v", GazeModel.VERSION);
        assertTrue(GazeModel.fromJson(o).isGood());
    }

    // ------------------------------------------------------------------ calibration

    static Calibration.Result runCalibration(Calibration c, long start, float noise, boolean face) {
        Calibration.Result r = Calibration.Result.RUNNING;
        for (long t = start; r == Calibration.Result.RUNNING && t < start + 30_000; t += 100) {
            final float[] spot = c.spotAt(t);
            final FaceFrame f = spot == null ? looking(t, 0.5f, 0.5f, H0)
                    : looking(t, spot[0], spot[1], jitter(noise * (float) Math.sin(t)));
            f.face = face;
            r = c.frame(f, false);
        }
        return r;
    }

    @Test
    public void fullCalibrationFitsTheReader() {
        // the head wavers by up to 1.5° while the eyes stay on each dot
        final Calibration c = Calibration.full(0, G);
        assertEquals(Calibration.Result.OK, runCalibration(c, 0, 1.5f, true));
        assertEquals(9, c.goodDots());
        assertEquals(0.71f, at(c.model(), 0.5f, 0.71f, H0)[1], 0.02);
        assertEquals(0.3f, at(c.model(), 0.3f, 0.5f, H0)[0], 0.02);
    }

    /**
     * A full calibration of a reader whose head turns during the head step (the eyes kept on the
     * dot); {@code reversed}: the face engine reports yaw and pitch the other way round.
     */
    static Calibration headStepRun(boolean reversed) {
        final Calibration c = Calibration.full(0, G);
        Calibration.Result r = Calibration.Result.RUNNING;
        for (long t = 0; r == Calibration.Result.RUNNING && t < 30_000; t += 100) {
            final float[] spot = c.spotAt(t);
            final float[] head = H0.clone();
            if (c.phase(t) == Calibration.Phase.HEAD) {
                head[GazeGeometry.YAW] = 12 * (float) Math.sin(t / 300.0);
                head[GazeGeometry.PITCH] = 9 * (float) Math.cos(t / 370.0);
            }
            final FaceFrame f = spot == null ? looking(t, 0.5f, 0.5f, head) : looking(t, spot[0], spot[1], head);
            if (reversed) {
                f.yaw = -f.yaw;
                f.pitch = -f.pitch;
            }
            r = c.frame(f, false);
        }
        assertEquals(Calibration.Result.OK, r);
        return c;
    }

    @Test
    public void theHeadStepMeasuresHowMuchTheHeadCounts() {
        final GazeModel same = headStepRun(false).model();
        assertEquals(1, same.kYaw, 0.05);
        assertEquals(1, same.kPitch, 0.05);
        // the angles the other way round: found, and a moved head keeps the point
        final GazeModel m = headStepRun(true).model();
        assertEquals(-1, m.kYaw, 0.05);
        assertEquals(-1, m.kPitch, 0.05);
        final FaceFrame f = looking(0, 0.3f, 0.7f, new float[]{0, 60, 300, -6, 7, 0});
        f.yaw = -f.yaw;
        f.pitch = -f.pitch;
        final float[] p = m.point(GazeModel.features(f), GazeGeometry.head(f), G);
        assertEquals(0.3f, p[0], 0.01);
        assertEquals(0.7f, p[1], 0.01);
        assertEquals(m.kYaw, GazeModel.fromJson(m.toJson()).kYaw, 1e-4);
    }

    @Test
    public void closingStepLearnsTheReadersEyes() {
        final Calibration c = Calibration.full(0, G);
        Calibration.Result r = Calibration.Result.RUNNING;
        for (long t = 0; r == Calibration.Result.RUNNING && t < 30_000; t += 100) {
            final float[] spot = c.spotAt(t);
            final FaceFrame f = spot == null ? looking(t, 0.5f, 0.5f, H0) : looking(t, spot[0], spot[1], H0);
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
        assertEquals(Calibration.Result.NO_FACE, runCalibration(Calibration.full(0, G), 0, 0, false));
    }

    @Test
    public void rezeroGivesTheCentreGaze() {
        final Calibration c = Calibration.rezero(0, G);
        assertEquals(Calibration.Result.OK, runCalibration(c, 0, 0, true));
        assertEquals(G.eyeAngles(0.5f, 0.5f, H0)[1], c.rezeroDot().median[GazeModel.EYE_V], E);
    }

    @Test
    public void timeAloneEndsTheRun() {
        final Calibration c = Calibration.full(0, G);
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
