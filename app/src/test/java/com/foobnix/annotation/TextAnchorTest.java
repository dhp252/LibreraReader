package com.foobnix.annotation;

import com.foobnix.ai.QuoteMatcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TextAnchorTest {

    /** A made-up text with "the" and "river" many times over, so short quotes repeat. */
    private static final List<String> BOOK = Arrays.asList((
            "The river ran past the mill and the mill stood by the river for a hundred years. "
                    + "Every spring the water rose and the miller watched the river from the door. "
                    + "In the third year of the drought the river fell so low that the wheel stopped. "
                    + "Nobody in the village remembered the wheel standing still before that summer. "
                    + "The miller's daughter walked to the source of the river to see what had happened there. "
                    + "She found the spring choked with stones that a landslide had carried down the hill. "
                    + "With the help of the shepherd she cleared the stones and the river ran again. "
                    + "When she came home the wheel was turning and the miller was singing at the door. "
                    + "From then on the village called the spring by her name and kept it clear of stones. "
                    + "The river still runs past the mill, though the wheel has long been taken down.")
            .split(" "));

    /** The book cut into pages of {@code perPage} words: a layout. */
    private static List<List<String>> layout(int perPage) {
        final List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < BOOK.size(); i += perPage) {
            pages.add(BOOK.subList(i, Math.min(BOOK.size(), i + perPage)));
        }
        return pages;
    }

    /** The anchor of book words {@code first..last} made in a layout, as the reader makes it. */
    private static Mark.Anchor anchor(List<List<String>> pages, int perPage, int first, int last) {
        final int page = first / perPage;
        final List<String> words = pages.get(page);
        final int lastPage = last / perPage;
        final List<String> after = pages.get(lastPage).subList(last % perPage + 1, pages.get(lastPage).size());
        return TextAnchor.of(words.subList(0, first % perPage), after, page, pages.size());
    }

    private static String quote(int first, int last) {
        return QuoteMatcher.join(BOOK.subList(first, last + 1));
    }

    private static TextAnchor.Pages pages(List<List<String>> layout) {
        return page -> new QuoteMatcher.Words(layout.get(page));
    }

    @Test
    public void keepsUpTo32LettersOfContextOnEachSide() {
        Mark.Anchor a = TextAnchor.of(Arrays.asList("Once,", "upon", "a", "time", "there", "was", "a", "very", "old", "king"),
                Arrays.asList("who", "ruled", "the", "land."), 3, 40);
        assertEquals("onceuponatimetherewasaveryoldking".substring(1), a.prefix);
        assertEquals("whoruledtheland", a.suffix);
        assertEquals(3, a.page);
        assertEquals(40, a.pageCount);
    }

    @Test
    public void findsAMarkAgainAfterTheBookIsLaidOutAnew() {
        // made with 20 words a page, read with 13
        List<List<String>> made = layout(20);
        List<List<String>> read = layout(13);
        Mark.Anchor a = anchor(made, 20, 45, 49);
        String key = TextAnchor.key(quote(45, 49));

        int page = TextAnchor.findPage(a, key, read.size(), pages(read));
        assertEquals(45 / 13, page);
        int[] range = TextAnchor.find(new QuoteMatcher.Words(read.get(page)), key, a);
        assertArrayEquals(new int[]{45 % 13, 49 % 13}, range);
    }

    @Test
    public void aShortQuoteIsToldApartFromItsTwinsByTheLettersAroundIt() {
        int the = BOOK.indexOf("drought") - 1;
        assertEquals("the", BOOK.get(the));
        List<List<String>> made = layout(20);
        List<List<String>> read = layout(17);
        Mark.Anchor a = anchor(made, 20, the, the);
        String key = TextAnchor.key("the");

        int page = TextAnchor.findPage(a, key, read.size(), pages(read));
        assertEquals(the / 17, page);
        assertArrayEquals(new int[]{the % 17, the % 17}, TextAnchor.find(new QuoteMatcher.Words(read.get(page)), key, a));
    }

    @Test
    public void aMarkCutByAPageEdgeIsFoundOnBothPages() {
        // read with 13 words a page: words 50..55 run from page 3 (39..51) onto page 4 (52..64)
        List<List<String>> made = layout(20);
        List<List<String>> read = layout(13);
        Mark.Anchor a = anchor(made, 20, 50, 55);
        String key = TextAnchor.key(quote(50, 55));

        assertArrayEquals(new int[]{11, 12}, TextAnchor.find(new QuoteMatcher.Words(read.get(3)), key, a));
        assertArrayEquals(new int[]{0, 3}, TextAnchor.find(new QuoteMatcher.Words(read.get(4)), key, a));
        // it starts on page 3, not on the page that holds its end
        assertEquals(3, TextAnchor.findPage(a, key, read.size(), pages(read)));
    }

    @Test
    public void wordsOfAnotherPageThatLookTheSameAreNotMarked() {
        List<List<String>> made = layout(20);
        Mark.Anchor a = anchor(made, 20, 45, 49);
        String key = TextAnchor.key(quote(45, 49));
        for (int page = 0; page < made.size(); page++) {
            if (page != 45 / 20) {
                assertNull("page " + page, TextAnchor.find(new QuoteMatcher.Words(made.get(page)), key, a));
            }
        }
    }

    @Test
    public void onlyPagesNearWhereTheMarkWasMadeAreSearched() {
        Mark.Anchor a = new Mark.Anchor("", "", 2, 10);
        assertTrue(TextAnchor.near(a, 2, 10));
        assertTrue(TextAnchor.near(a, 5, 10));
        assertFalse(TextAnchor.near(a, 9, 10));
        // twice as many pages now: the place moved with them
        assertTrue(TextAnchor.near(a, 4, 20));
        assertFalse(TextAnchor.near(a, 12, 20));
        assertEquals(4, TextAnchor.expectedPage(a, 20));
    }

    @Test
    public void aTinyQuoteWithoutContextAtAPageEdgeIsNotTrusted() {
        Mark.Anchor a = new Mark.Anchor("", "", 0, 1);
        assertNull(TextAnchor.find(new QuoteMatcher.Words(Arrays.asList("the", "mill", "stood", "by", "it")),
                TextAnchor.key("it"), a));
        // a quote long enough is evidence of its own
        assertArrayEquals(new int[]{1, 2}, TextAnchor.find(new QuoteMatcher.Words(Arrays.asList("the", "watermill", "stood")),
                TextAnchor.key("watermill stood"), a));
    }

    @Test
    public void partsOfWordsAreNotMarks() {
        Mark.Anchor a = new Mark.Anchor("", "", 0, 1);
        // "other" sits inside "brother" and "mother"
        assertNull(TextAnchor.find(new QuoteMatcher.Words(Arrays.asList("my", "brother", "and", "my", "mother", "smiled")),
                TextAnchor.key("other and my mother"), a));
    }
}
