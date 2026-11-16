package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkeletonPromptsTest {

    @Test
    public void systemAsksPartsOnlyWhenTheBookHasThem() {
        final String grouped = SkeletonPrompts.system("", true);
        assertTrue(grouped.contains("the n of the part"));
        assertTrue(grouped.contains("the language the book is written in"));
        final String flat = SkeletonPrompts.system("English", false);
        assertTrue(flat.contains("an empty list"));
        assertTrue(flat.contains("the n of the chapter"));
        assertTrue(flat.contains("Write in English."));
    }

    @Test
    public void userListsThePartsWithTheirChapterNumbers() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(SkeletonPartsTest.wealth(), 80);
        final List<BookChapters.Range> chapters = SkeletonParts.chapters(parts);
        final String user = SkeletonPrompts.user("The Wealth of Nations", parts, chapters, "<chapter n=\"0\"/>\n");
        assertTrue(user.startsWith("Book: The Wealth of Nations"));
        assertTrue(user, user.contains("<part n=\"0\" title=\"Introduction\" chapters=\"0\"/>"));
        assertTrue(user, user.contains("<part n=\"1\" title=\"Book I\" chapters=\"1-3\"/>"));
        assertTrue(user, user.contains("<part n=\"2\" title=\"Book II\" chapters=\"4-5\"/>"));
        assertTrue(user, user.contains("<part n=\"3\" title=\"Conclusion\" chapters=\"6\"/>"));
        assertTrue(user.endsWith("<book>\n<chapter n=\"0\"/>\n</book>"));
    }

    @Test
    public void userWithoutPartsHasNoPartsBlock() {
        final List<SkeletonParts.Part> parts = SkeletonParts.group(java.util.Arrays.asList(
                new BookChapters.Entry("One", 0, 0), new BookChapters.Entry("Two", 0, 5)), 10);
        assertFalse(SkeletonPrompts.user("", parts, SkeletonParts.chapters(parts), "").contains("<parts>"));
    }

    @Test
    public void parsesAWholeAnswerInAFence() {
        final SkeletonPrompts.Answer a = SkeletonPrompts.parse("```json\n{\"line\": \"Free exchange makes wealth grow.\","
                + " \"paragraph\": \"A nation's wealth is what it produces. Trade helps.\","
                + " \"parts\": [{\"n\": 0, \"line\": \"How work is split\", \"optional\": false},"
                + " {\"n\": 2, \"line\": \"How Europe grew rich\", \"optional\": true}],"
                + " \"conclusion\": 3, \"conclusion_note\": \"Smith saves his conclusion for Book IV.\","
                + " \"chapters\": [{\"n\": 0, \"line\": \"Splitting work multiplies output\"}, {\"n\": 1, \"line\": \"\"}]}\n```");
        assertEquals("Free exchange makes wealth grow.", a.line);
        assertEquals("A nation's wealth is what it produces. Trade helps.", a.paragraph);
        assertEquals(3, a.conclusion);
        assertEquals("Smith saves his conclusion for Book IV.", a.conclusionNote);
        assertEquals("How work is split", a.partLines.get(0));
        assertTrue(a.optionalParts.contains(2));
        assertFalse(a.optionalParts.contains(0));
        assertEquals("Splitting work multiplies output", a.chapterLines.get(0));
        assertFalse("an empty line is no line", a.chapterLines.containsKey(1));
        assertFalse(a.isEmpty());
    }

    @Test
    public void readsAnAnswerCutOff() {
        final SkeletonPrompts.Answer a = SkeletonPrompts.parse("{\"line\": \"Trade \\\"helps\\\".\", \"paragraph\": \"P.\","
                + " \"parts\": [{\"n\": 1, \"line\": \"Capital\", \"optional\": true}], \"conclusion\": 1,"
                + " \"conclusion_note\": \"Book II.\", \"chapters\": [{\"n\": 0, \"line\": \"First\"}, {\"n\": 1, \"line\": \"Sec");
        assertEquals("Trade \"helps\".", a.line);
        assertEquals("P.", a.paragraph);
        assertEquals(1, a.conclusion);
        assertEquals("Capital", a.partLines.get(1));
        assertTrue(a.optionalParts.contains(1));
        assertEquals("First", a.chapterLines.get(0));
        assertEquals("parts are not taken for chapters", 1, a.chapterLines.size());
    }

    @Test
    public void nothingReadable() {
        assertTrue(SkeletonPrompts.parse("Sorry, I cannot.").isEmpty());
        assertTrue(SkeletonPrompts.parse(null).isEmpty());
    }
}
