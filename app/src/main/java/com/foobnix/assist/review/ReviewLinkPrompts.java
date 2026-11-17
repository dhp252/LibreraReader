package com.foobnix.assist.review;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one request of "Find connections": the open book's key ideas and the stored ideas (or
 * chapter lines) of up to {@link #MAX_BOOKS} other books of the library, as short numbered lists;
 * back come at most {@link #MAX_LINKS} connections between an idea of the open book and an idea of
 * another. Only stored idea lists go out, never any book's text. Every idea has a short ref
 * ("A3", "C1"); an answer naming a ref that was not sent is dropped. No Android here.
 */
public final class ReviewLinkPrompts {

    public static final int MAX_BOOKS = 8;
    public static final int MAX_LINKS = 3;
    /** Ideas of the open book sent at most, and of each other book. */
    public static final int MAX_MINE = 40;
    public static final int MAX_OTHER = 20;
    static final int MAX_IDEA = 240;
    static final int MAX_LINE = 240;

    private ReviewLinkPrompts() {
    }

    /** A stored idea of a book. */
    public static final class Idea {
        public final String chapterKey;
        public final String chapterTitle;
        public final String ideaId;
        public final String text;

        public Idea(String chapterKey, String chapterTitle, String ideaId, String text) {
            this.chapterKey = chapterKey == null ? "" : chapterKey;
            this.chapterTitle = chapterTitle == null ? "" : chapterTitle.trim();
            this.ideaId = ideaId == null ? "" : ideaId;
            this.text = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        }
    }

    /** A book and the ideas of it that are sent. */
    public static final class Book {
        public final String name;
        public final String title;
        public final String author;
        public final int year;
        public final List<Idea> ideas = new ArrayList<>();

        public Book(String name, String title, String author, int year, List<Idea> ideas) {
            this.name = name == null ? "" : name;
            this.title = title == null ? "" : title.trim();
            this.author = author == null ? "" : author.trim();
            this.year = Math.max(0, year);
            if (ideas != null) {
                this.ideas.addAll(ideas);
            }
        }
    }

    /**
     * At most {@code max} of the ideas, spread evenly over the list (so a long book is covered
     * from its first chapter to its last), without empty or repeated ones.
     */
    public static List<Idea> spread(List<Idea> ideas, int max) {
        final List<Idea> clean = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        if (ideas != null) {
            for (Idea i : ideas) {
                if (!i.text.isEmpty() && seen.add(ReviewSources.norm(i.text))) {
                    clean.add(i);
                }
            }
        }
        if (clean.size() <= max) {
            return clean;
        }
        final List<Idea> out = new ArrayList<>();
        for (int n = 0; n < max; n++) {
            out.add(clean.get((int) ((long) n * clean.size() / max)));
        }
        return out;
    }

    /** The letter of a book in the request: A is the open book, B, C... the others. */
    static char letter(int book) {
        return (char) ('A' + book);
    }

    public static String system(String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the ideas are written in" : answerLanguage.trim();
        return "You find connections between ideas of books in a reader's library. You get short numbered lists "
                + "of ideas: book A is the book the reader is in, the others are books they have. You are given only "
                + "these lists, no text of any book: do not quote a book.\n"
                + "Find at most " + MAX_LINKS + " strong connections between an idea of book A and an idea of ANOTHER "
                + "book: the same idea in different words (\"same\"), one idea contradicting the other "
                + "(\"disagrees\"), or one building on the other (\"extends\"). Prefer \"same\". Only real "
                + "connections a careful reader would accept; when none is strong, answer with an empty list. Never "
                + "connect two ideas of one book.\n"
                + "For each connection: \"a\": the ref of book A's idea, \"b\": the ref of the other book's idea "
                + "(copy the refs exactly as given, like A3 and C1), \"relation\", and \"why\": one line, at most 25 "
                + "words, on how the two connect, in plain words.\n"
                + "You may add \"also\": up to " + ReviewLink.MAX_ALSO + " works that are NOT in these lists and that "
                + "you know disagree with or extend the idea: {\"relation\":\"disagrees\" or \"extends\",\"work\":"
                + "\"author, title, and the year or chapter only if you are sure\",\"point\":\"its point in a few "
                + "of your own words\"}. This is your own knowledge and will be shown as such: name only works you "
                + "are confident exist and say this, never invent one, no quotations. Leave \"also\" out when "
                + "unsure.\n"
                + "Write \"why\" and \"point\" in " + lang + ". Answer with JSON only, no other text: {\"links\":[{"
                + "\"a\":\"A3\",\"b\":\"C1\",\"relation\":\"same\",\"why\":\"...\",\"also\":[]}]}";
    }

    public static String user(Book mine, List<Book> others) {
        final StringBuilder out = new StringBuilder();
        book(out, 0, mine, "the book the reader is in");
        for (int i = 0; i < others.size() && i < MAX_BOOKS; i++) {
            book(out, i + 1, others.get(i), "in the reader's library");
        }
        return out.toString();
    }

    private static void book(StringBuilder out, int n, Book b, String what) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append("Book ").append(letter(n)).append(" (").append(what).append("): ").append(b.title);
        if (!b.author.isEmpty()) {
            out.append(", ").append(b.author);
        }
        if (b.year > 0) {
            out.append(", ").append(b.year);
        }
        out.append('\n');
        for (int i = 0; i < b.ideas.size(); i++) {
            final String text = b.ideas.get(i).text;
            out.append(letter(n)).append(i + 1).append(". ")
                    .append(text.length() > MAX_IDEA ? text.substring(0, MAX_IDEA) : text).append('\n');
        }
    }

    /**
     * The connections of an answer: each between an idea of the open book and an idea of another
     * book, both among those sent; a ref that was not sent, a pair inside one book, a relation
     * that is none of the three and a pair named twice are dropped; {@link #MAX_LINKS} at most.
     * Never throws.
     *
     * @param today the local day the links are stamped with
     */
    public static List<ReviewLink> parse(String reply, Book mine, List<Book> others, String today) {
        final List<ReviewLink> out = new ArrayList<>();
        if (reply == null || mine == null) {
            return out;
        }
        try {
            final Map<String, Object[]> refs = new HashMap<>();
            refs(refs, 0, mine);
            for (int i = 0; others != null && i < others.size() && i < MAX_BOOKS; i++) {
                refs(refs, i + 1, others.get(i));
            }
            JSONArray list = null;
            final JSONObject root = ReviewPrompts.object(reply);
            if (root != null) {
                for (String name : new String[]{"links", "connections", "items"}) {
                    if (list == null) {
                        list = root.optJSONArray(name);
                    }
                }
                if (list == null && root.has("a")) {
                    list = new JSONArray().put(root);
                }
            } else {
                list = ReviewPrompts.array(reply);
            }
            final Set<String> seen = new HashSet<>();
            for (int i = 0; list != null && i < list.length() && out.size() < MAX_LINKS; i++) {
                final JSONObject o = list.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Object[] a = refs.get(ref(o.opt("a")));
                Object[] b = refs.get(ref(o.opt("b")));
                final String relation = ReviewLink.relation(o.optString("relation"));
                if (a == null || b == null || relation == null || a[0] == b[0]) {
                    continue;
                }
                if (a[0] != mine && b[0] == mine) {
                    // the ends the other way round
                    final Object[] swap = a;
                    a = b;
                    b = swap;
                }
                if (a[0] != mine) {
                    continue;
                }
                final ReviewLink link = new ReviewLink();
                link.a = side((Book) a[0], (Idea) a[1]);
                link.b = side((Book) b[0], (Idea) b[1]);
                link.id = ReviewLink.id(link.a, link.b);
                if (!seen.add(link.id)) {
                    continue;
                }
                link.relation = relation;
                link.why = line(o.optString("why", o.optString("reason", "")));
                link.day = today == null ? "" : today;
                also(link, o.optJSONArray("also"));
                out.add(link);
            }
        } catch (Exception e) {
            // an answer that is not what was asked for: no connections
        }
        return out;
    }

    private static void refs(Map<String, Object[]> refs, int n, Book b) {
        for (int i = 0; i < b.ideas.size(); i++) {
            refs.put(String.valueOf(letter(n)) + (i + 1), new Object[]{b, b.ideas.get(i)});
        }
    }

    /** "A3" of "a3", "[A3]", "A 3", "A3." or {"ref": "A3"}. */
    static String ref(Object v) {
        final Object x = v instanceof JSONObject ? ((JSONObject) v).opt("ref") : v;
        return x == null ? "" : String.valueOf(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static ReviewLink.Side side(Book b, Idea i) {
        final ReviewLink.Side s = new ReviewLink.Side();
        s.book = b.name;
        s.bookTitle = b.title;
        s.author = b.author;
        s.year = b.year;
        s.chapterKey = i.chapterKey;
        s.chapterTitle = i.chapterTitle;
        s.ideaId = i.ideaId;
        s.idea = i.text;
        return s;
    }

    /** The works outside the library: "work: point", never with quotation marks around the point. */
    private static void also(ReviewLink link, JSONArray list) {
        for (int i = 0; list != null && i < list.length() && link.also.size() < ReviewLink.MAX_ALSO; i++) {
            final JSONObject o = list.optJSONObject(i);
            final String relation = o == null ? null : ReviewLink.relation(o.optString("relation"));
            if (relation == null || ReviewLink.SAME.equals(relation)) {
                continue;
            }
            final String work = unquote(line(o.optString("work", o.optString("source", ""))));
            final String point = unquote(line(o.optString("point", o.optString("text", ""))));
            if (!work.isEmpty() && !point.isEmpty()) {
                link.also.add(new ReviewLink.Outside(relation, work + ": " + point));
            }
        }
    }

    /** One line, cut to a length. */
    static String line(String s) {
        final String one = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return one.length() > MAX_LINE ? one.substring(0, MAX_LINE).trim() + "…" : one;
    }

    /**
     * A point without quotation marks: the model has no text of the work, so nothing it writes
     * about it may look like a quote.
     */
    static String unquote(String s) {
        return s.replaceAll("[\"“”„«»]", "").replaceAll("(^|\\s)['‘’]|['‘’](\\s|$|[.,;:!?])", "$1$2").trim();
    }
}
