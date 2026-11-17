package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FictionBlocksTest {

    /** 5 chapters of 4 pages. */
    private static FictionChapters five() {
        final List<BookChapters.Entry> toc = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            toc.add(new BookChapters.Entry("Chapter " + (i + 1), 0, i * 4));
        }
        return FictionChapters.of(toc, 20);
    }

    private static int[] lengths(int pages, int each) {
        final int[] l = new int[pages];
        Arrays.fill(l, each);
        return l;
    }

    private static String[] plain(int pages) {
        final String[] p = new String[pages];
        for (int i = 0; i < pages; i++) {
            p[i] = "text of page " + i + ".";
        }
        return p;
    }

    @Test
    public void aTextUnderTheCapIsOneBlockEndingAtTheReadersPage() {
        final FictionChapters c = five();
        // the reader is on page 9 (chapter 3, its second page)
        final FictionShield.Window w = FictionShield.window(0, 9, 20, 20);
        final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(c, w, lengths(20, 100), 5000);
        assertEquals(1, blocks.size());
        final FictionBlocks.Block b = blocks.get(0);
        assertEquals(0, b.from());
        assertEquals(10, b.to());
        assertEquals(3, b.slices.size());
        assertEquals(0, b.firstChapter());
        assertEquals(2, b.lastChapter());
        assertEquals(8, b.slices.get(2).from);
        assertEquals(10, b.slices.get(2).to);
    }

    @Test
    public void noBlockEverHoldsAPageAfterTheReaders() {
        final FictionChapters c = five();
        final String[] plain = plain(20);
        for (int page = 0; page < 20; page++) {
            for (int cap : new int[]{50, 150, 450, 100000}) {
                final FictionShield.Window w = FictionShield.window(0, page, 20, 20);
                int covered = 0;
                for (FictionBlocks.Block b : FictionBlocks.blocks(c, w, lengths(20, 100), cap)) {
                    assertTrue(b.to() <= page + 1);
                    final String text = FictionBlocks.text(c, w, b, plain);
                    for (int later = page + 1; later < 20; later++) {
                        assertFalse("page " + later + " sent to a reader on page " + page,
                                text.contains("text of page " + later + "."));
                    }
                    for (FictionBlocks.Slice s : b.slices) {
                        covered += s.to - s.from;
                    }
                }
                // and everything up to the page is sent, once
                assertEquals(page + 1, covered);
            }
        }
    }

    @Test
    public void textOutsideTheWindowIsNotTakenEvenWhenItIsThere() {
        final FictionChapters c = five();
        final FictionShield.Window whole = FictionShield.window(0, 19, 20, 20);
        final FictionBlocks.Block all = FictionBlocks.blocks(c, whole, lengths(20, 100), 100000).get(0);
        // a block made for the whole book, asked for with a reader on page 5
        final FictionShield.Window w = FictionShield.window(0, 5, 20, 20);
        final String text = FictionBlocks.text(c, w, all, plain(20));
        assertTrue(text.contains("text of page 5."));
        assertFalse(text.contains("text of page 6."));
    }

    @Test
    public void aLongTextGoesInBlocksInOrderAlongChapters() {
        final FictionChapters c = five();
        final FictionShield.Window w = FictionShield.window(0, 19, 20, 20);
        // a chapter is 400 characters: two chapters fit in 900
        final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(c, w, lengths(20, 100), 900);
        assertEquals(3, blocks.size());
        assertEquals(0, blocks.get(0).from());
        assertEquals(8, blocks.get(0).to());
        assertEquals(8, blocks.get(1).from());
        assertEquals(16, blocks.get(1).to());
        assertEquals(16, blocks.get(2).from());
        assertEquals(20, blocks.get(2).to());
    }

    @Test
    public void aChapterLongerThanTheCapIsCutBetweenPages() {
        final FictionChapters c = five();
        final FictionShield.Window w = FictionShield.window(0, 3, 20, 20);
        final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(c, w, lengths(20, 100), 250);
        assertEquals(2, blocks.size());
        assertEquals(2, blocks.get(0).to());
        assertFalse(blocks.get(0).slices.get(0).continued);
        assertTrue(blocks.get(1).slices.get(0).continued);
        assertTrue(FictionBlocks.text(c, w, blocks.get(1), plain(20)).contains("continued=\"yes\""));
    }

    @Test
    public void anUpdateSendsOnlyThePagesSinceTheStoredPlace() {
        final FictionChapters c = five();
        final FictionShield.Window w = FictionShield.window(6, 13, 20, 20);
        final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(c, w, lengths(20, 100), 100000);
        assertEquals(1, blocks.size());
        final String text = FictionBlocks.text(c, w, blocks.get(0), plain(20));
        assertFalse(text.contains("text of page 5."));
        assertTrue(text.contains("text of page 6."));
        assertTrue(text.contains("text of page 13."));
        assertFalse(text.contains("text of page 14."));
        assertTrue(blocks.get(0).slices.get(0).continued);
    }

    @Test
    public void chaptersWithoutTextAreLeftOutAndNoTextIsNoBlock() {
        final FictionChapters c = five();
        final FictionShield.Window w = FictionShield.window(0, 19, 20, 20);
        final int[] lengths = lengths(20, 100);
        for (int p = 4; p < 8; p++) {
            lengths[p] = 0;
        }
        final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(c, w, lengths, 100000);
        assertEquals(4, blocks.get(0).slices.size());
        assertTrue(FictionBlocks.blocks(c, w, new int[20], 100000).isEmpty());
        assertTrue(FictionBlocks.blocks(c, FictionShield.window(0, -1, 20, 20), lengths, 100000).isEmpty());
    }

    @Test
    public void theHeaderCarriesTheChaptersNumberAndTitle() {
        final FictionChapters c = five();
        final FictionShield.Window w = FictionShield.window(0, 5, 20, 20);
        final String text = FictionBlocks.text(c, w, FictionBlocks.blocks(c, w, lengths(20, 100), 100000).get(0), plain(20));
        assertTrue(text.startsWith("<chapter n=\"1\" title=\"Chapter 1\">\ntext of page 0."));
        assertTrue(text.contains("<chapter n=\"2\" title=\"Chapter 2\">"));
        assertFalse(text.contains("n=\"3\""));
    }
}
