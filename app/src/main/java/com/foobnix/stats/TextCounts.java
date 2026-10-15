package com.foobnix.stats;

import java.util.List;

/**
 * Words of a page for the stats, and the counts of the Flesch reading ease (English books only).
 * A CJK character counts as one word: CJK books are measured in characters per minute.
 */
public class TextCounts {

    public long words;
    public long sentences;
    public long syllables;

    /** Adds the words of a page, in reading order. */
    public TextCounts add(List<String> pageWords) {
        for (String w : pageWords) {
            addWord(w);
        }
        return this;
    }

    public void addAll(TextCounts other) {
        words += other.words;
        sentences += other.sentences;
        syllables += other.syllables;
    }

    /** Words of one page. */
    public static int count(List<String> pageWords) {
        return (int) new TextCounts().add(pageWords).words;
    }

    /** Flesch reading ease (higher is easier), or NaN without enough text. */
    public double flesch() {
        if (words < 100 || sentences == 0) {
            return Double.NaN;
        }
        return 206.835 - 1.015 * (words / (double) sentences) - 84.6 * (syllables / (double) words);
    }

    private void addWord(String w) {
        if (w == null) {
            return;
        }
        int cjk = 0;
        boolean letters = false;
        for (int i = 0; i < w.length(); i++) {
            final char c = w.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else if (Character.isLetterOrDigit(c)) {
                letters = true;
            }
        }
        words += cjk + (letters ? 1 : 0);
        if (letters) {
            syllables += syllables(w);
            final char last = w.charAt(w.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == '…') {
                sentences++;
            }
        }
    }

    static boolean isCjk(char c) {
        final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    /** Vowel groups, a silent final e dropped: the usual estimate for English. */
    static int syllables(String word) {
        final String w = word.toLowerCase().replaceAll("[^a-z]", "");
        if (w.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean vowel = false;
        for (int i = 0; i < w.length(); i++) {
            final boolean v = "aeiouy".indexOf(w.charAt(i)) >= 0;
            if (v && !vowel) {
                count++;
            }
            vowel = v;
        }
        if (w.endsWith("e") && !w.endsWith("le") && count > 1) {
            count--;
        }
        return Math.max(1, count);
    }
}
