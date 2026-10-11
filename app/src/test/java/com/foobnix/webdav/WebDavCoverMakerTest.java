package com.foobnix.webdav;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class WebDavCoverMakerTest {

    private static final WebDavCoverMaker.Supported BOOKS = name -> name.endsWith(".pdf") || name.endsWith(".epub");

    private static WebDavClient.Entry file(String path, long size, String etag) {
        return new WebDavClient.Entry(path, false, size, 1, etag);
    }

    @Test
    public void fetchesOnlyBooksUpToTheSizeLimit() {
        final WebDavClient.Entry small = file("Books/a.pdf", 3_000_000, "\"1\"");
        final WebDavClient.Entry limit = file("Books/b.epub", WebDavCoverMaker.MAX_SIZE, "\"2\"");
        final WebDavClient.Entry big = file("Books/scan.pdf", WebDavCoverMaker.MAX_SIZE + 1, "\"3\"");
        final WebDavClient.Entry empty = file("Books/empty.pdf", 0, "\"4\"");
        final WebDavClient.Entry notes = file("Books/notes.xyz", 1000, "\"5\"");
        final WebDavClient.Entry folder = new WebDavClient.Entry("Books/Sci-fi", true, 0, 1, null);

        final List<WebDavClient.Entry> todo = WebDavCoverMaker.todo(
                Arrays.asList(folder, small, big, limit, empty, notes), Collections.emptySet(), BOOKS);
        assertEquals(Arrays.asList(small, limit), todo);
    }

    @Test
    public void aFailedBookIsSkippedUntilItsFileChanges() {
        final WebDavClient.Entry broken = file("Books/broken.pdf", 1000, "\"v1\"");
        final HashSet<String> failed = new HashSet<>(Collections.singleton(WebDavCoverMaker.key(broken)));
        assertEquals(Collections.emptyList(), WebDavCoverMaker.todo(Collections.singletonList(broken), failed, BOOKS));

        final WebDavClient.Entry replaced = file("Books/broken.pdf", 1200, "\"v2\"");
        assertNotEquals(WebDavCoverMaker.key(broken), WebDavCoverMaker.key(replaced));
        assertEquals(Collections.singletonList(replaced),
                WebDavCoverMaker.todo(Collections.singletonList(replaced), failed, BOOKS));
    }
}
