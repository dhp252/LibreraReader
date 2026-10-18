package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** FaceSignals, HeadPose, Luma and the FaceFrame trace line. */
public class FaceMathTest {

    static final float E = 1e-3f;

    // ------------------------------------------------------------------ iris

    /** One eye: corners at (0,0) and (10,0), lids at (5,-2) and (5,2), iris at (ix,iy); then moved, scaled, turned. */
    static float[] eye(float ix, float iy, float scale, double turnDeg, float dx, float dy) {
        final float[][] p = {{0, 0}, {10, 0}, {5, -2}, {5, 2}, {ix, iy}};
        final float[] x = new float[5];
        final float[] y = new float[5];
        final double a = Math.toRadians(turnDeg);
        for (int i = 0; i < 5; i++) {
            x[i] = (float) (scale * (p[i][0] * Math.cos(a) - p[i][1] * Math.sin(a))) + dx;
            y[i] = (float) (scale * (p[i][0] * Math.sin(a) + p[i][1] * Math.cos(a))) + dy;
        }
        return FaceSignals.iris(x, y, 0, 1, 2, 3, 4);
    }

    static float[] eye(float ix, float iy) {
        return eye(ix, iy, 1, 0, 0, 0);
    }

    @Test
    public void irisCentredIsHalfAndZero() {
        final float[] c = eye(5, 0);
        assertEquals(0.5f, c[0], E);
        assertEquals(0f, c[1], E);
    }

    @Test
    public void irisAlongAndAcross() {
        assertEquals(0.2f, eye(2, 0)[0], E);
        assertEquals(0.1f, eye(5, 1)[1], E);
        assertEquals(-0.1f, eye(5, -1)[1], E);
    }

    @Test
    public void irisKeepsItsValueWhenTheFaceMovesScalesOrTilts() {
        final float[] plain = eye(3, 1);
        final float[] moved = eye(3, 1, 2.5f, 25, 100, 40);
        assertEquals(plain[0], moved[0], E);
        assertEquals(plain[1], moved[1], E);
    }

    @Test
    public void irisDownIsPositiveForBothEyes() {
        // the other eye: corners in the opposite order (outer on the right)
        final float[] x = {10, 0, 5, 5, 5};
        final float[] y = {0, 0, -2, 2, 1};
        assertEquals(0.1f, FaceSignals.iris(x, y, 0, 1, 2, 3, 4)[1], E);
    }

    @Test
    public void blendshapesByName() {
        final FaceFrame f = new FaceFrame();
        FaceSignals.blendshapes(f, Arrays.asList("_neutral", "eyeBlinkLeft", "eyeBlinkRight", "eyeLookDownLeft"),
                Arrays.asList(0.9f, 0.7f, 0.65f, 0.2f));
        assertEquals(0.7f, f.blinkL, E);
        assertEquals(0.65f, f.blinkR, E);
        assertEquals(0.2f, f.downL, E);
        assertEquals(0f, f.downR, E);
        f.face = true;
        assertTrue(f.eyesClosed());
        f.blinkR = 0.3f;
        assertFalse(f.eyesClosed());
    }

    // ------------------------------------------------------------------ head pose

    /** Column-major 4×4 from a row-major 3×3 rotation, scaled. */
    static float[] matrix(double[][] r, double scale) {
        final float[] m = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                m[col * 4 + row] = (float) (r[row][col] * scale);
            }
        }
        m[15] = 1;
        return m;
    }

    static double[][] rotY(double deg) {
        final double a = Math.toRadians(deg);
        return new double[][]{{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}};
    }

    static double[][] rotX(double deg) {
        final double a = Math.toRadians(deg);
        return new double[][]{{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}};
    }

    static double[][] transpose(double[][] r) {
        final double[][] t = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                t[i][j] = r[j][i];
            }
        }
        return t;
    }

    @Test
    public void identityIsStraight() {
        final float[] a = HeadPose.angles(matrix(new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, 1));
        assertEquals(0, a[0], E);
        assertEquals(0, a[1], E);
        assertEquals(0, a[2], E);
    }

    @Test
    public void yawAndPitch() {
        assertEquals(30, HeadPose.angles(matrix(rotY(30), 1))[0], 0.01);
        assertEquals(-20, HeadPose.angles(matrix(rotX(-20), 1))[1], 0.01);
    }

    @Test
    public void scaleDoesNotChangeTheAngles() {
        assertEquals(30, HeadPose.angles(matrix(rotY(30), 7.5))[0], 0.01);
    }

    @Test
    public void rowMajorReadingKeepsTheSize() {
        // if the matrix were row-major after all, single turns only change sign
        assertEquals(30, Math.abs(HeadPose.angles(matrix(transpose(rotY(30)), 1))[0]), 0.01);
        assertEquals(20, Math.abs(HeadPose.angles(matrix(transpose(rotX(-20)), 1))[1]), 0.01);
    }

    @Test
    public void flippedDepthAxisStillReadsStraight() {
        // the canonical face turned half round (depth axis reversed): a frontal face either way
        final float[] a = HeadPose.angles(matrix(rotY(180), 1));
        assertEquals(0, a[0], 0.01);
        assertEquals(0, a[1], 0.01);
        // and nodding 20° down on top of it still reads 20°
        final double[][] r = rotY(180);
        final double[][] x = rotX(20);
        final double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    m[i][j] += r[i][k] * x[k][j];
                }
            }
        }
        assertEquals(20, Math.abs(HeadPose.angles(matrix(m, 1))[1]), 0.01);
    }

    @Test
    public void badMatrixIsZero() {
        assertEquals(0, HeadPose.angles(null)[0], E);
        assertEquals(0, HeadPose.angles(new float[4])[1], E);
    }

    // ------------------------------------------------------------------ luma

    static ByteBuffer rgba(int w, int h, int rowStride, int r, int g, int b) {
        final ByteBuffer buf = ByteBuffer.allocate(rowStride * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rowStride / 4; x++) {
                final boolean padding = x >= w;
                buf.put(y * rowStride + x * 4, (byte) (padding ? 255 : r));
                buf.put(y * rowStride + x * 4 + 1, (byte) (padding ? 255 : g));
                buf.put(y * rowStride + x * 4 + 2, (byte) (padding ? 255 : b));
                buf.put(y * rowStride + x * 4 + 3, (byte) 255);
            }
        }
        return buf;
    }

    @Test
    public void lumaOfBlackWhiteAndGrey() {
        assertEquals(0f, Luma.mean(rgba(64, 48, 256, 0, 0, 0), 64, 48, 256, 4), E);
        assertEquals(1f, Luma.mean(rgba(64, 48, 256, 255, 255, 255), 64, 48, 256, 4), E);
        assertEquals(0.5f, Luma.mean(rgba(64, 48, 256, 128, 128, 128), 64, 48, 256, 4), 0.01);
    }

    @Test
    public void lumaIgnoresRowPadding() {
        assertEquals(0f, Luma.mean(rgba(60, 48, 256, 0, 0, 0), 60, 48, 256, 4), E);
    }

    @Test
    public void lumaLeavesTheBufferPosition() {
        final ByteBuffer buf = rgba(64, 48, 256, 10, 10, 10);
        buf.position(7);
        Luma.mean(buf, 64, 48, 256, 4);
        assertEquals(7, buf.position());
    }

    // ------------------------------------------------------------------ trace line

    @Test
    public void frameRoundTrip() throws Exception {
        final FaceFrame f = new FaceFrame();
        f.t = 123456;
        f.face = true;
        f.luma = 0.31234f;
        f.w = 480;
        f.h = 640;
        f.rot = 270;
        f.blinkL = 0.1f;
        f.blinkR = 0.2f;
        f.yaw = -12.5f;
        f.pitch = 4.25f;
        f.irisLX = 0.48f;
        f.irisRY = -0.03f;
        f.faceSize = 0.21f;
        f.lx = new float[]{1, 2};
        final String line = f.toJson("S");
        final JSONObject o = new JSONObject(line);
        assertFalse(o.has("lx"));
        assertFalse(o.has("ly"));
        assertEquals("S", o.getString("st"));

        final FaceFrame back = FaceFrame.fromJson(line);
        assertNotNull(back);
        assertEquals(123456, back.t);
        assertTrue(back.face);
        assertEquals(0.312f, back.luma, E);
        assertEquals(270, back.rot);
        assertEquals(-12.5f, back.yaw, E);
        assertEquals(0.48f, back.irisLX, E);
        assertEquals(-0.03f, back.irisRY, E);
        assertEquals(0.21f, back.faceSize, E);
        assertNull(back.lx);
    }

    @Test
    public void frameWithoutFaceWritesNoFaceFields() throws Exception {
        final FaceFrame f = new FaceFrame();
        f.t = 5;
        f.luma = 0.02f;
        final JSONObject o = new JSONObject(f.toJson(null));
        assertFalse(o.has("bl"));
        assertFalse(o.has("y"));
        assertFalse(o.has("st"));
        assertEquals(0, o.getInt("f"));
    }

    @Test
    public void otherLinesAreNotFrames() {
        assertNull(FaceFrame.fromJson("{\"ev\":\"page\",\"pg\":3}"));
        assertNull(FaceFrame.fromJson("not json"));
        assertNull(FaceFrame.fromJson(""));
    }
}
