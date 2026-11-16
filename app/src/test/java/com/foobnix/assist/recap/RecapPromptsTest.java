package com.foobnix.assist.recap;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RecapPromptsTest {

    @Test
    public void systemAsksForSpoilerFreeJsonInTheRightForm() {
        final String s = RecapPrompts.system("", RecapLogic.LONG);
        assertTrue(s.contains("nothing after it"));
        assertTrue(s.contains("\"kind\""));
        assertTrue(s.contains("fiction: the story so far; non-fiction: the argument so far"));
        assertTrue(s.contains("the language the text is written in"));
        assertTrue(RecapPrompts.system("German", RecapLogic.SHORT).contains("Write in German."));
        assertTrue(RecapPrompts.system("", RecapLogic.SHORT).contains("3 to 8"));
    }

    @Test
    public void userHoldsOnlyTheExcerptsGiven() {
        final List<RecapLogic.Excerpt> e = Arrays.asList(
                new RecapLogic.Excerpt("Chapter \"One\"", 3, 3, "It began.", false),
                new RecapLogic.Excerpt("Chapter 2", 40, 44, "Then this.", true));
        final String u = RecapPrompts.user("Around the World", "Chapter 2", 45, 300, 21, e);
        assertTrue(u.startsWith("Book: Around the World\n"));
        assertTrue(u.contains("stopped at page 45 of 300, in \"Chapter 2\", and has been away 21 days."));
        assertTrue(u.contains("<excerpt chapter=\"Chapter 'One'\" pages=\"3\" sent=\"opening only\">\nIt began.\n</excerpt>"));
        assertTrue(u.contains("pages=\"40-44\" sent=\"whole\">\nThen this.\n</excerpt>"));
        assertTrue(RecapPrompts.user("", "", 2, 9, 1, Collections.<RecapLogic.Excerpt>emptyList()).contains("away 1 day."));
    }

    private static final String ANSWER = "Here it is:\n```json\n{\"kind\": \"fiction\","
            + "\"last_time\": \"They reached **Bombay**.\", \"stopped_at\": \"On the train past Benares.\","
            + "\"story\": [{\"text\": \"Fogg bet £20,000.\", \"chapter\": \"Ch. 3\", \"level\": 1},"
            + "{\"text\": \"Fix trails them.\", \"chapter\": \"Ch. 6\", \"level\": 2},"
            + "{\"text\": \"A detail.\", \"chapter\": \"Ch. 9\", \"level\": 7},"
            + "{\"text\": \"\", \"chapter\": \"Ch. 9\", \"level\": 1}],"
            + "\"cast\": [{\"name\": \"Phileas Fogg\", \"initials\": \"\", \"card\": \"An English gentleman.\"},"
            + "{\"name\": \"Passepartout\", \"initials\": \"P\", \"card\": \"His servant.\"}, {\"card\": \"no name\"}],"
            + "\"status\": {\"headline\": \"Day 22 of 80\", \"detail\": \"On schedule.\"},"
            + "\"warmup\": [{\"q\": \"Why the bet?\", \"a\": \"A dispute.\"}, \"Who is Fix?\", \"Third?\"]}\n```";

    @Test
    public void parsesSections() {
        final RecapPrompts.Recap r = RecapPrompts.parse(ANSWER);
        assertNotNull(r);
        assertTrue(r.isFiction());
        assertEquals("They reached Bombay.", r.lastTime);
        assertEquals("On the train past Benares.", r.stoppedAt);
        assertEquals(3, r.story.size());
        assertEquals(RecapLogic.LEVEL_5M, r.story.get(2).level);
        assertEquals(1, r.lines(RecapLogic.LEVEL_30S).size());
        assertEquals(2, r.lines(RecapLogic.LEVEL_2M).size());
        assertEquals(3, r.lines(RecapLogic.LEVEL_5M).size());
        assertEquals("Ch. 3", r.story.get(0).chapter);
        assertEquals(2, r.cast.size());
        assertEquals("PF", r.cast.get(0).initials);
        assertEquals("P", r.cast.get(1).initials);
        assertEquals("Day 22 of 80", r.headline);
        assertEquals("On schedule.", r.detail);
        assertEquals(2, r.warmup.size());
        assertEquals("A dispute.", r.warmup.get(0).a);
        assertEquals("Who is Fix?", r.warmup.get(1).q);
    }

    @Test
    public void roundTripThroughSavedJson() {
        final RecapPrompts.Recap r = RecapPrompts.parse(ANSWER);
        final RecapPrompts.Recap back = RecapPrompts.fromJson(r.toJson());
        assertNotNull(back);
        assertEquals(r.lastTime, back.lastTime);
        assertEquals(r.story.size(), back.story.size());
        assertEquals(r.story.get(1).level, back.story.get(1).level);
        assertEquals(r.cast.get(0).card, back.cast.get(0).card);
        assertEquals(r.warmup.get(0).q, back.warmup.get(0).q);
        assertEquals(r.headline, back.headline);
    }

    @Test
    public void nonFictionKind() {
        final RecapPrompts.Recap r = RecapPrompts.parse("{\"kind\":\"Non-fiction\",\"story\":[\"Smith argues.\"]}");
        assertNotNull(r);
        assertFalse(r.isFiction());
        assertEquals(RecapLogic.LEVEL_2M, r.story.get(0).level);
        assertEquals(1, r.lines(RecapLogic.LEVEL_30S).size());
    }

    @Test
    public void unusableAnswers() {
        assertNull(RecapPrompts.parse(null));
        assertNull(RecapPrompts.parse("no json here"));
        assertNull(RecapPrompts.parse("{\"kind\": \"fiction\"}"));
        assertNull(RecapPrompts.parse("{\"story\": [ broken"));
    }

    @Test
    public void initials() {
        assertEquals("PF", RecapPrompts.initials("Phileas Fogg"));
        assertEquals("A", RecapPrompts.initials("Aouda"));
        assertEquals("JS", RecapPrompts.initials("  John   Stuart Mill"));
        assertEquals("", RecapPrompts.initials(""));
    }
}
