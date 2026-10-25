package com.foobnix.bookmap;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MapStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static MapBook.Chapter chapter(MapBook b, String key, int n) {
        final MapBook.Chapter c = b.chapterOrNew(key);
        c.n = n;
        c.title = "Chapter " + n;
        c.from = n * 10;
        c.to = n * 10 + 10;
        c.chars = 20000;
        c.threads.add(new MapBook.Thread("Gatsby", Arrays.asList("Jay"), "the host"));
        c.moments.add(new MapBook.Moment(Arrays.asList("Gatsby", "Daisy"), MapBook.TURN, "They meet",
                "the words of the book", n * 10 + 2, 0.4));
        return c;
    }

    @Test
    public void keepsChaptersBookAndViewAndReadsThemBackInOrder() throws Exception {
        final MapStore store = new MapStore(tmp.getRoot());
        store.update("Book.epub", b -> {
            chapter(b, "two#0", 2);
            chapter(b, "one#0", 1).cutOff = true;
            b.premise = "A man wants the past back.";
            b.lanes.add(new MapBook.Lane("gatsby", "Gatsby", "the host", Arrays.asList("Gatsby", "Jay Gatsby")));
            b.arcs.add(new MapBook.Arc("1.1", "2.1", "pays_off", "the light"));
            b.key.add("2.1");
            b.seen.addAll(Arrays.asList("one#0", "two#0"));
            b.model = "m";
            b.whole = true;
        });

        final MapBook b = store.load("Book.epub");
        assertEquals(Arrays.asList("one#0", "two#0"), Arrays.asList(b.ordered().get(0).key, b.ordered().get(1).key));
        final MapBook.Chapter one = b.chapter("one#0");
        assertTrue(one.cutOff);
        assertEquals("Gatsby", one.threads.get(0).name);
        assertEquals(Collections.singletonList("Jay"), one.threads.get(0).aliases);
        assertEquals(MapBook.TURN, one.moments.get(0).kind);
        assertEquals("the words of the book", one.moments.get(0).quote);
        assertEquals(12, one.moments.get(0).page);
        assertEquals(0.4, one.moments.get(0).at, 1e-9);
        assertEquals("1.1", one.momentId(0));
        assertTrue(b.hasBook());
        assertEquals("A man wants the past back.", b.premise);
        assertEquals(Arrays.asList("Gatsby", "Jay Gatsby"), b.lanes.get(0).merged);
        assertEquals("pays_off", b.arcs.get(0).type);
        assertEquals(Collections.singletonList("2.1"), b.key);
        assertEquals(Arrays.asList("one#0", "two#0"), b.seen);
        assertEquals(Boolean.TRUE, b.whole);
        assertTrue(store.has("Book.epub"));
        assertFalse(store.has("Other.epub"));
    }

    @Test
    public void onlyThePartsAChangeTouchedGetANewTime() throws Exception {
        final MapStore store = new MapStore(tmp.getRoot());
        MapBook b = store.update("Book.epub", x -> {
            chapter(x, "one#0", 1);
            chapter(x, "two#0", 2);
        });
        final long one = b.chapter("one#0").t;
        final long two = b.chapter("two#0").t;
        assertTrue(one > 0);
        assertNull(b.whole);

        b = store.update("Book.epub", x -> x.chapter("two#0").title = "Two");
        assertEquals(one, b.chapter("one#0").t);
        assertTrue(b.chapter("two#0").t > two);

        b = store.update("Book.epub", x -> x.whole = false);
        assertEquals(one, b.chapter("one#0").t);
        assertTrue(b.viewT > 0);
        final JSONObject saved = new JSONObject(new String(java.nio.file.Files.readAllBytes(
                new File(tmp.getRoot(), "map/Book.epub.json").toPath()), StandardCharsets.UTF_8));
        assertFalse(saved.has(MapBook.BOOK));
        assertFalse(saved.getJSONObject(MapBook.VIEW).getBoolean("whole"));
    }

    @Test
    public void anUnreadableFileIsNotOverwritten() throws Exception {
        final File folder = new File(tmp.getRoot(), "map");
        assertTrue(folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, "Book.epub.json"))) {
            out.write("{\"one#0\": {\"n\":".getBytes(StandardCharsets.UTF_8));
        }
        final MapStore store = new MapStore(tmp.getRoot());
        assertTrue(store.load("Book.epub").isEmpty());
        try {
            store.update("Book.epub", x -> chapter(x, "two#0", 2));
            fail("an unreadable map must not be overwritten");
        } catch (java.io.IOException expected) {
            // the half-written file waits for a good copy
        }
    }

    @Test
    public void clearingDeletesEveryPart() throws Exception {
        final MapStore store = new MapStore(tmp.getRoot());
        store.update("Book.epub", x -> {
            chapter(x, "one#0", 1);
            x.lanes.add(new MapBook.Lane("a", "A", "", null));
            x.whole = true;
        });
        final MapBook b = store.update("Book.epub", MapBook::clear);
        assertTrue(b.isEmpty());
        assertEquals("{}", b.toJson().toString());
        assertFalse(store.has("Book.epub"));
    }

    @Test
    public void namesItsFiles() {
        assertEquals("map/Book.epub.json", MapStore.profilePath("Book.epub"));
        assertTrue(MapStore.isMapFile("map/Book.epub.json"));
        assertTrue(MapStore.isMapFile("map\\Book.epub.json"));
        assertFalse(MapStore.isMapFile("study/Book.epub.json"));
        assertFalse(MapStore.isMapFile("map/Book.epub.json.tmp"));
    }
}
