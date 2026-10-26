package com.foobnix.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BookChaptersTest {

    private static BookChapters.Entry e(String title, int level, int page) {
        return new BookChapters.Entry(title, level, page);
    }

    @Test
    public void chaptersComeFromTheTopLevel() {
        List<BookChapters.Entry> toc = Arrays.asList(
                e("One", 0, 2), e("1.1", 1, 3), e("Two", 0, 10), e("Three", 0, 20));
        List<BookChapters.Range> chapters = BookChapters.chapters(toc, 30);

        assertEquals(4, chapters.size());
        assertEquals(0, chapters.get(0).from);   // front matter before chapter One
        assertEquals(2, chapters.get(0).to);
        assertEquals("One", chapters.get(1).title);
        assertEquals(10, chapters.get(1).to);
        assertEquals("Three", chapters.get(3).title);
        assertEquals(30, chapters.get(3).to);
    }

    @Test
    public void aSingleTopEntryMeansChaptersAreOneLevelDown() {
        List<BookChapters.Entry> toc = Arrays.asList(
                e("The Book", 0, 0), e("Chapter 1", 1, 1), e("Chapter 2", 1, 8), e("2.1", 2, 9));
        List<BookChapters.Range> chapters = BookChapters.chapters(toc, 15);

        // "The Book" and "Chapter 1"... are both levels <= 1; the title entry starts at page 0
        assertEquals("The Book", chapters.get(0).title);
        assertEquals(1, chapters.get(0).to);
        assertEquals("Chapter 2", chapters.get(2).title);
        assertEquals(15, chapters.get(2).to);
    }

    @Test
    public void chapterAtFindsThePageOrFallsBackToAPageBlock() {
        List<BookChapters.Entry> toc = Arrays.asList(e("A", 0, 0), e("B", 0, 5), e("C", 0, 12));
        BookChapters.Range b = BookChapters.chapterAt(toc, 7, 20, 10);
        assertEquals("B", b.title);
        assertEquals(5, b.from);
        assertEquals(12, b.to);

        // the same block from any of its pages, so what is stored for it is found again
        BookChapters.Range none = BookChapters.chapterAt(Collections.emptyList(), 15, 20, 10);
        assertEquals(10, none.from);
        assertEquals(20, none.to);
        assertEquals(none.key, BookChapters.chapterAt(Collections.emptyList(), 11, 20, 10).key);
    }

    @Test
    public void keysNameChaptersByTitleNotByPage() {
        List<BookChapters.Entry> toc = Arrays.asList(
                e("Notes", 0, 3), e("Part  One", 0, 5), e("notes", 0, 9));
        List<BookChapters.Range> chapters = BookChapters.chapters(toc, 12);

        assertEquals(BookChapters.FRONT, chapters.get(0).key);
        assertEquals("notes#0", chapters.get(1).key);
        assertEquals("part one#0", chapters.get(2).key);
        assertEquals("notes#1", chapters.get(3).key);

        // another layout moves the pages, not the keys
        List<BookChapters.Range> bigger = BookChapters.chapters(Arrays.asList(
                e("Notes", 0, 6), e("Part One", 0, 10), e("Notes", 0, 18)), 25);
        assertEquals(chapters.get(3).key, bigger.get(3).key);
    }

    @Test
    public void entriesOutsideTheBookAreIgnored() {
        List<BookChapters.Entry> toc = Arrays.asList(e("Bad", 0, -1), e("A", 0, 0), e("Far", 0, 99));
        assertEquals(1, BookChapters.chapters(toc, 10).size());
    }

    @Test
    public void partsJoinShortChaptersAndCutLongOnes() {
        List<BookChapters.Range> chapters = Arrays.asList(
                new BookChapters.Range(0, 2, "A"),   // 200 chars
                new BookChapters.Range(2, 4, "B"),   // 200 chars
                new BookChapters.Range(4, 10, "C")); // 6 pages x 100 = 600 chars
        int[] pageLength = {100, 100, 100, 100, 100, 100, 100, 100, 100, 100};

        List<BookChapters.Range> parts = BookChapters.parts(chapters, pageLength, 450);

        assertEquals(3, parts.size());
        assertEquals(0, parts.get(0).from);      // A + B joined: 400 <= 450
        assertEquals(4, parts.get(0).to);
        assertEquals(4, parts.get(1).from);      // C cut after 4 pages (400 chars)
        assertEquals(8, parts.get(1).to);
        assertEquals(8, parts.get(2).from);
        assertEquals(10, parts.get(2).to);
    }

    @Test
    public void partsWithoutTextAreLeftOut() {
        List<BookChapters.Range> chapters = Arrays.asList(
                new BookChapters.Range(0, 4, "A"),       // 400 chars, cut
                new BookChapters.Range(4, 6, "Index"),   // left out: no text
                new BookChapters.Range(6, 10, "C"));     // 400 chars, cut
        int[] pageLength = {100, 100, 100, 100, 0, 0, 100, 100, 100, 100};

        List<BookChapters.Range> parts = BookChapters.parts(chapters, pageLength, 300);

        for (BookChapters.Range part : parts) {
            assertTrue(part.from + "-" + part.to, BookChapters.length(pageLength, part.from, part.to) > 0);
        }
        assertEquals(4, parts.size());   // A in 2, C in 2
    }
}
