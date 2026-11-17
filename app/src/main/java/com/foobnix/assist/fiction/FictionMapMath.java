package com.foobnix.assist.fiction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The geometry of the places map, free of Android: an equirectangular projection fitted to the
 * places, which may straddle the 180° line; legs drawn the short way round; and, when too few
 * places have coordinates, a schematic route with the places in order along a winding line.
 */
public final class FictionMapMath {

    private FictionMapMath() {
    }

    /** Places with coordinates the map needs before it draws them where they are. */
    public static final int MIN_LOCATED = 3;

    /**
     * Where to cut the globe so the places sit together: the longitude in the middle of the
     * widest stretch without a place. Longitudes at or after the cut (going east) are drawn left
     * to right from it. -180 for no places.
     */
    public static double cut(double[] lons) {
        if (lons == null || lons.length == 0) {
            return -180;
        }
        final double[] sorted = new double[lons.length];
        for (int i = 0; i < lons.length; i++) {
            sorted[i] = norm(lons[i]);
        }
        Arrays.sort(sorted);
        double widest = -1;
        double cut = -180;
        for (int i = 0; i < sorted.length; i++) {
            final double a = sorted[i];
            final double b = i + 1 < sorted.length ? sorted[i + 1] : sorted[0] + 360;
            if (b - a > widest) {
                widest = b - a;
                cut = norm(a + (b - a) / 2);
            }
        }
        return cut;
    }

    /** A longitude in [-180, 180). */
    static double norm(double lon) {
        double l = lon % 360;
        if (l < -180) {
            l += 360;
        } else if (l >= 180) {
            l -= 360;
        }
        return l;
    }

    /** Degrees east of the cut, in [0, 360). */
    static double east(double lon, double cut) {
        double d = (norm(lon) - cut) % 360;
        if (d < 0) {
            d += 360;
        }
        return d;
    }

    /** A fitted projection: degrees to the pixels of a drawing area. */
    public static final class Fit {
        final double cut;
        /** Degrees east of the cut at the left edge of the places, and their span. */
        final double west;
        final double spanLon;
        final double south;
        final double spanLat;
        /** Pixels per degree of latitude, and per degree of longitude (shrunk by the middle latitude's cosine). */
        final double perLat;
        final double perLon;
        final double left;
        final double top;

        Fit(double cut, double west, double spanLon, double south, double spanLat, double perLat, double perLon,
            double left, double top) {
            this.cut = cut;
            this.west = west;
            this.spanLon = spanLon;
            this.south = south;
            this.spanLat = spanLat;
            this.perLat = perLat;
            this.perLon = perLon;
            this.left = left;
            this.top = top;
        }

        public float x(double lon) {
            return (float) (left + (east(lon, cut) - west) * perLon);
        }

        public float y(double lat) {
            return (float) (top + (south + spanLat - lat) * perLat);
        }

        /** The width of the whole globe in pixels: a point drawn once more this far left or right is the same place. */
        public float world() {
            return (float) (360 * perLon);
        }

        /** The longitude at the cut, the left edge of the unrolled globe. */
        public double cut() {
            return cut;
        }
    }

    /**
     * Fits the places into a drawing area of {@code width} x {@code height} pixels with {@code pad}
     * pixels free on every side, keeping the shape (one scale for both directions, longitude
     * shrunk by the cosine of the middle latitude), centred.
     */
    public static Fit fit(double[] lats, double[] lons, float width, float height, float pad) {
        final double cut = cut(lons);
        double west = 360;
        double eastMost = 0;
        double south = 90;
        double north = -90;
        for (int i = 0; i < lons.length; i++) {
            final double e = east(lons[i], cut);
            west = Math.min(west, e);
            eastMost = Math.max(eastMost, e);
            south = Math.min(south, lats[i]);
            north = Math.max(north, lats[i]);
        }
        if (lons.length == 0) {
            west = 0;
            eastMost = 360;
            south = -60;
            north = 75;
        }
        // places on one line or one spot still get an area to sit in
        final double spanLon = Math.max(eastMost - west, 2);
        final double spanLat = Math.max(north - south, 2);
        west = (west + eastMost) / 2 - spanLon / 2;
        south = (south + north) / 2 - spanLat / 2;
        north = south + spanLat;
        final double middle = Math.toRadians(Math.max(-80, Math.min(80, (south + north) / 2)));
        final double squeeze = Math.max(0.2, Math.cos(middle));
        final double w = Math.max(1, width - 2 * pad);
        final double h = Math.max(1, height - 2 * pad);
        final double perLat = Math.min(h / spanLat, w / (spanLon * squeeze));
        final double perLon = perLat * squeeze;
        final double left = pad + (w - spanLon * perLon) / 2;
        final double top = pad + (h - spanLat * perLat) / 2;
        return new Fit(cut, west, spanLon, south, spanLat, perLat, perLon, left, top);
    }

    /**
     * A leg between two places, the short way round the globe, as line pieces {x1, y1, x2, y2}:
     * one piece, or two when the short way crosses the cut (each runs off its edge of the map;
     * the view clips them).
     */
    public static List<float[]> leg(Fit f, double lat1, double lon1, double lat2, double lon2) {
        final List<float[]> out = new ArrayList<>();
        final float x1 = f.x(lon1);
        final float y1 = f.y(lat1);
        final float x2 = f.x(lon2);
        final float y2 = f.y(lat2);
        final double across = east(lon2, f.cut) - east(lon1, f.cut);
        if (Math.abs(across) <= 180) {
            out.add(new float[]{x1, y1, x2, y2});
            return out;
        }
        // the short way goes over the cut: towards the far copy of the other end
        final float world = f.world();
        final float shift = across > 0 ? -world : world;
        out.add(new float[]{x1, y1, x2 + shift, y2});
        out.add(new float[]{x1 - shift, y1, x2, y2});
        return out;
    }

    /** The degrees between grid lines for a span, so that 3 to 8 lines cross it. */
    public static double gridStep(double spanDegrees) {
        final double[] steps = {1, 2, 5, 10, 15, 30, 45, 90};
        for (double s : steps) {
            if (spanDegrees / s <= 8) {
                return s;
            }
        }
        return 90;
    }

    /** How many places a row of the schematic route holds in a width of {@code widthDp} dp. */
    public static int perRow(float widthDp) {
        return Math.max(2, Math.min(6, (int) (widthDp / 96)));
    }

    public static int rows(int places, int perRow) {
        return places <= 0 ? 0 : (places + perRow - 1) / perRow;
    }

    /**
     * The schematic route: {@code n} places in order along a winding line, rows of {@code perRow},
     * the first row left to right, the next right to left, and so on. Points as {x, y}.
     *
     * @param rowHeight pixels between two rows
     */
    public static float[][] schematic(int n, int perRow, float width, float pad, float rowHeight) {
        final float[][] out = new float[Math.max(0, n)][];
        final int per = Math.max(1, perRow);
        final float w = Math.max(1, width - 2 * pad);
        final float step = per == 1 ? 0 : w / (per - 1);
        for (int i = 0; i < n; i++) {
            final int row = i / per;
            final int col = i % per;
            final int place = row % 2 == 0 ? col : per - 1 - col;
            out[i] = new float[]{per == 1 ? pad + w / 2 : pad + place * step, pad + row * rowHeight};
        }
        return out;
    }

    /** A line as dashes: {x1, y1, x2, y2, …} for {@code Canvas.drawLines}. */
    public static float[] dashes(float x1, float y1, float x2, float y2, float dash, float gap) {
        final double length = Math.hypot(x2 - x1, y2 - y1);
        if (length <= 0 || dash <= 0) {
            return new float[0];
        }
        final double period = dash + Math.max(0, gap);
        final int count = (int) Math.ceil(length / period);
        final float[] out = new float[count * 4];
        final double ux = (x2 - x1) / length;
        final double uy = (y2 - y1) / length;
        for (int i = 0; i < count; i++) {
            final double from = i * period;
            final double to = Math.min(length, from + dash);
            out[i * 4] = (float) (x1 + ux * from);
            out[i * 4 + 1] = (float) (y1 + uy * from);
            out[i * 4 + 2] = (float) (x1 + ux * to);
            out[i * 4 + 3] = (float) (y1 + uy * to);
        }
        return out;
    }
}
