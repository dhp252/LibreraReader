package com.foobnix.assist.lens;

import com.foobnix.ai.AiPrompts;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LensPromptsTest {

    private static LensProfile lens() {
        final LensProfile p = new LensProfile();
        p.toggle("Machine learning");
        p.toggle("Software engineering");
        return p;
    }

    @Test
    public void everyRequestDescribesTheReader() {
        final LensProfile p = lens();
        final String reader = LensPrompts.reader(p, LensProfile.Level.NEW);
        assertTrue(reader.contains("knows these fields well: Machine learning, Software engineering."));
        assertTrue(reader.contains("new to this book's subject"));
        assertTrue(reader.contains("Explain through the reader's fields"));
        assertTrue(reader.contains("plain present-day words"));
        assertFalse(reader.contains("Keep the author's key terms"));
        for (String system : new String[]{LensPrompts.bridgeSystem(p, LensProfile.Level.NEW, "", true),
                LensPrompts.bridgeSystem(p, LensProfile.Level.NEW, "", false),
                LensPrompts.ladderSystem(p, LensProfile.Level.NEW, ""),
                LensPrompts.sentenceSystem(p, LensProfile.Level.NEW, "", true),
                LensPrompts.sentenceSystem(p, LensProfile.Level.NEW, "", false)}) {
            assertTrue(system, system.contains(reader));
        }
    }

    @Test
    public void theSwitchesAndTheLevelChangeTheReader() {
        final LensProfile p = lens();
        p.analogies = false;
        p.plainWords = false;
        p.keepTerms = true;
        final String reader = LensPrompts.reader(p, LensProfile.Level.EXPERT);
        assertTrue(reader.contains("is an expert"));
        assertTrue(reader.contains("Use no analogies"));
        assertFalse(reader.contains("plain present-day words"));
        assertTrue(reader.contains("Keep the author's key terms"));
        assertTrue(LensPrompts.reader(p, LensProfile.Level.SOME).contains("knows some of this book's subject"));
        assertTrue(LensPrompts.reader(p, null).contains("new to this book's subject"));
    }

    @Test
    public void aBridgeFromIdeasAsksForIdsNotQuotes() {
        final String system = LensPrompts.bridgeSystem(lens(), LensProfile.Level.NEW, "", true);
        assertTrue(system.contains("- id: the number of the idea"));
        assertFalse(system.contains("- quote:"));
        assertFalse(system.contains(AiPrompts.SIDE_MATTER));
        assertTrue(system.contains("{\"pairs\":[{\"id\":3,\"author\":\"...\",\"yours\":\"...\"}],\"breaks\":\"...\"}"));
        assertTrue(system.contains("the chapter's " + LensBridge.MAX_ROWS + " most important ideas"));
        assertTrue(system.contains("the analogy to the reader's fields stops fitting"));
        assertTrue(system.contains("in the language the book is written in."));
    }

    @Test
    public void aBridgeFromTextAsksForExactQuotes() {
        final String system = LensPrompts.bridgeSystem(lens(), LensProfile.Level.NEW, " Vietnamese ", false);
        assertTrue(system.contains("- quote: the one sentence of the chapter"));
        assertTrue(system.contains("copied exactly"));
        assertFalse(system.contains("- id:"));
        assertTrue(system.endsWith(AiPrompts.SIDE_MATTER));
        assertTrue(system.contains("{\"pairs\":[{\"author\":\"...\",\"quote\":\"...\",\"yours\":\"...\"}],\"breaks\":\"...\"}"));
        assertTrue(system.contains("breaks in Vietnamese."));
    }

    @Test
    public void withoutAnalogiesTheNoteIsAboutPlainWords() {
        final LensProfile p = lens();
        p.analogies = false;
        final String system = LensPrompts.bridgeSystem(p, LensProfile.Level.NEW, "", true);
        assertTrue(system.contains("the plain wording loses something"));
        assertFalse(system.contains("the closest thing in the reader's fields"));
        assertTrue(LensPrompts.ladderSystem(p, LensProfile.Level.NEW, "").contains("of everyday life"));
        assertTrue(LensPrompts.sentenceSystem(p, LensProfile.Level.NEW, "", true).contains("of everyday life"));
    }

    @Test
    public void theIdeasAreListedWithTheirIds() {
        final String user = LensPrompts.bridgeUser("The Wealth of Nations", "Adam Smith", "Of the Division of Labour",
                Arrays.asList(new LensIdea(1, "Splitting work\n multiplies output", "main", "quote", 3),
                        new LensIdea(4, "Pin shop", "", null, -1)));
        assertEquals("Book: The Wealth of Nations\nAuthor: Adam Smith\nChapter: Of the Division of Labour\n\n"
                + "Ideas of the chapter:\n1 [main]: Splitting work multiplies output\n4: Pin shop\n", user);
        // the marks' words are not sent
        assertFalse(user.contains("quote"));
    }

    @Test
    public void theTextIsCappedAtFortyThousandCharacters() {
        final StringBuilder text = new StringBuilder();
        while (text.length() < LensPrompts.MAX_CHARS + 5000) {
            text.append("word ");
        }
        final String user = LensPrompts.bridgeUser("Book", "", "", text.toString());
        assertTrue(user.startsWith("Book: Book\n\n<text>\nword "));
        assertTrue(user.endsWith("…\n</text>"));
        assertTrue(user.length() <= LensPrompts.MAX_CHARS + 40);
        assertEquals("<text>\nshort\n</text>", LensPrompts.bridgeUser(null, null, null, " short "));
    }

    @Test
    public void theLadderRequestCarriesTheIdeaItsTermsAndItsWords() {
        final String system = LensPrompts.ladderSystem(lens(), LensProfile.Level.SOME, "");
        assertTrue(system.contains("- principle:"));
        assertTrue(system.contains("- mechanism:"));
        assertTrue(system.contains("- example:"));
        assertTrue(system.contains("- world: one concrete situation from the reader's own fields"));
        assertTrue(system.contains("{\"principle\":\"...\",\"mechanism\":\"...\",\"example\":\"...\",\"world\":\"...\"}"));
        final String user = LensPrompts.ladderUser("Book", "", "Chapter 1", "Division of labour", "Split a monolith",
                "The greatest improvement in the productive powers of labour");
        assertEquals("Book: Book\nChapter: Chapter 1\n\nThe idea: Division of labour\nIn the reader's terms: Split a monolith\n"
                + "The author's words:\n<passage>\nThe greatest improvement in the productive powers of labour\n</passage>", user);
        assertFalse(LensPrompts.ladderUser("Book", "", "", "Idea", "", "").contains("<passage>"));
    }

    @Test
    public void aSentenceGoesDownOrUp() {
        final String down = LensPrompts.sentenceSystem(lens(), LensProfile.Level.NEW, "", true);
        assertTrue(down.contains("make one passage of a book concrete"));
        assertTrue(down.contains("from the reader's own fields"));
        assertTrue(down.contains("the language the passage is written in"));
        final String up = LensPrompts.sentenceSystem(lens(), LensProfile.Level.NEW, "English", false);
        assertTrue(up.contains("boil one passage of a book down"));
        assertTrue(up.contains("the general point the passage makes"));
        assertTrue(up.contains("Write in English."));
        assertEquals("Book: Book\nAuthor: Smith\n\n<passage>\nthe time lost in passing from one work to another\n</passage>",
                LensPrompts.sentenceUser("Book", "Smith", " the time lost\n in passing  from one work to another "));
    }

    @Test
    public void aSelectionIsOneLineAndCapped() {
        assertEquals("a b c", LensPrompts.sentence(" a\n b\t c "));
        assertEquals("", LensPrompts.sentence(null));
        final StringBuilder text = new StringBuilder();
        while (text.length() < LensPrompts.SENTENCE_CHARS * 2) {
            text.append("word ");
        }
        final String cut = LensPrompts.sentence(text.toString());
        assertTrue(cut.length() <= LensPrompts.SENTENCE_CHARS);
        assertTrue(cut.endsWith("…"));
    }

    @Test
    public void aPlainAnswerLosesFencesAndQuotationMarks() {
        assertEquals("Every jump costs a warm-up.", LensPrompts.plain("  \"Every jump costs a warm-up.\"\n"));
        assertEquals("Every jump costs a warm-up.", LensPrompts.plain("```\nEvery jump costs a warm-up.\n```"));
        assertEquals("", LensPrompts.plain(null));
        assertEquals("", LensPrompts.plain("   "));
    }
}
