package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReadsPromptsTest {

    @Test
    public void theSelectionIsSentWhenThereIsOne() {
        assertEquals("the selected words", ReadsPrompts.passage("  the selected\nwords ", "the whole page"));
        assertEquals("the whole page", ReadsPrompts.passage("  ", "the whole\n\npage"));
        assertEquals("the whole page", ReadsPrompts.passage(null, "the whole page"));
        assertEquals("", ReadsPrompts.passage(null, null));
    }

    @Test
    public void aLongPageIsCut() {
        final StringBuilder page = new StringBuilder();
        while (page.length() < 3 * ReadsPrompts.PASSAGE_CHARS) {
            page.append("words of the page ");
        }
        final String sent = ReadsPrompts.passage(null, page.toString());
        assertTrue(sent.length() <= ReadsPrompts.PASSAGE_CHARS);
        assertTrue(sent.endsWith("…"));
        assertTrue(ReadsPrompts.isCut(page.toString()));
        assertFalse(ReadsPrompts.isCut("a short page"));
        assertFalse(ReadsPrompts.isCut(null));
    }

    @Test
    public void plainAndExampleAreDifferentRequests() {
        final String plain = ReadsPrompts.system(ReadsPrompts.Kind.PLAIN, "");
        final String example = ReadsPrompts.system(ReadsPrompts.Kind.EXAMPLE, "");
        assertTrue(plain.contains("plain"));
        assertTrue(plain.contains("do not add examples"));
        assertTrue(example.contains("example"));
        // the example is the model's, and must not pass as the author's
        assertTrue(example.contains("not the book's"));
        assertTrue(plain.contains("add no claim"));
        assertTrue(example.contains("add no claim"));
    }

    @Test
    public void theAnswerLanguageIsTheReadersOrThePassages() {
        assertTrue(ReadsPrompts.system(ReadsPrompts.Kind.PLAIN, "Vietnamese").contains("Write in Vietnamese."));
        assertTrue(ReadsPrompts.system(ReadsPrompts.Kind.PLAIN, " ").contains("the language the passage is written in"));
    }

    @Test
    public void theRequestCarriesOnlyThePassageAndItsBook() {
        final String user = ReadsPrompts.user("The Wealth of Nations", "Adam Smith", "This great increase of work");
        assertTrue(user.contains("Book: The Wealth of Nations"));
        assertTrue(user.contains("Author: Adam Smith"));
        assertTrue(user.contains("<passage>\nThis great increase of work\n</passage>"));
    }

    @Test
    public void theAnswerLosesFencesAndWrappingQuotes() {
        assertEquals("Splitting the work lets people make more.",
                ReadsPrompts.answer("```\n\"Splitting the work lets people make more.\"\n```"));
        assertEquals("", ReadsPrompts.answer(null));
    }
}
