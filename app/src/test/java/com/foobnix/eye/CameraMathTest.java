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
    public void presenceRateUnlessTheGazeOrABlinkIsFollowed() {
        // focus, away, eyes shut: the low rate, seen or not
        assertEquals(EyeRules.PRESENCE_FPS, EyeRules.frameRate(false, true, false, false));
        assertEquals(EyeRules.PRESENCE_FPS, EyeRules.frameRate(false, false, false, false));
        // the gaze followed, but nobody seen: nothing to follow
        assertEquals(EyeRules.PRESENCE_FPS, EyeRules.frameRate(false, false, true, false));
        assertEquals(EyeRules.FULL_FPS, EyeRules.frameRate(false, true, true, false));
        // a closure timed for long blink, and the calibration, whatever else
        assertEquals(EyeRules.FULL_FPS, EyeRules.frameRate(false, true, false, true));
        assertEquals(EyeRules.FULL_FPS, EyeRules.frameRate(true, false, false, false));
    }

    @Test
    public void uprightSize() {
        assertArrayEquals(new int[]{640, 480}, CameraMath.upright(640, 480, 0));
        assertArrayEquals(new int[]{480, 640}, CameraMath.upright(640, 480, 270));
        assertArrayEquals(new int[]{640, 480}, CameraMath.upright(640, 480, 180));
    }

    @Test
    public void focalLengthInPixelsOfTheFrame() {
        // a 4:3 sensor 4.6 × 3.45 mm, a 3.5 mm lens, a 640 × 480 frame (either way round)
        assertEquals(3.5f / 4.6f * 640, CameraMath.focalPx(3.5f, 4.6f, 3.45f, 640, 480), 0.1);
        assertEquals(3.5f / 4.6f * 640, CameraMath.focalPx(3.5f, 4.6f, 3.45f, 480, 640), 0.1);
        // a 16:9 sensor cut to 4:3 keeps its short side
        assertEquals(3.5f / 3.6f * 480, CameraMath.focalPx(3.5f, 6.4f, 3.6f, 640, 480), 0.1);
        assertEquals(0, CameraMath.focalPx(0, 4.6f, 3.45f, 640, 480), 0);
    }
}
