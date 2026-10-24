package com.foobnix.eye;

/** Camera choices Eye mode makes, kept pure for the tests. */
public final class CameraMath {

    private CameraMath() {
    }

    /**
     * The auto-exposure frame-rate range to ask for: of the ranges that reach {@code want} frames
     * a second, the one with the lowest floor (a dark room may then expose each frame longer,
     * which is where Eye mode struggles most), then the lowest ceiling (less power).
     *
     * @param lows  lower bounds of the camera's ranges
     * @param highs upper bounds, same order
     * @return the index of the range, -1 when none reaches {@code want}
     */
    public static int pickFpsRange(int[] lows, int[] highs, int want) {
        int best = -1;
        for (int i = 0; i < lows.length && i < highs.length; i++) {
            if (highs[i] < want) {
                continue;
            }
            if (best < 0 || lows[i] < lows[best] || (lows[i] == lows[best] && highs[i] < highs[best])) {
                best = i;
            }
        }
        return best;
    }

    /**
     * The focal length in pixels of a {@code w} × {@code h} frame (either way round) from a sensor
     * of {@code sensorW} × {@code sensorH} mm and a lens of {@code focalMm}: the frame is the
     * sensor scaled, cut to its shape along the sensor's longer side when their shapes differ
     * (a 4:3 frame from a 16:9 sensor keeps the sensor's short side). 0 when something is unknown.
     */
    public static float focalPx(float focalMm, float sensorW, float sensorH, int w, int h) {
        if (focalMm <= 0 || sensorW <= 0 || sensorH <= 0 || w <= 0 || h <= 0) {
            return 0;
        }
        final float sensorLong = Math.max(sensorW, sensorH);
        final float sensorShort = Math.min(sensorW, sensorH);
        final int frameLong = Math.max(w, h);
        final int frameShort = Math.min(w, h);
        final boolean sensorWider = sensorLong / sensorShort > (float) frameLong / frameShort;
        return sensorWider ? focalMm / sensorShort * frameShort : focalMm / sensorLong * frameLong;
    }

    /** Width and height of a {@code w} × {@code h} frame turned by {@code rotation} degrees. */
    public static int[] upright(int w, int h, int rotation) {
        return rotation % 180 == 0 ? new int[]{w, h} : new int[]{h, w};
    }
}
