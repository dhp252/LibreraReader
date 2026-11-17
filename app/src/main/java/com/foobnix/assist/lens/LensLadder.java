package com.foobnix.assist.lens;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The ladder of one idea (canvas LensLadder): four rungs from abstract to concrete, 4 the
 * principle, 3 the mechanism, 2 the author's example, 1 the reader's world. Made by one request
 * and kept inside its row of the chapter's bridge. No Android here.
 */
public final class LensLadder {

    public static final int RUNGS = 4;
    public static final int PRINCIPLE = 4;
    public static final int MECHANISM = 3;
    public static final int EXAMPLE = 2;
    public static final int WORLD = 1;

    public String principle = "";
    public String mechanism = "";
    public String example = "";
    public String world = "";

    /** The text of a rung, 4 (abstract) to 1 (concrete); empty for another number. */
    public String rung(int n) {
        switch (n) {
            case PRINCIPLE:
                return principle;
            case MECHANISM:
                return mechanism;
            case EXAMPLE:
                return example;
            case WORLD:
                return world;
            default:
                return "";
        }
    }

    private void set(int n, String text) {
        switch (n) {
            case PRINCIPLE:
                principle = text;
                break;
            case MECHANISM:
                mechanism = text;
                break;
            case EXAMPLE:
                example = text;
                break;
            case WORLD:
                world = text;
                break;
            default:
                break;
        }
    }

    /** Every rung has text. */
    public boolean isComplete() {
        return !principle.isEmpty() && !mechanism.isEmpty() && !example.isEmpty() && !world.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("principle", principle).put("mechanism", mechanism).put("example", example)
                .put("world", world);
    }

    /** A stored ladder, or null when the item holds none (a rung is missing). */
    public static LensLadder fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final LensLadder l = new LensLadder();
        l.principle = o.optString("principle", "").trim();
        l.mechanism = o.optString("mechanism", "").trim();
        l.example = o.optString("example", "").trim();
        l.world = o.optString("world", "").trim();
        return l.isComplete() ? l : null;
    }

    /**
     * The ladder of an answer: an object with the four rungs by name (a few spellings), or a list
     * "rungs" from abstract to concrete; fences and text around the JSON are skipped. Null when a
     * rung is missing.
     */
    public static LensLadder parse(String answer) {
        final JSONObject o = LensBridge.object(answer);
        if (o == null) {
            return null;
        }
        final LensLadder l = new LensLadder();
        l.principle = first(o, "principle", "rule", "4");
        l.mechanism = first(o, "mechanism", "how", "3");
        l.example = first(o, "example", "authors_example", "author_example", "author's example", "author", "2");
        l.world = first(o, "world", "your_world", "yours", "your world", "1");
        final JSONArray rungs = o.optJSONArray("rungs");
        if (!l.isComplete() && rungs != null && rungs.length() >= RUNGS) {
            for (int i = 0; i < RUNGS; i++) {
                final Object r = rungs.opt(i);
                final String text = r instanceof JSONObject ? first((JSONObject) r, "text", "sentence", "rung")
                        : LensBridge.clean(r == null ? "" : String.valueOf(r));
                l.set(RUNGS - i, text);
            }
        }
        return l.isComplete() ? l : null;
    }

    private static String first(JSONObject o, String... names) {
        for (String name : names) {
            final Object v = o.opt(name);
            if (v instanceof String) {
                final String s = LensBridge.clean((String) v);
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }
}
