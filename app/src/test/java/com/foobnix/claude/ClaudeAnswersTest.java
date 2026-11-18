package com.foobnix.claude;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClaudeAnswersTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File desk() throws Exception {
        return ClaudeAnswers.desk(tmp.getRoot(), "My Book.epub");
    }

    private static File answer(File desk, String name, String feature, String chapter, int format, Object answer,
                               String made) throws Exception {
        final JSONObject o = new JSONObject().put("format", format).put("feature", feature).put("book", "My Book.epub")
                .put("chapter", chapter).put("by", "Claude Code").put("made", made).put("answer", answer);
        final File f = new File(new File(desk, ClaudeAnswers.ANSWERS), name);
        ClaudeAnswers.write(f, o.toString());
        return f;
    }

    @Test
    public void deskIsNamedAfterTheBookFile() throws Exception {
        assertEquals(new File(tmp.getRoot(), "claude/books/My Book.epub"), desk());
        assertEquals("a_b.pdf", ClaudeAnswers.folderName("a/b.pdf"));
        assertTrue(ClaudeAnswers.isDeskFile("claude/books/x/answers/a.json"));
        assertFalse(ClaudeAnswers.isDeskFile("assist/x.json"));
    }

    @Test
    public void findsTheAnswerOfItsFeatureAndChapter() throws Exception {
        final File desk = desk();
        answer(desk, "prequestions~3.json", "prequestions", "part one#0", 1, "[1]", "2026-11-18T20:00:00Z");
        answer(desk, "zoom~3.json", "zoom", "part one#0", 1, "{}", "2026-11-18T20:00:00Z");

        assertNull(ClaudeAnswers.find(desk, "prequestions", "part two#0", 1));
        assertTrue(ClaudeAnswers.has(desk, "prequestions", "part one#0", 1));
        // another format is no answer to wait for
        assertFalse(ClaudeAnswers.has(desk, "prequestions", "part one#0", 2));
        final ClaudeAnswers.Answer a = ClaudeAnswers.find(desk, "prequestions", "part one#0", 1);
        assertNotNull(a);
        assertEquals("[1]", a.text);
        assertEquals("Claude Code (WebDAV)", a.model());
        // the other feature's answer stays
        assertTrue(new File(desk, "answers/zoom~3.json").isFile());
    }

    @Test
    public void aJsonReplyMayStandAsJson() throws Exception {
        final File desk = desk();
        answer(desk, "xray~1.json", "xray", "k#0", 1, new JSONObject().put("ratings", 1), "2026-11-18T20:00:00Z");
        final ClaudeAnswers.Answer a = ClaudeAnswers.find(desk, "xray", "k#0", 1);
        assertEquals(1, new JSONObject(a.text).getInt("ratings"));
    }

    @Test
    public void usedAndRejectedMoveTheFile() throws Exception {
        final File desk = desk();
        answer(desk, "zoom~1.json", "zoom", "a#0", 1, "x", "2026-11-18T20:00:00Z");
        answer(desk, "zoom~2.json", "zoom", "b#0", 1, "y", "2026-11-18T20:00:00Z");
        ClaudeAnswers.used(ClaudeAnswers.find(desk, "zoom", "a#0", 1));
        ClaudeAnswers.rejected(ClaudeAnswers.find(desk, "zoom", "b#0", 1), "cut off");
        assertTrue(new File(desk, "answers/used/zoom~1.json").isFile());
        assertTrue(new File(desk, "answers/rejected/zoom~2.json").isFile());
        assertEquals("cut off\n", ClaudeAnswers.text(new File(desk, "answers/rejected/zoom~2.json.reason.txt")));
        assertFalse(ClaudeAnswers.has(desk, "zoom", "a#0", 1));
        assertNull(ClaudeAnswers.find(desk, "zoom", "b#0", 1));
    }

    @Test
    public void anotherFormatIsRejectedWithItsReason() throws Exception {
        final File desk = desk();
        answer(desk, "ideas~0.json", "ideas", "c#0", 1, "[]", "2026-11-18T20:00:00Z");
        assertNull(ClaudeAnswers.find(desk, "ideas", "c#0", 2));
        final File reason = new File(desk, "answers/rejected/ideas~0.json.reason.txt");
        assertTrue(reason.isFile());
        assertTrue(ClaudeAnswers.text(reason).contains("format 1"));
    }

    @Test
    public void emptyAndUnreadableAnswersAreRejected() throws Exception {
        final File desk = desk();
        answer(desk, "summary~0.json", "summary", "c#0", 1, "  ", "2026-11-18T20:00:00Z");
        ClaudeAnswers.write(new File(desk, "answers/broken.json"), "{\"feature\": ");
        assertNull(ClaudeAnswers.find(desk, "summary", "c#0", 1));
        assertTrue(new File(desk, "answers/rejected/summary~0.json").isFile());
        assertTrue(new File(desk, "answers/rejected/broken.json").isFile());
    }

    @Test
    public void theNewestAnswerWinsAndTheOlderIsRejected() throws Exception {
        final File desk = desk();
        answer(desk, "old.json", "updated", "c#0", 1, "old", "2026-11-18T19:00:00Z");
        answer(desk, "new.json", "updated", "c#0", 1, "new", "2026-11-18T21:00:00Z");
        assertEquals("new", ClaudeAnswers.find(desk, "updated", "c#0", 1).text);
        assertTrue(new File(desk, "answers/rejected/old.json").isFile());
        assertTrue(new File(desk, "answers/new.json").isFile());
    }

    @Test
    public void listsChaptersAndFeaturesWaiting() throws Exception {
        final File desk = desk();
        answer(desk, "ideas~1.json", "ideas", "a#0", 1, "[]", "t");
        answer(desk, "ideas~2.json", "ideas", "b#0", 1, "[]", "t");
        answer(desk, "summary~book.json", "summary", ClaudeAnswers.BOOK, 1, "s", "t");
        final List<String> chapters = ClaudeAnswers.chapters(desk, "ideas", 1);
        assertTrue(ClaudeAnswers.chapters(desk, "ideas", 2).isEmpty());
        assertEquals(2, chapters.size());
        assertTrue(chapters.containsAll(Arrays.asList("a#0", "b#0")));
        assertEquals(Arrays.asList("ideas"), ClaudeAnswers.waiting(desk, Arrays.asList("a#0")));
        assertEquals(2, ClaudeAnswers.waiting(desk, null).size());
        assertTrue(ClaudeAnswers.waiting(new File(tmp.getRoot(), "none"), null).isEmpty());
    }
}
