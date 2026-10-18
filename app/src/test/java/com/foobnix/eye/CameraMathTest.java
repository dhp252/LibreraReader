package com.foobnix.eye;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraMathTest {

    @Test
    public void lowestFloorThatReachesTheRate() {
        // [15,15] [7,30] [15,30] [30,30]
        assertEquals(1, CameraMath.pickFpsRange(new int[]{15, 7, 15, 30}, new int[]{15, 30, 30, 30}, 15));
    }

    @Test
    public void sameFloorTakesTheLowerCeiling() {
        // [5,30] [5,15]
        assertEquals(1, CameraMath.pickFpsRange(new int[]{5, 5}, new int[]{30, 15}, 5));
    }

    @Test
    public void noneReachesTheRate() {
        assertEquals(-1, CameraMath.pickFpsRange(new int[]{5, 7}, new int[]{10, 12}, 15));
        assertEquals(-1, CameraMath.pickFpsRange(new int[0], new int[0], 5));
    }

    @Test
    public void uprightSize() {
        assertArrayEquals(new int[]{640, 480}, CameraMath.upright(640, 480, 0));
        assertArrayEquals(new int[]{480, 640}, CameraMath.upright(640, 480, 270));
        assertArrayEquals(new int[]{640, 480}, CameraMath.upright(640, 480, 180));
    }
}
