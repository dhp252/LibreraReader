package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyChaptersTest {

    private static BookChapters.Entry e(String title, int level, int page) {
        return new BookChapters.Entry(title, level, page);
    }

    /** Every page holds {@code chars} characters. */
    private static int[] pages(int count, int chars) {
        final int[] p = new int[count];
        Arrays.fill(p, chars);
        return p;
    }

    /** Three Parts of 30 pages, each with three chapters of 10 pages, the Part title page alone. */
    private static List<BookChapters.Entry> threeParts() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        for (int part = 0; part < 3; part++) {
            final int start = 1 + part * 30;
            toc.add(e("Part " + (part + 1), 0, start));
            for (int ch = 0; ch < 3; ch++) {
                toc.add(e("Chapter " + (part * 3 + ch + 1), 1, start + 1 + ch * 10 - (ch == 0 ? 0 : 1)));
            }
        }
        return toc;
    }

    @Test
    public void longPartsAreFoundByTheirText() {
        // 91 pages of 3000 characters: each Part holds 90,000, over LONG_CHARS
        final Set<String> split = StudyChapters.longParts(threeParts(), 91, pages(91, 3000));
        assertEquals(new HashSet<>(Arrays.asList("part 1#0", "part 2#0", "part 3#0")), split);

        // short pages: the Parts stay whole
        assertTrue(StudyChapters.longParts(threeParts(), 91, pages(91, 500)).isEmpty());
    }

    @Test
    public void aSplitPartBecomesItsChaptersWithTheTitlePageInTheFirst() {
        final List<BookChapters.Entry> toc = threeParts();
        final Set<String> split = StudyChapters.longParts(toc, 91, pages(91, 3000));
        final List<BookChapters.Range> chapters = StudyChapters.cut(toc, 91, split);

        // front (page 0) + 9 chapters
        assertEquals(10, chapters.size());
        assertTrue(StudyChapters.isOutside(chapters.get(0)));
        assertEquals("Chapter 1", chapters.get(1).title);
        assertEquals(1, chapters.get(1).from);          // the "Part 1" title page joins chapter 1
        assertEquals(chapters.get(2).from, chapters.get(1).to);
        assertEquals("Chapter 9", chapters.get(9).title);
        assertEquals(91, chapters.get(9).to);
        for (int i = 1; i < chapters.size(); i++) {
            assertEquals("ranges follow each other", chapters.get(i - 1).to, chapters.get(i).from);
        }
    }

    @Test
    public void onlyTheLongPartIsSplit() {
        final List<BookChapters.Entry> toc = threeParts();
        final int[] chars = pages(91, 500);
        for (int p = 31; p < 61; p++) {
            chars[p] = 3000;   // Part 2 only
        }
        final Set<String> split = StudyChapters.longParts(toc, 91, chars);
        assertEquals(Collections.singleton("part 2#0"), split);

        final List<BookChapters.Range> chapters = StudyChapters.cut(toc, 91, split);
        final List<String> titles = new ArrayList<>();
        for (BookChapters.Range r : chapters) {
            titles.add(r.title);
        }
        assertEquals(Arrays.asList("", "Part 1", "Chapter 4", "Chapter 5", "Chapter 6", "Part 3"), titles);
    }

    @Test
    public void keysAreUniqueAndTheSameEveryTime() {
        final List<BookChapters.Entry> toc = Arrays.asList(
                e("Part", 0, 0), e("Notes", 1, 0), e("Body", 1, 5),
                e("Part", 0, 10), e("Notes", 1, 10), e("Body", 1, 15));
        final Set<String> split = new HashSet<>(Arrays.asList("part#0", "part#1"));
        final List<BookChapters.Range> a = StudyChapters.cut(toc, 20, split);
        final List<BookChapters.Range> b = StudyChapters.cut(toc, 20, split);

        final Set<String> keys = new HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            assertTrue("unique " + a.get(i).key, keys.add(a.get(i).key));
            assertEquals(a.get(i).key, b.get(i).key);
        }
        // a chapter of a split Part is named under it; a Part kept whole keeps its key of
        // BookChapters, so its ✦ summary is the same one
        assertEquals(4, a.size());
        assertEquals("part#0/notes#0", a.get(0).key);
        assertEquals("part#1/notes#0", a.get(2).key);
        final List<BookChapters.Range> whole = StudyChapters.cut(toc, 20, Collections.singleton("part#0"));
        assertEquals("part#1", whole.get(2).key);
    }

    @Test
    public void withoutATocTheBookIsInBlocks() {
        final List<BookChapters.Range> chapters = StudyChapters.cut(Collections.<BookChapters.Entry>emptyList(), 45,
                Collections.<String>emptySet());
        assertEquals(3, chapters.size());
        assertEquals(40, chapters.get(2).from);
        assertFalse(StudyChapters.isOutside(chapters.get(0)));
        assertTrue(StudyChapters.longParts(Collections.<BookChapters.Entry>emptyList(), 45, pages(45, 9000)).isEmpty());
    }

    @Test
    public void aPartWithoutChaptersInsideStaysWhole() {
        final List<BookChapters.Entry> toc = Arrays.asList(e("One", 0, 0), e("Two", 0, 40));
        assertTrue(StudyChapters.longParts(toc, 80, pages(80, 3000)).isEmpty());
        assertEquals(2, StudyChapters.cut(toc, 80, Collections.singleton("one#0")).size());
    }

    @Test
    public void atFindsTheChapterOfAPage() {
        final List<BookChapters.Range> chapters = StudyChapters.cut(threeParts(), 91,
                StudyChapters.longParts(threeParts(), 91, pages(91, 3000)));
        assertEquals("Chapter 2", StudyChapters.at(chapters, 15).title);
        assertEquals(0, StudyChapters.indexAt(chapters, 0));
        assertEquals(chapters.size() - 1, StudyChapters.indexAt(chapters, 500));
        assertEquals(-1, StudyChapters.indexAt(Collections.<BookChapters.Range>emptyList(), 3));
    }

    @Test
    public void sideMatterIsNotStudied() {
        assertTrue(StudyChapters.isOutside(new BookChapters.Range(80, 90, "Index", "index#0")));
        assertTrue(StudyChapters.isOutside(new BookChapters.Range(0, 2, "About the Author", "about the author#0")));
        assertTrue(StudyChapters.isOutside(new BookChapters.Range(80, 90, "", StudyChapters.SIDE)));
        assertFalse(StudyChapters.isOutside(new BookChapters.Range(80, 90, "Notes", "notes#0")));
        assertFalse(StudyChapters.isOutside(null));
    }

    @Test
    public void pagesNoPreviewedChapterHoldsAreSide() {
        final List<BookChapters.Range> chapters = StudyChapters.withGaps(Arrays.asList(
                new BookChapters.Range(0, 3, "", BookChapters.FRONT),
                new BookChapters.Range(5, 20, "One", "one#0"),
                new BookChapters.Range(20, 40, "Two", "two#0")), 50);
        assertEquals(5, chapters.size());
        assertEquals(StudyChapters.SIDE, chapters.get(1).key);   // contents, pages 3-4
        assertEquals(3, chapters.get(1).from);
        assertEquals(5, chapters.get(1).to);
        assertEquals(StudyChapters.SIDE, chapters.get(4).key);   // index, pages 40-49
        assertEquals(40, chapters.get(4).from);
        assertEquals(50, chapters.get(4).to);
        assertEquals("two#0", StudyChapters.at(chapters, 30).key);
        assertTrue(StudyChapters.isOutside(StudyChapters.at(chapters, 45)));
    }
}
