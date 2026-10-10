package com.foobnix.tts;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TtsTextLocatorTest {

    private static final String P = "ttsPAUSE";

    private static List<String> words(String... w) {
        return Arrays.asList(w);
    }

    @Test
    public void normalizeKeepsLettersAndDigitsOfAnyScript() {
        // Vietnamese D with stroke (U+0110) keeps its stroke; accents are folded away
        assertEquals("đayla", TtsTextLocator.normalize("Đây, là!"));
        // decomposed accent (o + combining circumflex) equals the composed one
        assertEquals(TtsTextLocator.normalize("hôm"), TtsTextLocator.normalize("hôm"));
        // ligature fi
        assertEquals("find", TtsTextLocator.normalize("ﬁnd"));
        assertEquals("exam", TtsTextLocator.normalize("exam-"));
        assertEquals("мир2", TtsTextLocator.normalize("Мир 2."));
        assertEquals("", TtsTextLocator.normalize(null));
    }

    @Test
    public void findsExactUtterance() {
        List<String> page = words("The", "quick", "brown", "fox.", "It", "jumps", "over", "the", "dog.");
        assertArrayEquals(new int[]{4, 8}, TtsTextLocator.findWords(page, "It jumps over the dog.", 0));
    }

    @Test
    public void joinsHyphenatedLineEnds() {
        List<String> page = words("an", "exam-", "ple", "of", "text.");
        assertArrayEquals(new int[]{0, 4}, TtsTextLocator.findWords(page, "an example of text.", 0));
    }

    @Test
    public void matchesVietnameseAndCyrillic() {
        List<String> vi = words("Tôi", "đi", "học.", "Hôm", "nay", "trời", "đẹp.");
        assertArrayEquals(new int[]{3, 6}, TtsTextLocator.findWords(vi, "Hôm nay trời đẹp.", 0));

        List<String> ru = words("Привет,", "мир.", "Как", "дела?");
        assertArrayEquals(new int[]{2, 3}, TtsTextLocator.findWords(ru, "Как дела?", 0));
    }

    @Test
    public void repeatedPhraseIsFoundInReadingOrder() {
        List<String> page = words("Yes", "indeed.", "Yes", "indeed.");
        assertArrayEquals(new int[]{0, 1}, TtsTextLocator.findWords(page, "Yes indeed.", 0));
        assertArrayEquals(new int[]{2, 3}, TtsTextLocator.findWords(page, "Yes indeed.", 2));
    }

    @Test
    public void sentenceCarriedOverFromPreviousPageStartsAtPageTop() {
        List<String> page = words("ended", "here.", "Next", "sentence", "starts", "now.");
        String spoken = "This sentence started on the previous page and ended here.";
        assertArrayEquals(new int[]{0, 1}, TtsTextLocator.findWords(page, spoken, 0));
    }

    @Test
    public void toleratesTextChangedByTtsReplacements() {
        List<String> page = words("Dr.", "Smith", "met", "Mr.", "Jones", "yesterday", "evening.");
        String spoken = "Doctor Smith met Mister Jones yesterday evening.";
        assertArrayEquals(new int[]{0, 6}, TtsTextLocator.findWords(page, spoken, 0));
    }

    @Test
    public void textNotOnPageGivesNull() {
        assertNull(TtsTextLocator.findWords(words("a", "b"), "completely different text here", 0));
        assertNull(TtsTextLocator.findWords(words("a", "b"), "zz", 0));
        assertNull(TtsTextLocator.findWords(words(), "anything", 0));
    }

    @Test
    public void readingStartsAtSentenceOfTappedWord() {
        String tts = "First sentence here." + P + " Second one is here." + P + " Third.";
        int start = TtsTextLocator.findReadingStart(tts, words("one", "is", "here."), 0.5f);
        assertTrue(tts.substring(start).trim().startsWith("Second one is here."));
    }

    @Test
    public void tapPositionChoosesBetweenRepeatedPhrases() {
        String tts = "Go now." + P + " Wait here." + P + " Go now." + P + " End.";
        int late = TtsTextLocator.findReadingStart(tts, words("Go", "now."), 0.9f);
        assertTrue(tts.substring(late).trim().startsWith("Go now." + P + " End."));
        int early = TtsTextLocator.findReadingStart(tts, words("Go", "now."), 0.0f);
        assertEquals(0, early);
    }

    @Test
    public void fewerWordsAreTriedWhenOneDiffers() {
        String tts = "He met Doctor Smith." + P + " Then left.";
        // "Thn" is an OCR typo on the page: the tapped word alone still finds the sentence
        assertEquals(0, TtsTextLocator.findReadingStart(tts, words("Smith", "Thn", "left"), 0.6f));
        assertEquals(-1, TtsTextLocator.findReadingStart(tts, words("absent"), 0.5f));
    }

    @Test
    public void sentenceAtReturnsSentenceAroundOffset() {
        String text = "One. Two three. Four";
        assertEquals("Two three.", TtsTextLocator.sentenceAt(text, text.indexOf("three")));
        assertEquals("Four", TtsTextLocator.sentenceAt(text, text.length() - 1));
        assertNull(TtsTextLocator.sentenceAt(text, -1));
    }
}
