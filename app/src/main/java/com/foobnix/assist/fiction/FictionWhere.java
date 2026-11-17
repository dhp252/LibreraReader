package com.foobnix.assist.fiction;

/**
 * The reader's place against the place stored fiction data was built for. The data always shows
 * under its own "as of Ch. N"; this says what else the screen must do:
 * <ul>
 * <li>{@link #AT}: the reader is where it was built (or on an earlier page of the same chapter);</li>
 * <li>{@link #PAST}: the reader has read on, so the data is behind: say so and offer the update;</li>
 * <li>{@link #BEFORE}: the reader went back to an earlier chapter: everything of a later chapter
 * (people first met later, later moments and ties) is hidden ({@link #sees}).</li>
 * </ul>
 */
public final class FictionWhere {

    public static final int AT = 0;
    public static final int PAST = 1;
    public static final int BEFORE = 2;

    public final int state;
    /** Index of the reader's chapter; -1 when unknown. */
    public final int reader;
    /** Index of the chapter the data goes up to; -1 when unknown. */
    public final int stored;
    /** First page after the reader's, and first page the data did not read, in the same layout. */
    public final int readerEnd;
    public final int storedEnd;

    private FictionWhere(int state, int reader, int stored, int readerEnd, int storedEnd) {
        this.state = state;
        this.reader = reader;
        this.stored = stored;
        this.readerEnd = readerEnd;
        this.storedEnd = storedEnd;
    }

    /**
     * @param readerEnd the first page after the reader's ({@link FictionShield#end}), in the
     *                  layout of {@code chapters}
     */
    public static FictionWhere of(FictionChapters chapters, int readerEnd, FictionMark mark) {
        final int storedEnd = mark.endIn(chapters.pages());
        final int reader = chapters.indexAt(Math.max(0, readerEnd - 1));
        final int stored = mark.index(chapters);
        final int state;
        if (stored >= 0 && reader < stored) {
            state = BEFORE;
        } else if (readerEnd > storedEnd && (stored < 0 || reader > stored || chapters.pages() == mark.pages
                || readerEnd > storedEnd + 1)) {
            // in another layout a page of difference inside the same chapter is rounding, not reading on
            state = PAST;
        } else {
            state = AT;
        }
        return new FictionWhere(state, reader, stored, readerEnd, storedEnd);
    }

    /**
     * Whether something of the chapter at {@code index} may show: always, unless the reader went
     * back, then only what is in the reader's chapter or before it. An unknown chapter (-1) is
     * hidden then.
     */
    public boolean sees(int index) {
        return state != BEFORE || (index >= 0 && index <= reader);
    }
}
