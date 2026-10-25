package com.foobnix.bookmap;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapJobTest {

    private static final List<BookChapters.Range> RANGES = Arrays.asList(
            new BookChapters.Range(0, 3, "", BookChapters.FRONT),
            new BookChapters.Range(3, 20, "One", "one#0"),
            new BookChapters.Range(20, 40, "Two", "two#0"),
            new BookChapters.Range(40, 60, "Three", "three#0"));

    private static AiStore.Book withIdeasIn(String key) {
        final AiStore.Book ai = new AiStore.Book();
        ai.putChapter(new AiStore.Chapter(key, "Two", "m", 1, new ArrayList<>(),
                Collections.singletonList(new AiStore.Idea(1, "An idea", AiCategory.MAIN, -1, true, 22)),
                new ArrayList<>(), false));
        // key passages from before ideas: nothing to send instead of the text
        ai.putChapter(new AiStore.Chapter("three#0", "Three", "m", 1, new ArrayList<>()));
        return ai;
    }

    @Test
    public void carriesOnFromTheChaptersAlreadyMapped() {
        final MapBook saved = new MapBook();
        saved.chapterOrNew("one#0").n = 2;
        saved.chapterOrNew(BookChapters.FRONT).noText = true;
        final List<MapJob.Todo> todo = MapJob.todo(RANGES, saved, new AiStore.Book(), false);
        assertEquals(2, todo.size());
        assertEquals("two#0", todo.get(0).range.key);
        assertEquals("the place in the book counts every chapter", 3, todo.get(0).n);
        assertEquals(4, todo.get(1).n);
        assertFalse(todo.get(0).fromIdeas);
    }

    @Test
    public void sendsSavedIdeasOnlyWhenTheSettingIsOnAndTheChapterHasThem() {
        final AiStore.Book ai = withIdeasIn("two#0");
        List<MapJob.Todo> todo = MapJob.todo(RANGES, new MapBook(), ai, true);
        assertEquals(4, todo.size());
        assertFalse(todo.get(1).fromIdeas);
        assertTrue(todo.get(2).fromIdeas);
        assertFalse("key passages without ideas", todo.get(3).fromIdeas);

        todo = MapJob.todo(RANGES, new MapBook(), ai, false);
        assertFalse(todo.get(2).fromIdeas);
        assertFalse(MapJob.todo(RANGES, new MapBook(), null, true).get(2).fromIdeas);
    }
}
