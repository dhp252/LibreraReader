package com.foobnix.assist.skeleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The small texts of the skeleton screen: totals, part labels, a thesis cut to one sentence. */
public final class SkeletonText {

    private SkeletonText() {
    }

    /** A numbering after a word: Roman or Arabic numerals, a letter, or a number word. */
    private static final String NUMBER = "(?:[IVXLCDM]+|\\d+|[A-Z]|(?i:one|two|three|four|five|six|seven|eight|nine|"
            + "ten|eleven|twelve|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth))";
    /** "Book IV", "Part Two: ...", "PART 3. ...": a word, a number, then nothing or a separator and the rest. */
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\p{L}{3,})\\s+(" + NUMBER + ")\\s*(?:[.:\\-–—]\\s*(.*))?$");

    /**
     * The totals under the thesis, as pieces: "5 books", "32 chapters", "about 27½ h in full".
     *
     * @param parts    parts holding chapters; 0 for a book without parts (no piece)
     * @param partNoun what the book calls a part ("book"), or null for "part"
     * @param minutes  reading time in full; 0 when unknown (no piece)
     */
    public static List<String> totals(int parts, String partNoun, int chapters, int minutes) {
        final List<String> out = new ArrayList<>();
        if (parts > 0) {
            final String noun = partNoun == null || partNoun.isEmpty() ? "part" : partNoun;
            out.add(parts + " " + (parts == 1 ? noun : plural(noun)));
        }
        if (chapters > 0) {
            out.add(chapters + (chapters == 1 ? " chapter" : " chapters"));
        }
        if (minutes > 0) {
            out.add("about " + duration(minutes) + " in full");
        }
        return out;
    }

    /** "45 min" under an hour, else hours to the half: "3 h", "27½ h". */
    public static String duration(int minutes) {
        if (minutes < 60) {
            return Math.max(1, minutes) + " min";
        }
        final long halves = Math.round(minutes / 30.0);
        return (halves / 2) + (halves % 2 == 1 ? "½" : "") + " h";
    }

    /**
     * What the book calls its parts, when every part title starts with the same word and a number
     * ("Book I", "Book II" = "book"); null otherwise.
     */
    public static String partNoun(List<String> titles) {
        String noun = null;
        for (String t : titles) {
            final Matcher m = t == null ? null : NUMBERED.matcher(t);
            if (m == null || !m.matches()) {
                return null;
            }
            final String word = m.group(1).toLowerCase(Locale.ROOT);
            if (noun == null) {
                noun = word;
            } else if (!noun.equals(word)) {
                return null;
            }
        }
        return noun;
    }

    /** English plural of a part noun. */
    static String plural(String noun) {
        if (noun.endsWith("s") || noun.endsWith("x") || noun.endsWith("ch") || noun.endsWith("sh")) {
            return noun + "es";
        }
        if (noun.length() > 1 && noun.endsWith("y") && "aeiou".indexOf(noun.charAt(noun.length() - 2)) < 0) {
            return noun.substring(0, noun.length() - 1) + "ies";
        }
        return noun + "s";
    }

    /** The short name on a part's card: "Book I" of "Book I: Of the causes..."; the title when it has no number. */
    public static String shortLabel(String title) {
        if (title == null) {
            return "";
        }
        final String t = title.trim().replaceAll("\\s+", " ");
        final Matcher m = NUMBERED.matcher(t);
        if (m.matches()) {
            return (m.group(1) + " " + m.group(2)).trim();
        }
        return t;
    }

    /** What a part's title says after its short label ("Of the causes..."), or empty. */
    public static String afterLabel(String title) {
        if (title == null) {
            return "";
        }
        final Matcher m = NUMBERED.matcher(title.trim().replaceAll("\\s+", " "));
        if (!m.matches()) {
            return "";
        }
        return m.group(3) == null ? "" : m.group(3).trim();
    }

    /** The first sentence of a text (a thesis of one or two sentences), else the whole text. */
    public static String firstSentence(String text) {
        if (text == null) {
            return "";
        }
        final String t = text.trim();
        for (int i = 0; i < t.length() - 1; i++) {
            final char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(t.charAt(i + 1))
                    && i + 2 < t.length() && Character.isUpperCase(firstLetter(t, i + 2))
                    && !abbreviation(t, i)) {
                return t.substring(0, i + 1);
            }
        }
        return t;
    }

    private static char firstLetter(String t, int from) {
        for (int i = from; i < t.length(); i++) {
            final char c = t.charAt(i);
            if (Character.isLetter(c)) {
                return c;
            }
            if (!Character.isWhitespace(c) && c != '"' && c != '“' && c != '\'' && c != '‘') {
                return c;
            }
        }
        return ' ';
    }

    /** "Mr." "Dr." "e.g." "U.S." before the dot: no sentence end. */
    private static boolean abbreviation(String t, int dot) {
        if (t.charAt(dot) != '.') {
            return false;
        }
        int start = dot;
        while (start > 0 && Character.isLetter(t.charAt(start - 1))) {
            start--;
        }
        final String word = t.substring(start, dot);
        if (word.length() == 1) {
            return true;
        }
        return word.matches("(?i)mr|mrs|ms|dr|st|vs|etc|eg|ie|no|vol|ch|pp|jr|sr");
    }
}
