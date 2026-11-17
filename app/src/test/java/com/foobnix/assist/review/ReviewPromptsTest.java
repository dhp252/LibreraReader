package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ReviewPromptsTest {

    @Test
    public void theRequestCarriesIdeasOnly() {
        final ReviewItem i = new ReviewItem();
        i.chapterKey = "c#0";
        i.id = "1";
        i.idea = "Time is lost\nswitching   between tasks";
        i.chapterTitle = "Of the Division of Labour";
        i.due = "2026-11-07";
        i.quote = "the time commonly lost in passing from one sort of work to another";
        final ReviewQueue.Entry e = new ReviewQueue.Entry("smith.epub", "/sdcard/Books/smith.epub",
                "The Wealth of Nations", "Adam Smith", i);
        final Map<String, List<ReviewCards.Candidate>> pools = new HashMap<>();
        pools.put("smith.epub", Arrays.asList(new ReviewCards.Candidate("Narrow tasks invite machines", "Ch. 1"),
                new ReviewCards.Candidate("Exchange runs on self-interest", "Ch. 2")));
        final List<ReviewCards.Card> cards = ReviewCards.build(Arrays.asList(e), pools, null, new Random(5));
        final String user = ReviewPrompts.user(cards);
        assertTrue(user.startsWith("Cards: 1\n"));
        assertTrue(user.contains("Card 1\nBook: The Wealth of Nations (Adam Smith)\n"));
        assertTrue(user.contains("Idea to review: Time is lost switching between tasks\n"));
        assertTrue(user.contains("- Narrow tasks invite machines\n"));
        assertTrue(user.contains("- Exchange runs on self-interest\n"));
        // no book text, no file path, and the right option is not listed as a wrong one
        assertFalse(user.contains("commonly lost"));
        assertFalse(user.contains("sdcard"));
        assertFalse(user.contains("- Time is lost"));

        final String system = ReviewPrompts.system("Vietnamese");
        assertTrue(system.contains("Write in Vietnamese."));
        assertTrue(system.contains("\"not_quite\""));
        assertTrue(system.contains("not flashcards"));
        assertTrue(ReviewPrompts.system(" ").contains("the language the ideas are written in"));
    }

    @Test
    public void readsTheAnswerAsAsked() {
        final Map<Integer, ReviewPrompts.Texts> t = ReviewPrompts.parse("{\"cards\":[{\"n\":1,\"scenario\":\"S1?\","
                + "\"right\":\"Right: because.\",\"not_quite\":\"Not quite. Look again.\"},{\"n\":2,\"scenario\":\"S2?\","
                + "\"right\":\"R2\",\"not_quite\":\"N2\"}]}");
        assertEquals(2, t.size());
        assertEquals("S1?", t.get(1).scenario);
        // the screen writes "Right:" and "Not quite." itself
        assertEquals("because.", t.get(1).right);
        assertEquals("Look again.", t.get(1).notQuite);
        assertEquals("N2", t.get(2).notQuite);
    }

    @Test
    public void readsAnAnswerInOtherShapes() {
        // a code fence and words around it, other names, numbers as text
        Map<Integer, ReviewPrompts.Texts> t = ReviewPrompts.parse("Here you go:\n```json\n{\"questions\": [{\"card\": "
                + "\"Card 2\", \"question\": \"Q2?\", \"correct\": \"C2\", \"wrong\": \"W2\"}]}\n```\nHope it helps.");
        assertEquals(1, t.size());
        assertEquals("Q2?", t.get(2).scenario);
        assertEquals("C2", t.get(2).right);
        assertEquals("W2", t.get(2).notQuite);

        // a bare list without numbers: the place in the list
        t = ReviewPrompts.parse("[{\"scenario\":\"A?\"},{\"scenario\":\"B?\",\"notQuite\":\"nb\"}]");
        assertEquals("A?", t.get(1).scenario);
        assertEquals("", t.get(1).right);
        assertEquals("B?", t.get(2).scenario);
        assertEquals("nb", t.get(2).notQuite);

        // an object keyed by number
        t = ReviewPrompts.parse("{\"1\":{\"scenario\":\"A?\"},\"3\":{\"scenario\":\"C?\"}}");
        assertEquals(2, t.size());
        assertEquals("C?", t.get(3).scenario);

        // one card on its own
        t = ReviewPrompts.parse("{\"scenario\":\"Only?\",\"right\":\"r\"}");
        assertEquals("Only?", t.get(1).scenario);

        // a card without a scenario is left out, the first of two with one number wins
        t = ReviewPrompts.parse("{\"cards\":[{\"n\":1,\"right\":\"r\"},{\"n\":2,\"scenario\":\"x\"},{\"n\":2,\"scenario\":\"y\"},"
                + "\"junk\",{\"n\":4,\"scenario\":{\"a\":1}}]}");
        assertEquals(1, t.size());
        assertEquals("x", t.get(2).scenario);
    }

    @Test
    public void anUnreadableAnswerGivesNothing() {
        assertTrue(ReviewPrompts.parse(null).isEmpty());
        assertTrue(ReviewPrompts.parse("").isEmpty());
        assertTrue(ReviewPrompts.parse("I cannot do that.").isEmpty());
        assertTrue(ReviewPrompts.parse("{\"cards\": [{\"n\": 1, \"scenario\": \"cut off").isEmpty());
        assertTrue(ReviewPrompts.parse("{\"cards\": \"none\"}").isEmpty());
        assertTrue(ReviewPrompts.parse("[1, 2, 3]").isEmpty());
        assertEquals("Rightly so.", ReviewPrompts.lead("Rightly so.", "right"));
        assertEquals("yes", ReviewPrompts.lead("RIGHT: yes", "right"));
    }
}
