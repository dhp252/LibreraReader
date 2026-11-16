package com.foobnix.assist.xray;

import java.util.List;

/**
 * The one request of the Argument X-ray per chapter: how well each idea is supported. Only the
 * chapter's ideas and the book's words that mark them are sent, never the chapter's text.
 */
public final class XrayPrompts {

    /** Marks sent per idea. */
    static final int QUOTES_PER_IDEA = 2;
    /** Characters of one mark sent at most. */
    static final int QUOTE_CHARS = 400;

    private XrayPrompts() {
    }

    /** @param answerLanguage the reader's choice; empty means the language of the ideas */
    public static String system(String answerLanguage) {
        return "You check how well the argument of one chapter of a book holds up, for someone reading it. "
                + "You get the chapter's ideas, each with its id, its kind (main, evidence, example, caveat, "
                + "concept, apply), the id of the idea it supports, and the book's own words that carry it. "
                + "Judge only from those words; you may use what is widely known about a source the author "
                + "names (a study, a famous story, a figure) to say it is questioned.\n"
                + "Rate every idea that supports another idea, and every idea other ideas support, by how well "
                + "it is supported:\n"
                + "- strong: numbers, a study, a measured result, or several independent cases\n"
                + "- fair: sound reasoning or a plain observation that holds up but is not measured\n"
                + "- observation: the author's own observation or one description, plausible but unchecked\n"
                + "- questioned: the source or the reported fact is open to doubt\n"
                + "- weak: one story, no numbers, or a leap the words do not carry\n"
                + "Then pick the single weakest spot: the supported idea whose support is thinnest for how much "
                + "rests on it.\n"
                + "Answer with JSON only, no Markdown: {\"ratings\":[{\"id\":3,\"rating\":\"strong\",\"label\":"
                + "\"Practice\",\"why\":\"numbers\"}],\"weakest\":{\"id\":7,\"title\":\"One story holds up this "
                + "cause\",\"explanation\":\"...\"}}. label: 1 to 3 words naming the idea. why: 1 to 3 words on "
                + "its support (\"numbers\", \"1 story\", \"observed\"). title: at most 7 words. explanation: "
                + "exactly 2 short sentences in plain words: what the idea claims, and why its support is thin. "
                + "If nothing is weak, still name the least supported idea. Write label, why, title and "
                + "explanation in " + language(answerLanguage) + ".";
    }

    /** The chapter's ideas and their marks' words. */
    public static String user(String bookTitle, String chapterTitle, List<XrayIdea> ideas) {
        final StringBuilder out = new StringBuilder();
        if (bookTitle != null && !bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(bookTitle.trim()).append('\n');
        }
        if (chapterTitle != null && !chapterTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(chapterTitle.trim()).append('\n');
        }
        out.append("\nIdeas:\n");
        for (XrayIdea idea : ideas) {
            out.append(idea.id).append(" [").append(idea.category == null ? "main" : idea.category.code).append(']');
            if (idea.parent >= 0 && idea.parent != idea.id) {
                out.append(" supports ").append(idea.parent);
            }
            out.append(": ").append(oneLine(idea.text)).append('\n');
            int n = 0;
            for (String quote : idea.quotes) {
                if (n >= QUOTES_PER_IDEA) {
                    break;
                }
                final String q = cut(oneLine(quote), QUOTE_CHARS);
                if (!q.isEmpty()) {
                    out.append("   \"").append(q).append("\"\n");
                    n++;
                }
            }
        }
        return out.toString();
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
                ? "the language the ideas are written in"
                : answerLanguage.trim();
    }
}
