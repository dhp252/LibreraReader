package com.foobnix.assist.path;

import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.study.StudyBook;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathChaptersTest {

    @Test
    public void chaptersInBookOrderWithLinesCodesAndPages() {
        final List<PathChapters.Entry> list = PathFixture.list();
        assertEquals(7, list.size());
        assertEquals(PathFixture.INTRO, list.get(0).key);
        assertEquals("", list.get(0).partTitle);
        assertEquals("", list.get(0).code);

        final PathChapters.Entry price = list.get(3);
        assertEquals(PathFixture.PRICE, price.key);
        assertEquals("the title stays as the book gives it", "Chapter VII: Of the Natural and Market Price", price.title);
        assertEquals("Book I", price.partTitle);
        assertEquals("I.7", price.code);
        assertEquals("Prices pull supply towards demand", price.line);
        assertEquals(6000, price.chars);
        assertEquals(30, price.from);
        assertEquals(40, price.to);
        assertEquals("II.2", list.get(5).code);
        assertTrue(PathChapters.hasLines(list));
    }

    @Test
    public void aSkeletonOfAnotherBookGivesNoLines() {
        final SkeletonData other = new SkeletonData();
        other.chapterLines.put("some other chapter#0", "A line");
        final List<PathChapters.Entry> list = PathChapters.list(PathFixture.parts(), other);
        assertEquals(7, list.size());
        assertFalse(PathChapters.hasLines(list));
        assertFalse(PathChapters.hasLines(PathChapters.list(null, other)));
    }

    @Test
    public void aPartThePreviewKeptWholeIsOneChapter() {
        // a study preview that did not split Book II: one line and one length under the part's key
        final StudyBook study = new StudyBook();
        study.previewed = 5;
        study.thesis = "Free exchange makes wealth grow.";
        final StudyBook.Chapter whole = new StudyBook.Chapter("book ii#0");
        whole.line = "Capital: how savings put people to work";
        whole.chars = 18000;
        study.chapters.put(whole.key, whole);
        final StudyBook.Chapter labour = new StudyBook.Chapter(PathFixture.LABOUR);
        labour.line = "Splitting work multiplies output";
        labour.chars = 6000;
        study.chapters.put(labour.key, labour);

        final List<PathChapters.Entry> list = PathChapters.list(PathFixture.parts(), SkeletonData.fromStudy(study));
        // intro, three chapters of Book I, Book II whole, conclusion
        assertEquals(6, list.size());
        final PathChapters.Entry book2 = list.get(4);
        assertEquals("book ii#0", book2.key);
        assertEquals("Book II", book2.title);
        assertEquals("Capital: how savings put people to work", book2.line);
        assertEquals(18000, book2.chars);
        assertEquals(40, book2.from);
        assertEquals(60, book2.to);
        assertEquals("Splitting work multiplies output", list.get(1).line);
        assertEquals("a chapter without a line stays in the list", "", list.get(2).line);
    }

    @Test
    public void rangesHoldPartsAndChapters() {
        final Map<String, int[]> ranges = PathChapters.ranges(PathFixture.parts());
        assertArrayEquals(new int[]{10, 40}, ranges.get("book i#0"));
        assertArrayEquals(new int[]{10, 20}, ranges.get(PathFixture.LABOUR));
        assertArrayEquals(new int[]{50, 60}, ranges.get(PathFixture.HAND));
        assertArrayEquals(new int[]{60, 70}, ranges.get(PathFixture.CONCLUSION));
        assertTrue(PathChapters.ranges(null).isEmpty());
    }
}
