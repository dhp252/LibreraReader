package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foobnix.ai.BookChapters;
import com.foobnix.assist.skeleton.SkeletonParts;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EditionNoteTest {

    @Test
    public void keysCarryTheChapterAndANumber() {
        assertEquals("edition:note:book i#0:2", EditionNote.key("book i#0", 2));
        assertEquals("book i#0", EditionNote.chapterOf("edition:note:book i#0:2"));
        assertEquals(2, EditionNote.numberOf("edition:note:book i#0:2"));
        // a chapter key may hold colons itself
        assertEquals("@pages:40", EditionNote.chapterOf(EditionNote.key("@pages:40", 0)));
        assertNull(EditionNote.chapterOf("edition:words:x"));
        assertNull(EditionNote.chapterOf(null));
        assertEquals(-1, EditionNote.numberOf("edition:note:x:y"));
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final EditionNote n = new EditionNote(" Babbage's  fourth cause ", "Cheaper labour for the easy steps.",
                "well established", "now the Babbage principle", " Of the Division of Labour ", 9L);
        final EditionNote read = EditionNote.fromJson(new JSONObject(n.toJson().toString()));
        assertEquals("Babbage's fourth cause", read.title);
        assertEquals("Cheaper labour for the easy steps.", read.body);
        assertEquals("well established", read.settled);
        assertEquals("now the Babbage principle", read.settledNote);
        assertEquals("Of the Division of Labour", read.chapterTitle);
        assertEquals(9L, read.date);
        assertTrue(read.same("Babbage's fourth  cause", "Cheaper labour for the easy steps. "));
        assertNull(EditionNote.fromJson(new JSONObject()));
        assertNull(EditionNote.fromJson(null));
    }

    @Test
    public void aNoteKeepsItsKeyAndANewOneTakesAFreeNumber() {
        final Map<String, EditionNote> taken = new HashMap<>();
        assertEquals("edition:note:ch:3", EditionNote.keyFor("ch", 3, "T", "B", taken));
        taken.put("edition:note:ch:3", new EditionNote("T", "B", "", "", "", 0));
        // the same note again: its own key, wherever it sits now
        assertEquals("edition:note:ch:3", EditionNote.keyFor("ch", 0, "T", "B", taken));
        // the chapter's notes were asked for again and another note has place 3 now
        assertEquals("edition:note:ch:4", EditionNote.keyFor("ch", 3, "Other", "B", taken));
        assertEquals("edition:note:ch:0", EditionNote.keyFor("ch", -5, "Other", "B", taken));
    }

    @Test
    public void theOutlineKeepsTheBooksOrderAndTitles() {
        final EditionOutline o = new EditionOutline().add("book i#0", " Book I ", true).add("book i#0/chapter 1#0",
                "CHAPTER 1", false).add("book i#0", "again", true);
        assertEquals(2, o.entries.size());
        assertEquals(0, o.order("book i#0"));
        assertEquals(1, o.order("book i#0/chapter 1#0"));
        assertEquals(-1, o.order("other"));
        assertEquals("Book I", o.title("book i#0"));
        assertEquals("CHAPTER 1", o.title("book i#0/chapter 1#0"));
        assertEquals("", o.title("other"));
        assertTrue(EditionOutline.of(null).isEmpty());
    }

    @Test
    public void theOutlineFromATableOfContents() {
        // Book I holds two chapters; the conclusion stands alone
        final java.util.List<SkeletonParts.Part> parts = SkeletonParts.group(Arrays.asList(
                new BookChapters.Entry("Book I", 0, 0), new BookChapters.Entry("Of Labour", 1, 0),
                new BookChapters.Entry("Of Money", 1, 10), new BookChapters.Entry("Conclusion", 0, 20)), 30);
        final EditionOutline o = EditionOutline.of(parts);
        assertEquals(4, o.entries.size());
        assertEquals("book i#0", o.entries.get(0).key);
        assertTrue(o.entries.get(0).part);
        assertEquals("Of Labour", o.entries.get(1).title);
        assertEquals("book i#0/of money#0", o.entries.get(2).key);
        assertEquals("conclusion#0", o.entries.get(3).key);
        assertTrue(!o.entries.get(3).part);
    }
}
