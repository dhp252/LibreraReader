package com.foobnix.stats;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The measurement rules of the reading stats, in one place (design: "Measurement rules"). The log
 * keeps raw times and the summaries are built from it in each app run, so a rule changed here
 * applies to all past reading too.
 */
public final class StatsRules {

    /** A page counts at most this long: the rest is idle time (the book left open). */
    public static final long PAGE_CAP_MS = 5 * 60_000L;
    /** A page left sooner was skimmed: not read, not in WPM. */
    public static final long SKIM_MS = 2_000L;
    /** Longer without reading starts a new session. */
    public static final long SESSION_GAP_MS = 10 * 60_000L;
    /**
     * Back to the book this soon (a dictionary app, a glance at a message) keeps the session;
     * later, leaving the reader ended it.
     */
    public static final long RESUME_KEEP_MS = 60_000L;
    /** An open shorter than this without a page read is a bounce. */
    public static final long BOUNCE_MS = 30_000L;
    /** Read aloud without a recorded stop counts at most this long. */
    public static final long LISTEN_CAP_MS = 4 * 3_600_000L;

    /** Words of a virtual page of a reflowable book. */
    public static final int WORDS_PER_VIRTUAL_PAGE = 250;
    /** Progress at which a book counts as finished. */
    public static final float FINISHED = 0.98f;
    /** A finished book read again from below this starts a new pass. */
    public static final float REREAD_FROM = 0.10f;
    /** An unfinished book not opened for this long is stopped. */
    public static final long STOPPED_AFTER_MS = 30L * 86_400_000L;
    /** Stretch of a session the fatigue curve groups by. */
    public static final long FATIGUE_BUCKET_MS = 10 * 60_000L;
    /** Width of a WPM histogram bucket. */
    public static final int WPM_BUCKET = 5;
    /** Faster than this is not reading (a page of pictures, a blank page): left out of WPM. */
    public static final int WPM_MAX = 1500;
    /** Places a book's progress is tracked in, for re-reads. */
    public static final int PROGRESS_SLOTS = 1000;

    /** A day with at least this much reading or listening keeps the streak. */
    public static final long STREAK_DAY_MS = 60_000L;
    /** Time left: the book's own speed once it has this much reading... */
    public static final long BOOK_WPM_AFTER_MS = 20 * 60_000L;
    /** ...else the overall speed once there is this much. */
    public static final long ALL_WPM_AFTER_MS = 15 * 60_000L;
    /** Finish date: words per reading day over this many days. */
    public static final int PACE_DAYS = 14;
    /** Books this year: the finish rate over this many days. */
    public static final int FINISH_RATE_DAYS = 90;
    /** Best window: hours with less reading than this in the period are not ranked. */
    public static final long BEST_HOUR_MIN_MS = 30 * 60_000L;

    /** Formats with fixed pages: a page is a page. Others use virtual pages. */
    private static final Set<String> FIXED = new HashSet<>(Arrays.asList("pdf", "djvu", "djv", "xps", "oxps", "cbz", "cbr", "tif", "tiff"));
    private static final Set<String> CJK_LANGS = new HashSet<>(Arrays.asList("zh", "ja", "ko"));

    private StatsRules() {
    }

    public static boolean isFixedLayout(String ext) {
        return ext != null && FIXED.contains(ext.toLowerCase());
    }

    public static boolean isCjkLanguage(String lang) {
        if (lang == null || lang.length() < 2) {
            return false;
        }
        return CJK_LANGS.contains(lang.substring(0, 2).toLowerCase());
    }
}
