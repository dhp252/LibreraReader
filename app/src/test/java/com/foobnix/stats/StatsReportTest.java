package com.foobnix.stats;

import static com.foobnix.stats.StatsEngineTest.MIN;
import static com.foobnix.stats.StatsEngineTest.T0;
import static com.foobnix.stats.StatsEngineTest.open;
import static com.foobnix.stats.StatsEngineTest.page;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Periods, streaks, goals, predictions and text counts of F-007. */
public class StatsReportTest {

    /** One minute-long page of 250 words read on each of these days, at 08:00 UTC. */
    private static StatsData readOn(int... days) {
        final StatsEngine engine = new StatsEngine();
        for (int day : days) {
            final long ts = day * 86_400_000L + 8 * 3_600_000L;
            engine.feed("a", open(ts, "b.epub"));
            engine.feed("a", page(ts + 2 * MIN, "b.epub", 10, 250, 2 * MIN));
        }
        return engine.merged(null);
    }

    @Test
    public void weekStartsOnMonday() {
        final int wednesday = Days.of(2026, 10, 14);
        assertEquals(2, StatsData.weekdayOf(wednesday));
        assertArrayEquals(new int[]{Days.of(2026, 10, 12), Days.of(2026, 10, 18)},
                StatsReport.Period.WEEK.range(wednesday, new StatsData()));
        assertArrayEquals(new int[]{Days.of(2026, 10, 1), Days.of(2026, 10, 31)},
                StatsReport.Period.MONTH.range(wednesday, new StatsData()));
        assertArrayEquals(new int[]{Days.of(2026, 9, 1), Days.of(2026, 9, 30)},
                StatsReport.Period.MONTH.previous(wednesday, new StatsData()));
        assertEquals("2026-10", Days.monthKey(wednesday));
    }

    @Test
    public void streakCountsUpToYesterdayWhenTodayIsNotRead() {
        final int today = Days.of(2026, 10, 15);
        final StatsData d = readOn(today - 5, today - 3, today - 2, today - 1);
        final StatsReport.Streak s = StatsReport.streak(d, today);
        assertEquals(3, s.current);
        assertEquals(3, s.longest);
        assertTrue(s.monthDays[14]);
        assertEquals(4, s.daysThisMonth);
    }

    @Test
    public void goalBalanceOverTheWeek() {
        final int monday = Days.of(2026, 10, 12);
        final StatsData d = readOn(monday, monday + 1);
        // 2 minutes on each of 2 days against 1 minute a day over 3 days: 1 ahead
        final StatsReport.Goal g = StatsReport.goal(d, monday + 2, 1);
        assertEquals(1, g.weekBalance);
        assertEquals(0, g.todayMinutes);
        assertNull(StatsReport.goal(d, monday + 2, 0));
    }

    @Test
    public void seriesOfAWeekHasSevenBars() {
        final int monday = Days.of(2026, 10, 12);
        final StatsData d = readOn(monday + 1);
        final StatsReport.Series s = StatsReport.series(d, StatsReport.Period.WEEK, monday + 3);
        assertEquals(7, s.ms.size());
        assertEquals(Long.valueOf(2 * MIN), s.ms.get(1));
        assertEquals(12, StatsReport.series(d, StatsReport.Period.YEAR, monday).ms.size());
    }

    @Test
    public void speedNeedsEnoughReading() {
        final int day = Days.of(2026, 10, 12);
        final int[] days = new int[6];
        for (int i = 0; i < days.length; i++) {
            days[i] = day + i;
        }
        // 6 pages of 2 minutes: 12 minutes, under both thresholds
        assertEquals(0, StatsReport.speedFor(readOn(days), "b.epub"));
        final int[] more = new int[12];
        for (int i = 0; i < more.length; i++) {
            more[i] = day + i;
        }
        // 24 minutes: the book's own speed, 125 wpm
        assertEquals(125, StatsReport.speedFor(readOn(more), "b.epub"), StatsRules.WPM_BUCKET);
        assertEquals(10, StatsReport.minutesFor(1250, 125));
        assertEquals(-1, StatsReport.minutesFor(1250, 0));
    }

    @Test
    public void finishDayAtThePaceOfTheLastTwoWeeks() {
        final int today = Days.of(2026, 10, 15);
        final StatsData d = readOn(today - 1, today);
        final StatsData.Book b = d.books.get("b.epub");
        // 250 words a reading day: 1000 words are 4 days
        assertEquals(today + 4, StatsReport.finishDay(b, 1000, today));
        assertEquals(-1, StatsReport.finishDay(b, 1000, today + 30));
    }

    @Test
    public void projectedBooks() {
        final StatsEngine engine = new StatsEngine();
        engine.feed("a", open(T0, "b.epub"));
        engine.feed("a", page(T0 + MIN, "b.epub", 100, 250, MIN));
        final int today = StatsData.dayOf(T0, 0);
        final int left = Days.yearEnd(today) - today;
        assertEquals(1 + (int) Math.floor(left / 90.0), StatsReport.projectedBooksThisYear(engine.merged(null), today));
    }

    @Test
    public void libraryBreakdowns() {
        final int today = StatsData.dayOf(T0, 0);
        final StatsData d = readOn(today);
        final StatsReport.Library l = StatsReport.library(d, Arrays.asList(
                new StatsReport.LibBook("b.epub", "Ann", "Essays", "en", 250, T0, 0.1f),
                new StatsReport.LibBook("c.epub", "Bob", "Novel", "en", 700, T0 - 400L * 86_400_000L, 0)),
                today, today, T0, 0);
        assertEquals(Long.valueOf(2 * MIN), l.byGenre.get("Essays"));
        assertEquals(Long.valueOf(2 * MIN), l.byLength.get("100-300"));
        assertEquals(1, l.backlog);
        assertEquals(1, l.acquired);
        assertEquals(Collections.singleton("Ann"), l.byAuthor.keySet());
    }

    @Test
    public void textCounts() {
        final TextCounts c = new TextCounts().add(Arrays.asList("The", "cat", "sat.", "你好", "—"));
        assertEquals(5, c.words);
        assertEquals(1, c.sentences);
        assertTrue(Double.isNaN(c.flesch()));
        assertEquals(1, TextCounts.syllables("cat"));
        assertEquals(2, TextCounts.syllables("reading"));
        assertEquals(3, TextCounts.syllables("example"));
        assertEquals(2, TextCounts.syllables("table"));
        final TextCounts easy = new TextCounts();
        for (int i = 0; i < 40; i++) {
            easy.add(Arrays.asList("The", "cat", "sat."));
        }
        assertTrue(easy.flesch() > 90);
    }
}
