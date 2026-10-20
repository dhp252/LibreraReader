package com.foobnix.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdeaMarksTest {

    /** Two pages: words 0..18 on page 4, the rest on page 5. */
    private static final String TEXT = "Habits are the compound interest of self-improvement over the years. "
            + "Small changes look like nothing at first and then add up. "
            + "For example a plane leaving Los Angeles for New York that turns three degrees lands in Washington instead.";

    private static List<String> words() {
        return Arrays.asList(TEXT.split(" "));
    }

    private static List<Integer> pages(int count) {
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(i < 19 ? 4 : 5);
        }
        return pages;
    }

    private static AiPrompts.Idea idea(int id, int parent, List<String> sentences, List<String[]> paragraphs) {
        return new AiPrompts.Idea(id, id == 1 ? AiCategory.MAIN : AiCategory.EXAMPLE, "Idea " + id, parent, sentences, paragraphs);
    }

    @Test
    public void joinRenumbersPartsAndKeepsParentsWithinAPart() {
        final List<AiPrompts.Idea> joined = IdeaMarks.join(Arrays.asList(
                Arrays.asList(idea(1, -1, null, null), idea(2, 1, null, null)),
                Arrays.asList(idea(1, -1, null, null), idea(2, 1, null, null), idea(3, 7, null, null))));
        assertEquals(5, joined.size());
        assertEquals(3, joined.get(2).id);
        assertEquals(4, joined.get(3).id);
        assertEquals(3, joined.get(3).parent);
        // the model named an idea it never listed
        assertEquals(-1, joined.get(4).parent);
    }

    @Test
    public void placesSentencesAndParagraphsAndFindsWhatIsUnmarked() {
        final List<String> words = words();
        final List<Integer> pages = pages(words.size());
        final List<AiPrompts.Idea> ideas = Arrays.asList(
                idea(1, -1, Arrays.asList("Habits are the compound interest of self-improvement over the years."),
                        Collections.singletonList(new String[]{"Habits are the compound interest", "then add up."})),
                idea(2, 1, Arrays.asList("A plane leaving Los Angeles turns and lands somewhere else."), Collections.<String[]>emptyList()));
        final IdeaMarks.Placed placed = IdeaMarks.place(words, pages, ideas);
        assertEquals(1, placed.sentences.size());
        assertEquals(1, placed.sentences.get(0).idea);
        assertEquals(AiCategory.MAIN, placed.sentences.get(0).category);
        assertEquals(1, placed.paragraphs.size());
        assertTrue(placed.paragraphs.get(0).text.endsWith("and then add up."));
        assertTrue(placed.isMarked(1));
        assertFalse(placed.isMarked(2));

        final List<AiPrompts.Idea> unmarked = IdeaMarks.unmarked(ideas, placed);
        assertEquals(1, unmarked.size());
        final Map<Integer, List<String>> found = new HashMap<>();
        found.put(2, Arrays.asList("For example a plane leaving Los Angeles for New York that turns three degrees lands in Washington instead."));
        found.put(1, Arrays.asList("Small changes look like nothing at first and then add up."));
        IdeaMarks.repair(placed, words, pages, unmarked, found);
        // only the ideas asked about take the repair's sentences
        assertEquals(2, placed.sentences.size());
        assertTrue(placed.isMarked(2));

        final AiStore.Chapter chapter = IdeaMarks.chapter("c#1", "C", "m", 9, ideas, placed, pages, true);
        assertEquals(0, chapter.notLocated());
        assertEquals(4, chapter.idea(1).page);
        assertEquals(5, chapter.idea(2).page);
        assertEquals(1, chapter.idea(2).parent);
        assertTrue(chapter.incomplete);
    }

    @Test
    public void theSameWordsForTwoIdeasAreDrawnOnceButMarkBoth() {
        final List<String> words = words();
        final List<String> same = Arrays.asList("Small changes look like nothing at first and then add up.");
        final IdeaMarks.Placed placed = IdeaMarks.place(words, pages(words.size()),
                Arrays.asList(idea(1, -1, same, Collections.<String[]>emptyList()), idea(2, -1, same, Collections.<String[]>emptyList())));
        assertEquals(1, placed.sentences.size());
        assertTrue(placed.isMarked(1));
        assertTrue(placed.isMarked(2));
    }
}
