package com.foobnix.assist.edition;

import com.foobnix.assist.useit.UseDates;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a book's edition remembers, the item {@code edition:book}: the day it was first opened, the
 * day of the next refresher, whether the "You finished it" offer was made, and whether the reader
 * went back to their raw words. Days are local days; "due" is never stored, it follows from the
 * refresher day and today, so nothing is scheduled: the app looks when a book is opened.
 */
public final class EditionState {

    public static final String KEY = "edition:book";

    /** The day the edition was first opened, or empty. */
    public String opened = "";
    /** The day of the next refresher, or empty before the first open. */
    public String next = "";
    /** The day the reader first reached the book's last page with something in the edition, or empty. */
    public String finished = "";
    /** The "You finished it" card was shown. */
    public boolean offered;
    /** The refresher day whose card was shown: one card per refresher. */
    public String carded = "";
    /** The reader went back to their raw words: tidied passages are kept but not shown. */
    public boolean raw;

    public static EditionState fromJson(JSONObject o) {
        final EditionState s = new EditionState();
        if (o == null) {
            return s;
        }
        s.opened = day(o.optString("opened", ""));
        s.next = day(o.optString("next", ""));
        s.finished = day(o.optString("finished", ""));
        s.offered = o.optBoolean("offered");
        s.carded = day(o.optString("carded", ""));
        s.raw = o.optBoolean("raw");
        return s;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("opened", opened).put("next", next).put("finished", finished)
                .put("offered", offered).put("carded", carded).put("raw", raw);
    }

    private static String day(String s) {
        return UseDates.isDay(s) ? s : "";
    }

    /**
     * The edition is opened on {@code today}: the first open sets the first refresher six weeks
     * on; an open on or after the refresher day is that refresher, and sets the next one.
     *
     * @return whether anything changed (and has to be stored)
     */
    public boolean open(String today) {
        if (!UseDates.isDay(today)) {
            return false;
        }
        if (opened.isEmpty()) {
            opened = today;
            next = EditionDates.refresherAfter(today);
            return true;
        }
        if (next.isEmpty() || UseDates.reached(today, next)) {
            next = EditionDates.refresherAfter(today);
            return true;
        }
        return false;
    }

    /** The refresher's day has come and its card was not shown yet. */
    public boolean refresherDue(String today) {
        return !next.isEmpty() && UseDates.reached(today, next) && !next.equals(carded);
    }

    /** The refresher's card was shown. */
    public void refresherShown() {
        carded = next;
    }

    /**
     * The reader reached the last page with something in the edition.
     *
     * @return whether this was the first time
     */
    public boolean finish(String today) {
        if (!finished.isEmpty() || !UseDates.isDay(today)) {
            return false;
        }
        finished = today;
        return true;
    }

    /** The "You finished it" card is still owed: the book was finished and the card never shown. */
    public boolean offerOwed() {
        return !finished.isEmpty() && !offered;
    }
}
