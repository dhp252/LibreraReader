package com.foobnix.assist.zoom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ZoomTextTest {

    @Test
    public void aParagraphOverAPageEdgeStaysOne() {
        final ZoomText t = ZoomSample.text();
        assertEquals(6, t.paragraphCount());
        assertEquals(3, t.pageCount());
        assertTrue(t.plain().contains("could make among them upwards of forty-eight thousand"));
        assertEquals(5, t.plain().split("\n\n").length - 1);
    }

    @Test
    public void aPageThatEndsASentenceStartsANewParagraph() {
        final ZoomText t = ZoomText.of(Arrays.asList("One sentence ends here.", "Another starts on the next page."));
        assertEquals(2, t.paragraphCount());
        final ZoomText quoted = ZoomText.of(Arrays.asList("He said \"it ends here.\"", "Then a new one."));
        assertEquals(2, quoted.paragraphCount());
    }

    @Test
    public void emptyPagesAndNoPagesGiveNoText() {
        assertEquals(0, ZoomText.of(null).paragraphCount());
        final ZoomText t = ZoomText.of(Arrays.asList("", null, "  \n\n  "));
        assertEquals(0, t.paragraphCount());
        assertEquals(0, t.chars());
        assertEquals("", t.plain());
        assertNull(t.locate("anything long enough to be looked for"));
    }

    @Test
    public void aQuoteIsFoundWithItsParagraphAndPage() {
        final ZoomText t = ZoomSample.text();
        final ZoomText.Place p = t.locate("The nature of agriculture, indeed, does not admit");
        assertNotNull(p);
        assertEquals(4, p.para);
        assertEquals(1, p.page);
        assertEquals("The nature of agriculture, indeed, does not admit", t.text(p));
        // punctuation and case of the model do not matter; the book's words are what comes back
        final ZoomText.Place loose = t.locate("the nature of agriculture indeed does NOT admit");
        assertNotNull(loose);
        assertEquals(t.text(p), t.text(loose));
        assertNull(t.locate("words that are not in the chapter anywhere"));
        assertNull(t.locate("too short"));
        assertNull(t.locate(null));
    }

    @Test
    public void aPassageIsFoundByItsFirstAndLastWords() {
        final ZoomText t = ZoomSample.text();
        final ZoomText.Place p = t.locateSpan("I have seen a small manufactory of this kind", "thousand pins in a day.");
        assertNotNull(p);
        assertEquals(3, p.para);
        assertEquals(3, p.paraTo);
        assertEquals(0, p.page);
        assertEquals(ZoomSample.P3A + " " + ZoomSample.P3B, t.text(p));
        // without last words: where the first words are
        final ZoomText.Place head = t.locateSpan("I have seen a small manufactory of this kind", "");
        assertEquals(9, head.words());
    }

    @Test
    public void aStoredBeginningAndWordCountFindThePassageAgain() {
        final ZoomText t = ZoomSample.text();
        final ZoomText.Place p = t.locateSpan("I have seen a small manufactory of this kind", "thousand pins in a day.");
        final String head = t.head(p);
        assertTrue(head.startsWith("I have seen a small manufactory"));
        assertTrue(head.length() < t.text(p).length());
        // another layout: other page breaks, the same words
        final ZoomText other = ZoomText.of(Collections.singletonList(String.join("\n\n", ZoomSample.pages())));
        final ZoomText.Place again = other.locateFrom(head, p.words());
        assertNotNull(again);
        assertEquals(t.text(p), other.text(again));
        assertNull(other.locateFrom("words that are not in the chapter anywhere", 10));
    }

    @Test
    public void theBeginningOfAShortPlaceIsThePlace() {
        final ZoomText t = ZoomSample.text();
        final ZoomText.Place p = t.locate("the trade of the pin-maker");
        assertEquals("the trade of the pin-maker;", t.head(p));
    }

    @Test
    public void aLongPassageIsCutAtAWord() {
        final StringBuilder long1 = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            long1.append("word").append(i).append(' ');
        }
        final ZoomText t = ZoomText.of(Collections.singletonList(long1.toString()));
        final ZoomText.Place p = t.locateFrom("word0 word1 word2 word3 word4", 400);
        assertEquals(400, p.words());
        final String s = t.passage(p);
        assertTrue(s.length() <= ZoomText.PASSAGE_CHARS + 1);
        assertTrue(s.endsWith("…"));
        assertTrue(s.matches("(?s).*word\\d+…"));
    }
}
