package com.foobnix.stats;

import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.tts.TTSEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logs listening: read aloud on ({@link #reading}, called as TTS starts each page) and off,
 * found by checking every few seconds whether it still plays. Read aloud has many ways to stop
 * (pause, the notification, a headset, the end of the book, the screen off), so checking beats
 * hooking each one.
 */
public final class ListenTracker {

    private static final long CHECK_MS = 5_000L;
    /** Silent this many checks in a row: stopped (a page change is a short silence). */
    private static final int SILENT_CHECKS = 2;

    private static final ScheduledExecutorService checker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "@T stats listen"));
    private static ScheduledFuture<?> check;
    private static String book;
    private static long lastPlaying;
    private static int silent;

    private ListenTracker() {
    }

    /** Read aloud starts a page of this book. */
    public static synchronized void reading(String path) {
        if (path == null) {
            return;
        }
        final String name = ExtUtils.getFileName(path);
        lastPlaying = System.currentTimeMillis();
        silent = 0;
        if (name.equals(book)) {
            return;
        }
        if (book != null) {
            stop(lastPlaying);
        }
        book = name;
        final StatsEvent on = StatsLog.event(StatsEvent.TTS, book);
        on.on = true;
        StatsLog.log(on);
        check = checker.scheduleWithFixedDelay(ListenTracker::check, CHECK_MS, CHECK_MS, TimeUnit.MILLISECONDS);
    }

    private static synchronized void check() {
        try {
            if (TTSEngine.get().isPlaying()) {
                lastPlaying = System.currentTimeMillis();
                silent = 0;
            } else if (++silent >= SILENT_CHECKS) {
                stop(lastPlaying);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void stop(long at) {
        if (book == null) {
            return;
        }
        final StatsEvent off = StatsLog.event(StatsEvent.TTS, book);
        // when it was last heard playing, not when the silence was noticed
        off.ts = Math.min(at, off.ts);
        off.on = false;
        StatsLog.log(off);
        book = null;
        if (check != null) {
            check.cancel(false);
            check = null;
        }
    }
}
