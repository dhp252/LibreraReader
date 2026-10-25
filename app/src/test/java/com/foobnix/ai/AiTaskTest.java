package com.foobnix.ai;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiTaskTest {

    private static Map<String, Object> stored(String kind) {
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("path", "/sdcard/Books/The Prince.epub");
        m.put("title", "The Prince");
        m.put("width", 1080);
        m.put("height", 2340);
        m.put("fontSize", 18);
        m.put("page", 41);
        m.put("wholeBook", true);
        return m;
    }

    @Test
    public void everyKindComesBackAsItWasStored() {
        for (String kind : new String[]{AiTask.HIGHLIGHTS, AiTask.MAP, AiTask.BOOK_SUMMARY, AiTask.STUDY_PREVIEW}) {
            final AiTask t = AiTask.fromMap(stored(kind));
            assertNotNull(kind, t);
            final AiTask again = AiTask.fromMap(t.toMap());
            assertEquals(kind, again.kind);
            assertEquals("/sdcard/Books/The Prince.epub", again.path);
            assertEquals("The Prince", again.title);
            assertEquals(1080, again.width);
            assertEquals(2340, again.height);
            assertEquals(18, again.fontSize);
            assertEquals(41, again.page);
            assertTrue(again.wholeBook);
            assertNull(again.chapter);
            assertEquals(kind + ":The Prince.epub", again.key());
        }
    }

    @Test
    public void keepsAGivenChapterAndTheOptions() {
        final Map<String, Object> m = stored(AiTask.HIGHLIGHTS);
        m.put("wholeBook", false);
        m.put("chapterFrom", 12);
        m.put("chapterTo", 30);
        m.put("chapterTitle", "Part One");
        m.put("chapterKey", "part one#0/chapter 2#0");
        final AiTask t = AiTask.fromMap(AiTask.fromMap(m).toMap());
        assertFalse(t.wholeBook);
        assertEquals(12, t.chapter.from);
        assertEquals(30, t.chapter.to);
        assertEquals("Part One", t.chapter.title);
        assertEquals("part one#0/chapter 2#0", t.chapter.key);

        final Map<String, Object> map = stored(AiTask.MAP);
        map.put("bookOnly", true);
        assertTrue(AiTask.fromMap(AiTask.fromMap(map).toMap()).bookOnly);
        final Map<String, Object> summary = stored(AiTask.BOOK_SUMMARY);
        summary.put("regenerate", true);
        assertTrue(AiTask.fromMap(AiTask.fromMap(summary).toMap()).regenerate);
    }

    @Test
    public void somethingElseIsNoTask() {
        assertNull(AiTask.fromMap(stored("translate")));
        final Map<String, Object> noBook = stored(AiTask.MAP);
        noBook.remove("path");
        assertNull(AiTask.fromMap(noBook));
        assertNull(AiTask.fromMap(null));
        assertEquals("map:Book.pdf", AiTask.key("Book.pdf", AiTask.MAP));
    }

    @Test
    public void aWholeBookRedoKeepsItsTime() {
        final Map<String, Object> m = stored(AiTask.HIGHLIGHTS);
        assertEquals(0, AiTask.fromMap(AiTask.fromMap(m).toMap()).redoBefore);
        m.put("redoBefore", 1_761_400_000_000L);
        final AiTask again = AiTask.fromMap(AiTask.fromMap(m).toMap());
        assertEquals(1_761_400_000_000L, again.redoBefore);
        assertTrue(again.wholeBook);
    }
}
