package com.foobnix.assist.debate;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Locale;

/**
 * The model's answer for one round ({@link DebatePrompts}): the author's reply, the quote it
 * names, the idea it rests on, and the referee's verdict. Read tolerantly: text around the JSON,
 * a flat or a nested verdict, a few spellings of who gets the point.
 */
public final class DebateReply {

    /** Who the referee gives a round to. */
    public enum Point {
        READER("reader"), AUTHOR("author"), DRAW("draw");

        public final String code;

        Point(String code) {
            this.code = code;
        }

        /** A point as the model or the store writes it; a draw when it cannot be told. */
        public static Point of(String s) {
            final String c = s == null ? "" : s.trim().toLowerCase(Locale.US);
            if (c.contains("draw") || c.contains("tie") || c.contains("neither") || c.contains("both")
                    || c.contains("none")) {
                return DRAW;
            }
            if (c.contains("reader") || c.equals("you") || c.contains("user") || c.contains("student")) {
                return READER;
            }
            if (c.contains("author") || c.contains("book") || c.contains("writer") || c.equals("ai")) {
                return AUTHOR;
            }
            return DRAW;
        }
    }

    /** The author's position in a few words (asked for in every round, used from the first), or empty. */
    public final String position;
    /** The author's reply, as the model wrote it (its quotes not checked yet). */
    public final String reply;
    /** The direct quote the model says it used, or empty. */
    public final String quote;
    /** The id of the idea the reply rests on, or -1. */
    public final int idea;
    public final Point point;
    /** One sentence on why the point went that way, or empty. */
    public final String why;
    /** The question for the next round, or empty. */
    public final String next;
    /** Where the reader and the author really disagree (the last round), or empty. */
    public final String crux;

    DebateReply(String position, String reply, String quote, int idea, Point point, String why, String next,
                String crux) {
        this.position = position;
        this.reply = reply;
        this.quote = quote;
        this.idea = idea;
        this.point = point;
        this.why = why;
        this.next = next;
        this.crux = crux;
    }

    /**
     * @param known the ids of the chapter's ideas; an idea not among them is dropped
     * @return null when the answer has no JSON object with a reply
     */
    public static DebateReply parse(String answer, Collection<Integer> known) {
        if (answer == null) {
            return null;
        }
        final int from = answer.indexOf('{');
        final int to = answer.lastIndexOf('}');
        if (from < 0 || to < from) {
            return null;
        }
        final JSONObject o;
        try {
            o = new JSONObject(answer.substring(from, to + 1));
        } catch (JSONException e) {
            return null;
        }
        final JSONObject author = o.optJSONObject("author");
        final String reply = first(o, "reply", "answer", "text", author == null ? "author" : "");
        final String said = reply.isEmpty() && author != null ? first(author, "reply", "answer", "text") : reply;
        if (said.isEmpty()) {
            return null;
        }
        JSONObject verdict = o.optJSONObject("verdict");
        if (verdict == null) {
            verdict = o.optJSONObject("referee");
        }
        final JSONObject v = verdict == null ? o : verdict;
        String point = first(v, "point", "winner", "to");
        if (point.isEmpty() && verdict == null) {
            // "verdict": "reader"
            point = first(o, "verdict");
        }
        int idea = number(o, "idea");
        if (idea < 0 && author != null) {
            idea = number(author, "idea");
        }
        if (idea >= 0 && known != null && !known.contains(idea)) {
            idea = -1;
        }
        String quote = first(o, "quote");
        if (quote.isEmpty() && author != null) {
            quote = first(author, "quote");
        }
        String crux = first(o, "crux", "disagreement");
        if (crux.isEmpty()) {
            crux = first(v, "crux", "disagreement");
        }
        return new DebateReply(first(o, "position", "stance"), said, quote, idea, Point.of(point),
                first(v, "why", "reason", "sentence"), first(v, "next", "next_question", "question"), crux);
    }

    /** The first of the keys that holds text. */
    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            if (key.isEmpty() || o.isNull(key)) {
                continue;
            }
            final Object value = o.opt(key);
            if (value instanceof String || value instanceof Number) {
                final String s = String.valueOf(value).replaceAll("\\s+", " ").trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    /** A whole number given as a number or as text, or -1. */
    private static int number(JSONObject o, String key) {
        final Object value = o.opt(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
