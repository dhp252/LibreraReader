package com.foobnix.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** The ✦ menu's colours: blue when a result is stored, grey when opening it asks the API. */
public class AiStoredTest {

    @Test
    public void colours() {
        assertEquals(Integer.valueOf(AiStored.STORED), AiStored.color(true));
        assertEquals(Integer.valueOf(AiStored.NOT_STORED), AiStored.color(false));
        assertNull(AiStored.color(null));
    }

    @Test
    public void assistItemOfTheChapter() {
        final Set<String> keys = new HashSet<>(Arrays.asList("xray:one#0", "skeleton:book"));
        assertTrue(AiStored.chapterItem(keys, "xray:", "one#0"));
        assertFalse(AiStored.chapterItem(keys, "xray:", "two#0"));
        assertFalse(AiStored.chapterItem(keys, "zoom:", "one#0"));
        // the chapter is not known yet: no colour
        assertNull(AiStored.chapterItem(keys, "xray:", null));
    }

    @Test
    public void summariesAndKeyPassages() {
        final AiStore.Book b = new AiStore.Book();
        b.putSummary(new AiStore.Summary("one#0", "One", "S", "M", 1));
        b.emptyKeys.add("two#0");
        assertTrue(AiStored.chapterSummary(b, "one#0"));
        assertFalse(AiStored.chapterSummary(b, "two#0"));
        assertNull(AiStored.chapterSummary(b, null));
        assertFalse(AiStored.keyPassages(b, "one#0"));
        assertTrue(AiStored.keyPassages(b, "two#0"));
        assertNull(AiStored.keyPassages(b, null));
    }
}
