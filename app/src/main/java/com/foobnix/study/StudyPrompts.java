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

    // --- check ---

    /** The rules of a check: feedback on the answers, then the chapter as a branch of the outline. */
    public static String checkSystem(String answerLanguage, boolean hasGoal) {
        final StringBuilder out = new StringBuilder();
        out.append("You check what a reader remembers of a chapter they just read, and you turn the chapter ")
                .append("into a branch of an outline of the book. Write in ").append(language(answerLanguage)).append(".\n")
                .append("Answer in this layout and nothing else:\n")
                .append("## Feedback\n")
                .append("One line per question: \"<n>. <verdict> - <one or two sentences>\", verdict being right, ")
                .append("partly or missed: what the reader got right, what is missing or wrong, kindly and concretely. ")
                .append("Under a line, when it helps, a line \"> <one sentence copied exactly from the chapter>\" ")
                .append("that holds the answer.\n")
                .append("## Recall\n")
                .append("Only when the reader wrote what else they remember: one or two sentences on the key points ")
                .append("of the chapter they did not mention; when the chapter's ideas are listed, name the ones ")
                .append("they left out in the words of that list.\n")
                .append("## Map\n")
                .append("A JSON array in a ```json fence: the chapter's 2 to 5 main claims, each {\"text\": \"<the ")
                .append("claim in at most 15 words>\", \"cat\": \"main\", \"quote\": \"<one sentence copied exactly ")
                .append("from the chapter>\", \"kids\": [...]}, with 0 to 4 kids of the same form whose cat is ")
                .append("evidence, concept, caveat or example");
        if (hasGoal) {
            out.append(", or apply (something the reader could act on for their goal)");
        }
        out.append(". Where the reader's marks, notes, the key passages or the chapter's ideas show what matters, follow them. ")
                .append("Quotes are copied character for character, no ellipses; leave a quote empty rather than ")
                .append("paraphrase it.");
        return out.toString();
    }

    /**
     * @param text       the chapter, or the summaries of its parts when {@code summarized}
     * @param marks      the reader's marks in the chapter, one per line ("quote" + note)
     * @param passages   the AI key passages of the chapter, "[category] quote"
     * @param ideas      the chapter's ideas as marked in the book, "[category] idea", or none
     */
    public static String check(String bookTitle, String chapterTitle, String goal, String text, boolean summarized,
                               List<String> questions, List<String> answers, String recall,
                               List<String> marks, List<String> passages, List<String> ideas) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (chapterTitle != null && !chapterTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(chapterTitle.trim()).append('\n');
        }
        if (goal != null && !goal.trim().isEmpty()) {
            out.append("The reader's goal: ").append(goal.trim()).append('\n');
        }
        out.append('\n');
        if (summarized) {
            out.append("The chapter is too long to send whole: these are summaries of its parts, in order. ")
                    .append("Quotes can only come from the reader's marks and the key passages below.\n");
        }
        out.append("<chapter>\n").append(text).append("\n</chapter>\n\n");
        if (marks != null && !marks.isEmpty()) {
            out.append("The reader's marks and notes in this chapter:\n");
            for (String m : marks) {
                out.append("- ").append(m).append('\n');
            }
            out.append('\n');
        }
        if (passages != null && !passages.isEmpty()) {
            out.append("Key passages the AI picked in this chapter:\n");
            for (String p : passages) {
                out.append("- ").append(p).append('\n');
            }
            out.append('\n');
        }
        if (ideas != null && !ideas.isEmpty()) {
            out.append("The ideas of this chapter, as marked in the reader's book:\n");
            for (String i : ideas) {
                out.append("- ").append(i).append('\n');
            }
            out.append('\n');
        }
        boolean answered = recall != null && !recall.trim().isEmpty();
        final StringBuilder qa = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            final String a = i < answers.size() && answers.get(i) != null ? answers.get(i).trim() : "";
            answered |= !a.isEmpty();
            qa.append(i + 1).append(". Q: ").append(questions.get(i)).append('\n')
                    .append("   A: ").append(a.isEmpty() ? "(no answer)" : a).append('\n');
        }
        if (!answered) {
            out.append("The reader did not answer: write only the ## Map section.");
            return out.toString();
        }
        out.append("The questions and the reader's answers:\n").append(qa);
        if (recall != null && !recall.trim().isEmpty()) {
            out.append("\nWhat else the reader remembers:\n").append(recall.trim()).append('\n');
        }
        return out.toString();
    }

    /** A check answer read back. */
    public static final class Check {
        public final List<StudyBook.Item> items = new ArrayList<>();
        /** The Feedback section as written. */
        public String feedback = "";
        public String recall = "";
        public final List<StudyBook.Node> branch = new ArrayList<>();
    }

    private static final Pattern VERDICT = Pattern.compile(
            "^\\s*(\\d+)\\s*[.)]\\s*\\**\\s*(right|partly|missed)\\s*\\**\\s*[-—–:]*\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    public static Check parseCheck(String answer) {
        final Check check = new Check();
        if (answer == null) {
            return check;
        }
        final String text = answer.replace("\r", "");
        final int map = heading(text, "map");
        final String head = map < 0 ? text : text.substring(0, map);
        final int recall = heading(head, "recall");
        final int feedback = heading(head, "feedback");
        final String feedbackText = section(head, feedback, recall);
        check.feedback = feedbackText.trim();
        check.recall = recall < 0 ? "" : section(head, recall, -1).trim();

        StudyBook.Item last = null;
        for (String line : feedbackText.split("\n")) {
            final Matcher m = VERDICT.matcher(line);
            if (m.matches()) {
                last = new StudyBook.Item(Integer.parseInt(m.group(1)) - 1, m.group(2).toLowerCase(java.util.Locale.ROOT),
                        m.group(3).trim(), "");
                check.items.add(last);
            } else if (last != null && line.trim().startsWith(">")) {
                last.quote = unquote(line.trim().substring(1));
            } else if (last != null && !line.trim().isEmpty()) {
                last.quote = last.quote == null ? "" : last.quote;
                final StudyBook.Item longer = new StudyBook.Item(last.question, last.verdict,
                        (last.note + " " + line.trim()).trim(), last.quote);
                check.items.set(check.items.size() - 1, longer);
                last = longer;
            }
        }
        if (map >= 0) {
            parseBranch(text.substring(map), check.branch);
        }
        return check;
    }

    /** What to show of a check while it streams: the feedback and recall, not the map's JSON. */
    public static String visible(String soFar) {
        if (soFar == null) {
            return "";
        }
        String text = soFar.replace("\r", "");
        final int map = heading(text, "map");
        if (map >= 0) {
            text = text.substring(0, map);
        }
        final int fence = text.indexOf("```");
        if (fence >= 0) {
            text = text.substring(0, fence);
        }
        final StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.trim().startsWith("## ")) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString().trim();
    }

    private static void parseBranch(String mapSection, List<StudyBook.Node> into) {
        final int open = mapSection.indexOf('[');
        final int close = mapSection.lastIndexOf(']');
        if (open < 0) {
            return;
        }
        try {
            if (close <= open) {
                throw new JSONException("cut off");
            }
            nodes(new JSONArray(mapSection.substring(open, close + 1)), into, 0);
        } catch (JSONException e) {
            // cut off: the claims that came whole
            into.clear();
            int i = open + 1;
            while ((i = mapSection.indexOf('{', i)) >= 0) {
                final int end = objectEnd(mapSection, i);
                if (end < 0) {
                    break;
                }
                try {
                    node(new JSONObject(mapSection.substring(i, end + 1)), into, 0);
                } catch (JSONException ignored) {
                    // a broken claim: the others count
                }
                i = end + 1;
            }
        }
    }

    private static void nodes(JSONArray array, List<StudyBook.Node> into, int depth) {
        for (int i = 0; array != null && i < array.length(); i++) {
            node(array.optJSONObject(i), into, depth);
        }
    }

    private static void node(JSONObject o, List<StudyBook.Node> into, int depth) {
        if (o == null || depth > 2) {
            return;
        }
        final String text = o.optString("text", "").trim();
        if (text.isEmpty()) {
            return;
        }
        final com.foobnix.ai.AiCategory category = com.foobnix.ai.AiCategory.of(o.optString("cat", ""));
        final String cat = category != null ? category.code : depth == 0 ? "main" : "evidence";
        final StudyBook.Node node = new StudyBook.Node(text, cat, unquote(o.optString("quote", "")));
        nodes(o.optJSONArray("kids"), node.kids, depth + 1);
        into.add(node);
    }

    /** Where the "## name" heading starts (any case), or -1. */
    private static int heading(String text, String name) {
        final Matcher m = Pattern.compile("(?im)^\\s*#{1,3}\\s*" + name + "\\b").matcher(text);
        return m.find() ? m.start() : -1;
    }

    /** The text after the heading at {@code from} up to {@code to} (-1: the end). */
    private static String section(String text, int from, int to) {
        if (from < 0) {
            return to < 0 ? text : text.substring(0, to);
        }
        final int body = text.indexOf('\n', from);
        if (body < 0) {
            return "";
        }
        final int end = to > from ? to : text.length();
        return body + 1 <= end ? text.substring(body + 1, end) : "";
    }

    private static String unquote(String s) {
        String q = s == null ? "" : s.trim();
        if (q.length() >= 2 && (q.startsWith("\"") && q.endsWith("\"") || q.startsWith("“") && q.endsWith("”"))) {
            q = q.substring(1, q.length() - 1).trim();
        }
        return q;
    }

    // --- looking back at the map ---

    public static String recallSystem(String answerLanguage) {
        return "A reader studied a book and kept an outline of it. Some time later they write what they still "
                + "remember of it. Compare what they wrote with the outline. Write in " + language(answerLanguage)
                + ". First one sentence on what they kept well. Then 2 to 5 short lines starting with \"- \", "
                + "each naming an idea of the outline they left out or got wrong, with its chapter number. "
                + "Plain text, no headings, nothing else.";
    }

    public static String recall(String bookTitle, String outline, String remembered) {
        return (bookTitle == null || bookTitle.trim().isEmpty() ? "" : "Book: " + bookTitle.trim() + "\n\n")
                + "<outline>\n" + outline + "\n</outline>\n\n"
                + "What the reader remembers:\n" + (remembered == null ? "" : remembered.trim());
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
