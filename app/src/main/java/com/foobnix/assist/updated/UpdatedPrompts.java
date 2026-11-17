package com.foobnix.assist.updated;

import com.foobnix.ai.AiPrompts;

import java.util.List;

/**
 * The one request of "The book, updated" per chapter: up to six notes on what is known today and
 * the strongest objection to the chapter's main claim. The model answers from its own knowledge,
 * so the prompt's first concern is honesty: only sources it is sure exist, "debated" and
 * "speculative" said plainly, and null where it has nothing.
 */
public final class UpdatedPrompts {

    private UpdatedPrompts() {
    }

    /**
     * @param answerLanguage the reader's choice; empty means the language of the text
     * @param lensFields     the fields of the reader's lens, or empty: then no "terms" are asked for
     */
    public static String system(String answerLanguage, List<String> lensFields) {
        final String lang = answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the text is written in" : answerLanguage.trim();
        final boolean lens = lensFields != null && !lensFields.isEmpty();
        final StringBuilder s = new StringBuilder();
        s.append("You annotate one chapter of a book for someone reading it today, as a careful modern editor would: ")
                .append("what later research, history and criticism have added or corrected since it was written. ")
                .append("You get the book's title, its author and a year when known, and the chapter's text. ")
                .append("You answer from your own knowledge; you cannot look anything up, and the reader is told so.\n\n")
                .append("NOTES. Write at most ").append(UpdatedChapter.MAX_NOTES).append(" notes, only where you know ")
                .append("something solid and relevant. Fewer good notes are better than six thin ones; if you know ")
                .append("nothing worth a note, give an empty list. Each note attaches to one sentence of the chapter ")
                .append("and has one kind:\n")
                .append("- source: where a fact, figure or story of the author really comes from, or whether it checks out\n")
                .append("- missed: a cause or explanation the author did not name and later work established\n")
                .append("- doubtful: a story or fact in the chapter that is doubted, disputed or known to be wrong\n")
                .append("- newer: how later research or events changed the picture\n")
                .append("For each note: \"quote\" is the one sentence of the chapter it attaches to, copied exactly from ")
                .append("the text, character for character, in the text's own language: no changes, no ellipses, no ")
                .append("quotation marks added, never a heading or a caption. \"title\": at most 8 words. \"body\": 2 to 3 ")
                .append("short sentences in plain words.\n\n")
                .append("HONESTY RULES, more important than being interesting:\n")
                .append("- Give a source only when you are sure the work exists and says this: its title (\"work\"), its ")
                .append("author and its year. If you are not sure of the work, \"source\" is null. If you are sure of the ")
                .append("work but not of its year or author, leave that part empty. Never invent a title, an author, a ")
                .append("year, a number or a quotation.\n")
                .append("- \"settled\" says how settled the note's point is: \"well established\" only for what is widely ")
                .append("accepted among experts, \"debated\" when experts disagree, \"speculative\" when it is a guess, a ")
                .append("single study or a minority view. When you hesitate between two, choose the weaker one. ")
                .append("\"settled_note\" may add at most 8 words (\"now called the Babbage principle\"), or be empty.\n")
                .append("- Do not write a note you would have to hedge until it says nothing.\n\n")
                .append("OBJECTION. Then give the single strongest objection to the chapter's main claim: \"claim\" (the ")
                .append("author's claim, one sentence), \"objection\" (one sentence), \"strength\" (\"strong\", \"moderate\" or ")
                .append("\"weak\": how much of the claim it really threatens, said honestly). \"twist\": if the author ")
                .append("answers or concedes the objection somewhere in this book, \"head\" says where in at most 8 words, ")
                .append("\"quote\" gives the book's own words only if you remember them exactly (else null), and \"note\" is ")
                .append("one sentence on what the author does about it; if there is no such place, \"twist\" is null. ")
                .append("\"pushed\": a later author who took the objection further, with \"who\", \"work\", \"year\" and ")
                .append("\"text\" (one sentence), under the same rule as sources: null unless you are sure. \"disagree\": ")
                .append("where the two really disagree, one sentence for the author's side (\"author\") and one for the ")
                .append("other side (\"other\"). If the chapter makes no claim to object to, \"objection\" is null.\n\n")
                .append("\"first_published\": the year the book first came out if you are sure of it, else null.\n");
        if (lens) {
            s.append("\nThe reader works in: ").append(UpdatedText.join("; ", lensFields.toArray(new String[0])))
                    .append(". Where a note's point carries over naturally to that field, add \"terms\": one sentence ")
                    .append("that puts it in that field's terms. Where it does not, \"terms\" is null; never force it.\n");
        }
        s.append("\nWrite titles, bodies and every other sentence in ").append(lang)
                .append("; names of works stay as they are known.\n")
                .append("Answer with only this JSON, no other text:\n")
                .append("{\"first_published\": 1776, \"notes\": [{\"kind\": \"missed\", \"quote\": \"...\", \"title\": \"...\", ")
                .append("\"body\": \"...\", \"settled\": \"well established\", \"settled_note\": \"\", \"source\": {\"work\": ")
                .append("\"...\", \"author\": \"...\", \"year\": 1832}");
        if (lens) {
            s.append(", \"terms\": null");
        }
        s.append("}], \"objection\": {\"claim\": \"...\", \"objection\": \"...\", \"strength\": \"strong\", \"twist\": ")
                .append("{\"head\": \"...\", \"quote\": null, \"note\": \"...\"}, \"pushed\": {\"who\": \"...\", \"work\": ")
                .append("\"...\", \"year\": 1867, \"text\": \"...\"}, \"disagree\": {\"author\": \"...\", \"other\": \"...\"}}}\n")
                .append(AiPrompts.SIDE_MATTER);
        return s.toString();
    }

    /**
     * @param year the year in the book's file, or 0: often the year of this edition, so the model
     *             is told not to trust it as the year the book was written
     */
    public static String user(String bookTitle, String author, int year, String chapterTitle, String text) {
        final StringBuilder out = new StringBuilder();
        out.append("Book: ").append(safe(bookTitle)).append('\n');
        if (!safe(author).isEmpty()) {
            out.append("Author: ").append(safe(author)).append('\n');
        }
        if (year > 0) {
            out.append("Year in the file (may be this edition's, not the first): ").append(year).append('\n');
        }
        if (!safe(chapterTitle).isEmpty()) {
            out.append("Chapter: ").append(safe(chapterTitle)).append('\n');
        }
        return out.append('\n').append(text == null ? "" : text).toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
