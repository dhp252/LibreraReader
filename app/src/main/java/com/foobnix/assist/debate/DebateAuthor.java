package com.foobnix.assist.debate;

/**
 * The author the reader debates, as the library's record of the book names them: the full name
 * (the first author), the short name the screen uses ("Smith") and the avatar's letters ("AS").
 * Without a name it is "the author" ({@link #known} false) and the screen uses its own words.
 */
public final class DebateAuthor {

    /** The first author's name as written, or empty. */
    public final String name;
    /** The family name, or the whole name when it is one word; empty when unknown. */
    public final String shortName;
    /** One or two letters for the avatar; empty when unknown. */
    public final String initials;
    public final boolean known;

    private DebateAuthor(String name, String shortName, String initials) {
        this.name = name;
        this.shortName = shortName;
        this.initials = initials;
        this.known = !name.isEmpty();
    }

    /**
     * @param meta the author field of the book's record: "Adam Smith", "Smith, Adam",
     *             "Adam Smith; David Hume", "Adam Smith, David Hume", or nothing
     */
    public static DebateAuthor of(String meta) {
        String s = meta == null ? "" : meta.replaceAll("\\s+", " ").trim();
        // the first of several authors
        for (String cut : new String[]{";", " & ", " and ", "|", "/"}) {
            final int at = s.indexOf(cut);
            if (at > 0) {
                s = s.substring(0, at).trim();
            }
        }
        final int comma = s.indexOf(',');
        if (comma > 0) {
            final String before = s.substring(0, comma).trim();
            final String after = s.substring(comma + 1).trim();
            final int next = after.indexOf(',');
            final String given = (next >= 0 ? after.substring(0, next) : after).trim();
            if (before.indexOf(' ') < 0 && !given.isEmpty() && next < 0) {
                // "Smith, Adam"
                s = given + " " + before;
            } else {
                // "Adam Smith, David Hume"
                s = before;
            }
        }
        if (s.isEmpty() || !hasLetter(s)) {
            return new DebateAuthor("", "", "");
        }
        final String[] words = s.split(" ");
        final String last = words[words.length - 1];
        final StringBuilder initials = new StringBuilder();
        appendFirstLetter(initials, words[0]);
        if (words.length > 1) {
            appendFirstLetter(initials, last);
        }
        return new DebateAuthor(s, last, initials.toString());
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static void appendFirstLetter(StringBuilder out, String word) {
        for (int i = 0; i < word.length(); ) {
            final int cp = word.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                out.appendCodePoint(Character.toUpperCase(cp));
                return;
            }
            i += Character.charCount(cp);
        }
    }
}
