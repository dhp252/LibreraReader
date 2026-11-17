package com.foobnix.assist.edition;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A note of "The book, updated" the reader chose for their edition ("Add to my edition" on the
 * note's sheet): the item {@code edition:note:<chapter key>:<n>} with the note's title, body and
 * how settled it is. A copy, so the edition keeps it when the chapter's notes are asked for again.
 */
public final class EditionNote {

    public static final String PREFIX = "edition:note:";

    public String title = "";
    public String body = "";
    /** How settled, as "The book, updated" stores it: "well established", "debated", "speculative" or empty. */
    public String settled = "";
    /** A few words after the rating; may be empty. */
    public String settledNote = "";
    public String chapterTitle = "";
    /** When it was added, ms. */
    public long date;

    public EditionNote() {
    }

    public EditionNote(String title, String body, String settled, String settledNote, String chapterTitle, long date) {
        this.title = clean(title);
        this.body = clean(body);
        this.settled = clean(settled);
        this.settledNote = clean(settledNote);
        this.chapterTitle = chapterTitle == null ? "" : chapterTitle.trim();
        this.date = date;
    }

    public static String prefix(String chapterKey) {
        return PREFIX + chapterKey + ":";
    }

    public static String key(String chapterKey, int n) {
        return prefix(chapterKey) + n;
    }

    /** The chapter key of an item key ({@code edition:note:<chapter key>:<n>}), or null when it is not one. */
    public static String chapterOf(String itemKey) {
        if (itemKey == null || !itemKey.startsWith(PREFIX)) {
            return null;
        }
        final int at = itemKey.lastIndexOf(':');
        return at < PREFIX.length() ? null : itemKey.substring(PREFIX.length(), at);
    }

    /** The number of an item key, or -1. */
    public static int numberOf(String itemKey) {
        final int at = itemKey == null ? -1 : itemKey.lastIndexOf(':');
        try {
            return at < 0 ? -1 : Integer.parseInt(itemKey.substring(at + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The same note (title and body), whatever its number. */
    public boolean same(String otherTitle, String otherBody) {
        return title.equals(clean(otherTitle)) && body.equals(clean(otherBody));
    }

    /**
     * The key a note is stored under: the one that already holds it, else {@code edition:note:
     * <chapter key>:<n>} with the note's place {@code n} in the chapter, or the next free number
     * when another note sits there (the chapter's notes were asked for again).
     *
     * @param taken the item keys of the chapter's edition notes with their notes
     */
    public static String keyFor(String chapterKey, int n, String title, String body,
                                java.util.Map<String, EditionNote> taken) {
        for (java.util.Map.Entry<String, EditionNote> e : taken.entrySet()) {
            if (e.getValue() != null && e.getValue().same(title, body)) {
                return e.getKey();
            }
        }
        int free = Math.max(0, n);
        while (taken.containsKey(key(chapterKey, free))) {
            free++;
        }
        return key(chapterKey, free);
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("title", title).put("body", body).put("settled", settled)
                .put("settled_note", settledNote).put("chapterTitle", chapterTitle).put("date", date);
    }

    /** A stored note; null when it has neither a title nor a body. */
    public static EditionNote fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final EditionNote n = new EditionNote(o.optString("title", ""), o.optString("body", ""),
                o.optString("settled", ""), o.optString("settled_note", ""), o.optString("chapterTitle", ""),
                o.optLong("date"));
        return n.title.isEmpty() && n.body.isEmpty() ? null : n;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
