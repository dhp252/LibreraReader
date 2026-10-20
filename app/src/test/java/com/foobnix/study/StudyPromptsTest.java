package com.foobnix.study;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyPromptsTest {

    @Test
    public void aPreviewIsReadWhole() {
        final String answer = "```json\n{\"thesis\": \"Price is a product decision.\", \"chapters\": ["
                + "{\"n\": 1, \"line\": \"Why prices matter\", \"label\": \"read\", \"reason\": \"core idea\","
                + " \"questions\": [\"What is value?\", \"Who sets it?\"]},"
                + "{\"n\": 2, \"line\": \"History\", \"label\": \"Skim\", \"reason\": \"background\","
                + " \"questions\": [\"a\", \"b\", \"c\", \"d\"]},"
                + "{\"n\": 3, \"line\": \"Airlines\", \"label\": \"deep-read\", \"reason\": \"?\", \"questions\": []}"
                + "]}\n```";
        final StudyPrompts.Preview p = StudyPrompts.parsePreview(answer);
        assertEquals("Price is a product decision.", p.thesis);
        assertEquals(3, p.chapters.size());
        assertEquals(Arrays.asList("What is value?", "Who sets it?"), p.chapters.get(1).questions);
        assertEquals(StudyBook.SKIM, p.chapters.get(2).label);
        assertEquals("at most three questions", 3, p.chapters.get(2).questions.size());
        assertEquals("unknown labels read closely", StudyBook.READ, p.chapters.get(3).label);
    }

    @Test
    public void aPreviewCutOffKeepsWhatCameWhole() {
        final String answer = "{\"thesis\": \"A \\\"quoted\\\" claim\", \"chapters\": ["
                + "{\"n\": 4, \"line\": \"L4\", \"label\": \"skip\", \"reason\": \"r\", \"questions\": [\"q\"]},"
                + "{\"n\": 5, \"line\": \"L5\", \"label\": \"read\", \"rea";
        final StudyPrompts.Preview p = StudyPrompts.parsePreview(answer);
        assertEquals("A \"quoted\" claim", p.thesis);
        assertEquals(1, p.chapters.size());
        assertEquals(StudyBook.SKIP, p.chapters.get(4).label);
    }

    @Test
    public void nothingUsableGivesAnEmptyPreview() {
        assertTrue(StudyPrompts.parsePreview("Sorry, I cannot help with that.").chapters.isEmpty());
        assertTrue(StudyPrompts.parsePreview(null).chapters.isEmpty());
        assertTrue(StudyPrompts.parsePreview("{\"chapters\": [{\"line\": \"no n\"}]}").chapters.isEmpty());
    }

    private static final String CHECK = "## Feedback\n"
            + "1. right - You named the anchor and why it fails.\n"
            + "> \"Anchors fail when buyers have a reference price.\"\n"
            + "2. **partly** — You named two of the three tests;\n"
            + "the third is the decoy test.\n"
            + "3. missed: The chapter says nothing about that.\n"
            + "## Recall\n"
            + "You did not mention the airline example.\n"
            + "## Map\n"
            + "```json\n"
            + "[{\"text\": \"Price is set by value\", \"cat\": \"main\", \"quote\": \"Value sets the price.\", \"kids\": ["
            + "{\"text\": \"A survey of 300 firms\", \"cat\": \"evidence\", \"quote\": \"\"},"
            + "{\"text\": \"Odd\", \"cat\": \"weird\", \"quote\": \"x\"}]},"
            + "{\"text\": \"Test prices\", \"cat\": \"apply\", \"quote\": \"\"}]\n"
            + "```";

    @Test
    public void aCheckIsReadBack() {
        final StudyPrompts.Check c = StudyPrompts.parseCheck(CHECK);
        assertEquals(3, c.items.size());
        assertEquals(0, c.items.get(0).question);
        assertEquals("right", c.items.get(0).verdict);
        assertEquals("Anchors fail when buyers have a reference price.", c.items.get(0).quote);
        assertEquals("partly", c.items.get(1).verdict);
        assertEquals("You named two of the three tests; the third is the decoy test.", c.items.get(1).note);
        assertEquals("missed", c.items.get(2).verdict);
        assertEquals("You did not mention the airline example.", c.recall);
        assertEquals(2, c.branch.size());
        assertEquals("Value sets the price.", c.branch.get(0).quote);
        assertEquals(2, c.branch.get(0).kids.size());
        assertEquals("unknown kinds below a claim are evidence", "evidence", c.branch.get(0).kids.get(1).category);
        assertEquals("apply", c.branch.get(1).category);
    }

    @Test
    public void aCheckWithoutAnswersHasOnlyAMapAndACutOffMapKeepsWholeClaims() {
        final StudyPrompts.Check c = StudyPrompts.parseCheck("## Map\n```json\n[{\"text\": \"One\", \"cat\": \"main\", "
                + "\"quote\": \"q\"}, {\"text\": \"Two\", \"cat\": \"main\", \"kids\": [{\"text\": \"cut");
        assertTrue(c.items.isEmpty());
        assertEquals(1, c.branch.size());
        assertEquals("One", c.branch.get(0).text);
    }

    @Test
    public void whileACheckStreamsTheMapIsNotShown() {
        assertEquals("1. right - You named the anchor and why it fails.\n"
                + "> \"Anchors fail when buyers have a reference price.\"",
                StudyPrompts.visible(CHECK.substring(0, CHECK.indexOf("2. **"))));
        final String shown = StudyPrompts.visible(CHECK);
        assertTrue(shown.endsWith("You did not mention the airline example."));
        assertFalse(shown.contains("Feedback"));
        assertFalse(shown.contains("json"));
        assertEquals("", StudyPrompts.visible("## Feed"));
    }

    @Test
    public void aCheckRequestCarriesTheAnswersOrAsksOnlyForTheMap() {
        final String asked = StudyPrompts.check("B", "Ch", "goal", "TEXT", false, Arrays.asList("Q1", "Q2"),
                Arrays.asList("A1", ""), "", Arrays.asList("\"m\" (note: n)"), Arrays.asList("[main] p"),
                Arrays.asList("[main] Habits compound."));
        assertTrue(asked.contains("<chapter>\nTEXT\n</chapter>"));
        assertTrue(asked.contains("1. Q: Q1\n   A: A1"));
        assertTrue(asked.contains("2. Q: Q2\n   A: (no answer)"));
        assertTrue(asked.contains("- \"m\" (note: n)"));
        assertTrue(asked.contains("- [main] p"));
        assertTrue(asked.contains("The ideas of this chapter, as marked in the reader's book:\n- [main] Habits compound."));
        final String mapOnly = StudyPrompts.check("B", "Ch", "", "TEXT", true, Arrays.asList("Q1"),
                Arrays.asList(""), " ", null, null, null);
        assertTrue(mapOnly.endsWith("write only the ## Map section."));
        assertTrue(mapOnly.contains("summaries of its parts"));
        assertTrue(StudyPrompts.checkSystem("", true).contains("apply"));
        assertFalse(StudyPrompts.checkSystem("", false).contains("apply"));
    }

    @Test
    public void thePreviewRequestCarriesTheGoalAndThePack() {
        final String user = StudyPrompts.preview("Pricing", "price my SaaS", "<chapter n=\"1\"/>\n");
        assertTrue(user.contains("Book: Pricing"));
        assertTrue(user.contains("The reader's goal: price my SaaS"));
        assertTrue(user.contains("<book>\n<chapter n=\"1\"/>\n</book>"));
        assertTrue(StudyPrompts.preview("T", "", "").contains("no goal"));
        assertTrue(StudyPrompts.previewSystem("").contains("the language the book is written in"));
        assertTrue(StudyPrompts.previewSystem("English").contains("Write in English."));
    }
}
