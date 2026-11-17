package com.foobnix.assist.review;

/** Small words of the review screens that need no Android: a book's short name and its letter. */
public final class ReviewText {

    private ReviewText() {
    }

    /** "Smith" of "Adam Smith" or "Smith, Adam"; empty without an author. Only the first author counts. */
    public static String shortAuthor(String author) {
        String a = author == null ? "" : author.trim();
        for (String sep : new String[]{";", "&", " and "}) {
            final int at = a.indexOf(sep);
            if (at > 0) {
                a = a.substring(0, at).trim();
            }
        }
        final int comma = a.indexOf(',');
        if (comma > 0) {
            return a.substring(0, comma).trim();
        }
        final int space = a.lastIndexOf(' ');
        return space < 0 ? a : a.substring(space + 1);
    }

    /** What a row calls the book: its author's short name, else its title as it is. */
    public static String who(String author, String title) {
        final String a = shortAuthor(author);
        return !a.isEmpty() ? a : title == null ? "" : title.trim();
    }

    /** The letter on a book's square. */
    public static String letter(String author, String title) {
        final String who = who(author, title);
        for (int i = 0; i < who.length(); ) {
            final int c = who.codePointAt(i);
            if (Character.isLetterOrDigit(c)) {
                return new String(Character.toChars(Character.toUpperCase(c)));
            }
            i += Character.charCount(c);
        }
        return "?";
    }

    /** Whether a wait reads better in weeks ("3 weeks") than in days ("8 days"). */
    public static boolean inWeeks(int days) {
        return days >= 14 && days % 7 == 0;
    }
}
