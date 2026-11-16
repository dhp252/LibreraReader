package com.foobnix.canvas.reader;

/**
 * The words the reader's bars show in the canvas look, worked out without Android so they can be
 * tested on the JVM: the "38%" beside the seek bar and the chapter line under the book's name.
 */
public final class ReaderText {

    private ReaderText() {
    }

    /**
     * How far into the book the page is, as the canvas's strip writes it: "38%". Whole percent,
     * rounded down, so 100% is only the last page. Empty when the page count is not known.
     *
     * @param page the page, counted from 1
     * @param pages the number of pages
     */
    public static String percent(int page, int pages) {
        if (pages <= 0) {
            return "";
        }
        final int p = Math.max(0, Math.min(page, pages));
        return (int) ((long) p * 100 / pages) + "%";
    }

    /** One line of chapter name: spaces and line breaks run together, nothing for none. */
    public static String chapterLine(String chapter) {
        if (chapter == null) {
            return "";
        }
        return chapter.replaceAll("\\s+", " ").trim();
    }
}
