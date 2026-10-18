package com.foobnix.eye;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Where on the screen the reader looks (M3, D-90): the gaze of {@link ReadingPose} (degrees)
 * mapped linearly to screen fractions, 0 at the top / left, 1 at the bottom / right. Fitted per
 * display rotation from the calibration dots; a re-zero moves it as a whole. Pure.
 */
public final class GazeModel {

    final float aY;
    final float bY;
    final float aX;
    final float bX;
    final float rmsY;
    final float spreadV;
    float offY;
    float offX;

    GazeModel(float aY, float bY, float aX, float bX, float rmsY, float spreadV) {
        this.aY = aY;
        this.bY = bY;
        this.aX = aX;
        this.bX = bX;
        this.rmsY = rmsY;
        this.spreadV = spreadV;
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
            o.put("aY", aY);
            o.put("bY", bY);
            o.put("aX", aX);
            o.put("bX", bX);
            o.put("rmsY", rmsY);
            o.put("spreadV", spreadV);
            o.put("offY", offY);
            o.put("offX", offX);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null for a damaged entry */
    public static GazeModel fromJson(JSONObject o) {
        if (o == null || !o.has("bY")) {
            return null;
        }
        final GazeModel m = new GazeModel((float) o.optDouble("aY", 0), (float) o.optDouble("bY", 0),
                (float) o.optDouble("aX", 0.5), (float) o.optDouble("bX", 0),
                (float) o.optDouble("rmsY", 1), (float) o.optDouble("spreadV", 0));
        m.offY = (float) o.optDouble("offY", 0);
        m.offX = (float) o.optDouble("offX", 0);
        return m;
    }
}
