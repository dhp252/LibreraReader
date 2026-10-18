package com.foobnix.study;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
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
