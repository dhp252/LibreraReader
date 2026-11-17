package com.foobnix.assist.zoom;

import com.foobnix.ai.AiPrompts;

/**
 * The one request of Semantic zoom: a chapter's text in, its sections with one line and a lean
 * version each, its claims by kind and the chapter in one line out, as one JSON object
 * ({@link ZoomData#parse}). Every piece carries words copied from the chapter, so the app can find
 * the author's passage again. No Android here.
 */
public final class ZoomPrompts {

    private ZoomPrompts() {
    }

    /** @param withGoal the request states the reader's goal: the answer then also says what it kept and squeezed */
    public static String system(String answerLanguage, boolean withGoal) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in" : answerLanguage.trim();
        return "You prepare a chapter of a book for a reader who chooses how closely to read it: in full, lean, "
                + "as claims only, or in one line. Work only from the text you are given; never add a fact, an "
                + "example or a number that is not in it.\n"
                + "1. \"sections\": the chapter's sections in order, 2 to 7 of them (a short chapter may have 1). "
                + "A section is a run of paragraphs that does one job. Each has \"title\" (at most 6 words), "
                + "\"line\" (the section in one plain sentence), \"question\" (the question the section answers, one "
                + "sentence), \"start\" (the first 8 to 12 words of the section, copied exactly) and \"lean\".\n"
                + "2. \"lean\": the section rewritten shorter in plain words, as paragraphs, at about 40% of its "
                + "length. Cut repetition, asides and all but the best example of each point; keep every claim, the "
                + "reasoning that connects them, and the numbers. Each paragraph is {\"kept\": ..., \"sentences\": "
                + "[...]}. \"kept\" says in at most 3 words why a paragraph keeps its detail (like \"best example\"), "
                + "else it is empty. Each sentence is {\"text\": the condensed sentence, \"quote\": the first 8 to "
                + "12 words of the passage of the chapter it condenses, copied exactly, \"end\": the last 4 to 8 "
                + "words of that passage, copied exactly}. Where you leave out a whole stretch (more examples, a "
                + "digression), put in its place, between the paragraphs, {\"folded\": what was left out in at most "
                + "8 words, \"quote\": the first 8 to 12 words of that stretch, copied exactly}.\n"
                + "3. \"claims\": the chapter's argument as 4 to 9 rows in the order of the reasoning. \"kind\" is "
                + "one of: claim (the main claim, first), evidence (what shows it), why (a reason it holds; one row "
                + "per reason), caveat (where it does not hold), so_what (what follows from it). Each row has "
                + "\"text\" (one short sentence), \"example\" (the best example in a few words, or empty) and "
                + "\"quote\" (the one sentence of the chapter that says it, copied exactly).\n"
                + "4. \"line\": the whole chapter in one sentence.\n"
                + (withGoal ? "5. The reader's goal, given with the text, decides what the lean version keeps in full "
                + "and what it squeezes or folds; the claims and the lines stay true to the chapter whatever the goal. "
                + "Say what the goal changed: \"kept\": up to 3 parts of the chapter you kept in full or nearly, each "
                + "named in at most 5 words; \"squeezed\": up to 3 parts you squeezed or folded, named the same way.\n"
                : "")
                + "Write everything except the copied words in " + lang + ". Copied words are taken character for "
                + "character from the text, in the text's own language: no changes, no ellipses, no quotation marks "
                + "added, never from a heading or a caption.\n"
                + "Answer with only a JSON object, no other text:\n"
                + "{\"line\": \"...\", \"sections\": [{\"title\": \"...\", \"line\": \"...\", \"question\": \"...\", "
                + "\"start\": \"...\", \"lean\": [{\"kept\": \"\", \"sentences\": [{\"text\": \"...\", \"quote\": \"...\", "
                + "\"end\": \"...\"}]}, {\"folded\": \"...\", \"quote\": \"...\"}]}], \"claims\": [{\"kind\": \"claim\", "
                + "\"text\": \"...\", \"example\": \"\", \"quote\": \"...\"}]"
                + (withGoal ? ", \"kept\": [\"...\"], \"squeezed\": [\"...\"]" : "") + "}\n"
                + AiPrompts.SIDE_MATTER;
    }

    /**
     * @param partial only the first part of the chapter is given
     * @param goal    the reader's goal as {@link ZoomGoal#prompt} states it; null or empty for none
     */
    public static String user(String bookTitle, String chapterTitle, String text, boolean partial, String goal) {
        return "Book: " + safe(bookTitle) + "\nChapter: " + safe(chapterTitle)
                + (safe(goal).isEmpty() ? "" : "\nThe reader's goal: " + safe(goal))
                + (partial ? "\n(Only the first part of this long chapter is given: treat it as the whole text.)" : "")
                + "\n\n" + safe(text);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
