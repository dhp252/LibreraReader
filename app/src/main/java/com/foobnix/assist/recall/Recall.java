package com.foobnix.assist.recall;

import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.QuoteMatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blank-page recall (canvas "Recall: blank page", "Blank-page recall"; Assist.RECALL), the pure
 * part: the data of a chapter's attempts as kept in {@code AssistStore} under
 * {@code recall:<chapter key>}, the one request that checks the reader's words against the
 * chapter's key ideas, reading its answer, the score, the nudge and when the offer shows.
 * No Android here.
 */
public final class Recall {

    public static final String PREFIX = "recall:";
    /** Characters of a chapter sent at most when it has no stored ideas. */
    public static final int MAX_CHARS = 40_000;
    /** Less text than this holds no ideas worth recalling. */
    public static final int MIN_CHARS = 600;
    /** Key ideas a chapter is checked against at most. */
    public static final int MAX_IDEAS = 8;
    /** Attempts kept in an item's history. */
    public static final int HISTORY = 20;
    /** The seconds the canvas asks for ("for about 30 seconds"). */
    public static final int ABOUT_SECONDS = 30;

    public static final String REMEMBERED = "remembered";
    public static final String UNSURE = "unsure";
    public static final String MISSED = "missed";

    /** An attempt was checked. */
    public static final String DONE = "done";
    /** The reader said not now, or skipped: no more offers for this chapter. */
    public static final String DECLINED = "declined";
    /** Only the chapter's ideas are known (a nudge, a teach-back), no attempt yet. */
    public static final String IDEAS = "ideas";

    private Recall() {
    }

    /** The store key of a chapter's recall. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    // ------------------------------------------------------------------ data

    /** One key idea of a chapter, and in an attempt how well it was recalled. */
    public static final class Idea {
        public int id;
        public String text = "";
        /** The idea it supports in the key passages, or -1: a main idea. */
        public int parent = -1;
        /** {@link #REMEMBERED}, {@link #UNSURE}, {@link #MISSED}, or empty before a check. */
        public String state = "";
        /** Its passages in the book's own words; the first is where a tap goes. */
        public final List<String> quotes = new ArrayList<>();
        /** The 0-based page of each passage when it was stored (a hint), or -1. */
        public final List<Integer> pages = new ArrayList<>();

        public Idea() {
        }

        public Idea(int id, String text) {
            this.id = id;
            this.text = text == null ? "" : text.trim();
        }

        public Idea add(String quote, int page) {
            if (quote != null && !quote.trim().isEmpty()) {
                quotes.add(quote.trim());
                pages.add(page);
            }
            return this;
        }

        /** The passage a tap goes to, or empty. */
        public String quote() {
            return quotes.isEmpty() ? "" : quotes.get(0);
        }

        public int page() {
            return pages.isEmpty() ? -1 : pages.get(0);
        }

        /** Whether a tap can go somewhere. */
        public boolean hasPlace() {
            return !quote().isEmpty() || page() >= 0;
        }

        public Idea copy() {
            final Idea i = new Idea(id, text);
            i.parent = parent;
            i.state = state;
            i.quotes.addAll(quotes);
            i.pages.addAll(pages);
            return i;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("idea", text);
            o.put("state", state);
            o.put("quote", quote());
            o.put("page", page());
            return o;
        }

        static Idea fromJson(JSONObject o) {
            final Idea i = new Idea(o.optInt("id"), o.optString("idea", ""));
            i.state = state(o.optString("state", ""));
            final String quote = o.optString("quote", "");
            final int page = o.optInt("page", -1);
            if (!quote.isEmpty()) {
                i.add(quote, page);
            } else if (page >= 0) {
                i.quotes.add("");
                i.pages.add(page);
            }
            return i;
        }
    }

    /** One earlier try: when, what the reader expected, what they got (percent). */
    public static final class Try {
        public final long date;
        public final int expected;
        public final int got;

        public Try(long date, int expected, int got) {
            this.date = date;
            this.expected = expected;
            this.got = got;
        }
    }

    /** A chapter's recall: the {@code recall:<chapter key>} item, the latest attempt and a short history. */
    public static final class Attempt {
        public String state = IDEAS;
        public String title = "";
        public long date;
        /** The reader's guess before recalling, percent. */
        public int expected;
        /** What the check found, percent. */
        public int got;
        public String words = "";
        public boolean spoken;
        /** Seconds talked, 0 when typed. */
        public int seconds;
        /** The chapter's key ideas; with their states once {@link #DONE}. */
        public List<Idea> ideas = new ArrayList<>();
        /** The ideas are the chapter's key passages (read again from them each time), not the check's own. */
        public boolean fromPassages;
        /** What a curious beginner asks next (the door to teach-back). */
        public String question = "";
        public List<Try> history = new ArrayList<>();

        public boolean isDone() {
            return DONE.equals(state) && !ideas.isEmpty();
        }

        public JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("state", state);
            o.put("title", title);
            o.put("date", date);
            o.put("expected", expected);
            o.put("got", got);
            o.put("words", words);
            o.put("spoken", spoken);
            o.put("seconds", seconds);
            final JSONArray list = new JSONArray();
            for (Idea i : ideas) {
                list.put(i.toJson());
            }
            o.put("ideas", list);
            o.put("fromPassages", fromPassages);
            o.put("question", question);
            final JSONArray h = new JSONArray();
            for (Try t : history) {
                h.put(new JSONObject().put("date", t.date).put("expected", t.expected).put("got", t.got));
            }
            o.put("history", h);
            return o;
        }

        /** The stored item, or null when there is none. */
        public static Attempt fromJson(JSONObject o) {
            if (o == null) {
                return null;
            }
            final Attempt a = new Attempt();
            a.state = o.optString("state", IDEAS);
            a.title = o.optString("title", "");
            a.date = o.optLong("date");
            a.expected = percent(o.optInt("expected"));
            a.got = percent(o.optInt("got"));
            a.words = o.optString("words", "");
            a.spoken = o.optBoolean("spoken");
            a.seconds = Math.max(0, o.optInt("seconds"));
            final JSONArray list = o.optJSONArray("ideas");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    final JSONObject idea = list.optJSONObject(i);
                    if (idea != null && !idea.optString("idea", "").trim().isEmpty()) {
                        a.ideas.add(Idea.fromJson(idea));
                    }
                }
            }
            a.fromPassages = o.optBoolean("fromPassages");
            a.question = o.optString("question", "");
            final JSONArray h = o.optJSONArray("history");
            if (h != null) {
                for (int i = 0; i < h.length(); i++) {
                    final JSONObject t = h.optJSONObject(i);
                    if (t != null) {
                        a.history.add(new Try(t.optLong("date"), percent(t.optInt("expected")), percent(t.optInt("got"))));
                    }
                }
            }
            return a;
        }
    }

    /** What the check request answered. */
    public static final class Check {
        public final List<Idea> ideas;
        public final String question;

        Check(List<Idea> ideas, String question) {
            this.ideas = ideas;
            this.question = question == null ? "" : question.trim();
        }
    }

    // ------------------------------------------------------------------ rules

    /**
     * The ideas a chapter is checked against, out of all its key-passage ideas: the main ones (no
     * parent), or all when it has fewer than two of those; {@link #MAX_IDEAS} at most, in reading order.
     */
    public static List<Idea> keyIdeas(List<Idea> all) {
        final List<Idea> main = new ArrayList<>();
        if (all == null) {
            return main;
        }
        for (Idea i : all) {
            if (i.parent < 0 && !i.text.isEmpty()) {
                main.add(i);
            }
        }
        if (main.size() < 2) {
            main.clear();
            for (Idea i : all) {
                if (!i.text.isEmpty()) {
                    main.add(i);
                }
            }
        }
        return main.size() > MAX_IDEAS ? new ArrayList<>(main.subList(0, MAX_IDEAS)) : main;
    }

    /** Whether a chapter is offered a recall at its end: a real chapter with no attempt and no "not now" yet. */
    public static boolean offerable(String chapterKey, String title, boolean sideTitle, boolean stored) {
        if (chapterKey == null || chapterKey.isEmpty() || chapterKey.startsWith("@") || sideTitle || stored) {
            return false;
        }
        return title != null && !title.trim().isEmpty();
    }

    /**
     * Whether the reader just reached the last page of a chapter by turning pages forward inside
     * it (a jump to the last page, or opening the book on it, is not reaching the end).
     *
     * @param prevFrom    first 0-based page shown before, -1 when none
     * @param prevTo      last page shown before
     * @param from        first page shown now
     * @param to          last page shown now
     * @param chapterFrom the chapter's first page
     * @param chapterTo   the page after the chapter's last
     */
    public static boolean reachedEnd(int prevFrom, int prevTo, int from, int to, int chapterFrom, int chapterTo) {
        final int last = chapterTo - 1;
        if (prevFrom < 0 || last <= chapterFrom || from > last || to < last || from < chapterFrom) {
            return false;
        }
        // before: inside the chapter, short of its last page; now: one turn on
        return prevFrom >= chapterFrom && prevTo < last && from > prevFrom && from - prevTo <= 2;
    }

    /** A percent kept in 0..100. */
    public static int percent(int p) {
        return Math.max(0, Math.min(100, p));
    }

    public static int count(List<Idea> ideas, String state) {
        int n = 0;
        for (Idea i : ideas) {
            if (state.equals(i.state)) {
                n++;
            }
        }
        return n;
    }

    /** The score of a check: a remembered idea counts whole, a half-remembered one half. */
    public static int score(List<Idea> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            return 0;
        }
        final int points = 2 * count(ideas, REMEMBERED) + count(ideas, UNSURE);
        return percent(Math.round(points * 50f / ideas.size()));
    }

    /** "0:21". */
    public static String clock(int seconds) {
        final int s = Math.max(0, seconds);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    /** The nudge: the first word of each idea, with "…" after it. */
    public static List<String> nudge(List<Idea> ideas) {
        final List<String> out = new ArrayList<>();
        if (ideas == null) {
            return out;
        }
        for (Idea i : ideas) {
            final String w = firstWord(i.text);
            if (!w.isEmpty()) {
                out.add(w + "…");
            }
        }
        return out;
    }

    static String firstWord(String text) {
        if (text == null) {
            return "";
        }
        for (String w : text.trim().split("\\s+")) {
            int from = 0;
            int to = w.length();
            while (from < to && !Character.isLetterOrDigit(w.codePointAt(from))) {
                from++;
            }
            while (to > from && !Character.isLetterOrDigit(w.codePointBefore(to))) {
                to--;
            }
            if (to > from) {
                return w.substring(from, to);
            }
        }
        return "";
    }

    /**
     * The item after a check: the new attempt on top, the tries before it kept as history
     * (oldest first, {@link #HISTORY} at most, this one last).
     */
    public static Attempt attempt(Attempt before, String title, long date, int expected, String words, boolean spoken,
                                  int seconds, Check check, boolean fromPassages) {
        final Attempt a = new Attempt();
        a.state = DONE;
        a.title = title == null ? "" : title;
        a.date = date;
        a.expected = percent(expected);
        a.words = words == null ? "" : words.trim();
        a.spoken = spoken;
        a.seconds = Math.max(0, seconds);
        a.ideas = check.ideas;
        a.fromPassages = fromPassages;
        a.question = check.question;
        a.got = score(check.ideas);
        if (before != null) {
            a.history.addAll(before.history);
        }
        a.history.add(new Try(date, a.expected, a.got));
        while (a.history.size() > HISTORY) {
            a.history.remove(0);
        }
        return a;
    }

    /** The item when only the chapter's ideas were found (a nudge): an attempt already there stays as it is. */
    public static Attempt withIdeas(Attempt before, String title, List<Idea> ideas, String question) {
        final Attempt a = before != null ? before : new Attempt();
        if (a.isDone()) {
            return a;
        }
        if (!DECLINED.equals(a.state)) {
            a.state = IDEAS;
        }
        a.title = title == null ? "" : title;
        a.ideas = ideas;
        a.fromPassages = false;
        if (question != null && !question.trim().isEmpty()) {
            a.question = question.trim();
        }
        return a;
    }

    /** The item of a chapter the reader skipped; an earlier attempt and its history stay. */
    public static Attempt declined(Attempt before, String title) {
        if (before != null && before.isDone()) {
            return before;
        }
        final Attempt a = before != null ? before : new Attempt();
        a.state = DECLINED;
        if (a.title.isEmpty()) {
            a.title = title == null ? "" : title;
        }
        return a;
    }

    // ------------------------------------------------------------------ the requests

    private static final String STATES = "For each idea decide how well the reader recalled it: \"" + REMEMBERED
            + "\" (their words clearly carry the idea, in any wording), \"" + UNSURE + "\" (they touch it but "
            + "vaguely, hedge, or get part of it wrong), or \"" + MISSED + "\" (nothing of it). Judge the meaning, "
            + "not the wording; be fair, not generous: a topic word alone is not the idea, and nothing they did not "
            + "say counts. ";

    private static String question(String lang) {
        return "Then write the one question a curious beginner who has not read the chapter would ask the reader "
                + "about the most important idea they missed or were unsure of (when there is none, about the idea "
                + "most worth explaining): one or two short spoken sentences, friendly, no jargon, never giving the "
                + "answer away. Write it in " + lang + ". ";
    }

    private static final String PICK = "Pick the chapter's 3 to 6 key ideas: its main claims in reading order, not "
            + "details, examples or headings. Each idea is at most 8 words; its quote is the one sentence of the "
            + "chapter that states it best, copied exactly from the text, character for character, in the text's own "
            + "language: no changes, no ellipses, no quotation marks added. ";

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in" : answerLanguage.trim();
    }

    /**
     * The system prompt of the check.
     *
     * @param withIdeas the chapter's key ideas are given (else its text, and the model picks them)
     */
    public static String checkSystem(String answerLanguage, boolean withIdeas) {
        final String lang = language(answerLanguage);
        final String start = "A reader just finished a chapter of a book and, without looking back, said or wrote "
                + "what they remember of it (blank-page recall). Their words may be a rough voice transcript with "
                + "recognition mistakes: read through those. ";
        if (withIdeas) {
            return start + "You get the chapter's key ideas, numbered, and the reader's words. " + STATES
                    + question(lang)
                    + "Answer with only JSON, no other text:\n"
                    + "{\"ideas\": [{\"id\": 1, \"state\": \"" + REMEMBERED + "\"}], \"question\": \"...\"}\n"
                    + "Every idea given, each once, by its number.";
        }
        return start + "You get the chapter's text and the reader's words. " + PICK + "Write the ideas in " + lang
                + ". " + STATES + question(lang)
                + "Answer with only JSON, no other text:\n"
                + "{\"ideas\": [{\"idea\": \"...\", \"quote\": \"...\", \"state\": \"" + MISSED + "\"}], "
                + "\"question\": \"...\"}\n"
                + AiPrompts.SIDE_MATTER;
    }

    /** The check's user message: the ideas (or, with none, the chapter's text) and the reader's words. */
    public static String checkUser(String bookTitle, String chapterTitle, List<Idea> ideas, String text, String words) {
        final StringBuilder out = new StringBuilder(head(bookTitle, chapterTitle));
        if (ideas != null && !ideas.isEmpty()) {
            out.append("Key ideas:\n");
            for (int i = 0; i < ideas.size(); i++) {
                out.append(i + 1).append(". ").append(oneLine(ideas.get(i).text)).append('\n');
            }
        } else {
            out.append("<chapter>\n").append(safe(text)).append("\n</chapter>\n");
        }
        return out.append("\nWhat the reader remembers:\n<recall>\n").append(safe(words)).append("\n</recall>").toString();
    }

    /** The system prompt of the request that only finds a chapter's key ideas (a nudge, a teach-back). */
    public static String ideasSystem(String answerLanguage) {
        final String lang = language(answerLanguage);
        return "A reader just finished a chapter of a book and will now recall it from memory. " + PICK
                + "Write the ideas in " + lang + ". Then write the one question a curious beginner who has not read "
                + "the chapter would ask about it first: one or two short spoken sentences, friendly, no jargon. "
                + "Write it in " + lang + ". Answer with only JSON, no other text:\n"
                + "{\"ideas\": [{\"idea\": \"...\", \"quote\": \"...\"}], \"question\": \"...\"}\n"
                + AiPrompts.SIDE_MATTER;
    }

    public static String ideasUser(String bookTitle, String chapterTitle, String text) {
        return head(bookTitle, chapterTitle) + "<chapter>\n" + safe(text) + "\n</chapter>";
    }

    private static String head(String bookTitle, String chapterTitle) {
        return "Book: " + oneLine(bookTitle) + "\nChapter: " + oneLine(chapterTitle) + "\n\n";
    }

    // ------------------------------------------------------------------ reading the answer

    /**
     * The check's answer. With {@code known} ideas (numbered from 1 in the request) each gets its
     * state, one the model left out counts as missed; without, the model's own ideas are read.
     * Tolerant of fences, text around the JSON and other words for the states.
     *
     * @return null when no idea could be read
     */
    public static Check parseCheck(String answer, List<Idea> known) {
        final JSONObject root = object(answer);
        final JSONArray list = root == null ? array(answer) : list(root);
        if (list == null) {
            return null;
        }
        final String question = root == null ? "" : root.optString("question", "");
        final List<Idea> out = new ArrayList<>();
        if (known != null && !known.isEmpty()) {
            for (Idea k : known) {
                final Idea copy = k.copy();
                copy.state = "";
                out.add(copy);
            }
            int read = 0;
            for (int i = 0; i < list.length(); i++) {
                final JSONObject o = list.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                final int n = number(o.opt("id"), o.opt("n"), o.opt("idea"), i + 1);
                final String state = state(o.optString("state", o.optString("status", "")));
                if (n >= 1 && n <= out.size() && !state.isEmpty() && out.get(n - 1).state.isEmpty()) {
                    out.get(n - 1).state = state;
                    read++;
                }
            }
            if (read == 0) {
                return null;
            }
            for (Idea i : out) {
                if (i.state.isEmpty()) {
                    i.state = MISSED;
                }
            }
            return new Check(out, question);
        }
        for (int i = 0; i < list.length() && out.size() < MAX_IDEAS; i++) {
            final JSONObject o = list.optJSONObject(i);
            if (o == null) {
                continue;
            }
            final String text = o.optString("idea", o.optString("text", "")).trim();
            if (text.isEmpty()) {
                continue;
            }
            final Idea idea = new Idea(out.size() + 1, text);
            idea.add(o.optString("quote", ""), -1);
            final String state = state(o.optString("state", o.optString("status", "")));
            idea.state = state.isEmpty() ? MISSED : state;
            out.add(idea);
        }
        return out.isEmpty() ? null : new Check(out, question);
    }

    /** The answer of the ideas request: the ideas without a state. Null when none could be read. */
    public static Check parseIdeas(String answer) {
        final Check c = parseCheck(answer, null);
        if (c != null) {
            for (Idea i : c.ideas) {
                i.state = "";
            }
        }
        return c;
    }

    /**
     * Ties the model's own ideas to the book: each quote found in the chapter becomes the book's
     * words with its page; a quote that is not there is dropped (the idea stays, without a place).
     *
     * @param words    the chapter's words in reading order
     * @param wordPage the 0-based page of each word
     */
    public static void place(List<Idea> ideas, List<String> words, List<Integer> wordPage) {
        final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
        for (Idea idea : ideas) {
            final String quote = idea.quote();
            idea.quotes.clear();
            idea.pages.clear();
            final int[] range = quote.isEmpty() ? null : QuoteMatcher.locate(all, quote);
            if (range != null) {
                idea.add(QuoteMatcher.join(words.subList(range[0], range[1] + 1)), wordPage.get(range[0]));
            }
        }
    }

    /** One of the three states for what a model wrote, or empty when it is none. */
    static String state(String s) {
        final String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return "";
        }
        if (v.startsWith("unsure") || v.startsWith("part") || v.startsWith("half") || v.startsWith("vague")
                || v.startsWith("uncertain") || v.startsWith("maybe")) {
            return UNSURE;
        }
        if (v.startsWith("miss") || v.startsWith("forgot") || v.startsWith("no") || v.startsWith("absent")
                || v.equals("false")) {
            return MISSED;
        }
        if (v.startsWith("remember") || v.startsWith("recall") || v.startsWith("yes") || v.startsWith("got")
                || v.equals("true") || v.equals("ok")) {
            return REMEMBERED;
        }
        return "";
    }

    private static int number(Object id, Object n, Object idea, int position) {
        for (Object o : new Object[]{id, n, idea}) {
            if (o instanceof Number) {
                return ((Number) o).intValue();
            }
            if (o instanceof String && ((String) o).trim().matches("\\d{1,3}")) {
                return Integer.parseInt(((String) o).trim());
            }
        }
        return position;
    }

    private static JSONArray list(JSONObject root) {
        for (String name : new String[]{"ideas", "items", "results"}) {
            final JSONArray a = root.optJSONArray(name);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    /** The outermost JSON object of an answer, or null. */
    public static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        final int bracket = answer.indexOf('[');
        if (open < 0 || close <= open || (bracket >= 0 && bracket < open)) {
            return null;
        }
        try {
            return new JSONObject(answer.substring(open, close + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    private static JSONArray array(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('[');
        final int close = answer.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return null;
        }
        try {
            return new JSONArray(answer.substring(open, close + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** A title or idea on one line of a prompt. */
    public static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
