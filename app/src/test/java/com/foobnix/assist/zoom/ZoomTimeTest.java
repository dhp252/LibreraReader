package com.foobnix.assist.zoom;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ZoomTimeTest {

    @Test
    public void minutesFollowTheReadersSpeed() {
        // 6 characters a word: 13,800 characters are 2,300 words, 10 minutes at 230 words a minute
        assertEquals(10, ZoomTime.minutes(13_800, 230));
        assertEquals(10, ZoomTime.minutes(13_800, 0));
        assertEquals(5, ZoomTime.minutes(13_800, 460));
        assertEquals(1, ZoomTime.minutes(10, 230));
        assertEquals(0, ZoomTime.minutes(0, 230));
        assertEquals(2, ZoomTime.wordMinutes(460, 230));
        assertEquals(1, ZoomTime.wordMinutes(5, 0));
        assertEquals(0, ZoomTime.wordMinutes(0, 230));
    }

    @Test
    public void durationsAreMinutesThenHoursToTheHalf() {
        assertEquals("1 min", ZoomTime.duration(0));
        assertEquals("45 min", ZoomTime.duration(45));
        assertEquals("1 h", ZoomTime.duration(60));
        assertEquals("1½ h", ZoomTime.duration(95));
        assertEquals("3 h", ZoomTime.duration(170));
    }

    @Test
    public void theStatusLineTimes() {
        assertEquals("about 15 min for this chapter", ZoomTime.full(15));
        assertEquals("", ZoomTime.full(0));
        assertEquals("about 6 min instead of 15", ZoomTime.lean(6, 15));
        assertEquals("about 45 min instead of 1½ h", ZoomTime.lean(45, 90));
        assertEquals("about 6 min", ZoomTime.lean(6, 6));
        assertEquals("about 6 min", ZoomTime.lean(6, 0));
        assertEquals("", ZoomTime.lean(0, 15));
        assertEquals("the whole chapter in about 1 min", ZoomTime.claims(0, false));
        assertEquals("this part of the chapter in about 2 min", ZoomTime.claims(2, true));
    }

    @Test
    public void aFoldsTimeComesFromItsWordsElseItsShare() {
        final ZoomData.Block fold = new ZoomData.Block();
        fold.fold = true;
        assertEquals(0, ZoomTime.foldMinutes(fold, 13_800, 230));
        fold.percent = 20;
        assertEquals(2, ZoomTime.foldMinutes(fold, 13_800, 230));
        fold.words = 690;
        assertEquals(3, ZoomTime.foldMinutes(fold, 13_800, 230));
    }
}
