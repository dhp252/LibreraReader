package com.foobnix.assist.zoom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ZoomDataTest {

    private static ZoomData placed() {
        final ZoomData d = ZoomData.parse(ZoomSample.ANSWER);
        d.place(ZoomSample.text(), 3);
        return d;
    }

    @Test
    public void theAnswerIsReadThroughFencesAndTextAroundIt() {
        final ZoomData d = ZoomData.parse(ZoomSample.ANSWER);
        assertNotNull(d);
        assertEquals("Splitting work multiplies what people make.", d.line);
        assertEquals(2, d.sections.size());
        final ZoomData.Section s = d.sections.get(0);
        assertEquals("The pin shop", s.title);
        assertEquals("Ten specialists make 48,000 pins a day.", s.line);
        assertEquals("Why can ten specialists make so many pins?", s.question);
        assertEquals(2, s.blocks.size());
        assertEquals("best example", s.blocks.get(0).label);
        assertEquals(2, s.blocks.get(0).sentences.size());
        assertTrue(s.blocks.get(1).fold);
        assertEquals("other trades, and why farming lags", s.blocks.get(1).label);
        // a JSON null is no text (Android's optString would say "null")
        assertEquals("", d.sections.get(1).question);
        assertEquals("", d.sections.get(1).blocks.get(0).label);
        assertEquals(6, d.claims.size());
        assertEquals(ZoomKind.CLAIM, d.claims.get(0).kind);
        assertEquals(ZoomKind.WHY, d.claims.get(3).kind);
        assertEquals(ZoomKind.SO_WHAT, d.claims.get(5).kind);
        assertEquals("the pin shop", d.claims.get(1).example);
    }

    @Test
    public void aLooseAnswerIsStillRead() {
        // the lean version beside the sections, sentences as plain strings, a paragraph as one string
        final ZoomData d = ZoomData.parse("{\"one_line\": \"All of it.\", \"sections\": [{\"title\": \"A\", \"summary\": \"First.\"},"
                + "{\"name\": \"B\", \"line\": \"Second.\"}], \"lean\": [{\"section\": 2, \"kept\": true, \"sentences\": [\"One.\", "
                + "{\"sentence\": \"Two.\", \"from\": \"some words of the book\"}]}, \"A whole paragraph.\", "
                + "{\"fold\": \"an aside\", \"percent\": 250}, 17, {\"sentences\": []}],"
                + "\"claims\": [\"A bare claim.\", {\"type\": \"objection\", \"claim\": \"But not always.\"}, {\"kind\": \"why\"}, 5]}");
        assertNotNull(d);
        assertEquals("All of it.", d.line);
        assertEquals("First.", d.sections.get(0).line);
        assertEquals("B", d.sections.get(1).title);
        assertEquals(1, d.sections.get(1).blocks.size());
        assertEquals("", d.sections.get(1).blocks.get(0).label);
        assertEquals("Two.", d.sections.get(1).blocks.get(0).sentences.get(1).text);
        assertEquals("some words of the book", d.sections.get(1).blocks.get(0).sentences.get(1).quote);
        assertEquals(2, d.sections.get(0).blocks.size());
        assertEquals("A whole paragraph.", d.sections.get(0).blocks.get(0).sentences.get(0).text);
        assertTrue(d.sections.get(0).blocks.get(1).fold);
        assertEquals(100, d.sections.get(0).blocks.get(1).percent);
        assertEquals(2, d.claims.size());
        assertEquals(ZoomKind.CLAIM, d.claims.get(0).kind);
        assertEquals(ZoomKind.CAVEAT, d.claims.get(1).kind);
    }

    @Test
    public void aLeanVersionWithoutSectionsGetsOne() {
        final ZoomData d = ZoomData.parse("{\"lean\": [{\"sentences\": [{\"text\": \"Only this.\"}]}]}");
        assertNotNull(d);
        assertEquals(1, d.sections.size());
        assertTrue(d.hasLean());
        assertEquals("", d.line);
    }

    @Test
    public void theOneLineFallsBackToTheFirstSection() {
        final ZoomData d = ZoomData.parse("{\"sections\": [{\"line\": \"First.\", \"lean\": [\"Lean text.\"]}]}");
        assertEquals("First.", d.line);
    }

    @Test
    public void anAnswerWithNothingToShowIsNone() {
        assertNull(ZoomData.parse(null));
        assertNull(ZoomData.parse(""));
        assertNull(ZoomData.parse("I cannot do that."));
        assertNull(ZoomData.parse("[1, 2, 3]"));
        assertNull(ZoomData.parse("{\"line\": \"Only a line.\"}"));
        assertNull(ZoomData.parse("{\"line\": \"cut off\", \"sections\": [{\"title\": \"A\", \"lean\": [{\"sentences\": [{\"text\": \"x"));
        assertNull(ZoomData.parse("{\"sections\": [{\"title\": \"A\", \"line\": \"B\"}], \"claims\": []}"));
    }

    @Test
    public void quotesBecomeTheBooksOwnWordsWithTheirParagraph() {
        final ZoomData d = placed();
        final ZoomData.Sentence pins = d.sections.get(0).blocks.get(0).sentences.get(1);
        assertTrue(pins.located());
        assertEquals(3, pins.para);
        assertEquals(0, pins.page);
        assertTrue(pins.quote.startsWith("I have seen a small manufactory of this kind"));
        assertEquals((ZoomSample.P3A + " " + ZoomSample.P3B).split(" ").length, pins.words);
        // the passage is found again from what is stored
        final ZoomText text = ZoomSample.text();
        assertEquals(ZoomSample.P3A + " " + ZoomSample.P3B, text.text(text.locateFrom(pins.quote, pins.words)));

        final ZoomData.Sentence made = d.sections.get(1).blocks.get(0).sentences.get(1);
        assertFalse(made.located());
        assertEquals("", made.quote);
        assertEquals(-1, made.page);

        final ZoomData.Claim evidence = d.claims.get(1);
        assertTrue(evidence.located());
        assertEquals(3, evidence.para);
        assertEquals("Those ten persons, therefore, could make among them upwards of forty-eight thousand pins in a day.",
                evidence.quote);
        assertEquals(4, d.claims.get(4).para);
        assertEquals(1, d.claims.get(4).page);
        final ZoomData.Claim missing = d.claims.get(3);
        assertFalse(missing.located());
        assertEquals("", missing.quote);
    }

    @Test
    public void aFoldStandsForTheWordsUpToTheNextThingFound() {
        final ZoomData d = placed();
        final ZoomData.Block fold = d.sections.get(0).blocks.get(1);
        assertTrue(fold.located());
        assertEquals(4, fold.para);
        assertEquals(1, fold.page);
        assertEquals(ZoomSample.P4.split(" ").length, fold.words);
        assertTrue(fold.quote.startsWith("In every other art and manufacture"));
    }

    @Test
    public void theLastFoldRunsToTheEndOfTheText() {
        final ZoomData d = ZoomData.parse("{\"sections\": [{\"line\": \"x\", \"lean\": [{\"sentences\": [{\"text\": \"Lean.\", "
                + "\"quote\": \"The greatest improvement in the productive powers\"}]}, {\"folded\": \"the rest\", "
                + "\"quote\": \"This great increase of the quantity of work\"}]}]}");
        d.place(ZoomSample.text(), 3);
        assertEquals((ZoomSample.P5 + " " + ZoomSample.P6).split(" ").length, d.sections.get(0).blocks.get(1).words);
    }

    @Test
    public void sectionsLearnTheirParagraphsAndPages() {
        final ZoomData d = placed();
        assertEquals(3, d.pages);
        final ZoomData.Section first = d.sections.get(0);
        final ZoomData.Section second = d.sections.get(1);
        assertEquals(1, first.para);
        assertEquals(4, first.paraTo);
        assertEquals(0, first.page);
        assertEquals(5, second.para);
        assertEquals(6, second.paraTo);
        assertEquals(2, second.page);
        assertTrue(second.start.startsWith("This great increase of the quantity of work"));
    }

    @Test
    public void aSectionWithoutItsFirstWordsStartsAtItsFirstSentenceFound() {
        final ZoomData d = ZoomData.parse(ZoomSample.ANSWER.replace("\"start\": \"This great increase of the quantity of work is owing\"",
                "\"start\": \"The greatest improvement in the productive powers\""));
        // the model repeated the first section's words: they are before this section, so not its start
        d.place(ZoomSample.text(), 3);
        assertEquals(5, d.sections.get(1).para);
        final ZoomData none = ZoomData.parse("{\"sections\": [{\"line\": \"x\", \"start\": \"nowhere in the chapter, these words\", "
                + "\"lean\": [\"Lean.\"]}]}");
        none.place(ZoomSample.text(), 3);
        assertFalse(none.sections.get(0).located());
        assertEquals(-1, none.sections.get(0).page);
        assertEquals(-1, none.sectionAt(0.5f));
    }

    @Test
    public void youAreHereIsTheLastSectionStartedAtTheReadersPage() {
        final ZoomData d = placed();
        assertEquals(0, d.sectionAt(0f));
        assertEquals(0, d.sectionAt(1f / 3));
        assertEquals(1, d.sectionAt(2f / 3));
        assertEquals(1, d.sectionAt(1f));
        // the same place in a layout with other pages
        assertEquals(2, d.pageIn(2, 3));
        assertEquals(20, d.pageIn(2, 30));
        assertEquals(0, d.pageIn(-1, 30));
        assertEquals(0, d.pageIn(0, 0));
        assertEquals(29, d.pageIn(3, 30));
    }

    @Test
    public void reasonsAreNumberedWhenThereAreSeveral() {
        final ZoomData d = placed();
        assertEquals(0, d.number(0));
        assertEquals(1, d.number(2));
        assertEquals(2, d.number(3));
        assertEquals(0, d.number(4));
        assertEquals(0, d.number(99));
        final ZoomData one = ZoomData.parse("{\"claims\": [{\"kind\": \"claim\", \"text\": \"A\"}, {\"kind\": \"why\", \"text\": \"B\"}]}");
        assertEquals(0, one.number(1));
    }

    @Test
    public void theStoredItemComesBackTheSame() throws Exception {
        final ZoomData d = placed();
        d.title = "CHAPTER I: Of the Division of Labour";
        d.chars = 1000;
        d.fullChars = 2500;
        d.model = "some-model";
        final ZoomData back = ZoomData.fromJson(new JSONObject(d.toJson().toString()));
        assertNotNull(back);
        assertEquals(d.toJson().toString().length(), back.toJson().toString().length());
        assertEquals("CHAPTER I: Of the Division of Labour", back.title);
        assertEquals(d.line, back.line);
        assertEquals(3, back.pages);
        assertTrue(back.partial());
        assertEquals(40, back.coveredPercent());
        assertEquals(2, back.sections.size());
        assertEquals(4, back.sections.get(0).paraTo);
        assertEquals(2, back.sections.get(1).page);
        final ZoomData.Block fold = back.sections.get(0).blocks.get(1);
        assertTrue(fold.fold);
        assertEquals(d.sections.get(0).blocks.get(1).words, fold.words);
        final ZoomData.Sentence pins = back.sections.get(0).blocks.get(0).sentences.get(1);
        assertEquals(3, pins.para);
        assertEquals(d.sections.get(0).blocks.get(0).sentences.get(1).words, pins.words);
        assertEquals(ZoomKind.SO_WHAT, back.claims.get(5).kind);
        assertEquals(d.claims.get(1).quote, back.claims.get(1).quote);
        assertEquals(d.leanChars(), back.leanChars());
        assertEquals(d.claimsChars(), back.claimsChars());
    }

    @Test
    public void whatTheGoalKeptAndSqueezedIsReadAndStored() throws Exception {
        final ZoomData d = ZoomData.parse("{\"claims\": [{\"text\": \"A claim.\"}], \"kept\": [\"The pin workshop\", "
                + "\" The three causes \", \"The pin workshop\", {\"name\": \"Workers inventing machines\"}, 7, \"\", \"a\", \"b\"], "
                + "\"squeezed\": [\"Farming vs factories\"]}");
        assertEquals(java.util.Arrays.asList("The pin workshop", "The three causes", "Workers inventing machines", "a"), d.kept);
        assertEquals(java.util.Collections.singletonList("Farming vs factories"), d.squeezed);
        d.goal = "work|running an ML team";
        final ZoomData back = ZoomData.fromJson(new JSONObject(d.toJson().toString()));
        assertEquals("work|running an ML team", back.goal);
        assertEquals(d.kept, back.kept);
        assertEquals(d.squeezed, back.squeezed);
        // levels made without a goal keep none
        final ZoomData plain = ZoomData.fromJson(new JSONObject(placed().toJson().toString()));
        assertEquals("", plain.goal);
        assertTrue(plain.kept.isEmpty());
        assertFalse(placed().toJson().has("goal"));
    }

    @Test
    public void nothingStoredIsNothing() throws Exception {
        assertNull(ZoomData.fromJson(null));
        assertNull(ZoomData.fromJson(new JSONObject("{\"t\": 5}")));
        final ZoomData whole = new ZoomData();
        whole.chars = 100;
        whole.fullChars = 100;
        assertFalse(whole.partial());
        assertEquals(100, whole.coveredPercent());
    }

    @Test
    public void aLongQuoteIsCutAtAWord() {
        assertEquals("one two", ZoomData.cut("one two three", 9));
        assertEquals("one two three", ZoomData.cut("one two three", 50));
        assertEquals("", ZoomData.cut(null, 5));
    }

    @Test
    public void kindsAreReadByNearWords() {
        assertEquals(ZoomKind.WHY, ZoomKind.of(" Reason "));
        assertEquals(ZoomKind.SO_WHAT, ZoomKind.of("so-what"));
        assertEquals(ZoomKind.SO_WHAT, ZoomKind.of("Implication"));
        assertEquals(ZoomKind.EVIDENCE, ZoomKind.of("example"));
        assertEquals(ZoomKind.CAVEAT, ZoomKind.of("limitation"));
        assertEquals(ZoomKind.CLAIM, ZoomKind.of("something else"));
        assertEquals(ZoomKind.CLAIM, ZoomKind.of(null));
    }
}
