package com.foobnix.assist.path;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PathPromptsTest {

    @Test
    public void systemAsksForThePathAsJson() {
        final String s = PathPrompts.system("");
        assertTrue(s.contains("\"path\""));
        assertTrue(s.contains("\"goal\""));
        assertTrue(s.contains("\"skipped\""));
        assertTrue(s.contains("the language of the chapter lines"));
        assertTrue(PathPrompts.system(" Vietnamese ").contains("Write in Vietnamese."));
    }

    @Test
    public void userListsEveryChapterWithItsNumberAndLine() {
        final String u = PathPrompts.user("The Wealth of Nations", "  the invisible\nhand ", PathFixture.list(), 600_000);
        assertTrue(u.startsWith("Book: The Wealth of Nations"));
        assertTrue(u.contains("I want to understand the invisible hand\n"));
        assertTrue(u.contains("<chapter n=\"0\" title=\"Introduction\" words=\"1000\">What the book sets out to show</chapter>"));
        assertTrue(u.contains("<chapter n=\"3\" part=\"Book I\" title=\"Chapter VII: Of the Natural and Market Price\" "
                + "words=\"1000\">Prices pull supply towards demand</chapter>"));
        assertTrue(u.contains("<chapter n=\"6\""));
        assertFalse(u.contains("<chapter n=\"7\""));
    }

    @Test
    public void userShortensTheLinesToFit() {
        final List<PathChapters.Entry> many = new ArrayList<>();
        final StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longLine.append("word ");
        }
        for (int i = 0; i < 60; i++) {
            many.add(new PathChapters.Entry("k" + i, "Chapter " + i, "", longLine.toString(), 6000, i, i + 1));
        }
        final String full = PathPrompts.user("B", "goal", many, 600_000);
        final String cut = PathPrompts.user("B", "goal", many, 12_000);
        assertTrue(full.length() > 30_000);
        assertTrue("fits the request: " + cut.length(), cut.length() <= 12_000);
        assertTrue(cut.contains("<chapter n=\"59\""));
        assertTrue(cut.contains("…</chapter>"));
    }

    @Test
    public void parsesAWholeAnswer() {
        final PathPrompts.Answer a = PathPrompts.parse("```json\n{\"path\": [{\"n\": 1, \"why\": \"Why people specialize\"},"
                + " {\"n\": \"3\", \"why\": \"How prices steer effort\"}, {\"n\": 5, \"why\": \"The passage\"}],"
                + " \"goal\": 5, \"skipped\": \"stock, governments\"}\n```");
        assertTrue(a.read);
        assertEquals(3, a.steps.size());
        assertEquals(1, a.steps.get(0).n);
        assertEquals("Why people specialize", a.steps.get(0).why);
        assertEquals(3, a.steps.get(1).n);
        assertEquals(5, a.goal);
        assertEquals("stock, governments", a.skipped);
    }

    @Test
    public void parsesKeysAndAListOfSkippedWords() {
        final PathPrompts.Answer a = PathPrompts.parse("{\"path\": [{\"key\": \"conclusion#0\", \"why\": \"x\"}, 2, {\"why\": \"no chapter\"}],"
                + " \"goal\": \"conclusion#0\", \"skipped\": [\"wages\", \"rent\"]}");
        assertEquals(2, a.steps.size());
        assertEquals(-1, a.steps.get(0).n);
        assertEquals("conclusion#0", a.steps.get(0).key);
        assertEquals(2, a.steps.get(1).n);
        assertEquals("conclusion#0", a.goalKey);
        assertEquals("wages, rent", a.skipped);
    }

    @Test
    public void anEmptyPathIsReadAsNoChapter() {
        final PathPrompts.Answer a = PathPrompts.parse("{\"path\": [], \"goal\": -1, \"skipped\": \"\"}");
        assertTrue(a.read);
        assertTrue(a.steps.isEmpty());
        assertEquals(-1, a.goal);
    }

    @Test
    public void readsWhatACutOffAnswerHolds() {
        final PathPrompts.Answer a = PathPrompts.parse("{\"goal\": 5, \"skipped\": \"wages, \\\"rent\\\"\", \"path\": "
                + "[{\"n\": 1, \"why\": \"a {brace}\"}, {\"n\": 5, \"why\": \"the pas");
        assertFalse(a.read);
        assertEquals(1, a.steps.size());
        assertEquals("a {brace}", a.steps.get(0).why);
        assertEquals(5, a.goal);
        assertEquals("wages, \"rent\"", a.skipped);
    }

    @Test
    public void nothingFromProse() {
        final PathPrompts.Answer a = PathPrompts.parse("I cannot help with that.");
        assertFalse(a.read);
        assertTrue(a.steps.isEmpty());
        assertTrue(PathPrompts.parse(null).steps.isEmpty());
    }

    // ------------------------------------------------------------------ the check

    @Test
    public void checkListsThePathChaptersWithoutTheGoal() {
        final PathData d = PathFixture.path();
        final String u = PathPrompts.checkUser("The Wealth of Nations", d.goal, d.toCheck());
        assertTrue(u.contains("The idea the reader is after: the invisible hand"));
        assertTrue(u.contains("<chapter n=\"0\" title=\"Chapter I: Of the Division of Labour\">Splitting work multiplies output"
                + " (needed: Why people specialize at all)</chapter>"));
        assertTrue(u.contains("<chapter n=\"2\""));
        assertFalse("the goal chapter is never checked", u.contains("The Invisible Hand"));
        assertTrue(PathPrompts.checkSystem("").contains("\"questions\""));
    }

    @Test
    public void parsesTheQuestions() {
        final Map<Integer, PathPrompts.Question> q = PathPrompts.parseCheck("{\"questions\": ["
                + "{\"n\": 0, \"q\": \"A frost ruins the crop. What brings prices down?\", \"options\": [\"A cap\", \"More supply\","
                + " \"A boycott\", \"Nothing\"], \"right\": 1},"
                + "{\"n\": 1, \"q\": \"By letter\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"right\": \"C\"},"
                + "{\"n\": 2, \"q\": \"By words\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"right\": \"d\"},"
                + "{\"n\": 3, \"q\": \"Three options\", \"options\": [\"a\", \"b\", \"c\"], \"right\": 0},"
                + "{\"n\": 4, \"q\": \"No right one\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"right\": 4},"
                + "{\"n\": 5, \"q\": \"\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"right\": 0}]}");
        assertEquals(3, q.size());
        assertEquals("A frost ruins the crop. What brings prices down?", q.get(0).text);
        assertEquals(4, q.get(0).options.size());
        assertEquals(1, q.get(0).right);
        assertEquals(2, q.get(1).right);
        assertEquals(3, q.get(2).right);
        assertNull(q.get(3));
    }

    @Test
    public void readsTheWholeQuestionsOfACutOffAnswer() {
        final Map<Integer, PathPrompts.Question> q = PathPrompts.parseCheck("Here you are: {\"questions\": ["
                + "{\"n\": 0, \"q\": \"One?\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"right\": 3},"
                + "{\"n\": 1, \"q\": \"Two?\", \"options\": [\"a\", \"b\"");
        assertEquals(1, q.size());
        assertEquals(3, q.get(0).right);
        assertTrue(PathPrompts.parseCheck("no json").isEmpty());
        assertTrue(PathPrompts.parseCheck(null).isEmpty());
    }

    @Test
    public void reorderingKeepsTheRightOption() throws Exception {
        final PathPrompts.Question q = PathPrompts.parseCheck("{\"questions\": [{\"n\": 0, \"q\": \"Q\", \"options\": "
                + "[\"a\", \"b\", \"c\", \"d\"], \"right\": 1}]}").get(0);
        final PathPrompts.Question moved = q.reordered(new int[]{3, 2, 1, 0});
        assertEquals("d", moved.options.get(0));
        assertEquals(2, moved.right);
        assertEquals("b", moved.options.get(moved.right));

        final PathPrompts.Question back = PathPrompts.Question.fromJson(new org.json.JSONObject(moved.toJson().toString()));
        assertNotNull(back);
        assertEquals(moved.options, back.options);
        assertEquals(2, back.right);
        assertNull(PathPrompts.Question.fromJson(null));
    }
}
