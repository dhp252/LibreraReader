package com.foobnix.assist.useit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The one request of "Use it": the chapter's ideas (or, without key passages, its text) and the
 * reader's answer in, one small experiment out. Also reads the answer, tolerant of fences, text
 * around the JSON and a few other shapes.
 */
public final class UsePrompts {

    /** Characters of a chapter's text sent at most (as pre-questions). */
    public static final int MAX_CHARS = 40_000;
    /** Less text than this is no chapter to take an idea from. */
    public static final int MIN_CHARS = 400;
    /** Ideas of a chapter sent at most. */
    static final int MAX_IDEAS = 60;
    /** Marks sent per idea. */
    static final int QUOTES_PER_IDEA = 2;
    /** Characters of one mark sent at most. */
    static final int QUOTE_CHARS = 400;
    /** Characters of the reader's answer sent at most. */
    static final int ANSWER_CHARS = 1500;

    /** One idea of the chapter as it is sent: its kind, the idea, and the book's words that carry it. */
    public static final class IdeaLine {
        final String kind;
        final String text;
        final List<String> quotes;

        public IdeaLine(String kind, String text, List<String> quotes) {
            this.kind = kind;
            this.text = text;
            this.quotes = quotes;
        }
    }

    private UsePrompts() {
    }

    /** @param answerLanguage the reader's choice; empty means the language of the reader's answer */
    public static String system(String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language of the reader's answer" : answerLanguage.trim();
        return "You help a reader put one idea of a book chapter to the test in their own work or life. You get "
                + "the chapter (its ideas with the book's own words, or its text) and the reader's answer to the "
                + "question \"Where would this change something you're doing this month?\"\n"
                + "Pick the ONE idea of the chapter that bears most on the reader's answer, and design one small "
                + "experiment the reader can run for about two weeks with what they already have: one change, "
                + "nothing to buy, nobody to persuade beyond their own team.\n"
                + "Answer with JSON only, no Markdown: {\"idea\":\"...\",\"try\":\"...\",\"predicts\":\"...\","
                + "\"measures\":[{\"name\":\"Tickets closed a week\",\"scale\":\"\",\"higher_is_better\":true}],"
                + "\"warning\":{\"text\":\"...\",\"quote\":\"...\"}}\n"
                + "- idea: the idea used, one sentence, as the book means it.\n"
                + "- try: exactly one sentence starting with a verb: the change to make, in the reader's own "
                + "situation and words (\"Keep each person on one job for a whole week instead of rotating "
                + "daily.\").\n"
                + "- predicts: exactly one sentence: what the book says should happen if its idea holds. The "
                + "book's claim, not your opinion; do not name the author.\n"
                + "- measures: 1 to " + UseExperiment.MAX_MEASURES + " things the reader can count or rate "
                + "themselves in a minute, now and again during the experiment. name: at most 5 words, with its "
                + "period when it is a count (\"Task switches a day\"). scale: only for a rating, like \"1-5\", "
                + "else \"\". higher_is_better: true when a higher value is the good direction. When the chapter "
                + "names a cost or side effect of the idea, make one measure watch for it.\n"
                + "- warning: only when the material you were given itself limits or cautions against the idea in "
                + "a way that matters for this experiment: text is one sentence saying what to watch for; quote "
                + "is the book's words for it copied exactly, letter for letter, from the material (5 to 30 "
                + "words), or \"\" when you cannot copy them exactly. Otherwise \"warning\": null. Never invent "
                + "a quote.\n"
                + "If the reader's answer is too vague to act on, still make the most likely experiment for it. "
                + "Write idea, try, predicts, the measure names and the warning text in " + lang + ".";
    }

    /**
     * @param material the chapter's ideas ({@link #ideas}) or its text
     * @param isIdeas  which of the two {@code material} is
     */
    public static String user(String bookTitle, String author, String chapterTitle, String material, boolean isIdeas,
                              String answer) {
        final StringBuilder out = new StringBuilder();
        line(out, "Book: ", bookTitle);
        line(out, "Author: ", author);
        line(out, "Chapter: ", chapterTitle);
        out.append("\nThe reader's answer: ").append(cut(oneLine(answer), ANSWER_CHARS)).append("\n\n");
        out.append(isIdeas ? "The chapter's ideas, each with the book's own words:\n" : "The chapter's text:\n");
        out.append(material == null ? "" : material.length() > MAX_CHARS ? material.substring(0, MAX_CHARS) : material);
        return out.toString();
    }

    private static void line(StringBuilder out, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            out.append(name).append(value.trim()).append('\n');
        }
    }

    /** The ideas of a chapter as lines: "3 [caveat]: the idea", then up to two of its marks' words. */
    public static String ideas(List<IdeaLine> ideas) {
        final StringBuilder out = new StringBuilder();
        int n = 0;
        for (IdeaLine idea : ideas) {
            final String text = oneLine(idea.text);
            if (text.isEmpty()) {
                continue;
            }
            if (++n > MAX_IDEAS || out.length() > MAX_CHARS) {
                break;
            }
            out.append(n).append(" [").append(idea.kind == null || idea.kind.isEmpty() ? "main" : idea.kind)
                    .append("]: ").append(text).append('\n');
            int quotes = 0;
            for (String quote : idea.quotes) {
                if (quotes >= QUOTES_PER_IDEA) {
                    break;
                }
                final String q = cut(oneLine(quote), QUOTE_CHARS);
                if (!q.isEmpty()) {
                    out.append("   \"").append(q).append("\"\n");
                    quotes++;
                }
            }
        }
        return out.toString();
    }

    /**
     * The experiment of an answer, not started yet (no id, no days); null when the answer has no
     * JSON object or no "try". Measures without a name are left out, more than three are cut.
     */
    public static UseExperiment parse(String answer) {
        final JSONObject o = object(answer);
        if (o == null) {
            return null;
        }
        final UseExperiment e = new UseExperiment();
        e.tryText = sentence(first(o, "try", "experiment", "action", "do"));
        if (e.tryText.isEmpty()) {
            return null;
        }
        e.idea = sentence(first(o, "idea", "idea_used", "ideaUsed"));
        e.predicts = sentence(first(o, "predicts", "prediction", "predict"));
        final JSONArray list = o.optJSONArray("measures");
        for (int i = 0; list != null && i < list.length() && e.measures.size() < UseExperiment.MAX_MEASURES; i++) {
            final UseExperiment.Measure m = measure(list.opt(i));
            if (m != null) {
                e.measures.add(m);
            }
        }
        final Object warning = o.opt("warning");
        if (warning instanceof JSONObject) {
            final JSONObject w = (JSONObject) warning;
            e.warning = sentence(first(w, "text", "warning", "note"));
            e.quote = unquote(first(w, "quote", "words"));
        } else if (warning instanceof String) {
            e.warning = sentence((String) warning);
        }
        if (e.quote.isEmpty()) {
            e.quote = unquote(first(o, "warning_quote", "quote"));
        }
        return e;
    }

    private static UseExperiment.Measure measure(Object item) {
        if (item instanceof String) {
            final String name = UseExperiment.clean((String) item);
            return name.isEmpty() ? null : new UseExperiment.Measure(name, "", true);
        }
        if (!(item instanceof JSONObject)) {
            return null;
        }
        final JSONObject m = (JSONObject) item;
        final String name = UseExperiment.clean(first(m, "name", "measure", "what"));
        if (name.isEmpty()) {
            return null;
        }
        // "1-5" reads better with a dash that does not look like a minus
        final String scale = UseExperiment.clean(first(m, "scale", "unit")).replaceAll("(\\d)\\s*-\\s*(\\d)", "$1–$2");
        boolean higher = true;
        for (String key : new String[]{"higher_is_better", "higherIsBetter", "higher_better", "higher"}) {
            if (m.has(key) && !m.isNull(key)) {
                final Object v = m.opt(key);
                higher = v instanceof Boolean ? (Boolean) v : !"false".equalsIgnoreCase(String.valueOf(v).trim());
                break;
            }
        }
        return new UseExperiment.Measure(name, scale, higher);
    }

    /**
     * Settles the warning's quote once the book's text was searched for it.
     *
     * @param found the quote in the words of the book, or null when it is not in the text
     */
    public static void settleQuote(UseExperiment e, String found) {
        if (found != null && !found.trim().isEmpty()) {
            e.quote = found.trim();
            e.quoted = true;
        } else {
            e.quoted = false;
        }
    }

    /**
     * The warning as shown: its sentence, and the book's words in quote marks only when they were
     * found in the book. Words not found are never shown as a quote; they stand in, without marks,
     * only when there is no sentence. Empty when there is no warning.
     */
    public static String warningLine(UseExperiment e) {
        final String text = e.warning == null ? "" : e.warning.trim();
        final String quote = e.quote == null ? "" : e.quote.trim();
        if (e.quoted && !quote.isEmpty()) {
            return text.isEmpty() ? "“" + quote + "”" : text + " “" + quote + "”";
        }
        return text.isEmpty() ? quote : text;
    }

    // ------------------------------------------------------------------ reading the answer

    /** The JSON object of an answer: the whole answer, or what stands between its first { and last }. */
    static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return null;
        }
        try {
            final JSONObject o = new JSONObject(answer.substring(open, close + 1));
            // {"experiment": {...}} is accepted too
            final JSONObject inner = o.optJSONObject("experiment");
            return inner != null && !o.has("try") ? inner : o;
        } catch (JSONException e) {
            return null;
        }
    }

    /** The first of the keys that holds text. */
    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.isNull(key)) {
                final Object v = o.opt(key);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    return (String) v;
                }
            }
        }
        return "";
    }

    private static String sentence(String s) {
        return UseExperiment.clean(s);
    }

    /** A quote without the marks the model may have put around it. */
    static String unquote(String s) {
        String q = UseExperiment.clean(s);
        while (q.length() > 1 && "\"“”„«»'‘’".indexOf(q.charAt(0)) >= 0) {
            q = q.substring(1).trim();
        }
        while (q.length() > 1 && "\"“”„«»'‘’".indexOf(q.charAt(q.length() - 1)) >= 0) {
            q = q.substring(0, q.length() - 1).trim();
        }
        return q;
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** At most {@code max} characters, cut at a space, with "…". */
    static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = s.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return s.substring(0, end).trim() + "…";
    }
}
