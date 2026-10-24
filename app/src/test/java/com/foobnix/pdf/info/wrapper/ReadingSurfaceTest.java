package com.foobnix.pdf.info.wrapper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReadingSurfaceTest {

    @Test
    public void aPortraitTextureCoversATallPhoneCentredSideways() {
        // bg1.jpg on a 1080 x 2400 phone: the height decides, the sides run off evenly
        float[] m = ReadingSurface.coverMatrix(536, 716, 1080, 2400);
        assertEquals(2400f / 716, m[0], 1e-5);
        assertEquals(0f, m[2], 1e-3);
        assertEquals((1080 - 536 * m[0]) / 2, m[1], 1e-3);
        assertTrue(m[1] < 0);
    }

    @Test
    public void aLandscapeScreenIsCoveredCentredUpAndDown() {
        float[] m = ReadingSurface.coverMatrix(536, 716, 2560, 1600);
        assertEquals(2560f / 536, m[0], 1e-5);
        assertEquals(0f, m[1], 1e-3);
        assertEquals((1600 - 716 * m[0]) / 2, m[2], 1e-3);
    }

    @Test
    public void theShapeIsKeptAndNothingIsLeftBare() {
        int[][] cases = {{536, 716, 720, 1280}, {368, 545, 1600, 2560}, {100, 100, 300, 50}};
        for (int[] c : cases) {
            float[] m = ReadingSurface.coverMatrix(c[0], c[1], c[2], c[3]);
            assertTrue(c[0] * m[0] >= c[2] - 1e-3);
            assertTrue(c[1] * m[0] >= c[3] - 1e-3);
            assertTrue(m[1] <= 1e-3 && m[2] <= 1e-3);
        }
    }
}
