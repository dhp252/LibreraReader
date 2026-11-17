package com.foobnix.assist.useit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UseIndexTest {

    private static UseExperiment experiment(String id, String tryText, String start, int weeks) {
        final UseExperiment e = new UseExperiment();
        e.id = id;
        e.tryText = tryText;
        e.bookTitle = "The Wealth of Nations";
        e.start(start, weeks);
        return e;
    }

    @Test
    public void onlyOpenExperimentsBelongInTheIndex() {
        final UseExperiment e = experiment("a", "One job a week", "2026-10-26", 2);
        assertTrue(UseIndex.isOpen(e));
        e.finish("2026-11-09", UseExperiment.HELD, "");
        assertFalse(UseIndex.isOpen(e));
        assertFalse(UseIndex.isOpen(null));
        // a draft that was never started
        assertFalse(UseIndex.isOpen(new UseExperiment()));
    }

    @Test
    public void theEntryCarriesWhatTheLibraryListShows() throws Exception {
        final JSONObject o = UseIndex.entry("wealth.epub", experiment("a", "One job a week", "2026-10-26", 2));
        assertEquals("wealth.epub", o.getString("book"));
        assertEquals("a", o.getString("id"));
        assertEquals("2026-11-09", o.getString("day"));
        assertEquals("One job a week", o.getString("title"));
        assertEquals("The Wealth of Nations", o.getString("bookTitle"));
        assertEquals("use:index:a", UseIndex.key("a"));
    }

    @Test
    public void dueAreThoseWhoseDayHasComeTheLongestWaitingFirst() throws Exception {
        final Map<String, JSONObject> items = new HashMap<>();
        items.put(UseIndex.key("late"), UseIndex.entry("b.epub", experiment("late", "Late one", "2026-10-01", 1)));
        items.put(UseIndex.key("today"), UseIndex.entry("a.epub", experiment("today", "Due today", "2026-10-26", 2)));
        items.put(UseIndex.key("soon"), UseIndex.entry("a.epub", experiment("soon", "Not yet", "2026-11-08", 1)));
        items.put(UseIndex.key("broken"), new JSONObject().put("book", "c.epub").put("id", "broken").put("day", "soon"));
        items.put(UseIndex.key("nobook"), new JSONObject().put("id", "nobook").put("day", "2026-01-01"));
        items.put("lens:goal", new JSONObject().put("book", "x").put("id", "y").put("day", "2026-01-01"));

        final List<UseIndex.Entry> due = UseIndex.due(items, "2026-11-09");
        assertEquals(2, due.size());
        assertEquals("late", due.get(0).id);
        assertEquals("b.epub", due.get(0).book);
        assertEquals("2026-10-08", due.get(0).day);
        assertEquals("today", due.get(1).id);
        assertEquals("Due today", due.get(1).title);
        assertEquals("The Wealth of Nations", due.get(1).bookTitle);

        assertTrue(UseIndex.due(items, "2026-10-07").isEmpty());
        assertEquals(3, UseIndex.due(items, "2026-12-01").size());
        assertTrue(UseIndex.due(items, "not a day").isEmpty());
        assertTrue(UseIndex.due(null, "2026-11-09").isEmpty());
    }

    @Test
    public void aListShowsDueThenRunningThenDone() {
        final UseExperiment done1 = experiment("d1", "Done first", "2026-09-01", 1);
        done1.finish("2026-09-09", UseExperiment.HELD, "");
        final UseExperiment done2 = experiment("d2", "Done later", "2026-10-01", 1);
        done2.finish("2026-10-09", UseExperiment.DIDNT, "");
        final UseExperiment running1 = experiment("r1", "Ends later", "2026-11-05", 4);
        final UseExperiment running2 = experiment("r2", "Ends sooner", "2026-11-05", 1);
        final UseExperiment due1 = experiment("u1", "Due a while", "2026-10-01", 2);
        final UseExperiment due2 = experiment("u2", "Due today", "2026-10-26", 2);

        final List<UseExperiment> all = new ArrayList<>();
        all.add(done1);
        all.add(running1);
        all.add(due2);
        all.add(done2);
        all.add(running2);
        all.add(due1);
        final List<UseExperiment> sorted = UseIndex.sorted(all, "2026-11-09");
        final StringBuilder ids = new StringBuilder();
        for (UseExperiment e : sorted) {
            ids.append(e.id).append(' ');
        }
        assertEquals("u1 u2 r2 r1 d2 d1 ", ids.toString());
        // the list given is left as it was
        assertEquals("d1", all.get(0).id);
    }
}
