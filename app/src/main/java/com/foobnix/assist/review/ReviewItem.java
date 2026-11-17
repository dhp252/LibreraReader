package com.foobnix.assist.review;

import com.foobnix.assist.useit.UseDates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One idea in the spaced-review queue (canvas "Today's review"; Assist.REVIEW), kept in the book's
 * assist file under {@code review:<chapter key>:<idea id>}:
 * {@code {idea, chapterTitle, reason, strength, due, history: [{day, kind, right, gap}]}} plus what the
 * app needs to find it again ({@code chapter}, {@code id}, {@code quote}, {@code page},
 * {@code gap}, {@code recall}). The key and the first six names are read by other features (Your
 * edition): do not rename them. No Android here.
 */
public final class ReviewItem {

    /** Key prefix of an idea in a book's assist file. */
    public static final String PREFIX = "review:";

    /** Why an idea is in the queue. */
    public static final String MISSED = "missed";
    public static final String UNSURE = "unsure";
    public static final String FADING = "fading";
    public static final String NOTE = "note";

    /** What a history entry was. */
    public static final String RECALL = "recall";
    public static final String REVIEW = "review";
    public static final String ADDED = "added";

    public static final int MAX_STRENGTH = 5;
    /** History entries kept. */
    public static final int HISTORY = 40;

    /** One past event of an idea: a blank-page recall, a scenario answer, or being added. */
    public static final class Past {
        public final String day;
        public final String kind;
        public final boolean right;
        /** The days the idea was sent away for after it, 0 when not known. */
        public final int gap;

        public Past(String day, String kind, boolean right, int gap) {
            this.day = day == null ? "" : day;
            this.kind = kind == null ? "" : kind;
            this.right = right;
            this.gap = Math.max(0, gap);
        }
    }

    public String chapterKey = "";
    /** The idea's id inside its chapter; never holds a colon. */
    public String id = "";
    public String idea = "";
    public String chapterTitle = "";
    /**
     * Why the idea entered the queue; it stays, so another feature can tell what was missed at
     * first ({@link #label()} is what the screens show now).
     */
    public String reason = FADING;
    /** "memory n of 5". */
    public int strength;
    /** The local day it comes back, yyyy-MM-dd. */
    public String due = "";
    public final List<Past> history = new ArrayList<>();

    /** The idea's passage in the book's own words (where "Explain more" goes), or empty. */
    public String quote = "";
    /** The 0-based page of the passage when it was stored (a hint), or -1. */
    public int page = -1;
    /** The days waited before {@link #due}, 0 when it was never answered. */
    public int gap;
    /** The time of the blank-page recall attempt read last, 0 when none. */
    public long recall;

    public String key() {
        return key(chapterKey, id);
    }

    public static String key(String chapterKey, String id) {
        return PREFIX + (chapterKey == null ? "" : chapterKey) + ":" + id(id);
    }

    /** An id safe after the key's last colon. */
    public static String id(String id) {
        return id == null ? "" : id.trim().replace(':', '_');
    }

    /**
     * What the screens say of the idea now: {@link #MISSED} or {@link #UNSURE} until it was
     * answered right after that recall, then {@link #FADING}; a note stays a {@link #NOTE}.
     */
    public String label() {
        if (!MISSED.equals(reason) && !UNSURE.equals(reason)) {
            return reason;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            final Past p = history.get(i);
            if (RECALL.equals(p.kind) && !p.right) {
                return reason;
            }
            if (REVIEW.equals(p.kind) && p.right) {
                return FADING;
            }
        }
        return reason;
    }

    /** The day of the last history entry, or empty. */
    public String lastDay() {
        return history.isEmpty() ? "" : history.get(history.size() - 1).day;
    }

    public void add(Past p) {
        history.add(p);
        while (history.size() > HISTORY) {
            history.remove(0);
        }
    }

    public boolean isDue(String today) {
        return UseDates.reached(today, due);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("idea", idea);
        o.put("chapterTitle", chapterTitle);
        o.put("reason", reason);
        o.put("strength", strength);
        o.put("due", due);
        final JSONArray h = new JSONArray();
        for (Past p : history) {
            h.put(new JSONObject().put("day", p.day).put("kind", p.kind).put("right", p.right).put("gap", p.gap));
        }
        o.put("history", h);
        o.put("chapter", chapterKey);
        o.put("id", id);
        o.put("quote", quote);
        o.put("page", page);
        o.put("gap", gap);
        o.put("recall", recall);
        return o;
    }

    /**
     * The item stored under {@code key}; null when it is not an idea of the queue (no idea text,
     * no due day, or a key of another kind).
     */
    public static ReviewItem fromJson(String key, JSONObject o) {
        if (o == null || key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        final ReviewItem i = new ReviewItem();
        final String rest = key.substring(PREFIX.length());
        final int colon = rest.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        // the key names the idea; the fields only repeat it
        i.chapterKey = rest.substring(0, colon);
        i.id = rest.substring(colon + 1);
        i.idea = o.optString("idea", "").trim();
        i.due = o.optString("due", "");
        if (i.idea.isEmpty() || i.id.isEmpty() || !UseDates.isDay(i.due)) {
            return null;
        }
        i.chapterTitle = o.optString("chapterTitle", "");
        i.reason = reason(o.optString("reason", ""));
        i.strength = strength(o.optInt("strength"));
        final JSONArray h = o.optJSONArray("history");
        if (h != null) {
            for (int n = 0; n < h.length(); n++) {
                final JSONObject p = h.optJSONObject(n);
                if (p != null && UseDates.isDay(p.optString("day"))) {
                    i.history.add(new Past(p.optString("day"), p.optString("kind", REVIEW), p.optBoolean("right"),
                            p.optInt("gap")));
                }
            }
        }
        i.quote = o.optString("quote", "");
        i.page = o.optInt("page", -1);
        i.gap = Math.max(0, o.optInt("gap"));
        i.recall = o.optLong("recall");
        return i;
    }

    public static String reason(String r) {
        return MISSED.equals(r) || UNSURE.equals(r) || NOTE.equals(r) ? r : FADING;
    }

    public static int strength(int s) {
        return Math.max(0, Math.min(MAX_STRENGTH, s));
    }
}
