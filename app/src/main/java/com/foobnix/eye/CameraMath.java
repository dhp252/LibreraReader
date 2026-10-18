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

    /** Width and height of a {@code w} × {@code h} frame turned by {@code rotation} degrees. */
    public static int[] upright(int w, int h, int rotation) {
        return rotation % 180 == 0 ? new int[]{w, h} : new int[]{h, w};
    }
}
