package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkimPackTest {

    /** A page: {@code paragraphs} paragraphs of {@code words} words, named after the page. */
    private static String page(int page, int paragraphs, int words) {
        final StringBuilder out = new StringBuilder();
        for (int p = 0; p < paragraphs; p++) {
            if (p > 0) {
                out.append("\n\n");
            }
            for (int w = 0; w < words; w++) {
                out.append(w == 0 ? "" : " ").append("p").append(page).append("w").append(w);
            }
        }
        return out.toString();
    }

    private static String[] book(int pages, int paragraphs, int words) {
        final String[] text = new String[pages];
        for (int p = 0; p < pages; p++) {
            text[p] = page(p, paragraphs, words);
        }
        return text;
    }

    /** Chapters of {@code size} pages, the first page front matter. */
    private static List<BookChapters.Range> chapters(int count, int size) {
        final List<BookChapters.Range> list = new ArrayList<>();
        list.add(new BookChapters.Range(0, 1, "", BookChapters.FRONT));
        for (int i = 0; i < count; i++) {
            list.add(new BookChapters.Range(1 + i * size, 1 + (i + 1) * size, "Chapter " + (i + 1), "c" + i));
        }
        return list;
    }

    @Test
    public void aSmallBookGoesWhole() {
        final String[] text = book(4, 1, 10);
        final String pack = SkimPack.build(chapters(3, 1), text, 100_000);
        assertTrue(pack.contains("<chapter n=\"1\" title=\"Chapter 1\" words=\"10\">"));
        assertTrue(pack.contains(text[3]));
        assertFalse("front matter is left out", pack.contains("p0w"));
        assertFalse(pack.contains("<opening>"));
    }

    @Test
    public void sixtyChaptersFitTheBudget() {
        final String[] text = book(1 + 60 * 10, 4, 120);
        final String pack = SkimPack.build(chapters(60, 10), text, 120_000);
        assertTrue("pack " + pack.length(), pack.length() <= 120_000);
        assertTrue(pack.contains("<chapter n=\"60\""));
        assertTrue(pack.contains("<opening>"));
        assertTrue(pack.contains("<ending>"));
        // every chapter keeps its true length
        assertTrue(pack.contains("words=\"4800\""));
    }

    @Test
    public void theIntroductionAndConclusionGetMore() {
        final String[] text = book(1 + 10 * 10, 6, 60);
        final String pack = SkimPack.build(chapters(10, 10), text, 30_000);
        final int first = section(pack, 1).length();
        final int middle = section(pack, 5).length();
        final int last = section(pack, 10).length();
        assertTrue(first + " vs " + middle, first > 2 * middle);
        assertTrue(last + " vs " + middle, last > 2 * middle);
    }

    @Test
    public void openingsAndEndingsKeepWholeParagraphs() {
        final String text = "One two three.\n\nFour five six.\n\nSeven eight nine.";
        assertEquals("One two three.\n\nFour five six.", SkimPack.opening(text, 32));
        assertEquals("Four five six.\n\nSeven eight nine.", SkimPack.ending(text, 35));
        // a single paragraph too long is cut at a word
        assertEquals("One two…", SkimPack.opening("One two three four", 9));
        assertEquals("…three four", SkimPack.ending("One two three four", 11));
    }

    @Test
    public void wordsAreCountedByWhiteSpace() {
        assertEquals(0, SkimPack.words("  "));
        assertEquals(4, SkimPack.words(" a b\n\nc  d "));
    }

    @Test
    public void aChapterWithoutTextIsStillListed() {
        final String[] text = book(4, 1, 10);
        text[2] = null;
        final String pack = SkimPack.build(chapters(3, 1), text, 10_000);
        assertTrue(pack.contains("<chapter n=\"2\" title=\"Chapter 2\" words=\"0\">\n</chapter>"));
        assertEquals("", SkimPack.build(Arrays.asList(new BookChapters.Range(0, 1, "", BookChapters.FRONT)), text, 10_000));
    }

    private static String section(String pack, int n) {
        final int start = pack.indexOf("<chapter n=\"" + n + "\"");
        return pack.substring(start, pack.indexOf("</chapter>", start));
    }
}
