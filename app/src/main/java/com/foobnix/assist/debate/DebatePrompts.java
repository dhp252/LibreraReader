package com.foobnix.assist.debate;

import com.foobnix.assist.xray.XrayIdea;

import java.util.ArrayList;
import java.util.List;

/**
 * The one request of a debate round: the chapter's ideas with the book's words that mark them, the
 * debate so far and the reader's message. The model answers as the author and, apart from that, as
 * a referee. The chapter's text itself is not sent.
 */
public final class DebatePrompts {

    /** Marks sent per idea, and characters of one mark, when everything fits. */
    static final int QUOTES_PER_IDEA = 2;
    static final int QUOTE_CHARS = 400;
    /** The ideas' share of a request when the provider takes little. */
    static final int MIN_IDEAS_CHARS = 2000;

    private DebatePrompts() {
    }

    /**
     * @param author         the author's name, or empty
     * @param last           the round asked for is the last one
     * @param answerLanguage the reader's choice; empty means the language the reader writes in
     */
    public static String system(String author, boolean last, String answerLanguage) {
        final String who = author == null || author.trim().isEmpty() ? "the author of the book" : author.trim();
        return "You run one round of a three-round debate between a reader and the author of a book the reader "
                + "is reading. The reader argues against one claim of the book. You play two parts and keep them "
                + "apart.\n"
                + "Part 1, the author. Answer the reader's latest message as " + who + ", in the first person, "
                + "defending the claim the way the book does. Rest on the chapter's ideas and passages you are "
                + "given and on what the book is widely known to say; do not invent facts, numbers or events. "
                + "Concede a point when the book itself does (its caveats) or when the reader is plainly right, "
                + "and say what still stands. 2 to 4 short sentences in plain words, no greeting, no flattery.\n"
                + "You may use at most one direct quote from the book. Put it inside the reply in double "
                + "quotation marks, copied word for word from the passages you are given (or from elsewhere in "
                + "the book only when you are certain of the exact words), at most 30 words, and repeat it in "
                + "\"quote\". Words that are not word for word the book's must not stand in quotation marks, and "
                + "quotation marks are used for nothing else. The app checks every quote against the book.\n"
                + "Part 2, the referee: neutral, not the author. Judge this round only: who made the better "
                + "point, \"reader\", \"author\", or \"draw\". Do not favour the author: a reader who finds a real "
                + "gap, or makes the author concede, gets the point; a reader who only repeats or misreads the "
                + "book does not. why: one sentence, speaking to the reader as \"you\" and naming the author. "
                + (last
                ? "This is the last round: leave \"next\" empty and give \"crux\": 1 or 2 sentences on where the "
                + "reader and the author really disagree (a value, a fact or a definition), fair to both.\n"
                : "next: one question for the next round that goes a step deeper into the disagreement. Leave "
                + "\"crux\" empty.\n")
                + "Answer with JSON only, no Markdown: {\"position\":\"...\",\"reply\":\"...\",\"quote\":\"\","
                + "\"idea\":3,\"verdict\":{\"point\":\"reader\",\"why\":\"...\",\"next\":\"...\"},\"crux\":\"\"}. "
                + "position: the author's position against the reader's, at most 8 words. idea: the id of the "
                + "idea the reply rests on most, or -1. quote: the direct quote in the reply, or empty. Write "
                + "position, reply, why, next and crux in " + language(answerLanguage) + "; a direct quote stays "
                + "in the book's language.";
    }

    /**
     * @param message  what the reader says in this round
     * @param maxChars characters the provider takes in one request ({@code AiSettings.maxChars})
     */
    public static String user(DebateSession s, String message, int maxChars) {
        final StringBuilder out = new StringBuilder();
        if (!s.bookTitle.trim().isEmpty()) {
            out.append("Book: ").append(s.bookTitle.trim()).append('\n');
        }
        if (s.author.known) {
            out.append("Author: ").append(s.author.name).append('\n');
        }
        if (!s.chapterTitle.trim().isEmpty()) {
            out.append("Chapter: ").append(s.chapterTitle.trim()).append('\n');
        }
        final StringBuilder debate = new StringBuilder();
        debate.append("\nThe claim under debate: idea ").append(s.claim.id).append(": ").append(oneLine(s.claim.text))
                .append('\n');
        debate.append("The reader's position: ").append(oneLine(s.readerPosition)).append('\n');
        if (!s.authorPosition.isEmpty()) {
            debate.append("The author's position: ").append(oneLine(s.authorPosition)).append('\n');
        }
        final String author = s.author.known ? s.author.name : "The author";
        int n = 1;
        for (DebateRound round : s.rounds) {
            debate.append(n == 1 ? "\nThe debate so far:\n" : "").append("Round ").append(n).append('\n');
            debate.append("Reader: ").append(oneLine(round.message)).append('\n');
            debate.append(author).append(": ").append(oneLine(round.replyText())).append('\n');
            debate.append("Referee: point to ").append(round.point.code);
            if (!round.why.isEmpty()) {
                debate.append(". ").append(oneLine(round.why));
            }
            if (!round.next.isEmpty()) {
                debate.append(" Next: ").append(oneLine(round.next));
            }
            debate.append('\n');
            n++;
        }
        debate.append("\nRound ").append(s.round()).append(" of ").append(DebateSession.ROUNDS);
        if (s.lastRound()) {
            debate.append(" (the last round)");
        }
        debate.append("\nReader: ").append(DebateSession.cut(message, DebateSession.MESSAGE_CHARS)).append('\n');

        final int room = Math.max(MIN_IDEAS_CHARS, maxChars - out.length() - debate.length() - 4000);
        out.append("\nIdeas of the chapter (id [kind], then the book's words that carry it):\n");
        out.append(ideas(s.ideas, s.claim.id, room));
        out.append(debate);
        return out.toString();
    }

    /**
     * The ideas with their marks, in at most about {@code room} characters: two marks an idea, then
     * one shorter mark, then only as many ideas as fit, the claim and the ideas around it first.
     */
    static String ideas(List<XrayIdea> ideas, int claimId, int room) {
        String block = lines(ideas, QUOTES_PER_IDEA, QUOTE_CHARS);
        if (block.length() <= room) {
            return block;
        }
        block = lines(ideas, 1, QUOTE_CHARS / 2);
        if (block.length() <= room) {
            return block;
        }
        // the claim, what supports it and what it supports, then the rest in reading order
        final List<XrayIdea> near = new ArrayList<>();
        final List<XrayIdea> far = new ArrayList<>();
        int parent = -1;
        for (XrayIdea idea : ideas) {
            if (idea.id == claimId) {
                parent = idea.parent;
            }
        }
        for (XrayIdea idea : ideas) {
            if (idea.id == claimId || idea.parent == claimId || (parent >= 0 && idea.id == parent)) {
                near.add(idea);
            } else {
                far.add(idea);
            }
        }
        near.addAll(far);
        final StringBuilder out = new StringBuilder();
        for (XrayIdea idea : near) {
            final String one = lines(java.util.Collections.singletonList(idea), 1, QUOTE_CHARS / 2);
            if (out.length() > 0 && out.length() + one.length() > room) {
                break;
            }
            out.append(one);
        }
        return out.toString();
    }

    /** "3 [evidence] supports 1: the idea", then its marks, each on a line of its own. */
    private static String lines(List<XrayIdea> ideas, int quotes, int quoteChars) {
        final StringBuilder out = new StringBuilder();
        for (XrayIdea idea : ideas) {
            out.append(idea.id).append(" [").append(idea.category == null ? "main" : idea.category.code).append(']');
            if (idea.parent >= 0 && idea.parent != idea.id) {
                out.append(" supports ").append(idea.parent);
            }
            out.append(": ").append(oneLine(idea.text)).append('\n');
            int n = 0;
            for (String quote : idea.quotes) {
                if (n >= quotes) {
                    break;
                }
                final String q = DebateSession.cut(quote, quoteChars);
                if (!q.isEmpty()) {
                    out.append("   \"").append(q.replace('"', '\'')).append("\"\n");
                    n++;
                }
            }
        }
        return out.toString();
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String language(String answerLanguage) {
        return answerLanguage == null || answerLanguage.trim().isEmpty()
                ? "the language the reader writes in"
                : answerLanguage.trim();
    }
}
