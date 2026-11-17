package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReadsSectionsTest {

    /** 300 ms a word: 200 words a minute. */
    private static final double PACE = 300;

    private static ReadsSignals.Page page(int seconds, int words) {
        final ReadsSignals.Page p = new ReadsSignals.Page();
        p.seconds = seconds;
        p.words = words;
        return p;
    }

    @Test
    public void subSectionsOfTheTableOfContentsAreTheSections() {
        final List<BookChapters.Entry> toc = Arrays.asList(
                new BookChapters.Entry("Chapter 1", 0, 10),
                new BookChapters.Entry("Opening claim", 1, 10),
                new BookChapters.Entry("The pin-maker", 1, 13),
                new BookChapters.Entry("Three causes", 1, 17),
                new BookChapters.Entry("Chapter 2", 0, 20));
        final List<ReadsSections.Section> s = ReadsSections.split(toc, 10, 20);
        assertEquals(3, s.size());
        assertEquals("Opening claim", s.get(0).title);
        assertEquals(0, s.get(0).from);
        assertEquals(3, s.get(0).to);
        assertEquals("The pin-maker", s.get(1).title);
        assertEquals(3, s.get(1).from);
        assertEquals(7, s.get(1).to);
        assertEquals(10, s.get(2).to);
    }

    @Test
    public void pagesBeforeTheFirstSubSectionAreAnUntitledSection() {
        final List<BookChapters.Entry> toc = Arrays.asList(
                new BookChapters.Entry("Chapter 1", 0, 10),
                new BookChapters.Entry("A", 1, 12),
                new BookChapters.Entry("B", 1, 15));
        final List<ReadsSections.Section> s = ReadsSections.split(toc, 10, 20);
        assertEquals(3, s.size());
        assertEquals("", s.get(0).title);
        assertEquals(2, s.get(0).to);
        assertEquals("A", s.get(1).title);
    }

    @Test
    public void subSectionsOnOnePageAreOneRow() {
        final List<BookChapters.Entry> toc = Arrays.asList(
                new BookChapters.Entry("Chapter 1", 0, 0),
                new BookChapters.Entry("A", 1, 2),
                new BookChapters.Entry("B", 1, 2),
                new BookChapters.Entry("C", 1, 5));
        final List<ReadsSections.Section> s = ReadsSections.split(toc, 0, 8);
        assertEquals(3, s.size());
        assertEquals("A / B", s.get(1).title);
        assertEquals(5, s.get(1).to);
    }

    @Test
    public void aChapterWithoutSubSectionsIsShownInBlocksOfPages() {
        final List<BookChapters.Entry> toc = Arrays.asList(
                new BookChapters.Entry("Chapter 1", 0, 0),
                new BookChapters.Entry("Only one", 1, 4),
                new BookChapters.Entry("Chapter 2", 0, 30));
        final List<ReadsSections.Section> s = ReadsSections.split(toc, 0, 30);
        assertEquals(8, s.size());
        assertEquals("", s.get(0).title);
        assertEquals(0, s.get(0).from);
        assertEquals(4, s.get(0).to);
        assertEquals(30, s.get(7).to);
        // every page is in exactly one block
        int next = 0;
        for (ReadsSections.Section x : s) {
            assertEquals(next, x.from);
            next = x.to;
        }
    }

    @Test
    public void aShortChapterHasOneBlockPerPage() {
        assertEquals(3, ReadsSections.split(null, 5, 8).size());
        assertTrue(ReadsSections.split(null, 5, 5).isEmpty());
    }

    @Test
    public void tooManySubSectionsFallBackToTheFirstLevelOrBlocks() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        toc.add(new BookChapters.Entry("Chapter", 0, 0));
        for (int i = 0; i < 4; i++) {
            toc.add(new BookChapters.Entry("S" + i, 1, i * 10));
            for (int j = 1; j < 6; j++) {
                toc.add(new BookChapters.Entry("S" + i + "." + j, 2, i * 10 + j));
            }
        }
        final List<ReadsSections.Section> s = ReadsSections.split(toc, 0, 40);
        assertEquals(4, s.size());
        assertEquals("S1", s.get(1).title);
        // and when one level alone is too many rows: blocks
        final List<BookChapters.Entry> flat = new ArrayList<>();
        flat.add(new BookChapters.Entry("Chapter", 0, 0));
        for (int i = 1; i < 30; i++) {
            flat.add(new BookChapters.Entry("S" + i, 1, i));
        }
        assertEquals(ReadsRules.SECTION_BLOCKS, ReadsSections.split(flat, 0, 40).size());
    }

    @Test
    public void aSectionAtThePaceIsSteadyAndAFastOneSkimmed() {
        // 200 words at 300 ms a word: 60 s expected
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(page(55, 200), PACE));
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(page(25, 200), PACE));
        assertEquals(ReadsSections.Rating.SKIMMED, ReadsSections.rating(page(20, 200), PACE));
    }

    @Test
    public void nothingIsSkimmedWithoutAPaceOrWords() {
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(page(2, 200), 0));
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(page(2, 0), PACE));
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(page(1, ReadsRules.MIN_WORDS - 1), PACE));
    }

    @Test
    public void aReReadOrALongPageIsStruggled() {
        final ReadsSignals.Page p = page(20, 200);
        p.rereads = 1;
        assertEquals(ReadsSections.Rating.STRUGGLED, ReadsSections.rating(p, PACE));
        final ReadsSignals.Page q = page(300, 200);
        q.longs = 1;
        assertEquals(ReadsSections.Rating.STRUGGLED, ReadsSections.rating(q, PACE));
    }

    @Test
    public void driftedNeedsADriftMarkAndHelpAloneIsNoStruggle() {
        final ReadsSignals.Page p = page(60, 200);
        p.helps = 1;
        p.offers = 1;
        assertEquals(ReadsSections.Rating.STEADY, ReadsSections.rating(p, PACE));
        p.drifts = 1;
        assertEquals(ReadsSections.Rating.DRIFTED, ReadsSections.rating(p, PACE));
        p.rereads = 2;
        assertEquals(ReadsSections.Rating.STRUGGLED, ReadsSections.rating(p, PACE));
    }

    @Test
    public void untimedPagesHaveNoRating() {
        assertEquals(ReadsSections.Rating.NONE, ReadsSections.rating(new ReadsSignals.Page(), PACE));
        final ReadsSignals.Page onlyOffer = new ReadsSignals.Page();
        onlyOffer.offers = 1;
        assertEquals(ReadsSections.Rating.NONE, ReadsSections.rating(onlyOffer, PACE));
        assertEquals(ReadsSections.Rating.NONE, ReadsSections.rating(null, PACE));
    }

    @Test
    public void sectionsAreRatedFromTheirOwnPages() {
        final ReadsSignals s = new ReadsSignals();
        s.pages = 6;
        s.at(0).add(page(60, 200));
        s.at(1).add(page(60, 200));
        s.at(2).add(page(10, 200));
        s.at(3).add(page(10, 200));
        s.at(4).add(page(200, 200));
        s.at(4).longs = 1;
        s.at(4).helps = 1;
        final List<ReadsSections.Section> sections = new ArrayList<>(Arrays.asList(
                new ReadsSections.Section("a", 0, 2), new ReadsSections.Section("b", 2, 4),
                new ReadsSections.Section("c", 4, 5), new ReadsSections.Section("d", 5, 6)));
        ReadsSections.rate(sections, s, PACE);
        assertEquals(ReadsSections.Rating.STEADY, sections.get(0).rating);
        assertEquals(120, sections.get(0).seconds);
        assertEquals(ReadsSections.Rating.SKIMMED, sections.get(1).rating);
        assertEquals(ReadsSections.Rating.STRUGGLED, sections.get(2).rating);
        assertEquals(1, sections.get(2).spots);
        assertEquals(ReadsSections.Rating.NONE, sections.get(3).rating);
        assertEquals(0, sections.get(0).spots);
        assertFalse(sections.get(3).seconds > 0);
    }
}
