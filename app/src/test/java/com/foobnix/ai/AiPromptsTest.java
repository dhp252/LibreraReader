package com.foobnix.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiPromptsTest {

    @Test
    public void parsesIdeasWithTheirAnchors() {
        final AiPrompts.Ideas read = AiPrompts.parseIdeas("[{\"id\": 1, \"category\": \"main\", \"idea\": \"Habits compound.\", "
                + "\"parent\": 0, \"sentences\": [\"First sentence.\"], \"paragraphs\": [{\"start\": \"Some years ago\", \"end\": \"to the sea.\"}]},"
                + " {\"id\": 2, \"category\": \"Caveat \", \"idea\": \"Not always.\", \"parent\": 1, \"sentences\": [\"But not always!\", \" \"]}]");
        assertFalse(read.cutOff);
        assertEquals(2, read.ideas.size());
        final AiPrompts.Idea first = read.ideas.get(0);
        assertEquals(1, first.id);
        assertEquals(AiCategory.MAIN, first.category);
        assertEquals("Habits compound.", first.text);
        assertEquals(-1, first.parent);
        assertEquals(Arrays.asList("First sentence."), first.sentences);
        assertArrayEquals(new String[]{"Some years ago", "to the sea."}, first.paragraphs.get(0));
        final AiPrompts.Idea second = read.ideas.get(1);
        assertEquals(AiCategory.CAVEAT, second.category);
        assertEquals(1, second.parent);
        assertEquals(Arrays.asList("But not always!"), second.sentences);
        assertTrue(second.paragraphs.isEmpty());
    }

    @Test
    public void parsesObjectsInsideACodeFenceWithProseAround() {
        final String answer = "Here they are:\n```json\n[\n {\"id\":1,\"category\":\"concept\",\"idea\":\"Braces.\",\"sentences\":[\"He said \\\"no {really}\\\".\"]},\n"
                + " {\"id\":2,\"category\":\"example\",\"idea\":\"Học.\",\"sentences\":[\"T\\u00f4i đi học.\"]}\n]\n```\nHope this helps.";
        final AiPrompts.Ideas read = AiPrompts.parseIdeas(answer);
        assertFalse(read.cutOff);
        assertEquals(2, read.ideas.size());
        assertEquals("He said \"no {really}\".", read.ideas.get(0).sentences.get(0));
        assertEquals("Tôi đi học.", read.ideas.get(1).sentences.get(0));
    }

    @Test
    public void unknownCategoriesAndBrokenAnswersAreLeftOut() {
        // yellow is the reader's, "highlight" is no category, no id or no idea is nothing
        final AiPrompts.Ideas read = AiPrompts.parseIdeas("[{\"id\":1,\"category\":\"yellow\",\"idea\":\"A.\"},"
                + "{\"id\":2,\"category\":\"highlight\",\"idea\":\"B.\"},{\"category\":\"evidence\",\"idea\":\"No id.\"},"
                + "{\"id\":4,\"category\":\"evidence\",\"idea\":\"\"},{\"id\":5,\"category\":\"main\",\"idea\":\"Own parent.\",\"parent\":5},"
                + "{\"id\":6,\"category\":\"evidence\",\"idea\":\"C.\"}, {\"id\":7,\"category\":\"main\",\"idea\":\"Cut off mid");
        assertEquals(2, read.ideas.size());
        assertEquals(-1, read.ideas.get(0).parent);
        assertEquals("C.", read.ideas.get(1).text);
        assertTrue(read.cutOff);
        assertTrue(AiPrompts.parseIdeas("I could not find any ideas.").ideas.isEmpty());
        assertTrue(AiPrompts.parseIdeas(null).ideas.isEmpty());
        assertFalse(AiPrompts.parseIdeas("[]").cutOff);
    }

    @Test
    public void parsesRepairAnswers() {
        final Map<Integer, List<String>> found = AiPrompts.parseRepair(
                "[{\"id\": 3, \"sentences\": [\"Found it.\"]}, {\"id\": 4, \"sentences\": []}, {\"id\": 0, \"sentences\": [\"No id.\"]}]");
        assertEquals(1, found.size());
        assertEquals(Arrays.asList("Found it."), found.get(3));
    }

    @Test
    public void applyIsOfferedOnlyWithAGoal() {
        assertTrue(AiPrompts.ideasSystem(true, "").contains("- apply"));
        assertFalse(AiPrompts.ideasSystem(false, "").contains("- apply"));
        assertTrue(AiPrompts.ideasSystem(false, "").contains("never use the category apply"));
        assertFalse(AiPrompts.ideasSystem(true, "").toLowerCase().contains("yellow"));
        assertTrue(AiPrompts.ideas("Book", "Ch 1", "Text.", "learn to negotiate").contains("The reader's goal: learn to negotiate"));
        assertFalse(AiPrompts.ideas("Book", "Ch 1", "Text.", " ").contains("goal"));
    }

    @Test
    public void ideasAreWrittenInTheChosenLanguage() {
        assertTrue(AiPrompts.ideasSystem(true, "").contains("Write the ideas in the language the text is written in."));
        assertTrue(AiPrompts.ideasSystem(true, "Vietnamese").contains("Write the ideas in Vietnamese."));
    }

    @Test
    public void repairListsOnlyTheGivenIdeas() {
        final String prompt = AiPrompts.repair("Book", "Ch 1", "The text.", Arrays.asList(
                new AiPrompts.Idea(3, AiCategory.MAIN, "Habits compound.", -1, null, null)));
        assertTrue(prompt.contains("3. Habits compound.\n"));
        assertTrue(prompt.contains("<text>\nThe text.\n</text>"));
    }

    @Test
    public void summaryLanguageDefaultsToTheBooks() {
        assertTrue(AiPrompts.summarySystem("").contains("the language the text is written in"));
        assertTrue(AiPrompts.summarySystem("Vietnamese").contains("Write in Vietnamese."));
    }

    @Test
    public void theMasterPromptComesBeforeTheBuiltInRules() {
        final String system = AiPrompts.ideasSystem(true, "");
        assertEquals(system, AiPrompts.withMaster("", system));
        assertEquals(system, AiPrompts.withMaster("  ", system));
        assertEquals(system, AiPrompts.withMaster(null, system));
        assertEquals("Focus on the arguments.\n\n" + system, AiPrompts.withMaster(" Focus on the arguments. ", system));
    }
}
