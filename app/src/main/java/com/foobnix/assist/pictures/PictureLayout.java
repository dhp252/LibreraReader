package com.foobnix.assist.pictures;

/**
 * Where the parts of a picture go, as plain numbers (pixels in, pixels out): the wrapping rows of
 * a process's step boxes and the arrows between them, the nodes of a loop round an ellipse with
 * the arrowed arcs between them, the lengths of compared bars, and a table's column width. The
 * views only measure text and draw what this says. Plain Java, no Android.
 */
public final class PictureLayout {

    private PictureLayout() {
    }

    // ------------------------------------------------------------------ a process

    /** Step boxes in rows, each but the last followed by an arrow on its own row. */
    public static final class Flow {
        /** The left and top of each box. */
        public final int[] x;
        public final int[] y;
        /** The row of each box, from 0. */
        public final int[] row;
        /** The centre of the arrow after each box but the last. */
        public final int[] arrowX;
        public final int[] arrowY;
        public final int rows;
        public final int height;

        Flow(int[] x, int[] y, int[] row, int[] arrowX, int[] arrowY, int rows, int height) {
            this.x = x;
            this.y = y;
            this.row = row;
            this.arrowX = arrowX;
            this.arrowY = arrowY;
            this.rows = rows;
            this.height = height;
        }
    }

    /**
     * Lays boxes out left to right, wrapping to a new row when a box and its arrow no longer fit.
     * Boxes of a row are centred on the row's tallest; an arrow stays on the row of the box it
     * follows.
     *
     * @param widths  the width of each box (the caller keeps each within {@link #maxBoxWidth})
     * @param heights the height of each box
     * @param arrow   the width an arrow takes
     * @param gap     the space on each side of an arrow
     * @param rowGap  the space between rows
     * @param width   the width to fill
     */
    public static Flow flow(int[] widths, int[] heights, int arrow, int gap, int rowGap, int width) {
        final int n = widths.length;
        final int[] x = new int[n];
        final int[] y = new int[n];
        final int[] row = new int[n];
        final int[] arrowX = new int[Math.max(0, n - 1)];
        final int[] arrowY = new int[Math.max(0, n - 1)];
        int left = 0;
        int top = 0;
        int rowHeight = 0;
        int rows = n == 0 ? 0 : 1;
        int rowStart = 0;
        for (int i = 0; i < n; i++) {
            final int need = widths[i] + (i < n - 1 ? gap + arrow : 0);
            if (left > 0 && left + need > width) {
                centre(rowStart, i, top, rowHeight, heights, y, arrowY);
                top += rowHeight + rowGap;
                left = 0;
                rowHeight = 0;
                rows++;
                rowStart = i;
            }
            x[i] = left;
            row[i] = rows - 1;
            rowHeight = Math.max(rowHeight, heights[i]);
            left += widths[i];
            if (i < n - 1) {
                arrowX[i] = left + gap + arrow / 2;
                left += gap + arrow + gap;
            }
        }
        centre(rowStart, n, top, rowHeight, heights, y, arrowY);
        return new Flow(x, y, row, arrowX, arrowY, rows, n == 0 ? 0 : top + rowHeight);
    }

    private static void centre(int from, int to, int top, int rowHeight, int[] heights, int[] y, int[] arrowY) {
        for (int i = from; i < to; i++) {
            y[i] = top + (rowHeight - heights[i]) / 2;
            if (i < arrowY.length) {
                arrowY[i] = top + rowHeight / 2;
            }
        }
    }

    /** The widest a step box may be so that it and its arrow fit one row. */
    public static int maxBoxWidth(int width, int arrow, int gap) {
        return Math.max(1, width - gap - arrow);
    }

    // ------------------------------------------------------------------ a loop

    /** The nodes of a loop round an ellipse, the first at the top, clockwise, and the arcs from each to the next. */
    public static final class Loop {
        public final int n;
        public final float width;
        public final float height;
        public final float boxWidth;
        public final float boxHeight;
        /** The centre of each node's box. */
        public final float[] cx;
        public final float[] cy;
        /** The arc from node i to the next as a line through points (x0, y0, x1, y1, ...); empty when the boxes touch. */
        public final float[][] arcs;
        /** The arrowhead at the end of arc i: its tip and the two ends of its base (6 numbers); empty with an empty arc. */
        public final float[][] heads;

        Loop(int n, float width, float height, float boxWidth, float boxHeight) {
            this.n = n;
            this.width = width;
            this.height = height;
            this.boxWidth = boxWidth;
            this.boxHeight = boxHeight;
            this.cx = new float[n];
            this.cy = new float[n];
            this.arcs = new float[n][];
            this.heads = new float[n][];
        }

        /** Whether two boxes come closer than {@code gap} to each other. */
        public boolean crowded(float gap) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (Math.abs(cx[i] - cx[j]) < boxWidth + gap && Math.abs(cy[i] - cy[j]) < boxHeight + gap) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Whether a box reaches into the middle, where the loop's mark and its words stand: the
         * part from {@code up} above the centre to {@code down} below it, {@code half} to each side.
         */
        public boolean coversCentre(float half, float up, float down) {
            for (int i = 0; i < n; i++) {
                final boolean beside = Math.abs(cx[i] - width / 2) < boxWidth / 2 + half;
                final float top = cy[i] - boxHeight / 2;
                final float bottom = cy[i] + boxHeight / 2;
                if (beside && bottom > height / 2 - up && top < height / 2 + down) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final double STEP = Math.PI / 360;

    /**
     * A loop of {@code n} boxes inside {@code width} x {@code height}: box centres on the ellipse
     * that keeps every box inside, and between neighbours an arc that starts and ends {@code gap}
     * away from the boxes, with an arrowhead of length {@code head} at its end.
     */
    public static Loop loop(int n, float width, float height, float boxWidth, float boxHeight, float gap, float head) {
        final Loop loop = new Loop(n, width, height, boxWidth, boxHeight);
        if (n == 0) {
            return loop;
        }
        final double rx = Math.max(0, (width - boxWidth) / 2);
        final double ry = Math.max(0, (height - boxHeight) / 2);
        final double ox = width / 2;
        final double oy = height / 2;
        final double turn = 2 * Math.PI / n;
        for (int i = 0; i < n; i++) {
            final double t = -Math.PI / 2 + i * turn;
            loop.cx[i] = (float) (ox + rx * Math.cos(t));
            loop.cy[i] = (float) (oy + ry * Math.sin(t));
        }
        for (int i = 0; i < n; i++) {
            loop.arcs[i] = new float[0];
            loop.heads[i] = new float[0];
            if (n < 2) {
                continue;
            }
            final int next = (i + 1) % n;
            final double from = -Math.PI / 2 + i * turn;
            final double to = from + turn;
            // leave the first box, stop short of the next
            double start = from;
            while (start < to && inside(ox + rx * Math.cos(start), oy + ry * Math.sin(start), loop, i, gap)) {
                start += STEP;
            }
            double tip = to;
            while (tip > start && inside(ox + rx * Math.cos(tip), oy + ry * Math.sin(tip), loop, next, gap)) {
                tip -= STEP;
            }
            final double tipX = ox + rx * Math.cos(tip);
            final double tipY = oy + ry * Math.sin(tip);
            // the line ends where the arrowhead begins
            double base = tip;
            while (base > start && Math.hypot(ox + rx * Math.cos(base) - tipX, oy + ry * Math.sin(base) - tipY) < head) {
                base -= STEP;
            }
            if (base <= start) {
                continue;
            }
            final int points = Math.max(2, (int) Math.ceil((base - start) / (Math.PI / 60)) + 1);
            final float[] line = new float[points * 2];
            for (int p = 0; p < points; p++) {
                final double t = start + (base - start) * p / (points - 1);
                line[p * 2] = (float) (ox + rx * Math.cos(t));
                line[p * 2 + 1] = (float) (oy + ry * Math.sin(t));
            }
            loop.arcs[i] = line;
            final double baseX = ox + rx * Math.cos(base);
            final double baseY = oy + ry * Math.sin(base);
            final double length = Math.max(1e-6, Math.hypot(tipX - baseX, tipY - baseY));
            final double half = head * 0.55;
            final double px = -(tipY - baseY) / length * half;
            final double py = (tipX - baseX) / length * half;
            loop.heads[i] = new float[]{(float) tipX, (float) tipY, (float) (baseX + px), (float) (baseY + py),
                    (float) (baseX - px), (float) (baseY - py)};
        }
        return loop;
    }

    private static boolean inside(double x, double y, Loop loop, int node, float gap) {
        return Math.abs(x - loop.cx[node]) < loop.boxWidth / 2 + gap && Math.abs(y - loop.cy[node]) < loop.boxHeight / 2 + gap;
    }

    /** The height a loop of {@code n} nodes is drawn in, for one dp of {@code dp} pixels (the canvas draws four in 344 dp). */
    public static float loopHeight(int n, float dp) {
        return (n <= 2 ? 264 : n <= 4 ? 344 : 420) * dp;
    }

    /**
     * The loop with the widest boxes (136 dp at most, 88 dp at least, 60 dp tall) that keeps them
     * 8 dp apart in {@code width} and clear of the mark in the middle.
     */
    public static Loop fitLoop(int n, float width, float dp) {
        final float height = loopHeight(n, dp);
        Loop loop = null;
        for (float box = Math.min(136 * dp, width * 0.42f); loop == null || box >= 88 * dp; box -= 4 * dp) {
            loop = loop(n, width, height, box, 60 * dp, 4 * dp, 9 * dp);
            // the 56 dp mark, and under it up to two lines of words 96 dp wide
            if (!loop.crowded(8 * dp) && !loop.coversCentre(30 * dp, 30 * dp, 30 * dp)
                    && !loop.coversCentre(50 * dp, -30 * dp, 70 * dp)) {
                break;
            }
        }
        return loop;
    }

    // ------------------------------------------------------------------ bars and tables

    /** The length of each compared bar as a share of the longest (0 to 1); all 0 when nothing is above 0. */
    public static float[] bars(double[] numbers) {
        double max = 0;
        for (double n : numbers) {
            if (!Double.isNaN(n) && !Double.isInfinite(n)) {
                max = Math.max(max, n);
            }
        }
        final float[] out = new float[numbers.length];
        for (int i = 0; i < out.length; i++) {
            final double n = numbers[i];
            out[i] = max <= 0 || Double.isNaN(n) || Double.isInfinite(n) ? 0 : (float) Math.max(0, Math.min(1, n / max));
        }
        return out;
    }

    /**
     * The width of a table's columns (the row names take one too): an equal share of
     * {@code available} while that leaves each at least {@code min}, else {@code min}, and the
     * table scrolls sideways.
     */
    public static int tableColumn(int columns, int available, int min) {
        final int share = available / Math.max(1, columns + 1);
        return Math.max(share, min);
    }

    /** Whether a table of that many columns is wider than {@code available} and scrolls sideways. */
    public static boolean tableScrolls(int columns, int available, int min) {
        return tableColumn(columns, available, min) * (columns + 1) > available;
    }
}
