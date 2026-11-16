package com.foobnix.assist.debate;

import com.foobnix.ai.AiCategory;
import com.foobnix.assist.xray.XrayIdea;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DebateSessionTest {

    private static final String PINS = "ten persons could make among them upwards of forty-eight thousand pins in a day";

    static List<XrayIdea> ideas() {
        return Arrays.asList(
                new XrayIdea(1, "Dividing work multiplies what people make", AiCategory.MAIN, -1, true, 3,
                        Collections.singletonList("The greatest improvement in the productive powers of labour"),
                        "The greatest improvement in the productive powers of labour", 3),
                new XrayIdea(2, "Ten men make 48,000 pins a day", AiCategory.EVIDENCE, 1, true, 4,
                        Arrays.asList(PINS, "a second mark of the pin shop", "a third mark that is never sent"), PINS, 4),
                new XrayIdea(3, "Machines come from narrow tasks", AiCategory.MAIN, -1, false, -1,
                        Collections.<String>emptyList(), null, -1),
                new XrayIdea(4, "One task for life dulls the mind", AiCategory.CAVEAT, 1, true, 9,
                        Collections.<String>emptyList(), null, 9));
    }

    static DebateSession session() {
        final List<XrayIdea> ideas = ideas();
        final DebateSession s = new DebateSession("The Wealth of Nations", "ch1", "Of the Division of Labour",
                DebateAuthor.of("Adam Smith"), ideas, ideas.get(0));
        s.readerPosition = "It makes workers' lives worse";
        return s;
    }

    private static DebateRound round(DebateReply.Point point, String why, String next) {
        return new DebateRound("my point", Collections.singletonList(new DebateQuotes.Part("the reply", false)), null, -1,
                false, "", point, why, next);
    }

    @Test
    public void theClaimsAreTheMainIdeas() {
        final List<XrayIdea> claims = DebateSession.claims(ideas());
        assertEquals(2, claims.size());
        assertEquals(1, claims.get(0).id);
        assertEquals(3, claims.get(1).id);
        // a chapter without a main idea offers every idea
        final List<XrayIdea> none = DebateSession.claims(Arrays.asList(ideas().get(1), ideas().get(3)));
        assertEquals(2, none.size());
        assertTrue(DebateSession.claims(new ArrayList<XrayIdea>()).isEmpty());
    }

    @Test
    public void threeRoundsThenItIsOver() {
        final DebateSession s = session();
        assertEquals(1, s.round());
        assertFalse(s.lastRound());
        s.add(round(DebateReply.Point.AUTHOR, "a", "q1"), "It makes everyone richer", "too early");
        assertEquals("It makes everyone richer", s.authorPosition);
        assertEquals("", s.crux);
        assertEquals(2, s.round());
        s.add(round(DebateReply.Point.READER, "b", "q2"), "Another position", "");
        // the position stays the first one given
        assertEquals("It makes everyone richer", s.authorPosition);
        assertTrue(s.lastRound());
        assertFalse(s.finished());
        s.add(round(DebateReply.Point.READER, "c", ""), "", " Whether wealth makes up for a dull life. ");
        assertTrue(s.finished());
        assertEquals(3, s.round());
        assertEquals("Whether wealth makes up for a dull life.", s.crux);
        // a fourth answer is not a round
        s.add(round(DebateReply.Point.AUTHOR, "d", ""), "", "other");
        assertEquals(3, s.rounds.size());
        assertEquals("Whether wealth makes up for a dull life.", s.crux);
    }

    @Test
    public void theResultKeepsPositionsVerdictsAndDateButNotTheChat() throws Exception {
        final DebateSession s = session();
        s.add(round(DebateReply.Point.AUTHOR, "Smith brings the numbers.", "q1"), "It makes everyone richer", "");
        s.add(round(DebateReply.Point.READER, "Smith concedes the harm.", "q2"), "", "");
        s.add(round(DebateReply.Point.DRAW, "", ""), "", "Whether wealth makes up for a dull life.");
        final DebateResult r = s.result(1234L);
        assertEquals(1, r.points(DebateReply.Point.READER));
        assertEquals(1, r.points(DebateReply.Point.AUTHOR));
        assertEquals(1, r.points(DebateReply.Point.DRAW));
        assertEquals(DebateReply.Point.DRAW, r.leader());

        final JSONObject json = r.toJson();
        final String stored = json.toString();
        assertFalse(stored.contains("my point"));
        assertFalse(stored.contains("the reply"));
        final DebateResult back = DebateResult.fromJson(new JSONObject(stored));
        assertNotNull(back);
        assertEquals("Dividing work multiplies what people make", back.claim);
        assertEquals("It makes workers' lives worse", back.you);
        assertEquals("It makes everyone richer", back.author);
        assertEquals("Adam Smith", back.name);
        assertEquals(1234L, back.date);
        assertEquals("Whether wealth makes up for a dull life.", back.crux);
        assertEquals(3, back.rounds.size());
        assertEquals(DebateReply.Point.READER, back.rounds.get(1).point);
        assertEquals("Smith concedes the harm.", back.rounds.get(1).why);
    }

    @Test
    public void theScoreNamesTheLeader() {
        final DebateResult r = new DebateResult();
        r.rounds.add(new DebateResult.Verdict(DebateReply.Point.READER, ""));
        r.rounds.add(new DebateResult.Verdict(DebateReply.Point.READER, ""));
        r.rounds.add(new DebateResult.Verdict(DebateReply.Point.AUTHOR, ""));
        assertEquals(DebateReply.Point.READER, r.leader());
        assertEquals(2, r.points(DebateReply.Point.READER));
        r.rounds.clear();
        r.rounds.add(new DebateResult.Verdict(DebateReply.Point.AUTHOR, ""));
        r.rounds.add(new DebateResult.Verdict(null, null));
        assertEquals(DebateReply.Point.AUTHOR, r.leader());
        assertEquals(1, r.points(DebateReply.Point.DRAW));
    }

    @Test
    public void theKeyNamesTheChapterAndTheIdea() {
        assertEquals("debate:ch1:7", DebateResult.key("ch1", 7));
        assertTrue(DebateResult.key("Book I: Of labour#1", 12).startsWith(DebateResult.prefix("Book I: Of labour#1")));
        assertEquals(12, DebateResult.ideaOf(DebateResult.key("Book I: Of labour#1", 12)));
        assertEquals(-1, DebateResult.ideaOf("debate:ch1:x"));
        assertEquals(-1, DebateResult.ideaOf(null));
        assertNull(DebateResult.fromJson(null));
        assertNull(DebateResult.fromJson(new JSONObject()));
    }

    @Test
    public void aRoundsSourceIsTheCheckedQuoteElseTheIdeaItRestsOn() throws Exception {
        final DebateSession s = session();
        final DebateReply reply = DebateReply.parse("{\"reply\":\"I said “" + PINS + "”.\",\"idea\":4,\"verdict\":"
                + "{\"point\":\"author\",\"why\":\"w\",\"next\":\"n\"}}", s.ids());

        // the quote is the book's: the source line goes to it, in the chapter it was found in
        final DebateQuotes.Checked found = DebateQuotes.check(reply.reply, reply.quote,
                quote -> DebateQuotes.inIdeas(s.ideas, quote));
        final DebateRound quoted = DebateRound.of(s, "msg", reply, found, "Book V, Chapter 1");
        assertTrue(quoted.hasSource());
        assertTrue(quoted.quoted);
        assertEquals(PINS, quoted.source);
        assertEquals(4, quoted.sourcePage);
        assertEquals("Book V, Chapter 1", quoted.sourceChapter);
        assertEquals("I said “" + PINS + "”.", quoted.replyText());
        assertEquals(DebateReply.Point.AUTHOR, quoted.point);
        assertEquals("n", quoted.next);
        assertEquals("Of the Division of Labour", DebateRound.of(s, "msg", reply, found, "").sourceChapter);

        // the quote is not the book's: no marks, and the source line is the idea's, not "quoted"
        final DebateQuotes.Checked none = DebateQuotes.check(reply.reply, reply.quote, quote -> null);
        final DebateRound idea = DebateRound.of(s, "msg", reply, none, "");
        assertTrue(idea.hasSource());
        assertFalse(idea.quoted);
        assertNull(idea.source);
        assertEquals(9, idea.sourcePage);
        assertEquals("Of the Division of Labour", idea.sourceChapter);
        assertEquals("I said " + PINS + ".", idea.replyText());

        // no quote, and an idea that was not located (or none): no source line at all
        final DebateReply bare = DebateReply.parse("{\"reply\":\"I hold to it.\",\"idea\":3,\"point\":\"draw\"}", s.ids());
        final DebateRound plain = DebateRound.of(s, "msg", bare, DebateQuotes.check(bare.reply, "", quote -> null), "");
        assertFalse(plain.hasSource());
        assertEquals("", plain.sourceChapter);
    }

    @Test
    public void cutKeepsOneLine() {
        assertEquals("a b c", DebateSession.cut(" a\n b\tc ", 20));
        assertEquals("", DebateSession.cut(null, 20));
        final String cut = DebateSession.cut("one two three four five six seven", 14);
        assertTrue(cut, cut.length() <= 14);
        assertTrue(cut.endsWith("…"));
        assertEquals("one two three…", cut);
    }
}
