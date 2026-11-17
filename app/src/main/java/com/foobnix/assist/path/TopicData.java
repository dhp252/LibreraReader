package com.foobnix.assist.path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "One topic, several books": the merged reading path through the chapters of 2 to 4 books that
 * are about one topic, what the books agree on (said once), where they disagree (each author's
 * position) and what is mostly in one book. Kept in the library's assist file under
 * {@code topic:<slug of the topic>}; books are named by file name, chapters by chapter key.
 */
public final class TopicData {

    public static final String PREFIX = "topic:";
    public static final int MIN_BOOKS = 2;
    public static final int MAX_BOOKS = 4;
    /** A merged path is never longer than this. */
    public static final int MAX_STEPS = 12;

    public String topic = "";
    public final List<Book> books = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final List<Agree> agree = new ArrayList<>();
    public final List<Disagree> disagree = new ArrayList<>();
    public final List<Only> only = new ArrayList<>();
    public String model = "";
    public long made;

    /** One of the books, as the library names it. */
    public static final class Book {
        /** The file name: what the book's data is stored under on every device. */
        public final String name;
        public final String title;
        public final String author;
        public final int year;

        public Book(String name, String title, String author, int year) {
            this.name = name == null ? "" : name;
            this.title = title == null ? "" : title.trim();
            this.author = author == null ? "" : author.trim();
            this.year = year;
        }

        /** What the book is called for short beside a position: the author's last name, else the title. */
        public String shortName() {
            if (author.isEmpty()) {
                return title.isEmpty() ? name : title;
            }
            // "Smith, Adam" and "Adam Smith" both give Smith
            final String first = author.split("[,;&]")[0].trim();
            final String one = author.contains(",") ? first : first.substring(first.lastIndexOf(' ') + 1);
            return one.isEmpty() ? author : one;
        }

        /** The letter on the book's badge. */
        public String letter() {
            final String s = shortName();
            for (int i = 0; i < s.length(); i++) {
                if (Character.isLetterOrDigit(s.charAt(i))) {
                    return s.substring(i, i + 1).toUpperCase(Locale.ROOT);
                }
            }
            return "?";
        }

        /** "Smith · 1776", or what of it is known. */
        public String byline() {
            final String who = author.isEmpty() ? "" : shortName();
            return year > 0 ? (who.isEmpty() ? String.valueOf(year) : who + " · " + year) : who;
        }
    }

    /** One chapter of the merged path. */
    public static final class Step {
        /** Index in {@link #books}. */
        public final int book;
        public final String key;
        public String title = "";
        public String code = "";
        public String why = "";
        /** What the chapter is about (its skeleton line). */
        public String line = "";
        public long chars;

        public Step(int book, String key) {
            this.book = book;
            this.key = key;
        }
    }

    /** A point the books agree on, said once. */
    public static final class Agree {
        public String point = "";
        /** "Examples kept: Smith's pins, Marx's watches." */
        public String note = "";
        /** Indexes in {@link #books}. */
        public final List<Integer> books = new ArrayList<>();
    }

    /** A question the books disagree on. */
    public static final class Disagree {
        public String question = "";
        public final List<Position> positions = new ArrayList<>();
    }

    public static final class Position {
        public final int book;
        public final String says;

        public Position(int book, String says) {
            this.book = book;
            this.says = says;
        }
    }

    /** A point found mostly in one book. */
    public static final class Only {
        public final int book;
        public final String point;

        public Only(int book, String point) {
            this.book = book;
            this.point = point;
        }
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** The AssistStore key of a topic in the library file. */
    public static String key(String topic) {
        return PREFIX + slug(topic);
    }

    /** A topic as a key: lower case, letters and digits of any script, "-" between words, 60 characters at most. */
    public static String slug(String topic) {
        final StringBuilder out = new StringBuilder();
        final String t = topic == null ? "" : topic.toLowerCase(Locale.ROOT);
        for (int i = 0; i < t.length() && out.length() < 60; ) {
            final int c = t.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isLetterOrDigit(c)) {
                out.appendCodePoint(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "topic" : out.toString();
    }

    /** How many chapters of the path are in a book. */
    public int stepsOf(int book) {
        int n = 0;
        for (Step s : steps) {
            if (s.book == book) {
                n++;
            }
        }
        return n;
    }

    /** "I.1, I.3, V.1": the codes of a book's chapters on the path; "" when any has none. */
    public String codesOf(int book) {
        final StringBuilder out = new StringBuilder();
        for (Step s : steps) {
            if (s.book != book) {
                continue;
            }
            if (s.code.isEmpty()) {
                return "";
            }
            out.append(out.length() > 0 ? ", " : "").append(s.code);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ from the model's answer

    /**
     * The model's answer laid on the books' chapter lists: a step naming a book or chapter that is
     * not there is dropped, a chapter counts once; a point of agreement needs two of the books, a
     * disagreement two positions.
     *
     * @param lists the chapter list of each book, in the order of {@code books}
     */
    public static TopicData fromAnswer(String topic, TopicPrompts.Answer a, List<Book> books,
                                       List<List<PathChapters.Entry>> lists, String model, long now) {
        final TopicData d = new TopicData();
        d.topic = topic == null ? "" : topic.trim().replaceAll("\\s+", " ");
        d.books.addAll(books);
        final Set<String> seen = new HashSet<>();
        for (TopicPrompts.Step s : a.steps) {
            if (s.book < 0 || s.book >= lists.size() || s.chapter < 0 || s.chapter >= lists.get(s.book).size()
                    || d.steps.size() >= MAX_STEPS) {
                continue;
            }
            final PathChapters.Entry e = lists.get(s.book).get(s.chapter);
            if (!seen.add(s.book + "|" + e.key)) {
                continue;
            }
            final Step step = new Step(s.book, e.key);
            step.title = e.title;
            step.code = e.code;
            step.why = PathText.clip(s.why, 140);
            step.line = e.line;
            step.chars = e.chars;
            d.steps.add(step);
        }
        for (Agree g : a.agree) {
            final Agree kept = new Agree();
            kept.point = PathText.clip(g.point, 200);
            kept.note = PathText.clip(g.note, 200);
            for (Integer b : g.books) {
                if (b != null && b >= 0 && b < books.size() && !kept.books.contains(b)) {
                    kept.books.add(b);
                }
            }
            if (!kept.point.isEmpty() && kept.books.size() >= 2 && d.agree.size() < 3) {
                d.agree.add(kept);
            }
        }
        for (Disagree g : a.disagree) {
            final Disagree kept = new Disagree();
            kept.question = PathText.clip(g.question, 200);
            final Set<Integer> said = new HashSet<>();
            for (Position p : g.positions) {
                if (p.book >= 0 && p.book < books.size() && !p.says.isEmpty() && said.add(p.book)) {
                    kept.positions.add(new Position(p.book, PathText.clip(p.says, 300)));
                }
            }
            if (!kept.question.isEmpty() && kept.positions.size() >= 2 && d.disagree.size() < 3) {
                d.disagree.add(kept);
            }
        }
        for (Only o : a.only) {
            if (o.book >= 0 && o.book < books.size() && !o.point.isEmpty() && d.only.size() < 4) {
                d.only.add(new Only(o.book, PathText.clip(o.point, 60)));
            }
        }
        d.model = model == null ? "" : model;
        d.made = now;
        return d;
    }

    // ------------------------------------------------------------------ stored

    public JSONObject toJson() throws JSONException {
        final JSONArray bookList = new JSONArray();
        for (Book b : books) {
            bookList.put(new JSONObject().put("name", b.name).put("title", b.title).put("author", b.author)
                    .put("year", b.year));
        }
        final JSONArray path = new JSONArray();
        for (Step s : steps) {
            path.put(new JSONObject().put("b", s.book).put("key", s.key).put("title", s.title).put("code", s.code)
                    .put("why", s.why).put("line", s.line).put("chars", s.chars));
        }
        final JSONArray agreeList = new JSONArray();
        for (Agree g : agree) {
            agreeList.put(new JSONObject().put("point", g.point).put("note", g.note).put("books", new JSONArray(g.books)));
        }
        final JSONArray disagreeList = new JSONArray();
        for (Disagree g : disagree) {
            final JSONArray positions = new JSONArray();
            for (Position p : g.positions) {
                positions.put(new JSONObject().put("b", p.book).put("says", p.says));
            }
            disagreeList.put(new JSONObject().put("question", g.question).put("positions", positions));
        }
        final JSONArray onlyList = new JSONArray();
        for (Only o : only) {
            onlyList.put(new JSONObject().put("b", o.book).put("point", o.point));
        }
        return new JSONObject().put("topic", topic).put("books", bookList).put("path", path).put("agree", agreeList)
                .put("disagree", disagreeList).put("only", onlyList).put("model", model).put("made", made);
    }

    /** A stored topic; empty when {@code o} is null. */
    public static TopicData fromJson(JSONObject o) {
        final TopicData d = new TopicData();
        if (o == null) {
            return d;
        }
        d.topic = o.optString("topic", "");
        final JSONArray bookList = o.optJSONArray("books");
        for (int i = 0; bookList != null && i < bookList.length(); i++) {
            final JSONObject b = bookList.optJSONObject(i);
            if (b != null) {
                d.books.add(new Book(b.optString("name", ""), b.optString("title", ""), b.optString("author", ""),
                        b.optInt("year", 0)));
            }
        }
        final JSONArray path = o.optJSONArray("path");
        for (int i = 0; path != null && i < path.length(); i++) {
            final JSONObject s = path.optJSONObject(i);
            final int book = s == null ? -1 : s.optInt("b", -1);
            if (s == null || book < 0 || book >= d.books.size() || s.optString("key", "").isEmpty()) {
                continue;
            }
            final Step step = new Step(book, s.optString("key", ""));
            step.title = s.optString("title", "");
            step.code = s.optString("code", "");
            step.why = s.optString("why", "");
            step.line = s.optString("line", "");
            step.chars = s.optLong("chars", 0);
            d.steps.add(step);
        }
        final JSONArray agreeList = o.optJSONArray("agree");
        for (int i = 0; agreeList != null && i < agreeList.length(); i++) {
            final JSONObject g = agreeList.optJSONObject(i);
            if (g == null) {
                continue;
            }
            final Agree a = new Agree();
            a.point = g.optString("point", "");
            a.note = g.optString("note", "");
            final JSONArray in = g.optJSONArray("books");
            for (int j = 0; in != null && j < in.length(); j++) {
                final int b = in.optInt(j, -1);
                if (b >= 0 && b < d.books.size()) {
                    a.books.add(b);
                }
            }
            d.agree.add(a);
        }
        final JSONArray disagreeList = o.optJSONArray("disagree");
        for (int i = 0; disagreeList != null && i < disagreeList.length(); i++) {
            final JSONObject g = disagreeList.optJSONObject(i);
            if (g == null) {
                continue;
            }
            final Disagree dis = new Disagree();
            dis.question = g.optString("question", "");
            final JSONArray positions = g.optJSONArray("positions");
            for (int j = 0; positions != null && j < positions.length(); j++) {
                final JSONObject p = positions.optJSONObject(j);
                final int b = p == null ? -1 : p.optInt("b", -1);
                if (b >= 0 && b < d.books.size()) {
                    dis.positions.add(new Position(b, p.optString("says", "")));
                }
            }
            d.disagree.add(dis);
        }
        final JSONArray onlyList = o.optJSONArray("only");
        for (int i = 0; onlyList != null && i < onlyList.length(); i++) {
            final JSONObject x = onlyList.optJSONObject(i);
            final int b = x == null ? -1 : x.optInt("b", -1);
            if (b >= 0 && b < d.books.size()) {
                d.only.add(new Only(b, x.optString("point", "")));
            }
        }
        d.model = o.optString("model", "");
        d.made = o.optLong("made", 0);
        return d;
    }
}
