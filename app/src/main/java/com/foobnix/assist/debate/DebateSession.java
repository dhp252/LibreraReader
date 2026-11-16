package com.foobnix.assist.debate;

import com.foobnix.assist.xray.XrayIdea;
import com.foobnix.assist.xray.XrayKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A debate with the author about one idea of a chapter: the positions and the rounds played so
 * far (3 at most). Lives in memory while the debate goes on; only {@link #result} is stored.
 */
public final class DebateSession {

    public static final int ROUNDS = 3;
    /** Characters of a position kept. */
    public static final int POSITION_CHARS = 120;
    /** Characters of one message of the reader sent. */
    public static final int MESSAGE_CHARS = 600;

    public final String bookTitle;
    public final String chapterKey;
    public final String chapterTitle;
    public final DebateAuthor author;
    /** The chapter's ideas with their marked passages. */
    public final List<XrayIdea> ideas;
    /** The idea argued about. */
    public final XrayIdea claim;
    public String readerPosition = "";
    /** The author's position in a few words, from the first answer; empty before it. */
    public String authorPosition = "";
    /** Where the two really disagree, from the last answer. */
    public String crux = "";
    public final List<DebateRound> rounds = new ArrayList<>();

    public DebateSession(String bookTitle, String chapterKey, String chapterTitle, DebateAuthor author,
                         List<XrayIdea> ideas, XrayIdea claim) {
        this.bookTitle = bookTitle == null ? "" : bookTitle;
        this.chapterKey = chapterKey == null ? "" : chapterKey;
        this.chapterTitle = chapterTitle == null ? "" : chapterTitle;
        this.author = author == null ? DebateAuthor.of(null) : author;
        this.ideas = ideas;
        this.claim = claim;
    }

    /** The ideas a reader can argue with: the chapter's main ideas, or every idea when it has none. */
    public static List<XrayIdea> claims(List<XrayIdea> ideas) {
        final List<XrayIdea> out = new ArrayList<>();
        for (XrayIdea idea : ideas) {
            if (idea.kind() == XrayKind.CLAIM && !idea.text.trim().isEmpty()) {
                out.add(idea);
            }
        }
        if (out.isEmpty()) {
            for (XrayIdea idea : ideas) {
                if (!idea.text.trim().isEmpty()) {
                    out.add(idea);
                }
            }
        }
        return out;
    }

    /** The round being played, from 1; {@link #ROUNDS} once the debate is over. */
    public int round() {
        return Math.min(ROUNDS, rounds.size() + 1);
    }

    public boolean finished() {
        return rounds.size() >= ROUNDS;
    }

    /** The round being played is the last one. */
    public boolean lastRound() {
        return rounds.size() == ROUNDS - 1;
    }

    public Set<Integer> ids() {
        final Set<Integer> ids = new HashSet<>();
        for (XrayIdea idea : ideas) {
            ids.add(idea.id);
        }
        return ids;
    }

    public XrayIdea idea(int id) {
        for (XrayIdea idea : ideas) {
            if (idea.id == id) {
                return idea;
            }
        }
        return null;
    }

    /** A round the model answered. The author's position is taken from the first answer that has one. */
    public void add(DebateRound round, String position, String crux) {
        if (finished()) {
            return;
        }
        rounds.add(round);
        if (authorPosition.isEmpty() && position != null && !position.trim().isEmpty()) {
            authorPosition = cut(position.trim(), POSITION_CHARS);
        }
        if (finished() && crux != null) {
            this.crux = crux.trim();
        }
    }

    /** What is stored of the debate. */
    public DebateResult result(long now) {
        final DebateResult r = new DebateResult();
        r.claim = claim.text;
        r.you = readerPosition;
        r.author = authorPosition;
        r.name = author.name;
        for (DebateRound round : rounds) {
            r.rounds.add(new DebateResult.Verdict(round.point, round.why));
        }
        r.crux = crux;
        r.date = now;
        return r;
    }

    /** One line, at most {@code max} characters, cut at a space with "…". */
    public static String cut(String s, int max) {
        final String line = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        if (line.length() <= max) {
            return line;
        }
        int end = line.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return line.substring(0, end).trim() + "…";
    }
}
