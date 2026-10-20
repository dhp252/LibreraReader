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

    /** How quotes of the book are copied: the app finds them in the book's words, so any change loses them. */
    private static final String QUOTE_RULES = "Every quote is copied exactly from the text, character for "
            + "character: no changes, no ellipses, no quotation marks added. Never quote headings, captions, "
            + "page numbers or lines of a table of contents.";

    /**
     * The rules of marking a text by its ideas (design-2026-10-21-layered-marks.md): every idea,
     * each with a category and its anchors in the text (key sentences, must-read paragraphs as
     * their first and last words). Without a reading goal "apply" is never used.
     *
     * @param answerLanguage the language of the ideas' own words; empty means the language of the book
     */
    public static String ideasSystem(boolean hasGoal, String answerLanguage) {
        final StringBuilder out = new StringBuilder();
        out.append("You map every idea of a text for someone studying it, so that a reader who reads only ")
                .append("what you mark misses nothing important. Go through the text section by section, in ")
                .append("order, and list every main idea (a claim, a step of the argument, a conclusion) and, ")
                .append("under it, the evidence, concepts, caveats and examples that matter for understanding ")
                .append("or believing it. Leave out only filler and repetition.\n")
                .append("Each idea has one category, the one whose test it passes best:\n");
        categories(out, hasGoal);
        out.append("Answer with a JSON array of objects in reading order and nothing else, each\n")
                .append("{\"id\": <1, 2, 3...>, \"category\": \"<category>\", \"idea\": \"<the idea in one short ")
                .append("sentence of your own>\", \"parent\": <id of the idea it supports, 0 for none>, ")
                .append("\"sentences\": [\"<key sentence>\"], \"paragraphs\": [{\"start\": \"<first words>\", ")
                .append("\"end\": \"<last words>\"}]}.\n")
                .append("sentences: the 1 to 3 sentences that carry the idea, each one whole sentence.\n")
                .append("paragraphs: the paragraphs a reader must read in full to get the idea, only where one ")
                .append("sentence is not enough; start is the paragraph's first 6 to 10 words, end its last 6 ")
                .append("to 10 words.\n")
                .append("Every idea needs at least one sentence or paragraph. Write the ideas in ")
                .append(language(answerLanguage)).append(". ").append(QUOTE_RULES);
        return out.toString();
    }

    /** @param goal what the reader wants from the book; empty when none was given */
    public static String ideas(String bookTitle, String chapterTitle, String text, String goal) {
        final StringBuilder out = new StringBuilder(header(bookTitle, chapterTitle));
        if (goal != null && !goal.trim().isEmpty()) {
            out.append("The reader's goal: ").append(goal.trim()).append("\n\n");
        }
        out.append("Map every idea of this text.\n\n")
                .append("<text>\n").append(text).append("\n</text>");
        return out.toString();
    }

    /** The second try for ideas none of whose quotes were found in the book. */
    public static String repairSystem() {
        return "You find where a text says given ideas. For each idea, copy from the text the 1 or 2 "
                + "sentences that say it most directly, each one whole sentence. If the text does not say "
                + "it, give no sentences. Answer with a JSON array and nothing else, each "
                + "{\"id\": <the idea's id>, \"sentences\": [\"<sentence>\"]}. " + QUOTE_RULES;
    }

    public static String repair(String bookTitle, String chapterTitle, String text, List<Idea> ideas) {
        final StringBuilder out = new StringBuilder(header(bookTitle, chapterTitle));
        out.append("The ideas:\n");
        for (Idea idea : ideas) {
            out.append(idea.id).append(". ").append(idea.text).append('\n');
        }
        out.append("\n<text>\n").append(text).append("\n</text>");
        return out.toString();
    }

    /** A tapped mark explained: what it means and why it matters where it stands. */
    public static String explainSystem(String answerLanguage) {
        return "You help someone studying a book understand one marked passage of it. In 3 to 5 sentences, "
                + "say what the passage means in plain words and why it matters for the idea it carries. "
                + "Stay with the text you are given; no outside knowledge about the book. "
                + "Write in " + language(answerLanguage) + ". Plain prose; no headings, lists or Markdown.";
    }

    /**
     * @param idea    the idea the mark carries, or empty (key passages from before ideas)
     * @param parent  the idea that one supports, or empty
     * @param context the paragraph around the passage, or other passages of its idea, or empty
     */
    public static String explain(String bookTitle, String chapterTitle, String idea, String parent,
                                 String passage, String context) {
        final StringBuilder out = new StringBuilder(header(bookTitle, chapterTitle));
        if (parent != null && !parent.trim().isEmpty()) {
            out.append("It supports: ").append(parent.trim()).append('\n');
        }
        if (idea != null && !idea.trim().isEmpty()) {
            out.append("The idea: ").append(idea.trim()).append("\n\n");
        }
        if (context != null && !context.trim().isEmpty() && !context.trim().equals(passage.trim())) {
            out.append("<context>\n").append(context.trim()).append("\n</context>\n\n");
        }
        out.append("Explain this passage:\n<passage>\n").append(passage.trim()).append("\n</passage>");
        return out.toString();
    }

    private static void categories(StringBuilder out, boolean hasGoal) {
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

    /** An idea of an ideas answer, with its anchors as the model wrote them. */
    public static final class Idea {
        public final int id;
        public final AiCategory category;
        public final String text;
        /** The id of the idea it supports, or -1. */
        public final int parent;
        public final List<String> sentences;
        /** Must-read paragraphs as {first words, last words}. */
        public final List<String[]> paragraphs;

        public Idea(int id, AiCategory category, String text, int parent, List<String> sentences, List<String[]> paragraphs) {
            this.id = id;
            this.category = category;
            this.text = text;
            this.parent = parent;
            this.sentences = sentences;
            this.paragraphs = paragraphs;
        }
    }

    /** An ideas answer: its whole ideas, and whether it was cut off before the array closed. */
    public static final class Ideas {
        public final List<Idea> ideas = new ArrayList<>();
        public boolean cutOff;
    }

    /**
     * The ideas of an ideas answer: the JSON objects of its array, also in a code fence, with
     * prose around or cut off at the end (whole objects only). Objects with an unknown category,
     * no id or no idea are left out; a parent of 0 or of the idea itself is none.
     */
    public static Ideas parseIdeas(String answer) {
        final Ideas out = new Ideas();
        out.cutOff = !objects(answer, o -> {
            final AiCategory category = AiCategory.of(o.optString("category", null));
            final int id = o.optInt("id", 0);
            final String text = o.optString("idea", "").trim();
            if (category == null || id <= 0 || text.isEmpty()) {
                return;
            }
            final int parent = o.optInt("parent", 0);
            final List<String[]> paragraphs = new ArrayList<>();
            final org.json.JSONArray p = o.optJSONArray("paragraphs");
            for (int k = 0; p != null && k < p.length(); k++) {
                final org.json.JSONObject para = p.optJSONObject(k);
                final String start = para == null ? "" : para.optString("start", "").trim();
                if (!start.isEmpty()) {
                    paragraphs.add(new String[]{start, para.optString("end", "").trim()});
                }
            }
            out.ideas.add(new Idea(id, category, text, parent > 0 && parent != id ? parent : -1,
                    strings(o.optJSONArray("sentences")), paragraphs));
        });
        return out;
    }

    /** A repair answer: the found sentences by idea id. */
    public static java.util.Map<Integer, List<String>> parseRepair(String answer) {
        final java.util.Map<Integer, List<String>> found = new java.util.LinkedHashMap<>();
        objects(answer, o -> {
            final int id = o.optInt("id", 0);
            final List<String> sentences = strings(o.optJSONArray("sentences"));
            if (id > 0 && !sentences.isEmpty()) {
                found.put(id, sentences);
            }
        });
        return found;
    }

    private static List<String> strings(org.json.JSONArray a) {
        final List<String> out = new ArrayList<>();
        for (int k = 0; a != null && k < a.length(); k++) {
            final String s = a.optString(k, "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private interface ObjectReader {
        void read(org.json.JSONObject o) throws org.json.JSONException;
    }

    /**
     * Hands each JSON object of the first array of an answer to {@code reader}: also in a code
     * fence, with prose around or cut off at the end (a broken object is skipped, the others
     * still count).
     *
     * @return whether the array closed: false when it was cut off (or there was none)
     */
    private static boolean objects(String answer, ObjectReader reader) {
        if (answer == null) {
            return false;
        }
        final int open = answer.indexOf('[');
        if (open < 0) {
            return false;
        }
        int i = open + 1;
        final int n = answer.length();
        while (i < n) {
            final char c = answer.charAt(i);
            if (c == ']') {
                return true;
            }
            if (c != '{') {
                i++;
                continue;
            }
            final int end = objectEnd(answer, i);
            if (end < 0) {
                return false;
            }
            try {
                reader.read(new org.json.JSONObject(answer.substring(i, end + 1)));
            } catch (org.json.JSONException e) {
                // a broken object: the others still count
            }
            i = end + 1;
        }
        return false;
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
