package com.foobnix.assist.pictures;

import java.util.List;

/**
 * The one request of "Prose to pictures" for a chapter (or a selected passage): what is sent and
 * what the answer must look like. A chapter's stored key ideas with the book's words that mark
 * them are sent when it has them, else its text ({@link #MAX_CHARS} at most). Plain Java.
 */
public final class PicturePrompts {

    /** Characters of a chapter sent at most. */
    public static final int MAX_CHARS = 40_000;
    /** Less chapter text than this is not worth a request. */
    public static final int MIN_CHARS = 600;
    /** A selected passage shorter than this is not drawn. */
    public static final int MIN_PASSAGE = 200;
    /** Characters of the book's own words a chapter's ideas must carry to be sent in place of its text. */
    public static final int MIN_MARK_CHARS = 300;

    private PicturePrompts() {
    }

    /** What was sent. */
    public enum Sent {
        /** The chapter's text. */
        TEXT,
        /** The chapter's stored key ideas and the book's words that mark them. */
        IDEAS,
        /** A passage the reader selected. */
        PASSAGE
    }

    /** Whether a chapter's stored ideas carry enough of the book's own words to draw from. */
    public static boolean enoughMarks(List<PictureIdea> ideas) {
        int chars = 0;
        for (PictureIdea idea : ideas) {
            chars += idea.markChars();
        }
        return chars >= MIN_MARK_CHARS;
    }

    /** @param answerLanguage the reader's choice; empty means the language of the text */
    public static String system(String answerLanguage, Sent sent) {
        final String where = sent == Sent.IDEAS ? "the words in quotation marks" : "the text";
        final StringBuilder s = new StringBuilder();
        s.append("You turn the prose of ").append(sent == Sent.PASSAGE ? "one passage" : "one chapter")
                .append(" of a book into pictures for someone reading it: at most three, one of a kind, and only of "
                        + "the kinds that truly fit what the text says.\n");
        if (sent == Sent.IDEAS) {
            s.append("You get the chapter's key ideas, each followed by the book's own words that carry it, in "
                    + "quotation marks. Only the words in quotation marks are the book's.\n");
        }
        s.append("The kinds:\n"
                + "- process: the text describes steps that follow one another (how a thing is made, done or "
                + "happens). 2 to 12 steps in the text's order, each a label of 1 to 3 words; \"note\" only for a "
                + "count the text gives for that step (\"×2–3\"), else \"\".\n"
                + "- table: the text compares the same things across cases. \"columns\" are the cases (2 to 6), "
                + "\"rows\" the things compared (2 to 8), \"corner\" one word for what the rows are; each cell a few "
                + "of the text's words, \"\" where the text does not say. \"best\": the columns, counted from 0, of "
                + "the cells the text itself calls the best of that row; [] when it calls none. \"best_means\": "
                + "what a marked cell means, in a few words. \"point\": the one sentence the comparison adds up to.\n"
                + "- loop: the text describes causes that feed back on themselves. 2 to 6 \"nodes\" in order round "
                + "the loop (each leads to the next, the last back to the first), \"type\": \"reinforcing\" or "
                + "\"balancing\", \"label\": 2 or 3 words for it (\"feeds itself\"). \"holds_back\": what limits the "
                + "loop, only if the text names it: \"what\" in 1 to 3 words, \"compare\" up to 3 quantities the text "
                + "itself compares (a label, the value as the text gives it, and its number), \"basis\" what they "
                + "share, \"note\" what follows from it; leave \"holds_back\" out when the text names no limit.\n"
                + "Rules:\n"
                + "- Draw only what the text says. Add no step, case, cause or number from your own knowledge. A "
                + "kind that does not fit is left out; if none fits, answer {\"pictures\": []}.\n"
                + "- Every picture needs \"quote\": one or two sentences that stand together in ").append(where)
                .append(", copied letter for letter, at most 50 words: the place the picture is drawn from. No "
                        + "\"…\", nothing changed. A picture whose quote is not found in ").append(where)
                .append(" is thrown away.\n"
                        + "- Every number you show must stand in the text, in digits or in words. A number that is "
                        + "not there is thrown away.\n"
                        + "- \"why\": only when the text itself gives the reasons the thing works: up to 3 cards, each "
                        + "a title of 1 or 2 words and one short line; \"why_title\" names them. Else leave both out.\n"
                        + "Answer with JSON only, no Markdown:\n"
                        + "{\"pictures\":[{\"kind\":\"process\",\"quote\":\"...\",\"title\":\"...\",\"steps\":[{\"label\":"
                        + "\"Draw wire\",\"note\":\"\"},{\"label\":\"Straighten\",\"note\":\"\"}],\"caption\":\"...\",\"why_title\":\"Why it works: three causes\","
                        + "\"why\":[{\"title\":\"Dexterity\",\"line\":\"One task, done thousands of times.\"}]},"
                        + "{\"kind\":\"table\",\"quote\":\"...\",\"title\":\"...\",\"corner\":\"Product\",\"columns\":"
                        + "[\"Poland\",\"France\"],\"rows\":[{\"label\":\"Corn\",\"cells\":[\"as cheap\",\"about the same\"],"
                        + "\"best\":[]},{\"label\":\"Silks\",\"cells\":[\"scarcely made\",\"better and cheaper\"],\"best\":[1]}],"
                        + "\"best_means\":\"clear winner, in the author's words\",\"point\":\"...\"},"
                        + "{\"kind\":\"loop\",\"quote\":\"...\",\"title\":\"...\",\"nodes\":[\"A bigger market\",\"Work split "
                        + "more finely\",\"More output per worker\",\"Cheaper goods\"],\"type\":\"reinforcing\",\"label\":"
                        + "\"feeds itself\",\"holds_back\":{\"what\":\"Transport\",\"basis\":\"Same six-week round trip\","
                        + "\"compare\":[{\"label\":\"Wagon\",\"value\":\"4 tons\",\"number\":4},{\"label\":\"Ship\",\"value\":"
                        + "\"200 tons\",\"number\":200}],\"note\":\"...\"}}]}\n"
                        + "title: at most 8 words. caption, point, note: one short sentence. Write titles, labels, "
                        + "cells and notes in ").append(language(answerLanguage))
                .append("; the quote stays as the text has it.");
        return s.toString();
    }

    /**
     * The user message.
     *
     * @param body the chapter's text, its ideas ({@link #ideasBody}) or the selected passage: the
     *             part the answer is checked against
     */
    public static String user(String bookTitle, String chapterTitle, Sent sent, String body) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (chapterTitle != null && !chapterTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(chapterTitle.trim()).append('\n');
        }
        out.append('\n').append(sent == Sent.IDEAS ? "Key ideas:" : sent == Sent.PASSAGE ? "Passage:" : "Text:").append('\n');
        return out.append(body).toString();
    }

    /** A chapter's ideas, each with the book's words that mark it in quotation marks. */
    public static String ideasBody(List<PictureIdea> ideas) {
        final StringBuilder out = new StringBuilder();
        for (PictureIdea idea : ideas) {
            out.append("- ");
            if (!idea.kind.isEmpty()) {
                out.append('[').append(idea.kind).append("] ");
            }
            out.append(idea.text).append('\n');
            for (String quote : idea.quotes) {
                out.append("   \"").append(quote).append("\"\n");
            }
        }
        return out.toString();
    }

    /** A selected passage as it is sent: one text, {@link #MAX_CHARS} at most; empty when too short to draw. */
    public static String passage(String selected) {
        final String s = selected == null ? "" : selected.trim();
        if (s.length() < MIN_PASSAGE) {
            return "";
        }
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) : s;
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language of the text"
                : answerLanguage.trim();
    }
}
