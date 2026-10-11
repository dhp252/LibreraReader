package com.foobnix.ai;

import java.util.ArrayList;
import java.util.List;

/** What is asked of the model, and reading its answers back. */
public final class AiPrompts {

    private AiPrompts() {
    }

    /**
     * The user's master prompt before Librera's own instructions: the built-in rules (the exact
     * quotes of key passages, the answer language) come last and keep their force.
     */
    public static String withMaster(String master, String system) {
        if (master == null || master.trim().isEmpty()) {
            return system;
        }
        return master.trim() + "\n\n" + system;
    }

    /** @param answerLanguage the reader's choice; empty means the language of the book */
    public static String summarySystem(String answerLanguage) {
        return "You summarize parts of a book for someone who is reading it. "
                + "Write in " + language(answerLanguage) + ". "
                + "Stay faithful to the text you are given: no outside knowledge about the book, "
                + "and nothing from parts of it you were not given. "
                + "Plain prose in short paragraphs; no headings, tables or Markdown.";
    }

    /** What a chapter summary is, also when the summaries of its parts are joined. */
    public static final String CHAPTER_TASK = "Summarize this chapter in 6 to 10 sentences: what happens "
            + "or what is argued, the people or ideas that matter, and why it matters for the rest of "
            + "the book. Skip filler such as \"This chapter describes\".";
    /** What a book summary is, also when the summaries of its parts are joined. */
    public static final String BOOK_TASK = "Summarize the whole book: its central conflict or thesis, "
            + "the main characters or ideas and what drives them, how it develops and ends, and 3 to 5 themes.";

    /** Summaries of parts too many for one request: a few at a time into one, a step towards the whole. */
    public static final String PARTS_TASK = "Summarize them together as one part in 5 to 8 sentences, keeping "
            + "names, events and claims a later summary of the whole would need.";

    public static String chapterSummary(String bookTitle, String chapterTitle, String text) {
        return header(bookTitle, chapterTitle) + CHAPTER_TASK + "\n\n<chapter>\n" + text + "\n</chapter>";
    }

    public static String bookSummary(String bookTitle, String text) {
        return header(bookTitle, null) + BOOK_TASK + "\n\n<book>\n" + text + "\n</book>";
    }

    /** First step of a long text: one part, summarized so that the parts can be joined. */
    public static String partSummary(String bookTitle, String partTitle, String text) {
        return header(bookTitle, partTitle)
                + "This is one part of a longer text. Summarize it in 5 to 8 sentences, keeping names, "
                + "events and claims a later summary of the whole would need.\n\n"
                + "<part>\n" + text + "\n</part>";
    }

    /** Second step of a long text: the part summaries, in order, into one summary. */
    public static String joinSummaries(String bookTitle, String task, List<String> parts) {
        final StringBuilder out = new StringBuilder(header(bookTitle, null));
        out.append("These are summaries of consecutive parts of the text, in order. ").append(task).append("\n\n");
        for (int i = 0; i < parts.size(); i++) {
            out.append("<part n=\"").append(i + 1).append("\">\n").append(parts.get(i)).append("\n</part>\n");
        }
        return out.toString();
    }

    /**
     * The rules of key passages: categories with their tests, the answer as JSON objects. Without
     * a reading goal "apply" is never used.
     */
    public static String keyPassagesSystem(boolean hasGoal) {
        final StringBuilder out = new StringBuilder();
        out.append("You pick the passages of a text that matter most for understanding it, and say why ")
                .append("each one matters with a category. The categories, what they mark and the test ")
                .append("each passage must pass:\n");
        for (AiCategory category : AiCategory.values()) {
            if (category == AiCategory.APPLY && !hasGoal) {
                continue;
            }
            out.append("- ").append(category.code).append(" (").append(category.promptName).append("): ")
                    .append(category.promptMeaning).append(" Test: \"").append(category.promptTest).append("\"\n");
        }
        if (!hasGoal) {
            out.append("The reader gave no goal: never use the category apply.\n");
        }
        out.append("Use only these categories, one per passage, the one whose test it passes best. ")
                .append("Answer with a JSON array of objects and nothing else, each ")
                .append("{\"category\": \"<category>\", \"quote\": \"<sentence>\"}. The quote is one sentence ")
                .append("copied exactly from the text, character for character: no changes, no ellipses, ")
                .append("no quotation marks added. Never pick headings, captions, page numbers or lines ")
                .append("of a table of contents.");
        return out.toString();
    }

    /** @param goal what the reader wants from the book; empty when none was given */
    public static String keyPassages(String bookTitle, String chapterTitle, String text, int count, String goal) {
        final StringBuilder out = new StringBuilder(header(bookTitle, chapterTitle));
        if (goal != null && !goal.trim().isEmpty()) {
            out.append("The reader's goal: ").append(goal.trim()).append("\n\n");
        }
        out.append("Pick about ").append(count).append(" passages of this text, as many of each category as ")
                .append("the text really has (none is fine). Copy each one exactly.\n\n")
                .append("<text>\n").append(text).append("\n</text>");
        return out.toString();
    }

    /** How many key passages to ask for: about one per 3000 characters, 3 to 12. */
    public static int passageCount(int textLength) {
        return Math.max(3, Math.min(12, textLength / 3000));
    }

    private static String header(String bookTitle, String partTitle) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (partTitle != null && !partTitle.trim().isEmpty()) {
            out.append("Part: ").append(partTitle.trim()).append('\n');
        }
        return out.length() > 0 ? out.append('\n').toString() : "";
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in"
                : answerLanguage.trim();
    }

    /** A passage the model picked, with its category. */
    public static final class Picked {
        public final AiCategory category;
        public final String quote;

        Picked(AiCategory category, String quote) {
            this.category = category;
            this.quote = quote;
        }
    }

    /**
     * The passages of a key-passages answer: the JSON objects of its array, also in a code fence,
     * with prose around or cut off at the end. Objects with an unknown category or no quote are
     * left out.
     */
    public static List<Picked> parsePicked(String answer) {
        final List<Picked> picked = new ArrayList<>();
        if (answer == null) {
            return picked;
        }
        final int open = answer.indexOf('[');
        if (open < 0) {
            return picked;
        }
        int i = open + 1;
        final int n = answer.length();
        while (i < n) {
            final char c = answer.charAt(i);
            if (c == ']') {
                break;
            }
            if (c != '{') {
                i++;
                continue;
            }
            final int end = objectEnd(answer, i);
            if (end < 0) {
                break;
            }
            try {
                final org.json.JSONObject o = new org.json.JSONObject(answer.substring(i, end + 1));
                final AiCategory category = AiCategory.of(o.optString("category", null));
                final String quote = o.optString("quote", "").trim();
                if (category != null && !quote.isEmpty()) {
                    picked.add(new Picked(category, quote));
                }
            } catch (org.json.JSONException e) {
                // a broken object: the others still count
            }
            i = end + 1;
        }
        return picked;
    }

    /** Where the object starting at {@code start} closes, strings and escapes skipped; -1 when it does not. */
    private static int objectEnd(String s, int start) {
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
