package com.foobnix.assist.useit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class UseExperimentTest {

    private static UseExperiment pinShop() {
        final UseExperiment e = new UseExperiment();
        e.id = "abc";
        e.idea = "Dividing work into single tasks raises output.";
        e.chapterKey = "ch1";
        e.chapterTitle = "Of the Division of Labour";
        e.bookTitle = "The Wealth of Nations";
        e.author = "Adam Smith";
        e.answer = "Our team rotates every day between labeling, training runs and on-call.";
        e.tryText = "Keep each person on one job for a whole week instead of rotating daily.";
        e.predicts = "Fewer switches, more work done.";
        e.measures.add(new UseExperiment.Measure("Tickets closed a week", "", true));
        e.measures.add(new UseExperiment.Measure("Task switches a day", "", false));
        e.measures.add(new UseExperiment.Measure("Boredom", "1–5", false));
        return e;
    }

    @Test
    public void theCanvasDifferences() {
        final UseExperiment e = pinShop();
        e.measures.get(0).before = 31;
        e.measures.get(0).during = 38;
        e.measures.get(1).before = 9;
        e.measures.get(1).during = 4;
        e.measures.get(2).before = 2;
        e.measures.get(2).during = 3;

        assertEquals("+7", e.measures.get(0).diff().text);
        assertFalse(e.measures.get(0).diff().worse);
        assertEquals("−5", e.measures.get(1).diff().text);
        assertFalse(e.measures.get(1).diff().worse);
        assertEquals("+1", e.measures.get(2).diff().text);
        assertTrue(e.measures.get(2).diff().worse);
        assertEquals("Boredom, 1–5", e.measures.get(2).label());
        assertEquals("Tickets closed a week", e.measures.get(0).label());
        // a scale the name already says is not said twice
        assertEquals("Boredom 1-5", new UseExperiment.Measure("Boredom 1-5", "1–5", false).label());
    }

    @Test
    public void aDropIsWorseOnlyWhenHigherIsBetter() {
        final UseExperiment.Measure m = new UseExperiment.Measure("Pages read", "", true);
        m.before = 40;
        m.during = 25.5;
        assertEquals("−14.5", m.diff().text);
        assertTrue(m.diff().worse);
        m.higherBetter = false;
        assertFalse(m.diff().worse);
    }

    @Test
    public void noChangeAndMissingValues() {
        final UseExperiment.Measure m = new UseExperiment.Measure("Focus", "1-5", true);
        assertNull(m.diff());
        m.before = 3;
        assertNull(m.diff());
        assertFalse(m.hasBoth());
        m.during = 3;
        assertEquals("0", m.diff().text);
        assertTrue(m.diff().same);
        assertFalse(m.diff().worse);
        m.before = 0.1 + 0.2;
        m.during = 0.3;
        assertTrue(m.diff().same);
    }

    @Test
    public void barsAreSharesOfTheLargerValue() {
        final UseExperiment.Measure m = new UseExperiment.Measure("Tickets", "", true);
        m.before = 31;
        m.during = 38;
        assertEquals(1f, m.share(m.during), 0.001f);
        assertEquals(31f / 38f, m.share(m.before), 0.001f);
        m.during = Double.NaN;
        assertEquals(1f, m.share(m.before), 0.001f);
        assertEquals(0f, m.share(m.during), 0.001f);
        m.before = 0;
        assertEquals(0f, m.share(m.before), 0.001f);
    }

    @Test
    public void numbersAsTypedAndShown() {
        assertEquals(31, UseExperiment.parseNumber("31"), 0);
        assertEquals(3.5, UseExperiment.parseNumber(" 3,5 "), 0);
        assertEquals(4.25, UseExperiment.parseNumber("4.25"), 0);
        assertEquals(-2, UseExperiment.parseNumber("−2"), 0);
        assertTrue(Double.isNaN(UseExperiment.parseNumber("")));
        assertTrue(Double.isNaN(UseExperiment.parseNumber(null)));
        assertTrue(Double.isNaN(UseExperiment.parseNumber("many")));
        assertTrue(Double.isNaN(UseExperiment.parseNumber("1.2.3")));
        assertEquals("31", UseExperiment.number(31));
        assertEquals("3.5", UseExperiment.number(3.5));
        assertEquals("4.25", UseExperiment.number(4.251));
        assertEquals("-2", UseExperiment.number(-2));
        assertEquals("", UseExperiment.number(Double.NaN));
    }

    @Test
    public void runningThenDueThenDone() {
        final UseExperiment e = pinShop();
        e.start("2026-10-26", 2);
        assertEquals("2026-11-09", e.checkDay);
        assertEquals(UseExperiment.RUNNING, e.stateOn("2026-10-26"));
        assertEquals(UseExperiment.RUNNING, e.stateOn("2026-11-08"));
        assertEquals(1, e.daysLeft("2026-11-08"));
        assertEquals(UseExperiment.DUE, e.stateOn("2026-11-09"));
        assertTrue(e.isDue("2026-12-24"));
        e.finish("2026-11-10", UseExperiment.MOSTLY, "  On-call weeks were draining. ");
        assertEquals(UseExperiment.DONE, e.stateOn("2026-11-09"));
        assertEquals(UseExperiment.DONE, e.stateOn("2026-10-01"));
        assertFalse(e.isDue("2026-12-24"));
        assertEquals("On-call weeks were draining.", e.surprise);
        assertEquals("2026-11-10", e.doneDay);
        // saved again later: the day of the first check-in stays
        e.finish("2026-11-20", UseExperiment.HELD, "");
        assertEquals("2026-11-10", e.doneDay);
        assertEquals(UseExperiment.HELD, e.verdict);
    }

    @Test
    public void lengthsAreOneTwoOrFourWeeks() {
        assertEquals("2026-11-02", UseExperiment.checkDay("2026-10-26", 1));
        assertEquals("2026-11-23", UseExperiment.checkDay("2026-10-26", 4));
        // anything else is the default 2
        assertEquals("2026-11-09", UseExperiment.checkDay("2026-10-26", 3));
        assertEquals(2, UseExperiment.weeks(0));
        final UseExperiment e = pinShop();
        e.start("2026-10-26", 9);
        assertEquals(2, e.weeks);
    }

    @Test
    public void aBrokenCheckInDayIsNeverDue() {
        final UseExperiment e = pinShop();
        e.checkDay = "";
        assertEquals(UseExperiment.RUNNING, e.stateOn("2026-11-09"));
        e.checkDay = "2026-11-09";
        assertEquals(UseExperiment.RUNNING, e.stateOn("not a day"));
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final UseExperiment e = pinShop();
        e.warning = "One task for too long dulls people.";
        e.quote = "becomes as stupid and ignorant as it is possible for a human creature to become";
        e.quoted = true;
        e.start("2026-10-26", 4);
        e.measures.get(0).before = 31;
        e.measures.get(2).before = 2;
        e.measures.get(2).during = 3;

        final JSONObject stored = e.toStore("2026-11-30");
        assertEquals(UseExperiment.DUE, stored.getString("state"));
        assertEquals("running", e.toStore("2026-10-27").getString("state"));

        final UseExperiment back = UseExperiment.fromJson(new JSONObject(stored.toString()));
        assertEquals("abc", back.id);
        assertEquals(e.tryText, back.tryText);
        assertEquals(e.predicts, back.predicts);
        assertEquals(e.idea, back.idea);
        assertEquals("ch1", back.chapterKey);
        assertEquals("Of the Division of Labour", back.chapterTitle);
        assertEquals(e.answer, back.answer);
        assertEquals(4, back.weeks);
        assertEquals("2026-10-26", back.startDay);
        assertEquals("2026-11-23", back.checkDay);
        assertTrue(back.quoted);
        assertEquals(e.quote, back.quote);
        assertEquals(3, back.measures.size());
        assertEquals(31, back.measures.get(0).before, 0);
        assertTrue(Double.isNaN(back.measures.get(0).during));
        assertTrue(Double.isNaN(back.measures.get(1).before));
        assertFalse(back.measures.get(1).higherBetter);
        assertEquals("1–5", back.measures.get(2).scale);
        assertEquals("+1", back.measures.get(2).diff().text);
        // "due" is worked out from the day, never trusted from the file
        assertEquals(UseExperiment.RUNNING, back.stateOn("2026-11-01"));
        assertEquals(UseExperiment.DUE, back.stateOn("2026-11-23"));

        back.finish("2026-11-24", UseExperiment.DIDNT, "It did not");
        final UseExperiment done = UseExperiment.fromJson(back.toStore("2026-11-24"));
        assertEquals(UseExperiment.DONE, done.stateOn("2026-11-01"));
        assertEquals(UseExperiment.DIDNT, done.verdict);
        assertEquals("It did not", done.surprise);
    }

    @Test
    public void whatIsNotAnExperiment() throws Exception {
        assertNull(UseExperiment.fromJson(null));
        assertNull(UseExperiment.fromJson(new JSONObject()));
        assertNull(UseExperiment.fromJson(new JSONObject().put("id", "x")));
        assertNull(UseExperiment.fromJson(new JSONObject().put("try", "Do it")));
        final UseExperiment e = UseExperiment.fromJson(new JSONObject().put("id", "x").put("try", "Do it")
                .put("verdict", "great").put("weeks", 7));
        assertEquals("", e.verdict);
        assertEquals(2, e.weeks);
        assertTrue(e.measures.isEmpty());
    }

    @Test
    public void theFamilyNameOfTheAuthor() {
        assertEquals("Smith", UseExperiment.shortAuthor("Adam Smith"));
        assertEquals("Smith", UseExperiment.shortAuthor("Smith, Adam"));
        assertEquals("Kahneman", UseExperiment.shortAuthor("Daniel Kahneman and Amos Tversky"));
        assertEquals("Heath", UseExperiment.shortAuthor("Chip Heath & Dan Heath"));
        assertEquals("Smith", UseExperiment.shortAuthor(" Adam Smith; David Hume "));
        assertEquals("Plato", UseExperiment.shortAuthor("Plato"));
        assertEquals("", UseExperiment.shortAuthor(""));
        assertEquals("", UseExperiment.shortAuthor(null));
    }

    @Test
    public void idsDifferAndKeepTheirOrder() {
        final String a = UseExperiment.newId(1_800_000_000_000L, 5);
        final String b = UseExperiment.newId(1_800_000_000_001L, 5);
        final String c = UseExperiment.newId(1_800_000_000_000L, 6);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertTrue(a.compareTo(b) < 0);
        assertEquals(a.length(), UseExperiment.newId(1_800_000_000_000L, -77).length());
        assertEquals("use:" + a, UseExperiment.key(a));
    }

    @Test
    public void canStartOnlyWithSomethingToTry() {
        final UseExperiment e = pinShop();
        assertTrue(e.isComplete());
        e.measures.add(new UseExperiment.Measure());
        assertFalse(e.isComplete());
        e.measures.clear();
        assertTrue(e.isComplete());
        e.tryText = "  ";
        assertFalse(e.isComplete());
    }
}
