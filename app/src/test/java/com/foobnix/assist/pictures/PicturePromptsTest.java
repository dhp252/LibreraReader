package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PicturePromptsTest {

    @Test
    public void theSystemPromptStatesTheRules() {
        final String text = PicturePrompts.system("", PicturePrompts.Sent.TEXT);
        assertTrue(text.contains("one chapter"));
        assertTrue(text.contains("at most three"));
        assertTrue(text.contains("Draw only what the text says"));
        assertTrue(text.contains("A picture whose quote is not found in the text is thrown away"));
        assertTrue(text.contains("Every number you show must stand in the text"));
        assertTrue(text.contains("{\"pictures\": []}"));
        assertTrue(text.contains("the language of the text"));
        assertFalse(text.contains("key ideas"));
        for (Picture.Kind kind : Picture.Kind.values()) {
            assertTrue(text.contains("- " + kind.code + ":"));
            assertTrue(text.contains("\"kind\":\"" + kind.code + "\""));
        }
    }

    @Test
    public void withIdeasTheQuoteComesFromTheWordsInQuotationMarks() {
        final String text = PicturePrompts.system("Vietnamese", PicturePrompts.Sent.IDEAS);
        assertTrue(text.contains("Only the words in quotation marks are the book's"));
        assertTrue(text.contains("A picture whose quote is not found in the words in quotation marks is thrown away"));
        assertTrue(text.contains("in Vietnamese; the quote stays as the text has it"));
        assertTrue(PicturePrompts.system(" ", PicturePrompts.Sent.PASSAGE).contains("one passage"));
    }

    @Test
    public void theExampleInThePromptIsReadByTheParser() {
        final String system = PicturePrompts.system("", PicturePrompts.Sent.TEXT);
        final String example = system.substring(system.indexOf("{\"pictures\":[{"), system.indexOf("\ntitle: at most"));
        final String quote = "A sentence of the passage that every picture of this example is drawn from.";
        final PictureSource source = PictureSource.ofPassage(quote + " Wagons carry 4 tons, ships 200 tons. Ten people"
                + " make 4800 pins each when the work is split, 20 when it is not.", 0);
        final PictureSet set = PictureSet.parse(example.replace("\"quote\":\"...\"", "\"quote\":\"" + quote + "\""), source);
        assertEquals(3, set.pictures.size());
        assertEquals(1, set.of(Picture.Kind.PROCESS).cards.size());
        assertEquals(4, set.of(Picture.Kind.LOOP).nodes.size());
        assertEquals(2, set.of(Picture.Kind.LOOP).quantities.size());
        assertEquals(1, set.of(Picture.Kind.LOOP).loopType);
        assertEquals(0, set.numbersOut);
        assertEquals(0, set.leftOut);
        // and so is its model: a switch, a slider, an output, a preset and a curve
        final SimModel sim = set.sim;
        assertEquals(2, sim.inputs.size());
        assertTrue(sim.inputs.get(0).isSwitch);
        assertEquals(1, sim.outputs.size());
        assertEquals(1, sim.presets.size());
        assertEquals("people", sim.curveX.id);
        assertEquals(48000, sim.output(0, sim.start()), 0);
        assertFalse(sim.illustration);
    }

    @Test
    public void theSystemPromptStatesTheFormulaLanguage() {
        final String text = PicturePrompts.system("", PicturePrompts.Sent.TEXT);
        assertTrue(text.contains("\"sim\" (beside \"pictures\", at most one)"));
        assertTrue(text.contains("+ - * / ^"));
        assertTrue(text.contains("if(a < b, x, y) with < > <= >= =="));
        assertTrue(text.contains("runs nothing"));
        assertTrue(text.contains("a model with a formula it cannot read is thrown away"));
        assertTrue(text.contains("shown as an illustration, not the book's numbers"));
        for (String function : new String[]{"min(", "max(", "abs(", "sqrt(", "exp(", "ln(", "pow("}) {
            assertTrue(function, text.contains(function));
            assertTrue(function, SimFormula.isFunction(function.substring(0, function.length() - 1)));
        }
    }

    @Test
    public void theUserMessageNamesTheBookAndWhatFollows() {
        assertEquals("Book: The Wealth of Nations\nChapter: Of the Division of Labour\n\nText:\nbody",
                PicturePrompts.user(" The Wealth of Nations ", "Of the Division of Labour", PicturePrompts.Sent.TEXT, "body"));
        assertEquals("\nKey ideas:\nbody", PicturePrompts.user(null, " ", PicturePrompts.Sent.IDEAS, "body"));
        assertEquals("Book: B\n\nPassage:\nbody", PicturePrompts.user("B", null, PicturePrompts.Sent.PASSAGE, "body"));
    }

    @Test
    public void ideasAreSentWithTheirMarksInQuotationMarks() {
        final PictureIdea first = new PictureIdea("Splitting work multiplies output", "main");
        first.add("One man draws out the wire", 4);
        first.add("another straights it", 4);
        final PictureIdea second = new PictureIdea("Not located", "");
        assertEquals("- [main] Splitting work multiplies output\n   \"One man draws out the wire\"\n"
                + "   \"another straights it\"\n- Not located\n", PicturePrompts.ideasBody(Arrays.asList(first, second)));
    }

    @Test
    public void ideasAreSentOnlyWhenTheyCarryEnoughOfTheBooksWords() {
        final PictureIdea thin = new PictureIdea("An idea", "main");
        thin.add("A short mark.", 1);
        assertFalse(PicturePrompts.enoughMarks(Collections.singletonList(thin)));
        assertFalse(PicturePrompts.enoughMarks(Collections.<PictureIdea>emptyList()));
        final StringBuilder mark = new StringBuilder();
        while (mark.length() < PicturePrompts.MIN_MARK_CHARS) {
            mark.append("the book's own words ");
        }
        final PictureIdea full = new PictureIdea("An idea", "main");
        full.add(mark.toString(), 1);
        final List<PictureIdea> ideas = Arrays.asList(thin, full);
        assertTrue(PicturePrompts.enoughMarks(ideas));
    }

    @Test
    public void aShortSelectionIsNotDrawn() {
        assertEquals("", PicturePrompts.passage(null));
        assertEquals("", PicturePrompts.passage("  a few words  "));
        final StringBuilder s = new StringBuilder();
        while (s.length() < PicturePrompts.MIN_PASSAGE) {
            s.append("selected words ");
        }
        assertEquals(s.toString().trim(), PicturePrompts.passage("  " + s));
        while (s.length() < PicturePrompts.MAX_CHARS + 100) {
            s.append("selected words ");
        }
        assertEquals(PicturePrompts.MAX_CHARS, PicturePrompts.passage(s.toString()).length());
    }
}
