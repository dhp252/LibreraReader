package com.foobnix.assist.teach;

import com.foobnix.assist.recall.Recall;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TeachTest {

    private static final String SAUNTER = "A man commonly saunters a little in turning his hand from one sort of "
            + "employment to another. When he first begins the new work he is seldom very keen and hearty.";

    private static List<Recall.Idea> ideas() {
        final List<Recall.Idea> ideas = new ArrayList<>();
        ideas.add(new Recall.Idea(11, "Splitting work multiplies output")
                .add("The division of labour occasions a proportionable increase of the productive powers of labour.", 3));
        ideas.add(new Recall.Idea(12, "Ten workers, about 48,000 pins a day"));
        ideas.add(new Recall.Idea(19, "No time lost switching tasks")
                .add("The advantage gained by saving the time commonly lost in passing from one sort of work to another.", 6)
                .add(SAUNTER, 7));
        return ideas;
    }

    @Test
    public void readsATurn() {
        final Teach.Reply r = Teach.parse("```json\n{\"explained\": [1, \"3\", \"x\"], \"gap\": {\"idea\": 3, "
                + "\"quote\": \"a man commonly saunters a little\", \"say\": \"Smith says it still costs plenty.\"}, "
                + "\"reply\": \" Oh, so it's warm-up time. Got an example? \", \"done\": false}\n```");
        assertNotNull(r);
        assertEquals(2, r.explained.size());
        assertEquals(3, (int) r.explained.get(1));
        assertEquals(3, r.gap.idea);
        assertEquals("Oh, so it's warm-up time. Got an example?", r.reply);
        assertFalse(r.done);
    }

    @Test
    public void readsATurnWithoutAGapAndAPlainSentence() {
        final Teach.Reply r = Teach.parse("{\"explained\": [], \"gap\": null, \"reply\": \"Why pins?\", \"done\": true}");
        assertNull(r.gap);
        assertTrue(r.done);
        // a gap without its quote is no gap
        assertNull(Teach.parse("{\"gap\": {\"idea\": 1, \"say\": \"x\"}, \"reply\": \"ok?\"}").gap);
        // no JSON at all: the beginner's line
        assertEquals("But why would that help?", Teach.parse(" But why would that help? ").reply);
        assertNull(Teach.parse(""));
        assertNull(Teach.parse(null));
        assertNull(Teach.parse("{\"explained\": [1]}"));
        assertNull(Teach.parse("{ broken"));
    }

    @Test
    public void aGapIsShownOnlyWithTheBooksWords() {
        final Teach.Gap gap = new Teach.Gap();
        gap.idea = 1;
        gap.say = "Smith says it still costs plenty.";
        // the model changed the case and named the wrong idea: found in idea 3's second passage
        gap.quote = "a man commonly saunters a little in turning his hand";
        final Teach.Gap found = Teach.verify(gap, ideas());
        assertNotNull(found);
        assertEquals(2, found.idea);
        assertEquals("A man commonly saunters a little in turning his hand", found.quote);
        assertEquals(7, found.page);
        assertEquals("Smith says it still costs plenty.", found.say);

        gap.quote = "workers lose a quarter of every day to switching";
        assertNull(Teach.verify(gap, ideas()));
        // too short to be told from chance
        gap.quote = "saunters";
        assertNull(Teach.verify(gap, ideas()));
        assertNull(Teach.verify(null, ideas()));
    }

    @Test
    public void aSessionCountsIdeasAndGapsAndEnds() throws Exception {
        final Teach.Session s = new Teach.Session(ideas(), "Why would switching tasks waste time?");
        assertEquals(1, s.lines.size());
        s.answer("Every switch means putting one tool down.", true);
        assertFalse(s.isLast());

        final Teach.Reply r = Teach.parse("{\"explained\": [1, 9], \"gap\": {\"idea\": 3, \"quote\": "
                + "\"he is seldom very keen and hearty\", \"say\": \"It still costs.\"}, \"reply\": \"Does it?\"}");
        final Teach.Gap shown = s.apply(r);
        assertNotNull(shown);
        assertEquals(1, s.explainedCount());
        assertTrue(s.isExplained(0));
        assertFalse(s.isExplained(2));
        assertEquals(1, s.gaps.size());
        // beginner, reader, gap, beginner
        assertEquals(4, s.lines.size());
        assertEquals(Teach.Line.GAP, s.lines.get(2).kind);
        assertEquals("he is seldom very keen and hearty.", s.lines.get(2).gap.quote);
        assertTrue(s.lines.get(1).spoken);
        assertFalse(s.finished);

        // a made-up quote: no gap card, the reply still comes; explained ideas stay explained
        s.answer("Less, I guess.", false);
        final Teach.Reply r2 = Teach.parse("{\"explained\": [3], \"gap\": {\"idea\": 2, \"quote\": "
                + "\"this sentence is not in any passage of the book\", \"say\": \"No.\"}, \"reply\": \"Hm?\"}");
        assertNull(s.apply(r2));
        assertEquals(1, s.gaps.size());
        assertEquals(2, s.explainedCount());
        assertEquals(6, s.lines.size());

        for (int i = s.answers; i < Teach.MAX_TURNS; i++) {
            assertFalse(s.finished);
            s.answer("answer " + i, false);
            s.apply(Teach.parse("{\"reply\": \"And then?\"}"));
        }
        assertTrue(s.isLast());
        assertTrue(s.finished);

        final JSONObject sum = s.summary("Chapter 1", 77L);
        assertEquals("Chapter 1", sum.getString("title"));
        assertEquals(77L, sum.getLong("date"));
        assertTrue(sum.getBoolean("finished"));
        assertEquals(Teach.MAX_TURNS, sum.getInt("answers"));
        assertEquals(3, sum.getInt("total"));
        assertEquals(2, sum.getJSONArray("explained").length());
        assertEquals("Ten workers, about 48,000 pins a day", sum.getJSONArray("missing").getString(0));
        assertEquals(1, sum.getJSONArray("gaps").length());
        assertEquals("No time lost switching tasks", sum.getJSONArray("gaps").getJSONObject(0).getString("idea"));
        assertEquals(7, sum.getJSONArray("gaps").getJSONObject(0).getInt("page"));
        // the chat is not kept
        assertFalse(sum.toString().contains("Less, I guess."));
    }

    @Test
    public void doneEndsEarly() {
        final Teach.Session s = new Teach.Session(ideas(), "What is it about?");
        s.answer("All of it.", false);
        s.apply(Teach.parse("{\"explained\": [1, 2, 3], \"reply\": \"Thanks, I get it now.\", \"done\": true}"));
        assertTrue(s.finished);
        assertEquals(3, s.explainedCount());
    }

    @Test
    public void theTurnCarriesIdeasPassagesChatAndTheLastAnswer() {
        final Teach.Session s = new Teach.Session(ideas(), "Why would switching\ntasks waste time?");
        s.answer("Every switch means putting one tool down.", false);
        s.apply(Teach.parse("{\"gap\": {\"idea\": 3, \"quote\": \"he is seldom very keen and hearty\", "
                + "\"say\": \"It still costs.\"}, \"reply\": \"Does it?\"}"));
        s.answer("Hmm, less I guess.", true);
        final String user = Teach.user("The Wealth of Nations", "Chapter 1", s);
        assertTrue(user.startsWith("Book: The Wealth of Nations\nChapter: Chapter 1\n"));
        assertTrue(user.contains("1. Splitting work multiplies output\n   passage: The division of labour"));
        assertTrue(user.contains("2. Ten workers, about 48,000 pins a day\n3. No time lost"));
        assertTrue(user.contains("Beginner: Why would switching tasks waste time?\nReader: Every switch means putting one "
                + "tool down.\n(Gap shown to the reader: It still costs.)\nBeginner: Does it?\n"));
        // the latest answer is given once, in its own block
        assertTrue(user.endsWith("(answer 2 of at most 8):\n<answer>\nHmm, less I guess.\n</answer>"));
        assertEquals(user.indexOf("Hmm, less I guess."), user.lastIndexOf("Hmm, less I guess."));

        assertTrue(Teach.system("", false).contains("exactly one follow-up question"));
        assertTrue(Teach.system("", true).contains("last answer"));
        assertFalse(Teach.system("", true).contains("exactly one follow-up question"));
        assertTrue(Teach.system("German", false).contains("in German"));
    }

    @Test
    public void longPassagesAreCutAtAWord() {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            b.append("word").append(i).append(' ');
        }
        final String cut = Teach.cut(b.toString().trim());
        assertTrue(cut.length() <= Teach.PASSAGE_CHARS);
        assertFalse(cut.endsWith(" "));
        assertTrue(b.toString().startsWith(cut + " "));
        assertEquals("short", Teach.cut("short"));
        assertEquals("teach:chapter 1#0", Teach.key("chapter 1#0"));
    }
}
