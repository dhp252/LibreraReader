package com.foobnix.study;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What study mode asks of the model, and reading its answers back. */
public final class StudyPrompts {

    private StudyPrompts() {
    }

    // --- preview ---

    /** @param answerLanguage the reader's choice; empty means the language of the book */
    public static String previewSystem(String answerLanguage) {
        return "You help a reader decide how to read a book before reading it, the way a skilled reader "
                + "skims: from the title, the length of each chapter and the opening and ending of each "
                + "chapter. Write in " + language(answerLanguage) + ".\n"
                + "For the whole book: its thesis, the main claim or story, in one or two sentences.\n"
                + "For each chapter:\n"
                + "- line: what the chapter is about, in one sentence of at most 20 words.\n"
                + "- label: how to read it for the reader's goal. \"read\": read closely, it holds ideas the "
                + "goal needs or that later chapters build on. \"skim\": the gist is enough (cases, history, "
                + "background, repetition). \"skip\": nothing the goal needs. Without a goal, judge by what "
                + "understanding the book's argument needs.\n"
                + "- reason: why that label, at most 15 words, concrete (\"mostly airline case studies\").\n"
                + "- questions: 2 or 3 short questions to keep in mind while reading the chapter, each "
                + "answerable from the chapter in a sentence or two.\n"
                + "Judge only from what you are given; when the skim is too thin to tell, choose \"read\".\n"
                + "Answer with one JSON object and nothing else: {\"thesis\": \"...\", \"chapters\": "
                + "[{\"n\": <the chapter's n>, \"line\": \"...\", \"label\": \"read|skim|skip\", "
                + "\"reason\": \"...\", \"questions\": [\"...\", \"...\"]}]}, one entry per chapter, in order.";
    }

    /** @param goal what the reader wants from the book; empty when none was given */
    public static String preview(String bookTitle, String goal, String pack) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        out.append(goal == null || goal.trim().isEmpty()
                ? "The reader gave no goal."
                : "The reader's goal: " + goal.trim()).append("\n\n");
        out.append("The skim of the book, chapter by chapter (words = the chapter's full length):\n\n")
                .append("<book>\n").append(pack).append("</book>");
        return out.toString();
    }

    /** One chapter of a preview answer. */
    public static final class Planned {
        public final String line;
        public final String label;
        public final String reason;
        public final List<String> questions;

        Planned(String line, String label, String reason, List<String> questions) {
            this.line = line;
            this.label = label;
            this.reason = reason;
            this.questions = questions;
        }
    }

    /** A preview answer: the thesis and the chapters by their {@code n}. */
    public static final class Preview {
        public String thesis = "";
        public final Map<Integer, Planned> chapters = new LinkedHashMap<>();
    }

    private static final Pattern THESIS = Pattern.compile("\"thesis\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * The preview in an answer: the whole JSON object, else whatever chapter objects can be read
     * (a code fence, prose around, an answer cut off at the end). Unknown labels read as "read";
     * at most {@link StudyRules#QUESTIONS_MAX} questions.
     */
    public static Preview parsePreview(String answer) {
        final Preview preview = new Preview();
        if (answer == null) {
            return preview;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open >= 0 && close > open) {
            try {
                final JSONObject root = new JSONObject(answer.substring(open, close + 1));
                preview.thesis = root.optString("thesis", "").trim();
                final JSONArray list = root.optJSONArray("chapters");
                for (int i = 0; list != null && i < list.length(); i++) {
                    addPlanned(preview, list.optJSONObject(i));
                }
                return preview;
            } catch (JSONException e) {
                // cut off or broken: read what can be read below
            }
        }
        final Matcher thesis = THESIS.matcher(answer);
        if (thesis.find()) {
            preview.thesis = unescape(thesis.group(1));
        }
        final int list = answer.indexOf("\"chapters\"");
        int i = list < 0 ? 0 : list;
        while ((i = answer.indexOf('{', i)) >= 0) {
            final int end = objectEnd(answer, i);
            if (end < 0) {
                break;
            }
            try {
                addPlanned(preview, new JSONObject(answer.substring(i, end + 1)));
                i = end + 1;
            } catch (JSONException e) {
                i++;
            }
        }
        return preview;
    }

    private static void addPlanned(Preview preview, JSONObject o) {
        if (o == null || !o.has("n")) {
            return;
        }
        final int n = o.optInt("n", -1);
        if (n < 0) {
            return;
        }
        final List<String> questions = new ArrayList<>();
        final JSONArray q = o.optJSONArray("questions");
        for (int k = 0; q != null && k < q.length() && questions.size() < StudyRules.QUESTIONS_MAX; k++) {
            final String s = q.optString(k, "").trim();
            if (!s.isEmpty()) {
                questions.add(s);
            }
        }
        preview.chapters.put(n, new Planned(o.optString("line", "").trim(), StudyBook.label(o.optString("label", "")),
                o.optString("reason", "").trim(), questions));
    }

    // --- shared ---

    static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the book is written in"
                : answerLanguage.trim();
    }

    private static String unescape(String jsonString) {
        try {
            return new JSONObject("{\"s\":\"" + jsonString + "\"}").getString("s").trim();
        } catch (JSONException e) {
            return jsonString.trim();
        }
    }

    /** Where the object starting at {@code start} closes, strings and escapes skipped; -1 when it does not. */
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
}
