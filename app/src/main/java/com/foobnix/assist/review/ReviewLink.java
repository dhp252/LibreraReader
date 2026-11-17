package com.foobnix.assist.review;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A connection between two ideas of two books of the library (canvas "A connection across
 * books"), kept in the library's assist file under {@code link:<id>}:
 * {@code {a: {book, bookTitle, chapterKey, ideaId, idea}, b: {...}, relation, why, sentence, state}}
 * plus {@code also} (works outside the library the model named) and {@code day}. The key and these
 * names are read by other features (Your edition): do not rename them. {@code book} is the
 * book's file name, as everywhere in the stores. No Android here.
 */
public final class ReviewLink {

    /** Key prefix of a connection in the library assist file. */
    public static final String PREFIX = "link:";

    public static final String SAME = "same";
    public static final String DISAGREES = "disagrees";
    public static final String EXTENDS = "extends";

    /** Found by the request, not looked at yet. */
    public static final String NEW = "new";
    /** "Add to my map". */
    public static final String ADDED = "added";
    /** "Not the same". */
    public static final String REJECTED = "rejected";

    /** Works outside the library kept per connection at most. */
    public static final int MAX_ALSO = 2;

    /** One end of a connection: an idea of a book. */
    public static final class Side {
        /** The book's file name. */
        public String book = "";
        public String bookTitle = "";
        public String author = "";
        /** The year the book was published, 0 when not known. */
        public int year;
        public String chapterKey = "";
        public String chapterTitle = "";
        public String ideaId = "";
        public String idea = "";

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("book", book).put("bookTitle", bookTitle).put("chapterKey", chapterKey)
                    .put("ideaId", ideaId).put("idea", idea).put("chapterTitle", chapterTitle).put("author", author)
                    .put("year", year);
        }

        static Side fromJson(JSONObject o) {
            if (o == null || o.optString("book").isEmpty() || o.optString("idea").trim().isEmpty()) {
                return null;
            }
            final Side s = new Side();
            s.book = o.optString("book");
            s.bookTitle = o.optString("bookTitle", "");
            s.author = o.optString("author", "");
            s.year = Math.max(0, o.optInt("year"));
            s.chapterKey = o.optString("chapterKey", "");
            s.chapterTitle = o.optString("chapterTitle", "");
            s.ideaId = o.optString("ideaId", "");
            s.idea = o.optString("idea").trim();
            return s;
        }

        private String ref() {
            return book + "|" + chapterKey + "|" + ideaId;
        }
    }

    /**
     * A work that is NOT in the library, named by the model from its own knowledge: shown only
     * under a line that says so, never as a quote, never checked by the app.
     */
    public static final class Outside {
        /** {@link #DISAGREES} or {@link #EXTENDS}. */
        public final String relation;
        /** The work and its point, in the model's words. */
        public final String text;

        public Outside(String relation, String text) {
            this.relation = relation;
            this.text = text == null ? "" : text.trim();
        }
    }

    public String id = "";
    public Side a = new Side();
    public Side b = new Side();
    public String relation = SAME;
    /** The model's one line on how the ideas connect. */
    public String why = "";
    /** The reader's own sentence. */
    public String sentence = "";
    public String state = NEW;
    public final List<Outside> also = new ArrayList<>();
    /** The local day it was found. */
    public String day = "";

    public String key() {
        return PREFIX + id;
    }

    /** The id of the connection between two ideas: the same whichever end is named first. */
    public static String id(Side a, Side b) {
        final String x = a.ref();
        final String y = b.ref();
        final String both = x.compareTo(y) <= 0 ? x + "~" + y : y + "~" + x;
        return Integer.toHexString(both.hashCode()) + Integer.toHexString(both.length());
    }

    public boolean isNew() {
        return NEW.equals(state);
    }

    /** "same", "disagrees" or "extends" of what a model or a file says; null for anything else. */
    public static String relation(String r) {
        final String s = r == null ? "" : r.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("same") ? SAME : s.startsWith("disagree") || s.startsWith("contradict") ? DISAGREES
                : s.startsWith("extend") || s.startsWith("build") ? EXTENDS : null;
    }

    /** The years between the two books, 0 when either year is not known. */
    public int yearsApart() {
        return a.year > 0 && b.year > 0 ? Math.abs(a.year - b.year) : 0;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("a", a.toJson());
        o.put("b", b.toJson());
        o.put("relation", relation);
        o.put("why", why);
        o.put("sentence", sentence);
        o.put("state", state);
        final JSONArray list = new JSONArray();
        for (Outside x : also) {
            list.put(new JSONObject().put("relation", x.relation).put("text", x.text));
        }
        o.put("also", list);
        o.put("day", day);
        return o;
    }

    /** The connection stored under {@code key}; null when it is not one (an end or the relation is missing). */
    public static ReviewLink fromJson(String key, JSONObject o) {
        if (o == null || key == null || !key.startsWith(PREFIX) || key.length() == PREFIX.length()) {
            return null;
        }
        final ReviewLink l = new ReviewLink();
        l.id = key.substring(PREFIX.length());
        l.a = Side.fromJson(o.optJSONObject("a"));
        l.b = Side.fromJson(o.optJSONObject("b"));
        final String relation = relation(o.optString("relation"));
        if (l.a == null || l.b == null || relation == null) {
            return null;
        }
        l.relation = relation;
        l.why = o.optString("why", "").trim();
        l.sentence = o.optString("sentence", "").trim();
        final String state = o.optString("state", NEW);
        l.state = ADDED.equals(state) || REJECTED.equals(state) ? state : NEW;
        final JSONArray list = o.optJSONArray("also");
        if (list != null) {
            for (int i = 0; i < list.length() && l.also.size() < MAX_ALSO; i++) {
                final JSONObject x = list.optJSONObject(i);
                final String r = x == null ? null : relation(x.optString("relation"));
                if (r != null && !SAME.equals(r) && !x.optString("text").trim().isEmpty()) {
                    l.also.add(new Outside(r, x.optString("text")));
                }
            }
        }
        l.day = o.optString("day", "");
        return l;
    }
}
