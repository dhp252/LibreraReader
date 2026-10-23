package com.foobnix.webdav;

import com.foobnix.dao2.FileMeta;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WebDavBookMetaTest {

    @Test
    public void metadataLivesBesideTheCover() {
        assertEquals("Books/.covers/Dune.epub.json", WebDavBookMeta.metaOf("Books/Sci-fi/Dune.epub"));
        assertEquals("Dune.epub.json", WebDavBookMeta.metaNameOf("Dune.epub"));
        assertNull(WebDavBookMeta.metaOf("profile.Librera/app-State.json"));
        assertEquals("Dune.epub", WebDavCovers.bookOfMeta("Books/.covers/Dune.epub.json"));
        assertNull(WebDavCovers.bookOfMeta("Books/.covers/Dune.epub.jpg"));
        assertNull(WebDavCovers.bookOfCover("Books/.covers/Dune.epub.json"));
    }

    @Test
    public void whatOneDeviceReadsAnotherShows() throws Exception {
        final FileMeta read = new FileMeta("/sdcard/Librera/Books/Dune.epub");
        read.setTitle("Dune");
        read.setAuthor("Frank Herbert");
        read.setSequence("Dune Chronicles");
        read.setSIndex(1);
        read.setPages(612);
        read.setYear(1965);
        read.setLang("en");
        read.setPublisher("Chilton");

        final FileMeta shown = new FileMeta("webdav:0:Books/Dune.epub");
        shown.setTitle("Dune.epub");
        WebDavBookMeta.apply(WebDavBookMeta.toJson(read), shown);

        assertEquals("Dune", shown.getTitle());
        assertEquals("Frank Herbert", shown.getAuthor());
        assertEquals("Dune Chronicles", shown.getSequence());
        assertEquals(Integer.valueOf(1), shown.getSIndex());
        assertEquals(Integer.valueOf(612), shown.getPages());
        assertEquals(Integer.valueOf(1965), shown.getYear());
        assertEquals("en", shown.getLang());
        assertEquals("Chilton", shown.getPublisher());
        assertNull(shown.getGenre());
    }

    @Test
    public void emptyOrDamagedMetadataKeepsTheFileName() {
        final FileMeta shown = new FileMeta("webdav:0:Books/x.pdf");
        shown.setTitle("x.pdf");
        WebDavBookMeta.apply("{\"title\":\"\",\"author\":null}", shown);
        assertEquals("x.pdf", shown.getTitle());
        assertNull(shown.getAuthor());
        WebDavBookMeta.apply("not json", shown);
        assertEquals("x.pdf", shown.getTitle());
    }
}
