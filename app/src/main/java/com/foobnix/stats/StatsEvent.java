package com.foobnix.stats;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One line of the reading log ({@code profile.<name>/stats/<device>/YYYY-MM.jsonl}): what the
 * reader did and when. Fields a type does not use stay at their defaults and are not written.
 * Format: .agents/features/F-007-reading-stats/design-2026-10-15.md, "Log format".
 */
public class StatsEvent {

    /** First line of a file: the device's id and name. */
    public static final String DEV = "dev";
    /** A book was opened in a reader. */
    public static final String OPEN = "open";
    /** The reader came back on screen. */
    public static final String RESUME = "resume";
    /** The reader left the screen (another app, screen off, a dictionary app...). */
    public static final String PAUSE = "pause";
    /** The reader was closed. */
    public static final String CLOSE = "close";
    /** A page was left: how long it was on screen. */
    public static final String PAGE = "page";
    /** A move of more than one page (TOC, slider, search, link, another device). */
    public static final String JUMP = "jump";
    /** Read aloud started ({@link #on}) or stopped. */
    public static final String TTS = "tts";
    public static final String HIGHLIGHT = "hl";
    public static final String NOTE = "note";
    public static final String BOOKMARK = "bm";
    public static final String SEARCH = "search";
    public static final String LOOKUP = "lookup";

    /** {@link #mode} of a page: read by the reader, turned by read aloud, by auto-scroll. */
    public static final String MODE_READ = "read";
    public static final String MODE_TTS = "tts";
    public static final String MODE_AUTO = "auto";

    /** {@link #via} of a jump made to follow another device: not the reader going back. */
    public static final String VIA_SYNC = "sync";

    public long ts;
    /** Offset of the device's time zone from UTC at {@link #ts}, in minutes. */
    public int tz;
    public String type;
    /** The book's file name (the key the progress sync uses too). */
    public String book;

    public String id;
    public String name;

    public String title;
    public String author;
    public String lang;
    public String ext;
    public int pages;
    public float percent = -1;

    /** 1-based page. */
    public int page;
    /** Words on the page, -1 when not counted yet. */
    public int words = -1;
    /** Time on the page without dialogs, uncapped; the engine applies the cap. */
    public long raw;
    public String chapter;
    public String mode;

    public int from;
    public int to;
    public String via;

    public boolean on;

    public StatsEvent() {
    }

    public StatsEvent(String type, long ts, int tz, String book) {
        this.type = type;
        this.ts = ts;
        this.tz = tz;
        this.book = book;
    }

    public String toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("ts", ts);
            o.put("tz", tz);
            o.put("e", type);
            putIf(o, "b", book);
            putIf(o, "id", id);
            putIf(o, "name", name);
            putIf(o, "title", title);
            putIf(o, "author", author);
            putIf(o, "lang", lang);
            putIf(o, "ext", ext);
            if (pages > 0) {
                o.put("pgs", pages);
            }
            if (percent >= 0) {
                o.put("p", Math.round(percent * 10000) / 10000.0);
            }
            if (PAGE.equals(type)) {
                o.put("pg", page);
                o.put("w", words);
                o.put("raw", raw);
                putIf(o, "ch", chapter);
                putIf(o, "m", mode);
            } else if (page > 0) {
                o.put("pg", page);
            }
            if (JUMP.equals(type)) {
                o.put("from", from);
                o.put("to", to);
                putIf(o, "via", via);
            }
            if (TTS.equals(type)) {
                o.put("on", on);
            }
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null for a line that is not an event (damaged, empty, unknown type) */
    public static StatsEvent fromJson(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        try {
            final JSONObject o = new JSONObject(line);
            final StatsEvent e = new StatsEvent();
            e.type = o.optString("e", null);
            e.ts = o.optLong("ts", 0);
            if (e.type == null || e.ts <= 0) {
                return null;
            }
            e.tz = o.optInt("tz", 0);
            e.book = o.optString("b", null);
            e.id = o.optString("id", null);
            e.name = o.optString("name", null);
            e.title = o.optString("title", null);
            e.author = o.optString("author", null);
            e.lang = o.optString("lang", null);
            e.ext = o.optString("ext", null);
            e.pages = o.optInt("pgs", 0);
            e.percent = (float) o.optDouble("p", -1);
            e.page = o.optInt("pg", 0);
            e.words = o.optInt("w", -1);
            e.raw = o.optLong("raw", 0);
            e.chapter = o.optString("ch", null);
            e.mode = o.optString("m", MODE_READ);
            e.from = o.optInt("from", 0);
            e.to = o.optInt("to", 0);
            e.via = o.optString("via", null);
            e.on = o.optBoolean("on", false);
            return e;
        } catch (JSONException e) {
            return null;
        }
    }

    private static void putIf(JSONObject o, String key, String value) throws JSONException {
        if (value != null) {
            o.put(key, value);
        }
    }
}
