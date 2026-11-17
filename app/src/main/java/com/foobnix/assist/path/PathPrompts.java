package com.foobnix.assist.path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What "Shortest path" asks of the model, and reading its answers back: the path to one idea from
 * the book's chapter lines, and the 2-minute check's one question per path chapter. Only the
 * chapter list (titles and one line each) is sent, never the book's text.
 */
public final class PathPrompts {

    private PathPrompts() {
    }

    /** A path is never longer than this (the prompt asks for 3 to 6). */
    public static final int MAX_STEPS = 8;
    /** The room the prompt and the answer need around the chapter list. */
    private static final int ROOM = 2500;
    private static final int MIN_LINE = 30;

    // ------------------------------------------------------------------ the path

    /** @param answerLanguage the reader's choice; empty means the language of the chapter lines */
    public static String system(String answerLanguage) {
        return "A reader wants to understand one idea of a book without reading all of it. You get the book's "
                + "chapters, one line each, and the idea the reader is after. Pick the shortest path to it: the "
                + "fewest chapters, in the order to read them, each one needed for the next, ending with the "
                + "chapter that holds the idea itself (the goal chapter). Usually 3 to 6 chapters, never more than "
                + MAX_STEPS + "; one alone when the goal chapter needs nothing before it. Write in "
                + language(answerLanguage) + ".\n"
                + "- path: for each chapter on the path, n: the chapter's n, and why: at most 8 words on why it is "
                + "needed for what comes next (\"Why people specialize at all\"); for the goal chapter, what the "
                + "reader finds there.\n"
                + "- goal: the n of the goal chapter, the last of the path.\n"
                + "- skipped: 3 to 6 words or short phrases naming what the chapters left out are about, separated "
                + "by commas (\"wages, rent, banking, Europe's history, taxes\").\n"
                + "Judge only from the list; use only the n it gives. When no chapter is about the idea, answer "
                + "{\"path\": [], \"goal\": -1, \"skipped\": \"\"}.\n"
                + "Answer with one JSON object and nothing else: {\"path\": [{\"n\": <n>, \"why\": \"...\"}], "
                + "\"goal\": <n>, \"skipped\": \"...\"}";
    }

    /**
     * @param chapters the book's chapter list; a chapter's n is its place in it
     * @param maxChars the most characters a request may hold; lines are shortened to fit
     */
    public static String user(String bookTitle, String goal, List<PathChapters.Entry> chapters, int maxChars) {
        final StringBuilder head = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            head.append("Book: ").append(bookTitle.trim()).append("\n\n");
        }
        head.append("The reader says: I want to understand ").append(PathText.clip(goal, 300)).append("\n\n")
                .append("The book's chapters (words = the chapter's length):\n");
        String list = chapters(chapters, Integer.MAX_VALUE);
        final int room = Math.max(ROOM, maxChars - ROOM) - head.length();
        if (list.length() > room && !chapters.isEmpty()) {
            final int withoutLines = chapters(chapters, 0).length();
            list = chapters(chapters, Math.max(MIN_LINE, (room - withoutLines) / chapters.size()));
        }
        return head.append(list).toString();
    }

    private static String chapters(List<PathChapters.Entry> chapters, int lineLimit) {
        final StringBuilder out = new StringBuilder("<chapters>\n");
        for (int i = 0; i < chapters.size(); i++) {
            final PathChapters.Entry e = chapters.get(i);
            out.append("<chapter n=\"").append(i).append('"');
            if (!e.partTitle.isEmpty()) {
                out.append(" part=\"").append(attr(e.partTitle)).append('"');
            }
            out.append(" title=\"").append(attr(e.title)).append('"');
            if (e.chars > 0) {
                out.append(" words=\"").append(Math.max(1, Math.round(e.chars / 6.0))).append('"');
            }
            out.append('>').append(lineLimit <= 0 ? "" : PathText.clip(e.line, lineLimit)).append("</chapter>\n");
        }
        return out.append("</chapters>").toString();
    }

    /** One chapter of the path as the model named it. */
    public static final class Step {
        /** The chapter's n, or -1 when the model gave a key instead. */
        public final int n;
        public final String key;
        public final String why;

        public Step(int n, String key, String why) {
            this.n = n;
            this.key = key;
            this.why = why;
        }
    }

    /** The model's path. */
    public static final class Answer {
        public final List<Step> steps = new ArrayList<>();
        /** The goal chapter's n, or -1. */
        public int goal = -1;
        public String goalKey = "";
        public String skipped = "";
        /** The answer was read as the JSON asked for (an empty path then means: no chapter is about the idea). */
        public boolean read;
    }

    private static final Pattern GOAL = Pattern.compile("\"goal\"\\s*:\\s*(-?\\d+)");
    private static final Pattern SKIPPED = Pattern.compile("\"skipped\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** The whole JSON object, else whatever can be read (a code fence, prose around, an answer cut off). */
    public static Answer parse(String answer) {
        final Answer out = new Answer();
        if (answer == null) {
            return out;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open >= 0 && close > open) {
            try {
                final JSONObject root = new JSONObject(answer.substring(open, close + 1));
                final JSONArray path = root.optJSONArray("path");
                for (int i = 0; path != null && i < path.length(); i++) {
                    step(path.opt(i), out.steps);
                }
                final Object goal = root.opt("goal");
                if (goal instanceof Number) {
                    out.goal = ((Number) goal).intValue();
                } else if (goal instanceof String) {
                    out.goalKey = ((String) goal).trim();
                    out.goal = number(out.goalKey);
                }
                final Object skipped = root.opt("skipped");
                if (skipped instanceof JSONArray) {
                    out.skipped = join((JSONArray) skipped);
                } else if (skipped instanceof String) {
                    out.skipped = ((String) skipped).trim();
                }
                out.read = path != null;
                return out;
            } catch (JSONException e) {
                // cut off or broken: read what can be read below
            }
        }
        final int pathAt = answer.indexOf("\"path\"");
        if (pathAt >= 0) {
            int i = pathAt;
            while ((i = answer.indexOf('{', i)) >= 0) {
                final int end = objectEnd(answer, i);
                if (end < 0) {
                    break;
                }
                try {
                    step(new JSONObject(answer.substring(i, end + 1)), out.steps);
                    i = end + 1;
                } catch (JSONException e) {
                    i++;
                }
            }
        }
        final Matcher g = GOAL.matcher(answer);
        if (g.find()) {
            out.goal = Integer.parseInt(g.group(1));
        }
        final Matcher s = SKIPPED.matcher(answer);
        if (s.find()) {
            out.skipped = unescape(s.group(1));
        }
        return out;
    }

    private static void step(Object item, List<Step> into) {
        if (item instanceof Number) {
            // a bare list of numbers
            into.add(new Step(((Number) item).intValue(), "", ""));
            return;
        }
        if (!(item instanceof JSONObject)) {
            return;
        }
        final JSONObject o = (JSONObject) item;
        final Object n = o.opt("n");
        final String key = o.optString("key", "").trim();
        final int number = n instanceof Number ? ((Number) n).intValue() : n instanceof String ? number((String) n) : -1;
        if (number < 0 && key.isEmpty()) {
            return;
        }
        into.add(new Step(number, key, o.optString("why", "").trim()));
    }

    // ------------------------------------------------------------------ the 2-minute check

    public static String checkSystem(String answerLanguage) {
        return "A reader is about to read a few chapters of a book on the way to one idea, and may know some of "
                + "them already. For each chapter you are given, write one multiple-choice question that shows "
                + "whether the reader already knows what that chapter teaches: a small concrete case from everyday "
                + "life, in one or two sentences, not a definition and not a quote. Someone who knows the idea "
                + "answers it in 15 seconds; someone who does not cannot guess it. Do not name the book, its "
                + "author or the chapter. Four options of at most 12 words each: exactly one right, three "
                + "plausible but wrong, no \"all of the above\" or \"none of the above\". Write in "
                + language(answerLanguage) + ".\n"
                + "Answer with one JSON object and nothing else: {\"questions\": [{\"n\": <the chapter's n>, "
                + "\"q\": \"...\", \"options\": [\"...\", \"...\", \"...\", \"...\"], \"right\": <0 to 3: the "
                + "place of the right option>}]}, one per chapter, in order.";
    }

    /**
     * @param chapters the path chapters to check; a chapter's n is its place in this list
     */
    public static String checkUser(String bookTitle, String goal, List<PathData.Chapter> chapters) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append("\n\n");
        }
        out.append("The idea the reader is after: ").append(PathText.clip(goal, 300)).append("\n\n")
                .append("The chapters to check, with what each is about and why it is on the reader's path:\n")
                .append("<chapters>\n");
        for (int i = 0; i < chapters.size(); i++) {
            final PathData.Chapter c = chapters.get(i);
            out.append("<chapter n=\"").append(i).append("\" title=\"").append(attr(c.title)).append("\">")
                    .append(PathText.clip(c.line, 400));
            if (!c.why.isEmpty()) {
                out.append(" (needed: ").append(PathText.clip(c.why, 200)).append(')');
            }
            out.append("</chapter>\n");
        }
        return out.append("</chapters>").toString();
    }

    /** One question of the check. */
    public static final class Question {
        public final String text;
        /** Four options. */
        public final List<String> options;
        /** The place of the right one, 0 to 3. */
        public final int right;

        public Question(String text, List<String> options, int right) {
            this.text = text;
            this.options = options;
            this.right = right;
        }

        /** The same question with its options in another order ({@code order} holds 0 to 3 once each). */
        public Question reordered(int[] order) {
            final List<String> moved = new ArrayList<>();
            int newRight = right;
            for (int i = 0; i < order.length; i++) {
                moved.add(options.get(order[i]));
                if (order[i] == right) {
                    newRight = i;
                }
            }
            return new Question(text, moved, newRight);
        }

        public JSONObject toJson() throws JSONException {
            return new JSONObject().put("text", text).put("options", new JSONArray(options)).put("right", right);
        }

        /** A stored question, or null when it is not a whole one. */
        public static Question fromJson(JSONObject o) {
            return o == null ? null : of(o.optString("text", ""), o.optJSONArray("options"), o.opt("right"));
        }

        static Question of(String text, JSONArray options, Object right) {
            final String q = text == null ? "" : text.trim();
            if (q.isEmpty() || options == null || options.length() != 4) {
                return null;
            }
            final List<String> list = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final String s = options.optString(i, "").trim();
                if (s.isEmpty()) {
                    return null;
                }
                list.add(s);
            }
            int r = -1;
            if (right instanceof Number) {
                r = ((Number) right).intValue();
            } else if (right instanceof String) {
                final String s = ((String) right).trim();
                // "B", or the right option's own words
                r = s.length() == 1 && s.charAt(0) >= 'A' && s.charAt(0) <= 'D' ? s.charAt(0) - 'A'
                        : s.length() == 1 && s.charAt(0) >= 'a' && s.charAt(0) <= 'd' ? s.charAt(0) - 'a'
                        : number(s) >= 0 ? number(s) : list.indexOf(s);
            }
            return r < 0 || r > 3 ? null : new Question(q, list, r);
        }
    }

    /** The questions by the chapter's n; a question that is not whole (no four options, no right one) is left out. */
    public static Map<Integer, Question> parseCheck(String answer) {
        final Map<Integer, Question> out = new LinkedHashMap<>();
        if (answer == null) {
            return out;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open >= 0 && close > open) {
            try {
                final JSONArray list = new JSONObject(answer.substring(open, close + 1)).optJSONArray("questions");
                for (int i = 0; list != null && i < list.length(); i++) {
                    question(list.optJSONObject(i), out);
                }
                if (list != null) {
                    return out;
                }
            } catch (JSONException e) {
                // cut off or broken: read the questions that are whole
            }
        }
        int i = answer.indexOf("\"questions\"");
        if (i < 0) {
            return out;
        }
        while ((i = answer.indexOf('{', i)) >= 0) {
            final int end = objectEnd(answer, i);
            if (end < 0) {
                break;
            }
            try {
                question(new JSONObject(answer.substring(i, end + 1)), out);
                i = end + 1;
            } catch (JSONException e) {
                i++;
            }
        }
        return out;
    }

    private static void question(JSONObject o, Map<Integer, Question> into) {
        if (o == null || !(o.opt("n") instanceof Number)) {
            return;
        }
        final int n = o.optInt("n", -1);
        final Question q = Question.of(o.optString("q", o.optString("question", "")), o.optJSONArray("options"),
                o.opt("right"));
        if (n >= 0 && q != null && !into.containsKey(n)) {
            into.put(n, q);
        }
    }

    // ------------------------------------------------------------------ shared

    static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language of the chapter lines" : answerLanguage.trim();
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String join(JSONArray words) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length(); i++) {
            final String w = words.optString(i, "").trim();
            if (!w.isEmpty()) {
                out.append(out.length() > 0 ? ", " : "").append(w);
            }
        }
        return out.toString();
    }

    private static String unescape(String raw) {
        try {
            return new JSONObject("{\"s\":\"" + raw + "\"}").getString("s").trim();
        } catch (JSONException e) {
            return raw.trim();
        }
    }

    static int objectEnd(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String attr(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("<", "(").replace(">", ")").replaceAll("\\s+", " ").trim();
    }
}
