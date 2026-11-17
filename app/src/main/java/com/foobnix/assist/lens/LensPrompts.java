package com.foobnix.assist.lens;

import com.foobnix.ai.AiPrompts;

import java.util.List;

/**
 * What the lens asks of the model: the chapter's bridge (one request per chapter), the ladder of
 * one idea, and one sentence made concrete or boiled down. Every request carries the reader's lens
 * ({@link LensProfile}) and their level in the book's subject. No Android here.
 */
public final class LensPrompts {

    /** Characters of a chapter's text sent at most, when it has no stored ideas. */
    public static final int MAX_CHARS = 40_000;
    /** Less text than this holds no ideas worth mapping. */
    public static final int MIN_CHARS = 300;
    /** Characters of a selected sentence sent at most. */
    public static final int SENTENCE_CHARS = 2_000;
    /** Characters of an idea's mark sent with its ladder request. */
    static final int QUOTE_CHARS = 600;

    private LensPrompts() {
    }

    /** The reader, as every request describes them: their fields, their level, the three switches. */
    static String reader(LensProfile profile, LensProfile.Level level) {
        final StringBuilder out = new StringBuilder();
        out.append("The reader knows these fields well: ").append(profile.fieldsText()).append(". ");
        switch (level == null ? LensProfile.Level.NEW : level) {
            case EXPERT:
                out.append("In this book's subject the reader is an expert: be precise and do not simplify the "
                        + "author's claims. ");
                break;
            case SOME:
                out.append("The reader knows some of this book's subject: skip its basics. ");
                break;
            default:
                out.append("The reader is new to this book's subject: assume no background in it. ");
                break;
        }
        out.append(profile.analogies
                ? "Explain through the reader's fields: a concept, practice or situation they work with, never a "
                + "vague likeness. "
                : "Use no analogies: say things in plain words. ");
        if (profile.plainWords) {
            out.append("Put old or specialist jargon of the book into plain present-day words. ");
        }
        if (profile.keepTerms) {
            out.append("Keep the author's key terms: explain a term once, then use it as the author writes it. ");
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ the bridge

    /**
     * @param fromIdeas the user message lists the chapter's stored ideas (else it holds its text)
     * @param answerLanguage the reader's choice; empty means the language of the book
     */
    public static String bridgeSystem(LensProfile profile, LensProfile.Level level, String answerLanguage,
                                      boolean fromIdeas) {
        return "You help a reader understand one chapter of a book through what they already know. "
                + reader(profile, level) + "\n"
                + "Pick the chapter's " + LensBridge.MAX_ROWS + " most important ideas, in reading order (fewer "
                + "when it has fewer). For each give:\n"
                + "- author: the idea named in the author's own terms, at most 7 words\n"
                + "- yours: the same idea in the reader's terms, at most 9 words"
                + (profile.analogies ? ": the closest thing in the reader's fields\n" : ", plainly\n")
                + (fromIdeas
                ? "- id: the number of the idea in the list you were given that it comes from\n"
                : "- quote: the one sentence of the chapter that states the idea best, copied exactly from the "
                + "text, character for character, in the text's own language: no changes, no ellipses, no "
                + "quotation marks added, never a heading or a caption\n")
                + "Then one note, breaks: in 1 or 2 short sentences, where "
                + (profile.analogies ? "the analogy to the reader's fields stops fitting what the author means"
                : "the plain wording loses something the author means")
                + ", so the reader does not carry it too far.\n"
                + "Stay with what you are given: add no idea the chapter does not have. Write author, yours and "
                + "breaks in " + language(answerLanguage) + ".\n"
                + "Answer with only JSON, no Markdown: {\"pairs\":[{"
                + (fromIdeas ? "\"id\":3,\"author\":\"...\"" : "\"author\":\"...\",\"quote\":\"...\"")
                + ",\"yours\":\"...\"}],\"breaks\":\"...\"}"
                + (fromIdeas ? "" : "\n" + AiPrompts.SIDE_MATTER);
    }

    /** The chapter as its stored key-passage ideas: nothing of the book's text but what the ideas say. */
    public static String bridgeUser(String bookTitle, String author, String chapterTitle, List<LensIdea> ideas) {
        final StringBuilder out = new StringBuilder(header(bookTitle, author, chapterTitle));
        out.append("Ideas of the chapter:\n");
        for (LensIdea idea : ideas) {
            out.append(idea.id);
            if (!idea.kind.isEmpty()) {
                out.append(" [").append(idea.kind).append(']');
            }
            out.append(": ").append(oneLine(idea.text)).append('\n');
        }
        return out.toString();
    }

    /** The chapter as its text (already capped by the caller at {@link #MAX_CHARS}). */
    public static String bridgeUser(String bookTitle, String author, String chapterTitle, String text) {
        return header(bookTitle, author, chapterTitle) + "<text>\n" + cut(text == null ? "" : text.trim(), MAX_CHARS)
                + "\n</text>";
    }

    // ------------------------------------------------------------------ the ladder

    public static String ladderSystem(LensProfile profile, LensProfile.Level level, String answerLanguage) {
        return "You lay one idea of a book out on a ladder of abstraction, for a reader who understands by moving "
                + "between the abstract and the concrete. " + reader(profile, level) + "\n"
                + "Give four rungs, each one sentence of at most 16 words:\n"
                + "- principle: the idea as a general rule, with no detail of the book\n"
                + "- mechanism: how or why it works, as the author explains it\n"
                + "- example: the author's own example of it in this book; when you are given none, the most "
                + "concrete thing the author's words say\n"
                + "- world: one concrete situation "
                + (profile.analogies ? "from the reader's own fields" : "of everyday life")
                + " where the same thing happens\n"
                + "Stay true to what the author means. Write in " + language(answerLanguage) + ".\n"
                + "Answer with only JSON, no Markdown: {\"principle\":\"...\",\"mechanism\":\"...\","
                + "\"example\":\"...\",\"world\":\"...\"}";
    }

    /**
     * @param idea  the idea in the author's terms
     * @param yours the idea in the reader's terms, from the bridge
     * @param quote the book's words that carry it, or empty
     */
    public static String ladderUser(String bookTitle, String author, String chapterTitle, String idea, String yours,
                                    String quote) {
        final StringBuilder out = new StringBuilder(header(bookTitle, author, chapterTitle));
        out.append("The idea: ").append(oneLine(idea)).append('\n');
        if (yours != null && !yours.trim().isEmpty()) {
            out.append("In the reader's terms: ").append(oneLine(yours)).append('\n');
        }
        if (quote != null && !quote.trim().isEmpty()) {
            out.append("The author's words:\n<passage>\n").append(cut(quote.trim(), QUOTE_CHARS)).append("\n</passage>");
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ any sentence

    /** @param concrete "Make it concrete" (down the ladder); else "Boil it down" (up) */
    public static String sentenceSystem(LensProfile profile, LensProfile.Level level, String answerLanguage,
                                        boolean concrete) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the passage is written in" : answerLanguage.trim();
        return (concrete
                ? "You make one passage of a book concrete for its reader. " + reader(profile, level) + "\n"
                + "Answer with one or two short sentences: one specific situation "
                + (profile.analogies ? "from the reader's own fields" : "of everyday life")
                + " in which what the passage says happens. "
                : "You boil one passage of a book down for its reader. " + reader(profile, level) + "\n"
                + "Answer with one short sentence: the general point the passage makes, without its details "
                + "and examples. ")
                + "Keep the author's meaning and add no claim the passage does not make. Write in " + lang
                + ". Plain prose only: no preface, no quotation marks, no lists, no Markdown.";
    }

    public static String sentenceUser(String bookTitle, String author, String sentence) {
        return header(bookTitle, author, null) + "<passage>\n" + sentence(sentence) + "\n</passage>";
    }

    /** A selected text as it is sent and shown: one line, {@link #SENTENCE_CHARS} at most. */
    public static String sentence(String selected) {
        return cut(oneLine(selected), SENTENCE_CHARS);
    }

    /** A one-sentence answer as shown: trimmed, without fences or quotation marks around it. */
    public static String plain(String answer) {
        if (answer == null) {
            return "";
        }
        String s = answer.trim();
        if (s.startsWith("```")) {
            final int line = s.indexOf('\n');
            s = line < 0 ? "" : s.substring(line + 1);
            if (s.trim().endsWith("```")) {
                s = s.substring(0, s.lastIndexOf("```"));
            }
        }
        return LensBridge.clean(s);
    }

    // ------------------------------------------------------------------ parts

    private static String header(String bookTitle, String author, String chapterTitle) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (author != null && !author.trim().isEmpty()) {
            out.append("Author: ").append(author.trim()).append('\n');
        }
        if (chapterTitle != null && !chapterTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(chapterTitle.trim()).append('\n');
        }
        return out.length() > 0 ? out.append('\n').toString() : "";
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

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the book is written in" : answerLanguage.trim();
    }
}
