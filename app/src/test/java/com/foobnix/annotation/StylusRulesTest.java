package com.foobnix.annotation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StylusRulesTest {

    private static final int STYLUS = StylusRules.TOOL_TYPE_STYLUS;

    @Test
    public void onlyTheStylusAndItsEraserDraw() {
        assertTrue(StylusRules.isStylus(StylusRules.TOOL_TYPE_STYLUS));
        assertTrue(StylusRules.isStylus(StylusRules.TOOL_TYPE_ERASER));
        assertFalse(StylusRules.isStylus(StylusRules.TOOL_TYPE_FINGER));
        assertFalse(StylusRules.isStylus(StylusRules.TOOL_TYPE_MOUSE));
    }

    @Test
    public void theSettingDecidesWhenNoToolIsPicked() {
        assertEquals(StylusRules.DRAW_PEN, StylusRules.draw(STYLUS, AnnotationTools.NONE, StylusRules.SETTING_PEN, false));
        assertEquals(StylusRules.DRAW_HIGHLIGHTER, StylusRules.draw(STYLUS, AnnotationTools.NONE, StylusRules.SETTING_HIGHLIGHTER, false));
        assertEquals(StylusRules.DRAW_NONE, StylusRules.draw(STYLUS, AnnotationTools.NONE, StylusRules.SETTING_OFF, false));
        // underline and strikethrough work through a finger's text selection
        assertEquals(StylusRules.DRAW_PEN, StylusRules.draw(STYLUS, AnnotationTools.UNDERLINE, StylusRules.SETTING_PEN, false));
    }

    @Test
    public void aPickedToolWinsOverTheSetting() {
        assertEquals(StylusRules.DRAW_PEN, StylusRules.draw(STYLUS, AnnotationTools.PEN, StylusRules.SETTING_HIGHLIGHTER, false));
        assertEquals(StylusRules.DRAW_HIGHLIGHTER, StylusRules.draw(STYLUS, AnnotationTools.HIGHLIGHT, StylusRules.SETTING_OFF, false));
        assertEquals(StylusRules.DRAW_ERASER, StylusRules.draw(STYLUS, AnnotationTools.ERASER, StylusRules.SETTING_PEN, false));
    }

    @Test
    public void theSideButtonSwapsPenAndHighlighter() {
        assertEquals(StylusRules.DRAW_HIGHLIGHTER, StylusRules.draw(STYLUS, AnnotationTools.NONE, StylusRules.SETTING_PEN, true));
        assertEquals(StylusRules.DRAW_PEN, StylusRules.draw(STYLUS, AnnotationTools.HIGHLIGHT, StylusRules.SETTING_PEN, true));
        assertEquals(StylusRules.DRAW_NONE, StylusRules.draw(STYLUS, AnnotationTools.NONE, StylusRules.SETTING_OFF, true));
    }

    @Test
    public void theEraserEndAlwaysErases() {
        for (int setting : new int[]{StylusRules.SETTING_OFF, StylusRules.SETTING_PEN, StylusRules.SETTING_HIGHLIGHTER}) {
            assertEquals(StylusRules.DRAW_ERASER,
                    StylusRules.draw(StylusRules.TOOL_TYPE_ERASER, AnnotationTools.PEN, setting, true));
        }
    }

    @Test
    public void aFingerIsAPalmWhileTheStylusIsNearAndAMomentAfter() {
        assertTrue(StylusRules.isPalm(true, 0, 1000));
        assertTrue(StylusRules.isPalm(false, 1000, 1000 + StylusRules.PALM_MS - 1));
        assertFalse(StylusRules.isPalm(false, 1000, 1000 + StylusRules.PALM_MS));
        assertFalse(StylusRules.isPalm(false, 0, 5000));
    }

    @Test
    public void pressureGivesThreeWidths() {
        assertEquals(StylusRules.STEP_THIN, StylusRules.step(0.1f));
        assertEquals(StylusRules.STEP_NORMAL, StylusRules.step(0.5f));
        assertEquals(StylusRules.STEP_BOLD, StylusRules.step(0.9f));
        assertEquals(StylusRules.STEP_BOLD, StylusRules.step(1.4f));
        assertTrue(StylusRules.stepWidth(StylusRules.STEP_THIN) < StylusRules.stepWidth(StylusRules.STEP_NORMAL));
        assertTrue(StylusRules.stepWidth(StylusRules.STEP_NORMAL) < StylusRules.stepWidth(StylusRules.STEP_BOLD));
    }

    @Test
    public void aShortFlickerKeepsTheWidthBeforeIt() {
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 2, 2, 2, 2},
                StylusRules.steady(new int[]{1, 1, 1, 0, 1, 1, 2, 2, 2, 2}, 3));
        // a flicker that matches the run after it joins both into one
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1},
                StylusRules.steady(new int[]{1, 1, 1, 2, 1, 1}, 3));
        // the first run stays, however short
        assertArrayEquals(new int[]{0, 1, 1, 1}, StylusRules.steady(new int[]{0, 1, 1, 1}, 3));
    }

    @Test
    public void runsMeetWithoutAGap() {
        final List<int[]> runs = StylusRules.runs(new int[]{1, 1, 1, 2, 2, 0});
        assertEquals(3, runs.size());
        assertArrayEquals(new int[]{0, 3}, runs.get(0));
        assertArrayEquals(new int[]{3, 5}, runs.get(1));
        assertArrayEquals(new int[]{5, 5}, runs.get(2));
        assertEquals(1, StylusRules.runs(new int[]{1, 1, 1}).size());
    }
}
