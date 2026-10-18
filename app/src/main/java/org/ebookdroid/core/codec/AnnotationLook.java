package org.ebookdroid.core.codec;

import android.graphics.RectF;

import java.util.List;

/**
 * How an annotation inside a PDF looks (fork, F-008): its color and the lines it covers, for the
 * Markdown export and the card a tap on it opens.
 */
public final class AnnotationLook {

    /** 0xffrrggbb, or 0 when the annotation has no color. */
    public final int color;
    /** One rectangle per quad, page-relative (0..1) like {@link Annotation}; empty for ink and notes. */
    public final List<RectF> lines;

    public AnnotationLook(int color, List<RectF> lines) {
        this.color = color;
        this.lines = lines;
    }

    /**
     * The RGB color of a PDF color.
     *
     * @param n components: 0 none, 1 gray, 3 RGB, 4 CMYK; {@code c} 0..1 each
     */
    public static int rgb(int n, float c0, float c1, float c2, float c3) {
        final float r;
        final float g;
        final float b;
        if (n == 1) {
            r = g = b = c0;
        } else if (n == 3) {
            r = c0;
            g = c1;
            b = c2;
        } else if (n == 4) {
            r = (1 - c0) * (1 - c3);
            g = (1 - c1) * (1 - c3);
            b = (1 - c2) * (1 - c3);
        } else {
            return 0;
        }
        return 0xff000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }
}
