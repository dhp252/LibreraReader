package com.foobnix.assist.path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PathTextTest {

    @Test
    public void durationsAsOnTheCanvas() {
        assertEquals("", PathText.duration(0));
        assertEquals("7 min", PathText.duration(7));
        assertEquals("59 min", PathText.duration(59));
        assertEquals("1 h", PathText.duration(60));
        assertEquals("1 h 45 min", PathText.duration(105));
        assertEquals("1 h 37 min", PathText.duration(97));
        assertEquals("27 h 30 min", PathText.duration(1650));
    }

    @Test
    public void codeFromNumberedTitles() {
        assertEquals("I.7", PathText.code("Book I", "Chapter VII: Of the Natural and Market Price"));
        assertEquals("IV.2", PathText.code("Book IV: Of Systems", "Chapter II"));
        assertEquals("2.5", PathText.code("Part 2", "Chapter 5. Capital"));
        assertEquals("I.3", PathText.code("Book I", "3. Rent"));
        // only the chapter is numbered: its number as the book writes it
        assertEquals("VII", PathText.code("", "Chapter VII: Price"));
        assertEquals("14", PathText.code(null, "Ch. 14 - The Division of Labour"));
        assertEquals("12", PathText.code("The Early Years", "12. Rent"));
    }

    @Test
    public void noCodeFromPlainTitles() {
        assertEquals("", PathText.code("Book I", "Of the Division of Labour"));
        assertEquals("", PathText.code("", "Introduction"));
        assertEquals("", PathText.code("", "Vitamin C and health"));
        assertEquals("", PathText.code("", "A Digression"));
        assertEquals("", PathText.code("", "1984 and after"));
        assertEquals("", PathText.code("", null));
    }

    @Test
    public void romanNumerals() {
        assertEquals("7", PathText.arabic("VII"));
        assertEquals("4", PathText.arabic("IV"));
        assertEquals("19", PathText.arabic("XIX"));
        assertEquals("40", PathText.arabic("XL"));
        assertEquals("12", PathText.arabic("12"));
    }

    @Test
    public void clipKeepsOneLine() {
        assertEquals("a b c", PathText.clip("  a \n b\tc ", 20));
        assertEquals("abcd…", PathText.clip("abcdefgh", 5));
        assertEquals("", PathText.clip(null, 5));
    }
}
