package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.edition.EditionModel.Block;
import com.foobnix.assist.edition.EditionModel.Kind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EditionPagesTest {

    private static String text(int sentences) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            out.append(out.length() > 0 ? " " : "").append("Sentence number ").append(i).append(" of the reader's words goes here.");
        }
        return out.toString();
    }

    private static Block words(String text) {
        return new Block(Kind.WORDS, "In your words", "", text, "");
    }

    private static EditionModel model(EditionModel.Section... sections) {
        final EditionModel m = new EditionModel();
        m.sections.addAll(Arrays.asList(sections));
        return m;
    }

    private static EditionModel.Section section(EditionModel.Part part, EditionModel.Group... groups) {
        return new EditionModel.Section(part, part.name(), Arrays.asList(groups));
    }

    private static EditionModel.Group group(String title, Block... blocks) {
        return new EditionModel.Group(title, title, Arrays.asList(blocks));
    }

    private static String allText(EditionPages pages, Kind kind) {
        final StringBuilder out = new StringBuilder();
        for (EditionPages.Page p : pages.pages) {
            for (EditionPages.Chunk c : p.chunks) {
                for (Block b : c.blocks) {
                    if (b.kind == kind) {
                        out.append(out.length() > 0 ? " " : "").append(b.text);
                    }
                }
            }
        }
        return out.toString();
    }

    @Test
    public void littleMaterialGivesFewPages() {
        final EditionPages pages = EditionPages.of(model(section(EditionModel.Part.WORDS, group("One", words(text(2))),
                group("Two", words(text(2))))));
        assertEquals(1, pages.size());
        assertEquals(2, pages.pages.get(0).chunks.size());
        assertEquals("One", pages.pages.get(0).chunks.get(0).heading);
        assertFalse(pages.pages.get(0).chunks.get(0).continued);
        assertEquals(1, pages.pageCount(0));
        assertEquals(1, pages.firstPage(0));
    }

    @Test
    public void noSectionsNoPages() {
        assertEquals(0, EditionPages.of(new EditionModel()).size());
    }

    @Test
    public void everySectionStartsAPage() {
        final EditionPages pages = EditionPages.of(model(
                section(EditionModel.Part.WORDS, group("One", words("Short."))),
                section(EditionModel.Part.WRONG, group("One", new Block(Kind.WRONG, "Wrong", "", "You missed: x.", ""))),
                section(EditionModel.Part.OPEN, group("One", new Block(Kind.OPEN, "Still open", "", "Why?", "")))));
        assertEquals(3, pages.size());
        assertEquals(2, pages.firstPage(1));
        assertEquals(3, pages.firstPage(2));
        assertEquals(1, pages.pageCount(2));
        assertEquals(2, pages.firstPageOf(Kind.WRONG));
        assertEquals(0, pages.firstPageOf(Kind.TRIED));
        for (int i = 0; i < pages.size(); i++) {
            assertEquals(i + 1, pages.pages.get(i).number);
            assertEquals(i, pages.pages.get(i).section);
        }
    }

    @Test
    public void aLongPassageRunsOnAtASentenceEndAndLosesNothing() {
        final String long1 = text(60);
        final EditionPages pages = EditionPages.of(model(section(EditionModel.Part.WORDS, group("One", words(long1)))), 800);
        assertTrue(pages.size() >= 3);
        assertEquals(long1, allText(pages, Kind.WORDS));
        for (int i = 0; i < pages.size(); i++) {
            final EditionPages.Chunk chunk = pages.pages.get(i).chunks.get(0);
            assertEquals("One", chunk.heading);
            assertEquals(i > 0, chunk.continued);
            final String piece = chunk.blocks.get(0).text;
            assertTrue(piece, piece.endsWith("."));
            assertTrue(piece.length() <= 800);
        }
    }

    @Test
    public void paragraphsBreakBeforeSentences() {
        final String a = text(8);
        final String b = text(8);
        final int at = EditionPages.breakAt(a + "\n\n" + b, a.length() + 40);
        assertEquals(a.length() + 2, at);
        assertEquals(0, EditionPages.breakAt("short", 3));
    }

    @Test
    public void aHeadingNeverStandsAloneAtTheFoot() {
        // the first chapter nearly fills the page; the second starts the next with its heading
        final List<EditionModel.Group> groups = new ArrayList<>();
        groups.add(group("One", words(text(12))));
        groups.add(group("Two", new Block(Kind.TRIED, "Tried it", "", text(5), "")));
        final EditionPages pages = EditionPages.of(model(new EditionModel.Section(EditionModel.Part.WORDS, "w", groups)), 800);
        for (EditionPages.Page p : pages.pages) {
            for (EditionPages.Chunk c : p.chunks) {
                assertFalse(c.blocks.isEmpty());
            }
        }
        final EditionPages.Page last = pages.pages.get(pages.size() - 1);
        assertEquals("Two", last.chunks.get(last.chunks.size() - 1).heading);
    }

    @Test
    public void aBlockTooLongToCutStaysWhole() {
        final StringBuilder one = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            one.append("xxxxx");
        }
        final EditionPages pages = EditionPages.of(model(section(EditionModel.Part.WORDS,
                group("", words(one.toString())))), 500);
        assertEquals(1, pages.size());
        assertEquals("", pages.pages.get(0).chunks.get(0).heading);
        assertEquals(Collections.singletonList(one.toString()).get(0), pages.pages.get(0).chunks.get(0).blocks.get(0).text);
    }

    @Test
    public void readingTimeFromTheWords() {
        final EditionModel m = model(section(EditionModel.Part.WORDS, group("One", words(text(100)))));
        // 100 sentences of 9 words, and the heading
        assertEquals(901, m.wordCount());
        assertEquals(5, m.minutes());
        assertEquals(1, new EditionModel().minutes());
    }
}
