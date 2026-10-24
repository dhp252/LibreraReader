package com.foobnix.crop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the printed matter of a page lies, measured on a small plain render of it.
 * <p>
 * Ink is whatever stands out from the paper, the paper being the page's most common tone, so
 * grey or yellowed scans and light text on dark pages are measured the same way as black on
 * white. Black bands along the edges (scanner borders, the binding's shadow) and specks away from
 * the text are not content. A short line at the head or the foot, set apart by a clear gap, is
 * reported apart from the body: it may be a running header or a page number, which {@link
 * CropPlan} decides from where such lines sit on the other pages.
 * <p>
 * Pure: pixels in, fractions of the sample out (0..1, {left, top, right, bottom}).
 */
public final class CropFinder {

    /** How far a tone must be from the paper's to be ink (0..255). */
    static final int INK = 40;
    /** An edge band is a row or column, within this share of the edge, that is mostly ink. */
    static final float EDGE_ZONE = 0.10f;
    static final float EDGE_SOLID = 0.5f;
    /** ... followed, still within the zone, by a line of paper (almost no ink). */
    static final float EDGE_PAPER = 0.02f;
    /** A run at the outside thinner than this, set apart by {@link #SPECK_GAP}, is a speck. */
    static final float SPECK_ROWS = 0.005f;
    static final float SPECK_COLUMNS = 0.01f;
    static final float SPECK_GAP = 0.02f;
    /** A header or a foot line: no taller than this, within this share of the head or foot. */
    static final float LINE_MAX = 0.03f;
    static final float LINE_ZONE = 0.15f;
    /** ... set apart from the body by at least this, and this many line gaps. */
    static final float LINE_GAP = 0.025f;
    static final float LINE_GAPS = 2.5f;
    /** A foot line is at most this wide against the body (a page number, not a footnote). */
    static final float FOOT_NARROW = 0.4f;

    private CropFinder() {
    }

    /** What was found on a page. */
    public static final class Measure {
        /** Everything printed, headers and page numbers included. */
        public final float[] all;
        /** All but the head and foot lines below. */
        public final float[] body;
        /** A short line apart at the head / at the foot, or null. */
        public final float[] head, foot;

        Measure(float[] all, float[] body, float[] head, float[] foot) {
            this.all = all;
            this.body = body;
            this.head = head;
            this.foot = foot;
        }
    }

    /**
     * @param argb   the sample, row by row
     * @param ignore {left, top, right, bottom}: shares of the sample left out at each edge
     *               (the crop dialog's margins), or null
     * @return what is printed, or null when the page is blank
     */
    public static Measure measure(int[] argb, int w, int h, float[] ignore) {
        if (w < 8 || h < 8 || argb.length < w * h) {
            return null;
        }
        int x0 = 0, y0 = 0, x1 = w, y1 = h;
        if (ignore != null) {
            x0 = clamp((int) (w * ignore[0]), 0, w / 2);
            y0 = clamp((int) (h * ignore[1]), 0, h / 2);
            x1 = clamp(w - (int) (w * ignore[2]), x0 + 1, w);
            y1 = clamp(h - (int) (h * ignore[3]), y0 + 1, h);
        }

        final boolean[] ink = ink(argb, w, h, x0, y0, x1, y1);

        // edge bands: twice, as cutting the columns changes what the rows hold and back
        final int zoneX = (int) (w * EDGE_ZONE), zoneY = (int) (h * EDGE_ZONE);
        for (int pass = 0; pass < 2; pass++) {
            final float[] cover = new float[Math.max(w, h)];
            for (int x = x0; x < x1; x++) {
                cover[x] = (float) column(ink, w, x, y0, y1) / (y1 - y0);
            }
            final int left = band(cover, x0, Math.min(x0 + zoneX, x1), 1);
            final int right = band(cover, x1 - 1, Math.max(x1 - 1 - zoneX, left), -1) + 1;
            x0 = left;
            x1 = Math.max(right, x0 + 1);
            for (int y = y0; y < y1; y++) {
                cover[y] = (float) row(ink, w, y, x0, x1) / (x1 - x0);
            }
            final int up = band(cover, y0, Math.min(y0 + zoneY, y1), 1);
            final int down = band(cover, y1 - 1, Math.max(y1 - 1 - zoneY, up), -1) + 1;
            y0 = up;
            y1 = Math.max(down, y0 + 1);
        }

        final int[] rows = new int[h];
        for (int y = y0; y < y1; y++) {
            rows[y] = row(ink, w, y, x0, x1);
        }
        final List<int[]> runs = runs(rows, y0, y1);
        dropSpecks(runs, (int) Math.ceil(h * SPECK_ROWS), (int) (h * SPECK_GAP));
        if (runs.isEmpty()) {
            return null;
        }

        final int top = runs.get(0)[0], bottom = runs.get(runs.size() - 1)[1];
        final int[] cols = new int[w];
        for (int x = x0; x < x1; x++) {
            cols[x] = column(ink, w, x, top, bottom);
        }
        final List<int[]> colRuns = runs(cols, x0, x1);
        dropSpecks(colRuns, (int) Math.ceil(w * SPECK_COLUMNS), (int) (w * SPECK_GAP));
        if (colRuns.isEmpty()) {
            return null;
        }
        final int left = colRuns.get(0)[0], right = colRuns.get(colRuns.size() - 1)[1];

        final float[] all = box(left, top, right, bottom, w, h);

        // head and foot lines: the first and last blocks of lines, when short and set apart
        final List<int[]> blocks = blocks(runs, h);
        int[] head = null, foot = null;
        if (blocks.size() >= 2) {
            final int[] first = blocks.get(0);
            if (first[1] - first[0] <= h * LINE_MAX && first[1] <= h * LINE_ZONE) {
                head = first;
            }
            final int[] last = blocks.get(blocks.size() - 1);
            if (blocks.size() - (head != null ? 1 : 0) >= 2
                    && last[1] - last[0] <= h * LINE_MAX && last[0] >= h * (1 - LINE_ZONE)) {
                foot = last;
            }
        }
        final int bodyTop = head != null ? blocks.get(1)[0] : top;
        final int bodyBottom = foot != null ? blocks.get(blocks.size() - 2)[1] : bottom;
        final int[] bodyX = extent(ink, w, bodyTop, bodyBottom, left, right);
        if (foot != null) {
            final int[] footX = extent(ink, w, foot[0], foot[1], left, right);
            if (footX[1] - footX[0] > FOOT_NARROW * (bodyX[1] - bodyX[0])) {
                foot = null;
            }
        }
        final int[] bx = foot == null && head == null ? new int[]{left, right} : extent(ink, w,
                bodyTop, foot != null ? bodyBottom : bottom, left, right);
        final float[] body = box(bx[0], bodyTop, bx[1], foot != null ? bodyBottom : bottom, w, h);
        return new Measure(all, body,
                head == null ? null : box(left, head[0], right, head[1], w, h),
                foot == null ? null : box(left, foot[0], right, foot[1], w, h));
    }

    /** Ink inside the region: far enough from the paper's tone, the most common one there. */
    static boolean[] ink(int[] argb, int w, int h, int x0, int y0, int x1, int y1) {
        final int[] lum = new int[w * h];
        final int[] bins = new int[64];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                final int i = y * w + x;
                final int p = argb[i];
                final int l = (299 * ((p >> 16) & 0xff) + 587 * ((p >> 8) & 0xff) + 114 * (p & 0xff)) / 1000;
                lum[i] = l;
                bins[l >> 2]++;
            }
        }
        int mode = 0;
        for (int b = 1; b < bins.length; b++) {
            if (bins[b] > bins[mode]) {
                mode = b;
            }
        }
        // the paper's tone: the mean of the most common bin and its two neighbours
        long sum = 0, n = 0;
        for (int b = Math.max(0, mode - 1); b <= Math.min(63, mode + 1); b++) {
            sum += (long) bins[b] * (b * 4 + 2);
            n += bins[b];
        }
        final int paper = n == 0 ? 255 : (int) (sum / n);

        final boolean[] ink = new boolean[w * h];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                final int i = y * w + x;
                ink[i] = Math.abs(lum[i] - paper) >= INK;
            }
        }
        return ink;
    }

    /**
     * Where an edge band ends, walking from {@code start} towards {@code limit} by {@code dir}:
     * lines mostly ink, then paper before the zone ends (a scanner border, the binding's shadow).
     * A band that runs on into the page is a picture to the edge, and is kept: {@code start}.
     */
    static int band(float[] cover, int start, int limit, int dir) {
        int i = start;
        while (i != limit && cover[i] > EDGE_SOLID) {
            i += dir;
        }
        if (i == start) {
            return start;
        }
        for (int j = i; j != limit; j += dir) {
            if (cover[j] <= EDGE_PAPER) {
                return i;
            }
        }
        return start;
    }

    static int row(boolean[] ink, int w, int y, int x0, int x1) {
        int n = 0;
        for (int x = x0, i = y * w + x0; x < x1; x++, i++) {
            if (ink[i]) {
                n++;
            }
        }
        return n;
    }

    static int column(boolean[] ink, int w, int x, int y0, int y1) {
        int n = 0;
        for (int y = y0, i = y0 * w + x; y < y1; y++, i += w) {
            if (ink[i]) {
                n++;
            }
        }
        return n;
    }

    /** Runs {start, end} of lines holding at least 2 ink pixels (one alone is noise). */
    static List<int[]> runs(int[] counts, int from, int to) {
        final List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = from; i < to; i++) {
            final boolean on = counts[i] >= 2;
            if (on && start < 0) {
                start = i;
            } else if (!on && start >= 0) {
                runs.add(new int[]{start, i});
                start = -1;
            }
        }
        if (start >= 0) {
            runs.add(new int[]{start, to});
        }
        return runs;
    }

    /** Thin runs at either end, set apart from the next one, are specks: drop them. */
    static void dropSpecks(List<int[]> runs, int thin, int gap) {
        while (runs.size() >= 2) {
            final int[] a = runs.get(0), b = runs.get(1);
            if (a[1] - a[0] <= thin && b[0] - a[1] >= gap) {
                runs.remove(0);
            } else {
                break;
            }
        }
        while (runs.size() >= 2) {
            final int[] a = runs.get(runs.size() - 1), b = runs.get(runs.size() - 2);
            if (a[1] - a[0] <= thin && a[0] - b[1] >= gap) {
                runs.remove(runs.size() - 1);
            } else {
                break;
            }
        }
        if (runs.size() == 1 && runs.get(0)[1] - runs.get(0)[0] <= thin) {
            runs.clear();
        }
    }

    /**
     * Lines grouped into blocks: a gap of more than {@link #LINE_GAP} of the height and
     * {@link #LINE_GAPS} usual line gaps starts a new block. Too few lines to tell: one block.
     */
    static List<int[]> blocks(List<int[]> runs, int h) {
        final List<int[]> blocks = new ArrayList<>();
        if (runs.size() < 4) {
            blocks.add(new int[]{runs.get(0)[0], runs.get(runs.size() - 1)[1]});
            return blocks;
        }
        final int[] gaps = new int[runs.size() - 1];
        for (int i = 1; i < runs.size(); i++) {
            gaps[i - 1] = runs.get(i)[0] - runs.get(i - 1)[1];
        }
        final int[] sorted = gaps.clone();
        Arrays.sort(sorted);
        final float split = Math.max(h * LINE_GAP, LINE_GAPS * sorted[sorted.length / 2]);
        int start = runs.get(0)[0];
        for (int i = 1; i < runs.size(); i++) {
            if (gaps[i - 1] >= split) {
                blocks.add(new int[]{start, runs.get(i - 1)[1]});
                start = runs.get(i)[0];
            }
        }
        blocks.add(new int[]{start, runs.get(runs.size() - 1)[1]});
        return blocks;
    }

    /** {left, right} of the ink between rows y0 and y1, within left..right. */
    static int[] extent(boolean[] ink, int w, int y0, int y1, int left, int right) {
        int l = right, r = left;
        for (int y = y0; y < y1; y++) {
            for (int x = left, i = y * w + left; x < right; x++, i++) {
                if (ink[i]) {
                    if (x < l) {
                        l = x;
                    }
                    if (x + 1 > r) {
                        r = x + 1;
                    }
                }
            }
        }
        return l < r ? new int[]{l, r} : new int[]{left, right};
    }

    static float[] box(int l, int t, int r, int b, int w, int h) {
        return new float[]{(float) l / w, (float) t / h, (float) r / w, (float) b / h};
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
