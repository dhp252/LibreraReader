package com.foobnix.assist.recall;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RecallTest {

    private static final String CHAPTER = "Take a tiny trade: pin-making. In a workshop the job is split into about "
            + "eighteen steps. Ten such work- ers made about 48,000 pins a day, or 4,800 each. The division of labour "
            + "cannot be pushed as far in farming as in manufacture. Why it works, in three parts: practice makes each "
            + "worker faster, nobody loses time switching tasks, and machines help.";

    private static List<Recall.Idea> five() {
        final List<Recall.Idea> ideas = new ArrayList<>();
        ideas.add(new Recall.Idea(11, "Splitting work multiplies output"));
        ideas.add(new Recall.Idea(12, "Ten workers, about 48,000 pins a day"));
        ideas.add(new Recall.Idea(15, "Practice makes each worker faster"));
        ideas.add(new Recall.Idea(16, "Narrow tasks invite machines"));
        ideas.add(new Recall.Idea(19, "No time lost switching tasks").add("nobody loses time switching tasks", 7));
        return ideas;
    }

    private static List<String> words(String text) {
        return Arrays.asList(text.split(" "));
    }

    @Test
    public void checksKnownIdeasByTheirNumberInTheRequest() {
        final String answer = "Sure!\n```json\n{\"ideas\": [{\"id\": 1, \"state\": \"remembered\"}, "
                + "{\"id\": \"2\", \"state\": \"Recalled\"}, {\"id\": 3, \"state\": \"yes\"}, "
                + "{\"id\": 4, \"state\": \"partly\"}, {\"id\": 9, \"state\": \"remembered\"}],"
                + " \"question\": \" Wait, why would switching tasks waste time? \"}\n```";
        final Recall.Check c = Recall.parseCheck(answer, five());
        assertNotNull(c);
        assertEquals(5, c.ideas.size());
        assertEquals(Recall.REMEMBERED, c.ideas.get(0).state);
        assertEquals(Recall.REMEMBERED, c.ideas.get(1).state);
        assertEquals(Recall.REMEMBERED, c.ideas.get(2).state);
        assertEquals(Recall.UNSURE, c.ideas.get(3).state);
        // the model left the fifth out: it counts as missed, and keeps its place in the book
        assertEquals(Recall.MISSED, c.ideas.get(4).state);
        assertEquals("nobody loses time switching tasks", c.ideas.get(4).quote());
        assertEquals(7, c.ideas.get(4).page());
        assertEquals(19, c.ideas.get(4).id);
        assertEquals("Wait, why would switching tasks waste time?", c.question);
        // the canvas: 3 of 5, plus 1 half-remembered, is 70%
        assertEquals(3, Recall.count(c.ideas, Recall.REMEMBERED));
        assertEquals(1, Recall.count(c.ideas, Recall.UNSURE));
        assertEquals(70, Recall.score(c.ideas));
    }

    @Test
    public void aBareArrayInOrderIsReadToo() {
        final Recall.Check c = Recall.parseCheck("[{\"state\":\"missed\"},{\"state\":\"unsure\"},{\"state\":\"got it\"},"
                + "{\"state\":\"forgot\"},{\"state\":\"no\"}]", five());
        assertNotNull(c);
        assertEquals(Recall.MISSED, c.ideas.get(0).state);
        assertEquals(Recall.UNSURE, c.ideas.get(1).state);
        assertEquals(Recall.REMEMBERED, c.ideas.get(2).state);
        assertEquals("", c.question);
        assertEquals(30, Recall.score(c.ideas));
    }

    @Test
    public void anAnswerWithoutAStateIsUnreadable() {
        assertNull(Recall.parseCheck("I think they did well.", five()));
        assertNull(Recall.parseCheck("{\"ideas\": [{\"id\": 1, \"state\": \"purple\"}]}", five()));
        assertNull(Recall.parseCheck(null, five()));
        assertNull(Recall.parseCheck("{\"ideas\": []}", null));
    }

    @Test
    public void theKnownIdeasAreNotChangedByACheck() {
        final List<Recall.Idea> known = five();
        Recall.parseCheck("{\"ideas\": [{\"id\": 1, \"state\": \"remembered\"}]}", known);
        assertEquals("", known.get(0).state);
    }

    @Test
    public void readsTheModelsOwnIdeasAndTiesThemToTheBook() {
        final String answer = "{\"ideas\": ["
                + "{\"idea\": \"Ten workers, about 48,000 pins a day\", \"quote\": \"Ten such workers made about 48,000 pins a day\", \"state\": \"remembered\"},"
                + "{\"idea\": \"Farming splits less\", \"quote\": \"This sentence is nowhere in the chapter at all.\", \"state\": \"missed\"},"
                + "{\"idea\": \"\", \"quote\": \"x\", \"state\": \"missed\"},"
                + "{\"idea\": \"No time lost switching\", \"quote\": \"nobody loses time switching tasks\"}],"
                + " \"question\": \"Why pins?\"}";
        final Recall.Check c = Recall.parseCheck(answer, null);
        assertNotNull(c);
        assertEquals(3, c.ideas.size());
        assertEquals(1, c.ideas.get(0).id);
        assertEquals(3, c.ideas.get(2).id);
        // no state given: nothing shows it was recalled
        assertEquals(Recall.MISSED, c.ideas.get(2).state);

        final List<String> words = words(CHAPTER);
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            pages.add(i < 30 ? 4 : 5);
        }
        Recall.place(c.ideas, words, pages);
        // the book's own words (its hyphenation too), and the page they are on
        assertEquals("Ten such workers made about 48,000 pins a day,", c.ideas.get(0).quote());
        assertEquals(4, c.ideas.get(0).page());
        assertTrue(c.ideas.get(0).hasPlace());
        // a quote that is not in the chapter: the idea stays, without a place
        assertEquals("", c.ideas.get(1).quote());
        assertFalse(c.ideas.get(1).hasPlace());
        assertEquals(5, c.ideas.get(2).page());
    }

    @Test
    public void ideasRequestGivesIdeasWithoutAState() {
        final Recall.Check c = Recall.parseIdeas("{\"ideas\":[{\"idea\":\"A\",\"quote\":\"q\"},{\"idea\":\"B\"}],"
                + "\"question\":\"What is it about?\"}");
        assertNotNull(c);
        assertEquals(2, c.ideas.size());
        assertEquals("", c.ideas.get(0).state);
        assertEquals("What is it about?", c.question);
        assertNull(Recall.parseIdeas("no json"));
    }

    @Test
    public void scoreRoundsAndStaysInRange() {
        assertEquals(0, Recall.score(new ArrayList<Recall.Idea>()));
        final List<Recall.Idea> three = new ArrayList<>(five().subList(0, 3));
        three.get(0).state = Recall.REMEMBERED;
        three.get(1).state = Recall.REMEMBERED;
        three.get(2).state = Recall.MISSED;
        assertEquals(67, Recall.score(three));
        three.get(2).state = Recall.REMEMBERED;
        assertEquals(100, Recall.score(three));
        assertEquals(100, Recall.percent(140));
        assertEquals(0, Recall.percent(-3));
    }

    @Test
    public void nudgeIsTheFirstWordOfEachIdea() {
        final List<Recall.Idea> ideas = five();
        ideas.add(new Recall.Idea(20, "“Saunters” a little"));
        ideas.add(new Recall.Idea(21, "  "));
        assertEquals(Arrays.asList("Splitting…", "Ten…", "Practice…", "Narrow…", "No…", "Saunters…"), Recall.nudge(ideas));
        assertTrue(Recall.nudge(null).isEmpty());
    }

    @Test
    public void keyIdeasAreTheMainOnes() {
        final List<Recall.Idea> all = five();
        all.get(1).parent = 11;
        all.get(2).parent = 11;
        final List<Recall.Idea> main = Recall.keyIdeas(all);
        assertEquals(3, main.size());
        assertEquals(16, main.get(1).id);
        // a single main idea: its supports are ideas too
        all.get(3).parent = 11;
        all.get(4).parent = 11;
        assertEquals(5, Recall.keyIdeas(all).size());
        final List<Recall.Idea> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(new Recall.Idea(i, "Idea " + i));
        }
        assertEquals(Recall.MAX_IDEAS, Recall.keyIdeas(many).size());
        assertEquals(0, Recall.keyIdeas(many).get(0).id);
    }

    @Test
    public void attemptsKeepAShortHistory() throws Exception {
        Recall.Attempt item = null;
        for (int i = 0; i < Recall.HISTORY + 3; i++) {
            final Recall.Check c = Recall.parseCheck("{\"ideas\": [{\"id\": 1, \"state\": \"remembered\"}]}", five());
            item = Recall.attempt(item, "Chapter 1", 1000L + i, 90, " words " + i + " ", true, 28, c, true);
        }
        assertEquals(Recall.HISTORY, item.history.size());
        // oldest first, this try last
        assertEquals(1003L, item.history.get(0).date);
        assertEquals(1000L + Recall.HISTORY + 2, item.history.get(Recall.HISTORY - 1).date);
        assertEquals(90, item.history.get(0).expected);
        assertEquals(20, item.history.get(0).got);
        assertEquals("words " + (Recall.HISTORY + 2), item.words);

        final Recall.Attempt back = Recall.Attempt.fromJson(new JSONObject(item.toJson().toString()));
        assertTrue(back.isDone());
        assertEquals("Chapter 1", back.title);
        assertEquals(90, back.expected);
        assertEquals(20, back.got);
        assertTrue(back.spoken);
        assertEquals(28, back.seconds);
        assertTrue(back.fromPassages);
        assertEquals(5, back.ideas.size());
        assertEquals(Recall.REMEMBERED, back.ideas.get(0).state);
        assertEquals(Recall.MISSED, back.ideas.get(4).state);
        assertEquals("nobody loses time switching tasks", back.ideas.get(4).quote());
        assertEquals(7, back.ideas.get(4).page());
        assertEquals(Recall.HISTORY, back.history.size());
        assertEquals(20, back.history.get(Recall.HISTORY - 1).got);
        assertNull(Recall.Attempt.fromJson(null));
    }

    @Test
    public void aSkipOrANudgeNeverReplacesAnAttempt() {
        final Recall.Check c = Recall.parseCheck("{\"ideas\": [{\"id\": 1, \"state\": \"remembered\"}]}", five());
        final Recall.Attempt done = Recall.attempt(null, "Ch", 5L, 50, "w", false, 0, c, false);
        assertSame(done, Recall.declined(done, "Ch"));
        assertSame(done, Recall.withIdeas(done, "Ch", five(), "q"));
        assertTrue(done.isDone());

        final Recall.Attempt skipped = Recall.declined(null, "Ch");
        assertEquals(Recall.DECLINED, skipped.state);
        assertFalse(skipped.isDone());
        // a nudge after a skip keeps "not now", and its ideas
        final Recall.Attempt nudged = Recall.withIdeas(skipped, "Ch", five(), "What is it about?");
        assertEquals(Recall.DECLINED, nudged.state);
        assertEquals(5, nudged.ideas.size());
        assertEquals("What is it about?", nudged.question);
        final Recall.Attempt fresh = Recall.withIdeas(null, "Ch", five(), "");
        assertEquals(Recall.IDEAS, fresh.state);
        assertFalse(fresh.isDone());
    }

    @Test
    public void offerOnlyForARealChapterNotSeenYet() {
        assertTrue(Recall.offerable("chapter 1#0", "Chapter 1", false, false));
        assertFalse(Recall.offerable("chapter 1#0", "Chapter 1", false, true));
        assertFalse(Recall.offerable("index#0", "Index", true, false));
        assertFalse(Recall.offerable("@front", "", false, false));
        assertFalse(Recall.offerable("@pages:20", "", false, false));
        assertFalse(Recall.offerable("#0", " ", false, false));
        assertFalse(Recall.offerable(null, "x", false, false));
    }

    @Test
    public void theEndIsReachedByTurningPagesNotByJumping() {
        // a chapter on pages 10..19 (to = 20)
        assertTrue(Recall.reachedEnd(18, 18, 19, 19, 10, 20));
        // two pages at a time: 16-17, then 18-19
        assertTrue(Recall.reachedEnd(16, 17, 18, 19, 10, 20));
        // the last page shares the screen with the next chapter's first
        assertTrue(Recall.reachedEnd(17, 18, 19, 20, 10, 20));
        // not the last page yet
        assertFalse(Recall.reachedEnd(17, 17, 18, 18, 10, 20));
        // a jump from the chapter's start, from another chapter, backwards, or the opening page
        assertFalse(Recall.reachedEnd(10, 10, 19, 19, 10, 20));
        assertFalse(Recall.reachedEnd(5, 5, 19, 19, 10, 20));
        assertFalse(Recall.reachedEnd(20, 20, 19, 19, 10, 20));
        assertFalse(Recall.reachedEnd(-1, -1, 19, 19, 10, 20));
        // a one-page chapter has no page turned inside it
        assertFalse(Recall.reachedEnd(9, 9, 10, 10, 10, 11));
        // already past it
        assertFalse(Recall.reachedEnd(19, 19, 20, 20, 10, 20));
    }

    @Test
    public void promptsCarryTheIdeasOrTheTextAndTheWords() {
        final String withIdeas = Recall.checkUser("The Wealth\nof Nations", "Chapter 1", five(), null, " pins and steps ");
        assertTrue(withIdeas.startsWith("Book: The Wealth of Nations\nChapter: Chapter 1\n\nKey ideas:\n1. Splitting work"));
        assertTrue(withIdeas.contains("5. No time lost switching tasks\n"));
        assertTrue(withIdeas.endsWith("<recall>\npins and steps\n</recall>"));
        assertFalse(withIdeas.contains("<chapter>"));
        final String withText = Recall.checkUser("B", "C", new ArrayList<Recall.Idea>(), CHAPTER, "w");
        assertTrue(withText.contains("<chapter>\nTake a tiny trade"));
        assertFalse(withText.contains("Key ideas:"));

        assertTrue(Recall.checkSystem("", true).contains("\"id\": 1"));
        assertTrue(Recall.checkSystem("", true).contains("the language the text is written in"));
        assertFalse(Recall.checkSystem("", true).contains("\"quote\""));
        assertTrue(Recall.checkSystem("Vietnamese", false).contains("\"quote\""));
        assertTrue(Recall.checkSystem("Vietnamese", false).contains("in Vietnamese"));
        assertTrue(Recall.ideasSystem("").contains("\"ideas\""));
        assertTrue(Recall.ideasUser("B", "C", "text").endsWith("<chapter>\ntext\n</chapter>"));
    }

    @Test
    public void clockAndKey() {
        assertEquals("0:00", Recall.clock(0));
        assertEquals("0:21", Recall.clock(21));
        assertEquals("1:05", Recall.clock(65));
        assertEquals("0:30", Recall.clock(Recall.ABOUT_SECONDS));
        assertEquals("recall:chapter 1#0", Recall.key("chapter 1#0"));
    }
}
