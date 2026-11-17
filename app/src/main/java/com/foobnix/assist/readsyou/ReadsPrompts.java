package com.foobnix.assist.readsyou;

import com.foobnix.assist.lens.LensPrompts;

/**
 * The requests of "Say it another way?" (Assist.READS_YOU): one passage of the book (the page the
 * reader is on, or the text they selected) said in plain words, or with an everyday example. The
 * third way, in the reader's own field, is the Lens's sentence request
 * ({@link LensPrompts#sentenceSystem}). Each is sent only when its tab is tapped. No Android here.
 */
public final class ReadsPrompts {

    /** Characters of the passage sent at most: the same as the Lens sends of a selected sentence. */
    public static final int PASSAGE_CHARS = LensPrompts.SENTENCE_CHARS;

    /** The ways a passage can be said again. */
    public enum Kind {PLAIN, EXAMPLE, FIELD}

    private ReadsPrompts() {
    }

    /**
     * The passage that is sent and shown: the selected text when there is one, else the page's
     * text; on one line, {@link #PASSAGE_CHARS} at most. Empty when there is neither.
     */
    public static String passage(String selected, String pageText) {
        final String chosen = selected != null && !selected.trim().isEmpty() ? selected : pageText;
        return LensPrompts.sentence(chosen);
    }

    /** Whether the page's text is longer than what {@link #passage} keeps of it. */
    public static boolean isCut(String text) {
        return text != null && text.replaceAll("\\s+", " ").trim().length() > PASSAGE_CHARS;
    }

    /** The system prompt of the plain-words and the example request. */
    public static String system(Kind kind, String answerLanguage) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the passage is written in" : answerLanguage.trim();
        final String rules = "Keep the author's meaning and add no claim the passage does not make. Write in " + lang
                + ". Plain prose only: no preface, no quotation marks, no lists, no Markdown.";
        if (kind == Kind.EXAMPLE) {
            return "You help a reader who is stuck on one passage of a book. Give one concrete example of what "
                    + "the passage says: a specific everyday situation, in two or three short sentences, in which "
                    + "the same thing happens, with each main point of the passage visible in it. The example is "
                    + "yours, not the book's: do not present it as something the author wrote. " + rules;
        }
        return "You help a reader who is stuck on one passage of a book. Say what the passage says in plain, "
                + "everyday words: two to four short sentences, in the order the passage goes, with no term the "
                + "passage does not explain. Do not judge the passage and do not add examples. " + rules;
    }

    public static String user(String bookTitle, String author, String passage) {
        return LensPrompts.sentenceUser(bookTitle, author, passage);
    }

    /** The model's answer as shown: code fences and wrapping quotation marks taken off. */
    public static String answer(String raw) {
        return LensPrompts.plain(raw);
    }
}
