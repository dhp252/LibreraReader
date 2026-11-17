package com.foobnix.assist.zoom;

import com.foobnix.study.StudyBook;
import com.foobnix.study.StudyTime;

/**
 * Reading times of the zoom bar's status line, at the reader's own speed ({@link StudyTime}, the
 * default speed before the reading stats know it): "about 6 min instead of 15". No Android here.
 */
public final class ZoomTime {

    private ZoomTime() {
    }

    /** Minutes to read {@code chars} characters in full; 0 for no text, else at least 1. */
    public static int minutes(long chars, int wpm) {
        return StudyTime.minutes(chars, StudyBook.READ, wpm);
    }

    /** Minutes to read {@code words} words; 0 for none, else at least 1. */
    public static int wordMinutes(int words, int wpm) {
        if (words <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round((double) words / StudyTime.wpm(wpm)));
    }

    /** "45 min" under an hour, else hours to the half: "1 h", "2½ h". */
    public static String duration(int minutes) {
        if (minutes < 60) {
            return Math.max(1, minutes) + " min";
        }
        final long halves = Math.round(minutes / 30.0);
        return (halves / 2) + (halves % 2 == 1 ? "½" : "") + " h";
    }

    /** The Full level: "about 15 min for this chapter"; empty when the length is not known. */
    public static String full(int minutes) {
        return minutes <= 0 ? "" : "about " + duration(minutes) + " for this chapter";
    }

    /** The Lean level: "about 6 min instead of 15"; without the comparison when it saves nothing. */
    public static String lean(int leanMinutes, int fullMinutes) {
        if (leanMinutes <= 0) {
            return "";
        }
        if (fullMinutes <= leanMinutes) {
            return "about " + duration(leanMinutes);
        }
        if (fullMinutes < 60) {
            return "about " + duration(leanMinutes) + " instead of " + fullMinutes;
        }
        return "about " + duration(leanMinutes) + " instead of " + duration(fullMinutes);
    }

    /** The goal screen's "what this changes": "15 → 6 min"; empty when a time is not known. */
    public static String change(int fullMinutes, int leanMinutes) {
        if (fullMinutes <= 0 || leanMinutes <= 0) {
            return "";
        }
        if (fullMinutes < 60) {
            return fullMinutes + " → " + duration(leanMinutes);
        }
        return duration(fullMinutes) + " → " + duration(leanMinutes);
    }

    /** The Claims level: "the whole chapter in about 1 min". */
    public static String claims(int minutes, boolean partial) {
        return (partial ? "this part of the chapter in about " : "the whole chapter in about ") + duration(Math.max(1, minutes));
    }

    /**
     * Minutes a folded stretch stands for: from its words when they were counted, else from the
     * share of the chapter the model gave; 0 when neither is known.
     */
    public static int foldMinutes(ZoomData.Block fold, long chapterChars, int wpm) {
        if (fold.words > 0) {
            return wordMinutes(fold.words, wpm);
        }
        if (fold.percent > 0 && chapterChars > 0) {
            return minutes(chapterChars * fold.percent / 100, wpm);
        }
        return 0;
    }
}
