package com.foobnix.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiPartsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aRunBrokenOffGoesOnFromTheNextPart() throws Exception {
        final File folder = new File(tmp.getRoot(), "ai-work");
        AiParts parts = AiParts.open(folder, "Book.epub", "1080x2340|18|m|180000|4");
        assertEquals(Arrays.asList(0, 1, 2, 3), parts.missing(4));
        parts.put(0, "Part one in short.");
        parts.put(2, "Part three, done out of order.");

        // the app was killed: the next run finds them
        parts = AiParts.open(folder, "Book.epub", "1080x2340|18|m|180000|4");
        assertEquals("Part one in short.", parts.get(0));
        assertNull(parts.get(1));
        assertEquals("Part three, done out of order.", parts.get(2));
        assertNull(parts.get(7));
        assertEquals(Arrays.asList(1, 3), parts.missing(4));
    }

    @Test
    public void otherPartsStartFreshAndAFinishedSummaryLeavesNothing() throws Exception {
        final File folder = new File(tmp.getRoot(), "ai-work");
        AiParts.open(folder, "Book.epub", "key A").put(0, "kept under A");
        assertNull("another model, layout or part size: other parts", AiParts.open(folder, "Book.epub", "key B").get(0));
        assertNull("another book", AiParts.open(folder, "Other.epub", "key A").get(0));

        AiParts.open(folder, "Book.epub", "").clear();
        assertFalse(new File(folder, "Book.epub.json").exists());
        assertNull(AiParts.open(folder, "Book.epub", "key A").get(0));
    }

    @Test
    public void aHalfWrittenFileCountsAsNone() throws Exception {
        final File folder = new File(tmp.getRoot(), "ai-work");
        assertTrue(folder.mkdirs());
        java.nio.file.Files.write(new File(folder, "Book.epub.json").toPath(), "{\"key\":\"k\",\"parts\":[\"a".getBytes());
        assertNull(AiParts.open(folder, "Book.epub", "k").get(0));
    }
}
