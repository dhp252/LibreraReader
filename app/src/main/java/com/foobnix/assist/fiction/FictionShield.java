package com.foobnix.assist.fiction;

import com.foobnix.ai.BookChapters;

/**
 * The spoiler shield of the fiction companion: the ONE place that says which pages of the book a
 * request may read. Every request of the feature is built from a {@link Window}, and a window is
 * only made here ({@link #window}), so no request can hold text from after the reader's page.
 * <p>
 * The reader's page is 0-based, the first page the reader shows (the left one of two); the window
 * ends with that page, inclusive. When the text is laid out differently from the reader (another
 * page count), the same share of the book is taken, rounded down: less text, never more.
 */
public final class FictionShield {

    /** The key of a {@link #ticket}: the reader's page count follows the colon. */
    static final String TICKET = "@fiction:";

    private FictionShield() {
    }

    /** Pages {@code from} (inclusive) to {@code end} (exclusive) of the text: all a request may read. */
    public static final class Window {
        public final int from;
        public final int end;
        /** Page count of the layout the text is read in. */
        public final int pages;

        private Window(int from, int end, int pages) {
            this.from = from;
            this.end = end;
            this.pages = pages;
        }

        public boolean isEmpty() {
            return end <= from;
        }

        public boolean holds(int page) {
            return page >= from && page < end;
        }

        /** The share of the book (0..1) read at the end of the window. */
        public double share() {
            return pages <= 0 ? 0 : end / (double) pages;
        }
    }

    /**
     * The first page (0-based) no request may read: the page after the reader's, in the text's
     * layout. 0 when the reader's place is not known.
     *
     * @param readerPage  0-based page the reader shows
     * @param readerPages page count of the reader's layout
     * @param textPages   page count of the layout the text is read in
     */
    public static int end(int readerPage, int readerPages, int textPages) {
        if (readerPage < 0 || readerPages <= 0 || textPages <= 0) {
            return 0;
        }
        final int page = Math.min(readerPage, readerPages - 1);
        if (readerPages == textPages) {
            return page + 1;
        }
        // another layout: the same share of the book, rounded down
        final long end = (long) (page + 1) * textPages / readerPages;
        return (int) Math.max(0, Math.min(textPages, end));
    }

    /**
     * The pages a request may read: from {@code from} (the start of the book, or where stored data
     * stops) to the end of the reader's page. Empty when {@code from} is at or past that.
     */
    public static Window window(int from, int readerPage, int readerPages, int textPages) {
        final int end = end(readerPage, readerPages, textPages);
        final int start = Math.max(0, Math.min(from, end));
        return new Window(start, end, Math.max(0, textPages));
    }

    /** The same window from a later page on (the next part of a long text); never past its end. */
    public static Window rest(Window w, int from) {
        return new Window(Math.max(w.from, Math.min(from, w.end)), w.end, w.pages);
    }

    /**
     * The reader's place as a long task carries it (a task holds a page range): pages 0 to the
     * reader's page, inclusive, with the reader's page count in the key.
     */
    public static BookChapters.Range ticket(int readerPage, int readerPages) {
        return new BookChapters.Range(0, Math.max(0, readerPage + 1), "", TICKET + Math.max(0, readerPages));
    }

    /** The window a {@link #ticket} allows; empty when it is not one. */
    public static Window window(int from, BookChapters.Range ticket, int textPages) {
        if (ticket == null || ticket.key == null || !ticket.key.startsWith(TICKET) || ticket.from != 0) {
            return new Window(0, 0, Math.max(0, textPages));
        }
        int readerPages;
        try {
            readerPages = Integer.parseInt(ticket.key.substring(TICKET.length()));
        } catch (NumberFormatException e) {
            readerPages = 0;
        }
        return window(from, ticket.to - 1, readerPages, textPages);
    }

    /**
     * Where stored data stops, in the pages of the layout at hand: the stored page when the layout
     * is the one it was built in, else the same share of the book rounded down (an update then
     * sends a page again rather than leaving one out).
     *
     * @param storedEnd   first page not read when it was built
     * @param storedPages page count it was built in
     */
    public static int storedEnd(int storedEnd, int storedPages, double storedShare, int pages) {
        if (pages <= 0) {
            return 0;
        }
        if (storedPages == pages) {
            return Math.max(0, Math.min(pages, storedEnd));
        }
        final double share = storedPages > 0 ? storedEnd / (double) storedPages : storedShare;
        return (int) Math.max(0, Math.min(pages, Math.floor(share * pages)));
    }
}
