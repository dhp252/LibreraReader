package com.foobnix.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuoteMatcherTest {

    private static List<String> words(String text) {
        return Arrays.asList(text.split(" "));
    }

    private static QuoteMatcher.Words page(String text) {
        return new QuoteMatcher.Words(words(text));
    }

    @Test
    public void locatesAQuoteDespitePunctuationAccentsAndHyphenatedLineEnds() {
        QuoteMatcher.Words chapter = page("Le soir tomba. « Il faut par-tir », dit-elle, et la mai- son resta vide. Fin.");
        // the model wrote the word whole and dropped the guillemets
        int[] range = QuoteMatcher.locate(chapter, "Il faut partir, dit-elle, et la maison resta vide.");
        assertArrayEquals(new int[]{4, 14}, range);
    }

    @Test
    public void locatesAQuoteWithAWordChangedInTheMiddle() {
        String text = "The committee met on Tuesday and after a long and heated debate it finally decided "
                + "that the old bridge would be rebuilt in stone before the winter floods arrived again.";
        QuoteMatcher.Words chapter = page(text);
        String changed = "after a long and heated debate it finally agreed "
                + "that the old bridge would be rebuilt in stone before the winter floods arrived again.";
        int[] range = QuoteMatcher.locate(chapter, changed);
        assertEquals("after", words(text).get(range[0]));
        assertEquals("again.", words(text).get(range[1]));
    }

    @Test
    public void aQuoteThatIsNotThereOrTooShortIsNotLocated() {
        QuoteMatcher.Words chapter = page("Nothing in this chapter talks about the weather at all, really.");
        assertNull(QuoteMatcher.locate(chapter, "The storm destroyed every house in the village."));
        assertNull(QuoteMatcher.locate(chapter, "at all"));
    }

    @Test
    public void findsEveryWholeQuoteOnAPage() {
        QuoteMatcher.Words p = page("Intro words here. The key point is stated plainly here. More text follows on.");
        List<int[]> ranges = QuoteMatcher.onPage(p,
                Collections.singletonList(QuoteMatcher.key("The key point is stated plainly here.")));
        assertEquals(1, ranges.size());
        assertArrayEquals(new int[]{3, 9}, ranges.get(0));
    }

    @Test
    public void findsAQuoteRunningOverThePageEnd() {
        String quote = "Every revolution begins with a quiet refusal to accept the way things are done.";
        // the page ends in the middle of the quote, then a running footer
        QuoteMatcher.Words p = page("Earlier text on the page. Every revolution begins with a quiet refusal to 213");
        List<int[]> ranges = QuoteMatcher.onPage(p, Collections.singletonList(QuoteMatcher.key(quote)));
        assertEquals(1, ranges.size());
        assertArrayEquals(new int[]{5, 12}, ranges.get(0));
    }

    @Test
    public void findsTheEndOfAQuoteAtThePageStart() {
        String quote = "Every revolution begins with a quiet refusal to accept the way things are done.";
        // a running head, then the rest of the quote
        QuoteMatcher.Words p = page("CHAPTER TWO accept the way things are done. The next sentence starts here.");
        List<int[]> ranges = QuoteMatcher.onPage(p, Collections.singletonList(QuoteMatcher.key(quote)));
        assertEquals(1, ranges.size());
        assertArrayEquals(new int[]{2, 7}, ranges.get(0));
    }

    @Test
    public void theStartOfAQuoteInTheMiddleOfAPageIsNotAPartialMatch() {
        String quote = "Every revolution begins with a quiet refusal to accept the way things are done.";
        StringBuilder text = new StringBuilder("Every revolution begins with a quiet refusal but that is another story.");
        for (int i = 0; i < 20; i++) {
            text.append(" Filler sentence number ").append(i).append(" keeps the page going.");
        }
        List<int[]> ranges = QuoteMatcher.onPage(page(text.toString()), Collections.singletonList(QuoteMatcher.key(quote)));
        assertTrue(ranges.isEmpty());
    }

    @Test
    public void locateAllKeepsTheBooksWordsOnceEach() {
        List<String> chapter = words("Intro. The mai- son was old and very quiet that night. Later on, nothing.");
        List<QuoteMatcher.Found> found = QuoteMatcher.locateAll(chapter, Arrays.asList(
                "The maison was old and very quiet that night.",
                "the MAISON was old, and very quiet that night",   // the same sentence again
                "A sentence the model made up entirely."));
        assertEquals(1, found.size());
        assertEquals("The maison was old and very quiet that night.", found.get(0).text);
        assertEquals(1, found.get(0).firstWord);
    }

    @Test
    public void joinPutsLineEndHyphensBackTogether() {
        assertEquals("the maison resta well-known", QuoteMatcher.join(Arrays.asList("the", "mai-", "son", "resta", "well-known")));
        assertEquals("pre- War", QuoteMatcher.join(Arrays.asList("pre-", "War")));
    }

    @Test
    public void locatesAParagraphByItsFirstAndLastWords() {
        // 0..2 before, 3..24 the paragraph, then the next one repeats its last words
        QuoteMatcher.Words chapter = page("End of before. Habits are the compound interest of self-improvement, "
                + "and small changes add up over years until they become remarkable results in the end. "
                + "Next paragraph: they become remarkable results in the end. again.");
        int[] span = QuoteMatcher.locateSpan(chapter, "Habits are the compound interest", "they become remarkable results in the end.");
        assertArrayEquals(new int[]{3, 24}, span);
    }

    @Test
    public void aShortParagraphMayHaveItsEndInsideItsStart() {
        QuoteMatcher.Words chapter = page("Before. A single short paragraph of only twelve words is here now. After it.");
        int[] span = QuoteMatcher.locateSpan(chapter, "A single short paragraph of only", "of only twelve words is here now.");
        assertArrayEquals(new int[]{1, 11}, span);
    }

    @Test
    public void aParagraphWithoutItsEndRunsTheFallbackLength() {
        StringBuilder text = new StringBuilder("The paragraph starts with these exact words");
        for (int i = 0; i < 100; i++) {
            text.append(" w").append(i);
        }
        QuoteMatcher.Words chapter = page(text.toString());
        int[] span = QuoteMatcher.locateSpan(chapter, "The paragraph starts with these exact words", "words the model made up at the end");
        assertArrayEquals(new int[]{0, QuoteMatcher.SPAN_FALLBACK_WORDS - 1}, span);
        assertNull(QuoteMatcher.locateSpan(chapter, "A start that is nowhere in the text", "w99 w99"));
    }
}
