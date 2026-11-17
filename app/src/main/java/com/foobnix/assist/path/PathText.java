package com.foobnix.assist.path;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The small texts of the shortest path: times, the short code of a chapter ("I.7"). */
public final class PathText {

    private PathText() {
    }

    /**
     * A word, then a number short enough for the code column (Roman or Arabic numerals, or one
     * letter), then the end or a separator: "Book IV", "Chapter 7: ...", not "Vitamin C and health".
     */
    private static final Pattern NUMBERED = Pattern.compile(
            "^\\s*\\p{L}{2,}\\.?\\s+([IVXLCDM]{1,6}|\\d{1,3}|[A-Z])(?=\\s*(?:$|[.:)\\-–—]))");
    /** "7. Of the natural price", "IV: ...": the number alone at the start. */
    private static final Pattern LEADING = Pattern.compile("^\\s*([IVXLCDM]{1,6}|\\d{1,3})\\s*[.:)\\-–—]\\s");

    /** "45 min" under an hour, else "1 h 45 min" ("2 h" on the hour); "" when unknown (0). */
    public static String duration(int minutes) {
        if (minutes <= 0) {
            return "";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        final int h = minutes / 60;
        final int m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /**
     * The short code of a chapter for the narrow column beside its title: "I.7" for "Chapter VII"
     * of "Book I", "7" when only the chapter is numbered, "" when it is not (the screen then shows
     * its place on the path). Read from the titles only; the titles themselves are never changed.
     */
    public static String code(String partTitle, String chapterTitle) {
        final String chapter = number(chapterTitle);
        if (chapter.isEmpty()) {
            return "";
        }
        final String part = number(partTitle);
        return part.isEmpty() ? chapter : part + "." + arabic(chapter);
    }

    /** The number a title starts with ("Book IV: ..." = "IV", "12. Rent" = "12"), or "". */
    static String number(String title) {
        if (title == null) {
            return "";
        }
        final Matcher lead = LEADING.matcher(title);
        if (lead.find()) {
            return lead.group(1);
        }
        final Matcher m = NUMBERED.matcher(title);
        return m.find() ? m.group(1) : "";
    }

    /** A Roman numeral as a number ("VII" = "7") after a part's number, so "I.7" reads at a glance; else as it is. */
    static String arabic(String number) {
        if (!number.matches("[IVXLCDM]+")) {
            return number;
        }
        int total = 0;
        int before = 0;
        for (int i = number.length() - 1; i >= 0; i--) {
            final int v = roman(number.charAt(i));
            total += v < before ? -v : v;
            before = Math.max(before, v);
        }
        return total > 0 ? String.valueOf(total) : number;
    }

    private static int roman(char c) {
        switch (c) {
            case 'I':
                return 1;
            case 'V':
                return 5;
            case 'X':
                return 10;
            case 'L':
                return 50;
            case 'C':
                return 100;
            case 'D':
                return 500;
            case 'M':
                return 1000;
            default:
                return 0;
        }
    }

    /** One line of text for a prompt or a card: trimmed, spaces collapsed, cut at {@code max} with "…". */
    public static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        final String t = s.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)).trim() + "…";
    }
}
