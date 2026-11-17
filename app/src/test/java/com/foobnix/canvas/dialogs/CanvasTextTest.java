package com.foobnix.canvas.dialogs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CanvasTextTest {

    @Test
    public void sentenceCaseLowersCapitalizedWords() {
        assertEquals("Contrast and brightness", CanvasText.sentenceCase("Contrast and Brightness"));
        assertEquals("Link color", CanvasText.sentenceCase("Link Color"));
        assertEquals("Open web page", CanvasText.sentenceCase("Open Web page"));
        assertEquals("Advanced settings", CanvasText.sentenceCase("Advanced Settings"));
    }

    @Test
    public void sentenceCaseKeepsAcronymsAndMixedWords() {
        assertEquals("TTS settings", CanvasText.sentenceCase("TTS Settings"));
        assertEquals("Record TTS (MP3, WAV)", CanvasText.sentenceCase("Record TTS (MP3, WAV)"));
        assertEquals("Sync with WebDAV", CanvasText.sentenceCase("Sync with WebDAV"));
        assertEquals("OK", CanvasText.sentenceCase("OK"));
        assertEquals("Read!", CanvasText.sentenceCase("Read!"));
        assertEquals("Page 3 of 10", CanvasText.sentenceCase("Page 3 of 10"));
    }

    @Test
    public void sentenceCaseCapitalizesTheFirstWord() {
        assertEquals("Do it again", CanvasText.sentenceCase("do it again"));
        assertEquals("“Quoted” title", CanvasText.sentenceCase("“quoted” Title"));
        assertEquals("", CanvasText.sentenceCase(""));
        assertNull(CanvasText.sentenceCase(null));
    }

    @Test
    public void sentenceCaseKeepsApostrophes() {
        assertEquals("Don't stop me", CanvasText.sentenceCase("Don't Stop Me"));
    }

    @Test
    public void english() {
        assertTrue(CanvasText.isEnglish("en"));
        assertTrue(CanvasText.isEnglish("en_US"));
        assertFalse(CanvasText.isEnglish("de"));
        assertFalse(CanvasText.isEnglish(null));
    }

    @Test
    public void speedChip() {
        assertEquals("1×", CanvasText.speed(1f));
        assertEquals("1.25×", CanvasText.speed(1.25f));
        assertEquals("0.5×", CanvasText.speed(0.5f));
        assertEquals("0.75×", CanvasText.speed(0.75f));
        assertEquals("2×", CanvasText.speed(2f));
        assertEquals("1.05×", CanvasText.speed(1.05f));
        assertEquals("6×", CanvasText.speed(6f));
        assertEquals("0×", CanvasText.speed(0f));
    }
}
