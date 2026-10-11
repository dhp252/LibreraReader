package com.foobnix.annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * The decisions behind drawing with a stylus, kept free of Android so they can be tested: which
 * touches are a stylus, which tool it draws with, when a finger is a palm to ignore, and how hard
 * the tip presses. Design: .agents/planning/stylus-annotation-design.md.
 */
public final class StylusRules {

    /** MotionEvent.TOOL_TYPE_* values (the same numbers, so this class needs no Android). */
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_STYLUS = 2;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int TOOL_TYPE_ERASER = 4;

    /** AppState.stylusTool: what a stylus draws with when no tool is picked in the toolbar. */
    public static final int SETTING_OFF = 0;
    public static final int SETTING_PEN = 1;
    public static final int SETTING_HIGHLIGHTER = 2;

    /** What a stylus stroke does. */
    public static final int DRAW_NONE = 0;
    public static final int DRAW_PEN = 1;
    public static final int DRAW_HIGHLIGHTER = 2;
    public static final int DRAW_ERASER = 3;

    /** A finger this soon after the stylus was last near the screen is a palm. */
    public static final long PALM_MS = 300;

    /** Pen width steps by pressure, and how much of the pen's width each draws. */
    public static final int STEP_THIN = 0;
    public static final int STEP_NORMAL = 1;
    public static final int STEP_BOLD = 2;
    private static final float[] STEP_WIDTH = {0.6f, 1f, 1.6f};

    private StylusRules() {
    }

    public static boolean isStylus(int toolType) {
        return toolType == TOOL_TYPE_STYLUS || toolType == TOOL_TYPE_ERASER;
    }

    /**
     * What a stylus stroke does. The eraser end always erases; a drawing tool picked in the
     * toolbar wins over the setting; the side button swaps pen and highlighter.
     *
     * @param toolbarTool the toolbar's tool while it is shown, AnnotationTools.NONE otherwise
     */
    public static int draw(int toolType, int toolbarTool, int setting, boolean sideButton) {
        if (toolType == TOOL_TYPE_ERASER) {
            return DRAW_ERASER;
        }
        final int draw;
        if (toolbarTool == AnnotationTools.PEN) {
            draw = DRAW_PEN;
        } else if (toolbarTool == AnnotationTools.HIGHLIGHT) {
            draw = DRAW_HIGHLIGHTER;
        } else if (toolbarTool == AnnotationTools.ERASER) {
            return DRAW_ERASER;
        } else if (setting == SETTING_PEN) {
            draw = DRAW_PEN;
        } else if (setting == SETTING_HIGHLIGHTER) {
            draw = DRAW_HIGHLIGHTER;
        } else {
            return DRAW_NONE;
        }
        if (!sideButton) {
            return draw;
        }
        return draw == DRAW_PEN ? DRAW_HIGHLIGHTER : DRAW_PEN;
    }

    /**
     * Whether a finger touch is a palm: the stylus is touching or hovering, or was a moment ago.
     *
     * @param stylusLastSeen when the stylus was last touching or hovering (ms), 0 if never
     */
    public static boolean isPalm(boolean stylusNear, long stylusLastSeen, long now) {
        return stylusNear || (stylusLastSeen > 0 && now - stylusLastSeen < PALM_MS);
    }

    /**
     * The width step of a point. Pressure runs 0..1 on most pens (some go above 1); the bounds
     * leave a wide middle, so a steady hand keeps one width.
     */
    public static int step(float pressure) {
        if (pressure < 0.3f) {
            return STEP_THIN;
        }
        if (pressure > 0.75f) {
            return STEP_BOLD;
        }
        return STEP_NORMAL;
    }

    public static float stepWidth(int step) {
        return STEP_WIDTH[Math.max(0, Math.min(STEP_WIDTH.length - 1, step))];
    }

    /**
     * Steps of a stroke with the flicker taken out: a step lasting fewer than {@code minRun}
     * points takes the step before it, so a stroke changes width only where the pressure really
     * changed.
     */
    public static int[] steady(int[] steps, int minRun) {
        final int[] out = steps.clone();
        int start = 0;
        while (start < out.length) {
            int end = start;
            while (end + 1 < out.length && out[end + 1] == out[start]) {
                end++;
            }
            if (start > 0 && end - start + 1 < minRun) {
                for (int i = start; i <= end; i++) {
                    out[i] = out[start - 1];
                }
                // the run joined the one before: look at the merged run again
                while (start > 0 && out[start - 1] == out[start]) {
                    start--;
                }
                continue;
            }
            start = end + 1;
        }
        return out;
    }

    /**
     * The stroke cut into runs of one step, as {start, end} point indexes. Each run ends on the
     * first point of the next, so the pieces meet with no gap.
     */
    public static List<int[]> runs(int[] steps) {
        final List<int[]> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= steps.length; i++) {
            if (i == steps.length || steps[i] != steps[start]) {
                runs.add(new int[]{start, Math.min(i, steps.length - 1)});
                start = i;
            }
        }
        return runs;
    }
}
