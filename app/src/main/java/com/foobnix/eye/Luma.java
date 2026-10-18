package com.foobnix.eye;

import java.nio.ByteBuffer;

/** How bright a camera frame is: the mean luminance of an RGBA buffer, from a grid of samples. */
public final class Luma {

    /** Samples per side of the grid. */
    static final int GRID = 24;

    private Luma() {
    }

    /**
     * @param rowStride bytes per row (may be more than width × pixelStride)
     * @param pixelStride bytes per pixel (4 for RGBA_8888)
     * @return 0 (black) .. 1 (white); the buffer's position is not moved
     */
    public static float mean(ByteBuffer buffer, int width, int height, int rowStride, int pixelStride) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        double sum = 0;
        int n = 0;
        for (int gy = 0; gy < GRID; gy++) {
            final int y = (int) ((gy + 0.5) * height / GRID);
            for (int gx = 0; gx < GRID; gx++) {
                final int x = (int) ((gx + 0.5) * width / GRID);
                final int at = y * rowStride + x * pixelStride;
                if (at + 2 >= buffer.limit()) {
                    continue;
                }
                final int r = buffer.get(at) & 0xFF;
                final int g = buffer.get(at + 1) & 0xFF;
                final int b = buffer.get(at + 2) & 0xFF;
                sum += 0.299 * r + 0.587 * g + 0.114 * b;
                n++;
            }
        }
        return n == 0 ? 0 : (float) (sum / n / 255.0);
    }
}
