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
}
