package com.foobnix.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the stats screens show, computed from {@link StatsData}: totals of a period, habits,
 * books, library, predictions, time left. Days are epoch days ({@link Days}).
 */
public final class StatsReport {

    private StatsReport() {
    }

    // ---------------------------------------------------------------- periods

    public enum Period {
        DAY, WEEK, MONTH, YEAR, ALL;

        /** [first, last] day of the period holding {@code today}. */
        public int[] range(int today, StatsData d) {
            switch (this) {
                case DAY:
                    return new int[]{today, today};
                case WEEK:
                    return new int[]{Days.weekStart(today), Days.weekStart(today) + 6};
                case MONTH:
                    return new int[]{Days.monthStart(today), Days.monthEnd(today)};
                case YEAR:
                    return new int[]{Days.yearStart(today), Days.yearEnd(today)};
                default:
                    return new int[]{d.days.isEmpty() ? today : Math.min(today, d.days.firstKey()), today};
            }
        }

        /** The period before, for comparisons; null for {@link #ALL}. */
        public int[] previous(int today, StatsData d) {
            switch (this) {
                case DAY:
                    return new int[]{today - 1, today - 1};
                case WEEK:
                    return new int[]{Days.weekStart(today) - 7, Days.weekStart(today) - 1};
                case MONTH: {
                    final int last = Days.monthStart(today) - 1;
                    return new int[]{Days.monthStart(last), last};
                }
                case YEAR: {
                    final int last = Days.yearStart(today) - 1;
                    return new int[]{Days.yearStart(last), last};
                }
                default:
                    return null;
            }
        }
    }

    // ---------------------------------------------------------------- totals of a period

    public static class Summary {
        public int from;
        public int to;
        public long readMs;
        public long listenMs;
        /** Eye mode (F-010): reading time it watched, and the time the reader looked. */
        public long eyeMs;
        public long lookMs;
        public long words;
        public float pages;
        /** Median words per minute, 0 without timed pages. */
        public int wpm;
        /** Median characters per minute of CJK books. */
        public int cpm;
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
        public int daysRead;
        /** Weekday (0 = Monday) x hour: minutes read. */
        public final long[][] heatMs = new long[7][24];
        public final StatsData.Speed[] hourSpeed = StatsData.newSpeeds(24);
        public final Map<String, StatsData.Speed> langSpeed = new HashMap<>();
        public final Map<Integer, StatsData.Speed> fatigue = new HashMap<>();
        public final Map<String, Long> bookMs = new HashMap<>();

        public long totalMs() {
            return readMs + listenMs;
        }

        public long averageSessionMs() {
            return sessions == 0 ? 0 : sessionMs / sessions;
        }

        public float sessionsPerDay() {
            return daysRead == 0 ? 0 : sessions / (float) daysRead;
        }

        /** Per 1,000 words read; 0 without words. */
        public float per1000(int count) {
            return words == 0 ? 0 : count * 1000f / words;
        }
    }

    public static Summary summary(StatsData d, int from, int to) {
        final Summary s = new Summary();
        s.from = from;
        s.to = to;
        if (from > to) {
            return s;
        }
        final StatsData.Hist wpm = new StatsData.Hist();
        final StatsData.Hist cpm = new StatsData.Hist();
        for (Map.Entry<Integer, StatsData.Day> e : d.days.subMap(from, true, to, true).entrySet()) {
            final StatsData.Day day = e.getValue();
            s.readMs += day.readMs;
            s.listenMs += day.listenMs;
            s.eyeMs += day.eyeMs;
            s.lookMs += day.lookMs;
            s.words += day.words;
            s.pages += day.pages;
            s.opens += day.opens;
            s.sessions += day.sessions;
            s.sessionMs += day.sessionMs;
            s.longestSessionMs = Math.max(s.longestSessionMs, day.longestSessionMs);
            s.bounces += day.bounces;
            s.skims += day.skims;
            s.backtracks += day.backtracks;
            s.rereads += day.rereads;
            s.switches += day.switches;
            s.highlights += day.highlights;
            s.notes += day.notes;
            s.bookmarks += day.bookmarks;
            s.searches += day.searches;
            s.lookups += day.lookups;
            s.finishes += day.finishes;
            s.starts += day.starts;
            if (day.totalMs() >= StatsRules.STREAK_DAY_MS) {
                s.daysRead++;
            }
            wpm.addAll(day.wpm);
            cpm.addAll(day.cpm);
            final int weekday = StatsData.weekdayOf(e.getKey());
            for (int h = 0; h < 24; h++) {
                s.heatMs[weekday][h] += day.hourMs[h];
                s.hourSpeed[h].addAll(day.hourSpeed[h]);
            }
            StatsData.addSpeeds(s.langSpeed, day.langSpeed);
            StatsData.addSpeeds(s.fatigue, day.fatigue);
            for (Map.Entry<String, Long> b : day.bookMs.entrySet()) {
                s.bookMs.put(b.getKey(), s.bookMs.getOrDefault(b.getKey(), 0L) + b.getValue());
            }
        }
        s.wpm = wpm.median();
        s.cpm = cpm.median();
        return s;
    }

    /** What a bar chart of the period shows: one bar per hour, day or month. */
    public static class Series {
        public final List<String> labels = new ArrayList<>();
        public final List<Long> ms = new ArrayList<>();
        public final List<Long> words = new ArrayList<>();
        public final List<Float> pages = new ArrayList<>();
    }

    /**
     * Bars of the period: days for a week or month, months for a year or everything. A day has
     * no hours in the summaries, so it shows the 7 days up to it.
     */
    public static Series series(StatsData d, Period period, int today) {
        final Series s = new Series();
        final int[] range = period.range(today, d);
        if (period == Period.DAY || period == Period.WEEK || period == Period.MONTH) {
            final int from = period == Period.DAY ? today - 6 : range[0];
            for (int day = from; day <= range[1]; day++) {
                final StatsData.Day x = d.days.get(day);
                s.labels.add(period == Period.MONTH ? String.valueOf(Days.dayOfMonth(day)) : weekdayLetter(day));
                s.ms.add(x == null ? 0 : x.totalMs());
                s.words.add(x == null ? 0 : x.words);
                s.pages.add(x == null ? 0 : x.pages);
            }
            return s;
        }
        int month = Days.monthStart(range[0]);
        while (month <= range[1]) {
            final int end = Days.monthEnd(month);
            final Summary m = summary(d, month, end);
            s.labels.add(period == Period.YEAR ? String.valueOf(Days.month(month)) : Days.monthKey(month).substring(2));
            s.ms.add(m.totalMs());
            s.words.add(m.words);
            s.pages.add(m.pages);
            month = end + 1;
        }
        return s;
    }

    private static String weekdayLetter(int day) {
        return "MTWTFSS".substring(StatsData.weekdayOf(day), StatsData.weekdayOf(day) + 1);
    }

    /** Median WPM of each of the {@code weeks} weeks up to the one holding {@code today}; 0 = no data. */
    public static int[] weeklyWpm(StatsData d, int today, int weeks) {
        final int[] out = new int[weeks];
        final int lastWeek = Days.weekStart(today);
        for (int i = 0; i < weeks; i++) {
            final int start = lastWeek - 7 * (weeks - 1 - i);
            out[i] = summary(d, start, start + 6).wpm;
        }
        return out;
    }

    // ---------------------------------------------------------------- habits

    public static class Streak {
        public int current;
        public int longest;
        /** Days of the month holding today that had reading, by day of month (index 1..31). */
        public final boolean[] monthDays = new boolean[32];
        public int daysThisMonth;
    }

    public static Streak streak(StatsData d, int today) {
        final Streak s = new Streak();
        int run = 0;
        Integer previous = null;
        for (Map.Entry<Integer, StatsData.Day> e : d.days.entrySet()) {
            if (e.getValue().totalMs() < StatsRules.STREAK_DAY_MS) {
                continue;
            }
            run = previous != null && previous == e.getKey() - 1 ? run + 1 : 1;
            previous = e.getKey();
            s.longest = Math.max(s.longest, run);
        }
        // today not read yet does not break the streak
        int day = read(d, today) ? today : today - 1;
        while (read(d, day)) {
            s.current++;
            day--;
        }
        final int start = Days.monthStart(today);
        for (int x = start; x <= Days.monthEnd(today); x++) {
            if (read(d, x)) {
                s.monthDays[x - start + 1] = true;
                s.daysThisMonth++;
            }
        }
        return s;
    }

    private static boolean read(StatsData d, int day) {
        final StatsData.Day x = d.days.get(day);
        return x != null && x.totalMs() >= StatsRules.STREAK_DAY_MS;
    }

    public static class Goal {
        public int goalMinutes;
        public int todayMinutes;
        /** Minutes ahead (+) or behind (-) of the goal this week, today included. */
        public int weekBalance;
    }

    /** @return null when no goal is set */
    public static Goal goal(StatsData d, int today, int goalMinutes) {
        if (goalMinutes <= 0) {
            return null;
        }
        final Goal g = new Goal();
        g.goalMinutes = goalMinutes;
        g.todayMinutes = minutes(summary(d, today, today).totalMs());
        final int weekStart = Days.weekStart(today);
        final int week = minutes(summary(d, weekStart, today).totalMs());
        g.weekBalance = week - goalMinutes * (today - weekStart + 1);
        return g;
    }

    /** The hour of the day with the most and fastest reading; -1 without enough reading. */
    public static int bestHour(Summary s) {
        int best = -1;
        double bestScore = 0;
        for (int h = 0; h < 24; h++) {
            long ms = 0;
            for (int w = 0; w < 7; w++) {
                ms += s.heatMs[w][h];
            }
            final int wpm = s.hourSpeed[h].wpm();
            if (ms < StatsRules.BEST_HOUR_MIN_MS || wpm == 0) {
                continue;
            }
            final double score = ms * (double) wpm;
            if (score > bestScore) {
                bestScore = score;
                best = h;
            }
        }
        return best;
    }

    /** WPM in each 10-minute stretch of a session, while stretches have enough timed reading. */
    public static List<Integer> fatigue(Summary s) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final StatsData.Speed speed = s.fatigue.get(i);
            if (speed == null || speed.ms < 5 * 60_000L) {
                break;
            }
            out.add(speed.wpm());
        }
        return out;
    }

    /** WPM by part of the day: night (0-6), morning, afternoon, evening. */
    public static int[] partsOfDay(Summary s) {
        final int[] out = new int[4];
        for (int part = 0; part < 4; part++) {
            final StatsData.Speed speed = new StatsData.Speed();
            for (int h = part * 6; h < part * 6 + 6; h++) {
                speed.addAll(s.hourSpeed[h]);
            }
            out[part] = speed.wpm();
        }
        return out;
    }

    // ---------------------------------------------------------------- books

    public static boolean isStarted(StatsData.Book b) {
        return b.firstRead > 0;
    }

    public static boolean isStopped(StatsData.Book b, long now) {
        return isStarted(b) && !b.finished && now - b.lastOpen > StatsRules.STOPPED_AFTER_MS;
    }

    public static boolean isInProgress(StatsData.Book b, long now) {
        return isStarted(b) && !b.finished && !isStopped(b, now);
    }

    /** Books by most recently opened first. */
    public static List<StatsData.Book> books(StatsData d, BookFilter filter) {
        final List<StatsData.Book> out = new ArrayList<>();
        for (StatsData.Book b : d.books.values()) {
            if (filter.accept(b)) {
                out.add(b);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastOpen, a.lastOpen));
        return out;
    }

    public interface BookFilter {
        boolean accept(StatsData.Book b);
    }

    /** A finish of a book in a period: how many days and hours the reading took. */
    public static class Finish {
        public final StatsData.Book book;
        public final long at;
        public final int days;
        public final long ms;

        Finish(StatsData.Book book, long at, int days, long ms) {
            this.book = book;
            this.at = at;
            this.days = days;
            this.ms = ms;
        }
    }

    public static List<Finish> finishes(StatsData d, int from, int to, int tzMinutes) {
        final List<Finish> out = new ArrayList<>();
        for (StatsData.Book b : d.books.values()) {
            for (long at : b.finishTimes) {
                final int day = StatsData.dayOf(at, tzMinutes);
                if (day < from || day > to) {
                    continue;
                }
                final int first = b.firstRead > 0 ? StatsData.dayOf(b.firstRead, tzMinutes) : day;
                out.add(new Finish(b, at, day - first + 1, b.totalMs()));
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.at, a.at));
        return out;
    }

    /** Books finished by the end of the year at the finish rate of the last 90 days. */
    public static int projectedBooksThisYear(StatsData d, int today) {
        final int finishedThisYear = summary(d, Days.yearStart(today), today).finishes;
        final int recent = summary(d, today - StatsRules.FINISH_RATE_DAYS + 1, today).finishes;
        final int daysLeft = Days.yearEnd(today) - today;
        return finishedThisYear + (int) Math.floor(recent * daysLeft / (double) StatsRules.FINISH_RATE_DAYS);
    }

    // ---------------------------------------------------------------- one book

    /** Words per minute to estimate with: the book's own, else the overall; 0 = not enough reading. */
    public static int speedFor(StatsData d, String bookKey) {
        final StatsData.Book b = d.books.get(bookKey);
        if (b != null && b.speed.ms >= StatsRules.BOOK_WPM_AFTER_MS) {
            return b.wpm.median();
        }
        final boolean cjk = b != null && b.isCjk();
        final StatsData.Hist all = new StatsData.Hist();
        final StatsData.Speed time = new StatsData.Speed();
        for (StatsData.Book x : d.books.values()) {
            if (x.isCjk() == cjk) {
                all.addAll(x.wpm);
                time.addAll(x.speed);
            }
        }
        return time.ms >= StatsRules.ALL_WPM_AFTER_MS ? all.median() : 0;
    }

    /** Minutes to read this many words; -1 when the speed is not known yet. */
    public static int minutesFor(long words, int wpm) {
        if (wpm <= 0 || words < 0) {
            return -1;
        }
        return (int) Math.ceil(words / (double) wpm);
    }

    /** Words still to read when only the progress is known: an average page times the pages left. */
    public static long wordsLeftEstimate(StatsData.Book b) {
        if (b.pages <= 0 || b.averagePageWords() == 0) {
            return -1;
        }
        return Math.round((1 - Math.min(1f, b.progress)) * b.pages * (double) b.averagePageWords());
    }

    /**
     * The day the book is finished at the pace of the last 14 days (words per day it was read);
     * -1 without reading in that time.
     */
    public static int finishDay(StatsData.Book b, long wordsLeft, int today) {
        if (wordsLeft < 0) {
            return -1;
        }
        long words = 0;
        int days = 0;
        for (Map.Entry<Integer, Long> e : b.wordsByDay.subMap(today - StatsRules.PACE_DAYS + 1, true, today, true).entrySet()) {
            if (e.getValue() > 0) {
                words += e.getValue();
                days++;
            }
        }
        if (words == 0) {
            return -1;
        }
        final double perDay = words / (double) days;
        return today + (int) Math.ceil(wordsLeft / perDay);
    }

    /** Chapters in book order. */
    public static List<StatsData.Chapter> chapters(StatsData.Book b) {
        final List<StatsData.Chapter> out = new ArrayList<>(b.chapters.values());
        Collections.sort(out, (x, y) -> Integer.compare(x.firstPage, y.firstPage));
        return out;
    }

    // ---------------------------------------------------------------- library

    /** A book of the library, from the app's book database. */
    public static class LibBook {
        public final String key;
        public final String author;
        public final String genre;
        public final String lang;
        public final int pages;
        /** When it came into the library (the file's date), ms. */
        public final long added;
        /** Progress the app knows from before the stats (0..1). */
        public final float progress;

        public LibBook(String key, String author, String genre, String lang, int pages, long added, float progress) {
            this.key = key;
            this.author = author;
            this.genre = genre;
            this.lang = lang;
            this.pages = pages;
            this.added = added;
            this.progress = progress;
        }
    }

    public static class Library {
        /** Minutes read per genre, author, language and length, largest first. */
        public Map<String, Long> byGenre;
        public Map<String, Long> byAuthor;
        public Map<String, Long> byLanguage;
        public Map<String, Long> byLength;
        /** Books never started. */
        public int backlog;
        public int acquired;
        public int finished;
        /** Average pages of finished and of stopped books, 0 when none. */
        public int finishedPages;
        public int stoppedPages;
    }

    public static Library library(StatsData d, List<LibBook> lib, int from, int to, long now, int tzMinutes) {
        final Map<String, LibBook> byKey = new HashMap<>();
        for (LibBook b : lib) {
            byKey.put(b.key, b);
        }
        final Summary s = summary(d, from, to);
        final Map<String, Long> genre = new HashMap<>();
        final Map<String, Long> author = new HashMap<>();
        final Map<String, Long> language = new HashMap<>();
        final Map<String, Long> length = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : s.bookMs.entrySet()) {
            final LibBook l = byKey.get(e.getKey());
            final StatsData.Book b = d.books.get(e.getKey());
            add(genre, l == null ? null : l.genre, e.getValue());
            add(author, l != null && notEmpty(l.author) ? l.author : b == null ? null : b.author, e.getValue());
            add(language, l != null && notEmpty(l.lang) ? l.lang : b == null ? null : b.lang, e.getValue());
            final int pages = l != null && l.pages > 0 ? l.pages : b == null ? 0 : b.pages;
            add(length, lengthBand(pages), e.getValue());
        }
        final Library out = new Library();
        out.byGenre = sorted(genre);
        out.byAuthor = sorted(author);
        out.byLanguage = sorted(language);
        out.byLength = sorted(length);
        long finishedPages = 0;
        int finishedCount = 0;
        long stoppedPages = 0;
        int stoppedCount = 0;
        for (LibBook l : lib) {
            final StatsData.Book b = d.books.get(l.key);
            if ((b == null || !isStarted(b)) && l.progress <= 0) {
                out.backlog++;
            }
            final int added = StatsData.dayOf(l.added, tzMinutes);
            if (l.added > 0 && added >= from && added <= to) {
                out.acquired++;
            }
            final int pages = l.pages > 0 ? l.pages : b == null ? 0 : b.pages;
            if (b != null && pages > 0) {
                if (b.finishes() > 0) {
                    finishedPages += pages;
                    finishedCount++;
                } else if (isStopped(b, now)) {
                    stoppedPages += pages;
                    stoppedCount++;
                }
            }
        }
        out.finished = s.finishes;
        out.finishedPages = finishedCount == 0 ? 0 : (int) (finishedPages / finishedCount);
        out.stoppedPages = stoppedCount == 0 ? 0 : (int) (stoppedPages / stoppedCount);
        return out;
    }

    static String lengthBand(int pages) {
        if (pages <= 0) {
            return "?";
        }
        if (pages < 100) {
            return "< 100";
        }
        if (pages < 300) {
            return "100-300";
        }
        if (pages < 600) {
            return "300-600";
        }
        return "600+";
    }

    private static void add(Map<String, Long> map, String key, long ms) {
        final String k = notEmpty(key) ? key.trim() : "?";
        map.put(k, map.getOrDefault(k, 0L) + ms);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static Map<String, Long> sorted(Map<String, Long> map) {
        final List<Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        final Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : entries) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public static int minutes(long ms) {
        return (int) (ms / 60_000L);
    }
}
