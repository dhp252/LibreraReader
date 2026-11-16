package com.foobnix.canvas.dialogs;

import java.util.Locale;

/**
 * Text rules of the canvas look for dialogs, free of Android types so they are tested on the JVM:
 * sentence case for the titles and buttons the app builds from its own strings, and the speed
 * chip of the read-aloud controls.
 */
public final class CanvasText {

    private CanvasText() {
    }

    /**
     * "Contrast and Brightness" → "Contrast and brightness". Only words written Capitalized (one
     * capital, then small letters) after the first word are lowered: acronyms (TTS, PDF, MP3) and
     * mixed words (WebDAV, iPhone) stay as they are. Meant for English strings of the app only:
     * other languages (German nouns) and book or chapter titles are left alone by the caller.
     */
    public static String sentenceCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        final StringBuilder out = new StringBuilder(s.length());
        boolean first = true;
        int i = 0;
        while (i < s.length()) {
            final char ch = s.charAt(i);
            if (!Character.isLetter(ch)) {
                out.append(ch);
                i++;
                continue;
            }
            int end = i;
            while (end < s.length() && (Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '\'')) {
                end++;
            }
            final String word = s.substring(i, end);
            if (first) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                first = false;
            } else if (isCapitalized(word)) {
                out.append(word.toLowerCase(Locale.ENGLISH));
            } else {
                out.append(word);
            }
            i = end;
        }
        return out.toString();
    }

    /** One capital letter, then at least one letter, all of them small. */
    static boolean isCapitalized(String word) {
        if (word.length() < 2 || !Character.isUpperCase(word.charAt(0))) {
            return false;
        }
        for (int i = 1; i < word.length(); i++) {
            final char c = word.charAt(i);
            if (Character.isUpperCase(c) || Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /** Sentence case is used for English only. */
    public static boolean isEnglish(String language) {
        return language != null && language.toLowerCase(Locale.ENGLISH).startsWith("en");
    }

    /** The speed chip: 1 → "1×", 1.25 → "1.25×", 0.5 → "0.5×". */
    public static String speed(float speed) {
        final long hundredths = Math.round(speed * 100);
        String s = String.valueOf(hundredths / 100);
        final long rest = Math.abs(hundredths % 100);
        if (rest != 0) {
            s += rest % 10 == 0 ? "." + (rest / 10) : (rest < 10 ? ".0" + rest : "." + rest);
        }
        return s + "×";
    }
}
