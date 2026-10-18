package com.foobnix.study;

import java.util.Collection;

/** How long the chapters take at the reader's speed: read in full, skimmed at a quarter, skipped. */
public final class StudyTime {

    private StudyTime() {
    }

    /** The reader's speed, or the default before the reading stats know it. */
    public static int wpm(int known) {
        return known > 0 ? known : StudyRules.DEFAULT_WPM;
    }

    public static double share(String label) {
        if (StudyBook.SKIP.equals(label)) {
            return 0;
        }
        return StudyBook.SKIM.equals(label) ? StudyRules.SKIM_SHARE : 1;
    }

    /** Minutes for a chapter of {@code chars} characters read as {@code label}; at least 1 unless nothing. */
    public static int minutes(long chars, String label, int wpm) {
        final double words = chars / StudyRules.CHARS_PER_WORD * share(label);
        if (words <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(words / wpm(wpm)));
    }

    /** The plan of a book: minutes as labelled vs everything read, and the labels counted. */
    public static final class Plan {
        public int minutes;
        public int fullMinutes;
        public int read;
        public int skim;
        public int skip;
    }

    public static Plan plan(Collection<StudyBook.Chapter> chapters, int wpm) {
        final Plan plan = new Plan();
        for (StudyBook.Chapter c : chapters) {
            plan.minutes += minutes(c.chars, c.label, wpm);
            plan.fullMinutes += minutes(c.chars, StudyBook.READ, wpm);
            if (StudyBook.SKIP.equals(c.label)) {
                plan.skip++;
            } else if (StudyBook.SKIM.equals(c.label)) {
                plan.skim++;
            } else {
                plan.read++;
            }
        }
        return plan;
    }
}
