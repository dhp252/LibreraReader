package com.foobnix.assist.review;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one request of a review session, sent when the reader taps Start: for each due idea, the
 * idea, its book's title and the other ideas shown as wrong options. Only stored ideas go out,
 * never any of a book's text. Back come, per card, a short everyday scenario, the "Right:"
 * explanation and the "Not quite." one. The answer is read tolerantly; whatever cannot be read
 * leaves its card without a scenario. No Android here.
 */
public final class ReviewPrompts {

    /** Characters of one idea sent at most. */
    static final int MAX_IDEA = 300;

    private ReviewPrompts() {
    }

    /** What the request wrote for one card. */
    public static final class Texts {
        public final String scenario;
        public final String right;
        public final String notQuite;

        Texts(String scenario, String right, String notQuite) {
            this.scenario = scenario;
            this.right = right;
            this.notQuite = notQuite;
        }
    }

    public static String system(String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the ideas are written in" : answerLanguage.trim();
        return "You write review questions for a reader, about ideas from books they have read. They are not "
                + "flashcards: each card describes a situation and the reader picks the idea that explains it.\n"
                + "For each numbered card you get the book, the idea to review and other ideas of the reader's "
                + "books that will be shown beside it as wrong options. You are given no text of any book: do not "
                + "quote a book, and do not state facts about a book that the ideas do not carry.\n"
                + "Write for each card:\n"
                + "- \"scenario\": a short everyday situation from ordinary work or life today (2 or 3 sentences, "
                + "at most 50 words) in which the idea is at play, ending with a question such as \"Which idea "
                + "explains it best?\". It must not be the book's own example, must not name the idea or reuse its "
                + "key words, and must fit the idea clearly better than each of the other ideas.\n"
                + "- \"right\": one or two short sentences shown after the right answer: why the idea explains "
                + "the situation.\n"
                + "- \"not_quite\": one or two short sentences shown after a wrong answer: what in the situation "
                + "points to the idea. Kind, never scolding.\n"
                + "Write in " + lang + ". Answer with JSON only, no other text: {\"cards\":[{\"n\":1,"
                + "\"scenario\":\"...\",\"right\":\"...\",\"not_quite\":\"...\"}]}, one entry per card, with the "
                + "cards' own numbers.";
    }

    public static String user(List<ReviewCards.Card> cards) {
        final StringBuilder out = new StringBuilder();
        out.append("Cards: ").append(cards.size()).append('\n');
        for (int i = 0; i < cards.size(); i++) {
            final ReviewCards.Card c = cards.get(i);
            out.append("\nCard ").append(i + 1).append('\n');
            out.append("Book: ").append(c.entry.bookTitle);
            if (!c.entry.author.isEmpty()) {
                out.append(" (").append(c.entry.author).append(')');
            }
            out.append('\n');
            out.append("Idea to review: ").append(cut(c.entry.item.idea)).append('\n');
            final List<String> others = c.others();
            if (!others.isEmpty()) {
                out.append("Other ideas shown as wrong options:\n");
                for (String o : others) {
                    out.append("- ").append(cut(o)).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static String cut(String s) {
        final String one = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return one.length() > MAX_IDEA ? one.substring(0, MAX_IDEA) : one;
    }

    /**
     * The texts by card number (1-based). Reads {@code {"cards": [...]}}, a bare list, or an object
     * keyed by number, with or without a code fence or words around it; an entry without a number
     * takes its place in the list. Never throws: what cannot be read is left out.
     */
    public static Map<Integer, Texts> parse(String reply) {
        final Map<Integer, Texts> out = new LinkedHashMap<>();
        if (reply == null) {
            return out;
        }
        try {
            JSONArray list = null;
            final JSONObject root = object(reply);
            if (root != null) {
                for (String name : new String[]{"cards", "items", "questions", "scenarios"}) {
                    if (list == null) {
                        list = root.optJSONArray(name);
                    }
                }
                if (list == null) {
                    // {"1": {...}, "2": {...}}, or one card on its own
                    final Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        final String k = keys.next();
                        final JSONObject o = root.optJSONObject(k);
                        final int n = number(k);
                        if (o != null && n > 0) {
                            put(out, n, o);
                        }
                    }
                    if (out.isEmpty()) {
                        put(out, Math.max(1, number(first(root, "n", "card", "id", "number"))), root);
                    }
                    return out;
                }
            } else {
                list = array(reply);
            }
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    final JSONObject o = list.optJSONObject(i);
                    if (o != null) {
                        final int n = number(first(o, "n", "card", "id", "number"));
                        put(out, n > 0 ? n : i + 1, o);
                    }
                }
            }
        } catch (Exception e) {
            // an answer that is not what was asked for: no scenarios
        }
        return out;
    }

    private static void put(Map<Integer, Texts> out, int n, JSONObject o) {
        final String scenario = first(o, "scenario", "question", "situation");
        if (scenario.isEmpty() || out.containsKey(n)) {
            return;
        }
        out.put(n, new Texts(scenario, lead(first(o, "right", "correct", "why"), "right"),
                lead(first(o, "not_quite", "notQuite", "not quite", "wrong"), "not quite")));
    }

    /** The first of these fields that holds text. */
    private static String first(JSONObject o, String... names) {
        for (String name : names) {
            final Object v = o.opt(name);
            if (v != null && v != JSONObject.NULL && !(v instanceof JSONObject) && !(v instanceof JSONArray)) {
                final String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    /** A text without a "Right:" or "Not quite." the screen writes itself. */
    static String lead(String text, String word) {
        String s = text.trim();
        if (s.toLowerCase(java.util.Locale.ROOT).startsWith(word)) {
            final String rest = s.substring(word.length()).trim();
            if (rest.startsWith(":") || rest.startsWith(".") || rest.startsWith("!") || rest.startsWith(",")) {
                s = rest.substring(1).trim();
            }
        }
        return s;
    }

    private static int number(String s) {
        final String digits = s == null ? "" : s.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() || digits.length() > 4 ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The JSON object in a reply (the widest braces), or null when the reply is a list or holds none. */
    static JSONObject object(String reply) {
        final int brace = reply.indexOf('{');
        final int bracket = reply.indexOf('[');
        final int end = reply.lastIndexOf('}');
        if (brace < 0 || end <= brace || (bracket >= 0 && bracket < brace)) {
            return null;
        }
        try {
            return new JSONObject(reply.substring(brace, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    static JSONArray array(String reply) {
        final int from = reply.indexOf('[');
        final int to = reply.lastIndexOf(']');
        if (from < 0 || to <= from) {
            return null;
        }
        try {
            return new JSONArray(reply.substring(from, to + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
