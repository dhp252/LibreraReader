package com.foobnix.webdav;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebDavListingsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final WebDavClient.Entry FOLDER = new WebDavClient.Entry("Books/Sci-fi", true, 0, 0, null);
    private static final WebDavClient.Entry BOOK = new WebDavClient.Entry("Books/Dune é.epub", false, 1234, 1_700_000_000_000L, "\"v1\"");

    @Test
    public void aListingOutlivesTheAppOnDisk() throws Exception {
        final File dir = tmp.newFolder();
        new WebDavListings(dir, "https://a/dav|me").put("Books", Arrays.asList(FOLDER, BOOK), 5000);

        final WebDavListings.Listing read = new WebDavListings(dir, "https://a/dav|me").get("Books");
        assertEquals(5000, read.time);
        assertEquals(2, read.entries.size());
        final WebDavClient.Entry folder = read.entries.get(0);
        assertEquals("Books/Sci-fi", folder.path);
        assertTrue(folder.directory);
        assertNull(folder.etag);
        final WebDavClient.Entry book = read.entries.get(1);
        assertEquals(BOOK.toString(), book.toString());
    }

    @Test
    public void anotherAccountOrFolderNeverSeesIt() throws Exception {
        final File dir = tmp.newFolder();
        new WebDavListings(dir, "https://a/dav|me").put("Books", Collections.singletonList(BOOK), 5000);

        assertNull(new WebDavListings(dir, "https://a/dav|you").get("Books"));
        assertNull(new WebDavListings(dir, "https://b/dav|me").get("Books"));
        assertNull(new WebDavListings(dir, "https://a/dav|me").get("Books/Sci-fi"));
        // a file of another folder under the same name (a hash shared by two) is not taken
        final String text = WebDavListings.toJson(new WebDavListings.Listing(1, Collections.singletonList(BOOK)), "https://a/dav|me", "Books");
        assertNull(WebDavListings.fromJson(text, "https://a/dav|me", "Books/Other"));
    }

    @Test
    public void oneEntryIsReplacedKeepingTheListingTime() throws Exception {
        final File dir = tmp.newFolder();
        final WebDavListings kept = new WebDavListings(dir, "acc");
        kept.put("Books/.covers", Collections.singletonList(new WebDavClient.Entry("Books/.covers/a.pdf.jpg", false, 1, 1, "\"1\"")), 7000);

        kept.replace("Books/.covers", "Books/.covers/a.pdf.jpg", new WebDavClient.Entry("Books/.covers/a.pdf.jpg", false, 2, 2, "\"2\""));
        kept.replace("Books/.covers", "Books/.covers/b.pdf.json", new WebDavClient.Entry("Books/.covers/b.pdf.json", false, 3, 3, "\"3\""));
        kept.replace("Books/.covers", "Books/.covers/gone.jpg", null);
        kept.replace("Books/never-listed", "Books/never-listed/x.pdf", BOOK);

        final WebDavListings.Listing read = new WebDavListings(dir, "acc").get("Books/.covers");
        assertEquals(7000, read.time);
        assertEquals(2, read.entries.size());
        assertEquals("\"2\"", read.entries.get(0).etag);
        assertEquals("Books/.covers/b.pdf.json", read.entries.get(1).path);
        assertNull(kept.get("Books/never-listed"));

        kept.replace("Books/.covers", "Books/.covers/a.pdf.jpg", null);
        assertEquals(1, new WebDavListings(dir, "acc").get("Books/.covers").entries.size());
    }

    @Test
    public void freshnessIsTheListingAge() {
        final WebDavListings.Listing listing = new WebDavListings.Listing(10_000, Collections.<WebDavClient.Entry>emptyList());
        assertFalse(listing.olderThan(60_000, 70_000));
        assertTrue(listing.olderThan(60_000, 70_001));
    }
}
