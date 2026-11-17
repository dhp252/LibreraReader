package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;

import com.foobnix.ai.AiTask;
import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FictionTaskTest {

    @Test
    public void theReadersPlaceSurvivesTheTaskStore() {
        final BookChapters.Range ticket = FictionShield.ticket(13, 100);
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", AiTask.FICTION_CAST);
        m.put("path", "/sdcard/Books/Verne.epub");
        m.put("title", "Around the World in Eighty Days");
        m.put("width", 1080);
        m.put("height", 2340);
        m.put("fontSize", 18);
        m.put("page", 13);
        m.put("chapterFrom", ticket.from);
        m.put("chapterTo", ticket.to);
        m.put("chapterTitle", ticket.title);
        m.put("chapterKey", ticket.key);
        m.put("redoBefore", 5L);
        final AiTask t = AiTask.fromMap(AiTask.fromMap(m).toMap());
        assertEquals(AiTask.FICTION_CAST, t.kind);
        // what the job may read is the same after WorkManager stored the task
        assertEquals(14, FictionShield.window(0, t.chapter, 100).end);
        assertEquals(21, FictionShield.window(0, t.chapter, 150).end);
        assertEquals(5L, t.redoBefore);
        assertEquals("fictionCast:Verne.epub", t.key());
    }

    @Test
    public void theTimelineTaskCarriesTheSameTicket() {
        final BookChapters.Range ticket = FictionShield.ticket(13, 100);
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", AiTask.FICTION_TIMELINE);
        m.put("path", "/sdcard/Books/Verne.epub");
        m.put("title", "Around the World in Eighty Days");
        m.put("page", 13);
        m.put("chapterFrom", ticket.from);
        m.put("chapterTo", ticket.to);
        m.put("chapterTitle", ticket.title);
        m.put("chapterKey", ticket.key);
        final AiTask t = AiTask.fromMap(AiTask.fromMap(m).toMap());
        assertEquals(AiTask.FICTION_TIMELINE, t.kind);
        assertEquals(14, FictionShield.window(0, t.chapter, 100).end);
        assertEquals("fictionTimeline:Verne.epub", t.key());
    }
}
