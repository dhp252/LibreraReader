package com.foobnix.webdav;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Against a real WebDAV server, when one is advertised in {tmpdir}/librera-webdav-test.txt
 * (three lines: URL, user, password); skipped otherwise. The dev-build-apk skill documents a
 * local wsgidav server for it.
 */
public class WebDavServerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String url;
    private String user;
    private String password;
    private String folder;
    private WebDavClient parent;
    private WebDavClient client;

    @Before
    public void connect() throws IOException {
        final File config = new File(System.getProperty("java.io.tmpdir"), "librera-webdav-test.txt");
        Assume.assumeTrue("no WebDAV test server advertised in " + config, config.isFile());
        final String[] lines = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8).trim().split("\n");
        url = lines[0].trim();
        user = lines[1].trim();
        password = lines[2].trim();
        folder = "it-" + System.nanoTime();
        parent = new WebDavClient(url, user, password);
        parent.mkdirs(folder);
        client = new WebDavClient(url + "/" + folder, user, password);
    }

    @After
    public void cleanUp() throws IOException {
        if (parent != null) {
            parent.delete(folder);
        }
    }

    private File write(File dir, String path, String content, long modified) throws IOException {
        final File file = new File(dir, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        file.setLastModified(modified);
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void filesWithUnicodeNamesRoundTrip() throws IOException {
        final String path = "Books/Văn học/Chiến tranh và hòa bình.txt";
        final File source = write(tmp.getRoot(), "source.txt", "Nội dung sách", 1_000_000);

        client.upload(source, path);

        final WebDavClient.Entry stat = client.stat(path);
        assertNotNull(stat);
        assertFalse(stat.directory);
        assertEquals(source.length(), stat.size);

        boolean folderListed = false;
        boolean fileListed = false;
        for (WebDavClient.Entry entry : client.listRecursive("")) {
            folderListed |= entry.directory && entry.path.equals("Books/Văn học");
            fileListed |= !entry.directory && entry.path.equals(path);
        }
        assertTrue(folderListed);
        assertTrue(fileListed);

        final File back = new File(tmp.getRoot(), "back.txt");
        client.download(path, back);
        assertEquals("Nội dung sách", read(back));

        client.delete("Books");
        assertNull(client.stat(path));
        assertTrue(client.listRecursive("").isEmpty());
    }

    @Test
    public void conditionalUploadsAreRefusedOnceAnotherDeviceWrote() throws IOException {
        final File mine = write(tmp.getRoot(), "mine.json", "{mine}", 1_000_000);
        final File theirs = write(tmp.getRoot(), "theirs.json", "{theirs, longer}", 1_000_000);

        client.upload(mine, "a.json", FolderSync.Remote.NO_FILE);
        try {
            client.upload(mine, "a.json", FolderSync.Remote.NO_FILE);
            fail("a.json exists already");
        } catch (WebDavClient.WebDavException e) {
            assertEquals(WebDavClient.PRECONDITION_FAILED, e.code);
        }
        final WebDavClient.Entry seen = client.stat("a.json");
        assertNotNull(seen.etag);

        client.upload(theirs, "a.json", seen.etag);
        try {
            client.upload(mine, "a.json", seen.etag);
            fail("the copy seen was replaced");
        } catch (WebDavClient.WebDavException e) {
            assertEquals(WebDavClient.PRECONDITION_FAILED, e.code);
        }
        final File back = new File(tmp.getRoot(), "back.json");
        client.download("a.json", back);
        assertEquals("{theirs, longer}", read(back));
    }

    @Test
    public void missingRootFolderIsCreated() throws IOException {
        final WebDavClient fresh = new WebDavClient(url + "/" + folder + "/Librera", user, password);
        try {
            fresh.list("");
            fail("the folder does not exist yet");
        } catch (WebDavClient.WebDavException e) {
            assertEquals(404, e.code);
        }
        fresh.mkdirs("");
        assertTrue(fresh.list("").isEmpty());
        fresh.mkdirs("");
        assertTrue(fresh.list("").isEmpty());
    }

    @Test
    public void wrongPasswordIsAnHttp401() throws IOException {
        try {
            new WebDavClient(url, user, password + "-wrong").list("");
            fail("listing with a wrong password must fail");
        } catch (WebDavClient.WebDavException e) {
            assertEquals(401, e.code);
        }
    }

    @Test
    public void twoDevicesStayInStep() throws IOException {
        final File deviceA = tmp.newFolder("A");
        final File deviceB = tmp.newFolder("B");
        final FolderSync a = new FolderSync(client, deviceA, "profile.Librera", new File(tmp.getRoot(), "a.state"), FolderSync.Mode.MIRROR, null);
        final FolderSync b = new FolderSync(client, deviceB, "profile.Librera", new File(tmp.getRoot(), "b.state"), FolderSync.Mode.MIRROR, null);

        // A reads a book: its progress reaches B
        write(deviceA, "device.A/app-Progress.json", "{\"book.epub\":0.10}", 1_000_000);
        assertEquals(1, a.sync().uploaded);
        assertEquals(1, b.sync().downloaded);
        assertEquals("{\"book.epub\":0.10}", read(new File(deviceB, "device.A/app-Progress.json")));

        // B reads too: its own file reaches A
        write(deviceB, "device.B/app-Progress.json", "{\"book.epub\":0.35}", 2_000_000);
        assertEquals(1, b.sync().uploaded);
        assertEquals(1, a.sync().downloaded);
        assertEquals("{\"book.epub\":0.35}", read(new File(deviceA, "device.B/app-Progress.json")));

        // A reads further: the change arrives, nothing else moves
        write(deviceA, "device.A/app-Progress.json", "{\"book.epub\":0.50 }", 3_000_000);
        assertEquals(1, a.sync().uploaded);
        final FolderSync.Result toB = b.sync();
        assertEquals(1, toB.downloaded);
        assertEquals(0, toB.uploaded);
        assertEquals("{\"book.epub\":0.50 }", read(new File(deviceB, "device.A/app-Progress.json")));

        // B drops its file: the deletion reaches A
        assertTrue(new File(deviceB, "device.B/app-Progress.json").delete());
        assertEquals(1, b.sync().deletedRemote);
        assertEquals(1, a.sync().deletedLocal);
        assertFalse(new File(deviceA, "device.B/app-Progress.json").exists());

        // and a further round trip is quiet
        final FolderSync.Result quiet = a.sync();
        assertEquals(0, quiet.uploaded + quiet.downloaded + quiet.deletedLocal + quiet.deletedRemote);
        assertTrue(quiet.errors.isEmpty());
    }
    @Test
    public void hiddenFoldersAreNotEnteredWhenListing() throws IOException {
        final File book = tmp.newFile("book.pdf");
        Files.write(book.toPath(), "PDF".getBytes(StandardCharsets.UTF_8));
        client.upload(book, "Books/Sci-fi/book.pdf");
        client.upload(book, "Books/.covers/Sci-fi/book.pdf.jpg");

        final List<WebDavClient.Entry> all = client.listRecursive("Books");
        boolean coversFolder = false;
        for (WebDavClient.Entry entry : all) {
            assertFalse(entry.path, entry.path.startsWith("Books/.covers/"));
            coversFolder |= entry.path.equals("Books/.covers") && entry.directory;
        }
        assertTrue(coversFolder);
        assertEquals(1, client.listRecursive("Books/.covers").stream().filter(e -> !e.directory).count());
    }
}
