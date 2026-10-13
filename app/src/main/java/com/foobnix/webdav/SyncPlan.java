package com.foobnix.webdav;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What to sync and when, from the events of the app (.agents/planning/event-sync-design.md).
 * Pure: it is handed the time of each event, so the waits are JVM-tested; {@link SyncEvents}
 * runs what falls due.
 */
final class SyncPlan {

    static final long NEVER = Long.MAX_VALUE;
    /** A turned page or a bookmark goes up once nothing more changed for this long... */
    static final long SETTLE_MS = 10_000;
    /** ...and settings once they were left alone this long... */
    static final long SETTINGS_SETTLE_MS = 30_000;
    /** ...but never later than this after the first change not sent: steady reading still syncs. */
    static final long MAX_WAIT_MS = 60_000;
    /** Coming back to the app syncs everything, at most once in this time. */
    static final long FULL_GAP_MS = 60_000;

    /** One run: what is due. */
    static final class Job {
        boolean full;
        boolean push;
        boolean settings;
        final Set<String> books = new LinkedHashSet<>();

        boolean isEmpty() {
            return !full && !push && !settings && books.isEmpty();
        }
    }

    private long pushAt = NEVER;
    private long pushFirst = NEVER;
    private long settingsAt = NEVER;
    private long settingsFirst = NEVER;
    private boolean full;
    private long lastFull = Long.MIN_VALUE / 2;
    private final Set<String> books = new LinkedHashSet<>();

    /** Reading progress or bookmarks changed here. */
    void changed(long now) {
        if (pushFirst == NEVER) {
            pushFirst = now;
        }
        pushAt = Math.min(now + SETTLE_MS, pushFirst + MAX_WAIT_MS);
    }

    /** Something to send at once (an AI result). */
    void changedNow(long now) {
        if (pushFirst == NEVER) {
            pushFirst = now;
        }
        pushAt = now;
    }

    void settingsChanged(long now) {
        if (settingsFirst == NEVER) {
            settingsFirst = now;
        }
        settingsAt = Math.min(now + SETTINGS_SETTLE_MS, settingsFirst + MAX_WAIT_MS);
    }

    /** The app came to the foreground. */
    void foreground(long now) {
        if (now - lastFull >= FULL_GAP_MS) {
            full = true;
        }
    }

    /** Another device changed something: everything, now. */
    void fullNow() {
        full = true;
    }

    /** A full sync was queued (by anyone): it sends whatever was waiting. */
    void fullQueued(long now) {
        lastFull = now;
        full = false;
        pushAt = pushFirst = NEVER;
        settingsAt = settingsFirst = NEVER;
    }

    /** The files of this book, now (opened, shown again, or changed by another device). */
    void pull(String book) {
        books.add(book);
    }

    /** The app is leaving: true when changes were still waiting, which are then handed over. */
    boolean takeWaiting() {
        final boolean waiting = pushAt != NEVER || settingsAt != NEVER;
        pushAt = pushFirst = NEVER;
        settingsAt = settingsFirst = NEVER;
        return waiting;
    }

    /** When the next run falls due; {@link #NEVER} when nothing waits. */
    long nextDue() {
        if (full || !books.isEmpty()) {
            return 0;
        }
        return Math.min(pushAt, settingsAt);
    }

    /** What is due at {@code now}, taken off the plan. */
    Job take(long now) {
        final Job job = new Job();
        if (full) {
            job.full = true;
            fullQueued(now);
        } else {
            if (pushAt <= now) {
                job.push = true;
                pushAt = pushFirst = NEVER;
            }
            if (settingsAt <= now) {
                job.settings = true;
                settingsAt = settingsFirst = NEVER;
            }
        }
        job.books.addAll(books);
        books.clear();
        return job;
    }
}
