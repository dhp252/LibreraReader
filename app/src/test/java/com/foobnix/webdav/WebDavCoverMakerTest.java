package com.foobnix.webdav;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

public class WebDavCoverMakerTest {

    private static final WebDavCoverMaker.Supported BOOKS = name -> name.endsWith(".pdf") || name.endsWith(".epub");

    private static WebDavClient.Entry file(String path, long size, String etag) {
        return new WebDavClient.Entry(path, false, size, 1, etag);
    }

    @Test
    public void fetchesBooksOfAnySizeThatLackSomething() {
        final WebDavCoverMaker.Need small = new WebDavCoverMaker.Need(file("Books/a.pdf", 3_000_000, "\"1\""), true, true);
        final WebDavCoverMaker.Need huge = new WebDavCoverMaker.Need(file("Books/scan.pdf", 900_000_000L, "\"2\""), true, false);
        final WebDavCoverMaker.Need metaOnly = new WebDavCoverMaker.Need(file("Books/b.epub", 1000, "\"3\""), false, true);
        final WebDavCoverMaker.Need complete = new WebDavCoverMaker.Need(file("Books/c.epub", 1000, "\"4\""), false, false);
        final WebDavCoverMaker.Need notes = new WebDavCoverMaker.Need(file("Books/notes.xyz", 1000, "\"5\""), true, true);
        final WebDavCoverMaker.Need folder = new WebDavCoverMaker.Need(
                new WebDavClient.Entry("Books/Sci-fi", true, 0, 1, null), true, true);

        final List<WebDavCoverMaker.Need> todo = WebDavCoverMaker.todo(
                Arrays.asList(folder, small, huge, metaOnly, complete, notes), Collections.emptySet(), BOOKS);
        assertEquals(3, todo.size());
        assertSame(small, todo.get(0));
        assertSame(huge, todo.get(1));
        assertSame(metaOnly, todo.get(2));
    }

    @Test
    public void aFailedBookIsSkippedUntilItsFileChanges() {
        final WebDavClient.Entry broken = file("Books/broken.pdf", 1000, "\"v1\"");
        final HashSet<String> failed = new HashSet<>(Collections.singleton(WebDavCoverMaker.key(broken)));
        assertEquals(0, WebDavCoverMaker.todo(Collections.singletonList(
                new WebDavCoverMaker.Need(broken, true, true)), failed, BOOKS).size());

        final WebDavClient.Entry replaced = file("Books/broken.pdf", 1200, "\"v2\"");
        assertNotEquals(WebDavCoverMaker.key(broken), WebDavCoverMaker.key(replaced));
        assertEquals(1, WebDavCoverMaker.todo(Collections.singletonList(
                new WebDavCoverMaker.Need(replaced, true, true)), failed, BOOKS).size());
    }
}
