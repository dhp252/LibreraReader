package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FictionChaptersTest {

    /** 37 chapters of 10 pages after 4 front pages, titled as Verne's are. */
    static FictionChapters verne() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        for (int i = 1; i <= 37; i++) {
            toc.add(new BookChapters.Entry("CHAPTER " + FictionChaptersTest.roman(i) + ". IN WHICH SOMETHING HAPPENS", 0, 4 + (i - 1) * 10));
        }
        return FictionChapters.of(toc, 374);
    }

    static String roman(int n) {
        final int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] marks = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                out.append(marks[i]);
                n -= values[i];
            }
        }
        return out.toString();
    }

    @Test
    public void numbersAreReadFromTitles() {
        assertEquals(14, FictionChapters.number("CHAPTER XIV. IN WHICH PHILEAS FOGG DESCENDS THE VALLEY"));
        assertEquals(14, FictionChapters.number("Chapter 14"));
        assertEquals(12, FictionChapters.number("Chapter Twelve"));
        assertEquals(21, FictionChapters.number("Chapter Twenty-One: The Vote"));
        assertEquals(3, FictionChapters.number("Chương 3: Ra đi"));
        assertEquals(7, FictionChapters.number("7. Of the natural price"));
        assertEquals(4, FictionChapters.number("IV"));
        assertEquals(5, FictionChapters.number("Ch. 5"));
        assertEquals(0, FictionChapters.number("I Am Legend"));
        assertEquals(0, FictionChapters.number("Chi"));
        assertEquals(0, FictionChapters.number("Chapters of my life"));
        assertEquals(0, FictionChapters.number("MIX: a story"));
        assertEquals(0, FictionChapters.number("The Boy Who Lived"));
        assertEquals(0, FictionChapters.number(null));
    }

    @Test
    public void romanNumeralsMustBeWrittenTheUsualWay() {
        assertEquals(14, FictionChapters.roman("XIV"));
        assertEquals(37, FictionChapters.roman("XXXVII"));
        assertEquals(0, FictionChapters.roman("IIII"));
        assertEquals(0, FictionChapters.roman("CIVIL"));
        assertEquals(0, FictionChapters.roman("xiv"));
    }

    @Test
    public void aNumberedBookShowsItsOwnChapterNumbers() {
        final FictionChapters c = verne();
        // the front pages are a chapter of their own, so the model's number is one more than the book's
        assertEquals(38, c.size());
        assertEquals("the opening pages", c.label(0));
        assertEquals("Ch. 1", c.label(1));
        assertEquals("Ch. 14", c.label(14));
        assertEquals(14, c.indexAt(4 + 13 * 10));
        assertEquals(14, c.indexAt(4 + 13 * 10 + 9));
        assertEquals(15, c.indexAt(4 + 14 * 10));
        assertEquals("Ch. 15–37", c.range(15, 37));
        assertEquals("", c.label(99));
        assertEquals(c.get(14).key, c.key(15));
        assertEquals("", c.key(0));
        assertEquals("", c.key(99));
    }

    @Test
    public void theShieldCountsTheChaptersThatStartAfterThePage() {
        final FictionChapters c = verne();
        // on a page of chapter 14: chapters 15 to 37 are hidden
        assertEquals(23, c.hiddenAfter(4 + 13 * 10 + 3));
        assertEquals(0, c.hiddenAfter(373));
        assertEquals(37, c.hiddenAfter(0));
    }

    @Test
    public void sideMatterIsNoHiddenChapter() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        toc.add(new BookChapters.Entry("Chapter 1", 0, 0));
        toc.add(new BookChapters.Entry("Chapter 2", 0, 10));
        toc.add(new BookChapters.Entry("Chapter 3", 0, 20));
        toc.add(new BookChapters.Entry("About the Author", 0, 30));
        final FictionChapters c = FictionChapters.of(toc, 32);
        assertEquals(2, c.hiddenAfter(5));
    }

    @Test
    public void aBookThatDoesNotNumberItsChaptersCountsThem() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        toc.add(new BookChapters.Entry("The Boy Who Lived", 0, 2));
        toc.add(new BookChapters.Entry("The Vanishing Glass", 0, 20));
        toc.add(new BookChapters.Entry("The Letters from No One", 0, 35));
        final FictionChapters c = FictionChapters.of(toc, 60);
        assertEquals("the opening pages", c.label(0));
        assertEquals("Ch. 1", c.label(1));
        assertEquals("Ch. 3", c.label(3));
        assertEquals("Ch. 2–3", c.range(2, 3));
    }

    @Test
    public void anUnnumberedChapterOfANumberedBookKeepsItsTitle() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        toc.add(new BookChapters.Entry("Prologue", 0, 0));
        toc.add(new BookChapters.Entry("Chapter 1", 0, 5));
        toc.add(new BookChapters.Entry("Chapter 2", 0, 15));
        toc.add(new BookChapters.Entry("Chapter 3", 0, 25));
        final FictionChapters c = FictionChapters.of(toc, 40);
        assertEquals("Prologue", c.label(0));
        assertEquals("Ch. 1", c.label(1));
        assertEquals("Prologue – Ch. 2", c.range(0, 2));
    }

    @Test
    public void partsAreCutIntoTheirChaptersAndNamedWhenNumbersRepeat() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        toc.add(new BookChapters.Entry("Book the First: Recalled to Life", 0, 0));
        toc.add(new BookChapters.Entry("Chapter I. The Period", 1, 1));
        toc.add(new BookChapters.Entry("Chapter II. The Mail", 1, 6));
        toc.add(new BookChapters.Entry("Book the Second: The Golden Thread", 0, 12));
        toc.add(new BookChapters.Entry("Chapter I. Five Years Later", 1, 13));
        toc.add(new BookChapters.Entry("Chapter II. A Sight", 1, 20));
        toc.add(new BookChapters.Entry("Chapter III. A Disappointment", 1, 26));
        final FictionChapters c = FictionChapters.of(toc, 40);
        assertEquals(5, c.size());
        // the part's title page goes with its first chapter
        assertEquals(0, c.get(0).from);
        assertEquals(6, c.get(0).to);
        assertEquals(12, c.get(2).from);
        assertEquals("Book the First, Ch. 1", c.label(0));
        assertEquals("Book the Second, Ch. 2", c.label(3));
        assertEquals("Ch. 3", c.label(4));
        assertEquals(2, c.indexAt(12));
        assertTrue(c.get(2).key.startsWith("book the second: the golden thread#0/"));
        // every page is in a chapter
        for (int p = 0; p < 40; p++) {
            assertTrue(c.indexAt(p) >= 0);
        }
    }

    @Test
    public void aBookWithoutContentsIsCutIntoPageBlocks() {
        final FictionChapters c = FictionChapters.of(null, 45);
        assertFalse(c.hasToc());
        assertEquals(3, c.size());
        assertEquals("p. 1–20", c.label(0));
        assertEquals("p. 41–45", c.label(2));
        assertEquals("p. 21–45", c.range(1, 2));
        assertEquals(1, c.indexAt(25));
        assertEquals(2, c.hiddenAfter(10));
    }

    @Test
    public void storedChaptersAreFoundByKeyThenByNumber() {
        final FictionChapters c = verne();
        assertEquals(14, c.indexOf(c.get(14).key, 3, 38));
        // an unknown key: by number
        assertEquals(11, c.indexOf("gone#0", 12, 38));
        assertEquals(-1, c.indexOf("", 0, 38));
        // another number of chapters (page blocks in another layout): scaled, rounded up
        final FictionChapters blocks = FictionChapters.of(null, 200);
        assertEquals(10, blocks.size());
        assertEquals(5, blocks.indexOf("@pages:190", 10, 20));
        assertEquals(9, blocks.indexOf("@pages:999", 40, 20));
    }
}
