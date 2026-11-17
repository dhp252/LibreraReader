package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.edition.EditionModel.Block;
import com.foobnix.assist.edition.EditionModel.Kind;
import com.foobnix.assist.edition.EditionPrint.Piece;
import com.foobnix.assist.edition.EditionPrint.Slice;
import com.foobnix.assist.edition.EditionPrint.Style;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditionPrintTest {

    private static Piece body(int lines) {
        return new Piece(lines, 15f, 0f, 4f, false, false);
    }

    private static Piece heading() {
        return new Piece(1, 17f, 16f, 4f, false, true);
    }

    private static int lines(List<Slice> page) {
        int n = 0;
        for (Slice s : page) {
            n += s.to - s.from;
        }
        return n;
    }

    @Test
    public void pageMeasures() {
        assertEquals(332f, EditionPrint.textWidth(), 0.001f);
        assertEquals(489f, EditionPrint.textHeight(), 0.001f);
        assertEquals(32, EditionPrint.linesPerPage(489f, 15f));
        assertEquals(2, EditionPrint.linesPerPage(30f, 15f));
        assertEquals(0, EditionPrint.linesPerPage(10f, 15f));
        assertEquals(0, EditionPrint.linesPerPage(10f, 0f));
    }

    @Test
    public void aLongParagraphFillsPagesLineByLine() {
        final List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(body(100)), 489f);
        assertEquals(4, pages.size());
        assertEquals(32, lines(pages.get(0)));
        assertEquals(32, lines(pages.get(1)));
        assertEquals(32, lines(pages.get(2)));
        assertEquals(4, lines(pages.get(3)));
        assertEquals(0f, pages.get(1).get(0).top, 0.001f);
        assertEquals(32, pages.get(1).get(0).from);
        assertEquals(64, pages.get(1).get(0).to);
    }

    @Test
    public void neverALoneLineOnEitherSideOfABreak() {
        // 33 lines: 32 fit, but one line alone would follow: the page ends two lines early
        List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(body(33)), 489f);
        assertEquals(2, pages.size());
        assertEquals(31, lines(pages.get(0)));
        assertEquals(2, lines(pages.get(1)));
        // room for one line only at the foot: the paragraph starts the next page
        pages = EditionPrint.layout(Arrays.asList(body(31), body(5)), 489f);
        assertEquals(2, pages.size());
        assertEquals(31, lines(pages.get(0)));
        assertEquals(5, lines(pages.get(1)));
        // a three-line paragraph with room for two moves whole
        pages = EditionPrint.layout(Arrays.asList(body(30), body(3)), 489f);
        assertEquals(30, lines(pages.get(0)));
        assertEquals(3, lines(pages.get(1)));
    }

    @Test
    public void spaceAboveIsDroppedAtTheTopOfAPageAndKeptElsewhere() {
        final List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(heading(), body(2), heading(), body(2)), 489f);
        assertEquals(1, pages.size());
        final List<Slice> page = pages.get(0);
        assertEquals(0f, page.get(0).top, 0.001f);
        assertEquals(21f, page.get(1).top, 0.001f);
        // 21 + 30 + 4 after, then 16 before the second heading
        assertEquals(71f, page.get(2).top, 0.001f);
        assertEquals(92f, page.get(3).top, 0.001f);
    }

    @Test
    public void aHeadingGoesWithTheParagraphItHeads() {
        // 30 lines, then a heading that would fit alone at the foot but not with two lines of its text
        final List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(body(30), heading(), body(10)), 489f);
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).size());
        assertEquals(1, pages.get(1).get(0).piece);
        assertEquals(0f, pages.get(1).get(0).top, 0.001f);
        assertEquals(2, pages.get(1).get(1).piece);
    }

    @Test
    public void aBreakBeforeStartsAPageButNotAnEmptyOne() {
        final Piece section = new Piece(1, 20f, 0f, 12f, true, true);
        final List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(section, body(3), section, body(3)), 489f);
        assertEquals(2, pages.size());
        assertEquals(0, pages.get(0).get(0).piece);
        assertEquals(2, pages.get(1).get(0).piece);
        // paragraphs without lines take no room
        assertEquals(1, EditionPrint.layout(Arrays.asList(body(0), body(2), body(0)), 489f).get(0).size());
        assertEquals(1, EditionPrint.layout(new ArrayList<Piece>(), 489f).size());
    }

    @Test
    public void aPageTooShortStillEnds() {
        final List<List<Slice>> pages = EditionPrint.layout(Arrays.asList(body(5)), 10f);
        assertEquals(5, pages.size());
        for (List<Slice> page : pages) {
            assertEquals(1, lines(page));
        }
    }

    @Test
    public void everyLineIsPlacedOnceAndInsideThePage() {
        final List<Piece> pieces = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < 40; i++) {
            pieces.add(heading());
            pieces.add(body(1 + (i * 7) % 23));
            total += 1 + 1 + (i * 7) % 23;
        }
        final List<List<Slice>> pages = EditionPrint.layout(pieces, 489f);
        int placed = 0;
        for (List<Slice> page : pages) {
            assertFalse(page.isEmpty());
            for (Slice s : page) {
                final Piece p = pieces.get(s.piece);
                assertTrue(s.top >= 0);
                assertTrue(s.top + (s.to - s.from) * p.lineHeight <= 489.01f);
                placed += s.to - s.from;
            }
        }
        assertEquals(total, placed);
    }

    @Test
    public void theRunsFollowTheSectionsChaptersAndBlocks() {
        final EditionModel m = new EditionModel();
        m.bookTitle = "The Wealth of Nations";
        m.sections.add(new EditionModel.Section(EditionModel.Part.WORDS, "In your words", Arrays.asList(
                new EditionModel.Group("ch1", "Of the Division of Labour", Arrays.asList(
                        new Block(Kind.WORDS, "In your words", "", "My words.", ""),
                        new Block(Kind.TRIED, "Tried it · Oct 26 – Nov 9", "", "Week-long roles.", ""))))));
        m.sections.add(new EditionModel.Section(EditionModel.Part.CONNECTIONS, "Connections", Arrays.asList(
                new EditionModel.Group("ch1", "Of the Division of Labour", Arrays.asList(
                        new Block(Kind.LINK, "Connections", "Disagrees · Alienation · Capital", "Marx.", ""),
                        new Block(Kind.LINK, "Connections", "Same idea · Residue · Deep Work", "", ""),
                        new Block(Kind.NOTE, "The book, updated", "Babbage", "Body.", "How settled: debated"))))));
        final List<EditionPrint.Run> runs = EditionPrint.runs(m);
        final StringBuilder out = new StringBuilder();
        for (EditionPrint.Run r : runs) {
            out.append(r.style).append(r.breakBefore ? "*" : "").append(r.keepWithNext ? "+" : "").append(r.bar ? "|" : "")
                    .append(':').append(r.text).append('\n');
        }
        assertEquals("SECTION*+:In your words\n"
                + "CHAPTER+:Of the Division of Labour\n"
                + "LABEL+:IN YOUR WORDS\n"
                + "BODY:My words.\n"
                + "LABEL+:TRIED IT · OCT 26 – NOV 9\n"
                + "PLAIN|:Week-long roles.\n"
                + "SECTION*+:Connections\n"
                + "CHAPTER+:Of the Division of Labour\n"
                + "LABEL+:CONNECTIONS\n"
                + "TITLE+:Disagrees · Alienation · Capital\n"
                + "PLAIN:Marx.\n"
                + "TITLE:Same idea · Residue · Deep Work\n"
                + "LABEL+:THE BOOK, UPDATED\n"
                + "TITLE+:Babbage\n"
                + "PLAIN:Body.\n"
                + "SMALL:How settled: debated\n", out.toString());
        assertEquals(EditionPrint.Tone.ACCENT, runs.get(5).tone);
        assertEquals(322f, runs.get(5).width(332f), 0.001f);
        assertEquals(Style.BODY.lineHeight, Piece.of(runs.get(3), 4).lineHeight, 0.001f);
        assertEquals("THE WEALTH OF NATIONS · YOUR EDITION", EditionPrint.head(m, new EditionWords()));
    }

    @Test
    public void fileName() {
        assertEquals("The Wealth of Nations - Your edition.pdf",
                EditionPrint.fileName("The Wealth of Nations", "Your edition"));
        assertEquals("A_B_ C_ - Your edition.pdf", EditionPrint.fileName("A/B: C?", "Your edition"));
        assertEquals("Your edition.pdf", EditionPrint.fileName("", "Your edition"));
        assertEquals("Your edition.pdf", EditionPrint.fileName(null, "Your edition"));
    }
}
