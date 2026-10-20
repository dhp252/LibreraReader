package com.foobnix.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void keepsSummariesAndHighlights() throws Exception {
        AiStore store = new AiStore(tmp.getRoot(), "device.Phone");
        store.update("Book.epub", book -> {
            book.putSummary(new AiStore.Summary(AiStore.BOOK, "Book", "It is about a whale.", "m", 5));
            book.putChapter(new AiStore.Chapter("one#0", "One", "m", 6,
                    Arrays.asList(new AiStore.Quote("Call me Ishmael.", 3, AiCategory.MAIN))));
        });

        AiStore.Book loaded = store.load("Book.epub");
        assertEquals("It is about a whale.", loaded.summary(AiStore.BOOK).text);
        assertEquals("Call me Ishmael.", loaded.chapter("one#0").quotes.get(0).text);
        assertEquals(3, loaded.chapter("one#0").quotes.get(0).page);
        assertTrue(new File(tmp.getRoot(), "device.Phone/ai/Book.epub.json").isFile());
    }

    @Test
    public void theNewestCopyOfAnyDeviceWins() throws Exception {
        write("device.Phone/ai/Book.pdf.json", "{\"updated\":100,\"summaries\":[{\"key\":\"@book\",\"text\":\"old\"}]}");
        write("device.Tablet/ai/Book.pdf.json", "{\"updated\":200,\"summaries\":[{\"key\":\"@book\",\"text\":\"new\"}]}");
        write("device.Broken/ai/Book.pdf.json", "{\"updated\":300, cut off");

        AiStore store = new AiStore(tmp.getRoot(), "device.Phone");
        assertEquals("new", store.load("Book.pdf").summary(AiStore.BOOK).text);
    }

    @Test
    public void aChangeStartsFromTheNewestCopy() throws Exception {
        write("device.Tablet/ai/Book.pdf.json", "{\"updated\":" + (System.currentTimeMillis() + 60_000)
                + ",\"summaries\":[{\"key\":\"@book\",\"text\":\"from the tablet\"}]}");

        AiStore phone = new AiStore(tmp.getRoot(), "device.Phone");
        AiStore.Book written = phone.update("Book.pdf", book ->
                book.putSummary(new AiStore.Summary("ch#0", "Ch", "from the phone", "m", 1)));

        AiStore.Book loaded = phone.load("Book.pdf");
        assertEquals("from the tablet", loaded.summary(AiStore.BOOK).text);
        assertEquals("from the phone", loaded.summary("ch#0").text);
        // newer than the tablet's copy although the tablet's clock runs ahead
        assertEquals(written.updated, loaded.updated);
    }

    @Test
    public void removingAChapterAndCounting() throws Exception {
        AiStore store = new AiStore(tmp.getRoot(), "device.Phone");
        store.update("B", book -> {
            book.putChapter(new AiStore.Chapter("a#0", "A", "m", 1, Arrays.asList(
                    new AiStore.Quote("First quote here.", 1, AiCategory.MAIN), new AiStore.Quote("Second quote here.", 2, AiCategory.MAIN))));
            book.putChapter(new AiStore.Chapter("b#0", "B", "m", 1, Arrays.asList(new AiStore.Quote("Third.", 5, AiCategory.MAIN))));
        });
        AiStore.Book book = store.update("B", b -> b.removeChapter("a#0"));
        assertNull(book.chapter("a#0"));
        assertEquals(1, book.quoteCount());
        assertEquals(Arrays.asList("Third."), book.quoteTexts());
    }

    @Test
    public void fileNamesAreSafe() {
        assertEquals("a_b_c_.pdf.json", AiStore.fileName("a/b:c?.pdf"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            longName.append("chapter ");
        }
        String name = AiStore.fileName(longName.toString());
        assertTrue(name.length() < 130);
        assertTrue(name.endsWith(".json"));
    }

    private void write(String path, String text) throws IOException {
        File file = new File(tmp.getRoot(), path);
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void keepsCategoriesAndTheGoal() throws Exception {
        AiStore store = new AiStore(tmp.getRoot(), "device.Phone");
        store.update("Book.epub", book -> {
            book.goal = "negotiate better";
            book.putChapter(new AiStore.Chapter("one#0", "One", "m", 6,
                    Arrays.asList(new AiStore.Quote("Call me Ishmael.", 3, AiCategory.CAVEAT))));
        });
        AiStore.Book read = store.load("Book.epub");
        assertEquals("negotiate better", read.goal);
        assertEquals(AiCategory.CAVEAT, read.chapter("one#0").quotes.get(0).category);
        // never asked is not the same as asked and left empty
        assertNull(new AiStore.Book().goal);
    }

    @Test
    public void highlightsFromBeforeCategoriesAreDropped() throws Exception {
        final String v1 = "{\"version\":1,\"updated\":5,\"summaries\":[{\"key\":\"@book\",\"text\":\"Whale.\"}],"
                + "\"highlights\":[{\"key\":\"one#0\",\"quotes\":[{\"text\":\"Call me Ishmael.\",\"page\":3}]}]}";
        final AiStore.Book book = AiStore.Book.fromJson(v1);
        assertTrue(book.highlights.isEmpty());
        assertEquals("Whale.", book.summary(AiStore.BOOK).text);
    }

    @Test
    public void keepsIdeasAndParagraphs() throws Exception {
        final AiStore.Book book = new AiStore.Book();
        book.putChapter(new AiStore.Chapter("one#0", "One", "m", 6,
                Arrays.asList(new AiStore.Quote("Call me Ishmael.", 3, AiCategory.MAIN, 1)),
                Arrays.asList(new AiStore.Idea(1, "The narrator goes to sea.", AiCategory.MAIN, -1, true, 3),
                        new AiStore.Idea(2, "Whaling pays poorly.", AiCategory.EVIDENCE, 1, false, -1)),
                Arrays.asList(new AiStore.Quote("Some years ago, never mind how long precisely.", 3, AiCategory.MAIN, 1)),
                true));
        final AiStore.Chapter read = AiStore.Book.fromJson(book.toJson()).chapter("one#0");
        assertEquals(1, read.quotes.get(0).idea);
        assertEquals(2, read.ideas.size());
        assertEquals("Whaling pays poorly.", read.idea(2).text);
        assertEquals(1, read.idea(2).parent);
        assertEquals(AiCategory.EVIDENCE, read.idea(2).category);
        assertEquals(1, read.notLocated());
        assertEquals(3, read.idea(1).page);
        assertEquals("Some years ago, never mind how long precisely.", read.paragraphs.get(0).text);
        assertTrue(read.incomplete);
    }

    @Test
    public void keyPassagesFromBeforeIdeasStillRead() throws Exception {
        final String v2 = "{\"version\":2,\"updated\":5,\"highlights\":[{\"key\":\"one#0\",\"quotes\":"
                + "[{\"text\":\"Call me Ishmael.\",\"page\":3,\"category\":\"main\"}]}]}";
        final AiStore.Chapter read = AiStore.Book.fromJson(v2).chapter("one#0");
        assertEquals(-1, read.quotes.get(0).idea);
        assertTrue(read.ideas.isEmpty());
        assertTrue(read.paragraphs.isEmpty());
        assertEquals(0, read.notLocated());
    }
}
