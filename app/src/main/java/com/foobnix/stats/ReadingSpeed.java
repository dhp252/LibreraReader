package com.foobnix.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reading speed time left is estimated with, per book, as of the last
 * {@link StatsLog#refresh}: read on every page turn, so kept ready instead of computed.
 */
public final class ReadingSpeed {

    private static volatile StatsData data;
    private static final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private ReadingSpeed() {
    }

    static void update(StatsStore store) {
        data = store == null ? null : store.data(null);
        cache.clear();
    }

    /** Words per minute for this book (file name); 0 = not enough reading yet. */
    public static int wpm(String bookKey) {
        final StatsData d = data;
        if (d == null || bookKey == null) {
            return 0;
        }
        Integer wpm = cache.get(bookKey);
        if (wpm == null) {
            wpm = StatsReport.speedFor(d, bookKey);
            cache.put(bookKey, wpm);
        }
        return wpm;
    }

    /** The summaries of all devices as of the last refresh, null before the first. */
    public static StatsData data() {
        return data;
    }
}
