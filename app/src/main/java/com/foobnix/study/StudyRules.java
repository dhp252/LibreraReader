package com.foobnix.study;

/** The numbers study mode works with (F-009 design). */
public final class StudyRules {

    /**
     * A top-level TOC part with more text than this (about 12,000 words) and chapters inside is
     * studied chapter by chapter.
     */
    public static final int LONG_CHARS = 72_000;
    /** Skimming a chapter takes about this share of reading it. */
    public static final double SKIM_SHARE = 0.25;
    /** Days away from a studied book before the map is offered first. */
    public static final int WELCOME_DAYS = 3;
    /** Reading speed until the reading stats know the reader's. */
    public static final int DEFAULT_WPM = 230;
    /** Characters per word, to turn page text into words. */
    public static final double CHARS_PER_WORD = 6.0;
    public static final int QUESTIONS_MIN = 2;
    public static final int QUESTIONS_MAX = 3;

    private StudyRules() {
    }
}
