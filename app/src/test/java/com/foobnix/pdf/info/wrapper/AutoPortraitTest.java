package com.foobnix.pdf.info.wrapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoPortraitTest {

    // Surface.ROTATION_*
    private static final int R0 = 0, R90 = 1, R180 = 2, R270 = 3;

    @Test
    public void uprightScreenFlipsWhenTheDeviceIsTurnedOver() {
        assertTrue(AutoPortrait.isUpsideDown(180, R0));
        assertTrue(AutoPortrait.isUpsideDown(150, R0));
        assertTrue(AutoPortrait.isUpsideDown(215, R0));
    }

    @Test
    public void uprightScreenStaysWhenHeldUprightOrSideways() {
        assertFalse(AutoPortrait.isUpsideDown(0, R0));
        assertFalse(AutoPortrait.isUpsideDown(355, R0));
        assertFalse(AutoPortrait.isUpsideDown(90, R0));
        assertFalse(AutoPortrait.isUpsideDown(270, R0));
        assertFalse(AutoPortrait.isUpsideDown(130, R0));
    }

    @Test
    public void flippedScreenFlipsBackWhenHeldUpright() {
        assertTrue(AutoPortrait.isUpsideDown(0, R180));
        assertTrue(AutoPortrait.isUpsideDown(350, R180));
        assertTrue(AutoPortrait.isUpsideDown(25, R180));
        assertFalse(AutoPortrait.isUpsideDown(180, R180));
    }

    @Test
    public void landscapeNaturalTabletsWork() {
        // portrait on such a tablet is rotation 90 or 270; upright there is 270° or 90° of device turn
        assertTrue(AutoPortrait.isUpsideDown(90, R90));
        assertFalse(AutoPortrait.isUpsideDown(270, R90));
        assertTrue(AutoPortrait.isUpsideDown(270, R270));
        assertFalse(AutoPortrait.isUpsideDown(90, R270));
    }

    @Test
    public void flatDeviceNeverFlips() {
        assertFalse(AutoPortrait.isUpsideDown(-1, R0));
        assertFalse(AutoPortrait.isUpsideDown(-1, R180));
    }
}
