package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;
import com.foobnix.study.StudyBook;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkeletonModelTest {

    private static List<SkeletonParts.Part> parts() {
        return SkeletonParts.group(SkeletonPartsTest.wealth(), 80);
    }

    /** 600 characters a page: 100 words a page. */
    private static int[] pages() {
        final int[] chars = new int[80];
        Arrays.fill(chars, 600);
        return chars;
    }

    private static SkeletonData fromAnswer() {
        final SkeletonPrompts.Answer a = new SkeletonPrompts.Answer();
        a.line = "Free exchange makes wealth grow.";
        a.paragraph = "Long version.";
        a.conclusion = 2;
        a.conclusionNote = "Smith saves his conclusion for Book II.";
        a.partLines.put(1, "How work is split");
        a.optionalParts.add(0);
        a.optionalParts.add(2);
        a.chapterLines.put(1, "Splitting work multiplies output");
        a.chapterLines.put(9, "out of range");
        final List<SkeletonParts.Part> parts = parts();
        return SkeletonData.fromAnswer(a, parts, SkeletonParts.chapters(parts), pages(), "model", 5L);
    }

    @Test
    public void answerNumbersBecomeKeys() {
        final SkeletonData d = fromAnswer();
        assertEquals("book ii#0", d.conclusion);
        assertEquals("How work is split", d.partLines.get("book i#0"));
        assertTrue(d.optional.contains("introduction#0"));
        assertFalse("the conclusion is never optional", d.optional.contains("book ii#0"));
        assertEquals("Splitting work multiplies output", d.chapterLines.get("book i#0/of the division of labour#0"));
        assertEquals(1, d.chapterLines.size());
        // pages 10-20: 6000 characters
        assertEquals(Long.valueOf(6000), d.chars.get("book i#0/of the division of labour#0"));
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final SkeletonData d = SkeletonData.fromJson(new JSONObject(fromAnswer().toJson().toString()));
        assertEquals("Free exchange makes wealth grow.", d.line);
        assertEquals("Long version.", d.paragraph);
        assertEquals("Smith saves his conclusion for Book II.", d.note);
        assertEquals("book ii#0", d.conclusion);
        assertEquals("How work is split", d.partLines.get("book i#0"));
        assertTrue(d.optional.contains("introduction#0"));
        assertEquals(Long.valueOf(6000), d.chars.get("book i#0/of the division of labour#0"));
        assertEquals("model", d.model);
        assertEquals(5L, d.made);
        assertTrue(SkeletonData.fromJson(null).isEmpty());
    }

    @Test
    public void modelOfTheMap() {
        // the reader is on page 25: Book I, its second chapter
        final SkeletonModel m = SkeletonModel.build(parts(), fromAnswer(), 25, 100);
        assertEquals(4, m.parts.size());
        assertTrue(m.grouped);
        assertEquals(2, m.conclusion);
        assertEquals("Smith saves his conclusion for Book II.", m.note);

        final SkeletonModel.PartView book1 = m.parts.get(1);
        assertEquals("Book I", book1.label);
        assertEquals("How work is split", book1.line);
        assertEquals(3, book1.chapterCount);
        assertTrue(book1.here);
        assertFalse(book1.chapters.get(0).here);
        assertTrue(book1.chapters.get(1).here);
        assertEquals(10, book1.from);

        final SkeletonModel.PartView intro = m.parts.get(0);
        assertEquals("a chapter standing alone counts no chapters", 0, intro.chapterCount);
        assertTrue(intro.optional);
        assertFalse(m.parts.get(2).optional);
        assertTrue(m.parts.get(2).conclusion);

        // 2 parts holding chapters; 7 chapters; pages 4-70 = 66 pages of 100 words at 100 wpm
        assertEquals(Arrays.asList("2 books", "7 chapters", "about 1 h in full"), m.totals);
    }

    @Test
    public void fromAStudyPreview() {
        final StudyBook b = new StudyBook();
        b.thesis = "Wealth is what people produce. Free trade makes it grow.";
        b.previewed = 1;
        final StudyBook.Chapter intro = b.chapterOrNew("introduction#0");
        intro.line = "Why wealth is not gold";
        intro.chars = 6000 * 6;
        // Book I studied chapter by chapter, Book II kept whole
        final StudyBook.Chapter first = b.chapterOrNew("book i#0/of the division of labour#0");
        first.line = "Splitting work";
        first.chars = 6000;
        final StudyBook.Chapter book2 = b.chapterOrNew("book ii#0");
        book2.line = "Capital puts people to work";
        book2.chars = 12000;

        final SkeletonData d = SkeletonData.fromStudy(b);
        assertTrue(d.fromStudy);
        assertEquals("Wealth is what people produce.", d.line);
        assertEquals(b.thesis, d.paragraph);

        final SkeletonModel m = SkeletonModel.build(parts(), d, 0, 100);
        assertEquals(-1, m.conclusion);
        assertEquals("", m.note);
        assertEquals("Why wealth is not gold", m.parts.get(0).line);
        assertEquals("Splitting work", m.parts.get(1).chapters.get(0).line);
        assertEquals("a part kept whole gives its line to the card", "Capital puts people to work", m.parts.get(2).line);
        for (SkeletonModel.PartView p : m.parts) {
            assertFalse(p.here);
        }
        // 36000 + 6000 + 12000 characters = 9000 words = 90 minutes
        assertEquals("about 1½ h in full", m.totals.get(2));
        assertTrue(SkeletonData.fromStudy(new StudyBook()).isEmpty());
    }

    @Test
    public void flatBookConclusionByChapter() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(Arrays.asList(
                new BookChapters.Entry("One", 0, 0), new BookChapters.Entry("Two", 0, 10),
                new BookChapters.Entry("Three", 0, 20)), 30);
        final SkeletonPrompts.Answer a = new SkeletonPrompts.Answer();
        a.line = "L";
        a.conclusion = 2;
        a.conclusionNote = "The end says it.";
        a.optionalChapters.add(1);
        a.chapterLines.put(0, "First line");
        final SkeletonData d = SkeletonData.fromAnswer(a, parts, SkeletonParts.chapters(parts), null, "", 0);
        final SkeletonModel m = SkeletonModel.build(parts, d, 12, 0);
        assertFalse(m.grouped);
        assertEquals(2, m.conclusion);
        assertTrue(m.parts.get(1).optional);
        assertTrue(m.parts.get(1).here);
        assertEquals("First line", m.parts.get(0).line);
        assertEquals("no lengths: no time", Arrays.asList("3 chapters"), m.totals);
    }
}
