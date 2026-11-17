package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FictionPromptsTest {

    @Test
    public void everyRequestCarriesTheShield() {
        for (boolean update : new boolean[]{false, true}) {
            final String s = FictionPrompts.castSystem("", update);
            assertTrue(s.contains("only up to the page the reader is on"));
            assertTrue(s.contains("Never use what you know of this book from anywhere else"));
            assertTrue(s.contains("the language the text is written in"));
            assertTrue(s.contains("exactly as the book spells it"));
        }
        assertTrue(FictionPrompts.castSystem("Vietnamese", false).contains("Write in Vietnamese."));
        assertFalse(FictionPrompts.castSystem("", false).contains("who's who so far"));
        assertTrue(FictionPrompts.castSystem("", true).contains("who's who so far"));
    }

    @Test
    public void theFirstRequestHasNoCastAndAnUpdateCarriesItWithoutStoryLines() {
        final String first = FictionPrompts.castUser("Around the World in Eighty Days", null, 1, 1, "<chapter n=\"1\">\nx\n</chapter>");
        assertTrue(first.startsWith("Book: Around the World in Eighty Days\n"));
        assertTrue(first.contains("from the start of the book"));
        assertTrue(first.contains("ends at the reader's page"));
        final FictionCast cast = FictionCastTest.verneCast();
        final String next = FictionPrompts.castUser("T", cast, 2, 3, "<chapter n=\"16\">\ny\n</chapter>");
        assertTrue(next.contains("part 2"));
        assertTrue(next.contains("up to the end of chapter 15"));
        assertTrue(next.contains("\"id\":\"fogg\""));
        assertTrue(next.contains("On the train to Calcutta."));
        assertFalse(next.contains("Makes the bet at the club."));
        assertTrue(next.endsWith("</chapter>"));
    }

    @Test
    public void theCapFollowsTheSettingsWithinBounds() {
        assertEquals(FictionPrompts.MAX_BLOCK, FictionPrompts.cap(600_000));
        assertEquals(18_000, FictionPrompts.cap(24_000));
        assertEquals(FictionPrompts.MIN_BLOCK, FictionPrompts.cap(1000));
    }

    @Test
    public void theTimelineRequestCarriesTheShieldAndAsksForAPlanOnlyWhenTheBookStatesOne() {
        for (boolean update : new boolean[]{false, true}) {
            final String s = FictionPrompts.timelineSystem("", update);
            assertTrue(s.contains("only up to the page the reader is on"));
            assertTrue(s.contains("Never use what you know of this book from anywhere else"));
            assertTrue(s.contains("only when the book itself states a plan"));
            assertTrue(s.contains("otherwise null"));
            assertTrue(s.contains("for a real place only"));
            assertTrue(s.contains("never work a time out yourself"));
        }
        assertTrue(FictionPrompts.timelineSystem("", true).contains("timeline so far"));
    }

    @Test
    public void theTimelineRequestUsesTheNamesOfPeopleMetByThen() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        // by the book's chapter 5 only Fogg has been met
        assertEquals(java.util.Arrays.asList("Phileas Fogg"), FictionPrompts.names(cast, c, 5));
        // by chapter 14 everyone, the main people first
        assertEquals(java.util.Arrays.asList("Phileas Fogg", "Aouda", "The Parsee guide"), FictionPrompts.names(cast, c, 14));
        final String first = FictionPrompts.timelineUser("T", FictionPrompts.names(cast, c, 5), null, 1, 1, "<chapter n=\"1\">x</chapter>");
        assertTrue(first.contains("by the names to use for them: Phileas Fogg."));
        assertFalse(first.contains("Aouda"));
        final FictionTimeline t = FictionTimelineTest.verneTimeline();
        final String next = FictionPrompts.timelineUser("T", null, t, 2, 2, "<chapter n=\"16\">y</chapter>");
        assertTrue(next.contains("timeline so far (up to the end of chapter 15)"));
        assertTrue(next.contains("\"places_so_far\""));
        assertTrue(next.contains("Round the world in eighty days"));
        assertTrue(next.contains("Aouda rescued at dawn"));
    }
}
