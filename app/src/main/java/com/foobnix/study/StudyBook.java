package com.foobnix.study;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What study mode keeps for one book: {@code {"@book": {...}, "<chapter key>": {...}}}, each object
 * with its own change time {@code t}, so WebDAV sync merges the file chapter by chapter
 * (ProfileMerge) and answers written on two devices both stay.
 */
public class StudyBook {

    public static final String BOOK = "@book";

    public static final String READ = "read";
    public static final String SKIM = "skim";
    public static final String SKIP = "skip";

    /** Studying is on for the book (off keeps everything). */
    public boolean on;
    /** The book's main claim, from the preview. */
    public String thesis = "";
    /** The goal the labels were judged against. */
    public String goal = "";
    public String model = "";
    /** When the preview was made; 0 = none yet. */
    public long previewed;
    /** Keys of the top-level parts studied chapter by chapter ({@link StudyChapters#longParts}). */
    public final Set<String> split = new LinkedHashSet<>();
    public long t;

    /** In book order. */
    public final Map<String, Chapter> chapters = new LinkedHashMap<>();

    public static class Chapter {
        public final String key;
        public String title = "";
        /** Place in the book, for the order. */
        public int index;
        /** Pages (0-based, to exclusive) when previewed: right for fixed layouts, a hint for others. */
        public int from = -1;
        public int to = -1;
        public long chars;
        /** One line on what the chapter is about. */
        public String line = "";
        public String label = READ;
        /** The reader set the label: a new preview keeps it. */
        public boolean labelMine;
        public String reason = "";
        public final List<String> questions = new ArrayList<>();
        public long beforeShown;
        public long afterShown;
        /** The reader's answers, one per question (kept as typed, also before a check). */
        public final List<String> answers = new ArrayList<>();
        /** "Anything else you remember?" */
        public String recall = "";
        /** Null until checked. */
        public Feedback feedback;
        /** The chapter's part of the outline. */
        public final List<Node> branch = new ArrayList<>();
        /** When the chapter was checked or added to the map; 0 = not yet. */
        public long checked;
        public long t;

        public Chapter(String key) {
            this.key = key;
        }

        public boolean hasAnswers() {
            if (recall != null && !recall.trim().isEmpty()) {
                return true;
            }
            for (String a : answers) {
                if (a != null && !a.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject()
                    .put("title", title)
                    .put("i", index)
                    .put("from", from)
                    .put("to", to)
                    .put("chars", chars)
                    .put("line", line)
                    .put("label", label)
                    .put("mine", labelMine)
                    .put("reason", reason)
                    .put("q", new JSONArray(questions))
                    .put("before", beforeShown)
                    .put("after", afterShown)
                    .put("a", new JSONArray(answers))
                    .put("recall", recall)
                    .put("checked", checked)
                    .put("t", t);
            if (feedback != null) {
                o.put("fb", feedback.toJson());
            }
            if (!branch.isEmpty()) {
                o.put("branch", Node.toJson(branch));
            }
            return o;
        }

        static Chapter fromJson(String key, JSONObject o) {
            final Chapter c = new Chapter(key);
            c.title = o.optString("title", "");
            c.index = o.optInt("i", 0);
            c.from = o.optInt("from", -1);
            c.to = o.optInt("to", -1);
            c.chars = o.optLong("chars", 0);
            c.line = o.optString("line", "");
            c.label = label(o.optString("label", READ));
            c.labelMine = o.optBoolean("mine", false);
            c.reason = o.optString("reason", "");
            strings(o.optJSONArray("q"), c.questions);
            c.beforeShown = o.optLong("before", 0);
            c.afterShown = o.optLong("after", 0);
            strings(o.optJSONArray("a"), c.answers);
            c.recall = o.optString("recall", "");
            c.checked = o.optLong("checked", 0);
            c.t = o.optLong("t", 0);
            final JSONObject fb = o.optJSONObject("fb");
            c.feedback = fb == null ? null : Feedback.fromJson(fb);
            Node.fromJson(o.optJSONArray("branch"), c.branch);
            return c;
        }
    }

    /** What the AI said about the reader's answers. */
    public static class Feedback {
        public final List<Item> items = new ArrayList<>();
        /** The feedback as the model wrote it (shown when its lines could not be read one by one). */
        public String text = "";
        /** About the free recall: what was left out. */
        public String recall = "";
        public String model = "";
        public long time;

        JSONObject toJson() throws JSONException {
            final JSONArray list = new JSONArray();
            for (Item item : items) {
                list.put(new JSONObject().put("q", item.question).put("v", item.verdict)
                        .put("note", item.note).put("quote", item.quote).put("p", item.page));
            }
            return new JSONObject().put("items", list).put("text", text).put("recall", recall).put("model", model)
                    .put("time", time);
        }

        static Feedback fromJson(JSONObject o) {
            final Feedback f = new Feedback();
            final JSONArray list = o.optJSONArray("items");
            for (int i = 0; list != null && i < list.length(); i++) {
                final JSONObject item = list.optJSONObject(i);
                if (item != null) {
                    final Item it = new Item(item.optInt("q", i), item.optString("v", Item.PARTLY),
                            item.optString("note", ""), item.optString("quote", ""));
                    it.page = item.optInt("p", -1);
                    f.items.add(it);
                }
            }
            f.text = o.optString("text", "");
            f.recall = o.optString("recall", "");
            f.model = o.optString("model", "");
            f.time = o.optLong("time", 0);
            return f;
        }
    }

    /** The verdict on one answer. */
    public static class Item {
        public static final String RIGHT = "right";
        public static final String PARTLY = "partly";
        public static final String MISSED = "missed";

        /** 0-based question. */
        public final int question;
        public final String verdict;
        public final String note;
        /** Words of the book the note points to (a page link), or empty. */
        public String quote;
        /** 0-based page where the quote was found (in the layout of the check), -1 when not found. */
        public int page = -1;

        public Item(int question, String verdict, String note, String quote) {
            this.question = question;
            this.verdict = verdict;
            this.note = note;
            this.quote = quote;
        }
    }

    /** One idea of the outline: a claim, its evidence, a caveat, an example, a note... */
    public static class Node {
        public final String text;
        /** An AI category code (main, evidence, concept, caveat, apply, example) or "note". */
        public final String category;
        /** Words of the book it comes from (its page link), or empty. */
        public String quote;
        /** 0-based page where the quote was found (in the layout of the check), -1 when not found. */
        public int page = -1;
        public final List<Node> kids = new ArrayList<>();

        public Node(String text, String category, String quote) {
            this.text = text;
            this.category = category;
            this.quote = quote;
        }

        public static int count(List<Node> nodes) {
            int n = 0;
            for (Node node : nodes) {
                n += 1 + count(node.kids);
            }
            return n;
        }

        static JSONArray toJson(List<Node> nodes) throws JSONException {
            final JSONArray out = new JSONArray();
            for (Node n : nodes) {
                final JSONObject o = new JSONObject().put("text", n.text).put("cat", n.category).put("quote", n.quote)
                        .put("p", n.page);
                if (!n.kids.isEmpty()) {
                    o.put("kids", toJson(n.kids));
                }
                out.put(o);
            }
            return out;
        }

        static void fromJson(JSONArray array, List<Node> into) {
            for (int i = 0; array != null && i < array.length(); i++) {
                final JSONObject o = array.optJSONObject(i);
                if (o == null || o.optString("text", "").trim().isEmpty()) {
                    continue;
                }
                final Node node = new Node(o.optString("text", "").trim(), o.optString("cat", ""), o.optString("quote", ""));
                node.page = o.optInt("p", -1);
                fromJson(o.optJSONArray("kids"), node.kids);
                into.add(node);
            }
        }
    }

    public Chapter chapter(String key) {
        return chapters.get(key);
    }

    /** The chapter with this key, added at the end when new. */
    public Chapter chapterOrNew(String key) {
        Chapter c = chapters.get(key);
        if (c == null) {
            c = new Chapter(key);
            c.index = chapters.size();
            chapters.put(key, c);
        }
        return c;
    }

    public boolean hasPreview() {
        return previewed > 0 && !chapters.isEmpty();
    }

    /** ● read closely, ◐ skim, ○ skip. */
    public static String labelMark(String label) {
        if (SKIP.equals(label)) {
            return "○";
        }
        return SKIM.equals(label) ? "◐" : "●";
    }

    /** Green read, amber skim, grey skip. */
    public static int labelColor(String label) {
        if (SKIP.equals(label)) {
            return 0xff9e9e9e;
        }
        return SKIM.equals(label) ? 0xfff4a300 : 0xff5fb236;
    }

    /** A known label, else {@link #READ}. */
    public static String label(String s) {
        if (s == null) {
            return READ;
        }
        final String l = s.trim().toLowerCase(java.util.Locale.ROOT);
        return SKIM.equals(l) || SKIP.equals(l) ? l : READ;
    }

    JSONObject bookJson() throws JSONException {
        return new JSONObject()
                .put("on", on)
                .put("thesis", thesis)
                .put("goal", goal)
                .put("model", model)
                .put("previewed", previewed)
                .put("split", new JSONArray(new ArrayList<>(split)))
                .put("t", t);
    }

    JSONObject toJson() throws JSONException {
        final JSONObject root = new JSONObject();
        root.put(BOOK, bookJson());
        for (Chapter c : chapters.values()) {
            root.put(c.key, c.toJson());
        }
        return root;
    }

    static StudyBook fromJson(String json) throws JSONException {
        final StudyBook book = new StudyBook();
        if (json == null || json.trim().isEmpty()) {
            return book;
        }
        final JSONObject root = new JSONObject(json);
        final JSONObject b = root.optJSONObject(BOOK);
        if (b != null) {
            book.on = b.optBoolean("on", false);
            book.thesis = b.optString("thesis", "");
            book.goal = b.optString("goal", "");
            book.model = b.optString("model", "");
            book.previewed = b.optLong("previewed", 0);
            final List<String> split = new ArrayList<>();
            strings(b.optJSONArray("split"), split);
            book.split.addAll(split);
            book.t = b.optLong("t", 0);
        }
        final List<Chapter> list = new ArrayList<>();
        final Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final JSONObject o = root.optJSONObject(key);
            if (!BOOK.equals(key) && o != null) {
                list.add(Chapter.fromJson(key, o));
            }
        }
        Collections.sort(list, (x, y) -> Integer.compare(x.index, y.index));
        for (Chapter c : list) {
            book.chapters.put(c.key, c);
        }
        return book;
    }

    private static void strings(JSONArray array, List<String> into) {
        for (int i = 0; array != null && i < array.length(); i++) {
            into.add(array.optString(i, ""));
        }
    }
}
