package com.foobnix.stats;

import static com.foobnix.stats.StatsEngineTest.MIN;
import static com.foobnix.stats.StatsEngineTest.T0;
import static com.foobnix.stats.StatsEngineTest.open;
import static com.foobnix.stats.StatsEngineTest.page;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Reading the logs of every device from files, incrementally. */
public class StatsStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void append(File file, String... lines) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file, true)) {
            for (String line : lines) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static StatsEvent header(String id, String name) {
        final StatsEvent e = new StatsEvent(StatsEvent.DEV, T0 - 1, 0, null);
        e.id = id;
        e.name = name;
        return e;
    }

    private static long readMs(StatsStore store) {
        final int day = StatsData.dayOf(T0, 0);
        return StatsReport.summary(store.data(null), day, day).readMs;
    }

    @Test
    public void readsAllDevicesAndOnlyWhatWasAppended() throws IOException {
        final File root = tmp.newFolder("stats");
        final File phone = new File(root, "p1/2026-10.jsonl");
        final File tablet = new File(root, "t1/2026-10.jsonl");
        append(phone, header("p1", "Phone").toJson(), open(T0, "b.epub").toJson(), page(T0 + MIN, "b.epub", 1, 250, MIN).toJson());
        append(tablet, header("t1", "Tablet").toJson(), open(T0, "b.epub").toJson(), page(T0 + 2 * MIN, "b.epub", 1, 250, 2 * MIN).toJson());

        final StatsStore store = new StatsStore(root);
        assertTrue(store.refresh());
        assertEquals(3 * MIN, readMs(store));
        assertEquals("Phone", store.devices().get("p1"));
        assertEquals("Tablet", store.devices().get("t1"));

        assertFalse(store.refresh());
        append(phone, page(T0 + 3 * MIN, "b.epub", 2, 250, MIN).toJson());
        assertTrue(store.refresh());
        assertEquals(4 * MIN, readMs(store));
    }

    @Test
    public void halfWrittenLineWaitsDamagedLineIsSkipped() throws IOException {
        final File root = tmp.newFolder("stats");
        final File log = new File(root, "p1/2026-10.jsonl");
        append(log, header("p1", "Phone").toJson(), "{broken", open(T0, "b.epub").toJson());
        try (OutputStream out = new FileOutputStream(log, true)) {
            final String partial = page(T0 + MIN, "b.epub", 1, 250, MIN).toJson();
            out.write(partial.getBytes(StandardCharsets.UTF_8));
        }
        final StatsStore store = new StatsStore(root);
        store.refresh();
        assertEquals(0, readMs(store));
        assertEquals(1, store.damagedLines());
        append(log, "");
        store.refresh();
        assertEquals(MIN, readMs(store));
    }

    @Test
    public void rewrittenLogIsReadAgainFromTheStart() throws IOException {
        final File root = tmp.newFolder("stats");
        final File log = new File(root, "p1/2026-10.jsonl");
        append(log, header("p1", "Phone").toJson(), open(T0, "b.epub").toJson(), page(T0 + MIN, "b.epub", 1, 250, MIN).toJson());
        final StatsStore store = new StatsStore(root);
        store.refresh();
        assertEquals(MIN, readMs(store));

        // the log replaced by a longer one that starts differently
        assertTrue(log.delete());
        final StatsEvent other = header("p1", "Phone");
        other.ts = T0 + 1;
        append(log, other.toJson(), open(T0, "b.epub").toJson(), page(T0 + 2 * MIN, "b.epub", 1, 250, 2 * MIN).toJson(),
                page(T0 + 3 * MIN, "b.epub", 2, 250, MIN).toJson());
        store.refresh();
        assertEquals(3 * MIN, readMs(store));
    }

    @Test
    public void clearedMarkerHidesWhatCameBefore() throws IOException {
        final File root = tmp.newFolder("stats");
        final File log = new File(root, "p1/2026-10.jsonl");
        append(log, header("p1", "Phone").toJson(), open(T0, "b.epub").toJson(), page(T0 + MIN, "b.epub", 1, 250, MIN).toJson());
        final StatsStore store = new StatsStore(root);
        store.refresh();
        assertEquals(MIN, readMs(store));

        StatsStore.writeCleared(root, T0 + 2 * MIN);
        append(log, open(T0 + 3 * MIN, "b.epub").toJson(), page(T0 + 5 * MIN, "b.epub", 2, 250, 2 * MIN).toJson());
        store.refresh();
        assertEquals(2 * MIN, readMs(store));
        assertEquals("Phone", store.devices().get("p1"));
    }
}
