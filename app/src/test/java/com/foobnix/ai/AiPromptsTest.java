package com.foobnix.ai;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiPromptsTest {

    @Test
    public void parsesCategorizedPassages() {
        final List<AiPrompts.Picked> picked = AiPrompts.parsePicked(
                "[{\"category\": \"main\", \"quote\": \"First sentence.\"}, {\"category\": \"Caveat \", \"quote\": \"But not always!\"}]");
        assertEquals(2, picked.size());
        assertEquals(AiCategory.MAIN, picked.get(0).category);
        assertEquals("First sentence.", picked.get(0).quote);
        assertEquals(AiCategory.CAVEAT, picked.get(1).category);
    }

    @Test
    public void parsesObjectsInsideACodeFenceWithProseAround() {
        final String answer = "Here they are:\n```json\n[\n {\"category\":\"concept\",\"quote\":\"He said \\\"no {really}\\\".\"},\n"
                + " {\"category\":\"example\",\"quote\":\"T\\u00f4i đi học.\"}\n]\n```\nHope this helps.";
        final List<AiPrompts.Picked> picked = AiPrompts.parsePicked(answer);
        assertEquals(2, picked.size());
        assertEquals("He said \"no {really}\".", picked.get(0).quote);
        assertEquals("Tôi đi học.", picked.get(1).quote);
    }

    @Test
    public void unknownCategoriesAndBrokenAnswersAreLeftOut() {
        // yellow is the reader's, "highlight" is no category, an empty quote is nothing
        final List<AiPrompts.Picked> picked = AiPrompts.parsePicked("[{\"category\":\"yellow\",\"quote\":\"A.\"},"
                + "{\"category\":\"highlight\",\"quote\":\"B.\"},{\"category\":\"evidence\",\"quote\":\"\"},"
                + "{\"category\":\"evidence\",\"quote\":\"C.\"}, {\"category\":\"main\",\"quote\":\"Cut off mid");
        assertEquals(1, picked.size());
        assertEquals("C.", picked.get(0).quote);
        assertTrue(AiPrompts.parsePicked("I could not find important sentences.").isEmpty());
        assertTrue(AiPrompts.parsePicked(null).isEmpty());
    }

    @Test
    public void applyIsOfferedOnlyWithAGoal() {
        assertTrue(AiPrompts.keyPassagesSystem(true).contains("- apply"));
        assertFalse(AiPrompts.keyPassagesSystem(false).contains("- apply"));
        assertTrue(AiPrompts.keyPassagesSystem(false).contains("never use the category apply"));
        assertFalse(AiPrompts.keyPassagesSystem(true).toLowerCase().contains("yellow"));
        assertTrue(AiPrompts.keyPassages("Book", "Ch 1", "Text.", 5, "learn to negotiate").contains("The reader's goal: learn to negotiate"));
        assertFalse(AiPrompts.keyPassages("Book", "Ch 1", "Text.", 5, " ").contains("goal"));
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

    @Test
    public void theMasterPromptComesBeforeTheBuiltInRules() {
        final String system = AiPrompts.keyPassagesSystem(true);
        assertEquals(system, AiPrompts.withMaster("", system));
        assertEquals(system, AiPrompts.withMaster("  ", system));
        assertEquals(system, AiPrompts.withMaster(null, system));
        assertEquals("Focus on the arguments.\n\n" + system,AiPrompts.withMaster(" Focus on the arguments. ", system));
    }
}
