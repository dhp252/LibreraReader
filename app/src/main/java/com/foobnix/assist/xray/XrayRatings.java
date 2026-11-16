package com.foobnix.assist.xray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How well each idea of a chapter is supported, from one AI request per chapter
 * ({@link XrayPrompts}), and the weakest supported idea with a short explanation. Stored in
 * {@code AssistStore} as {@code xray:<chapter key>} ({@link #toJson()}).
 */
public final class XrayRatings {

    /** A rating, with how many of the 3 bars it fills. */
    public enum Rating {
        STRONG("strong", 3), FAIR("fair", 2), OBSERVATION("observation", 2), QUESTIONED("questioned", 1),
        WEAK("weak", 1);

        public final String code;
        public final int bars;

        Rating(String code, int bars) {
            this.code = code;
            this.bars = bars;
        }

        /** A rating as the model or the store writes it (a few spellings), or null. */
        public static Rating of(String s) {
            if (s == null) {
                return null;
            }
            final String c = s.trim().toLowerCase(Locale.US).replace('_', ' ').replace('-', ' ');
            if (c.isEmpty()) {
                return null;
            }
            for (Rating r : values()) {
                if (r.code.equals(c)) {
                    return r;
                }
            }
            if (c.contains("question") || c.contains("doubt")) {
                return QUESTIONED;
            }
            if (c.contains("observ")) {
                return OBSERVATION;
            }
            if (c.contains("weak")) {
                return WEAK;
            }
            if (c.contains("strong")) {
                return STRONG;
            }
            if (c.contains("fair") || c.contains("moderate")) {
                return FAIR;
            }
            return null;
        }

        /** Lower ranks are weaker: weak, questioned, observation, fair, strong. */
        int rank() {
            switch (this) {
                case WEAK:
                    return 0;
                case QUESTIONED:
                    return 1;
                case OBSERVATION:
                    return 2;
                case FAIR:
                    return 3;
                default:
                    return 4;
            }
        }

        public boolean weakerThan(Rating other) {
            return other == null || rank() < other.rank();
        }
    }

    /** The rating of one idea. */
    public static final class Entry {
        public final int id;
        public final Rating rating;
        /** 1 to 3 words naming the idea ("Practice"), or empty. */
        public final String label;
        /** 1 to 3 words on its support ("numbers", "1 story"), or empty. */
        public final String why;

        public Entry(int id, Rating rating, String label, String why) {
            this.id = id;
            this.rating = rating;
            this.label = label == null ? "" : label.trim();
            this.why = why == null ? "" : why.trim();
        }
    }

    public final Map<Integer, Entry> entries = new LinkedHashMap<>();
    /** The weakest supported idea, or -1. */
    public int weakest = -1;
    /** Its card's title ("One story holds up this cause"), or empty. */
    public String weakTitle = "";
    /** Two sentences on why its support is thin, or empty. */
    public String weakWhy = "";
    /** The time of the chapter's ideas the ratings were made for (they go stale when the ideas are redone). */
    public long ideasTime;
    public String model = "";

    public Entry get(int id) {
        return entries.get(id);
    }

    public Rating rating(int id) {
        final Entry e = entries.get(id);
        return e == null ? null : e.rating;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int count(Rating rating) {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.rating == rating) {
                n++;
            }
        }
        return n;
    }

    /** Only the ideas still in the chapter (after the ideas were redone); the weakest goes when it is gone. */
    public XrayRatings keep(Collection<Integer> ids) {
        final XrayRatings out = new XrayRatings();
        for (Entry e : entries.values()) {
            if (ids.contains(e.id)) {
                out.entries.put(e.id, e);
            }
        }
        if (ids.contains(weakest)) {
            out.weakest = weakest;
            out.weakTitle = weakTitle;
            out.weakWhy = weakWhy;
        }
        out.ideasTime = ideasTime;
        out.model = model;
        return out;
    }

    /**
     * The weakest idea: the one the model named, else the first rated weak, else the first whose
     * source is questioned; -1 when every idea holds up.
     */
    public int weakestId() {
        if (weakest >= 0 && entries.containsKey(weakest)) {
            return weakest;
        }
        Entry best = null;
        for (Entry e : entries.values()) {
            if ((e.rating == Rating.WEAK || e.rating == Rating.QUESTIONED)
                    && (best == null || e.rating.weakerThan(best.rating))) {
                best = e;
            }
        }
        return best == null ? -1 : best.id;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("ideas", ideasTime);
        o.put("model", model == null ? "" : model);
        final JSONArray list = new JSONArray();
        for (Entry e : entries.values()) {
            list.put(new JSONObject().put("id", e.id).put("rating", e.rating.code).put("label", e.label).put("why", e.why));
        }
        o.put("ratings", list);
        if (weakest >= 0) {
            o.put("weakest", new JSONObject().put("id", weakest).put("title", weakTitle).put("explanation", weakWhy));
        }
        return o;
    }

    /** Stored ratings, or null when there are none. */
    public static XrayRatings fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final XrayRatings r = read(o, null);
        r.ideasTime = o.optLong("ideas");
        r.model = o.optString("model", "");
        return r;
    }

    /**
     * The model's answer: {@code {"ratings":[{"id","rating","label","why"}], "weakest":{"id","title",
     * "explanation"}}} (a bare ratings array is read too); ids not in {@code known} (when given)
     * are dropped. Null when nothing in it can be read.
     */
    public static XrayRatings parse(String answer, Collection<Integer> known) {
        if (answer == null) {
            return null;
        }
        final String s = answer.trim();
        try {
            final int brace = s.indexOf('{');
            final int bracket = s.indexOf('[');
            final XrayRatings r;
            if (bracket >= 0 && (brace < 0 || bracket < brace)) {
                final int end = s.lastIndexOf(']');
                if (end < bracket) {
                    return null;
                }
                r = read(new JSONObject().put("ratings", new JSONArray(s.substring(bracket, end + 1))), known);
            } else {
                final int end = s.lastIndexOf('}');
                if (brace < 0 || end < brace) {
                    return null;
                }
                r = read(new JSONObject(s.substring(brace, end + 1)), known);
            }
            return r.entries.isEmpty() ? null : r;
        } catch (JSONException e) {
            return null;
        }
    }

    private static XrayRatings read(JSONObject o, Collection<Integer> known) {
        final XrayRatings r = new XrayRatings();
        final JSONArray list = o.optJSONArray("ratings");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                final JSONObject e = list.optJSONObject(i);
                if (e == null) {
                    continue;
                }
                final int id = e.optInt("id", -1);
                final Rating rating = Rating.of(e.optString("rating", ""));
                if (id < 0 || rating == null || (known != null && !known.contains(id)) || r.entries.containsKey(id)) {
                    continue;
                }
                r.entries.put(id, new Entry(id, rating, e.optString("label", ""), e.optString("why", "")));
            }
        }
        final JSONObject w = o.optJSONObject("weakest");
        if (w != null) {
            final int id = w.optInt("id", -1);
            if (id >= 0 && (known == null || known.contains(id))) {
                r.weakest = id;
                r.weakTitle = w.optString("title", "").trim();
                r.weakWhy = w.optString("explanation", "").trim();
                if (!r.entries.containsKey(id)) {
                    r.entries.put(id, new Entry(id, Rating.WEAK, "", ""));
                }
            }
        }
        return r;
    }
}
