package com.foobnix.eye;

import java.util.List;

/**
 * The numbers Eye mode takes from MediaPipe's 478 face landmarks and 52 blendshapes. Pure: the
 * landmarks come in as pixel coordinates of the upright frame.
 */
public final class FaceSignals {

    // MediaPipe face mesh indices ("left" = MediaPipe's left eye)
    static final int L_OUTER = 263;
    static final int L_INNER = 362;
    static final int L_UPPER = 386;
    static final int L_LOWER = 374;
    static final int L_IRIS = 473;
    static final int R_OUTER = 33;
    static final int R_INNER = 133;
    static final int R_UPPER = 159;
    static final int R_LOWER = 145;
    static final int R_IRIS = 468;
    /** With the iris points; the plain face mesh has {@link #MESH}. */
    public static final int LANDMARKS = 478;
    public static final int MESH = 468;
    /** Eye aspect ratio points p1..p6 (corner, upper, upper, corner, lower, lower) per eye. */
    static final int[] EAR_R = {33, 160, 158, 133, 153, 144};
    static final int[] EAR_L = {362, 385, 387, 263, 373, 380};

    private FaceSignals() {
    }

    /**
     * Where the iris sits in an eye: {along, across}. Along runs 0 at the outer corner to 1 at the
     * inner one (about 0.5 looking straight); across is the distance below the middle of the lids,
     * in eye widths (+ = down). Both stay the same when the face is nearer, farther or tilted.
     */
    public static float[] iris(float[] x, float[] y, int outer, int inner, int upper, int lower, int iris) {
        final float ax = x[inner] - x[outer];
        final float ay = y[inner] - y[outer];
        final float len2 = ax * ax + ay * ay;
        if (len2 <= 0) {
            return new float[]{0.5f, 0};
        }
        final float len = (float) Math.sqrt(len2);
        final float cx = x[iris] - x[outer];
        final float cy = y[iris] - y[outer];
        final float along = (cx * ax + cy * ay) / len2;

        // the normal of the corner axis, turned to point from the upper lid to the lower one
        float nx = -ay / len;
        float ny = ax / len;
        if (nx * (x[lower] - x[upper]) + ny * (y[lower] - y[upper]) < 0) {
            nx = -nx;
            ny = -ny;
        }
        final float mx = (x[upper] + x[lower]) / 2;
        final float my = (y[upper] + y[lower]) / 2;
        final float across = ((x[iris] - mx) * nx + (y[iris] - my) * ny) / len;
        return new float[]{along, across};
    }

    /**
     * The eye aspect ratio: lid gap over eye width, (|p2−p6| + |p3−p5|) / (2 |p1−p4|). About 0.25–0.3
     * open, under 0.1 closed; the same for a face near or far, upright or tilted.
     */
    public static float ear(float[] x, float[] y, int[] p) {
        final double width = Math.hypot(x[p[0]] - x[p[3]], y[p[0]] - y[p[3]]);
        if (width <= 0) {
            return 0;
        }
        final double a = Math.hypot(x[p[1]] - x[p[5]], y[p[1]] - y[p[5]]);
        final double b = Math.hypot(x[p[2]] - x[p[4]], y[p[2]] - y[p[4]]);
        return (float) ((a + b) / (2 * width));
    }

    /** Fills the face fields of {@code f} from landmark pixel coordinates (frame {@code f.w} × {@code f.h}). */
    public static void landmarks(FaceFrame f, float[] x, float[] y) {
        f.earL = ear(x, y, EAR_L);
        f.earR = ear(x, y, EAR_R);
        if (x.length >= LANDMARKS) {
            final float[] l = iris(x, y, L_OUTER, L_INNER, L_UPPER, L_LOWER, L_IRIS);
            final float[] r = iris(x, y, R_OUTER, R_INNER, R_UPPER, R_LOWER, R_IRIS);
            f.irisLX = l[0];
            f.irisLY = l[1];
            f.irisRX = r[0];
            f.irisRY = r[1];
        }
        final float lx = (x[L_OUTER] + x[L_INNER]) / 2;
        final float ly = (y[L_OUTER] + y[L_INNER]) / 2;
        final float rx = (x[R_OUTER] + x[R_INNER]) / 2;
        final float ry = (y[R_OUTER] + y[R_INNER]) / 2;
        if (f.w > 0 && f.h > 0) {
            f.faceX = (lx + rx) / 2 / f.w;
            f.faceY = (ly + ry) / 2 / f.h;
            f.faceSize = (float) Math.hypot(lx - rx, ly - ry) / f.w;
        }
    }

    /** A blendshape by its MediaPipe name, as name/score pairs; 0 when missing. */
    public static float score(List<String> names, List<Float> scores, String name) {
        for (int i = 0; i < names.size() && i < scores.size(); i++) {
            if (name.equals(names.get(i))) {
                return scores.get(i);
            }
        }
        return 0;
    }

    /** Fills the blendshape fields of {@code f}. */
    public static void blendshapes(FaceFrame f, List<String> names, List<Float> scores) {
        f.blinkL = score(names, scores, "eyeBlinkLeft");
        f.blinkR = score(names, scores, "eyeBlinkRight");
        f.downL = score(names, scores, "eyeLookDownLeft");
        f.downR = score(names, scores, "eyeLookDownRight");
        // looking to one side turns one eye out and the other in
        final float one = (score(names, scores, "eyeLookOutLeft") + score(names, scores, "eyeLookInRight")) / 2;
        final float other = (score(names, scores, "eyeLookOutRight") + score(names, scores, "eyeLookInLeft")) / 2;
        f.eyesH = one - other;
        f.eyesV = (score(names, scores, "eyeLookUpLeft") + score(names, scores, "eyeLookUpRight")) / 2
                - (f.downL + f.downR) / 2;
    }
}
