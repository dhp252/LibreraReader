package com.foobnix.canvas.reader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReaderTextTest {

    @Test
    public void percentIsWholeAndRoundedDown() {
        assertEquals("38%", ReaderText.percent(38, 100));
        assertEquals("33%", ReaderText.percent(1, 3));
        assertEquals("66%", ReaderText.percent(2, 3));
        assertEquals("0%", ReaderText.percent(1, 300));
    }

    @Test
    public void percentIsHundredOnlyOnTheLastPage() {
        assertEquals("99%", ReaderText.percent(299, 300));
        assertEquals("100%", ReaderText.percent(300, 300));
        assertEquals("100%", ReaderText.percent(1, 1));
    }

    @Test
    public void percentStaysInRange() {
        assertEquals("0%", ReaderText.percent(-4, 10));
        assertEquals("100%", ReaderText.percent(12, 10));
        assertEquals("", ReaderText.percent(3, 0));
    }

    @Test
    public void percentOfAHugeBookDoesNotOverflow() {
        assertEquals("50%", ReaderText.percent(Integer.MAX_VALUE / 2, Integer.MAX_VALUE - 1));
    }

    @Test
    public void chapterLineIsOneLine() {
        assertEquals("Book I · Ch. 1", ReaderText.chapterLine("  Book I ·\n Ch. 1 "));
        assertEquals("", ReaderText.chapterLine(null));
        assertEquals("", ReaderText.chapterLine(" \t "));
    }
}
