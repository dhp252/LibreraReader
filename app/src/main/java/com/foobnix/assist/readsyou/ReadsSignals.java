package com.foobnix.assist.readsyou;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * The signals kept for one chapter of a book (Assist.READS_YOU), in the book's assist file under
 * {@code reads:signals:<chapter key>}: for each page of the chapter that was read, small counters
 * and nothing else. Pages are counted from the chapter's first page (0) in the layout the book
 * had, with the chapter's page count beside them, so the counters can be moved to another layout
 * ({@link #fit}). This is everything the feature stores about a chapter; the "What is stored"
 * screen lists these same fields. No Android here.
 */
public final class ReadsSignals {

    /** Key prefix of a chapter's signals in a book's assist file. */
    public static final String PREFIX = "reads:signals:";

    public static String key(String chapterKey) {
        return PREFIX + (chapterKey == null ? "" : chapterKey);
    }

    /** The counters of one page. */
    public static final class Page {
        /** Seconds the page was on screen while being read ("s"). */
        public int seconds;
        /** Words on the page when it was last read, 0 when not known ("w"): what pace is judged by. */
        public int words;
        /** Times the reader paged back to it and forward again ("r"). */
        public int rereads;
        /** Times it took much longer than the reader's pace ("l"). */
        public int longs;
        /** Times "Say it another way?" was offered on it ("o"), and times the AI was asked ("h"). */
        public int offers;
        public int helps;
        /** Times the break card came up on it ("b"). */
        public int breaks;
        /** Drift marks ("d"). */
        public int drifts;
        /** Only with Eye mode's camera: seconds it watched the page ("e"), seconds the eyes were off it ("a"). */
        public int watched;
        public int away;
        /** Only with a calibrated gaze: times the gaze went from the bottom back to the top without a turn ("g"). */
        public int passes;

        public boolean isEmpty() {
            return seconds == 0 && rereads == 0 && longs == 0 && offers == 0 && helps == 0 && breaks == 0
                    && drifts == 0 && watched == 0 && away == 0 && passes == 0;
        }

        /** A spot to review: the reader struggled or drifted here, or asked for help. */
        public boolean isSpot() {
            return rereads > 0 || longs > 0 || helps > 0 || drifts > 0 || passes > 0;
        }

        public void add(Page other) {
            seconds += other.seconds;
            // the words of the pages that fall together in a coarser layout
            words += other.words;
            rereads += other.rereads;
            longs += other.longs;
            offers += other.offers;
            helps += other.helps;
            breaks += other.breaks;
            drifts += other.drifts;
            watched += other.watched;
            away += other.away;
            passes += other.passes;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            put(o, "s", seconds);
            put(o, "w", words);
            put(o, "r", rereads);
            put(o, "l", longs);
            put(o, "o", offers);
            put(o, "h", helps);
            put(o, "b", breaks);
            put(o, "d", drifts);
            put(o, "e", watched);
            put(o, "a", away);
            put(o, "g", passes);
            return o;
        }

        static Page fromJson(JSONObject o) {
            final Page p = new Page();
            p.seconds = count(o, "s");
            p.words = count(o, "w");
            p.rereads = count(o, "r");
            p.longs = count(o, "l");
            p.offers = count(o, "o");
            p.helps = count(o, "h");
            p.breaks = count(o, "b");
            p.drifts = count(o, "d");
            p.watched = count(o, "e");
            p.away = count(o, "a");
            p.passes = count(o, "g");
            return p;
        }

        private static void put(JSONObject o, String name, int value) throws JSONException {
            if (value > 0) {
                o.put(name, value);
            }
        }

        private static int count(JSONObject o, String name) {
            return Math.max(0, o.optInt(name));
        }
    }

    /** The chapter's title when the signals were last written: only so a list can name the chapter. */
    public String title = "";
    /** Pages of the chapter in the layout the page numbers count in; 0 before the first page. */
    public int pages;
    /** The read pages by their number in the chapter, from 0. */
    public final TreeMap<Integer, Page> byPage = new TreeMap<>();

    /** The counters of a page of the chapter, made when it has none yet. */
    public Page at(int page) {
        final int p = Math.max(0, pages > 0 ? Math.min(pages - 1, page) : page);
        Page out = byPage.get(p);
        if (out == null) {
            out = new Page();
            byPage.put(p, out);
        }
        return out;
    }

    /**
     * Adds what one stay on a page (or one event on it) measured.
     *
     * @param page the page's number in the chapter, from 0, in the layout of {@link #pages}
     */
    public void apply(int page, ReadsDelta d) {
        final Page p = at(page);
        p.seconds += Math.max(0, d.seconds);
        if (d.words > 0) {
            p.words = d.words;
        }
        p.rereads += Math.max(0, d.rereads);
        p.longs += Math.max(0, d.longs);
        p.offers += Math.max(0, d.offers);
        p.helps += Math.max(0, d.helps);
        p.breaks += Math.max(0, d.breaks);
        p.drifts += Math.max(0, d.drifts);
        p.watched += Math.max(0, d.watched);
        p.away += Math.max(0, d.away);
        p.passes += Math.max(0, d.passes);
    }

    /** The counters of a page, or null when it has none. */
    public Page peek(int page) {
        return byPage.get(page);
    }

    /**
     * Brings the counters to a layout in which the chapter has {@code chapterPages} pages: each
     * page goes to the page as far into the chapter as it was, and pages that fall together are
     * added up. Nothing changes when the count is the same.
     */
    public void fit(int chapterPages) {
        if (chapterPages <= 0 || chapterPages == pages) {
            return;
        }
        if (pages > 0 && !byPage.isEmpty()) {
            final TreeMap<Integer, Page> moved = new TreeMap<>();
            for (Map.Entry<Integer, Page> e : byPage.entrySet()) {
                final int to = Math.max(0, Math.min(chapterPages - 1,
                        (int) Math.floor((e.getKey() + 0.5) * chapterPages / pages)));
                final Page there = moved.get(to);
                if (there == null) {
                    moved.put(to, e.getValue());
                } else {
                    there.add(e.getValue());
                }
            }
            byPage.clear();
            byPage.putAll(moved);
        }
        pages = chapterPages;
    }

    public boolean isEmpty() {
        for (Page p : byPage.values()) {
            if (!p.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The chapter's counters added up. */
    public Page total() {
        final Page t = new Page();
        for (Page p : byPage.values()) {
            t.add(p);
        }
        return t;
    }

    /** Pages with a spot to review. */
    public int spots() {
        int n = 0;
        for (Page p : byPage.values()) {
            if (p.isSpot()) {
                n++;
            }
        }
        return n;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("pages", pages);
        final JSONObject p = new JSONObject();
        for (Map.Entry<Integer, Page> e : byPage.entrySet()) {
            if (!e.getValue().isEmpty()) {
                p.put(String.valueOf(e.getKey()), e.getValue().toJson());
            }
        }
        o.put("p", p);
        return o;
    }

    /** The stored signals; empty ones for null or a badly formed item. */
    public static ReadsSignals fromJson(JSONObject o) {
        final ReadsSignals s = new ReadsSignals();
        if (o == null) {
            return s;
        }
        s.title = o.optString("title", "");
        s.pages = Math.max(0, o.optInt("pages"));
        final JSONObject p = o.optJSONObject("p");
        if (p != null) {
            final Iterator<String> keys = p.keys();
            while (keys.hasNext()) {
                final String k = keys.next();
                final JSONObject page = p.optJSONObject(k);
                try {
                    final int n = Integer.parseInt(k);
                    if (page != null && n >= 0 && (s.pages <= 0 || n < s.pages)) {
                        final Page parsed = Page.fromJson(page);
                        if (!parsed.isEmpty()) {
                            s.byPage.put(n, parsed);
                        }
                    }
                } catch (NumberFormatException e) {
                    // not a page: left out
                }
            }
        }
        return s;
    }
}
