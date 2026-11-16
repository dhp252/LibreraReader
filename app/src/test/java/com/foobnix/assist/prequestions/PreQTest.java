package com.foobnix.assist.prequestions;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PreQTest {

    private static final String ANSWER = "Here they are:\n```json\n["
            + "{\"question\": \"Ten people share the work of making pins. About how many pins a day?\","
            + " \"short\": \"Ten pin-makers: how many pins a day?\","
            + " \"options\": [\"About 200\", \"About 2,000\", \"About 48,000\", \"About 480,000\"],"
            + " \"answer\": 2, \"number\": 48000, \"label\": \"Smith's workshop\","
            + " \"why\": \"About 18 steps, each done by someone who does little else.\","
            + " \"quote\": \"Ten such workers made about 48,000 pins a day.\"},"
            + "{\"question\": \"Why are rich countries better at making things?\","
            + " \"options\": [\"Better machines\", \"Division of labour\", \"Cheap land\", \"Luck\"],"
            + " \"answer\": \"B\", \"number\": null, \"why\": \"Work splits better in manufacture.\","
            + " \"quote\": \"The division of labour cannot be pushed as far in farming as in manufacture.\"},"
            + "{\"question\": \"No quote here\", \"options\": [\"a\", \"b\"], \"answer\": 0},"
            + "{\"question\": \"Made-up quote\", \"options\": [\"a\", \"b\", \"c\", \"d\"], \"answer\": 1,"
            + " \"quote\": \"This sentence is nowhere in the chapter at all.\"},"
            + "{\"question\": \"Name three reasons specialists work faster.\","
            + " \"options\": [\"Practice\", \"Tools\", \"Focus\", \"Pay\", \"All of practice, time saved and machines\"],"
            + " \"answer\": 4, \"why\": \"Smith gives three.\","
            + " \"quote\": \"practice makes each worker faster, nobody loses time switching tasks\"}"
            + "]\n```";

    private static final String CHAPTER = "Take a tiny trade: pin-making. In a workshop the job is split into about "
            + "eighteen steps. Ten such work- ers made about 48,000 pins a day, or 4,800 each. The division of labour "
            + "cannot be pushed as far in farming as in manufacture. Why it works, in three parts: practice makes each "
            + "worker faster, nobody loses time switching tasks, and machines help.";

    private static List<String> words(String text) {
        return Arrays.asList(text.split(" "));
    }

    @Test
    public void parsesATolerantAnswer() {
        final List<PreQ.Question> qs = PreQ.parse(ANSWER);
        // the one without a quote is left out
        assertEquals(4, qs.size());
        assertEquals(2, qs.get(0).answer);
        assertEquals(48000, qs.get(0).number, 0.001);
        assertEquals("Smith's workshop", qs.get(0).label);
        assertEquals(1, qs.get(1).answer);
        assertNull(qs.get(1).number);
        // no short form: the question itself
        assertEquals(qs.get(1).text, qs.get(1).shortText);
        // five options: four kept, the right one among them
        assertEquals(4, qs.get(3).options.size());
        assertEquals(3, qs.get(3).answer);
        assertEquals("All of practice, time saved and machines", qs.get(3).answerText());
    }

    @Test
    public void parsesAnObjectHoldingTheList() {
        final List<PreQ.Question> qs = PreQ.parse("{\"questions\": [{\"question\": \"Q?\", \"options\": [\"x\", \"y\"],"
                + " \"answer\": \"y\", \"quote\": \"some sentence of the book\"}]}");
        assertEquals(1, qs.size());
        assertEquals(1, qs.get(0).answer);
        assertTrue(PreQ.parse("no json at all").isEmpty());
        assertTrue(PreQ.parse(null).isEmpty());
    }

    @Test
    public void keepsOnlyQuotesFoundInTheChapterWithTheirPages() {
        final List<String> w = words(CHAPTER);
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < w.size(); i++) {
            pages.add(i < 20 ? 10 : 11);
        }
        final List<PreQ.Question> kept = PreQ.place(PreQ.parse(ANSWER), w, pages);
        assertEquals(3, kept.size());
        // the book's words, a word cut at a line end put back together
        assertEquals("Ten such workers made about 48,000 pins a day,", kept.get(0).quote);
        assertEquals(10, kept.get(0).page);
        assertEquals(11, kept.get(0).lastPage);
        assertEquals(11, kept.get(1).page);
        assertTrue(kept.get(2).text.startsWith("Name three"));
    }

    @Test
    public void readsNumbers() {
        assertEquals(48000, PreQ.number("About 48,000"), 0.001);
        assertEquals(2500000, PreQ.number("2.5 million"), 0.001);
        assertEquals(480000, PreQ.number("about 480 000 a day"), 0.001);
        assertEquals(3000, PreQ.number("3k"), 0.001);
        assertEquals(2, PreQ.number("2 more"), 0.001);
        assertNull(PreQ.number("Better machines"));
        assertNull(PreQ.number(null));
    }

    @Test
    public void comparesAGuessWithTheAnswer() {
        PreQ.Comparison c = PreQ.compare(2000, 48000);
        assertEquals(PreQ.Comparison.MORE, c.kind);
        assertEquals("24", c.times);
        assertEquals(2000f / 48000f, c.guessShare, 0.0001);
        assertEquals(1f, c.answerShare, 0.0001);
        c = PreQ.compare(480000, 48000);
        assertEquals(PreQ.Comparison.LESS, c.kind);
        assertEquals("10", c.times);
        assertEquals(PreQ.Comparison.RIGHT, PreQ.compare(45000, 48000).kind);
        assertEquals(PreQ.Comparison.CLOSE, PreQ.compare(40000, 48000).kind);
        assertEquals("2.5", PreQ.compare(1000, 2460).times);
        assertEquals("3", PreQ.compare(1000, 3010).times);
        assertEquals("1,234", PreQ.times(1234.4));
    }

    @Test
    public void formatsForASentence() {
        assertEquals("about 2,000", PreQ.inSentence("About 2,000."));
        assertEquals("NASA", PreQ.inSentence("NASA"));
        assertEquals("48,000", PreQ.format(48000));
        assertEquals("2.5", PreQ.format(2.5));
    }

    @Test
    public void keepsItsDataAndRevealsOnceLockedInTheSameLayout() throws Exception {
        final PreQ.Chapter c = new PreQ.Chapter();
        c.title = "Chapter 1";
        c.state = PreQ.READY;
        c.layout = PreQ.layoutKey(false, 1080, 1920, 18);
        final List<String> w = words(CHAPTER);
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < w.size(); i++) {
            pages.add(i < 20 ? 10 : 11);
        }
        c.questions = PreQ.place(PreQ.parse(ANSWER), w, pages);
        c.questions.get(0).guess = 1;
        c.questions.get(0).confidence = PreQ.GUESSING;
        assertEquals(1, c.nextOpen());
        // not locked: nothing to flag, nothing underlined
        assertTrue(c.revealsOn(10, 10, c.layout).isEmpty());
        assertTrue(c.keys().isEmpty());

        c.questions.get(1).own = "machines";
        c.questions.get(2).skipped = true;
        assertEquals(-1, c.nextOpen());
        c.state = PreQ.LOCKED;

        final PreQ.Chapter back = PreQ.Chapter.fromJson(new org.json.JSONObject(c.toJson().toString()));
        assertEquals(3, back.questions.size());
        assertEquals(48000, back.questions.get(0).number, 0.001);
        assertNull(back.questions.get(1).number);
        assertEquals("about 2,000", PreQ.inSentence(back.questions.get(0).guessText()));
        assertEquals(2000, back.questions.get(0).guessNumber(), 0.001);
        assertEquals("machines", back.questions.get(1).guessText());
        assertNull(back.questions.get(2).guessNumber());

        assertEquals(Arrays.asList(0), back.revealsOn(10, 10, back.layout));
        assertEquals(Arrays.asList(0, 1, 2), back.revealsOn(11, 11, back.layout));
        assertTrue(back.revealsOn(11, 11, PreQ.layoutKey(false, 1080, 1920, 22)).isEmpty());
        assertTrue(back.needsPages(PreQ.layoutKey(false, 1080, 1920, 22)));
        assertFalse(back.needsPages(back.layout));
        back.questions.get(0).found = 5;
        assertEquals(Arrays.asList(1, 2), back.revealsOn(10, 11, back.layout));
        assertEquals(1, back.foundCount());
        assertEquals(3, back.keys().size());
    }

    @Test
    public void findsAQuoteOnAPage() {
        assertTrue(PreQ.onPage(words(CHAPTER), "The division of labour cannot be pushed as far in farming"));
        assertFalse(PreQ.onPage(words(CHAPTER), "The division of labour is everything here"));
    }

    @Test
    public void offersRealChaptersOnce() {
        assertTrue(PreQ.offerable("chapter 1#0", "Chapter 1", false, null));
        assertFalse(PreQ.offerable("@front", "", false, null));
        assertFalse(PreQ.offerable("index#0", "Index", true, null));
        final PreQ.Chapter offered = new PreQ.Chapter();
        assertFalse(PreQ.offerable("chapter 1#0", "Chapter 1", false, offered));
        final PreQ.Chapter ready = new PreQ.Chapter();
        ready.state = PreQ.READY;
        ready.questions.add(new PreQ.Question());
        assertTrue(PreQ.offerable("chapter 1#0", "Chapter 1", false, ready));
        ready.state = PreQ.LOCKED;
        assertFalse(PreQ.offerable("chapter 1#0", "Chapter 1", false, ready));
    }

    @Test
    public void asksForJsonWithQuotes() {
        final String system = PreQ.system("");
        assertTrue(system.contains("JSON array"));
        assertTrue(system.contains("\"quote\""));
        assertTrue(system.contains("language the text is written in"));
        assertTrue(PreQ.system("English").contains("in English."));
        assertTrue(PreQ.user("Wealth", "Chapter 1", "text").startsWith("Book: Wealth\nChapter: Chapter 1\n\ntext"));
    }
}
