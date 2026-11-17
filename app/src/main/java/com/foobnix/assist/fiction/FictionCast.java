package com.foobnix.assist.fiction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The who's who of a story as far as the reader has read ({@code fiction:cast} in the book's
 * AssistStore file): the people, how they are tied, each one's story so far, and the place in the
 * book all of it was built from ({@link #mark}). Free of Android.
 * <p>
 * Names keep the book's spelling. A chapter is kept as its number in the chapter list when the
 * cast was built and its key ({@link FictionChapters}). Anything the model places in a chapter
 * after the last one it was sent is dropped when its answer is read: it can only come from
 * outside the text.
 */
public final class FictionCast {

    public static final String KEY = "fiction:cast";

    static final int MAX_PEOPLE = 120;
    static final int MAX_ALIASES = 8;
    static final int MAX_MOMENTS = 40;
    static final int MAX_RELATIONS = 40;

    public static final String FEMALE = "f";
    public static final String MALE = "m";

    /** One line of a person's story so far. */
    public static final class Moment {
        /** Number of its chapter (from 1) when stored, and that chapter's key. */
        public final int ch;
        public final String key;
        public final String line;

        public Moment(int ch, String key, String line) {
            this.ch = ch;
            this.key = key == null ? "" : key;
            this.line = line;
        }
    }

    public static final class Person {
        public String id = "";
        /** As the book spells it. */
        public String name = "";
        public final List<String> aliases = new ArrayList<>();
        /** {@link #FEMALE}, {@link #MALE}, or "" when the text does not say. */
        public String gender = "";
        /** Who they are, in a few words. */
        public String role = "";
        /** Number and key of the chapter they are first met in. */
        public int first;
        public String firstKey = "";
        /** Who they are and what has happened to them, as of the cast's mark. */
        public String asOf = "";
        /** Where they are and what they are doing at the mark. */
        public String now = "";
        public String group = "";
        public boolean minor;
        /** The answer said whether they are minor (an update may leave it out). */
        boolean minorGiven;
        public final List<Moment> moments = new ArrayList<>();

        /** "PF" for "Phileas Fogg", "A" for "Aouda". */
        public String initials() {
            return FictionCast.initials(name);
        }
    }

    /** A tie between two people, or a person and a group. */
    public static final class Relation {
        /** A person's id. */
        public final String a;
        /** A person's id, or the name of a group. */
        public final String b;
        public final String label;
        /** Number and key of the chapter the tie begins in; 0 when the answer did not say. */
        public final int ch;
        public final String key;

        public Relation(String a, String b, String label, int ch, String key) {
            this.a = a;
            this.b = b;
            this.label = label;
            this.ch = ch;
            this.key = key == null ? "" : key;
        }
    }

    /** How far into the book the cast goes. */
    public FictionMark mark = new FictionMark();
    public String model = "";
    /** False when the model said the text is no story with people in it. */
    public boolean story = true;
    public final List<Person> people = new ArrayList<>();
    public final List<Relation> relations = new ArrayList<>();

    public boolean isEmpty() {
        return people.isEmpty();
    }

    public Person person(String id) {
        if (id != null) {
            for (Person p : people) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the stored item

    public JSONObject toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("v", 1);
            mark.write(o);
            o.put("model", model);
            final JSONArray ps = new JSONArray();
            for (Person p : people) {
                final JSONObject j = new JSONObject();
                j.put("id", p.id).put("name", p.name).put("aliases", new JSONArray(p.aliases)).put("g", p.gender)
                        .put("role", p.role).put("first", p.first).put("firstKey", p.firstKey).put("asOf", p.asOf)
                        .put("now", p.now).put("group", p.group).put("minor", p.minor);
                final JSONArray ms = new JSONArray();
                for (Moment m : p.moments) {
                    ms.put(new JSONObject().put("ch", m.ch).put("key", m.key).put("line", m.line));
                }
                j.put("moments", ms);
                ps.put(j);
            }
            o.put("people", ps);
            final JSONArray rs = new JSONArray();
            for (Relation r : relations) {
                rs.put(new JSONObject().put("a", r.a).put("b", r.b).put("label", r.label).put("ch", r.ch)
                        .put("key", r.key));
            }
            o.put("relations", rs);
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** The stored cast; empty (never null) when there is none or it cannot be read. */
    public static FictionCast fromJson(JSONObject o) {
        final FictionCast cast = new FictionCast();
        if (o == null) {
            return cast;
        }
        cast.mark = FictionMark.read(o);
        cast.model = o.optString("model", "");
        final JSONArray ps = o.optJSONArray("people");
        for (int i = 0; ps != null && i < ps.length(); i++) {
            final JSONObject j = ps.optJSONObject(i);
            if (j == null) {
                continue;
            }
            final Person p = new Person();
            p.name = clean(j.optString("name"));
            p.id = slug(j.optString("id"));
            if (p.name.isEmpty() || p.id.isEmpty() || cast.person(p.id) != null) {
                continue;
            }
            addAliases(p, j.opt("aliases"));
            p.gender = gender(j.optString("g"));
            p.role = clean(j.optString("role"));
            p.first = Math.max(0, j.optInt("first"));
            p.firstKey = j.optString("firstKey", "");
            p.asOf = clean(j.optString("asOf"));
            p.now = clean(j.optString("now"));
            p.group = clean(j.optString("group"));
            p.minor = j.optBoolean("minor");
            final JSONArray ms = j.optJSONArray("moments");
            for (int k = 0; ms != null && k < ms.length(); k++) {
                final JSONObject m = ms.optJSONObject(k);
                final String line = m == null ? "" : clean(m.optString("line"));
                if (!line.isEmpty()) {
                    p.moments.add(new Moment(Math.max(0, m.optInt("ch")), m.optString("key", ""), line));
                }
            }
            cast.people.add(p);
        }
        final JSONArray rs = o.optJSONArray("relations");
        for (int i = 0; rs != null && i < rs.length(); i++) {
            final JSONObject j = rs.optJSONObject(i);
            if (j == null) {
                continue;
            }
            final String a = j.optString("a", "");
            final String b = j.optString("b", "");
            final String label = clean(j.optString("label"));
            if (!a.isEmpty() && !b.isEmpty() && !label.isEmpty()) {
                cast.relations.add(new Relation(a, b, label, Math.max(0, j.optInt("ch")), j.optString("key", "")));
            }
        }
        return cast;
    }

    // ------------------------------------------------------------------ the model's answer

    /**
     * The model's answer to a cast request (one JSON object, maybe in a code fence or with words
     * around it), read tolerantly. Null when it cannot be read.
     *
     * @param chapters the book's chapters, for the key of a chapter number
     * @param firstN   number of the first chapter of the text that was sent
     * @param lastN    number of the last chapter of the text that was sent: a person, moment or
     *                 tie placed after it is dropped (the text cannot have said it)
     */
    public static FictionCast parse(String answer, FictionChapters chapters, int firstN, int lastN) {
        final JSONObject o = object(answer);
        if (o == null) {
            return null;
        }
        final FictionCast cast = new FictionCast();
        final Object story = o.opt("story");
        if (Boolean.FALSE.equals(story) || "false".equalsIgnoreCase(String.valueOf(story))) {
            cast.story = false;
            return cast;
        }
        JSONArray ps = o.optJSONArray("people");
        if (ps == null) {
            ps = o.optJSONArray("cast");
        }
        if (ps == null) {
            ps = o.optJSONArray("characters");
        }
        for (int i = 0; ps != null && i < ps.length() && cast.people.size() < MAX_PEOPLE; i++) {
            final JSONObject j = ps.optJSONObject(i);
            if (j == null) {
                continue;
            }
            final Person p = new Person();
            p.name = clean(j.optString("name"));
            p.id = slug(j.optString("id"));
            if (p.id.isEmpty()) {
                p.id = slug(p.name);
            }
            if (p.id.isEmpty() || cast.person(p.id) != null) {
                continue;
            }
            final int first = number(j, "first", "first_met", "ch", "chapter");
            if (first > lastN) {
                // first met after the text ends: not from the text
                continue;
            }
            p.first = first < 1 ? 0 : first;
            p.firstKey = chapters.key(p.first);
            addAliases(p, j.opt("aliases"));
            p.gender = gender(j.optString("gender", j.optString("g")));
            p.role = clean(j.optString("role"));
            p.asOf = clean(first(j, "as_of", "asOf", "description", "card"));
            p.now = clean(first(j, "now", "right_now", "rightNow"));
            p.group = clean(j.optString("group"));
            if (j.has("minor") && !j.isNull("minor")) {
                p.minorGiven = true;
                p.minor = j.optBoolean("minor", "true".equalsIgnoreCase(j.optString("minor")));
            }
            final JSONArray ms = j.optJSONArray("moments");
            for (int k = 0; ms != null && k < ms.length() && p.moments.size() < MAX_MOMENTS; k++) {
                final JSONObject m = ms.optJSONObject(k);
                final String line = clean(m == null ? ms.optString(k) : first(m, "line", "text"));
                if (line.isEmpty()) {
                    continue;
                }
                int ch = m == null ? 0 : number(m, "ch", "chapter");
                if (ch > lastN) {
                    continue;
                }
                if (ch < 1) {
                    // not said: taken as the latest it can be, so it is never shown too early
                    ch = lastN;
                }
                p.moments.add(new Moment(ch, chapters.key(ch), line));
            }
            cast.people.add(p);
        }
        final JSONArray rs = o.optJSONArray("relations");
        for (int i = 0; rs != null && i < rs.length() && cast.relations.size() < MAX_RELATIONS; i++) {
            final JSONObject j = rs.optJSONObject(i);
            if (j == null) {
                continue;
            }
            final String a = slug(j.optString("a"));
            final String rawB = clean(j.optString("b"));
            final String label = clean(j.optString("label"));
            int ch = number(j, "ch", "chapter");
            if (a.isEmpty() || rawB.isEmpty() || label.isEmpty() || ch > lastN) {
                continue;
            }
            if (ch < 1) {
                ch = lastN;
            }
            // the other end is a person when it is the id of one, else a group's name as given
            final String b = cast.person(slug(rawB)) != null ? slug(rawB) : rawB;
            cast.relations.add(new Relation(a, b, label, ch, chapters.key(ch)));
        }
        // an answer that says nothing about first chapters: the latest they can be
        for (Person p : cast.people) {
            if (p.first < 1) {
                p.first = Math.max(firstN, lastN);
                p.firstKey = chapters.key(p.first);
            }
        }
        return cast;
    }

    // ------------------------------------------------------------------ an update

    /**
     * Takes in what a later part of the text gave: new people are added; a person already here
     * (same id, or the same name) gets the new description and situation, new aliases and new
     * moments, and keeps the chapter they were first met in. Ties are added, or relabelled when
     * the pair is already tied. The mark is not touched: the caller sets it.
     */
    public void merge(FictionCast update) {
        if (update == null) {
            return;
        }
        for (Person u : update.people) {
            Person p = person(u.id);
            if (p == null) {
                p = byName(u.name);
            }
            if (p == null) {
                if (u.name.isEmpty() || people.size() >= MAX_PEOPLE) {
                    continue;
                }
                p = new Person();
                p.id = unique(u.id);
                p.name = u.name;
                p.first = u.first;
                p.firstKey = u.firstKey;
                p.minor = u.minor;
                people.add(p);
            } else {
                if (!u.name.isEmpty() && !same(u.name, p.name)) {
                    // the book now names them: the earlier name stays as an alias
                    final String old = p.name;
                    p.name = u.name;
                    addAlias(p, old);
                }
                if (u.first >= 1 && (p.first < 1 || u.first < p.first)) {
                    p.first = u.first;
                    p.firstKey = u.firstKey;
                }
                if (u.minorGiven) {
                    p.minor = u.minor;
                }
            }
            for (String alias : u.aliases) {
                addAlias(p, alias);
            }
            if (!u.gender.isEmpty()) {
                p.gender = u.gender;
            }
            if (!u.role.isEmpty()) {
                p.role = u.role;
            }
            if (!u.asOf.isEmpty()) {
                p.asOf = u.asOf;
            }
            if (!u.now.isEmpty()) {
                p.now = u.now;
            }
            if (!u.group.isEmpty()) {
                p.group = u.group;
            }
            for (Moment m : u.moments) {
                boolean have = false;
                for (Moment old : p.moments) {
                    if (old.ch == m.ch && same(old.line, m.line)) {
                        have = true;
                        break;
                    }
                }
                if (!have) {
                    p.moments.add(m);
                }
            }
            while (p.moments.size() > MAX_MOMENTS) {
                // the oldest lines go first
                p.moments.remove(0);
            }
        }
        for (Relation u : update.relations) {
            final String a = resolve(update, u.a);
            final String b = resolve(update, u.b);
            if (person(a) == null) {
                continue;
            }
            int at = -1;
            for (int i = 0; i < relations.size(); i++) {
                final Relation r = relations.get(i);
                if ((r.a.equals(a) && same(r.b, b)) || (same(r.b, a) && r.a.equals(b))) {
                    at = i;
                    break;
                }
            }
            if (at >= 0) {
                final Relation old = relations.get(at);
                // the tie began where it was first seen
                relations.set(at, new Relation(old.a, old.b, u.label, old.ch >= 1 ? old.ch : u.ch,
                        old.ch >= 1 ? old.key : u.key));
            } else if (relations.size() < MAX_RELATIONS) {
                relations.add(new Relation(a, b, u.label, u.ch, u.key));
            }
        }
    }

    /** An end of a tie in an update, as this cast knows it: the id a merged person has here. */
    private String resolve(FictionCast update, String end) {
        if (person(end) != null) {
            return end;
        }
        final Person in = update.person(slug(end));
        if (in == null && person(slug(end)) != null) {
            return slug(end);
        }
        if (in != null) {
            final Person here = byName(in.name);
            if (here != null) {
                return here.id;
            }
        }
        final Person named = byName(end);
        return named != null ? named.id : end;
    }

    /** The person carrying a name (their name or an alias, any case), or null. */
    public Person byName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (Person p : people) {
            if (same(p.name, name)) {
                return p;
            }
        }
        for (Person p : people) {
            for (String alias : p.aliases) {
                if (same(alias, name)) {
                    return p;
                }
            }
        }
        return null;
    }

    private String unique(String id) {
        final String base = id.isEmpty() ? "p" : id;
        String out = base;
        for (int i = 2; person(out) != null; i++) {
            out = base + "-" + i;
        }
        return out;
    }

    // ------------------------------------------------------------------ small things

    private static void addAliases(Person p, Object aliases) {
        if (aliases instanceof JSONArray) {
            final JSONArray a = (JSONArray) aliases;
            for (int i = 0; i < a.length(); i++) {
                addAlias(p, a.optString(i));
            }
        } else if (aliases instanceof String) {
            for (String s : ((String) aliases).split("[,;]")) {
                addAlias(p, s);
            }
        }
    }

    private static void addAlias(Person p, String alias) {
        final String a = clean(alias);
        if (a.isEmpty() || same(a, p.name) || p.aliases.size() >= MAX_ALIASES) {
            return;
        }
        for (String have : p.aliases) {
            if (same(have, a)) {
                return;
            }
        }
        p.aliases.add(a);
    }

    static boolean same(String a, String b) {
        return a != null && b != null
                && a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    private static String gender(String g) {
        final String s = g == null ? "" : g.trim().toLowerCase(Locale.ROOT);
        if (s.equals("f") || s.equals("female") || s.equals("woman") || s.equals("she")) {
            return FEMALE;
        }
        if (s.equals("m") || s.equals("male") || s.equals("man") || s.equals("he")) {
            return MALE;
        }
        return "";
    }

    /** One or two letters for a name: "Phileas Fogg" = "PF", "Aouda" = "A", "Mr. Fogg" = "F". */
    public static String initials(String name) {
        final StringBuilder out = new StringBuilder();
        final StringBuilder all = new StringBuilder();
        if (name != null) {
            for (String part : name.trim().split("[\\s,]+")) {
                if (part.isEmpty() || !Character.isLetterOrDigit(part.codePointAt(0))) {
                    continue;
                }
                final int c = Character.toUpperCase(part.codePointAt(0));
                if (all.length() < 2) {
                    all.appendCodePoint(c);
                }
                // titles and articles are no part of the initials when a name follows
                if (out.length() < 2 && !FictionNames.isTitle(part) && Character.isUpperCase(part.codePointAt(0))) {
                    out.appendCodePoint(c);
                }
            }
        }
        return out.length() > 0 ? out.toString() : all.toString();
    }

    /** A short lowercase id: letters and digits, the rest as single hyphens. */
    static String slug(String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder();
        boolean gap = false;
        for (int i = 0; i < s.length() && out.length() < 40; ) {
            final int c = s.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isLetterOrDigit(c)) {
                if (gap && out.length() > 0) {
                    out.append('-');
                }
                out.appendCodePoint(Character.toLowerCase(c));
                gap = false;
            } else {
                gap = true;
            }
        }
        return out.toString();
    }

    /** No Markdown emphasis or stray whitespace in a shown string. */
    static String clean(String s) {
        if (s == null || "null".equals(s)) {
            return "";
        }
        return s.replace("**", "").replace("__", "").replaceAll("\\s+", " ").trim();
    }

    /** The first of the fields that holds text. */
    static String first(JSONObject o, String... names) {
        for (String n : names) {
            final String v = o.optString(n, "");
            if (!v.trim().isEmpty() && !"null".equals(v)) {
                return v;
            }
        }
        return "";
    }

    /** The first of the fields that holds a number ("12", 12, "Ch. 12"); 0 when none does. */
    static int number(JSONObject o, String... names) {
        for (String n : names) {
            final Object v = o.opt(n);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String) {
                final String digits = ((String) v).replaceAll("[^0-9]", " ").trim();
                if (!digits.isEmpty()) {
                    try {
                        return Integer.parseInt(digits.split("\\s+")[0]);
                    } catch (NumberFormatException e) {
                        // not a number: the next field
                    }
                }
            }
        }
        return 0;
    }

    /** The JSON object of an answer: the outermost braces, or null. */
    static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int start = answer.indexOf('{');
        final int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return new JSONObject(answer.substring(start, end + 1));
        } catch (JSONException e) {
            return null;
        }
    }
}
