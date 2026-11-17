package com.foobnix.assist.path;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PathDataTest {

    /** 6000 characters = 1000 words; at 250 words a minute, 4 minutes. */
    private static final int WPM = 250;

    private static PathPrompts.Question question(int right) {
        return new PathPrompts.Question("Q?", Arrays.asList("a", "b", "c", "d"), right);
    }

    private static PathData withQuestions() {
        final PathData d = PathFixture.path();
        d.chapter(PathFixture.LABOUR).question = question(0);
        d.chapter(PathFixture.PRINCIPLE).question = question(1);
        d.chapter(PathFixture.PRICE).question = question(2);
        return d;
    }

    @Test
    public void theAnswerBecomesThePath() {
        final PathData d = PathFixture.path();
        assertEquals("the invisible hand", d.goal);
        assertEquals(4, d.chapters.size());
        assertEquals(PathFixture.LABOUR, d.chapters.get(0).key);
        assertEquals("Chapter I: Of the Division of Labour", d.chapters.get(0).title);
        assertEquals("I.1", d.chapters.get(0).code);
        assertEquals("Why people specialize at all", d.chapters.get(0).why);
        assertEquals("Splitting work multiplies output", d.chapters.get(0).line);
        assertEquals(PathFixture.HAND, d.goalKey);
        assertTrue(d.isGoal(d.chapters.get(3)));
        assertEquals(7, d.total);
        assertEquals(3, d.skippedCount());
        assertEquals("stock, governments", d.skipped);
        assertEquals(7L, d.made);
    }

    @Test
    public void unknownChaptersAreDroppedAndTheGoalGoesLast() {
        final PathPrompts.Answer a = new PathPrompts.Answer();
        a.steps.add(new PathPrompts.Step(5, "", "the goal, named first"));
        a.steps.add(new PathPrompts.Step(99, "", "no such chapter"));
        a.steps.add(new PathPrompts.Step(-1, "no such key#0", "no such key"));
        a.steps.add(new PathPrompts.Step(1, "", "needed"));
        a.steps.add(new PathPrompts.Step(1, "", "twice"));
        a.steps.add(new PathPrompts.Step(-1, PathFixture.PRICE, "by key"));
        a.goal = 5;
        final PathData d = PathData.fromAnswer("x", a, PathFixture.list(), "", 0);
        assertEquals(3, d.chapters.size());
        assertEquals(PathFixture.LABOUR, d.chapters.get(0).key);
        assertEquals("needed", d.chapters.get(0).why);
        assertEquals(PathFixture.PRICE, d.chapters.get(1).key);
        assertEquals(PathFixture.HAND, d.chapters.get(2).key);
        assertEquals(PathFixture.HAND, d.goalKey);
    }

    @Test
    public void withoutANamedGoalTheLastChapterIsTheGoal() {
        final PathPrompts.Answer a = new PathPrompts.Answer();
        a.steps.add(new PathPrompts.Step(1, "", ""));
        a.steps.add(new PathPrompts.Step(3, "", ""));
        a.goal = 6;
        final PathData d = PathData.fromAnswer("x", a, PathFixture.list(), "", 0);
        assertEquals(PathFixture.PRICE, d.goalKey);
        assertTrue(PathData.fromAnswer("x", new PathPrompts.Answer(), PathFixture.list(), "", 0).isEmpty());
    }

    @Test
    public void aPathIsNeverLongerThanTheMost() {
        final PathPrompts.Answer a = new PathPrompts.Answer();
        final List<PathChapters.Entry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(new PathChapters.Entry("k" + i, "T" + i, "", "line", 600, i, i + 1));
            a.steps.add(new PathPrompts.Step(i, "", ""));
        }
        assertEquals(PathPrompts.MAX_STEPS, PathData.fromAnswer("x", a, many, "", 0).chapters.size());
    }

    @Test
    public void timesOfThePathAndTheBook() {
        final PathData d = PathFixture.path();
        // three chapters of 1000 words and the goal of 2000
        assertEquals(4 + 4 + 4 + 8, d.minutes(WPM));
        // six chapters of 1000 words and one of 2000
        assertEquals(6 * 4 + 8, d.bookMinutes(WPM));
        assertEquals(20f / 32f, d.share(WPM), 0.001f);
        assertEquals(0, PathData.minutes(0, WPM));
        assertTrue("the default speed when the reader's is not known", d.minutes(0) > 0);
        assertEquals(0f, new PathData().share(WPM), 0f);
    }

    @Test
    public void aRightAnswerTakesTheChapterOffAndUndoPutsItBack() {
        final PathData d = withQuestions();
        assertEquals(3, d.toCheck().size());
        assertTrue(d.canCheck());

        assertTrue(d.answer(PathFixture.PRINCIPLE, 1));
        assertEquals(3, d.active().size());
        assertEquals(1, d.known().size());
        assertEquals(4, d.skippedCount());
        assertEquals(4 + 4 + 8, d.minutes(WPM));
        assertEquals(2, d.toCheck().size());

        assertTrue(d.putBack(PathFixture.PRINCIPLE));
        assertFalse("only once", d.putBack(PathFixture.PRINCIPLE));
        assertEquals(4, d.active().size());
        assertEquals(20, d.minutes(WPM));
        assertEquals("put back, it is not asked again", 2, d.toCheck().size());
    }

    @Test
    public void aWrongAnswerOrNotSureKeepsTheChapter() {
        final PathData d = withQuestions();
        assertFalse(d.answer(PathFixture.LABOUR, 3));
        assertFalse(d.answer(PathFixture.PRICE, PathData.NOT_SURE));
        assertEquals(4, d.active().size());
        assertEquals(1, d.toCheck().size());
        assertEquals(3, d.chapter(PathFixture.LABOUR).answer);
        assertEquals(PathData.NOT_SURE, d.chapter(PathFixture.PRICE).answer);
    }

    @Test
    public void theGoalChapterIsNeverCheckedOrTakenOff() {
        final PathData d = withQuestions();
        d.chapter(PathFixture.HAND).question = question(0);
        assertFalse(d.answer(PathFixture.HAND, 0));
        assertFalse(d.chapter(PathFixture.HAND).known);
        assertFalse(d.answer("no such chapter", 0));
        for (PathData.Chapter c : d.toCheck()) {
            assertFalse(d.isGoal(c));
        }
    }

    @Test
    public void aChapterReadOrWithoutAQuestionIsNotTakenOff() {
        final PathData d = PathFixture.path();
        assertFalse("no question yet", d.answer(PathFixture.LABOUR, 0));
        d.chapter(PathFixture.PRINCIPLE).done = true;
        assertEquals(1, d.toCheck().size());
        d.resetCheck();
        assertEquals(2, d.toCheck().size());
    }

    @Test
    public void firstUnreadAndNext() {
        final PathData d = withQuestions();
        assertEquals(PathFixture.LABOUR, d.firstUnread().key);
        d.chapter(PathFixture.LABOUR).done = true;
        d.answer(PathFixture.PRINCIPLE, 1);
        assertEquals("the one taken off is passed over", PathFixture.PRICE, d.firstUnread().key);
        assertEquals(PathFixture.PRICE, d.nextAfter(PathFixture.LABOUR).key);
        assertEquals(PathFixture.HAND, d.nextAfter(PathFixture.PRICE).key);
        assertFalse(d.allDone());

        // the goal read before the chapter in between: that one is next
        d.chapter(PathFixture.HAND).done = true;
        assertEquals(PathFixture.PRICE, d.nextAfter(PathFixture.HAND).key);
        d.chapter(PathFixture.PRICE).done = true;
        assertNull(d.nextAfter(PathFixture.HAND));
        assertTrue(d.allDone());
        assertEquals("all read: the goal chapter again", PathFixture.HAND, d.firstUnread().key);
        assertNull(new PathData().firstUnread());
        assertFalse(new PathData().allDone());
    }

    @Test
    public void aPageTurnOutOfAChapterFinishesIt() {
        final PathData d = PathFixture.path();
        final Map<String, int[]> ranges = PathChapters.ranges(PathFixture.parts());
        // Labour is pages 10-19
        assertNull("inside the chapter", d.finishedBy(ranges, 17, 17, 18, 18, PathFixture.PAGES));
        assertEquals(PathFixture.LABOUR, d.finishedBy(ranges, 19, 19, 20, 20, PathFixture.PAGES).key);
        assertNull("backwards", d.finishedBy(ranges, 20, 20, 19, 19, PathFixture.PAGES));
        assertNull("a jump from the middle", d.finishedBy(ranges, 12, 12, 20, 20, PathFixture.PAGES));
        assertNull("a jump far away", d.finishedBy(ranges, 19, 19, 50, 50, PathFixture.PAGES));
        assertNull("the first page shown", d.finishedBy(ranges, -1, -1, 20, 20, PathFixture.PAGES));
        assertNull("no layout", d.finishedBy(null, 19, 19, 20, 20, PathFixture.PAGES));
        // two pages at once: 18-19 then 20-21, or 19-20 then 21-22
        assertEquals(PathFixture.LABOUR, d.finishedBy(ranges, 18, 19, 20, 21, PathFixture.PAGES).key);
        assertEquals(PathFixture.LABOUR, d.finishedBy(ranges, 19, 20, 21, 22, PathFixture.PAGES).key);
        // a chapter that is not on the path (Stock, pages 40-49)
        assertNull(d.finishedBy(ranges, 49, 49, 50, 50, PathFixture.PAGES));

        d.chapter(PathFixture.LABOUR).done = true;
        assertNull("already read", d.finishedBy(ranges, 19, 19, 20, 20, PathFixture.PAGES));
    }

    @Test
    public void theLastChapterOfTheBookIsFinishedOnItsLastPage() {
        final PathPrompts.Answer a = new PathPrompts.Answer();
        a.steps.add(new PathPrompts.Step(0, "", ""));
        final List<PathChapters.Entry> one = Arrays.asList(new PathChapters.Entry("last#0", "Last", "", "line", 600, 70, 80));
        final PathData d = PathData.fromAnswer("x", a, one, "", 0);
        final Map<String, int[]> ranges = new java.util.HashMap<>();
        ranges.put("last#0", new int[]{70, 80});
        assertNull(d.finishedBy(ranges, 77, 77, 78, 78, 80));
        assertEquals("last#0", d.finishedBy(ranges, 78, 78, 79, 79, 80).key);
        assertNull("a jump to the end", d.finishedBy(ranges, 72, 72, 79, 79, 80));
    }

    @Test
    public void theChapterBeingRead() {
        final PathData d = withQuestions();
        final Map<String, int[]> ranges = PathChapters.ranges(PathFixture.parts());
        assertEquals(PathFixture.LABOUR, d.at(ranges, 10).key);
        assertEquals(PathFixture.HAND, d.at(ranges, 59).key);
        assertNull("not on the path", d.at(ranges, 45));
        d.answer(PathFixture.LABOUR, 0);
        assertNull("taken off", d.at(ranges, 10));
        assertNull(d.at(null, 10));
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final PathData d = withQuestions();
        d.chapter(PathFixture.LABOUR).done = true;
        d.answer(PathFixture.PRINCIPLE, 1);
        d.answer(PathFixture.PRICE, PathData.NOT_SURE);

        final PathData back = PathData.fromJson(new JSONObject(d.toJson().toString()));
        assertEquals("the invisible hand", back.goal);
        assertEquals(4, back.chapters.size());
        assertEquals(PathFixture.HAND, back.goalKey);
        assertEquals("stock, governments", back.skipped);
        assertEquals(7, back.total);
        assertEquals(d.bookMinutes(WPM), back.bookMinutes(WPM));
        assertEquals("model", back.model);
        assertEquals(7L, back.made);

        final PathData.Chapter labour = back.chapter(PathFixture.LABOUR);
        assertTrue(labour.done);
        assertEquals("I.1", labour.code);
        assertEquals("Chapter I: Of the Division of Labour", labour.title);
        assertEquals(6000, labour.chars);
        assertEquals(0, labour.question.right);
        final PathData.Chapter principle = back.chapter(PathFixture.PRINCIPLE);
        assertTrue(principle.known);
        assertEquals(1, principle.answer);
        assertEquals(PathData.NOT_SURE, back.chapter(PathFixture.PRICE).answer);
        assertEquals(PathData.NOT_ASKED, back.chapter(PathFixture.HAND).answer);
        assertNull(back.chapter(PathFixture.HAND).question);
        assertEquals(3, back.active().size());
    }

    @Test
    public void anEmptyOrBrokenItemIsNoPath() throws Exception {
        assertTrue(PathData.fromJson(null).isEmpty());
        assertTrue(PathData.fromJson(new JSONObject("{\"goal\": \"x\"}")).isEmpty());
        // a goal key that is not on the path falls back to the last chapter; a chapter without a key is left out
        final PathData d = PathData.fromJson(new JSONObject("{\"goal\": \"x\", \"goalKey\": \"gone\", \"chapters\": "
                + "[{\"key\": \"a\"}, {\"title\": \"no key\"}, {\"key\": \"b\"}, {\"key\": \"a\"}]}"));
        assertEquals(2, d.chapters.size());
        assertEquals("b", d.goalKey);
        assertEquals(2, d.total);
        assertSame(d.chapters.get(0), d.chapter("a"));
    }
}
