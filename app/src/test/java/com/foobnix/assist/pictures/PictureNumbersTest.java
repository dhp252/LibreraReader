package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PictureNumbersTest {

    private static Set<String> set(String... numbers) {
        return new HashSet<>(Arrays.asList(numbers));
    }

    @Test
    public void readsNumbersWrittenInDigits() {
        assertEquals(set("48000", "0.3", "20", "1776", "18"),
                PictureNumbers.of("48,000 pins, 0.3 of them and 20% in 1776; the 18th operation"));
        // a no-break space groups thousands; a plain space does not
        assertEquals(set("4800", "12.5"), PictureNumbers.of("4" + (char) 0xA0 + "800 pounds and 12.50 more"));
        assertEquals(set("4", "800"), PictureNumbers.of("4 800 pounds"));
        assertEquals(set("2", "3"), PictureNumbers.of("×2–3"));
        assertEquals(set("0"), PictureNumbers.of("from 0 to none"));
        assertTrue(PictureNumbers.of("").isEmpty());
        assertTrue(PictureNumbers.of(null).isEmpty());
        assertTrue(PictureNumbers.of("no number here").isEmpty());
    }

    @Test
    public void readsEnglishNumberWords() {
        assertEquals(set("48000"), PictureNumbers.of("upwards of forty-eight thousand pins in a day"));
        assertEquals(set("4800"), PictureNumbers.of("four thousand eight hundred pins"));
        assertEquals(set("2", "3"), PictureNumbers.of("two or three distinct operations"));
        assertEquals(set("18"), PictureNumbers.of("about eighteen distinct operations"));
        assertEquals(set("240"), PictureNumbers.of("Two hundred and forty times"));
        assertEquals(set("100"), PictureNumbers.of("a hundred men"));
        assertEquals(set("24"), PictureNumbers.of("two dozen"));
        assertEquals(set("10", "12"), PictureNumbers.of("ten persons made twelve pounds"));
        assertEquals(set("1000000"), PictureNumbers.of("a million"));
        assertEquals(set("2500000"), PictureNumbers.of("two million five hundred thousand"));
    }

    @Test
    public void numberWordsSideBySideAreSeparateNumbers() {
        assertEquals(set("1", "2", "3"), PictureNumbers.of("one two three"));
        assertEquals(set("10", "12"), PictureNumbers.of("ten and twelve"));
        assertEquals(set("20", "30"), PictureNumbers.of("twenty thirty"));
        assertEquals(set("2", "100"), PictureNumbers.of("two and a hundred"));
        // "and" alone ends a number
        assertEquals(set("200"), PictureNumbers.of("two hundred and the rest"));
    }

    @Test
    public void aLineShowsItsDigitsAndItsLargerNumberWords() {
        assertTrue(PictureNumbers.shown("One loop, two brakes").isEmpty());
        assertEquals(set("18"), PictureNumbers.shown("One pin, about eighteen operations"));
        assertEquals(set("2", "3"), PictureNumbers.shown("×2–3"));
        assertEquals(set("48000"), PictureNumbers.shown("≈ 48,000"));
    }

    @Test
    public void aLineHoldsOnlyWhenEveryNumberIsKnown() {
        final Set<String> known = PictureNumbers.of("ten workers made upwards of forty-eight thousand pins, 4 tons");
        assertTrue(PictureNumbers.allIn("Same 10 people", known));
        assertTrue(PictureNumbers.allIn("≈ 48,000 pins a day", known));
        assertTrue(PictureNumbers.allIn("No number at all", known));
        assertTrue(PictureNumbers.allIn("", known));
        assertFalse(PictureNumbers.allIn("≈ 48,000 pins from 18 steps", known));
        assertFalse(PictureNumbers.allIn("240×", known));
    }

    @Test
    public void aValueIsKnownByItsSize() {
        final Set<String> known = PictureNumbers.of("4 tons, 0.3 and forty-eight thousand");
        assertTrue(PictureNumbers.has(4, known));
        assertTrue(PictureNumbers.has(4.0, known));
        assertTrue(PictureNumbers.has(-4, known));
        assertTrue(PictureNumbers.has(0.3, known));
        assertTrue(PictureNumbers.has(48000, known));
        assertFalse(PictureNumbers.has(200, known));
        assertFalse(PictureNumbers.has(Double.NaN, known));
        assertEquals("48000", PictureNumbers.canon(48000.0));
        assertEquals("0.3", PictureNumbers.canon(0.1 + 0.2));
        assertEquals("0", PictureNumbers.canon(0));
        assertNull(PictureNumbers.canon(Double.POSITIVE_INFINITY));
    }
}
