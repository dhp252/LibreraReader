package com.foobnix.assist.debate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What is kept of a finished debate, as {@code debate:<chapter key>:<idea id>} in the book's
 * assist file: the claim, both positions, each round's verdict, where the two really disagree and
 * the date. The chat itself is not kept.
 */
public final class DebateResult {

    public static final String PREFIX = "debate:";

    /** The referee's verdict of one round. */
    public static final class Verdict {
        public final DebateReply.Point point;
        public final String why;

        public Verdict(DebateReply.Point point, String why) {
            this.point = point == null ? DebateReply.Point.DRAW : point;
            this.why = why == null ? "" : why;
        }
    }

    /** The idea argued about. */
    public String claim = "";
    /** The reader's position. */
    public String you = "";
    /** The author's position. */
    public String author = "";
    /** The author's name, or empty. */
    public String name = "";
    public final List<Verdict> rounds = new ArrayList<>();
    /** Where the reader and the author really disagree, or empty. */
    public String crux = "";
    /** When the debate ended, ms. */
    public long date;

    /** The item key of a chapter's idea. */
    public static String key(String chapterKey, int ideaId) {
        return prefix(chapterKey) + ideaId;
    }

    /** What the keys of a chapter's debates start with. */
    public static String prefix(String chapterKey) {
        return PREFIX + chapterKey + ":";
    }

    /** The idea id of an item key, or -1. */
    public static int ideaOf(String key) {
        final int at = key == null ? -1 : key.lastIndexOf(':');
        if (at < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(key.substring(at + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int points(DebateReply.Point who) {
        int n = 0;
        for (Verdict v : rounds) {
            if (v.point == who) {
                n++;
            }
        }
        return n;
    }

    /** Who has more points; a draw when they are level. */
    public DebateReply.Point leader() {
        final int reader = points(DebateReply.Point.READER);
        final int author = points(DebateReply.Point.AUTHOR);
        return reader > author ? DebateReply.Point.READER : author > reader ? DebateReply.Point.AUTHOR
                : DebateReply.Point.DRAW;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("claim", claim);
        o.put("you", you);
        o.put("author", author);
        o.put("name", name);
        final JSONArray list = new JSONArray();
        for (Verdict v : rounds) {
            list.put(new JSONObject().put("point", v.point.code).put("why", v.why));
        }
        o.put("rounds", list);
        o.put("crux", crux);
        o.put("date", date);
        return o;
    }

    /** A stored result, or null when there is none or it has no rounds. */
    public static DebateResult fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final DebateResult r = new DebateResult();
        r.claim = o.optString("claim", "");
        r.you = o.optString("you", "");
        r.author = o.optString("author", "");
        r.name = o.optString("name", "");
        r.crux = o.optString("crux", "");
        r.date = o.optLong("date");
        final JSONArray list = o.optJSONArray("rounds");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                final JSONObject v = list.optJSONObject(i);
                if (v != null) {
                    r.rounds.add(new Verdict(DebateReply.Point.of(v.optString("point", "")), v.optString("why", "")));
                }
            }
        }
        return r.rounds.isEmpty() ? null : r;
    }
}
