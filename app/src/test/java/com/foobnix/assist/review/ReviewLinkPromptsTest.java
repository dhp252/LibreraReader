package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReviewLinkPromptsTest {

    private static ReviewLinkPrompts.Idea idea(String chapter, String id, String text) {
        return new ReviewLinkPrompts.Idea(chapter, "Title of " + chapter, id, text);
    }

    private static ReviewLinkPrompts.Book mine() {
        return new ReviewLinkPrompts.Book("wealth.epub", "The Wealth of Nations", "Adam Smith", 1776, Arrays.asList(
                idea("c1", "1", "Splitting work multiplies output"),
                idea("c1", "2", "Time lost switching tasks"),
                idea("c3", "1", "Market size limits splitting")));
    }

    private static List<ReviewLinkPrompts.Book> others() {
        final List<ReviewLinkPrompts.Book> out = new ArrayList<>();
        out.add(new ReviewLinkPrompts.Book("deep.epub", "Deep Work", "Cal Newport", 2016, Arrays.asList(
                idea("d1", "5", "Attention residue"), idea("d2", "line", "Depth is rare and valuable"))));
        out.add(new ReviewLinkPrompts.Book("capital.epub", "Capital", "", 0, Arrays.asList(
                idea("k14", "2", "Specialists end up narrowed"))));
        return out;
    }

    @Test
    public void theRequestCarriesTheListsWithTheirRefs() {
        final String user = ReviewLinkPrompts.user(mine(), others());
        assertTrue(user.startsWith("Book A (the book the reader is in): The Wealth of Nations, Adam Smith, 1776\n"));
        assertTrue(user.contains("A2. Time lost switching tasks\n"));
        assertTrue(user.contains("Book B (in the reader's library): Deep Work, Cal Newport, 2016\n"));
        assertTrue(user.contains("B1. Attention residue\n"));
        assertTrue(user.contains("Book C (in the reader's library): Capital\n"));
        assertTrue(user.contains("C1. Specialists end up narrowed\n"));
        // file names, chapter keys and ids stay on the device
        assertFalse(user.contains("epub"));
        assertFalse(user.contains("k14"));
    }

    @Test
    public void atMostEightOtherBooksAreSent() {
        final List<ReviewLinkPrompts.Book> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new ReviewLinkPrompts.Book("b" + i, "Title " + i, "", 0, Arrays.asList(idea("c", "1", "idea " + i))));
        }
        final String user = ReviewLinkPrompts.user(mine(), many);
        assertTrue(user.contains("Book I ("));
        assertFalse(user.contains("Book J ("));
        // a ref of a book that was not sent is not known
        final String reply = "{\"links\":[{\"a\":\"A1\",\"b\":\"J1\",\"relation\":\"same\",\"why\":\"x\"}]}";
        assertTrue(ReviewLinkPrompts.parse(reply, mine(), many, "2026-11-07").isEmpty());
    }

    @Test
    public void aLinkNamesBothIdeasAsTheyAreStored() {
        final String reply = "Here you go:\n```json\n{\"links\":[{\"a\":\"A2\",\"b\":\"B1\",\"relation\":\"same\","
                + "\"why\":\"Both say that switching\\n costs attention.\"}]}\n```";
        final List<ReviewLink> links = ReviewLinkPrompts.parse(reply, mine(), others(), "2026-11-07");
        assertEquals(1, links.size());
        final ReviewLink l = links.get(0);
        assertEquals("wealth.epub", l.a.book);
        assertEquals("The Wealth of Nations", l.a.bookTitle);
        assertEquals("c1", l.a.chapterKey);
        assertEquals("2", l.a.ideaId);
        assertEquals("Time lost switching tasks", l.a.idea);
        assertEquals("deep.epub", l.b.book);
        assertEquals("d1", l.b.chapterKey);
        assertEquals("5", l.b.ideaId);
        assertEquals("Attention residue", l.b.idea);
        assertEquals(ReviewLink.SAME, l.relation);
        assertEquals("Both say that switching costs attention.", l.why);
        assertEquals(ReviewLink.NEW, l.state);
        assertEquals("2026-11-07", l.day);
        assertEquals(240, l.yearsApart());
        assertEquals(ReviewLink.id(l.a, l.b), l.id);
    }

    @Test
    public void unknownRefsOneBookPairsAndOddRelationsAreDropped() {
        final String reply = "{\"links\":["
                + "{\"a\":\"A9\",\"b\":\"B1\",\"relation\":\"same\",\"why\":\"no such idea\"},"
                + "{\"a\":\"A1\",\"b\":\"D1\",\"relation\":\"same\",\"why\":\"no such book\"},"
                + "{\"a\":\"A1\",\"b\":\"A3\",\"relation\":\"extends\",\"why\":\"one book\"},"
                + "{\"a\":\"B1\",\"b\":\"C1\",\"relation\":\"same\",\"why\":\"not the open book\"},"
                + "{\"a\":\"A1\",\"b\":\"B2\",\"relation\":\"similar\",\"why\":\"odd relation\"},"
                + "{\"a\":\"A3\",\"b\":\"C1\",\"relation\":\"disagrees\",\"why\":\"kept\"}]}";
        final List<ReviewLink> links = ReviewLinkPrompts.parse(reply, mine(), others(), "2026-11-07");
        assertEquals(1, links.size());
        assertEquals("kept", links.get(0).why);
        assertEquals(ReviewLink.DISAGREES, links.get(0).relation);
        assertEquals("capital.epub", links.get(0).b.book);
    }

    @Test
    public void endsTheOtherWayRoundAreTurnedAndAPairIsKeptOnce() {
        final String reply = "[{\"a\":\"b1\",\"b\":\"[A2]\",\"relation\":\"Same idea\",\"reason\":\"turned\"},"
                + "{\"a\":{\"ref\":\"A 2\"},\"b\":\"B1.\",\"relation\":\"extends\",\"why\":\"again\"}]";
        final List<ReviewLink> links = ReviewLinkPrompts.parse(reply, mine(), others(), "2026-11-07");
        assertEquals(1, links.size());
        assertEquals("wealth.epub", links.get(0).a.book);
        assertEquals("deep.epub", links.get(0).b.book);
        assertEquals("turned", links.get(0).why);
    }

    @Test
    public void atMostThreeLinks() {
        final String reply = "{\"connections\":["
                + "{\"a\":\"A1\",\"b\":\"B1\",\"relation\":\"same\"},"
                + "{\"a\":\"A1\",\"b\":\"B2\",\"relation\":\"same\"},"
                + "{\"a\":\"A2\",\"b\":\"B1\",\"relation\":\"same\"},"
                + "{\"a\":\"A3\",\"b\":\"C1\",\"relation\":\"same\"}]}";
        assertEquals(ReviewLinkPrompts.MAX_LINKS, ReviewLinkPrompts.parse(reply, mine(), others(), "2026-11-07").size());
    }

    @Test
    public void outsideWorksLoseTheirQuoteMarks() {
        final String reply = "{\"links\":[{\"a\":\"A1\",\"b\":\"C1\",\"relation\":\"disagrees\",\"why\":\"w\",\"also\":["
                + "{\"relation\":\"disagrees\",\"work\":\"Marx, Capital, ch. 14\",\"point\":\"\\\"specialists end up crippled\\\"\"},"
                + "{\"relation\":\"same\",\"work\":\"X\",\"point\":\"dropped\"},"
                + "{\"relation\":\"extends\",\"work\":\"\",\"point\":\"no work named\"},"
                + "{\"relation\":\"extends\",\"work\":\"Babbage, 1832\",\"point\":\"pay each step only for its skill\"},"
                + "{\"relation\":\"extends\",\"work\":\"One more\",\"point\":\"over the limit\"}]}]}";
        final List<ReviewLink> links = ReviewLinkPrompts.parse(reply, mine(), others(), "2026-11-07");
        assertEquals(1, links.size());
        final ReviewLink l = links.get(0);
        assertEquals(2, l.also.size());
        assertEquals(ReviewLink.DISAGREES, l.also.get(0).relation);
        assertEquals("Marx, Capital, ch. 14: specialists end up crippled", l.also.get(0).text);
        assertEquals("Babbage, 1832: pay each step only for its skill", l.also.get(1).text);
    }

    @Test
    public void unquoteKeepsApostrophesInsideWords() {
        assertEquals("the worker's time isn't free", ReviewLinkPrompts.unquote("“the worker's time isn't free”"));
        assertEquals("plain words.", ReviewLinkPrompts.unquote("'plain words'."));
    }

    @Test
    public void anAnswerThatIsNotJsonGivesNothing() {
        assertTrue(ReviewLinkPrompts.parse("I found no connections.", mine(), others(), "2026-11-07").isEmpty());
        assertTrue(ReviewLinkPrompts.parse("{\"links\":[]}", mine(), others(), "2026-11-07").isEmpty());
        assertTrue(ReviewLinkPrompts.parse(null, mine(), others(), "2026-11-07").isEmpty());
        assertTrue(ReviewLinkPrompts.parse("{\"links\":[{\"a\":\"A1\"}]}", mine(), null, "2026-11-07").isEmpty());
    }

    @Test
    public void spreadCoversALongListAndDropsRepeats() {
        final List<ReviewLinkPrompts.Idea> ideas = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ideas.add(idea("c" + i, "1", "idea number " + i));
        }
        ideas.add(idea("c0", "2", "Idea  number 0"));
        ideas.add(idea("c0", "3", " "));
        final List<ReviewLinkPrompts.Idea> out = ReviewLinkPrompts.spread(ideas, 40);
        assertEquals(40, out.size());
        assertEquals("idea number 0", out.get(0).text);
        assertEquals("idea number 97", out.get(39).text);
        assertEquals(3, ReviewLinkPrompts.spread(mine().ideas, 40).size());
        assertTrue(ReviewLinkPrompts.spread(null, 40).isEmpty());
    }
}
