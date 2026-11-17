package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ReviewCardsTest {

    private static ReviewQueue.Entry entry(String book, String idea, String chapter) {
        final ReviewItem i = new ReviewItem();
        i.chapterKey = chapter.toLowerCase() + "#0";
        i.id = String.valueOf(Math.abs(idea.hashCode()));
        i.idea = idea;
        i.chapterTitle = chapter;
        i.due = "2026-11-07";
        return new ReviewQueue.Entry(book, "", "The Wealth of Nations", "Adam Smith", i);
    }

    private static Map<String, List<ReviewCards.Candidate>> pools() {
        final Map<String, List<ReviewCards.Candidate>> pools = new HashMap<>();
        pools.put("smith", Arrays.asList(
                new ReviewCards.Candidate("Time is lost switching between tasks", "Ch. 1"),
                new ReviewCards.Candidate("Narrow tasks invite machines", "Ch. 1"),
                new ReviewCards.Candidate("narrow tasks invite machines.", "Ch. 1"),
                new ReviewCards.Candidate("The market limits how far work can be split", "Ch. 3"),
                new ReviewCards.Candidate("Exchange runs on self-interest", "Ch. 2"),
                new ReviewCards.Candidate("Money replaces barter", "Ch. 4"),
                new ReviewCards.Candidate("", "Ch. 5")));
        pools.put("verne", Arrays.asList(new ReviewCards.Candidate("Fix believes Fogg robbed the bank", "Chapter V")));
        pools.put("work", Arrays.asList(new ReviewCards.Candidate("Attention residue", "Rule 1"),
                new ReviewCards.Candidate("Depth is rare and valuable", "Intro")));
        return pools;
    }

    private static Map<String, String> titles() {
        final Map<String, String> t = new HashMap<>();
        t.put("smith", "The Wealth of Nations");
        t.put("verne", "Around the World in Eighty Days");
        t.put("work", "Deep Work");
        return t;
    }

    @Test
    public void aCardHasTheIdeaAndThreeOthersOfItsBook() {
        final List<ReviewQueue.Entry> due = Arrays.asList(entry("smith", "Time is lost switching between tasks", "Ch. 1"));
        for (int seed = 0; seed < 20; seed++) {
            final ReviewCards.Card card = ReviewCards.build(due, pools(), titles(), new Random(seed)).get(0);
            assertEquals(4, card.options.size());
            final Set<String> seen = new HashSet<>();
            int correct = 0;
            for (ReviewCards.Option o : card.options) {
                // never the idea itself twice, never the same wrong idea twice, never an empty one
                assertTrue(seen.add(ReviewSources.norm(o.text)));
                assertFalse(o.text.isEmpty());
                assertTrue(o.tag.startsWith("Ch. "));
                if (o.correct) {
                    correct++;
                    assertEquals("Time is lost switching between tasks", o.text);
                }
            }
            assertEquals(1, correct);
            assertEquals(card.options.get(card.correct()).text, "Time is lost switching between tasks");
            assertEquals(3, card.others().size());
        }
    }

    @Test
    public void optionsAreShuffled() {
        final List<ReviewQueue.Entry> due = Arrays.asList(entry("smith", "Time is lost switching between tasks", "Ch. 1"));
        final Set<Integer> places = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            places.add(ReviewCards.build(due, pools(), titles(), new Random(seed)).get(0).correct());
        }
        assertEquals(4, places.size());
    }

    @Test
    public void aBookWithFewIdeasBorrowsFromTheOthers() {
        final List<ReviewQueue.Entry> due = Arrays.asList(entry("verne", "Fix believes Fogg robbed the bank", "Chapter V"));
        final ReviewCards.Card card = ReviewCards.build(due, pools(), titles(), new Random(1)).get(0);
        assertEquals(4, card.options.size());
        for (ReviewCards.Option o : card.options) {
            if (!o.correct) {
                // a borrowed idea says which book it is from
                assertTrue(o.tag.equals("The Wealth of Nations") || o.tag.equals("Deep Work"));
            }
        }
        // no pools at all: the card still stands, with the idea alone
        final ReviewCards.Card alone = ReviewCards.build(due, null, null, new Random(1)).get(0);
        assertEquals(1, alone.options.size());
        assertEquals(0, alone.correct());
    }

    @Test
    public void theRequestsTextsGoOnTheirCards() {
        final List<ReviewQueue.Entry> due = new ArrayList<>(Arrays.asList(
                entry("smith", "Time is lost switching between tasks", "Ch. 1"),
                entry("smith", "Exchange runs on self-interest", "Ch. 2"),
                entry("work", "Attention residue", "Rule 1")));
        final List<ReviewCards.Card> cards = ReviewCards.build(due, pools(), titles(), new Random(3));
        ReviewCards.apply(cards, ReviewPrompts.parse("{\"cards\":[{\"n\":3,\"scenario\":\"You close a call and "
                + "open a report. Which idea explains it best?\",\"right\":\"Part of you is still in the call.\","
                + "\"not_quite\":\"Look at what stays behind.\"},{\"n\":1,\"scenario\":\"\",\"right\":\"x\"}]}"));
        assertFalse(cards.get(0).hasScenario());
        assertFalse(cards.get(1).hasScenario());
        assertTrue(cards.get(2).hasScenario());
        assertEquals("Part of you is still in the call.", cards.get(2).right);
        assertEquals("Look at what stays behind.", cards.get(2).notQuite);
        ReviewCards.apply(cards, null);
        assertTrue(cards.get(2).hasScenario());
    }

    @Test
    public void theFallbackHidesTheFirstWords() {
        assertEquals("___ ___ ___ switching between tasks", ReviewCards.hidden("Time is lost switching between tasks"));
        assertEquals("___ residue", ReviewCards.hidden("Attention residue"));
        assertEquals("___", ReviewCards.hidden("Specialisation"));
        assertEquals("___ ___ ___ ___ e f g h i j", ReviewCards.hidden("a b c d e f g h i j"));
        assertEquals("", ReviewCards.hidden("  "));
        assertEquals("", ReviewCards.hidden(null));
    }
}
