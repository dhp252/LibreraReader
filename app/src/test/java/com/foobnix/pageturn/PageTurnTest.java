package com.foobnix.pageturn;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageTurnTest {

    @Test
    public void oldCheckboxDecidesUntilAStyleIsChosen() {
        assertEquals(PageTurn.STYLE_SLIDE, PageTurn.style(-1, true));
        assertEquals(PageTurn.STYLE_NONE, PageTurn.style(-1, false));
        assertEquals(PageTurn.STYLE_CURL, PageTurn.style(PageTurn.STYLE_CURL, false));
        assertEquals(PageTurn.STYLE_NONE, PageTurn.style(PageTurn.STYLE_NONE, true));
        // a value from a newer build falls back like "not chosen"
        assertEquals(PageTurn.STYLE_SLIDE, PageTurn.style(9, true));
        assertFalse(PageTurn.isAnimated(PageTurn.STYLE_NONE));
        assertTrue(PageTurn.isAnimated(PageTurn.STYLE_COVER));
    }

    @Test
    public void slideDurationStaysInACalmRange() {
        assertEquals(PageTurn.SLIDE_MIN_MS, PageTurn.slideDuration(100, true));
        assertEquals(250, PageTurn.slideDuration(250, true));
        assertEquals(PageTurn.SLIDE_MAX_MS, PageTurn.slideDuration(600, true));
        // taps and keys: always the same, whatever ViewPager asks
        assertEquals(PageTurn.SLIDE_TAP_MS, PageTurn.slideDuration(200, false));
        assertEquals(PageTurn.SLIDE_TAP_MS, PageTurn.slideDuration(600, false));
    }

    @Test
    public void coverKeepsTheLeftPageStillLeftToRight() {
        // going forward: shown page (0 -> -1) stays, next page (1 -> 0) moves normally
        assertEquals(0.4f, PageTurn.coverTranslation(-0.4f, false), 1e-6);
        assertEquals(0f, PageTurn.coverTranslation(0.6f, false), 1e-6);
        assertEquals(0f, PageTurn.coverTranslation(0f, false), 1e-6);
        assertEquals(0f, PageTurn.coverTranslation(-1f, false), 1e-6);
        assertTrue(PageTurn.coverIsMoving(0.6f, false));
        assertFalse(PageTurn.coverIsMoving(-0.4f, false));
        assertFalse(PageTurn.coverIsMoving(0f, false));
    }

    @Test
    public void coverIsMirroredRightToLeft() {
        assertEquals(-0.4f, PageTurn.coverTranslation(0.4f, true), 1e-6);
        assertEquals(0f, PageTurn.coverTranslation(-0.6f, true), 1e-6);
        assertTrue(PageTurn.coverIsMoving(-0.6f, true));
        assertFalse(PageTurn.coverIsMoving(0.4f, true));
    }

    @Test
    public void textureSideIsTheNearestPowerOfTwoUpTo2048() {
        assertEquals(1024, PageTurn.textureSide(1080));
        assertEquals(2048, PageTurn.textureSide(2400));
        assertEquals(2048, PageTurn.textureSide(1600));
        assertEquals(1024, PageTurn.textureSide(1400));
        assertEquals(512, PageTurn.textureSide(512));
        assertEquals(2048, PageTurn.textureSide(5000));
        assertEquals(1, PageTurn.textureSide(0));
    }

    @Test
    public void releaseCompletesOnDistanceOrFlick() {
        assertTrue(PageTurn.completes(0.5f, 0f));
        assertFalse(PageTurn.completes(0.1f, 0f));
        assertTrue(PageTurn.completes(0.1f, 2f));
        assertFalse(PageTurn.completes(0.8f, -2f));
        // a slow drag is judged by the distance
        assertFalse(PageTurn.completes(0.2f, 0.5f));
    }
}
