package com.foobnix.annotation;

import android.graphics.Color;

import com.foobnix.model.AppState;
import com.foobnix.pdf.info.model.AnnotationType;

/** The tools of the annotation toolbar and their colors (kept in AppState, so they sync like other settings). */
public final class AnnotationTools {

    public static final int NONE = 0;
    public static final int HIGHLIGHT = 1;
    public static final int UNDERLINE = 2;
    public static final int STRIKEOUT = 3;
    public static final int PEN = 4;
    public static final int ERASER = 5;

    /** Zotero's annotation colors. */
    public static final String[] COLORS = {"#ffd400", "#ff6666", "#5fb236", "#2ea8e5", "#a28ae5", "#e56eee", "#f19837", "#aaaaaa", "#000000"};

    private AnnotationTools() {
    }

    public static boolean isMarkup(int tool) {
        return tool == HIGHLIGHT || tool == UNDERLINE || tool == STRIKEOUT;
    }

    public static boolean hasColor(int tool) {
        return isMarkup(tool) || tool == PEN;
    }

    public static AnnotationType markupType(int tool) {
        switch (tool) {
            case UNDERLINE:
                return AnnotationType.UNDERLINE;
            case STRIKEOUT:
                return AnnotationType.STRIKEOUT;
            default:
                return AnnotationType.HIGHLIGHT;
        }
    }

    public static String color(int tool) {
        final AppState s = AppState.get();
        switch (tool) {
            case HIGHLIGHT:
                return s.annotHighlightColor;
            case UNDERLINE:
                return s.annotUnderlineColor;
            case STRIKEOUT:
                return s.annotStrikeoutColor;
            case PEN:
                return s.annotPenColor;
            default:
                return COLORS[0];
        }
    }

    public static void setColor(int tool, String color) {
        final AppState s = AppState.get();
        switch (tool) {
            case HIGHLIGHT:
                s.annotHighlightColor = color;
                break;
            case UNDERLINE:
                s.annotUnderlineColor = color;
                break;
            case STRIKEOUT:
                s.annotStrikeoutColor = color;
                break;
            case PEN:
                s.annotPenColor = color;
                break;
            default:
        }
    }

    public static int colorInt(int tool) {
        try {
            return Color.parseColor(color(tool));
        } catch (Exception e) {
            return Color.parseColor(COLORS[0]);
        }
    }
}
