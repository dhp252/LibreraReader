package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkeletonPartsTest {

    private static BookChapters.Entry e(String title, int level, int page) {
        return new BookChapters.Entry(title, level, page);
    }

    /** Contents, an introduction, two books with chapters, a conclusion and an index. */
    static List<BookChapters.Entry> wealth() {
        return Arrays.asList(
                e("Contents", 0, 2),
                e("Introduction", 0, 4),
                e("Book I", 0, 10),
                e("Of the Division of Labour", 1, 11),
                e("Of the Principle", 1, 20),
                e("Of Money", 1, 30),
                e("Book II", 0, 40),
                e("Of Stock", 1, 40),
                e("Of Capital", 1, 50),
                e("Conclusion", 0, 60),
                e("Index", 0, 70));
    }

    @Test
    public void partsHoldTheirChaptersAndSideMatterIsLeftOut() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(wealth(), 80);
        assertEquals(4, parts.size());
        assertEquals("Introduction", parts.get(0).range.title);
        assertFalse(parts.get(0).isGroup());
        assertEquals(1, parts.get(0).chapters.size());

        final SkeletonParts.Part book1 = parts.get(1);
        assertTrue(book1.isGroup());
        assertEquals(3, book1.chapters.size());
        // the part's title page goes with its first chapter
        assertEquals(10, book1.chapters.get(0).from);
        assertEquals(20, book1.chapters.get(0).to);
        assertEquals(40, book1.chapters.get(2).to);
        // study mode's keys of a part's chapters
        assertEquals("book i#0/of the division of labour#0", book1.chapters.get(0).key);

        assertEquals(2, parts.get(2).chapters.size());
        assertEquals("Conclusion", parts.get(3).range.title);
        assertEquals(70, parts.get(3).range.to);
        assertTrue(SkeletonParts.hasGroups(parts));
        assertEquals(1 + 3 + 2 + 1, SkeletonParts.chapters(parts).size());
    }

    @Test
    public void aFlatTocHasNoGroups() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(Arrays.asList(
                e("One", 0, 0), e("Two", 0, 10), e("Three", 0, 20)), 30);
        assertEquals(3, parts.size());
        assertFalse(SkeletonParts.hasGroups(parts));
    }

    @Test
    public void noTocGivesPageBlocks() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(null, 45);
        assertEquals(3, parts.size());
        assertEquals("@pages:20", parts.get(1).range.key);
    }

    @Test
    public void whereTheReaderIs() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(wealth(), 80);
        assertEquals(-1, SkeletonParts.partAt(parts, 0));
        assertEquals(1, SkeletonParts.partAt(parts, 25));
        assertEquals("Of the Principle", SkeletonParts.chapterAt(parts.get(1), 25).title);
        assertNull(SkeletonParts.chapterAt(parts.get(1), 45));
        assertEquals("the index is no part", -1, SkeletonParts.partAt(parts, 75));
    }
}
