package com.foobnix.eye;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Where on the screen the reader looks (M3, D-90), as screen fractions, 0 at the top / left, 1 at
 * the bottom / right. The calibration learns where the eyes point inside the head: an angle per
 * axis, linear in the eye signals; {@link GazeGeometry} then puts that through the head of the
 * moment (where the eyes are, how the head is turned) onto the screen (D-133), so moving the head
 * or the phone does not move the point.
 * <p>
 * The eyes give two signals per axis ({@link #features}): MediaPipe's eyeLook scores and the iris
 * position measured from the eye corners. Which one, or both, fits a reader best differs from
 * person to person, so the calibration tries each per axis and keeps the one with the smallest
 * expected error: how well the other dots place a left-out dot, plus the frames' own scatter
 * (D-131). Fitted per display rotation; a re-zero moves it as a whole. Pure.
 */
public final class GazeModel {

    /**
     * What the stored lines are: 4 = eye angles inside the head (D-133). Older entries (screen
     * lines: head + eyes before D-130, eye scores alone, D-131's picked signals) load not good.
     */
    static final int VERSION = 4;

    /** A frame's eye signals ({@link #features}): eye scores across, up/down; iris across, up/down. */
    static final int EYE_H = 0, EYE_V = 1, IRIS_H = 2, IRIS_V = 3, FEATURES = 4;
    /** A pinch of ridge on the standardized signals, so two signals that move together stay sane. */
    static final double RIDGE = 0.1;
    /** Of a frame's scatter, the share of variance the gaze smoothing leaves (about a third). */
    static final double SMOOTHED = 1 / 3.0;

    /**
     * The eyes' angle down inside the head = aY + bY·eye scores up/down + cY·iris up/down (degrees;
     * a weight is 0 when unused); across alike with aX, bX, cX.
     */
    final float aY;
    final float bY;
    final float cY;
    final float aX;
    final float bX;
    final float cX;
    /** Expected error on the screen, up/down and across (fractions of the screen). */
    final float rmsY;
    final float rmsX;
    /**
     * How much the head's yaw and pitch count (D-134): measured by the calibration's head step, 1
     * when it could not tell. -1 = MediaPipe's angles turn the other way than assumed.
     */
    float kYaw = 1;
    float kPitch = 1;
    float offY;
    float offX;
    /** The reader's eyes from the same calibration (D-106), null when that step failed. */
    EyeProfile eyes;

    GazeModel(float[] y, float[] x, float rmsY, float rmsX) {
        this.aY = y[0];
        this.bY = y[1];
        this.cY = y[2];
        this.aX = x[0];
        this.bX = x[1];
        this.cX = x[2];
        this.rmsY = rmsY;
        this.rmsX = rmsX;
    }

    /**
     * The eye scores' gaze across, in the degrees of {@link ReadingPose}'s eye part. No head pose
     * (D-130): it made the screen point worse; only looking or away ({@link ReadingPose}) takes it.
     */
    public static float eyesH(FaceFrame f) {
        return EyeRules.EYE_DEG_H * f.eyesH;
    }

    /** The eye scores' gaze down (+) / up, degrees, as {@link #eyesH}. */
    public static float eyesV(FaceFrame f) {
        return -EyeRules.EYE_DEG_V * f.eyesV;
    }

    /**
     * The irises across: each one's place between its eye's corners. Looking to one side moves
     * one toward its outer corner and the other toward its inner one; looking near moves both in,
     * which cancels out.
     */
    public static float irisH(FaceFrame f) {
        return (f.irisRX - f.irisLX) / 2;
    }

    /** The irises below their eye-corner lines (+ = down): the corners stay put, the lids follow the eye. */
    public static float irisV(FaceFrame f) {
        return (f.irisLD + f.irisRD) / 2;
    }

    /** All a frame's eye signals, indexed by {@link #EYE_H} .. {@link #IRIS_V}. */
    public static float[] features(FaceFrame f) {
        return new float[]{eyesH(f), eyesV(f), irisH(f), irisV(f)};
    }

    /**
     * A calibration dot: where it was shown, the {@link #features} and {@link GazeGeometry#head}s of
     * the frames looking at it, and so the angles the eyes needed inside the head to see it.
     */
    public static final class Dot {
        final float x;
        final float y;
        final List<float[]> frames;
        final List<float[]> heads;
        final GazeGeometry g;
        final float[] median = new float[FEATURES];
        final float[] head = new float[GazeGeometry.HEAD];
        /** The eyes' angles inside the head the dot needed, {across, down} (medians over the frames). */
        final float[] need = new float[2];
        /** Screen fraction per degree at the dot's distance, {across, down}. */
        final float[] perDegree;

        public Dot(float x, float y, List<float[]> frames, List<float[]> heads, GazeGeometry g) {
            this.x = x;
            this.y = y;
            this.frames = new ArrayList<>(frames);
            this.heads = new ArrayList<>(heads);
            this.g = g;
            for (int i = 0; i < FEATURES; i++) {
                median[i] = Calibration.median(this.frames, i);
            }
            for (int i = 0; i < GazeGeometry.HEAD; i++) {
                head[i] = Calibration.median(heads, i);
            }
            perDegree = g.perDegree(head);
            aim(1, 1);
        }

        /** The angles the eyes needed with the head's yaw and pitch counted this much. */
        void aim(float kYaw, float kPitch) {
            final List<float[]> needs = new ArrayList<>();
            for (float[] h : heads) {
                needs.add(g.eyeAngles(x, y, turned(h, kYaw, kPitch)));
            }
            need[0] = Calibration.median(needs, 0);
            need[1] = Calibration.median(needs, 1);
        }

        float target(boolean vertical) {
            return need[vertical ? 1 : 0];
        }
    }

    /** A head with its yaw and pitch counted this much. */
    static float[] turned(float[] head, float kYaw, float kPitch) {
        if (kYaw == 1 && kPitch == 1) {
            return head;
        }
        final float[] h = head.clone();
        h[GazeGeometry.YAW] *= kYaw;
        h[GazeGeometry.PITCH] *= kPitch;
        return h;
    }

    /**
     * The model of the dots, then of how much the head counts from {@code headStep} (the eyes kept
     * on one dot while the head turns, D-134; null: not measured), and the dots again with it.
     */
    public static GazeModel fit(List<Dot> dots, Dot headStep) {
        final GazeModel m = fit(dots);
        if (m == null || headStep == null) {
            return m;
        }
        final float kYaw = gain(m, headStep, false);
        final float kPitch = gain(m, headStep, true);
        if (kYaw == 1 && kPitch == 1) {
            return m;
        }
        for (Dot d : dots) {
            d.aim(kYaw, kPitch);
        }
        final GazeModel again = fit(dots);
        if (again == null) {
            for (Dot d : dots) {
                d.aim(1, 1);
            }
            return m;
        }
        again.kYaw = kYaw;
        again.kPitch = kPitch;
        return again;
    }

    /**
     * How much the head's yaw (or pitch) counts: in the head step the dot stays where it is, so
     * what the head must add to the eyes' angle (by model {@code m}) to reach it, against the
     * head's angle, is a line whose slope is the answer. 1 when the head did not turn enough
     * ({@link EyeRules#HEAD_STEP_MIN_DEG}) or the slope is not a sane one.
     */
    static float gain(GazeModel m, Dot step, boolean vertical) {
        final int axis = vertical ? 1 : 0;
        final int turn = vertical ? GazeGeometry.PITCH : GazeGeometry.YAW;
        final int n = Math.min(step.frames.size(), step.heads.size());
        if (n < EyeRules.CAL_MIN_FRAMES) {
            return 1;
        }
        final float[] angle = new float[n];
        final float[] rest = new float[n];
        for (int i = 0; i < n; i++) {
            final float[] f = step.frames.get(i);
            final float[] h = step.heads.get(i);
            final float[] eyes = GazeGeometry.untilt(m.across(f), m.down(f), h[GazeGeometry.TILT]);
            angle[i] = h[turn];
            rest[i] = step.g.cameraAngles(step.x, step.y, h)[axis] - eyes[axis];
        }
        final float[] sorted = angle.clone();
        java.util.Arrays.sort(sorted);
        if (sorted[n * 9 / 10] - sorted[n / 10] < EyeRules.HEAD_STEP_MIN_DEG) {
            return 1;
        }
        double ma = 0;
        double mr = 0;
        for (int i = 0; i < n; i++) {
            ma += angle[i] / n;
            mr += rest[i] / n;
        }
        double cov = 0;
        double var = 0;
        for (int i = 0; i < n; i++) {
            cov += (angle[i] - ma) * (rest[i] - mr);
            var += (angle[i] - ma) * (angle[i] - ma);
        }
        final double k = cov / var;
        final double size = Math.abs(k);
        return size >= EyeRules.HEAD_GAIN_MIN && size <= EyeRules.HEAD_GAIN_MAX ? (float) k : 1;
    }

    /** @return null for fewer than 3 dots or no signal that moved up and down between them */
    public static GazeModel fit(List<Dot> dots) {
        if (dots == null || dots.size() < 3) {
            return null;
        }
        final Line y = best(dots, true);
        if (y == null) {
            return null;
        }
        Line x = best(dots, false);
        if (x == null) {
            // nothing moved across: the eyes' mean angle, so at least the middle is right
            float mean = 0;
            for (Dot d : dots) {
                mean += d.need[0] / dots.size();
            }
            x = new Line(new float[]{mean, 0, 0}, 1);
        }
        return new GazeModel(y.w, x.w, y.err, x.err);
    }

    /** One axis: {a, b (eye scores), c (iris)} and its expected error. */
    static final class Line {
        final float[] w;
        final float err;

        Line(float[] w, float err) {
            this.w = w;
            this.err = err;
        }
    }

    /** The eye scores, the iris or both, whichever places this axis best (ties: the simpler). */
    static Line best(List<Dot> dots, boolean vertical) {
        final int eye = vertical ? EYE_V : EYE_H;
        final int iris = vertical ? IRIS_V : IRIS_H;
        Line best = null;
        for (int[] cols : new int[][]{{eye}, {iris}, {eye, iris}}) {
            final Line l = line(dots, cols, vertical);
            if (l != null && (best == null || l.err < best.err)) {
                best = l;
            }
        }
        return best;
    }

    /** The axis from these signals, null when one did not move between the dots. */
    static Line line(List<Dot> dots, int[] cols, boolean vertical) {
        final float[] w = solve(dots, cols, vertical, -1);
        if (w == null) {
            return null;
        }
        final int axis = vertical ? 1 : 0;
        // leave one dot out: how well the others place it (degrees, then on the screen)
        double loo = 0;
        for (int j = 0; j < dots.size(); j++) {
            final float[] wj = solve(dots, cols, vertical, j);
            if (wj == null) {
                return null;
            }
            final Dot d = dots.get(j);
            final double e = (at(wj, cols, d.median) - d.target(vertical)) * d.perDegree[axis];
            loo += e * e;
        }
        // the frames' own scatter round their dot, on the screen, as the smoothing leaves it
        double noise = 0;
        int n = 0;
        for (Dot d : dots) {
            final float centre = at(w, cols, d.median);
            for (float[] f : d.frames) {
                final double e = (at(w, cols, f) - centre) * d.perDegree[axis];
                noise += e * e;
                n++;
            }
        }
        final float err = (float) Math.sqrt(loo / dots.size() + (n == 0 ? 0 : SMOOTHED * noise / n));
        final float[] abc = {w[0], 0, 0};
        for (int i = 0; i < cols.length; i++) {
            abc[cols[i] == EYE_H || cols[i] == EYE_V ? 1 : 2] = w[i + 1];
        }
        return new Line(abc, err);
    }

    /**
     * Least squares screen = w0 + Σ wi·signal over the dots' medians but {@code skip}, on
     * standardized signals with a pinch of ridge. @return {w0, w1..} for the raw signals, null
     * when a signal did not move
     */
    static float[] solve(List<Dot> dots, int[] cols, boolean vertical, int skip) {
        final int k = cols.length;
        final double[] mu = new double[k];
        final double[] sd = new double[k];
        int n = 0;
        for (int j = 0; j < dots.size(); j++) {
            if (j == skip) {
                continue;
            }
            n++;
            for (int i = 0; i < k; i++) {
                mu[i] += dots.get(j).median[cols[i]];
            }
        }
        if (n < k + 1) {
            return null;
        }
        for (int i = 0; i < k; i++) {
            mu[i] /= n;
        }
        for (int j = 0; j < dots.size(); j++) {
            if (j != skip) {
                for (int i = 0; i < k; i++) {
                    final double d = dots.get(j).median[cols[i]] - mu[i];
                    sd[i] += d * d;
                }
            }
        }
        for (int i = 0; i < k; i++) {
            sd[i] = Math.sqrt(sd[i] / n);
            if (sd[i] < 1e-6) {
                return null;
            }
        }
        // normal equations [1, z1..zk] · w = target, with the ridge on the zi
        final double[][] m = new double[k + 1][k + 2];
        final double[] z = new double[k + 1];
        z[0] = 1;
        for (int j = 0; j < dots.size(); j++) {
            if (j == skip) {
                continue;
            }
            final Dot d = dots.get(j);
            for (int i = 0; i < k; i++) {
                z[i + 1] = (d.median[cols[i]] - mu[i]) / sd[i];
            }
            for (int r = 0; r <= k; r++) {
                for (int c = 0; c <= k; c++) {
                    m[r][c] += z[r] * z[c];
                }
                m[r][k + 1] += z[r] * d.target(vertical);
            }
        }
        for (int r = 1; r <= k; r++) {
            m[r][r] += RIDGE;
        }
        final double[] ws = gauss(m);
        if (ws == null) {
            return null;
        }
        final float[] w = new float[k + 1];
        double a = ws[0];
        for (int i = 0; i < k; i++) {
            w[i + 1] = (float) (ws[i + 1] / sd[i]);
            a -= ws[i + 1] * mu[i] / sd[i];
        }
        w[0] = (float) a;
        return w;
    }

    /** Solves an augmented system (Gauss-Jordan, partial pivoting); null when singular. */
    static double[] gauss(double[][] m) {
        final int n = m.length;
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) {
                if (Math.abs(m[r][c]) > Math.abs(m[p][c])) {
                    p = r;
                }
            }
            if (Math.abs(m[p][c]) < 1e-12) {
                return null;
            }
            final double[] t = m[c];
            m[c] = m[p];
            m[p] = t;
            for (int r = 0; r < n; r++) {
                if (r != c) {
                    final double f = m[r][c] / m[c][c];
                    for (int j = c; j <= n; j++) {
                        m[r][j] -= f * m[c][j];
                    }
                }
            }
        }
        final double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = m[i][n] / m[i][i];
        }
        return w;
    }

    /** {@code w} of {@link #solve} applied to one frame's signals. */
    static float at(float[] w, int[] cols, float[] f) {
        float v = w[0];
        for (int i = 0; i < cols.length; i++) {
            v += w[i + 1] * f[cols[i]];
        }
        return v;
    }

    /** Good enough to steer anything: placing a dot up/down within {@link EyeRules#CAL_RMS}. */
    public boolean isGood() {
        return rmsY <= EyeRules.CAL_RMS && (bY != 0 || cY != 0);
    }

    /** The eyes' angle down inside the head for a frame's {@link #features}, degrees. */
    float down(float[] f) {
        return aY + bY * f[EYE_V] + cY * f[IRIS_V];
    }

    float across(float[] f) {
        return aX + bX * f[EYE_H] + cX * f[IRIS_H];
    }

    /** The screen point {x, y} of a frame's {@link #features} with this {@link GazeGeometry#head}. */
    public float[] point(float[] f, float[] head, GazeGeometry g) {
        final float[] p = g.point(across(f), down(f), turned(head, kYaw, kPitch));
        return new float[]{p[0] + offX, p[1] + offY};
    }

    /** Re-zero: the reader looks at ({@code atX}, {@code atY}) with these {@link #features} and head. */
    public void rezero(float atX, float atY, float[] f, float[] head, GazeGeometry g) {
        final float[] p = g.point(across(f), down(f), turned(head, kYaw, kPitch));
        offY = atY - p[1];
        if (bX != 0 || cX != 0) {
            offX = atX - p[0];
        }
    }

    /** A small move of the vertical offset (book mode: the first lines after a page turn). */
    public void nudgeY(float delta) {
        offY = Math.max(-EyeRules.NUDGE_MAX, Math.min(EyeRules.NUDGE_MAX, offY + delta));
    }

    public float rmsY() {
        return rmsY;
    }

    /** Which signal an axis uses: "eyes" (eye scores), "iris", "both" or "none". */
    public String signal(boolean vertical) {
        final float b = vertical ? bY : bX;
        final float c = vertical ? cY : cX;
        return b != 0 && c != 0 ? "both" : b != 0 ? "eyes" : c != 0 ? "iris" : "none";
    }

    public JSONObject toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("v", VERSION);
            o.put("aY", aY);
            o.put("bY", bY);
            o.put("cY", cY);
            o.put("aX", aX);
            o.put("bX", bX);
            o.put("cX", cX);
            o.put("rmsY", rmsY);
            o.put("rmsX", rmsX);
            o.put("kYaw", kYaw);
            o.put("kPitch", kPitch);
            o.put("offY", offY);
            o.put("offX", offX);
            if (eyes != null) {
                o.put("eyes", eyes.toJson());
            }
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null for a damaged entry; an entry of an older version comes back not good, with its eyes */
    public static GazeModel fromJson(JSONObject o) {
        if (o == null || !o.has("bY")) {
            return null;
        }
        if (o.optInt("v", 1) < VERSION) {
            // the line is no use any more (calibrate again); the reader's eyes still are, for long blink
            final GazeModel old = new GazeModel(new float[]{0, 0, 0}, new float[]{0.5f, 0, 0}, 1, 1);
            old.eyes = EyeProfile.fromJson(o.optJSONObject("eyes"));
            return old;
        }
        final GazeModel m = new GazeModel(
                new float[]{(float) o.optDouble("aY", 0), (float) o.optDouble("bY", 0), (float) o.optDouble("cY", 0)},
                new float[]{(float) o.optDouble("aX", 0.5), (float) o.optDouble("bX", 0), (float) o.optDouble("cX", 0)},
                (float) o.optDouble("rmsY", 1), (float) o.optDouble("rmsX", 1));
        m.offY = (float) o.optDouble("offY", 0);
        m.offX = (float) o.optDouble("offX", 0);
        m.kYaw = (float) o.optDouble("kYaw", 1);
        m.kPitch = (float) o.optDouble("kPitch", 1);
        m.eyes = EyeProfile.fromJson(o.optJSONObject("eyes"));
        return m;
    }
}
