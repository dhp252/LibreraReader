package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StudyJobsTest {

    private final List<BookChapters.Range> chapters = Arrays.asList(
            new BookChapters.Range(0, 1, "", BookChapters.FRONT),
            new BookChapters.Range(1, 3, "One", "one#0"),
            new BookChapters.Range(3, 5, "Two", "two#0"));
    private final int[] lengths = {100, 600, 600, 1200, 1200};

    private static StudyPrompts.Preview preview(String label1, String label2, String q) {
        return StudyPrompts.parsePreview("{\"thesis\": \"T\", \"chapters\": ["
                + "{\"n\": 1, \"line\": \"L1\", \"label\": \"" + label1 + "\", \"reason\": \"r1\", \"questions\": [\"" + q + "1a\", \"" + q + "1b\"]},"
                + "{\"n\": 2, \"line\": \"L2\", \"label\": \"" + label2 + "\", \"reason\": \"r2\", \"questions\": [\"" + q + "2a\", \"" + q + "2b\"]}]}");
    }

    @Test
    public void aPreviewBecomesTheBooksChapters() {
        final StudyBook b = new StudyBook();
        StudyJobs.apply(b, preview("read", "skip", "q"), chapters, lengths, Collections.singleton("x#0"), "goal", "m");
        assertTrue(b.on);
        assertTrue(b.hasPreview());
        assertEquals("T", b.thesis);
        assertEquals("goal", b.goal);
        assertTrue(b.split.contains("x#0"));
        assertNull("front matter is not a chapter", b.chapter(BookChapters.FRONT));
        final StudyBook.Chapter two = b.chapter("two#0");
        assertEquals(StudyBook.SKIP, two.label);
        assertEquals(2400, two.chars);
        assertEquals(3, two.from);
        assertEquals(5, two.to);
        assertEquals(Arrays.asList("q2a", "q2b"), two.questions);
    }

    @Test
    public void quotesOfACheckAreFoundInTheChapterWithTheirPage() {
        final List<String> words = Arrays.asList("The", "price", "is", "set", "by", "value.", "Buyers", "compare,",
                "always.", "Anchors", "fail", "often.");
        final List<Integer> pages = Arrays.asList(4, 4, 4, 4, 4, 4, 5, 5, 5, 6, 6, 6);
        final StudyPrompts.Check check = StudyPrompts.parseCheck("## Feedback\n1. right - yes\n> \"Buyers compare, always.\"\n"
                + "2. missed - no\n> \"Something never said.\"\n## Map\n```json\n"
                + "[{\"text\": \"Value\", \"cat\": \"main\", \"quote\": \"the price is set by value\","
                + " \"kids\": [{\"text\": \"Anchors\", \"cat\": \"caveat\", \"quote\": \"Anchors fail often.\"}]}]\n```");
        StudyJobs.place(check, words, pages);
        assertEquals(5, check.items.get(0).page);
        assertEquals("Buyers compare, always.", check.items.get(0).quote);
        assertEquals("not in the chapter: no page", -1, check.items.get(1).page);
        assertEquals(4, check.branch.get(0).page);
        assertEquals("the book's own words", "The price is set by value.", check.branch.get(0).quote);
        assertEquals(6, check.branch.get(0).kids.get(0).page);
    }

    @Test
    public void aNewPreviewKeepsTheReadersLabelsAndAnsweredQuestions() {
        final StudyBook b = new StudyBook();
        StudyJobs.apply(b, preview("read", "read", "q"), chapters, lengths, Collections.<String>emptySet(), "", "m");
        b.chapter("one#0").label = StudyBook.SKIM;
        b.chapter("one#0").labelMine = true;
        b.chapter("two#0").answers.add("my answer");
        b.chapterOrNew("gone#0").title = "Not in the book any more";

        StudyJobs.apply(b, preview("skip", "skim", "new"), chapters, lengths, Collections.<String>emptySet(), "new goal", "m");
        assertEquals("the reader's label stays", StudyBook.SKIM, b.chapter("one#0").label);
        assertEquals("unanswered questions are new", Arrays.asList("new1a", "new1b"), b.chapter("one#0").questions);
        assertEquals(StudyBook.SKIM, b.chapter("two#0").label);
        assertEquals("answered questions stay", Arrays.asList("q2a", "q2b"), b.chapter("two#0").questions);
        assertFalse(b.chapters.containsKey("gone#0"));
    }
}
