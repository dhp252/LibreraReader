package com.foobnix.webdav;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FolderSyncTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File local;
    private File stateFile;
    private FakeRemote remote;

    /** A server in memory: path -> content, ETag and modified time. */
    static class FakeRemote implements FolderSync.Remote {
        static class Item {
            byte[] data;
            String etag;
            long modified;
        }

        final Map<String, Item> files = new TreeMap<>();
        int etags;
        long clock = 1_000_000;

        void put(String path, String content) {
            final Item item = new Item();
            item.data = content.getBytes(StandardCharsets.UTF_8);
            item.etag = "\"" + (++etags) + "\"";
            item.modified = clock += 1000;
            files.put(path, item);
        }

        String get(String path) {
            final Item item = files.get(path);
            return item == null ? null : new String(item.data, StandardCharsets.UTF_8);
        }

        @Override
        public List<WebDavClient.Entry> listRecursive(String folder) {
            final List<WebDavClient.Entry> list = new ArrayList<>();
            for (Map.Entry<String, Item> e : files.entrySet()) {
                if (folder.isEmpty() || e.getKey().startsWith(folder + "/")) {
                    list.add(entry(e.getKey(), e.getValue()));
                }
            }
            return list;
        }

        @Override
        public WebDavClient.Entry stat(String path) {
            final Item item = files.get(path);
            return item == null ? null : entry(path, item);
        }

        @Override
        public void download(String path, File target) throws IOException {
            target.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(files.get(path).data);
            }
        }

        @Override
        public void upload(File source, String path) throws IOException {
            put(path, new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        }

        @Override
        public void delete(String path) {
            files.remove(path);
        }

        private static WebDavClient.Entry entry(String path, Item item) {
            return new WebDavClient.Entry(path, false, item.data.length, item.modified, item.etag);
        }
    }

    @Before
    public void setUp() throws IOException {
        local = tmp.newFolder("local");
        stateFile = new File(tmp.getRoot(), "state.txt");
        remote = new FakeRemote();
    }

    private FolderSync sync(FolderSync.Mode mode) {
        return new FolderSync(remote, local, "Librera", stateFile, mode, null);
    }

    private File write(String path, String content, long modified) throws IOException {
        final File file = new File(local, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        file.setLastModified(modified);
        return file;
    }

    private String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(local, path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void firstSyncUploadsAndSecondDoesNothing() throws IOException {
        write("profile.Librera/pixel/app-Progress.json", "{p:1}", 10_000);
        write("profile.Librera/pixel/app-Bookmarks.json", "{b:1}", 10_000);

        FolderSync.Result first = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(2, first.uploaded);
        assertEquals("{p:1}", remote.get("Librera/profile.Librera/pixel/app-Progress.json"));

        FolderSync.Result second = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(0, second.uploaded + second.downloaded + second.deletedLocal + second.deletedRemote);
    }

    @Test
    public void changesTravelBothWays() throws IOException {
        write("a.json", "{v:1}", 10_000);
        sync(FolderSync.Mode.MIRROR).sync();

        remote.put("Librera/a.json", "{v:2 remote}");
        FolderSync.Result down = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(1, down.downloaded);
        assertEquals("{v:2 remote}", read("a.json"));

        write("a.json", "{v:3 local!}", 99_000_000);
        FolderSync.Result up = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(1, up.uploaded);
        assertEquals("{v:3 local!}", remote.get("Librera/a.json"));
    }

    @Test
    public void deletionsTravelBothWaysInMirrorMode() throws IOException {
        write("a.json", "{a}", 10_000);
        write("b.json", "{b}", 10_000);
        sync(FolderSync.Mode.MIRROR).sync();

        remote.delete("Librera/a.json");
        assertTrue(new File(local, "b.json").delete());
        FolderSync.Result result = sync(FolderSync.Mode.MIRROR).sync();

        assertEquals(1, result.deletedLocal);
        assertEquals(1, result.deletedRemote);
        assertFalse(new File(local, "a.json").exists());
        assertEquals(null, remote.get("Librera/b.json"));
    }

    @Test
    public void anEmptiedLocalFolderDoesNotEmptyTheServer() throws IOException {
        // a fresh install restored with the old sync state, or a moved or cleared Librera folder
        for (int i = 0; i < 12; i++) {
            write("profile.Librera/device.Pixel/file" + i + ".json", "{" + i + "}", 10_000);
        }
        sync(FolderSync.Mode.MIRROR).sync();
        for (File f : new File(local, "profile.Librera/device.Pixel").listFiles()) {
            assertTrue(f.delete());
        }

        FolderSync.Result result = sync(FolderSync.Mode.MIRROR).sync();

        assertEquals(0, result.deletedRemote);
        assertEquals(12, result.keptOnServer);
        assertEquals(12, result.downloaded);
        assertEquals("{3}", remote.get("Librera/profile.Librera/device.Pixel/file3.json"));
        assertEquals("{3}", read("profile.Librera/device.Pixel/file3.json"));
    }

    @Test
    public void mostFilesGoneFromTheServerAreNotDeletedHere() throws IOException {
        for (int i = 0; i < 12; i++) {
            write("f" + i + ".json", "{" + i + "}", 10_000);
        }
        sync(FolderSync.Mode.MIRROR).sync();
        for (int i = 0; i < 11; i++) {
            remote.delete("Librera/f" + i + ".json");
        }

        FolderSync.Result result = sync(FolderSync.Mode.MIRROR).sync();

        assertEquals(0, result.deletedLocal);
        assertEquals(11, result.keptHere);
        assertEquals(11, result.uploaded);
        assertEquals("{4}", remote.get("Librera/f4.json"));
    }

    @Test
    public void newServerFileIsDownloadedOnlyInMirrorMode() throws IOException {
        remote.put("Librera/new.json", "{n}");

        FolderSync.Result onDemand = sync(FolderSync.Mode.ON_DEMAND).sync();
        assertEquals(0, onDemand.downloaded);
        assertFalse(new File(local, "new.json").exists());

        FolderSync.Result mirror = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(1, mirror.downloaded);
        assertEquals("{n}", read("new.json"));
    }

    @Test
    public void removedDownloadOnDemandKeepsTheServerFile() throws IOException {
        remote.put("Librera/Books/novel.epub", "EPUB");
        FolderSync books = sync(FolderSync.Mode.ON_DEMAND);

        File downloaded = books.downloadOnDemand(remote.stat("Librera/Books/novel.epub"));
        assertEquals("EPUB", read("Books/novel.epub"));

        assertTrue(downloaded.delete());
        FolderSync.Result result = books.sync();
        assertEquals(0, result.deletedRemote);
        assertEquals("EPUB", remote.get("Librera/Books/novel.epub"));
        // and it is not downloaded again behind the user's back
        assertEquals(0, books.sync().downloaded);
        assertFalse(downloaded.exists());
    }

    @Test
    public void annotatedBookIsUploadedBackInOnDemandMode() throws IOException {
        remote.put("Librera/Books/paper.pdf", "PDF");
        FolderSync books = sync(FolderSync.Mode.ON_DEMAND);
        books.downloadOnDemand(remote.stat("Librera/Books/paper.pdf"));

        write("Books/paper.pdf", "PDF+annotations", 99_000_000);
        assertEquals(1, books.sync().uploaded);
        assertEquals("PDF+annotations", remote.get("Librera/Books/paper.pdf"));
    }

    @Test
    public void conflictGoesToTheNewerSide() throws IOException {
        write("a.json", "{base}", 10_000);
        sync(FolderSync.Mode.MIRROR).sync();

        write("a.json", "{local edit}", 20_000);
        remote.clock = 50_000_000;
        remote.put("Librera/a.json", "{remote edit, newer}");

        FolderSync.Result result = sync(FolderSync.Mode.MIRROR).sync();
        assertEquals(1, result.conflicts);
        assertEquals("{remote edit, newer}", read("a.json"));
    }

    @Test
    public void filterAndTemporaryFilesAreLeftAlone() throws IOException {
        write("profile.Librera/x/app-Recent.json", "{r}", 10_000);
        write("Fonts/big.ttf", "TTF", 10_000);
        write("profile.Librera/x/.app-Recent.json.part", "half", 10_000);

        FolderSync profiles = new FolderSync(remote, local, "Librera", stateFile, FolderSync.Mode.MIRROR,
                path -> path.startsWith("profile."));
        FolderSync.Result result = profiles.sync();

        assertEquals(1, result.uploaded);
        assertEquals(null, remote.get("Librera/Fonts/big.ttf"));
        assertEquals(null, remote.get("Librera/profile.Librera/x/.app-Recent.json.part"));
    }

    @Test
    public void stateEscapingRoundTrips() {
        String tricky = "Books/a\tb\\c\nd.epub";
        assertEquals(tricky, FolderSync.unescape(FolderSync.escape(tricky)));
        assertEquals("\"etag\"", FolderSync.unescape(FolderSync.escape("\"etag\"")));
    }
}
