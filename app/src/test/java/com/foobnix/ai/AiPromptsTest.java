package com.foobnix.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiPromptsTest {

    @Test
    public void parsesAPlainJsonArray() {
        List<String> quotes = AiPrompts.parseQuotes("[\"First sentence.\", \"Second one!\"]");
        assertEquals(Arrays.asList("First sentence.", "Second one!"), quotes);
    }

    @Test
    public void parsesAnArrayInsideACodeFenceWithProseAround() {
        String answer = "Here are the key sentences:\n```json\n[\n  \"He said \\\"no\\\".\",\n  \"Tôi đi học.\"\n]\n```\nHope this helps.";
        List<String> quotes = AiPrompts.parseQuotes(answer);
        assertEquals(Arrays.asList("He said \"no\".", "Tôi đi học."), quotes);
    }

    @Test
    public void decodesEscapes() {
        List<String> quotes = AiPrompts.parseQuotes("[\"tab\\there\", \"line\\nbreak\", \"\\u00e9t\\u00e9\"]");
        assertEquals(Arrays.asList("tab\there", "line\nbreak", "été"), quotes);
    }

    @Test
    public void noArrayMeansNoQuotes() {
        assertTrue(AiPrompts.parseQuotes("I could not find important sentences.").isEmpty());
        assertTrue(AiPrompts.parseQuotes(null).isEmpty());
        // an answer cut off inside a string keeps the complete ones
        assertEquals(Arrays.asList("Done."), AiPrompts.parseQuotes("[\"Done.\", \"Cut off mid"));
    }

    @Test
    public void passageCountGrowsWithTheText() {
        assertEquals(3, AiPrompts.passageCount(1000));
        assertEquals(10, AiPrompts.passageCount(30000));
        assertEquals(12, AiPrompts.passageCount(500000));
    }

    @Test
    public void summaryLanguageDefaultsToTheBooks() {
        assertTrue(AiPrompts.summarySystem("").contains("the language the text is written in"));
        assertTrue(AiPrompts.summarySystem("Vietnamese").contains("Write in Vietnamese."));
    }
}
