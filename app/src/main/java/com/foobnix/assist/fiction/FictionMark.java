package com.foobnix.assist.fiction;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * How far into the book stored fiction data goes: what "as of Ch. 14" stands on. Kept with the
 * data as the first page not read ({@code end}) in the page count it was built in, the same as a
 * share of the book (for another layout), and the chapter holding the last page read (its number
 * in the chapter list then, and its key).
 */
public final class FictionMark {

    /** First page (0-based) not read when the data was built. */
    public int end;
    /** Page count of the layout it was built in. */
    public int pages;
    /** {@code end / pages}. */
    public double share;
    /** Number (from 1) of the chapter holding the last page read. */
    public int ch;
    /** Key of that chapter. */
    public String key = "";
    /** How many chapters the book had. */
    public int chapters;

    public FictionMark() {
    }

    /** The mark of data built from pages before {@code end}. */
    public static FictionMark at(FictionChapters chapters, int end) {
        final FictionMark m = new FictionMark();
        m.end = Math.max(0, end);
        m.pages = chapters.pages();
        m.share = m.pages <= 0 ? 0 : m.end / (double) m.pages;
        final int index = chapters.indexAt(Math.max(0, m.end - 1));
        m.ch = index < 0 ? 0 : FictionChapters.n(index);
        m.key = index < 0 ? "" : chapters.get(index).key;
        m.chapters = chapters.size();
        return m;
    }

    public boolean isEmpty() {
        return end <= 0;
    }

    /** Where the data stops in a layout of {@code pageCount} pages (see {@link FictionShield#storedEnd}). */
    public int endIn(int pageCount) {
        return FictionShield.storedEnd(end, pages, share, pageCount);
    }

    /** The index of the chapter the data goes up to, in the chapters at hand; -1 when unknown. */
    public int index(FictionChapters chapters) {
        return chapters.indexOf(key, ch, this.chapters);
    }

    void write(JSONObject into) throws JSONException {
        into.put("end", end).put("pages", pages).put("share", share).put("ch", ch).put("key", key)
                .put("chapters", chapters);
    }

    static FictionMark read(JSONObject o) {
        final FictionMark m = new FictionMark();
        if (o != null) {
            m.end = Math.max(0, o.optInt("end"));
            m.pages = Math.max(0, o.optInt("pages"));
            m.share = Math.max(0, Math.min(1, o.optDouble("share", 0)));
            m.ch = Math.max(0, o.optInt("ch"));
            m.key = o.optString("key", "");
            m.chapters = Math.max(0, o.optInt("chapters"));
        }
        return m;
    }
}
