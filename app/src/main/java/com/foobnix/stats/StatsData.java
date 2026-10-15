package com.foobnix.stats;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Summaries of the reading log: per local day and per book. {@link StatsEngine} keeps one per
 * device; {@link #merge} combines the devices a view shows. Days are epoch days of the reader's
 * local time ({@link #dayOf}).
 */
public class StatsData {

    public final TreeMap<Integer, Day> days = new TreeMap<>();
    public final Map<String, Book> books = new HashMap<>();
    /** Device id -> name (for "All" the devices merged). */
    public final Map<String, String> devices = new LinkedHashMap<>();

    public static int dayOf(long ts, int tzMinutes) {
        return (int) Math.floorDiv(ts + tzMinutes * 60_000L, 86_400_000L);
    }

    public static int hourOf(long ts, int tzMinutes) {
        return (int) (Math.floorMod(ts + tzMinutes * 60_000L, 86_400_000L) / 3_600_000L);
    }

    /** 0 = Monday ... 6 = Sunday. */
    public static int weekdayOf(int day) {
        return Math.floorMod(day + 3, 7);
    }

    public Day day(int day) {
        Day d = days.get(day);
        if (d == null) {
            d = new Day();
            days.put(day, d);
        }
        return d;
    }

    public Book book(String key) {
        Book b = books.get(key);
        if (b == null) {
            b = new Book(key);
            books.put(key, b);
        }
        return b;
    }

    /** Median WPM buckets: bucket -> pages. */
    public static class Hist {
        public final TreeMap<Integer, Integer> counts = new TreeMap<>();
        public int total;

        public void add(double wpm) {
            final int bucket = (int) (wpm / StatsRules.WPM_BUCKET);
            counts.put(bucket, counts.getOrDefault(bucket, 0) + 1);
            total++;
        }

        public void addAll(Hist other) {
            for (Map.Entry<Integer, Integer> e : other.counts.entrySet()) {
                counts.put(e.getKey(), counts.getOrDefault(e.getKey(), 0) + e.getValue());
            }
            total += other.total;
        }

        /** @return 0 when empty */
        public int median() {
            if (total == 0) {
                return 0;
            }
            final int half = (total + 1) / 2;
            int seen = 0;
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                seen += e.getValue();
                if (seen >= half) {
                    return e.getKey() * StatsRules.WPM_BUCKET + StatsRules.WPM_BUCKET / 2;
                }
            }
            return 0;
        }
    }

    /** Time and words of the pages that count for speed. */
    public static class Speed {
        public long ms;
        public long words;

        public void add(long ms, long words) {
            this.ms += ms;
            this.words += words;
        }

        public void addAll(Speed other) {
            add(other.ms, other.words);
        }

        public int wpm() {
            return ms <= 0 ? 0 : (int) Math.round(words * 60_000.0 / ms);
        }
    }

    public static class Day {
        public long readMs;
        public long listenMs;
        /** Words of pages read (not of skimmed ones). */
        public long words;
        /** Real pages of fixed-layout books plus virtual pages of the others. */
        public float pages;
        public int opens;
        public int sessions;
        public long sessionMs;
        public long longestSessionMs;
        public int bounces;
        public int skims;
        public int backtracks;
        public int rereads;
        public int switches;
        public int highlights;
        public int notes;
        public int bookmarks;
        public int searches;
        public int lookups;
        public int finishes;
        public int starts;
        public final Hist wpm = new Hist();
        /** Characters per minute of CJK books. */
        public final Hist cpm = new Hist();
        public final long[] hourMs = new long[24];
        public final Speed[] hourSpeed = newSpeeds(24);
        public final Map<String, Speed> langSpeed = new HashMap<>();
        /** 10-minute stretch of a session -> speed in it. */
        public final Map<Integer, Speed> fatigue = new HashMap<>();
        /** Reading and listening per book this day. */
        public final Map<String, Long> bookMs = new HashMap<>();

        public long totalMs() {
            return readMs + listenMs;
        }

        void addBookMs(String book, long ms) {
            bookMs.put(book, bookMs.getOrDefault(book, 0L) + ms);
        }

        void addAll(Day o) {
            readMs += o.readMs;
            listenMs += o.listenMs;
            words += o.words;
            pages += o.pages;
            opens += o.opens;
            sessions += o.sessions;
            sessionMs += o.sessionMs;
            longestSessionMs = Math.max(longestSessionMs, o.longestSessionMs);
            bounces += o.bounces;
            skims += o.skims;
            backtracks += o.backtracks;
            rereads += o.rereads;
            switches += o.switches;
            highlights += o.highlights;
            notes += o.notes;
            bookmarks += o.bookmarks;
            searches += o.searches;
            lookups += o.lookups;
            finishes += o.finishes;
            starts += o.starts;
            wpm.addAll(o.wpm);
            cpm.addAll(o.cpm);
            for (int h = 0; h < 24; h++) {
                hourMs[h] += o.hourMs[h];
                hourSpeed[h].addAll(o.hourSpeed[h]);
            }
            addSpeeds(langSpeed, o.langSpeed);
            addSpeeds(fatigue, o.fatigue);
            for (Map.Entry<String, Long> e : o.bookMs.entrySet()) {
                addBookMs(e.getKey(), e.getValue());
            }
        }
    }

    public static class Chapter {
        public final String title;
        /** First page it was seen on, to list chapters in book order. */
        public int firstPage;
        public long ms;
        public long words;
        public final Speed speed = new Speed();

        public Chapter(String title, int firstPage) {
            this.title = title;
            this.firstPage = firstPage;
        }
    }

    public static class Book {
        public final String key;
        public String title;
        public String author;
        public String lang;
        public String ext;
        public int pages;
        public long firstOpen;
        public long lastOpen;
        public long firstRead;
        public long readMs;
        public long listenMs;
        public long words;
        public float pagesRead;
        /** Page views read (2 s or more), the count skims are compared with. */
        public int pageViews;
        public final Speed speed = new Speed();
        public final Hist wpm = new Hist();
        public int opens;
        public int sessions;
        public int bounces;
        public int skims;
        public int backtracks;
        public int rereads;
        public int highlights;
        public int notes;
        public int bookmarks;
        public int searches;
        public int lookups;
        public final List<Long> finishTimes = new ArrayList<>();
        /** Last known progress (0..1) and when. */
        public float progress;
        public long progressTs;
        /** Finished and not started again from the beginning. */
        public boolean finished;
        public final Set<Integer> readDays = new HashSet<>();
        public final TreeMap<Integer, Long> wordsByDay = new TreeMap<>();
        public final Map<String, Chapter> chapters = new LinkedHashMap<>();
        /** Words of pages with a count, and how many such pages: the average fills uncounted pages. */
        public long countedWords;
        public int countedPages;

        // device-local state of the engine, not merged
        transient int prevPage = -1;
        transient BitSet seen = new BitSet(StatsRules.PROGRESS_SLOTS);

        public Book(String key) {
            this.key = key;
        }

        public boolean isFixedLayout() {
            return StatsRules.isFixedLayout(ext);
        }

        public boolean isCjk() {
            return StatsRules.isCjkLanguage(lang);
        }

        public int finishes() {
            return finishTimes.size();
        }

        public long totalMs() {
            return readMs + listenMs;
        }

        /** Words of an average page so far, 0 when no page was counted. */
        public int averagePageWords() {
            return countedPages == 0 ? 0 : (int) (countedWords / countedPages);
        }

        public long lastFinish() {
            long last = 0;
            for (long t : finishTimes) {
                last = Math.max(last, t);
            }
            return last;
        }

        public String displayTitle() {
            return title == null || title.isEmpty() ? key : title;
        }

        void addAll(Book o) {
            if (o.title != null && (title == null || o.lastOpen >= lastOpen)) {
                title = o.title;
            }
            if (o.author != null && (author == null || o.lastOpen >= lastOpen)) {
                author = o.author;
            }
            if (o.lang != null && lang == null) {
                lang = o.lang;
            }
            if (o.ext != null && ext == null) {
                ext = o.ext;
            }
            if (o.progressTs >= progressTs) {
                progress = o.progress;
                progressTs = o.progressTs;
                finished = o.finished;
                if (o.pages > 0) {
                    pages = o.pages;
                }
            }
            firstOpen = minSet(firstOpen, o.firstOpen);
            firstRead = minSet(firstRead, o.firstRead);
            lastOpen = Math.max(lastOpen, o.lastOpen);
            readMs += o.readMs;
            listenMs += o.listenMs;
            words += o.words;
            pagesRead += o.pagesRead;
            pageViews += o.pageViews;
            speed.addAll(o.speed);
            wpm.addAll(o.wpm);
            opens += o.opens;
            sessions += o.sessions;
            bounces += o.bounces;
            skims += o.skims;
            backtracks += o.backtracks;
            rereads += o.rereads;
            highlights += o.highlights;
            notes += o.notes;
            bookmarks += o.bookmarks;
            searches += o.searches;
            lookups += o.lookups;
            finishTimes.addAll(o.finishTimes);
            readDays.addAll(o.readDays);
            for (Map.Entry<Integer, Long> e : o.wordsByDay.entrySet()) {
                wordsByDay.put(e.getKey(), wordsByDay.getOrDefault(e.getKey(), 0L) + e.getValue());
            }
            for (Chapter c : o.chapters.values()) {
                Chapter mine = chapters.get(c.title);
                if (mine == null) {
                    mine = new Chapter(c.title, c.firstPage);
                    chapters.put(c.title, mine);
                }
                mine.firstPage = Math.min(mine.firstPage, c.firstPage);
                mine.ms += c.ms;
                mine.words += c.words;
                mine.speed.addAll(c.speed);
            }
            countedWords += o.countedWords;
            countedPages += o.countedPages;
        }
    }

    /** The given devices' data as one (all of them when {@code ids} is null). */
    public static StatsData merge(Map<String, StatsData> perDevice, Set<String> ids) {
        final StatsData all = new StatsData();
        for (Map.Entry<String, StatsData> e : perDevice.entrySet()) {
            if (ids != null && !ids.contains(e.getKey())) {
                continue;
            }
            final StatsData d = e.getValue();
            all.devices.putAll(d.devices);
            for (Map.Entry<Integer, Day> day : d.days.entrySet()) {
                all.day(day.getKey()).addAll(day.getValue());
            }
            for (Book b : d.books.values()) {
                all.book(b.key).addAll(b);
            }
        }
        return all;
    }

    static Speed[] newSpeeds(int n) {
        final Speed[] speeds = new Speed[n];
        for (int i = 0; i < n; i++) {
            speeds[i] = new Speed();
        }
        return speeds;
    }

    static <K> void addSpeeds(Map<K, Speed> into, Map<K, Speed> from) {
        for (Map.Entry<K, Speed> e : from.entrySet()) {
            Speed s = into.get(e.getKey());
            if (s == null) {
                s = new Speed();
                into.put(e.getKey(), s);
            }
            s.addAll(e.getValue());
        }
    }

    static <K> Speed speed(Map<K, Speed> map, K key) {
        Speed s = map.get(key);
        if (s == null) {
            s = new Speed();
            map.put(key, s);
        }
        return s;
    }

    private static long minSet(long a, long b) {
        if (a == 0) {
            return b;
        }
        if (b == 0) {
            return a;
        }
        return Math.min(a, b);
    }
}
