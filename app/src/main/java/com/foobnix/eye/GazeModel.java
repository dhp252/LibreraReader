package com.foobnix.eye;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Where on the screen the reader looks (M3, D-90): the eyes' own direction ({@link #eyesH},
 * {@link #eyesV}, degrees) mapped linearly to screen fractions, 0 at the top / left, 1 at the
 * bottom / right. Fitted per display rotation from the calibration dots; a re-zero moves it as a
 * whole. Pure.
 */
public final class GazeModel {

    /**
     * What the stored lines were fitted on: 2 = the eyes alone (D-130). Version 1 (no "v") took
     * the head pose too; its line does not fit the eyes alone.
     */
    static final int VERSION = 2;

    final float aY;
    final float bY;
    final float aX;
    final float bX;
    final float rmsY;
    final float spreadV;
    float offY;
    float offX;
    /** The reader's eyes from the same calibration (D-106), null when that step failed. */
    EyeProfile eyes;

    GazeModel(float aY, float bY, float aX, float bX, float rmsY, float spreadV) {
        this.aY = aY;
        this.bY = bY;
        this.aX = aX;
        this.bX = bX;
        this.rmsY = rmsY;
        this.spreadV = spreadV;
    }

    /**
     * The gaze across that calibration, gaze, page turn and scroll speed use: the eyes alone, in
     * the degrees of {@link ReadingPose}'s eye part. No head pose (D-130): it made the screen point
     * worse; only looking or away ({@link ReadingPose}) takes it.
     */
    public static float eyesH(FaceFrame f) {
        return EyeRules.EYE_DEG_H * f.eyesH;
    }

    /** The eyes' gaze up (+) / down, degrees, as {@link #eyesH}. */
    public static float eyesV(FaceFrame f) {
        return -EyeRules.EYE_DEG_V * f.eyesV;
    }

    /**
     * @param dots each {screen x, screen y, gaze across, gaze up/down}: the median gaze while the
     *             reader looked at one calibration dot
     * @return null for fewer than 3 dots or no spread at all
     */
    public static GazeModel fit(List<float[]> dots) {
        if (dots == null || dots.size() < 3) {
            return null;
        }
        final float[] y = line(dots, 3, 1);
        final float[] x = line(dots, 2, 0);
        if (y == null) {
            return null;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        double sq = 0;
        for (float[] d : dots) {
            min = Math.min(min, d[3]);
            max = Math.max(max, d[3]);
            final double r = d[1] - (y[0] + y[1] * d[3]);
            sq += r * r;
        }
        final float rms = (float) Math.sqrt(sq / dots.size());
        return new GazeModel(y[0], y[1], x == null ? 0.5f : x[0], x == null ? 0 : x[1], rms, max - min);
    }

    /** Least squares screen = a + b·gaze over column {@code g} → column {@code s}; null without spread. */
    private static float[] line(List<float[]> dots, int g, int s) {
        double mg = 0;
        double ms = 0;
        for (float[] d : dots) {
            mg += d[g];
            ms += d[s];
        }
        mg /= dots.size();
        ms /= dots.size();
        double cov = 0;
        double var = 0;
        for (float[] d : dots) {
            cov += (d[g] - mg) * (d[s] - ms);
            var += (d[g] - mg) * (d[g] - mg);
        }
        if (var < 1e-6) {
            return null;
        }
        final double b = cov / var;
        return new float[]{(float) (ms - b * mg), (float) b};
    }

    /** Good enough to steer anything: the dots fit the line and the eyes moved between them. */
    public boolean isGood() {
        return rmsY <= EyeRules.CAL_RMS && spreadV >= EyeRules.CAL_MIN_SPREAD && bY != 0;
    }

    public float y(float gazeV) {
        return aY + bY * gazeV + offY;
    }

    public float x(float gazeH) {
        return aX + bX * gazeH + offX;
    }

    /** Re-zero: the reader looks at ({@code atX}, {@code atY}) with this gaze. */
    public void rezero(float atX, float atY, float gazeH, float gazeV) {
        offY = atY - (aY + bY * gazeV);
        if (bX != 0) {
            offX = atX - (aX + bX * gazeH);
        }
    }

    /** A small move of the vertical offset (book mode: the first lines after a page turn). */
    public void nudgeY(float delta) {
        offY = Math.max(-EyeRules.NUDGE_MAX, Math.min(EyeRules.NUDGE_MAX, offY + delta));
    }

    public float rmsY() {
        return rmsY;
    }

    public JSONObject toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("v", VERSION);
            o.put("aY", aY);
            o.put("bY", bY);
            o.put("aX", aX);
            o.put("bX", bX);
            o.put("rmsY", rmsY);
            o.put("spreadV", spreadV);
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

    /** @return null for a damaged entry; an entry fitted on the head pose too comes back not good, with its eyes */
    public static GazeModel fromJson(JSONObject o) {
        if (o == null || !o.has("bY")) {
            return null;
        }
        if (o.optInt("v", 1) < VERSION) {
            // the line is no use any more (calibrate again); the reader's eyes still are, for long blink
            final GazeModel old = new GazeModel(0, 0, 0.5f, 0, 1, 0);
            old.eyes = EyeProfile.fromJson(o.optJSONObject("eyes"));
            return old;
        }
        final GazeModel m = new GazeModel((float) o.optDouble("aY", 0), (float) o.optDouble("bY", 0),
                (float) o.optDouble("aX", 0.5), (float) o.optDouble("bX", 0),
                (float) o.optDouble("rmsY", 1), (float) o.optDouble("spreadV", 0));
        m.offY = (float) o.optDouble("offY", 0);
        m.offX = (float) o.optDouble("offX", 0);
        m.eyes = EyeProfile.fromJson(o.optJSONObject("eyes"));
        return m;
    }
}
