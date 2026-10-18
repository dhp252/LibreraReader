package com.foobnix.study;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StudyTimeTest {

    private static StudyBook.Chapter chapter(String key, long chars, String label) {
        final StudyBook.Chapter c = new StudyBook.Chapter(key);
        c.chars = chars;
        c.label = label;
        return c;
    }

    @Test
    public void minutesFollowTheLabelAndTheSpeed() {
        // 6,000 characters = 1,000 words
        assertEquals(5, StudyTime.minutes(6_000, StudyBook.READ, 200));
        assertEquals(1, StudyTime.minutes(6_000, StudyBook.SKIM, 200));
        assertEquals(0, StudyTime.minutes(6_000, StudyBook.SKIP, 200));
        assertEquals(0, StudyTime.minutes(0, StudyBook.READ, 200));
        assertEquals("at least a minute", 1, StudyTime.minutes(60, StudyBook.READ, 200));
        // no speed known yet: the default
        assertEquals(Math.round(1000.0 / StudyRules.DEFAULT_WPM), StudyTime.minutes(6_000, StudyBook.READ, 0));
    }

    @Test
    public void aPlanCountsTheLabelsAndTheTimeSaved() {
        final StudyTime.Plan plan = StudyTime.plan(Arrays.asList(
                chapter("a", 60_000, StudyBook.READ),
                chapter("b", 60_000, StudyBook.SKIM),
                chapter("c", 60_000, StudyBook.SKIP)), 250);
        assertEquals(1, plan.read);
        assertEquals(1, plan.skim);
        assertEquals(1, plan.skip);
        assertEquals(40 + 10, plan.minutes);
        assertEquals(120, plan.fullMinutes);
    }
}
