package com.foobnix.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the ✦ menu's Key passages popup (design-2026-10-25-ai-menu.md): the ideas first,
 * then running, showing and removing them. While they run, one row shows the progress, and nothing
 * that writes the same AI file is offered.
 */
public final class AiMenuRows {

    public enum Row {
        IDEAS, HIGHLIGHT_CHAPTER, HIGHLIGHT_BOOK, PROGRESS, SHOW, GOAL, REMOVE_CHAPTER, REMOVE_BOOK, SAVE_INTO_PDF
    }

    private AiMenuRows() {
    }

    /**
     * @param busy        key passages of this book are queued or run
     * @param hasMarks    the book has AI highlights to remove or save
     * @param pdfDrawable a PDF the app may draw into
     */
    public static List<Row> keyPassages(boolean busy, boolean hasMarks, boolean pdfDrawable) {
        final List<Row> rows = new ArrayList<>();
        rows.add(Row.IDEAS);
        if (busy) {
            rows.add(Row.PROGRESS);
        } else {
            rows.add(Row.HIGHLIGHT_CHAPTER);
            rows.add(Row.HIGHLIGHT_BOOK);
        }
        rows.add(Row.SHOW);
        rows.add(Row.GOAL);
        if (!busy && hasMarks) {
            rows.add(Row.REMOVE_CHAPTER);
            rows.add(Row.REMOVE_BOOK);
            if (pdfDrawable) {
                rows.add(Row.SAVE_INTO_PDF);
            }
        }
        return rows;
    }
}
