package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PictureSourceTest {

    @Test
    public void findsAQuoteInTheChapterWithItsPage() {
        final PictureSource text = PictureSetTest.chapter();
        final PictureSource.Found found = text.locate("“the corn of poland, in the same degree of goodness”");
        assertNotNull(found);
        // as the book has it
        assertEquals("The corn of Poland, in the same degree of goodness,", found.text);
        assertEquals(5, found.page);
        assertNull(text.locate("The corn of Portugal, in the same degree of goodness"));
        assertNull(text.locate("too short"));
        assertNull(text.locate(null));
        assertNull(text.locate(""));
    }

    @Test
    public void aShortenedQuoteIsFoundByItsFirstPieceInTheText() {
        final PictureSource text = PictureSetTest.chapter();
        final PictureSource.Found found = text.locate("The corn of Poland, in the same degree of goodness… But the hardware"
                + " of England is beyond all comparison superior…");
        assertNotNull(found);
        assertEquals("The corn of Poland, in the same degree of goodness", found.text.replaceAll(",$", ""));
        // the first piece is not the book's, the second is
        final PictureSource.Found second = text.locate("Nothing of this kind stands in the chapter ... limited by the extent"
                + " of the market");
        assertNotNull(second);
        assertEquals("limited by the extent of the market.", second.text);
        assertNull(text.locate("Nothing of this kind stands there … nor does this other piece of it"));
    }

    @Test
    public void withIdeasOnlyTheirMarksAreTheBooksWords() {
        final PictureIdea first = new PictureIdea("Splitting work multiplies output 240 times", "main");
        first.add(PictureSetTest.WIRE, 4);
        first.add(PictureSetTest.PINS, 6);
        final PictureIdea second = new PictureIdea("Markets limit it", "caveat");
        second.add(PictureSetTest.MARKET, 9);
        final List<PictureIdea> ideas = Arrays.asList(first, second);
        final PictureSource source = PictureSource.ofIdeas(ideas, PicturePrompts.ideasBody(ideas));
        assertTrue(source.fromIdeas);
        assertEquals(6, source.locate("upwards of forty-eight thousand pins in a day").page);
        assertEquals(9, source.locate(PictureSetTest.MARKET).page);
        // an idea's own line is not the book's
        assertNull(source.locate("Splitting work multiplies output 240 times"));
        // two marks do not stand together in the book
        assertNull(source.locate("pins in a day. The division of labour is limited"));
        // but every number sent counts, the ideas' too
        assertTrue(source.numbers().contains("240"));
        assertTrue(source.numbers().contains("48000"));
        assertFalse(source.numbers().contains("18"));
    }

    @Test
    public void aPassageIsOneRunOnOnePage() {
        final PictureSource passage = PictureSource.ofPassage(PictureSetTest.WAGON, 12);
        assertFalse(passage.fromIdeas);
        assertEquals(12, passage.locate("carries about four ton weight of goods").page);
        assertTrue(passage.numbers().contains("200"));
        assertTrue(passage.numbers().contains("4"));
    }

    @Test
    public void quotationMarksAroundAQuoteAreDropped() {
        assertEquals("a quote", PictureSource.strip(" “a quote” "));
        assertEquals("it's \"so\" here", PictureSource.strip("\"it's \"so\" here\""));
        assertEquals("", PictureSource.strip("\"\""));
    }

    @Test
    public void anIdeaCarriesAFewCutMarks() {
        final PictureIdea idea = new PictureIdea("  An idea\n over two lines ", null);
        assertEquals("An idea over two lines", idea.text);
        assertEquals("", idea.kind);
        final StringBuilder longMark = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longMark.append("word ");
        }
        idea.add(longMark.toString(), 1);
        idea.add("", 2);
        idea.add("second", 3);
        idea.add("third", 4);
        idea.add("fourth", 5);
        assertEquals(PictureIdea.MAX_QUOTES, idea.quotes.size());
        assertTrue(idea.quotes.get(0).length() <= PictureIdea.QUOTE_CHARS);
        assertTrue(idea.quotes.get(0).endsWith("word"));
        assertEquals(Arrays.asList(1, 3, 4), idea.pages);
        assertEquals(idea.quotes.get(0).length() + 11, idea.markChars());
    }
}
