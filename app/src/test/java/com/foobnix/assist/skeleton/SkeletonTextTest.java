package com.foobnix.assist.skeleton;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SkeletonTextTest {

    @Test
    public void totalsLikeTheCanvas() {
        // 27½ h = 1650 minutes
        assertEquals(Arrays.asList("5 books", "32 chapters", "about 27½ h in full"),
                SkeletonText.totals(5, "book", 32, 1650));
        assertEquals(Arrays.asList("3 parts", "1 chapter"), SkeletonText.totals(3, null, 1, 0));
        assertEquals("no parts: no piece", Arrays.asList("12 chapters", "about 7 h in full"),
                SkeletonText.totals(0, null, 12, 420));
        assertEquals(Collections.singletonList("1 book"), SkeletonText.totals(1, "book", 0, 0));
    }

    @Test
    public void durationToTheHalfHour() {
        assertEquals("45 min", SkeletonText.duration(45));
        assertEquals("1 min", SkeletonText.duration(0));
        assertEquals("1 h", SkeletonText.duration(60));
        assertEquals("1½ h", SkeletonText.duration(95));
        assertEquals("2 h", SkeletonText.duration(110));
        assertEquals("27½ h", SkeletonText.duration(1650));
    }

    @Test
    public void partNounOnlyWhenEveryTitleIsNumbered() {
        assertEquals("book", SkeletonText.partNoun(Arrays.asList("Book I", "Book II: Of Stock", "BOOK III")));
        assertEquals("part", SkeletonText.partNoun(Arrays.asList("Part One: Rise", "Part Two - Fall")));
        assertNull(SkeletonText.partNoun(Arrays.asList("Book I", "Part II")));
        assertNull(SkeletonText.partNoun(Arrays.asList("Book I", "Afterword")));
        assertNull("a word, not a number", SkeletonText.partNoun(Collections.singletonList("Where I Stood")));
    }

    @Test
    public void plurals() {
        assertEquals("books", SkeletonText.plural("book"));
        assertEquals("classes", SkeletonText.plural("class"));
        assertEquals("studies", SkeletonText.plural("study"));
        assertEquals("days", SkeletonText.plural("day"));
    }

    @Test
    public void shortLabelAndTheRest() {
        assertEquals("Book I", SkeletonText.shortLabel("Book I: Of the Causes of Improvement"));
        assertEquals("Of the Causes of Improvement", SkeletonText.afterLabel("Book I: Of the Causes of Improvement"));
        assertEquals("Part 3", SkeletonText.shortLabel("Part 3"));
        assertEquals("", SkeletonText.afterLabel("Part 3"));
        assertEquals("Chapter Two", SkeletonText.shortLabel("Chapter Two. The Market"));
        assertEquals("unnumbered: the whole title", "Where I Stood", SkeletonText.shortLabel("Where I  Stood"));
        assertEquals("", SkeletonText.afterLabel("Where I Stood"));
    }

    @Test
    public void firstSentenceOfAThesis() {
        assertEquals("A nation's wealth is what its people produce.",
                SkeletonText.firstSentence("A nation's wealth is what its people produce. Free exchange makes it grow."));
        assertEquals("one sentence stays whole", "Wealth grows by trade", SkeletonText.firstSentence(" Wealth grows by trade "));
        assertEquals("Dr. Smith argues that trade helps.",
                SkeletonText.firstSentence("Dr. Smith argues that trade helps. Then more."));
        assertEquals("", SkeletonText.firstSentence(null));
    }
}
