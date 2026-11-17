package com.foobnix.assist.debate;

import java.util.Collections;
import java.util.List;

/**
 * One finished round as the screen shows it: the reader's message, the author's reply with its
 * quotes checked, where the reply's source line goes, and the referee's verdict. Kept in memory
 * only; the store gets {@link DebateResult}.
 */
public final class DebateRound {

    /** What the reader wrote. */
    public final String message;
    /** The author's reply; only the book's own words are quoted parts. */
    public final List<DebateQuotes.Part> reply;
    /** The book's words the source line goes to: the checked quote, else the idea's first mark; null for no source line. */
    public final String source;
    /** 0-based page of the source (a hint), or -1. */
    public final int sourcePage;
    /** The source is a direct quote in the reply ("QUOTED"). */
    public final boolean quoted;
    /** The title of the chapter the source is in, or empty. */
    public final String sourceChapter;
    public final DebateReply.Point point;
    public final String why;
    /** The question for the next round, or empty. */
    public final String next;

    public DebateRound(String message, List<DebateQuotes.Part> reply, String source, int sourcePage, boolean quoted,
                       String sourceChapter, DebateReply.Point point, String why, String next) {
        this.message = message == null ? "" : message;
        this.reply = reply == null ? Collections.<DebateQuotes.Part>emptyList() : reply;
        this.source = source;
        this.sourcePage = sourcePage;
        this.quoted = quoted;
        this.sourceChapter = sourceChapter == null ? "" : sourceChapter;
        this.point = point == null ? DebateReply.Point.DRAW : point;
        this.why = why == null ? "" : why;
        this.next = next == null ? "" : next;
    }

    /**
     * The round from the model's answer and its checked quotes. The source line goes to the quote
     * found in the book ({@code quoteChapter}: the title of the chapter it is in); a reply without
     * one goes to the first mark of the idea it rests on, when that idea is in the chapter and was
     * located. A quote that was not found leaves no source line of its own.
     */
    public static DebateRound of(DebateSession session, String message, DebateReply reply,
                                 DebateQuotes.Checked checked, String quoteChapter) {
        if (checked.quote != null) {
            return new DebateRound(message, checked.parts, checked.quote, checked.page, true,
                    quoteChapter == null || quoteChapter.trim().isEmpty() ? session.chapterTitle : quoteChapter,
                    reply.point, reply.why, reply.next);
        }
        final com.foobnix.assist.xray.XrayIdea idea = reply.idea < 0 ? null : session.idea(reply.idea);
        if (idea != null && (idea.first != null || idea.firstPage >= 0)) {
            return new DebateRound(message, checked.parts, idea.first, idea.firstPage, false, session.chapterTitle,
                    reply.point, reply.why, reply.next);
        }
        return new DebateRound(message, checked.parts, null, -1, false, "", reply.point, reply.why, reply.next);
    }

    /** Whether the reply has a source line: something in the book to go to. */
    public boolean hasSource() {
        return (source != null && !source.isEmpty()) || sourcePage >= 0;
    }

    /** The reply as one text; only the book's own words stand in quotation marks. */
    public String replyText() {
        final StringBuilder out = new StringBuilder();
        for (DebateQuotes.Part p : reply) {
            out.append(p.quoted ? "“" + p.text + "”" : p.text);
        }
        return out.toString();
    }
}
