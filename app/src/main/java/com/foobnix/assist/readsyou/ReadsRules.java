package com.foobnix.assist.readsyou;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every threshold of "The book reads you back" (Assist.READS_YOU) and the small decisions made
 * with them, so they can be read and tested in one place. The signals are page turns and the
 * time a page stays on screen, nothing finer: the app does not know which line or word is read.
 * Pace is milliseconds per word, so a larger number is slower reading. No Android here.
 */
public final class ReadsRules {

    private ReadsRules() {
    }

    // ------------------------------------------------------------------ pages and pace

    /** A page with fewer words (a title, a picture) says nothing about pace. */
    public static final int MIN_WORDS = 40;
    /** The fastest and slowest pace taken as reading: 1000 and 20 words a minute. */
    public static final double PACE_FASTEST = 60;
    public static final double PACE_SLOWEST = 3000;
    /** Pages needed before the reader's own median pace is used. */
    public static final int PACE_MIN_SAMPLES = 5;
    /** The median is taken over this many latest pages. */
    public static final int PACE_KEEP = 40;
    /** A turn of more view pages than this at once is a jump, not a page turn. */
    public static final int TURN_MAX = 2;

    // ------------------------------------------------------------------ re-read

    /** Going back this many pages at most is paging back; further is a jump. */
    public static final int BACK_MAX_PAGES = 3;
    /** The earlier page must stay on screen this long to count as read again. */
    public static final long REREAD_MIN_MS = 4_000;
    /** Going back and forward again counts within this time. */
    public static final long REREAD_WINDOW_MS = 5 * 60_000L;
    /** Back on an earlier page this long: the help chip may come. */
    public static final long REREAD_OFFER_MS = 8_000;

    // ------------------------------------------------------------------ dwell

    /** A page taking this many times its expected time is a long dwell... */
    public static final double DWELL_FACTOR = 2.5;
    /** ...when that is at least this long. */
    public static final long DWELL_MIN_MS = 40_000;
    /** Past this many times the expected time the reader is taken to have left the page. */
    public static final double AWAY_FACTOR = 8;
    /** The least time before a page counts as left, and the time when the pace is not known. */
    public static final long AWAY_MIN_MS = 4 * 60_000L;
    public static final long AWAY_UNKNOWN_MS = 5 * 60_000L;

    // ------------------------------------------------------------------ session

    /** This long without reading ends the unbroken session. */
    public static final long BREAK_MS = 3 * 60_000L;
    /** A gap between two ticks longer than this means the app was not running. */
    public static final long TICK_GAP_MS = 5_000;

    // ------------------------------------------------------------------ slowdown and fatigue

    /** The session's first pages and its latest pages that are compared. */
    public static final int SLOW_BASE = 5;
    public static final int SLOW_RECENT = 5;
    /** Reading at least this much slower than at the session's start is a real slowdown. */
    public static final double SLOWDOWN_MIN = 0.20;
    /** An unbroken session this long, with a real slowdown, brings the break card. */
    public static final long FATIGUE_MIN_MS = 40 * 60_000L;
    /** "Keep reading": no break card for this long. */
    public static final long SNOOZE_MS = 20 * 60_000L;
    /** "Take 5 minutes". */
    public static final long BREAK_LENGTH_MS = 5 * 60_000L;

    // ------------------------------------------------------------------ decisions

    /** How long a page of {@code words} words takes at {@code pace} ms per word; -1 when either is unknown. */
    public static long expectedMs(int words, double pace) {
        return words < MIN_WORDS || pace <= 0 ? -1 : Math.round(words * pace);
    }

    /** The time on one page after which the reader is taken to have left it. */
    public static long awayLimit(int words, double pace) {
        final long expected = expectedMs(words, pace);
        return expected < 0 ? AWAY_UNKNOWN_MS : Math.max(AWAY_MIN_MS, Math.round(AWAY_FACTOR * expected));
    }

    /** A page taking much longer than the reader's own pace for its words (and not left alone). */
    public static boolean isDwell(long ms, int words, double pace) {
        final long expected = expectedMs(words, pace);
        return expected > 0 && ms >= Math.max(DWELL_MIN_MS, Math.round(DWELL_FACTOR * expected))
                && ms < awayLimit(words, pace);
    }

    /** Whether a page's time and words give a pace that can be reading. */
    public static boolean isSample(long ms, int words) {
        if (words < MIN_WORDS || ms <= 0) {
            return false;
        }
        final double pace = (double) ms / words;
        return pace >= PACE_FASTEST && pace <= PACE_SLOWEST;
    }

    /** The median of the values; 0 for none. */
    public static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        final int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    /**
     * How much slower the session's latest pages were read than its first ones, 0..1 (0.3 = 30%
     * slower); 0 while there are not enough pages for two separate groups, or when not slower.
     *
     * @param session the pace of each page of the unbroken session, in reading order
     */
    public static double slowdown(List<Double> session) {
        if (session == null || session.size() < SLOW_BASE + SLOW_RECENT) {
            return 0;
        }
        final double base = median(session.subList(0, SLOW_BASE));
        final double recent = median(session.subList(session.size() - SLOW_RECENT, session.size()));
        return base <= 0 || recent <= base ? 0 : 1 - base / recent;
    }

    /** {@link #slowdown} as a whole percent. */
    public static int percent(double slowdown) {
        return (int) Math.round(100 * Math.max(0, Math.min(1, slowdown)));
    }

    public static boolean isSlowdown(double slowdown) {
        return slowdown >= SLOWDOWN_MIN;
    }

    /**
     * Whether the break card may come: a long unbroken session, a real slowdown, and the reader
     * did not say "Keep reading" within the last {@link #SNOOZE_MS}.
     *
     * @param now         wall-clock time, ms
     * @param snoozeUntil wall-clock time before which the card stays away, 0 for none
     */
    public static boolean wantsBreak(long sessionMs, double slowdown, long now, long snoozeUntil) {
        return sessionMs >= FATIGUE_MIN_MS && isSlowdown(slowdown) && now >= snoozeUntil;
    }

    /** Whole minutes of a time, rounded down: never more than was measured. */
    public static int minutes(long ms) {
        return (int) (Math.max(0, ms) / 60_000L);
    }

    /** Words a minute for a pace, 0 when unknown. */
    public static int wpm(double pace) {
        return pace <= 0 ? 0 : (int) Math.round(60_000 / pace);
    }

    // ------------------------------------------------------------------ "How you read it"

    /** A section read in less than this share of its expected time was skimmed. */
    public static final double SKIM_FACTOR = 0.4;
    /** A chapter without sub-sections in the table of contents is shown in this many blocks of pages at most. */
    public static final int SECTION_BLOCKS = 8;
    /** More sub-sections than this are too many rows: only the first level below the chapter is shown, or blocks. */
    public static final int SECTION_MAX = 14;
    /** Less measured reading than this in a chapter is too little to say how it was read. */
    public static final int MAP_MIN_SECONDS = 60;

    /** Whether {@code seconds} for {@code words} words is much faster than the reader's pace. */
    public static boolean isSkim(int seconds, int words, double pace) {
        return pace > 0 && words >= MIN_WORDS && seconds * 1000.0 < SKIM_FACTOR * words * pace;
    }

    // ------------------------------------------------------------------ with Eye mode's camera

    /** Two frames further apart than this are not one stretch of watching (the camera stopped or was busy). */
    public static final long FRAME_GAP_MS = 2_000;
    /** Frames closer together than this are skipped for blinks, so 5 and 10 frames a second count alike. */
    public static final long BLINK_FRAME_MS = 180;
    /** Two blink frames further apart than this are not compared (a closed eye between them is unknown). */
    public static final long BLINK_GAP_MS = 600;
    /** The reader's own blink baseline: the first minutes the camera watched in this session... */
    public static final long BLINK_WINDOW_MS = 5 * 60_000L;
    /** ...needs at least this many blinks to compare against... */
    public static final int BLINK_MIN = 8;
    /** ...and "blinking more" is this many times the baseline, over the last window. */
    public static final double BLINK_MORE = 1.5;

    /** Eyes off one page for this long in one stretch is a drift... */
    public static final long DRIFT_AWAY_MS = 20_000;
    /** ...unless it is this long: then the reader left, which is a break, not a drift. */
    public static final long DRIFT_AWAY_MAX_MS = BREAK_MS;
    /** A look-away of at least this long, and the page then passed... */
    public static final long DRIFT_GLANCE_MS = 5_000;
    /** ...in less than this share of its expected time, is a drift too. */
    public static final double DRIFT_FAST_FACTOR = 0.3;
    /** No second drift card sooner than this (the marks are still counted). */
    public static final long DRIFT_QUIET_MS = 2 * 60_000L;

    /** The gaze zones of a second pass (Eye mode's own: EyeRules.BOTTOM_ZONE / TOP_ZONE / BOTTOM_DWELL_MS). */
    public static final float PASS_BOTTOM = 0.72f;
    public static final float PASS_TOP = 0.35f;
    public static final long PASS_BOTTOM_MS = 1_000;
    /** The gaze must stay at the top this long after the jump back... */
    public static final long PASS_TOP_MS = 600;
    /** ...and the page must still be shown this long after it: a look up just before a turn is no second pass. */
    public static final long PASS_CONFIRM_MS = 3_000;

    /** Whether one stretch of eyes off the page is a drift. */
    public static boolean isDriftAway(long awayRunMs) {
        return awayRunMs >= DRIFT_AWAY_MS && awayRunMs < DRIFT_AWAY_MAX_MS;
    }

    /** Whether a page passed in {@code ms} after a look-away of {@code awayRunMs} was passed far too fast. */
    public static boolean isDriftFast(long ms, int words, double pace, long awayRunMs) {
        return awayRunMs >= DRIFT_GLANCE_MS && awayRunMs < DRIFT_AWAY_MAX_MS && pace > 0 && words >= MIN_WORDS
                && ms < DRIFT_FAST_FACTOR * expectedMs(words, pace);
    }

    /** The break card with the camera: a long session, and a real slowdown or more blinking than the baseline. */
    public static boolean wantsBreak(long sessionMs, double slowdown, boolean blinkingMore, long now, long snoozeUntil) {
        return sessionMs >= FATIGUE_MIN_MS && (isSlowdown(slowdown) || blinkingMore) && now >= snoozeUntil;
    }

    // ------------------------------------------------------------------ auto-zoom

    /** A section that took this many times its expected time was read slowly. */
    public static final double SLOW_FACTOR = 1.5;

    /** Whether {@code seconds} for {@code words} words is clearly slower than the reader's pace. */
    public static boolean isSlow(int seconds, int words, double pace) {
        return pace > 0 && words >= MIN_WORDS && seconds * 1000.0 > SLOW_FACTOR * words * pace;
    }
}
