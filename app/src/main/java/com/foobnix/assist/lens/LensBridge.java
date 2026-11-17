package com.foobnix.assist.lens;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A chapter's concept bridge (canvas LensBridge, "in your terms"): up to six of its ideas, each
 * named as the author has it and as the reader's lens puts it, and one note on where the analogy
 * breaks. Made by one request per chapter ({@link LensPrompts}) and kept in the book's file of
 * {@code AssistStore} under {@code lens:bridge:<chapter key>}; a row's ladder is kept inside its
 * row. No Android here.
 */
public final class LensBridge {

    public static final String PREFIX = "lens:bridge:";
    /** Ideas mapped per chapter. */
    public static final int MAX_ROWS = 6;

    /** One idea, mapped. */
    public static final class Row {
        /** The idea in the author's terms ("Division of labour"). */
        public String idea = "";
        /** The same idea in the reader's terms ("Split a monolith into pipeline stages"). */
        public String yours = "";
        /** The book's words a tap goes to, as the pages hold them; empty when none was found. */
        public String quote = "";
        /** 0-based page of the quote when it was found (a hint: pages of a reflowing book move), or -1. */
        public int page = -1;
        /** The key-passage idea it comes from, or -1. */
        public int id = -1;
        /** Its ladder once it was asked for, else null. */
        public LensLadder ladder;

        /** A tap can go to its place in the book. */
        public boolean located() {
            return page >= 0 || !quote.isEmpty();
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("idea", idea);
            o.put("yours", yours);
            o.put("quote", quote);
            o.put("page", page);
            o.put("id", id);
            if (ladder != null) {
                o.put("ladder", ladder.toJson());
            }
            return o;
        }

        static Row fromJson(JSONObject o) {
            final Row r = new Row();
            r.idea = o.optString("idea", "");
            r.yours = o.optString("yours", "");
            r.quote = o.optString("quote", "");
            r.page = o.optInt("page", -1);
            r.id = o.optInt("id", -1);
            r.ladder = LensLadder.fromJson(o.optJSONObject("ladder"));
            return r;
        }
    }

    /** The chapter's title as the table of contents has it (may be empty). */
    public String title = "";
    public final List<Row> rows = new ArrayList<>();
    /** Where the analogy stops fitting, in a sentence or two; may be empty. */
    public String breaks = "";
    /** What it was made with ({@link LensProfile#signature}). */
    public String lens = "";
    /** The lens's short name when it was made ("ML +1"). */
    public String lensName = "";
    /** Made from the chapter's stored key-passage ideas (else from its text). */
    public boolean fromIdeas;
    /** The time of those key passages. */
    public long ideasTime;
    public String model = "";

    /** The store key of a chapter's bridge. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    /** Made with another lens or level than {@code signature}. */
    public boolean isOfAnotherLens(String signature) {
        return !lens.equals(signature);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        final JSONArray list = new JSONArray();
        for (Row r : rows) {
            list.put(r.toJson());
        }
        o.put("rows", list);
        o.put("breaks", breaks);
        o.put("lens", lens);
        o.put("lensName", lensName);
        o.put("fromIdeas", fromIdeas);
        o.put("ideasTime", ideasTime);
        o.put("model", model);
        return o;
    }

    /** A stored bridge, or null when the item is missing or holds no row. */
    public static LensBridge fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final LensBridge b = new LensBridge();
        b.title = o.optString("title", "");
        final JSONArray list = o.optJSONArray("rows");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                final JSONObject r = list.optJSONObject(i);
                if (r != null) {
                    b.rows.add(Row.fromJson(r));
                }
            }
        }
        b.breaks = o.optString("breaks", "");
        b.lens = o.optString("lens", "");
        b.lensName = o.optString("lensName", "");
        b.fromIdeas = o.optBoolean("fromIdeas");
        b.ideasTime = o.optLong("ideasTime");
        b.model = o.optString("model", "");
        return b.rows.isEmpty() ? null : b;
    }

    // ------------------------------------------------------------------ the answer

    /**
     * The bridge of an answer: {@code {"pairs":[{"id":3,"author":"...","quote":"...","yours":"..."}],
     * "breaks":"..."}}. Tolerant of Markdown fences, text around the JSON, a bare list of pairs and
     * a few other names; a pair without both sides is left out; {@link #MAX_ROWS} at most. Null
     * when no pair can be read.
     */
    public static LensBridge parse(String answer) {
        if (answer == null) {
            return null;
        }
        JSONArray pairs = null;
        String breaks = "";
        final int brace = answer.indexOf('{');
        final int bracket = answer.indexOf('[');
        if (bracket >= 0 && (brace < 0 || bracket < brace)) {
            final int close = answer.lastIndexOf(']');
            if (close > bracket) {
                try {
                    pairs = new JSONArray(answer.substring(bracket, close + 1));
                } catch (JSONException e) {
                    pairs = null;
                }
            }
        }
        if (pairs == null) {
            final JSONObject o = object(answer);
            if (o == null) {
                return null;
            }
            for (String name : new String[]{"pairs", "rows", "bridges", "ideas", "map"}) {
                pairs = o.optJSONArray(name);
                if (pairs != null) {
                    break;
                }
            }
            breaks = text(o, "breaks", "where_it_breaks", "analogy_breaks", "where the analogy breaks", "limits");
        }
        if (pairs == null) {
            return null;
        }
        final LensBridge b = new LensBridge();
        b.breaks = breaks;
        for (int i = 0; i < pairs.length() && b.rows.size() < MAX_ROWS; i++) {
            final JSONObject p = pairs.optJSONObject(i);
            if (p == null) {
                continue;
            }
            final Row r = new Row();
            r.idea = text(p, "author", "idea", "author_says", "says", "authors_idea");
            r.yours = text(p, "yours", "in_your_terms", "your_terms", "terms", "you");
            if (r.idea.isEmpty() || r.yours.isEmpty()) {
                continue;
            }
            r.quote = text(p, "quote", "sentence");
            r.id = number(p.opt("id"));
            b.rows.add(r);
        }
        return b.rows.isEmpty() ? null : b;
    }

    /**
     * Ties rows made from stored ideas to the book: a row naming an idea's id takes that idea's
     * mark (its words and page); any other row keeps no quote, since the model saw none.
     */
    public void attach(List<LensIdea> ideas) {
        for (Row r : rows) {
            r.quote = "";
            r.page = -1;
            LensIdea found = null;
            for (LensIdea idea : ideas) {
                if (idea.id == r.id) {
                    found = idea;
                    break;
                }
            }
            if (found == null) {
                r.id = -1;
                continue;
            }
            r.quote = found.quote == null ? "" : found.quote;
            r.page = found.page;
        }
    }

    /** The first JSON object in an answer (from its first "{" to its last "}"), or null. */
    static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return null;
        }
        try {
            return new JSONObject(answer.substring(open, close + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    /** The first of these names holding text (a string, or an object with "text"), cleaned; else empty. */
    private static String text(JSONObject o, String... names) {
        for (String name : names) {
            final Object v = o.opt(name);
            String s = "";
            if (v instanceof String) {
                s = clean((String) v);
            } else if (v instanceof JSONObject) {
                s = clean(((JSONObject) v).optString("text", ((JSONObject) v).optString("note", "")));
            }
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    private static int number(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** One line, trimmed, without quotation marks or asterisks wrapped around it. */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        String out = s.replaceAll("\\s+", " ").trim();
        if (out.length() >= 2 && isWrap(out.charAt(0)) && isWrap(out.charAt(out.length() - 1))) {
            int from = 0;
            int to = out.length();
            while (from < to && isWrap(out.charAt(from))) {
                from++;
            }
            while (to > from && isWrap(out.charAt(to - 1))) {
                to--;
            }
            final String core = out.substring(from, to);
            // marks inside too ("stock" becomes "savings") belong to words, not to the whole text
            if (core.indexOf(out.charAt(0)) < 0 && core.indexOf(out.charAt(out.length() - 1)) < 0) {
                out = core.trim();
            }
        }
        return out;
    }

    private static boolean isWrap(char c) {
        return c == '"' || c == '“' || c == '”' || c == '\'' || c == '*' || c == '`' || c == '«'
                || c == '»';
    }
}
