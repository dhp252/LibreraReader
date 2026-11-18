package com.foobnix.claude;

import com.foobnix.ai.BookChapters;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClaudeDeskTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final List<BookChapters.Range> CHAPTERS = Arrays.asList(
            new BookChapters.Range(0, 1, "", BookChapters.FRONT),
            new BookChapters.Range(1, 3, "One", "one#0"),
            new BookChapters.Range(3, 4, "Index", "index#0"));

    @Test
    public void textHasChapterAndPageLines() {
        final String text = ClaudeDesk.text(CHAPTERS, new String[]{"Cover", "First page.", "", "a b c"});
        assertTrue(text.contains("=== chapter 0:  [@front] ===\n--- page 0 ---\nCover\n"));
        assertTrue(text.contains("=== chapter 1: One [one#0] ===\n--- page 1 ---\nFirst page.\n"));
        // an empty page leaves no page line
        assertFalse(text.contains("--- page 2 ---"));
        // side matter: named, no text
        assertTrue(text.contains("=== chapter 2: Index [index#0] (side matter: no text) ==="));
        assertFalse(text.contains("a b c"));
    }

    @Test
    public void bookJsonListsChaptersWithKeys() throws Exception {
        final ClaudeDesk.Book b = book();
        final JSONObject o = ClaudeDesk.bookJson(b);
        assertEquals(ClaudeDesk.DESK_FORMAT, o.getInt("format"));
        assertEquals("one#0", o.getJSONArray("chapters").getJSONObject(1).getString("key"));
        assertEquals(1, o.getJSONArray("chapters").getJSONObject(1).getInt("n"));
        assertEquals(1200, o.getJSONArray("chapters").getJSONObject(1).getLong("chars"));
        assertTrue(o.getJSONArray("chapters").getJSONObject(2).getBoolean("side"));
        assertTrue(o.isNull("file"));
        assertEquals("0-1", o.getJSONObject("skeleton").getJSONArray("parts").getJSONObject(0).getString("chapters"));
        assertTrue(o.getJSONArray("categories").length() > 0);
        assertEquals("2026-11-18T19:00:00Z", ClaudeDesk.iso(1795028400000L));
    }

    @Test
    public void textIsCurrentForTheSameFileAndLayout() throws Exception {
        final File desk = tmp.newFolder("desk");
        final ClaudeDesk.Book b = book();
        assertFalse(ClaudeDesk.textCurrent(desk, 10, 20, "1x2@3"));
        ClaudeDesk.write(new File(desk, ClaudeDesk.TEXT), "text");
        ClaudeDesk.write(new File(desk, ClaudeDesk.BOOK_JSON), ClaudeDesk.bookJson(b).toString());
        assertTrue(ClaudeDesk.textCurrent(desk, 10, 20, "1x2@3"));
        assertFalse(ClaudeDesk.textCurrent(desk, 11, 20, "1x2@3"));
        assertFalse(ClaudeDesk.textCurrent(desk, 10, 20, "1x2@4"));
    }

    @Test
    public void anUnchangedFileIsNotWrittenAgain() throws Exception {
        final File f = new File(tmp.newFolder("d"), "a.txt");
        assertTrue(ClaudeDesk.write(f, "x"));
        assertFalse(ClaudeDesk.write(f, "x"));
        assertTrue(ClaudeDesk.write(f, "y"));
        assertEquals("y", ClaudeAnswers.text(f));
    }

    private static ClaudeDesk.Book book() {
        final ClaudeDesk.Book b = new ClaudeDesk.Book();
        b.book = "b.epub";
        b.title = "B";
        b.size = 10;
        b.modified = 20;
        b.layout = "1x2@3";
        b.pages = 4;
        b.chapters = CHAPTERS;
        b.chars = Collections.singletonMap("one#0", 1200L);
        b.skeletonParts = Collections.singletonList(new String[]{"Part", "0-1"});
        b.skeletonChapters = CHAPTERS.subList(1, 2);
        b.features = Arrays.asList("ideas", "summary");
        b.prepared = 1795028400000L;
        return b;
    }
}
