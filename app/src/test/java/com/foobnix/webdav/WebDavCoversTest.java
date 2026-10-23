package com.foobnix.webdav;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebDavCoversTest {

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Test
    public void coversAreNamedAfterTheBookFileWhateverItsFolder() {
        assertEquals("Books/.covers/novel.epub.jpg", WebDavCovers.coverOf("Books/novel.epub"));
        assertEquals("Books/.covers/Dune é.pdf.jpg", WebDavCovers.coverOf("/Books/Sci-fi/Old/Dune é.pdf"));
        assertEquals(WebDavCovers.coverOf("Books/Sci-fi/Dune é.pdf"), WebDavCovers.coverOf("Books/Read/Dune é.pdf"));
        assertNull(WebDavCovers.coverOf("profile.Librera/app-State.json"));
        assertNull(WebDavCovers.coverOf("Books"));

        assertEquals("Dune é.pdf", WebDavCovers.bookOfCover("Books/.covers/Dune é.pdf.jpg"));
        assertNull(WebDavCovers.bookOfCover("Books/.covers/notes.txt"));
        assertNull(WebDavCovers.bookOfCover("Books/novel.epub.jpg"));
        // the first layout's covers, kept in folders, are not taken for flat ones
        assertNull(WebDavCovers.bookOfCover("Books/.covers/Sci-fi/Dune é.pdf.jpg"));
    }

    @Test
    public void booksAreToldByFileNameTheFirstPathWinning() {
        final Map<String, String> byName = WebDavCovers.byFileName(set("b/Dune.epub", "a/Dune.epub", "Emma.pdf"));

        assertEquals(2, byName.size());
        assertEquals("a/Dune.epub", byName.get("Dune.epub"));
        assertEquals("Emma.pdf", byName.get("Emma.pdf"));
    }

    @Test
    public void itemPathsCarryTheServerPathAndTheCoverVersion() {
        final WebDavClient.Entry cover = new WebDavClient.Entry("Books/.covers/a:b.pdf.jpg", false, 30_000, 1, "\"v1\"");
        final String item = WebDavCovers.bookItem("Books/a:b.pdf", cover);

        assertTrue(WebDavCovers.isItem(item));
        assertFalse(WebDavCovers.isFolderItem(item));
        assertTrue(WebDavCovers.hasCover(item));
        assertEquals("Books/a:b.pdf", WebDavCovers.serverPathOf(item));
        assertEquals("a:b.pdf", WebDavCovers.nameOf(item));

        // a changed cover is another item path, so another Glide cache key
        final WebDavClient.Entry newer = new WebDavClient.Entry("Books/.covers/a:b.pdf.jpg", false, 31_000, 2, "\"v2\"");
        assertFalse(item.equals(WebDavCovers.bookItem("Books/a:b.pdf", newer)));

        final String bare = WebDavCovers.bookItem("Books/a.pdf", null);
        assertFalse(WebDavCovers.hasCover(bare));
        assertEquals("Books/a.pdf", WebDavCovers.serverPathOf(bare));

        final String folder = WebDavCovers.folderItem("Books/Sci-fi");
        assertTrue(WebDavCovers.isFolderItem(folder));
        assertFalse(WebDavCovers.hasCover(folder));
        assertEquals("Books/Sci-fi", WebDavCovers.serverPathOf(folder));

        assertFalse(WebDavCovers.isItem("/storage/emulated/0/Librera/Books/a.pdf"));
        assertFalse(WebDavCovers.isItem(null));
    }

    @Test
    public void localBooksWithoutCoverAreUploaded() {
        WebDavCovers.Plan plan = WebDavCovers.plan(set("a.pdf", "b.epub", "c.pdf"), set("a.pdf", "b.epub", "c.pdf"),
                set("a.pdf"), set("c.pdf"));

        assertEquals(Collections.singletonList("b.epub"), plan.upload);
        assertTrue(plan.delete.isEmpty());
    }

    @Test
    public void coversOfBooksGoneFromTheServerAreDeleted() {
        WebDavCovers.Plan plan = WebDavCovers.plan(set("here.pdf"), set("kept.pdf"),
                set("kept.pdf", "gone.pdf", "here.pdf"), set());

        assertEquals(Collections.singletonList("gone.pdf"), plan.delete);
    }

    @Test
    public void noCoverIsDeletedWithoutABooksListing() {
        // the Books folder missing on the server, or emptied: not a reason to drop every cover
        assertTrue(WebDavCovers.plan(set(), null, set("a.pdf"), set()).delete.isEmpty());
        assertTrue(WebDavCovers.plan(set(), set(), set("a.pdf"), set()).delete.isEmpty());
    }
}
