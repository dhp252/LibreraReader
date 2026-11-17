package com.foobnix.assist.recap;

import com.foobnix.ai.BookChapters;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RecapLogicTest {

    private static final long DAY = RecapLogic.DAY_MS;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void bucketBySizeOfTimeAway() {
        assertNull(RecapLogic.bucket(0, NOW));
        assertNull(RecapLogic.bucket(NOW - DAY, NOW));
        assertNull(RecapLogic.bucket(NOW - 2 * DAY + 1, NOW));
        assertEquals(RecapLogic.SHORT, RecapLogic.bucket(NOW - 2 * DAY, NOW));
        assertEquals(RecapLogic.SHORT, RecapLogic.bucket(NOW - 6 * DAY - DAY / 2, NOW));
        assertEquals(RecapLogic.LONG, RecapLogic.bucket(NOW - 7 * DAY, NOW));
        assertEquals(RecapLogic.LONG, RecapLogic.bucket(NOW - 300 * DAY, NOW));
        // a clock ahead on another device: no recap
        assertNull(RecapLogic.bucket(NOW + 5 * DAY, NOW));
    }

    @Test
    public void defaultLengthGrowsWithTimeAway() {
        assertEquals(RecapLogic.LEVEL_30S, RecapLogic.level(7));
        assertEquals(RecapLogic.LEVEL_2M, RecapLogic.level(21));
        assertEquals(RecapLogic.LEVEL_5M, RecapLogic.level(90));
    }

    @Test
    public void awayInDaysWeeksMonths() {
        assertArrayEquals(new int[]{2, RecapLogic.UNIT_DAYS}, RecapLogic.away(2));
        assertArrayEquals(new int[]{3, RecapLogic.UNIT_WEEKS}, RecapLogic.away(21));
        assertArrayEquals(new int[]{4, RecapLogic.UNIT_MONTHS}, RecapLogic.away(125));
    }

    @Test
    public void localDay() {
        final long midnightUtc = 20000 * DAY;
        assertEquals(20000, RecapLogic.day(midnightUtc + 1000, 0));
        assertEquals(19999, RecapLogic.day(midnightUtc + 1000, -2 * 3600_000));
        assertEquals(20000, RecapLogic.day(midnightUtc - 1000, 2 * 3600_000));
    }

    @Test
    public void budgetWithinProviderLimit() {
        assertEquals(RecapLogic.SHORT_BUDGET, RecapLogic.budget(RecapLogic.SHORT, 600_000));
        assertEquals(RecapLogic.LONG_BUDGET, RecapLogic.budget(RecapLogic.LONG, 600_000));
        assertEquals(24_000, RecapLogic.budget(RecapLogic.LONG, 24_000));
        assertTrue(RecapLogic.tailBudget(RecapLogic.SHORT, 1000) > RecapLogic.tailBudget(RecapLogic.LONG, 1000));
    }

    private final List<BookChapters.Range> chapters = Arrays.asList(
            new BookChapters.Range(0, 2, "", BookChapters.FRONT),
            new BookChapters.Range(2, 10, "Chapter 1", "chapter 1#0"),
            new BookChapters.Range(10, 20, "Chapter 2", "chapter 2#0"),
            new BookChapters.Range(20, 30, "Chapter 3", "chapter 3#0"),
            new BookChapters.Range(30, 40, "Index", "index#0"));

    @Test
    public void openingsOnlyBeforeTheTailAndNoSideMatter() {
        final List<RecapLogic.Slice> s = RecapLogic.openings(chapters, 15, 3000);
        assertEquals(2, s.size());
        assertEquals(2, s.get(0).from);
        assertEquals(10, s.get(0).to);
        assertEquals(10, s.get(1).from);
        // never past the first page sent whole
        assertEquals(15, s.get(1).to);
        assertEquals(1500, s.get(0).maxChars);
    }

    @Test
    public void manyChaptersSpreadEvenlyKeepingTheLatest() {
        final BookChapters.Range[] many = new BookChapters.Range[50];
        for (int i = 0; i < many.length; i++) {
            many[i] = new BookChapters.Range(i * 4, i * 4 + 4, "C" + i, "c" + i);
        }
        final List<RecapLogic.Slice> s = RecapLogic.openings(Arrays.asList(many), 200, RecapLogic.MIN_OPENING * 5);
        assertEquals(5, s.size());
        assertEquals("C0", s.get(0).title);
        assertEquals("C49", s.get(4).title);
        assertTrue(s.get(0).maxChars >= RecapLogic.MIN_OPENING);
    }

    @Test
    public void noOpeningsWithoutBudget() {
        assertTrue(RecapLogic.openings(chapters, 15, 10).isEmpty());
        assertTrue(RecapLogic.openings(chapters, 2, 10_000).isEmpty());
    }

    @Test
    public void cutAtASentence() {
        assertEquals("Short.", RecapLogic.cut("Short.", 100));
        final String cut = RecapLogic.cut("One two three. Four five six seven eight nine.", 24);
        assertEquals("One two three.…", cut);
        assertTrue(RecapLogic.cut("aaaa bbbb cccc dddd eeee ffff", 16).endsWith("…"));
        assertTrue(RecapLogic.cut("aaaa bbbb cccc dddd eeee ffff", 16).length() <= 16);
    }

    @Test
    public void excerptsNeverPastThePage() {
        final String[] plain = new String[40];
        for (int p = 0; p < plain.length; p++) {
            plain[p] = "page" + p;
        }
        // openings read for pages 2 and 10 only; whole from 18; reader on page 22
        for (int p = 0; p < 18; p++) {
            if (p != 2 && p != 10) {
                plain[p] = null;
            }
        }
        final List<RecapLogic.Excerpt> e = RecapLogic.excerpts(chapters, plain, 18, 22);
        assertEquals(4, e.size());
        assertEquals("Chapter 1", e.get(0).chapter);
        assertFalse(e.get(0).whole);
        assertEquals("page2", e.get(0).text);
        assertEquals("page10", e.get(1).text);
        assertEquals("page18\n\npage19", e.get(2).text);
        assertTrue(e.get(2).whole);
        assertEquals("Chapter 3", e.get(3).chapter);
        assertEquals("page20\n\npage21", e.get(3).text);
        assertEquals(21, e.get(3).firstPage);
        assertEquals(22, e.get(3).lastPage);
        for (RecapLogic.Excerpt x : e) {
            assertFalse(x.text.contains("page22"));
            assertFalse(x.text.contains("page23"));
        }
    }

    @Test
    public void markNearThePageAndNeverAfter() {
        final List<RecapLogic.MarkInfo> marks = Arrays.asList(
                new RecapLogic.MarkInfo(5, 500, "old but newest", ""),
                new RecapLogic.MarkInfo(90, 100, "near", ""),
                new RecapLogic.MarkInfo(95, 50, "near, older", ""),
                new RecapLogic.MarkInfo(120, 900, "ahead: a spoiler", ""),
                new RecapLogic.MarkInfo(99, 950, "", ""));
        assertEquals("near", RecapLogic.pickMark(marks, 100).quote);
        assertEquals("old but newest", RecapLogic.pickMark(marks, 50).quote);
        assertNull(RecapLogic.pickMark(marks, 2));
        assertNull(RecapLogic.pickMark(null, 2));
    }

    @Test
    public void savedRecapReusedSamePageSameDay() throws Exception {
        final JSONObject saved = new JSONObject().put(RecapLogic.PAGE, 41).put(RecapLogic.PAGES, 300)
                .put(RecapLogic.DAY, 20000L).put(RecapLogic.RECAP, new JSONObject().put("last_time", "x"));
        assertTrue(RecapLogic.reusable(saved, 41, 300, 20000));
        assertFalse(RecapLogic.reusable(saved, 42, 300, 20000));
        assertFalse(RecapLogic.reusable(saved, 41, 310, 20000));
        assertFalse(RecapLogic.reusable(saved, 41, 300, 20001));
        assertFalse(RecapLogic.reusable(null, 41, 300, 20000));
        assertEquals("recap:short", RecapLogic.key(RecapLogic.SHORT));
    }

    @Test
    public void shortChapterForTheContinueButton() {
        assertEquals("Chapter XIV", RecapLogic.shortChapter("Chapter XIV. In which Phileas Fogg descends"));
        assertEquals("Ch. 14", RecapLogic.shortChapter("Ch. 14"));
        assertEquals("Book I", RecapLogic.shortChapter("Book I: Of the Division of Labour"));
        assertEquals("A very long chapt…", RecapLogic.shortChapter("A very long chapter title indeed"));
        assertEquals("", RecapLogic.shortChapter(null));
    }
}
