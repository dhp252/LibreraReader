package com.foobnix.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageEdgesTest {

    private static final float LINE = 0.02f;

    /** A page: optional head, body lines, optional foot; each line's words spread over the width. */
    private static List<PageEdges.Word> page(String head, List<String> body, String foot) {
        final List<PageEdges.Word> words = new ArrayList<>();
        if (head != null) {
            add(words, head, 0.04f);
        }
        float y = 0.1f;
        for (String line : body) {
            add(words, line, y);
            y += 1.5f * LINE;
        }
        if (foot != null) {
            add(words, foot, 0.94f);
        }
        return words;
    }

    private static void add(List<PageEdges.Word> words, String line, float top) {
        final String[] parts = line.split(" ");
        for (int i = 0; i < parts.length; i++) {
            words.add(new PageEdges.Word(parts[i], 0.1f + 0.8f * i / parts.length, top, top + LINE));
            // MuPDF gives the space between words as a word of its own
            words.add(new PageEdges.Word(" ", 0.1f + 0.8f * i / parts.length + 0.01f, top, top + LINE));
        }
    }

    /** Twenty lines of text; no two pages have a line alike, numbers aside. */
    private static List<String> body(int n) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            lines.add("line " + i + " of page" + (char) ('a' + n) + " says something new " + (char) ('a' + i));
        }
        return lines;
    }

    /** Words of the page left once its heads are out. */
    private static String kept(List<PageEdges.Word> words, Set<Integer> heads) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (!heads.contains(i) && !words.get(i).text.trim().isEmpty()) {
                out.append(words.get(i).text).append(' ');
            }
        }
        return out.toString().trim();
    }

    /** The heads of page {@code n} of the book, judged by the pages near it. */
    private static Set<Integer> heads(List<List<PageEdges.Word>> book, int n) {
        final List<PageEdges.Page> nearby = new ArrayList<>();
        for (int p = Math.max(0, n - PageEdges.NEAR); p <= n + PageEdges.NEAR && p < book.size(); p++) {
            if (p != n) {
                nearby.add(PageEdges.page(PageEdges.lines(book.get(p))));
            }
        }
        return PageEdges.heads(PageEdges.page(PageEdges.lines(book.get(n))), nearby);
    }

    @Test
    public void runningHeadsAndPageNumbersGo() {
        final List<List<PageEdges.Word>> book = new ArrayList<>();
        for (int n = 0; n < 10; n++) {
            // the book's title on even pages, the chapter's on odd ones, the number at the foot
            final String head = n % 2 == 0 ? (40 + n) + " THE PSYCHOLOGY OF MONEY" : "NO ONE'S CRAZY " + (40 + n);
            book.add(page(head, body(n), String.valueOf(40 + n)));
        }
        final String left = kept(book.get(5), heads(book, 5));
        assertTrue(left, left.startsWith("line 0 of pagef"));
        assertTrue(left, left.endsWith("new t"));
        assertFalse(left, left.contains("CRAZY"));
    }

    @Test
    public void aChaptersFirstPageLosesOnlyItsNumber() {
        final List<List<PageEdges.Word>> book = new ArrayList<>();
        for (int n = 0; n < 6; n++) {
            book.add(n == 3 ? page(null, Arrays.asList("CHAPTER 3", "Risk", "The text of the chapter begins here and"),
                    "- 43 -") : page("RISK " + (40 + n), body(n), null));
        }
        assertEquals("CHAPTER 3 Risk The text of the chapter begins here and", kept(book.get(3), heads(book, 3)));
    }

    @Test
    public void textAtTheEdgesStays() {
        // no heads: the first and last lines of each page are the book's text and never repeat
        final List<List<PageEdges.Word>> book = new ArrayList<>();
        for (int n = 0; n < 8; n++) {
            book.add(page(null, body(n), null));
        }
        assertTrue(heads(book, 4).isEmpty());
    }

    @Test
    public void pageNumbers() {
        assertTrue(PageEdges.isPageNumber("12", false));
        assertTrue(PageEdges.isPageNumber("- 12 -", false));
        assertTrue(PageEdges.isPageNumber("Page 12 of 300", false));
        assertTrue(PageEdges.isPageNumber("xii", true));
        assertFalse("a Roman number must stand apart", PageEdges.isPageNumber("mix", false));
        assertFalse(PageEdges.isPageNumber("vivid", true));
        assertFalse(PageEdges.isPageNumber("Chapter 3", true));
        assertFalse(PageEdges.isPageNumber("page", true));
        assertFalse(PageEdges.isPageNumber("", true));
    }

    @Test
    public void keysReadNumbersAsOne() {
        assertEquals(PageEdges.key("48 THE PSYCHOLOGY OF MONEY"), PageEdges.key("50  The Psychology of Money"));
        assertEquals(PageEdges.key("PREFACE xi"), PageEdges.key("Preface xiii"));
        assertFalse(PageEdges.key("NO ONE'S CRAZY 41").equals(PageEdges.key("48 THE PSYCHOLOGY OF MONEY")));
    }

    @Test
    public void wordsAtOneHeightAreOneLine() {
        final List<PageEdges.Word> words = new ArrayList<>(Arrays.asList(
                new PageEdges.Word("right", 0.6f, 0.101f, 0.12f),
                new PageEdges.Word("left", 0.1f, 0.1f, 0.12f),
                new PageEdges.Word("below", 0.1f, 0.15f, 0.17f),
                new PageEdges.Word(" ", 0.2f, 0.1f, 0.12f),
                new PageEdges.Word("off", -1, -1, -1)));
        final List<PageEdges.Line> lines = PageEdges.lines(words);
        assertEquals(2, lines.size());
        assertEquals("left right", lines.get(0).text);
        assertEquals(new HashSet<>(Arrays.asList(0, 1)), new HashSet<>(lines.get(0).words));
        assertEquals("below", lines.get(1).text);
    }
}
