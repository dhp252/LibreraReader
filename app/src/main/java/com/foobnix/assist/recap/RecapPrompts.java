package com.foobnix.assist.recap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the re-entry recap asks of the model and reading its answer into sections, free of
 * Android. The model gets only text before the reader's page ({@link RecapLogic#excerpts}) and
 * decides itself whether the book is fiction (story so far by chapter, who's who) or non-fiction
 * (the argument so far, key ideas). One answer serves every length: each story line carries the
 * level (30 sec, 2 min, 5 min) it first shows at.
 */
public final class RecapPrompts {

    public static final String FICTION = "fiction";
    public static final String NONFICTION = "nonfiction";

    static final int MAX_STORY = 16;
    static final int MAX_CAST = 8;
    static final int MAX_WARMUP = 2;

    private RecapPrompts() {
    }

    /** @param answerLanguage the reader's choice (AI settings); empty: the language of the book */
    public static String system(String answerLanguage, String bucket) {
        final boolean brief = !RecapLogic.LONG.equals(bucket);
        return "You write a re-entry recap for someone coming back to a book they are reading after time away. "
                + "Write in " + language(answerLanguage) + ". "
                + "Use only the text you are given: it is the book up to where the reader stopped, and nothing after it. "
                + "Never reveal, guess or hint at anything that comes later, and never use outside knowledge of this "
                + "book, even if you know it. Some chapters are given only by their opening; the pages right before "
                + "where the reader stopped are given whole.\n"
                + "First decide whether the book is fiction (a novel, stories, a play) or non-fiction.\n"
                + "Answer with one JSON object only, no Markdown around it, with these fields:\n"
                + "- \"kind\": \"fiction\" or \"nonfiction\".\n"
                + "- \"last_time\": one sentence on what the reader read last, the latest scene or point before "
                + "where they stopped.\n"
                + "- \"stopped_at\": one short sentence placing exactly where they stopped (the last lines given).\n"
                + "- \"story\": fiction: the story so far; non-fiction: the argument so far. "
                + (brief ? "3 to 8" : "6 to " + MAX_STORY) + " items in book order, each {\"text\": one or two "
                + "sentences, \"chapter\": a short reference to where it happens as the book names its parts, such "
                + "as \"Ch. 3\" or \"Ch. 4–14\", \"level\": 1, 2 or 3}. Level 1: the 3 or 4 items a 30-second recap "
                + "needs; level 2: what a 2-minute recap adds; level 3: the details a 5-minute recap adds.\n"
                + "- \"cast\": fiction: the people who matter so far; non-fiction: the key ideas or terms so far. At "
                + "most " + MAX_CAST + ", each {\"name\": a short name, \"initials\": 1 or 2 letters, \"card\": two "
                + "or three sentences on who they are, or what it means, as of where the reader stopped, nothing "
                + "later}.\n"
                + "- \"status\": {\"headline\": a few words on where things stand (fiction: the journey, the "
                + "conflict or the stakes; non-fiction: the question the book is working on), \"detail\": one "
                + "sentence}.\n"
                + "- \"warmup\": " + MAX_WARMUP + " questions that wake the memory up before reading on, each "
                + "{\"q\": the question, \"a\": a short answer from the text given}.\n"
                + "Plain text in every string: no Markdown, no page numbers.";
    }

    /**
     * @param page      1-based page the reader stopped at; the excerpts end before it
     * @param chapter   title of the chapter the reader is in, or empty
     */
    public static String user(String bookTitle, String chapter, int page, int pageCount, int daysAway,
                              List<RecapLogic.Excerpt> excerpts) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        out.append("The reader stopped at page ").append(page).append(" of ").append(pageCount);
        if (chapter != null && !chapter.trim().isEmpty()) {
            out.append(", in \"").append(attr(chapter)).append('"');
        }
        out.append(", and has been away ").append(daysAway).append(daysAway == 1 ? " day" : " days").append(".\n");
        out.append("The text they have read, in order (only openings of the earlier chapters):\n\n");
        if (excerpts != null) {
            for (RecapLogic.Excerpt e : excerpts) {
                out.append("<excerpt chapter=\"").append(attr(e.chapter)).append("\" pages=\"").append(e.firstPage);
                if (e.lastPage != e.firstPage) {
                    out.append('-').append(e.lastPage);
                }
                out.append("\" sent=\"").append(e.whole ? "whole" : "opening only").append("\">\n")
                        .append(e.text).append("\n</excerpt>\n");
            }
        }
        return out.toString();
    }

    private static String attr(String s) {
        return s == null ? "" : s.replace('"', '\'').replace('\n', ' ').replace('<', '(').replace('>', ')').trim();
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in"
                : answerLanguage.trim();
    }

    // ------------------------------------------------------------------ the answer

    /** A line of the story (or argument) so far. */
    public static final class Line {
        public final String text;
        public final String chapter;
        public final int level;

        public Line(String text, String chapter, int level) {
            this.text = text;
            this.chapter = chapter;
            this.level = level;
        }
    }

    /** A who's-who chip (fiction) or key idea (non-fiction), with its spoiler-free card. */
    public static final class Person {
        public final String name;
        public final String initials;
        public final String card;

        public Person(String name, String initials, String card) {
            this.name = name;
            this.initials = initials;
            this.card = card;
        }
    }

    public static final class Question {
        public final String q;
        public final String a;

        public Question(String q, String a) {
            this.q = q;
            this.a = a;
        }
    }

    /** A recap in sections. */
    public static final class Recap {
        public String kind = FICTION;
        public String lastTime = "";
        public String stoppedAt = "";
        public final List<Line> story = new ArrayList<>();
        public final List<Person> cast = new ArrayList<>();
        public String headline = "";
        public String detail = "";
        public final List<Question> warmup = new ArrayList<>();

        public boolean isFiction() {
            return !NONFICTION.equals(kind);
        }

        /** The story lines a length shows: those of its level and below. */
        public List<Line> lines(int level) {
            final List<Line> out = new ArrayList<>();
            for (Line l : story) {
                if (l.level <= level) {
                    out.add(l);
                }
            }
            if (out.isEmpty() && !story.isEmpty()) {
                // levels all set too high: the first few lines still make a recap
                out.addAll(story.subList(0, Math.min(4, story.size())));
            }
            return out;
        }

        public JSONObject toJson() {
            try {
                final JSONObject o = new JSONObject();
                o.put("kind", kind);
                o.put("last_time", lastTime);
                o.put("stopped_at", stoppedAt);
                final JSONArray s = new JSONArray();
                for (Line l : story) {
                    s.put(new JSONObject().put("text", l.text).put("chapter", l.chapter).put("level", l.level));
                }
                o.put("story", s);
                final JSONArray c = new JSONArray();
                for (Person p : cast) {
                    c.put(new JSONObject().put("name", p.name).put("initials", p.initials).put("card", p.card));
                }
                o.put("cast", c);
                o.put("status", new JSONObject().put("headline", headline).put("detail", detail));
                final JSONArray w = new JSONArray();
                for (Question q : warmup) {
                    w.put(new JSONObject().put("q", q.q).put("a", q.a));
                }
                o.put("warmup", w);
                return o;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }

    /** The model's answer (a JSON object, maybe in a code fence or with words around it), or null. */
    public static Recap parse(String answer) {
        if (answer == null) {
            return null;
        }
        final int start = answer.indexOf('{');
        final int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return fromJson(new JSONObject(answer.substring(start, end + 1)));
        } catch (JSONException e) {
            return null;
        }
    }

    /** A recap from its JSON (an answer, or one saved by {@link Recap#toJson}); null when it holds nothing. */
    public static Recap fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final Recap r = new Recap();
        r.kind = NONFICTION.equals(clean(o.optString("kind")).toLowerCase(Locale.ROOT).replace("-", "").replace(" ", ""))
                ? NONFICTION : FICTION;
        r.lastTime = clean(o.optString("last_time"));
        r.stoppedAt = clean(o.optString("stopped_at"));
        final JSONArray story = o.optJSONArray("story");
        for (int i = 0; story != null && i < story.length() && r.story.size() < MAX_STORY; i++) {
            final JSONObject l = story.optJSONObject(i);
            final String text = l == null ? clean(story.optString(i)) : clean(l.optString("text"));
            if (text.isEmpty()) {
                continue;
            }
            final int level = l == null ? RecapLogic.LEVEL_2M
                    : Math.max(RecapLogic.LEVEL_30S, Math.min(RecapLogic.LEVEL_5M, l.optInt("level", RecapLogic.LEVEL_2M)));
            r.story.add(new Line(text, l == null ? "" : clean(l.optString("chapter")), level));
        }
        final JSONArray cast = o.optJSONArray("cast");
        for (int i = 0; cast != null && i < cast.length() && r.cast.size() < MAX_CAST; i++) {
            final JSONObject p = cast.optJSONObject(i);
            if (p == null) {
                continue;
            }
            final String name = clean(p.optString("name"));
            if (name.isEmpty()) {
                continue;
            }
            String initials = clean(p.optString("initials"));
            if (initials.isEmpty() || initials.length() > 2) {
                initials = initials(name);
            }
            r.cast.add(new Person(name, initials.toUpperCase(Locale.ROOT), clean(p.optString("card"))));
        }
        final JSONObject status = o.optJSONObject("status");
        if (status != null) {
            r.headline = clean(status.optString("headline"));
            r.detail = clean(status.optString("detail"));
        }
        final JSONArray warmup = o.optJSONArray("warmup");
        for (int i = 0; warmup != null && i < warmup.length() && r.warmup.size() < MAX_WARMUP; i++) {
            final JSONObject q = warmup.optJSONObject(i);
            final String question = q == null ? clean(warmup.optString(i)) : clean(q.optString("q"));
            if (!question.isEmpty()) {
                r.warmup.add(new Question(question, q == null ? "" : clean(q.optString("a"))));
            }
        }
        if (r.story.isEmpty() && r.lastTime.isEmpty() && r.stoppedAt.isEmpty()) {
            return null;
        }
        return r;
    }

    /** One or two letters for a name: "Phileas Fogg" → "PF", "Passepartout" → "P". */
    public static String initials(String name) {
        final StringBuilder out = new StringBuilder();
        if (name != null) {
            for (String part : name.trim().split("\\s+")) {
                if (!part.isEmpty() && Character.isLetterOrDigit(part.codePointAt(0)) && out.length() < 2) {
                    out.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
                }
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
}
