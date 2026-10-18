package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyCardsTest {

    /** Front matter page 0, then four chapters of 10 pages: c1 read, c2 skip, c3 skim, c4 read. */
    private final List<BookChapters.Range> chapters = Arrays.asList(
            new BookChapters.Range(0, 1, "", BookChapters.FRONT),
            new BookChapters.Range(1, 11, "One", "c1"),
            new BookChapters.Range(11, 21, "Two", "c2"),
            new BookChapters.Range(21, 31, "Three", "c3"),
            new BookChapters.Range(31, 41, "Four", "c4"));
    private StudyBook book;

    @Before
    public void studied() {
        book = new StudyBook();
        book.on = true;
        book.previewed = 1;
        book.chapterOrNew("c1").label = StudyBook.READ;
        book.chapterOrNew("c2").label = StudyBook.SKIP;
        book.chapterOrNew("c3").label = StudyBook.SKIM;
        book.chapterOrNew("c4").label = StudyBook.READ;
    }

    private String cards(int prev, int page, boolean jump) {
        return StudyCards.next(prev, page, jump, 2, chapters, book).toString();
    }

    @Test
    public void openingTheBookGivesTheBeforeCardOfItsChapter() {
        assertEquals("[before c1]", cards(-1, 3, false));
        assertEquals("front matter has none", "[]", cards(-1, 0, false));
    }

    @Test
    public void pagingOutOfAChapterGivesItsAfterCardThenTheNextBeforeCard() {
        assertEquals("[after c1, before c2]", cards(10, 11, false));
        assertEquals("within a chapter, nothing", "[]", cards(4, 5, false));
        // two pages per screen
        assertEquals("[after c1, before c2]", cards(9, 11, false));
    }

    @Test
    public void aSkipChapterHasNoAfterCard() {
        assertEquals("[before c3]", cards(20, 21, false));
    }

    @Test
    public void aJumpGivesOnlyTheBeforeCardOfWhereItLands() {
        assertEquals("[before c4]", cards(5, 33, false));     // more than a turn
        assertEquals("[before c4]", cards(30, 31, true));     // TOC, search, another device
    }

    @Test
    public void pagingBackGivesNothing() {
        assertEquals("[]", cards(11, 10, false));
        assertEquals("[]", cards(21, 10, false));
    }

    @Test
    public void everyCardComesOnce() {
        book.chapter("c1").afterShown = 5;
        book.chapter("c2").beforeShown = 5;
        assertEquals("[]", cards(10, 11, false));
        book.chapter("c4").checked = 9;
        assertEquals("a checked chapter has no cards", "[]", cards(30, 31, true));
        book.chapter("c3").checked = 9;
        assertEquals("[]", cards(30, 31, false));
    }

    @Test
    public void aBookNotStudiedHasNoCards() {
        book.on = false;
        assertEquals("[]", cards(10, 11, false));
        book.on = true;
        book.previewed = 0;
        assertEquals("[]", cards(10, 11, false));
    }

    @Test
    public void welcomeBackAfterThreeDaysAway() {
        final long day = 24L * 60 * 60 * 1000;
        assertTrue(StudyCards.welcomeBack(1000, 1000 + 3 * day));
        assertFalse(StudyCards.welcomeBack(1000, 1000 + 2 * day));
        assertFalse("never read", StudyCards.welcomeBack(0, 10 * day));
        assertEquals(5, StudyCards.daysAway(0, 5 * day + 1000));
    }
}
