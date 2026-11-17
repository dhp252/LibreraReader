package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FictionQuotesTest {

    private static String[] pages() {
        final String[] p = new String[6];
        p[0] = "Mr. Phileas Fogg lived, in 1872, at No. 7, Saville Row, Burlington Gardens.";
        p[1] = "He was one of the most noticeable members of the Reform Club, though he seemed always to avoid attracting attention.";
        p[2] = "Fix, the detective, had no doubt that this was his man; he resolved to follow him to the";
        p[3] = "end of the world if need be. The steamer Mongolia was due at Suez that morning.";
        p[4] = "The Parsee guide led the elephant into the thick of the forest before dawn.";
        p[5] = "At Hong Kong the Carnatic had sailed the evening before, twelve hours ahead of her time.";
        return p;
    }

    @Test
    public void aQuoteIsKeptAsTheBookHasItWithItsPage() {
        final FictionQuotes q = new FictionQuotes(pages(), 0, 5);
        final FictionQuotes.Found f = q.find("the detective had no doubt that this was his man");
        assertNotNull(f);
        assertEquals("the detective, had no doubt that this was his man;", f.text);
        assertEquals(2, f.page);
        // over a page edge: the page it starts on
        final FictionQuotes.Found over = q.find("he resolved to follow him to the end of the world if need be");
        assertNotNull(over);
        assertEquals(2, over.page);
    }

    @Test
    public void wordsThatAreNotInTheTextAreNoQuote() {
        final FictionQuotes q = new FictionQuotes(pages(), 0, 5);
        assertNull(q.find("Fogg swore that he would win the wager at any cost"));
        assertNull(q.find(""));
        assertNull(q.find(null));
        // too short to be found safely
        assertNull(q.find("his man"));
    }

    @Test
    public void onlyThePagesOfTheBlockAreLookedIn() {
        // page 5 lies after the block that was sent: its words are not there to be found
        final FictionQuotes q = new FictionQuotes(pages(), 0, 5);
        assertNull(q.find("the Carnatic had sailed the evening before"));
        assertNotNull(new FictionQuotes(pages(), 0, 6).find("the Carnatic had sailed the evening before"));
        assertNull(new FictionQuotes(pages(), 3, 5).find("the most noticeable members of the Reform Club"));
    }

    @Test
    public void anAnswersQuotesAreCheckedAndTheOthersCleared() {
        final FictionTimeline t = FictionTimeline.parse(FictionTimelineTest.ANSWER, FictionChaptersTest.verne(), 15);
        t.places.get(1).quote = "The steamer Mongolia was due at Suez that morning";
        new FictionQuotes(pages(), 0, 5).check(t);
        assertEquals("the detective, had no doubt that this was his man;", t.moments.get(0).quote);
        assertEquals(2, t.moments.get(0).page);
        // the model made this one up: the moment stays, without a quote
        assertEquals("", t.moments.get(1).quote);
        assertEquals(-1, t.moments.get(1).page);
        assertEquals("Passepartout's trouble at the pagoda", t.moments.get(1).line);
        assertEquals("", t.moments.get(2).quote);
        assertEquals("The steamer Mongolia was due at Suez that morning.", t.places.get(1).quote);
        assertEquals(3, t.places.get(1).page);
        assertEquals("", t.places.get(0).quote);
    }
}
