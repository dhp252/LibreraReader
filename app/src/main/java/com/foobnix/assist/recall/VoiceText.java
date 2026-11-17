package com.foobnix.assist.recall;

/**
 * The words of one talk: speech recognition hands them over in pieces (it stops at every pause and
 * is started again while the reader holds the button), each piece first as a changing guess.
 * This keeps the finished pieces and the guess in progress as one text. No Android here.
 */
public final class VoiceText {

    private final StringBuilder done = new StringBuilder();
    private String partial = "";

    /** A finished piece: joined to the ones before as a sentence of its own. */
    public void add(String piece) {
        partial = "";
        final String p = piece == null ? "" : piece.replaceAll("\\s+", " ").trim();
        if (p.isEmpty()) {
            return;
        }
        if (done.length() > 0) {
            if (!endsSentence(done)) {
                done.append('.');
            }
            done.append(' ');
        }
        final int first = p.codePointAt(0);
        done.appendCodePoint(Character.toUpperCase(first)).append(p.substring(Character.charCount(first)));
    }

    /** The guess for the piece being said now; it replaces the guess before. */
    public void partial(String piece) {
        partial = piece == null ? "" : piece.replaceAll("\\s+", " ").trim();
    }

    /** The guess in progress becomes a finished piece (recognition stopped before it was final). */
    public void commit() {
        if (!partial.isEmpty()) {
            add(partial);
        }
    }

    public void clear() {
        done.setLength(0);
        partial = "";
    }

    public boolean isEmpty() {
        return done.length() == 0 && partial.isEmpty();
    }

    /** Everything said so far: the finished pieces, then the guess in progress. */
    public String text() {
        if (partial.isEmpty()) {
            return done.toString();
        }
        if (done.length() == 0) {
            return partial;
        }
        return done + (endsSentence(done) ? " " : ". ") + partial;
    }

    /** {@code said} after what is already in a text box, a space between them. */
    public static String join(String typed, String said) {
        final String a = typed == null ? "" : typed.trim();
        final String b = said == null ? "" : said.trim();
        if (a.isEmpty()) {
            return b;
        }
        return b.isEmpty() ? a : a + (endsSentence(a) ? " " : ". ") + b;
    }

    private static boolean endsSentence(CharSequence s) {
        final char c = s.charAt(s.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '…' || c == ':' || c == ';' || c == ',';
    }
}
