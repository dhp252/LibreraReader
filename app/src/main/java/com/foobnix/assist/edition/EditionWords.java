package com.foobnix.assist.edition;

/**
 * The fixed words of "Your edition" (labels, sentence frames, month names), so the model and the
 * PDF plan stay free of Android: the app fills them from its string resources
 * ({@code EditionLook.words}), tests use these English defaults. Frames take {@code %1$s} style
 * arguments ({@link #f}).
 */
public final class EditionWords {

    public String yourEdition = "Your edition";

    public String sectionSkeleton = "The skeleton";
    public String sectionWords = "In your words";
    public String sectionWrong = "What you got wrong, and the fix";
    public String sectionConnections = "Connections";
    public String sectionOpen = "Questions still open";

    public String labelLine = "The book in one line";
    public String labelWords = "In your words";
    public String labelTidied = "In your words · tidied";
    public String labelExplained = "You explained · teach-back";
    public String labelWrong = "What you got wrong, and the fix";
    public String labelTried = "Tried it";
    public String labelTriedDates = "Tried it · %1$s";
    public String labelConnections = "Connections";
    public String labelNote = "The book, updated";
    public String labelOpen = "Still open";
    public String labelGuess = "You guessed wrong";

    public String missed = "You missed: %1$s";
    public String unsure = "You were unsure of: %1$s";
    public String leftOut = "You did not get to explain: %1$s";
    public String bookSays = "The book: “%1$s”";
    public String rightSince = "Right on every review since %1$s.";
    public String notYet = "Not right yet at the last review, %1$s.";
    public String missedShort = "Missed “%1$s” at first";
    public String unsureShort = "Unsure of “%1$s” at first";
    public String rightSinceShort = "right on every review since";

    public String measure = "%1$s: %2$s → %3$s";
    public String verdictHeld = "It held.";
    public String verdictMostly = "It mostly held.";
    public String verdictDidnt = "It didn't hold.";

    public String same = "Same idea";
    public String disagrees = "Disagrees";
    public String extendsIdea = "Extends";

    public String debateOn = "From your debate on: %1$s";
    public String guess = "You guessed %1$s. The book: %2$s.";
    public String howSettled = "How settled: %1$s";
    public String settledEstablished = "well established";
    public String settledDebated = "debated";
    public String settledSpeculative = "speculative";

    public String front = "Before the first chapter";
    public String pagesFrom = "From page %1$s";
    public String continued = "%1$s (continued)";

    public String kindRecalls = "recalls";
    public String kindMisses = "misses";
    public String kindExperiments = "experiments";
    public String kindLinks = "links";
    public String kindQuestions = "questions";

    public String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** A frame with its arguments; a frame that cannot be formatted is returned as it is. */
    public static String f(String frame, Object... args) {
        try {
            return String.format(java.util.Locale.ROOT, frame, args);
        } catch (RuntimeException e) {
            return frame;
        }
    }
}
