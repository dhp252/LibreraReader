package com.foobnix.assist;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AssistStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static JSONObject fileOf(File profile, String book) throws Exception {
        final File f = new File(new File(profile, AssistStore.FOLDER), book + ".json");
        try (FileInputStream in = new FileInputStream(f)) {
            final byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                off += in.read(b, off, b.length - off);
            }
            return new JSONObject(new String(b, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void putThenItemReadsACopyStampedWithATime() throws Exception {
        final AssistStore store = new AssistStore(tmp.getRoot());
        assertNull(store.item("Book.epub", "recap:last"));
        store.put("Book.epub", "recap:last", new JSONObject().put("text", "The pin shop"));

        final JSONObject item = store.item("Book.epub", "recap:last");
        assertNotNull(item);
        assertEquals("The pin shop", item.getString("text"));
        assertTrue(item.getLong(AssistStore.T) > 0);
        // a copy: changing it changes nothing stored
        item.put("text", "changed");
        assertEquals("The pin shop", store.item("Book.epub", "recap:last").getString("text"));
        // the file is where the sync looks for it
        assertTrue(AssistStore.isAssistFile(AssistStore.profilePath("Book.epub")));
        assertTrue(new File(tmp.getRoot(), AssistStore.profilePath("Book.epub")).isFile());
    }

    @Test
    public void itemsByPrefixWithoutRemovedOnes() throws Exception {
        final AssistStore store = new AssistStore(tmp.getRoot());
        store.put("B.pdf", "preq:c1", new JSONObject().put("n", 1));
        store.put("B.pdf", "recap:last", new JSONObject().put("n", 2));
        store.put("B.pdf", "preq:c2", new JSONObject().put("n", 3));
        store.put("B.pdf", "preq:c3", new JSONObject().put("n", 4));
        store.remove("B.pdf", "preq:c3");

        final Map<String, JSONObject> preq = store.items("B.pdf", "preq:");
        // (Android's org.json keeps file order; the JVM's does not, so compared as a set here)
        assertEquals(new HashSet<>(Arrays.asList("preq:c1", "preq:c2")), new HashSet<>(preq.keySet()));
        assertEquals(3, preq.get("preq:c2").getInt("n"));
        assertEquals(3, store.items("B.pdf", null).size());
        assertTrue(store.items("Other.pdf", "preq:").isEmpty());
    }

    @Test
    public void removeLeavesATombstoneSoTheRemovalSyncs() throws Exception {
        final AssistStore store = new AssistStore(tmp.getRoot());
        store.put("B.pdf", "xray:c1", new JSONObject().put("marks", 5));
        final long before = store.item("B.pdf", "xray:c1").getLong(AssistStore.T);
        store.remove("B.pdf", "xray:c1");

        assertNull(store.item("B.pdf", "xray:c1"));
        final JSONObject raw = fileOf(tmp.getRoot(), "B.pdf").getJSONObject("xray:c1");
        assertTrue(raw.getBoolean(AssistStore.DEL));
        assertFalse(raw.has("marks"));
        assertTrue(raw.getLong(AssistStore.T) > before);

        // put again after a removal: live again, newer still
        store.put("B.pdf", "xray:c1", new JSONObject().put("marks", 6));
        assertEquals(6, store.item("B.pdf", "xray:c1").getInt("marks"));
        assertTrue(store.item("B.pdf", "xray:c1").getLong(AssistStore.T) > raw.getLong(AssistStore.T));
    }

    @Test
    public void aTimeAlwaysGrowsEvenWithAClockBehind() throws Exception {
        final AssistStore store = new AssistStore(tmp.getRoot());
        final long future = System.currentTimeMillis() + 3_600_000L;
        final File folder = new File(tmp.getRoot(), AssistStore.FOLDER);
        assertTrue(folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, "B.pdf.json"))) {
            out.write(("{\"recap:last\":{\"t\":" + future + ",\"text\":\"other device\"}}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        store.put("B.pdf", "recap:last", new JSONObject().put("text", "here"));
        final JSONObject item = store.item("B.pdf", "recap:last");
        assertEquals("here", item.getString("text"));
        assertEquals(future + 1, item.getLong(AssistStore.T));

        // two quick writes in a row: each newer than the last
        final long first = item.getLong(AssistStore.T);
        store.put("B.pdf", "recap:last", new JSONObject().put("text", "again"));
        assertTrue(store.item("B.pdf", "recap:last").getLong(AssistStore.T) > first);
    }

    @Test
    public void otherItemsAndTheLibraryFileStayApart() throws Exception {
        final AssistStore store = new AssistStore(tmp.getRoot());
        store.put("B.pdf", "a:1", new JSONObject().put("v", "a"));
        store.put(AssistStore.LIBRARY, "lens:field", new JSONObject().put("v", "ML"));
        store.put("B.pdf", "b:1", new JSONObject().put("v", "b"));
        assertEquals("a", store.item("B.pdf", "a:1").getString("v"));
        assertEquals("ML", store.item(AssistStore.LIBRARY, "lens:field").getString("v"));
        assertNull(store.item(AssistStore.LIBRARY, "a:1"));
    }

    @Test
    public void anUnreadableFileIsNeverOverwritten() throws Exception {
        final File folder = new File(tmp.getRoot(), AssistStore.FOLDER);
        assertTrue(folder.mkdirs());
        final File file = new File(folder, "B.pdf.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{\"recap:last\": {\"t\": 1, \"te".getBytes(StandardCharsets.UTF_8));
        }
        final long length = file.length();
        final AssistStore store = new AssistStore(tmp.getRoot());
        assertNull(store.item("B.pdf", "recap:last"));
        assertTrue(store.items("B.pdf", null).isEmpty());
        try {
            store.put("B.pdf", "recap:last", new JSONObject());
            fail("must refuse");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(length, file.length());
    }

    @Test
    public void assistFilesAreKnownByTheirFolder() {
        assertTrue(AssistStore.isAssistFile("assist/Book.epub.json"));
        assertTrue(AssistStore.isAssistFile("assist\\Book.epub.json"));
        assertFalse(AssistStore.isAssistFile("study/Book.epub.json"));
        assertFalse(AssistStore.isAssistFile("assist/Book.epub.json.tmp"));
        assertEquals("assist/Book.epub.json", AssistStore.profilePath("Book.epub"));
    }
}
