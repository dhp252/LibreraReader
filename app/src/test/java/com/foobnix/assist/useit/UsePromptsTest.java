package com.foobnix.assist.useit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UsePromptsTest {

    private static final String ANSWER = "{\"idea\":\"Dividing work raises output.\","
            + "\"try\":\"Keep each person on one job for a whole week instead of rotating daily.\","
            + "\"predicts\":\"Fewer switches, more work done.\","
            + "\"measures\":[{\"name\":\"Tickets closed a week\",\"scale\":\"\",\"higher_is_better\":true},"
            + "{\"name\":\"Task switches a day\",\"scale\":\"\",\"higher_is_better\":false},"
            + "{\"name\":\"Boredom\",\"scale\":\"1-5\",\"higher_is_better\":false}],"
            + "\"warning\":{\"text\":\"One task for too long dulls people.\","
            + "\"quote\":\"“generally becomes as stupid and ignorant as it is possible”\"}}";

    @Test
    public void theAnswerBecomesADraft() {
        final UseExperiment e = UsePrompts.parse(ANSWER);
        assertNotNull(e);
        assertEquals("Dividing work raises output.", e.idea);
        assertEquals("Keep each person on one job for a whole week instead of rotating daily.", e.tryText);
        assertEquals("Fewer switches, more work done.", e.predicts);
        assertEquals(3, e.measures.size());
        assertTrue(e.measures.get(0).higherBetter);
        assertFalse(e.measures.get(1).higherBetter);
        assertEquals("Boredom, 1–5", e.measures.get(2).label());
        assertEquals("One task for too long dulls people.", e.warning);
        // the model's own quote marks go; whether it is shown as a quote is settled against the book
        assertEquals("generally becomes as stupid and ignorant as it is possible", e.quote);
        assertFalse(e.quoted);
        assertEquals("", e.id);
        assertEquals("", e.startDay);
    }

    @Test
    public void fencesAndTalkAroundTheJsonAreIgnored() {
        final UseExperiment e = UsePrompts.parse("Here is your experiment:\n```json\n" + ANSWER + "\n```\nGood luck!");
        assertNotNull(e);
        assertEquals(3, e.measures.size());
    }

    @Test
    public void otherShapesAreRead() {
        final UseExperiment e = UsePrompts.parse("{\"experiment\":{\"action\":\"Read  one chapter\\n a day.\","
                + "\"prediction\":\"More gets finished.\",\"measures\":[\"Chapters a week\",\"\",{\"measure\":\"Focus\","
                + "\"unit\":\"1 - 10\",\"higher\":\"false\"},{\"scale\":\"1-5\"},{\"name\":\"Mood\"},{\"name\":\"A fourth\"}],"
                + "\"warning\":\"Do not skim.\"}}");
        assertNotNull(e);
        assertEquals("Read one chapter a day.", e.tryText);
        assertEquals("More gets finished.", e.predicts);
        assertEquals("", e.idea);
        // unnamed ones are left out, and only three are kept
        assertEquals(3, e.measures.size());
        assertEquals("Chapters a week", e.measures.get(0).name);
        assertTrue(e.measures.get(0).higherBetter);
        assertEquals("Focus", e.measures.get(1).name);
        assertEquals("1–10", e.measures.get(1).scale);
        assertFalse(e.measures.get(1).higherBetter);
        assertEquals("Mood", e.measures.get(2).name);
        assertEquals("Do not skim.", e.warning);
        assertEquals("", e.quote);
    }

    @Test
    public void noWarningAndNoMeasures() {
        final UseExperiment e = UsePrompts.parse("{\"try\":\"Walk before writing.\",\"predicts\":null,\"warning\":null}");
        assertNotNull(e);
        assertEquals("", e.predicts);
        assertEquals("", e.warning);
        assertEquals("", e.quote);
        assertTrue(e.measures.isEmpty());
        assertEquals("", UsePrompts.warningLine(e));
    }

    @Test
    public void whatCannotBeRead() {
        assertNull(UsePrompts.parse(null));
        assertNull(UsePrompts.parse(""));
        assertNull(UsePrompts.parse("I cannot help with that."));
        assertNull(UsePrompts.parse("{\"try\": \"cut off"));
        assertNull(UsePrompts.parse("{\"idea\":\"An idea but nothing to try\"}"));
        assertNull(UsePrompts.parse("{\"try\":\"   \"}"));
    }

    @Test
    public void quoteMarksOnlyForWordsFoundInTheBook() {
        final UseExperiment e = UsePrompts.parse(ANSWER);
        // not found: the sentence alone, no quote marks anywhere
        UsePrompts.settleQuote(e, null);
        assertFalse(e.quoted);
        assertEquals("One task for too long dulls people.", UsePrompts.warningLine(e));

        // found: shown in the book's own words, in quote marks
        UsePrompts.settleQuote(e, " generally becomes as stupid and ignorant as it is possible ");
        assertTrue(e.quoted);
        assertEquals("One task for too long dulls people. “generally becomes as stupid and ignorant as it is possible”",
                UsePrompts.warningLine(e));

        // a quote without a sentence
        e.warning = "";
        assertEquals("“generally becomes as stupid and ignorant as it is possible”", UsePrompts.warningLine(e));
        UsePrompts.settleQuote(e, "");
        assertFalse(e.quoted);
        assertEquals("generally becomes as stupid and ignorant as it is possible", UsePrompts.warningLine(e));
    }

    @Test
    public void theRequestCarriesTheAnswerAndTheIdeas() {
        final List<UsePrompts.IdeaLine> ideas = new ArrayList<>();
        ideas.add(new UsePrompts.IdeaLine("main", "Division of labour\n raises output.",
                Arrays.asList("ten persons could make among them upwards of forty-eight thousand pins in a day", "second",
                        "a third mark that is not sent")));
        ideas.add(new UsePrompts.IdeaLine(null, "  ", Collections.<String>emptyList()));
        ideas.add(new UsePrompts.IdeaLine("caveat", "It dulls the worker.", Collections.<String>emptyList()));
        final String material = UsePrompts.ideas(ideas);
        assertTrue(material.contains("1 [main]: Division of labour raises output.\n"));
        assertTrue(material.contains("   \"ten persons could make among them upwards of forty-eight thousand pins in a day\"\n"));
        assertTrue(material.contains("   \"second\"\n"));
        assertFalse(material.contains("a third mark"));
        // the empty idea is passed over and does not take a number
        assertTrue(material.contains("2 [caveat]: It dulls the worker.\n"));

        final String user = UsePrompts.user("The Wealth of Nations", "Adam Smith", "Of the Division of Labour", material,
                true, "Our team rotates\nevery day.");
        assertTrue(user.startsWith("Book: The Wealth of Nations\nAuthor: Adam Smith\nChapter: Of the Division of Labour\n"));
        assertTrue(user.contains("The reader's answer: Our team rotates every day.\n"));
        assertTrue(user.contains("The chapter's ideas, each with the book's own words:\n1 [main]"));

        final String text = UsePrompts.user("", null, " ", "Some chapter text.", false, "x");
        assertFalse(text.contains("Book:"));
        assertFalse(text.contains("Author:"));
        assertFalse(text.contains("Chapter:"));
        assertTrue(text.endsWith("The chapter's text:\nSome chapter text."));
    }

    @Test
    public void longMaterialAndAnswersAreCut() {
        final StringBuilder longText = new StringBuilder();
        while (longText.length() < UsePrompts.MAX_CHARS + 500) {
            longText.append("word ");
        }
        final String user = UsePrompts.user("B", "A", "C", longText.toString(), false, longText.toString());
        assertTrue(user.length() < UsePrompts.MAX_CHARS + UsePrompts.ANSWER_CHARS + 200);

        final List<UsePrompts.IdeaLine> many = new ArrayList<>();
        for (int i = 0; i < UsePrompts.MAX_IDEAS + 20; i++) {
            many.add(new UsePrompts.IdeaLine("main", "Idea " + i, Collections.<String>emptyList()));
        }
        final String material = UsePrompts.ideas(many);
        assertTrue(material.contains(UsePrompts.MAX_IDEAS + " [main]"));
        assertFalse(material.contains((UsePrompts.MAX_IDEAS + 1) + " [main]"));
    }

    @Test
    public void theSystemPromptNamesTheLanguageAndTheShape() {
        final String auto = UsePrompts.system("");
        assertTrue(auto.contains("the language of the reader's answer"));
        assertTrue(auto.contains("\"higher_is_better\""));
        assertTrue(auto.contains("Never invent a quote"));
        assertTrue(UsePrompts.system(" Vietnamese ").endsWith("in Vietnamese."));
    }
}
