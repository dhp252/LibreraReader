package com.foobnix.assist.readsyou;

/**
 * What is to be added to one page's stored counters ({@link ReadsSignals.Page}): a finished stay
 * on the page, or one event on it (help offered, help taken, the break card, a drift mark).
 * No Android here.
 */
public final class ReadsDelta {

    /** The book's own page, 0-based, in the layout the reader had. */
    public final int page;
    public int seconds;
    /** Words on the page, 0 when not known. */
    public int words;
    public int rereads;
    public int longs;
    public int offers;
    public int helps;
    public int breaks;
    public int drifts;
    /** With Eye mode's camera: seconds it watched the page, seconds the eyes were off it, second passes. */
    public int watched;
    public int away;
    public int passes;

    public ReadsDelta(int page) {
        this.page = page;
    }

    /** A finished stay as the model measured it. */
    public static ReadsDelta of(ReadsModel.Visit v) {
        final ReadsDelta d = new ReadsDelta(v.tag);
        d.seconds = (int) Math.round(v.ms / 1000.0);
        d.words = Math.max(0, v.words);
        d.rereads = v.reread ? 1 : 0;
        d.longs = v.dwell ? 1 : 0;
        return d;
    }

    public boolean isEmpty() {
        return seconds == 0 && rereads == 0 && longs == 0 && offers == 0 && helps == 0 && breaks == 0 && drifts == 0
                && watched == 0 && away == 0 && passes == 0;
    }
}
