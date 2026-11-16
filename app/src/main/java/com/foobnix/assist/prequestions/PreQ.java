package com.foobnix.assist.prequestions;

import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.QuoteMatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-questions (canvas "Pre-question", Assist.PRE_QUESTIONS), the pure part: the data of one
 * chapter's questions as kept in {@code AssistStore} under {@code preq:<chapter key>}, the request
 * that writes them, reading its answer, tying each answer to a sentence of the book, the numbers
 * of a guess against the answer ("24 times more") and which answers a page holds. No Android here.
 */
public final class PreQ {

    public static final String PREFIX = "preq:";
    /** Questions a chapter gets. */
    public static final int COUNT = 3;
    /** Questions asked of the model: one more than kept, as one may not be found in the book. */
    static final int ASKED = 4;
    public static final int OPTIONS = 4;

    /** The offer was shown; nothing else happened. */
    public static final String OFFERED = "offered";
    /** The reader said not now: no more offers for this chapter. */
    public static final String DECLINED = "declined";
    /** Questions made, guesses not all in. */
    public static final String READY = "ready";
    /** Every guess is in: the answers are flagged while reading. */
    public static final String LOCKED = "locked";

    public static final int NONE = -1;
    public static final int GUESSING = 0;
    public static final int FAIRLY_SURE = 1;
    public static final int CERTAIN = 2;

    private PreQ() {
    }

    /** The store key of a chapter's pre-questions. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    // ------------------------------------------------------------------ data

    public static final class Question {
        public String text = "";
        /** The question in a few words, for the locked-in list. */
        public String shortText = "";
        public List<String> options = new ArrayList<>();
        /** Index of the right option. */
        public int answer;
        /** The right value when the answer is a quantity, else null. */
        public Double number;
        /** What the right number is ("Smith's workshop"), may be empty. */
        public String label = "";
        public String why = "";
        /** The answer sentence as the book has it (its words joined). */
        public String quote = "";
        /** 0-based page of the quote in the layout of {@link Chapter#layout}; -1 unknown. */
        public int page = -1;
        /** The page where the quote ends (it may run over a page edge). */
        public int lastPage = -1;

        /** The option guessed, or {@link #NONE}. */
        public int guess = NONE;
        /** The guess in the reader's own words (when no option was picked). */
        public String own = "";
        public int confidence = NONE;
        public boolean skipped;
        /** When the reader reached the answer, 0 before. */
        public long found;

        public boolean answered() {
            return skipped || guess >= 0 || !own.trim().isEmpty();
        }

        public String answerText() {
            return answer >= 0 && answer < options.size() ? options.get(answer) : "";
        }

        /** The reader's guess as text: the option, else their own words; empty when skipped. */
        public String guessText() {
            if (guess >= 0 && guess < options.size()) {
                return options.get(guess);
            }
            return own.trim();
        }

        public boolean isRight() {
            return guess >= 0 && guess == answer;
        }

        /** The guess as a number, or null. */
        public Double guessNumber() {
            return skipped ? null : PreQ.number(guessText());
        }

        /** Whether the answer is on pages {@code from}..{@code to} (0-based, inclusive). */
        public boolean on(int from, int to) {
            if (page < 0) {
                return false;
            }
            final int last = Math.max(page, lastPage);
            return page <= to && last >= from;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("q", text);
            o.put("short", shortText);
            o.put("options", new JSONArray(options));
            o.put("answer", answer);
            if (number != null) {
                o.put("number", number.doubleValue());
            }
            o.put("label", label);
            o.put("why", why);
            o.put("quote", quote);
            o.put("page", page);
            o.put("lastPage", lastPage);
            o.put("guess", guess);
            o.put("own", own);
            o.put("confidence", confidence);
            o.put("skipped", skipped);
            o.put("found", found);
            return o;
        }

        static Question fromJson(JSONObject o) {
            final Question q = new Question();
            q.text = o.optString("q", "");
            q.shortText = o.optString("short", "");
            final JSONArray options = o.optJSONArray("options");
            if (options != null) {
                for (int i = 0; i < options.length(); i++) {
                    q.options.add(options.optString(i, ""));
                }
            }
            q.answer = o.optInt("answer", 0);
            q.number = o.has("number") && !o.isNull("number") ? o.optDouble("number") : null;
            if (q.number != null && (q.number.isNaN() || q.number.isInfinite())) {
                q.number = null;
            }
            q.label = o.optString("label", "");
            q.why = o.optString("why", "");
            q.quote = o.optString("quote", "");
            q.page = o.optInt("page", -1);
            q.lastPage = o.optInt("lastPage", q.page);
            q.guess = o.optInt("guess", NONE);
            q.own = o.optString("own", "");
            q.confidence = o.optInt("confidence", NONE);
            q.skipped = o.optBoolean("skipped");
            q.found = o.optLong("found");
            return q;
        }
    }

    /** One chapter's pre-questions: the {@code preq:<chapter key>} item. */
    public static final class Chapter {
        public String title = "";
        public String state = OFFERED;
        /** The layout the questions' pages hold for ({@link #layoutKey}). */
        public String layout = "";
        public long locked;
        public List<Question> questions = new ArrayList<>();

        public boolean isLocked() {
            return LOCKED.equals(state);
        }

        /** The first question without a guess, or -1 when all are in. */
        public int nextOpen() {
            for (int i = 0; i < questions.size(); i++) {
                if (!questions.get(i).answered()) {
                    return i;
                }
            }
            return -1;
        }

        public int foundCount() {
            int n = 0;
            for (Question q : questions) {
                if (q.found > 0) {
                    n++;
                }
            }
            return n;
        }

        /**
         * The answers the reader reaches on pages {@code from}..{@code to} that were not flagged yet
         * (only once every guess is locked in, and in the layout the pages were found in), by index.
         */
        public List<Integer> revealsOn(int from, int to, String layoutNow) {
            final List<Integer> out = new ArrayList<>();
            if (!isLocked() || !layout.equals(layoutNow)) {
                return out;
            }
            for (int i = 0; i < questions.size(); i++) {
                final Question q = questions.get(i);
                if (q.found == 0 && q.on(from, to)) {
                    out.add(i);
                }
            }
            return out;
        }

        /** Whether the answers' pages must be found again: locked, some not reached, another layout. */
        public boolean needsPages(String layoutNow) {
            if (!isLocked() || layout.equals(layoutNow)) {
                return false;
            }
            for (Question q : questions) {
                if (q.found == 0) {
                    return true;
                }
            }
            return false;
        }

        /** The answer sentences as {@link QuoteMatcher#key}s, for underlining them. */
        public List<String> keys() {
            final List<String> keys = new ArrayList<>();
            if (!isLocked()) {
                return keys;
            }
            for (Question q : questions) {
                final String key = QuoteMatcher.key(q.quote);
                if (key != null) {
                    keys.add(key);
                }
            }
            return keys;
        }

        public JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("state", state);
            o.put("layout", layout);
            o.put("locked", locked);
            final JSONArray list = new JSONArray();
            for (Question q : questions) {
                list.put(q.toJson());
            }
            o.put("questions", list);
            return o;
        }

        public static Chapter fromJson(JSONObject o) {
            final Chapter c = new Chapter();
            if (o == null) {
                return c;
            }
            c.title = o.optString("title", "");
            c.state = o.optString("state", OFFERED);
            c.layout = o.optString("layout", "");
            c.locked = o.optLong("locked");
            final JSONArray list = o.optJSONArray("questions");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    final JSONObject q = list.optJSONObject(i);
                    if (q != null) {
                        c.questions.add(Question.fromJson(q));
                    }
                }
            }
            return c;
        }
    }

    /** Names the layout pages are counted in: a fixed layout's pages never move; a reflowing book's move with size and font. */
    public static String layoutKey(boolean fixed, int width, int height, int fontSize) {
        return fixed ? "fixed" : width + "x" + height + "@" + fontSize;
    }

    /** Whether a chapter is offered pre-questions on entry: a real chapter never offered, or one with guesses left. */
    public static boolean offerable(String chapterKey, String title, boolean sideTitle, Chapter stored) {
        if (chapterKey == null || chapterKey.isEmpty() || chapterKey.startsWith("@") || sideTitle) {
            return false;
        }
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        return stored == null || READY.equals(stored.state) && !stored.questions.isEmpty();
    }

    // ------------------------------------------------------------------ the request

    public static String system(String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in" : answerLanguage.trim();
        return "You write pre-questions for a reader who is about to read a chapter of a book. The reader "
                + "guesses first; reaching the answer in the text later makes it stick. Write " + ASKED
                + " questions about this chapter, the best first. Each one is answered by a single sentence of "
                + "the chapter. Good questions ask for a surprising number or fact, a cause, or a claim the "
                + "reader would not expect; never about headings, the chapter's title, or a twist of a story "
                + "that a guess would spoil. Each question has " + OPTIONS + " short options (at most 6 words "
                + "each): exactly one is right according to the chapter, the others are plausible. When the "
                + "answer is a quantity, every option is a quantity of a clearly different size (like About "
                + "200 / About 2,000 / About 48,000 / About 480,000) and \"number\" is the right value as a plain "
                + "number; otherwise \"number\" is null. Write the questions, options, label and why in " + lang
                + ". The quote is the one sentence of the chapter that gives the answer, copied exactly from the "
                + "text, character for character, in the text's own language: no changes, no ellipses, no "
                + "quotation marks added, never a heading or a caption.\n"
                + "Answer with only a JSON array, no other text:\n"
                + "[{\"question\": \"...\", \"short\": \"the question in at most 8 words\", \"options\": [\"...\", "
                + "\"...\", \"...\", \"...\"], \"answer\": 0, \"number\": null, \"label\": \"for a quantity, whose "
                + "number it is in at most 3 words; else empty\", \"why\": \"one short sentence: why the answer is "
                + "so\", \"quote\": \"...\"}]\n"
                + "\"answer\" is the index (0 to " + (OPTIONS - 1) + ") of the right option.\n"
                + AiPrompts.SIDE_MATTER;
    }

    public static String user(String bookTitle, String chapterTitle, String text) {
        return "Book: " + safe(bookTitle) + "\nChapter: " + safe(chapterTitle) + "\n\n" + safe(text);
    }

    /** The questions of an answer; tolerant of fences and text around the array; badly formed ones are left out. */
    public static List<Question> parse(String answer) {
        final List<Question> out = new ArrayList<>();
        final JSONArray list = array(answer);
        if (list == null) {
            return out;
        }
        for (int i = 0; i < list.length(); i++) {
            final JSONObject o = list.optJSONObject(i);
            final Question q = o == null ? null : question(o);
            if (q != null) {
                out.add(q);
            }
        }
        return out;
    }

    private static JSONArray array(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('[');
        final int close = answer.lastIndexOf(']');
        if (open >= 0 && close > open) {
            try {
                return new JSONArray(answer.substring(open, close + 1));
            } catch (JSONException e) {
                // maybe an object holding the list
            }
        }
        final int brace = answer.indexOf('{');
        final int end = answer.lastIndexOf('}');
        if (brace >= 0 && end > brace) {
            try {
                final JSONObject o = new JSONObject(answer.substring(brace, end + 1));
                return o.optJSONArray("questions");
            } catch (JSONException e) {
                return null;
            }
        }
        return null;
    }

    private static Question question(JSONObject o) {
        final Question q = new Question();
        q.text = o.optString("question", o.optString("q", "")).trim();
        q.quote = o.optString("quote", "").trim();
        final JSONArray options = o.optJSONArray("options");
        if (q.text.isEmpty() || q.quote.isEmpty() || options == null) {
            return null;
        }
        for (int i = 0; i < options.length(); i++) {
            final String s = options.optString(i, "").trim();
            if (!s.isEmpty()) {
                q.options.add(s);
            }
        }
        q.answer = answerIndex(o.opt("answer"), q.options);
        if (q.options.size() < 2 || q.answer < 0) {
            return null;
        }
        if (q.options.size() > OPTIONS) {
            if (q.answer >= OPTIONS) {
                // keep the right one in the four shown
                final String right = q.options.get(q.answer);
                q.options = new ArrayList<>(q.options.subList(0, OPTIONS - 1));
                q.options.add(right);
                q.answer = OPTIONS - 1;
            } else {
                q.options = new ArrayList<>(q.options.subList(0, OPTIONS));
            }
        }
        q.shortText = o.optString("short", "").trim();
        if (q.shortText.isEmpty()) {
            q.shortText = q.text;
        }
        q.why = o.optString("why", "").trim();
        q.label = o.optString("label", "").trim();
        final Object n = o.opt("number");
        if (n instanceof Number) {
            final double d = ((Number) n).doubleValue();
            q.number = d > 0 && !Double.isInfinite(d) && !Double.isNaN(d) ? d : null;
        } else if (n instanceof String) {
            q.number = number((String) n);
        }
        return q;
    }

    /** "answer" as an index, a letter ("B"), or the right option's text; -1 when none of the options. */
    static int answerIndex(Object a, List<String> options) {
        if (a instanceof Number) {
            final int i = ((Number) a).intValue();
            return i >= 0 && i < options.size() ? i : -1;
        }
        if (!(a instanceof String)) {
            return -1;
        }
        final String s = ((String) a).trim();
        if (s.matches("\\d+")) {
            final int i = Integer.parseInt(s);
            return i < options.size() ? i : -1;
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(s)) {
                return i;
            }
        }
        if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
            final int i = Character.toUpperCase(s.charAt(0)) - 'A';
            return i >= 0 && i < options.size() ? i : -1;
        }
        return -1;
    }

    /**
     * Keeps the questions whose answer sentence is in the chapter (at most {@link #COUNT}, in
     * order), each with the sentence as the book has it and the pages it is on; the rest are left out.
     *
     * @param words    the chapter's words in reading order
     * @param wordPage the 0-based page of each word
     */
    public static List<Question> place(List<Question> questions, List<String> words, List<Integer> wordPage) {
        final List<Question> kept = new ArrayList<>();
        final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
        final List<String> seen = new ArrayList<>();
        for (Question q : questions) {
            if (kept.size() >= COUNT) {
                break;
            }
            final int[] range = QuoteMatcher.locate(all, q.quote);
            if (range == null) {
                continue;
            }
            final String text = QuoteMatcher.join(words.subList(range[0], range[1] + 1));
            final String key = QuoteMatcher.key(text);
            if (key == null || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            q.quote = text;
            q.page = wordPage.get(range[0]);
            q.lastPage = wordPage.get(range[1]);
            kept.add(q);
        }
        return kept;
    }

    /** Whether a quote is on a page (whole, or running over its top or bottom edge). */
    public static boolean onPage(List<String> pageWords, String quote) {
        final String key = QuoteMatcher.key(quote);
        return key != null && !QuoteMatcher.onPage(new QuoteMatcher.Words(pageWords), Collections.singletonList(key)).isEmpty();
    }

    // ------------------------------------------------------------------ numbers

    private static final Pattern NUMBER = Pattern.compile(
            "(\\d{1,3}(?:[,   ]\\d{3})+|\\d+)(?:\\.(\\d+))?\\s*(thousand|million|billion|trillion|bn|k|m)?\\b",
            Pattern.CASE_INSENSITIVE);

    /** The first number in a text ("About 48,000" = 48000, "2.5 million" = 2500000), or null. */
    public static Double number(String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        final String whole = m.group(1).replaceAll("[,   ]", "");
        double value = Double.parseDouble(m.group(2) == null ? whole : whole + "." + m.group(2));
        final String scale = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT);
        switch (scale) {
            case "thousand":
            case "k":
                value *= 1e3;
                break;
            case "million":
            case "m":
                value *= 1e6;
                break;
            case "billion":
            case "bn":
                value *= 1e9;
                break;
            case "trillion":
                value *= 1e12;
                break;
            default:
                break;
        }
        return value;
    }

    /** How a guessed number stands to the answer. */
    public static final class Comparison {
        public static final int RIGHT = 0;
        public static final int CLOSE = 1;
        public static final int MORE = 2;
        public static final int LESS = 3;

        public final int kind;
        /** "24": how many times the answer is the guess (MORE) or the guess the answer (LESS); else empty. */
        public final String times;
        /** Bar lengths, 0..1, the larger number being 1. */
        public final float guessShare;
        public final float answerShare;

        Comparison(int kind, String times, float guessShare, float answerShare) {
            this.kind = kind;
            this.times = times;
            this.guessShare = guessShare;
            this.answerShare = answerShare;
        }
    }

    /** Within 10% is right, within 1.5 times close, else so many times more or less. */
    public static Comparison compare(double guess, double answer) {
        final double max = Math.max(guess, answer);
        final float g = max <= 0 ? 0 : (float) (guess / max);
        final float a = max <= 0 ? 0 : (float) (answer / max);
        if (guess <= 0 || answer <= 0) {
            return new Comparison(guess == answer ? Comparison.RIGHT : answer > guess ? Comparison.MORE : Comparison.LESS,
                    "", g, a);
        }
        final double r = answer / guess;
        if (Math.abs(r - 1) <= 0.1) {
            return new Comparison(Comparison.RIGHT, "", g, a);
        }
        if (r < 1.5 && r > 1 / 1.5) {
            return new Comparison(Comparison.CLOSE, "", g, a);
        }
        return r >= 1.5 ? new Comparison(Comparison.MORE, times(r), g, a) : new Comparison(Comparison.LESS, times(1 / r), g, a);
    }

    /** 24 → "24", 2.46 → "2.5", 1234 → "1,234". */
    static String times(double ratio) {
        if (ratio >= 10) {
            return format(Math.round(ratio));
        }
        final double one = Math.round(ratio * 10) / 10.0;
        return one == Math.floor(one) ? String.valueOf((long) one) : String.valueOf(one);
    }

    /** A number for people: 48000 → "48,000", 2.5 → "2.5". */
    public static String format(double n) {
        final DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(n);
    }

    /** An option inside a sentence: "About 2,000" → "about 2,000" (an acronym stays), no closing period. */
    public static String inSentence(String option) {
        String s = option == null ? "" : option.trim();
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.length() >= 2 && Character.isUpperCase(s.charAt(0)) && Character.isLowerCase(s.charAt(1))) {
            s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
