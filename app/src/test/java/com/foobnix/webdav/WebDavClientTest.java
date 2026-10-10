package com.foobnix.webdav;

import org.junit.Test;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebDavClientTest {

    private static long utc(int y, int m, int d, int h, int min, int s) {
        return ZonedDateTime.of(y, m, d, h, min, s, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** Nextcloud / sabre/dav: relative hrefs, "d:" prefix, a 404 propstat for missing props. */
    @Test
    public void parsesNextcloudListing() throws IOException {
        String xml = "<?xml version=\"1.0\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\" xmlns:s=\"http://sabredav.org/ns\" xmlns:oc=\"http://owncloud.org/ns\">"
                + "<d:response><d:href>/remote.php/dav/files/me/Librera/</d:href>"
                + "<d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>"
                + "<d:getlastmodified>Wed, 07 Oct 2026 10:15:00 GMT</d:getlastmodified><d:getetag>\"root\"</d:getetag></d:prop>"
                + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
                + "<d:propstat><d:prop><d:getcontentlength/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>"
                + "</d:response>"
                + "<d:response><d:href>/remote.php/dav/files/me/Librera/Books/Chi%E1%BA%BFn%20tranh.epub</d:href>"
                + "<d:propstat><d:prop><d:resourcetype/><d:getcontentlength>12345</d:getcontentlength>"
                + "<d:getlastmodified>Wed, 07 Oct 2026 10:15:00 GMT</d:getlastmodified><d:getetag>\"e1\"</d:getetag></d:prop>"
                + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "<d:response><d:href>/remote.php/dav/files/me/Librera/profile.Librera/</d:href>"
                + "<d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>"
                + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        HttpUrl root = HttpUrl.parse("https://cloud.example.com/remote.php/dav/files/me/Librera/");

        List<WebDavClient.Entry> entries = WebDavClient.parseMultistatus(xml, root, root);

        assertEquals(3, entries.size());
        assertEquals("", entries.get(0).path);
        assertTrue(entries.get(0).directory);

        WebDavClient.Entry book = entries.get(1);
        assertEquals("Books/Chiến tranh.epub", book.path);
        assertEquals("Chiến tranh.epub", book.getName());
        assertFalse(book.directory);
        assertEquals(12345, book.size);
        assertEquals("\"e1\"", book.etag);
        assertEquals(utc(2026, 10, 7, 10, 15, 0), book.lastModified);

        assertEquals("profile.Librera", entries.get(2).path);
        assertTrue(entries.get(2).directory);
    }

    /** Apache mod_dav / many NAS: absolute URL hrefs, "D:" and "lp1:" prefixes for DAV:. */
    @Test
    public void parsesApacheListingWithAbsoluteHrefs() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<D:multistatus xmlns:D=\"DAV:\"><D:response xmlns:lp1=\"DAV:\">"
                + "<D:href>http://nas.local/webdav/Librera/a%20b.json</D:href>"
                + "<D:propstat><D:prop><lp1:resourcetype/><lp1:getcontentlength>7</lp1:getcontentlength>"
                + "<lp1:getlastmodified>Thu, 08 Oct 2026 01:02:03 GMT</lp1:getlastmodified><lp1:getetag>\"7-abc\"</lp1:getetag>"
                + "</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>";
        HttpUrl root = HttpUrl.parse("http://nas.local/webdav/Librera/");

        List<WebDavClient.Entry> entries = WebDavClient.parseMultistatus(xml, root, root);

        assertEquals(1, entries.size());
        assertEquals("a b.json", entries.get(0).path);
        assertEquals(7, entries.get(0).size);
        assertEquals("\"7-abc\"", entries.get(0).etag);
        assertEquals(utc(2026, 10, 8, 1, 2, 3), entries.get(0).lastModified);
    }

    @Test
    public void entriesOutsideTheRootAreIgnored() throws IOException {
        String xml = "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/other/x.json</d:href>"
                + "<d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
                + "</d:response></d:multistatus>";
        HttpUrl root = HttpUrl.parse("https://h/dav/Librera/");
        assertTrue(WebDavClient.parseMultistatus(xml, root, root).isEmpty());
        assertTrue(WebDavClient.parseMultistatus("", root, root).isEmpty());
    }

    @Test
    public void buildsEncodedUrlsBelowTheRoot() {
        WebDavClient client = new WebDavClient(new OkHttpClient(), "https://h/dav/Librera");
        assertEquals("https://h/dav/Librera/Books/Chi%E1%BA%BFn%20tranh.epub",
                client.url("Books/Chiến tranh.epub", false).toString());
        assertEquals("https://h/dav/Librera/Books/", client.url("/Books/", true).toString());
        assertEquals("https://h/dav/Librera/", client.url("", true).toString());
    }

    @Test
    public void parsesHttpDatesAndPaths() {
        assertEquals(utc(2026, 10, 7, 10, 15, 0), WebDavClient.parseHttpDate("Wed, 07 Oct 2026 10:15:00 GMT"));
        assertEquals(0, WebDavClient.parseHttpDate("not a date"));
        assertEquals(0, WebDavClient.parseHttpDate(null));

        assertEquals("a/b", WebDavClient.trimSlashes("//a/b/"));
        assertEquals("a", WebDavClient.parentOf("a/b.json"));
        assertEquals("", WebDavClient.parentOf("b.json"));
        assertNull(null);
    }
}
