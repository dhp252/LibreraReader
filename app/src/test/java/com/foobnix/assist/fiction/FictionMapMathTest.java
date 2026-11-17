package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class FictionMapMathTest {

    // London, Suez, Bombay, Calcutta, Hong Kong, Yokohama, San Francisco, New York
    private static final double[] LATS = {51.5, 29.97, 18.97, 22.57, 22.3, 35.44, 37.77, 40.71};
    private static final double[] LONS = {-0.13, 32.55, 72.83, 88.36, 114.2, 139.64, -122.42, -74.0};

    @Test
    public void theCutIsInTheWidestEmptyStretch() {
        // round the world: the Pacific between Yokohama (139.64) and San Francisco (-122.42) is widest
        final double cut = FictionMapMath.cut(LONS);
        assertEquals(-171.39, cut, 0.01);
        // places on both sides of the 180° line: the cut is far from it
        final double pacific = FictionMapMath.cut(new double[]{170, -175, 178, -160});
        assertTrue(pacific > -10 && pacific < 10);
        assertEquals(-180, FictionMapMath.cut(new double[0]), 0);
    }

    @Test
    public void placesAcrossTheDateLineSitTogether() {
        final double[] lats = {-17.7, -13.8, -21.1, -18.1};
        final double[] lons = {178.0, -171.8, -175.2, 179.4};
        final FictionMapMath.Fit f = FictionMapMath.fit(lats, lons, 400, 300, 20);
        // west to east across the line: 178, 179.4, -175.2, -171.8
        assertTrue(f.x(178.0) < f.x(179.4));
        assertTrue(f.x(179.4) < f.x(-175.2));
        assertTrue(f.x(-175.2) < f.x(-171.8));
        for (int i = 0; i < lons.length; i++) {
            assertTrue(f.x(lons[i]) >= 20 - 0.01 && f.x(lons[i]) <= 380 + 0.01);
            assertTrue(f.y(lats[i]) >= 20 - 0.01 && f.y(lats[i]) <= 280 + 0.01);
        }
        // one piece for a leg over the date line: it is the short way and does not cross the cut
        assertEquals(1, FictionMapMath.leg(f, -17.7, 178.0, -13.8, -171.8).size());
    }

    @Test
    public void everyPlaceIsInsideTheAreaAndNorthIsUp() {
        final FictionMapMath.Fit f = FictionMapMath.fit(LATS, LONS, 390, 260, 16);
        for (int i = 0; i < LONS.length; i++) {
            assertTrue(f.x(LONS[i]) >= 16 - 0.01 && f.x(LONS[i]) <= 374 + 0.01);
            assertTrue(f.y(LATS[i]) >= 16 - 0.01 && f.y(LATS[i]) <= 244 + 0.01);
        }
        assertTrue(f.y(51.5) < f.y(18.97));
        // east of the cut, left to right: San Francisco, New York, London, Suez … Yokohama
        assertTrue(f.x(-122.42) < f.x(-74.0));
        assertTrue(f.x(-74.0) < f.x(-0.13));
        assertTrue(f.x(-0.13) < f.x(32.55));
        assertTrue(f.x(114.2) < f.x(139.64));
    }

    @Test
    public void aLegOverTheCutIsDrawnTheShortWayInTwoPieces() {
        final FictionMapMath.Fit f = FictionMapMath.fit(LATS, LONS, 390, 260, 16);
        // London to Suez: one piece
        assertEquals(1, FictionMapMath.leg(f, 51.5, -0.13, 29.97, 32.55).size());
        // Yokohama to San Francisco: over the Pacific, off the right edge and in from the left
        final List<float[]> pieces = FictionMapMath.leg(f, 35.44, 139.64, 37.77, -122.42);
        assertEquals(2, pieces.size());
        final float[] out = pieces.get(0);
        assertEquals(f.x(139.64), out[0], 0.01);
        assertTrue(out[2] > out[0]);
        assertEquals(f.x(-122.42) + f.world(), out[2], 0.01);
        final float[] in = pieces.get(1);
        assertEquals(f.x(-122.42), in[2], 0.01);
        assertTrue(in[0] < in[2]);
        assertEquals(f.x(139.64) - f.world(), in[0], 0.01);
        // and the other way round
        final List<float[]> back = FictionMapMath.leg(f, 37.77, -122.42, 35.44, 139.64);
        assertEquals(2, back.size());
        assertTrue(back.get(0)[2] < back.get(0)[0]);
    }

    @Test
    public void onePlaceOrPlacesInALineStillFit() {
        final FictionMapMath.Fit one = FictionMapMath.fit(new double[]{48.85}, new double[]{2.35}, 300, 200, 10);
        assertTrue(one.x(2.35) > 10 && one.x(2.35) < 290);
        assertTrue(one.y(48.85) > 10 && one.y(48.85) < 190);
        final FictionMapMath.Fit none = FictionMapMath.fit(new double[0], new double[0], 300, 200, 10);
        assertTrue(none.world() > 0);
    }

    @Test
    public void gridLinesAreAFewForAnySpan() {
        assertEquals(1, FictionMapMath.gridStep(4), 0);
        assertEquals(5, FictionMapMath.gridStep(40), 0);
        assertEquals(45, FictionMapMath.gridStep(300), 0);
        assertEquals(90, FictionMapMath.gridStep(1000), 0);
    }

    @Test
    public void theSchematicRouteWindsRowByRow() {
        assertEquals(4, FictionMapMath.perRow(390));
        assertEquals(2, FictionMapMath.perRow(100));
        assertEquals(3, FictionMapMath.rows(10, 4));
        assertEquals(0, FictionMapMath.rows(0, 4));
        final float[][] p = FictionMapMath.schematic(10, 4, 400, 20, 60);
        assertEquals(10, p.length);
        // first row left to right
        assertEquals(20, p[0][0], 0.01);
        assertEquals(380, p[3][0], 0.01);
        assertEquals(20, p[0][1], 0.01);
        // second row right to left, under the end of the first
        assertEquals(380, p[4][0], 0.01);
        assertEquals(80, p[4][1], 0.01);
        assertEquals(20, p[7][0], 0.01);
        // third row left to right again
        assertEquals(20, p[8][0], 0.01);
        assertEquals(140, p[8][1], 0.01);
        assertEquals(0, FictionMapMath.schematic(0, 4, 400, 20, 60).length);
        assertEquals(200, FictionMapMath.schematic(1, 1, 400, 20, 60)[0][0], 0.01);
    }

    @Test
    public void aDashedLineEndsWhereTheLineEnds() {
        final float[] d = FictionMapMath.dashes(0, 0, 25, 0, 6, 4);
        // dashes at 0-6, 10-16, 20-25
        assertEquals(12, d.length);
        assertEquals(6, d[2], 0.01);
        assertEquals(10, d[4], 0.01);
        assertEquals(25, d[10], 0.01);
        assertEquals(0, FictionMapMath.dashes(3, 3, 3, 3, 6, 4).length);
    }
}
