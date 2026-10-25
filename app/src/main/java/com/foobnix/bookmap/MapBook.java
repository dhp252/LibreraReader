package com.foobnix.bookmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The book map of one book (F-013): the threads and moments of each chapter, the whole book's
 * lanes, links and key moments, and how far the reader lets it show. One JSON object per part,
 * each with its own {@code t}, so WebDAV sync merges two devices part by part:
 * {@code {"@book": {...}, "@view": {...}, "<chapter key>": {...}}}.
 */
public final class MapBook {

    public static final String BOOK = "@book";
    public static final String VIEW = "@view";

    public static final String INTRODUCE = "introduce";
    public static final String DEVELOP = "develop";
    public static final String TURN = "turn";
    public static final String RESOLVE = "resolve";

    /** Where a chapter's moments came from: its own text, or the ideas of its key passages. */
    public static final String OWN = "own";
    public static final String IDEAS = "ideas";

    public static boolean isKind(String kind) {
        return INTRODUCE.equals(kind) || DEVELOP.equals(kind) || TURN.equals(kind) || RESOLVE.equals(kind);
    }

    /** What a link between two moments says. */
    public static boolean isArcType(String type) {
        return "pays_off".equals(type) || "leads_to".equals(type) || "supports".equals(type)
                || "contradicts".equals(type) || "needs".equals(type);
    }

    /** A thing a chapter is built from: a person, an idea, an object, a question. */
    public static final class Thread {
        public final String name;
        public final List<String> aliases;
        public final String what;

        public Thread(String name, List<String> aliases, String what) {
            this.name = name;
            this.aliases = aliases == null ? new ArrayList<>() : aliases;
            this.what = what == null ? "" : what;
        }
    }

    /** A moment that changes one or more threads, anchored to the book's own words. */
    public static final class Moment {
        public final List<String> threads;
        public final String kind;
        public final String label;
        /** The words as the book has them. */
        public final String quote;
        /** 0-based page where the quote started when the map was built (a hint: EPUB pages move). */
        public final int page;
        /** Where in the chapter, 0 (start) to 1 (end). */
        public final double at;

        public Moment(List<String> threads, String kind, String label, String quote, int page, double at) {
            this.threads = threads;
            this.kind = kind;
            this.label = label;
            this.quote = quote;
            this.page = page;
            this.at = at;
        }
    }

    public static final class Chapter {
        public final String key;
        /** 1-based place among the book's chapters. */
        public int n;
        public String title = "";
        public int from;
        public int to;
        /** Characters of text, for the chapter's length. */
        public long chars;
        public String source = OWN;
        public String model = "";
        public boolean cutOff;
        /** The chapter has no text (scanned pages, pictures): nothing to ask about. */
        public boolean noText;
        /** Moments the model gave whose quote was not found in the book. */
        public int dropped;
        public final List<Thread> threads = new ArrayList<>();
        public final List<Moment> moments = new ArrayList<>();
        public long t;

        public Chapter(String key) {
            this.key = key;
        }

        /** The id the whole-book step and the page use for the moment at {@code index}. */
        public String momentId(int index) {
            return n + "." + (index + 1);
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("n", n);
            o.put("title", title);
            o.put("from", from);
            o.put("to", to);
            o.put("chars", chars);
            o.put("source", source);
            o.put("model", model);
            if (cutOff) {
                o.put("cutOff", true);
            }
            if (noText) {
                o.put("noText", true);
            }
            if (dropped > 0) {
                o.put("dropped", dropped);
            }
            final JSONArray th = new JSONArray();
            for (Thread x : threads) {
                th.put(new JSONObject().put("name", x.name).put("aliases", new JSONArray(x.aliases)).put("what", x.what));
            }
            o.put("threads", th);
            final JSONArray ms = new JSONArray();
            for (Moment m : moments) {
                ms.put(new JSONObject().put("threads", new JSONArray(m.threads)).put("kind", m.kind)
                        .put("label", m.label).put("quote", m.quote).put("page", m.page).put("at", m.at));
            }
            o.put("moments", ms);
            o.put("t", t);
            return o;
        }

        static Chapter fromJson(String key, JSONObject o) {
            final Chapter c = new Chapter(key);
            c.n = o.optInt("n", 0);
            c.title = o.optString("title", "");
            c.from = o.optInt("from", 0);
            c.to = o.optInt("to", 0);
            c.chars = o.optLong("chars", 0);
            c.source = o.optString("source", OWN);
            c.model = o.optString("model", "");
            c.cutOff = o.optBoolean("cutOff", false);
            c.noText = o.optBoolean("noText", false);
            c.dropped = o.optInt("dropped", 0);
            final JSONArray th = o.optJSONArray("threads");
            for (int i = 0; th != null && i < th.length(); i++) {
                final JSONObject x = th.optJSONObject(i);
                if (x != null && !x.optString("name", "").isEmpty()) {
                    c.threads.add(new Thread(x.optString("name"), strings(x.optJSONArray("aliases")), x.optString("what", "")));
                }
            }
            final JSONArray ms = o.optJSONArray("moments");
            for (int i = 0; ms != null && i < ms.length(); i++) {
                final JSONObject m = ms.optJSONObject(i);
                if (m != null && isKind(m.optString("kind"))) {
                    c.moments.add(new Moment(strings(m.optJSONArray("threads")), m.optString("kind"),
                            m.optString("label", ""), m.optString("quote", ""), m.optInt("page", -1), m.optDouble("at", 0)));
                }
            }
            c.t = o.optLong("t", 0);
            return c;
        }
    }

    /** A lane of the map: one thread of the whole book, made of the chapter threads it merges. */
    public static final class Lane {
        public final String id;
        public final String name;
        public final String what;
        public final List<String> merged;

        public Lane(String id, String name, String what, List<String> merged) {
            this.id = id;
            this.name = name;
            this.what = what == null ? "" : what;
            this.merged = merged == null ? new ArrayList<>() : merged;
        }
    }

    /** A link between two moments of different chapters, by their ids ("3.4"). */
    public static final class Arc {
        public final String from;
        public final String to;
        public final String type;
        public final String why;

        public Arc(String from, String to, String type, String why) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.why = why == null ? "" : why;
        }
    }

    // the whole book (@book)
    public String premise = "";
    public final List<Lane> lanes = new ArrayList<>();
    public final List<Arc> arcs = new ArrayList<>();
    public final List<String> key = new ArrayList<>();
    /** The chapter keys the whole-book step saw. */
    public final List<String> seen = new ArrayList<>();
    public String model = "";
    public long t;

    // the reader's view (@view): null = never chosen
    public Boolean whole;
    public long viewT;

    public final Map<String, Chapter> chapters = new LinkedHashMap<>();

    /** Whether the whole-book step has run. */
    public boolean hasBook() {
        return !lanes.isEmpty();
    }

    public boolean isEmpty() {
        return chapters.isEmpty() && !hasBook();
    }

    public Chapter chapter(String key) {
        return chapters.get(key);
    }

    public Chapter chapterOrNew(String key) {
        Chapter c = chapters.get(key);
        if (c == null) {
            c = new Chapter(key);
            chapters.put(key, c);
        }
        return c;
    }

    /** The chapters in the book's order. */
    public List<Chapter> ordered() {
        final List<Chapter> out = new ArrayList<>(chapters.values());
        Collections.sort(out, (a, b) -> a.n != b.n ? Integer.compare(a.n, b.n) : Integer.compare(a.from, b.from));
        return out;
    }

    /** Everything goes: the map is deleted (the sync deletes it on the other devices too). */
    public void clear() {
        premise = "";
        lanes.clear();
        arcs.clear();
        key.clear();
        seen.clear();
        model = "";
        whole = null;
        chapters.clear();
    }

    /** The whole-book part as JSON, or null when the whole-book step never ran. */
    JSONObject bookJson() throws JSONException {
        if (!hasBook() && premise.isEmpty()) {
            return null;
        }
        final JSONObject o = new JSONObject();
        o.put("premise", premise);
        final JSONArray ls = new JSONArray();
        for (Lane l : lanes) {
            ls.put(new JSONObject().put("id", l.id).put("name", l.name).put("what", l.what).put("merged", new JSONArray(l.merged)));
        }
        o.put("lanes", ls);
        final JSONArray as = new JSONArray();
        for (Arc a : arcs) {
            as.put(new JSONObject().put("from", a.from).put("to", a.to).put("type", a.type).put("why", a.why));
        }
        o.put("arcs", as);
        o.put("key", new JSONArray(key));
        o.put("chapters", new JSONArray(seen));
        o.put("model", model);
        o.put("t", t);
        return o;
    }

    JSONObject viewJson() throws JSONException {
        return whole == null ? null : new JSONObject().put("whole", whole).put("t", viewT);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        final JSONObject book = bookJson();
        if (book != null) {
            o.put(BOOK, book);
        }
        final JSONObject view = viewJson();
        if (view != null) {
            o.put(VIEW, view);
        }
        for (Chapter c : ordered()) {
            o.put(c.key, c.toJson());
        }
        return o;
    }

    public static MapBook fromJson(String json) throws JSONException {
        final MapBook b = new MapBook();
        if (json == null || json.trim().isEmpty()) {
            return b;
        }
        final JSONObject o = new JSONObject(json);
        final JSONObject book = o.optJSONObject(BOOK);
        if (book != null) {
            b.premise = book.optString("premise", "");
            final JSONArray ls = book.optJSONArray("lanes");
            for (int i = 0; ls != null && i < ls.length(); i++) {
                final JSONObject l = ls.optJSONObject(i);
                if (l != null && !l.optString("id", "").isEmpty()) {
                    b.lanes.add(new Lane(l.optString("id"), l.optString("name", l.optString("id")), l.optString("what", ""),
                            strings(l.optJSONArray("merged"))));
                }
            }
            final JSONArray as = book.optJSONArray("arcs");
            for (int i = 0; as != null && i < as.length(); i++) {
                final JSONObject a = as.optJSONObject(i);
                if (a != null) {
                    b.arcs.add(new Arc(a.optString("from"), a.optString("to"), a.optString("type"), a.optString("why", "")));
                }
            }
            b.key.addAll(strings(book.optJSONArray("key")));
            b.seen.addAll(strings(book.optJSONArray("chapters")));
            b.model = book.optString("model", "");
            b.t = book.optLong("t", 0);
        }
        final JSONObject view = o.optJSONObject(VIEW);
        if (view != null && view.has("whole")) {
            b.whole = view.optBoolean("whole");
            b.viewT = view.optLong("t", 0);
        }
        final Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            final String k = keys.next();
            if (BOOK.equals(k) || VIEW.equals(k)) {
                continue;
            }
            final JSONObject c = o.optJSONObject(k);
            if (c != null) {
                b.chapters.put(k, Chapter.fromJson(k, c));
            }
        }
        return b;
    }

    static List<String> strings(JSONArray a) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            final String s = a.optString(i, "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
