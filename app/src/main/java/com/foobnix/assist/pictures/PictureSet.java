package com.foobnix.assist.pictures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The pictures of one chapter (or of one selected passage): at most one of each kind, in the
 * order process, table, loop. Made from the model's one answer by {@link #parse}, which keeps
 * only pictures whose words are in the text sent; stored as the assist item
 * {@code picture:<chapter key>} of the book. Plain Java, no Android.
 */
public final class PictureSet {

    public static final String PREFIX = "picture:";

    /** The chapter's title as the book has it. */
    public String title = "";
    public final List<Picture> pictures = new ArrayList<>();
    /** Drawn from the chapter's stored ideas and their marks, not from its text. */
    public boolean fromIdeas;
    /** When the model was asked. */
    public long at;
    public String model = "";
    /** Pictures of the answer left out: their words are not in the text sent. */
    public int leftOut;
    /** Lines, cells and quantities left out: they showed a number the text sent does not have. */
    public int numbersOut;

    /** The key of a chapter's item in the book's assist file. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    public boolean isEmpty() {
        return pictures.isEmpty();
    }

    /** The picture of a kind, or null. */
    public Picture of(Picture.Kind kind) {
        for (Picture p : pictures) {
            if (p.kind == kind) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the answer

    /**
     * The pictures of a model's answer that the text sent carries. The answer may list them
     * ({@code {"pictures": [{"kind": "process", ...}]}}, or the bare list) or name them by kind
     * ({@code {"process": {...}, "table": {...}}}), with text or code fences around the JSON. A
     * picture that lacks what its kind needs, or whose quote is not among the book's words sent, is
     * left out; so is every number the text sent does not have. Null when no JSON can be read; an
     * empty set when the model drew nothing that holds.
     */
    public static PictureSet parse(String answer, PictureSource source) {
        final JSONObject o = PictureText.object(answer);
        if (o == null) {
            return null;
        }
        final PictureSet set = new PictureSet();
        final Picture.Left left = new Picture.Left();
        final List<Picture> read = new ArrayList<>();
        final JSONArray list = PictureText.list(o, "pictures", "diagrams");
        for (int i = 0; list != null && i < list.length(); i++) {
            add(read, Picture.read(list.optJSONObject(i), null), list.optJSONObject(i) != null, left);
        }
        for (Picture.Kind kind : Picture.Kind.values()) {
            final JSONObject named = o.optJSONObject(kind.code);
            if (named != null) {
                add(read, Picture.read(named, kind), true, left);
            }
        }
        for (Picture.Kind kind : Picture.Kind.values()) {
            for (Picture p : read) {
                // the first of each kind that holds
                if (p.kind == kind && set.of(kind) == null && p.check(source, left)) {
                    set.pictures.add(p);
                }
            }
        }
        set.leftOut = left.pictures;
        set.numbersOut = left.numbers;
        set.fromIdeas = source.fromIdeas;
        return set;
    }

    private static void add(List<Picture> read, Picture p, boolean given, Picture.Left left) {
        if (p != null) {
            read.add(p);
        } else if (given) {
            // something was drawn, but not a picture that can be shown
            left.pictures++;
        }
    }

    // ------------------------------------------------------------------ the store

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        final JSONArray list = new JSONArray();
        for (Picture p : pictures) {
            list.put(p.toJson());
        }
        o.put("pictures", list);
        o.put("from_ideas", fromIdeas);
        o.put("at", at);
        o.put("model", model);
        o.put("left_out", leftOut);
        o.put("numbers_out", numbersOut);
        return o;
    }

    /** A stored set (checked when it was made); null for no item. */
    public static PictureSet fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final PictureSet set = new PictureSet();
        set.title = o.optString("title", "");
        final JSONArray list = o.optJSONArray("pictures");
        for (int i = 0; list != null && i < list.length(); i++) {
            final Picture p = Picture.read(list.optJSONObject(i), null);
            if (p != null && !p.quote.isEmpty() && set.of(p.kind) == null) {
                set.pictures.add(p);
            }
        }
        set.fromIdeas = o.optBoolean("from_ideas", false);
        set.at = o.optLong("at", 0);
        set.model = o.optString("model", "");
        set.leftOut = o.optInt("left_out", 0);
        set.numbersOut = o.optInt("numbers_out", 0);
        return set;
    }
}
