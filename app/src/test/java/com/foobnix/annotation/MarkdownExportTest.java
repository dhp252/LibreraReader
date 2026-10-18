package com.foobnix.annotation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarkdownExportTest {

    private static final String YELLOW = ColorNames.EMOJI[0];
    private static final String RED = ColorNames.EMOJI[1];
    private static final String BLUE = ColorNames.EMOJI[3];

    @Test
    public void writesTheAgreedLayout() {
        MarkdownExport.Book book = new MarkdownExport.Book("Thinking, Fast and Slow", "Daniel Kahneman", "tfs.epub",
                "2026-10-18", Arrays.asList(new MarkdownExport.Chapter("Part I. Two Systems", 20),
                new MarkdownExport.Chapter("Part II", 100)));
        String md = MarkdownExport.build(book,
                Arrays.asList(
                        new MarkdownExport.Item(35, 0.1f, "The law of least effort.", "", "#2ea8e5", "underline"),
                        new MarkdownExport.Item(21, 0.3f, "System 1 operates automatically and quickly, with little or no effort.",
                                "compare with dual-process theory in ch. 3.", "#ffd400", "highlight"),
                        new MarkdownExport.Item(120, 0.2f, "", "a sticky note", "#ffff00", "note"),
                        new MarkdownExport.Item(5, 0.5f, "Intro quote", "", "#ff6666", "strikethrough")),
                Collections.singletonList(new MarkdownExport.Bookmark(12, "Remember this")),
                Collections.singletonList(new MarkdownExport.Passage("Part I", 22, "An AI quote.", RED + " Main idea")));

        assertEquals("---\n"
                + "title: \"Thinking, Fast and Slow\"\n"
                + "author: \"Daniel Kahneman\"\n"
                + "file: \"tfs.epub\"\n"
                + "exported: 2026-10-18\n"
                + "tags: [book, librera]\n"
                + "---\n"
                + "# Thinking, Fast and Slow\n"
                + "Daniel Kahneman\n"
                + "\n"
                + "> Intro quote\n"
                + "\n"
                + "p. 5 · " + RED + " red · strikethrough\n"
                + "\n"
                + "## Part I. Two Systems\n"
                + "\n"
                + "> System 1 operates automatically and quickly, with little or no effort.\n"
                + "\n"
                + "p. 21 · " + YELLOW + " yellow · highlight\n"
                + "\n"
                + "**Note:** compare with dual-process theory in ch. 3.\n"
                + "\n"
                + "> The law of least effort.\n"
                + "\n"
                + "p. 35 · " + BLUE + " blue · underline\n"
                + "\n"
                + "## Part II\n"
                + "\n"
                + "p. 120 · " + YELLOW + " yellow · note\n"
                + "\n"
                + "**Note:** a sticky note\n"
                + "\n"
                + "## Bookmarks\n"
                + "\n"
                + "- p. 12 · Remember this\n"
                + "\n"
                + "## AI key passages\n"
                + "\n"
                + "### Part I\n"
                + "\n"
                + "> An AI quote.\n"
                + "\n"
                + "p. 22 · " + RED + " Main idea\n", md);
    }

    @Test
    public void quotesOverSeveralLinesStayQuotedAndEmptySectionsAreLeftOut() {
        MarkdownExport.Book book = new MarkdownExport.Book("Say \"hi\"", "", "a.pdf", "2026-01-02",
                Collections.<MarkdownExport.Chapter>emptyList());
        String md = MarkdownExport.build(book,
                Collections.singletonList(new MarkdownExport.Item(3, 0, "first line\r\nsecond line", "", "#5fb236", "highlight")),
                Collections.<MarkdownExport.Bookmark>emptyList(), null);
        assertTrue(md.startsWith("---\ntitle: \"Say \\\"hi\\\"\"\nfile: \"a.pdf\"\n"));
        assertTrue(md.contains("\n> first line\n> second line\n"));
        assertFalse(md.contains("author:"));
        assertFalse(md.contains("## Bookmarks"));
        assertFalse(md.contains("## AI key passages"));
    }

    @Test
    public void aMarkFallsInTheLastChapterStartedBeforeIt() {
        java.util.List<MarkdownExport.Chapter> toc = Arrays.asList(new MarkdownExport.Chapter("Book", 1),
                new MarkdownExport.Chapter("One", 3), new MarkdownExport.Chapter("One.1", 3),
                new MarkdownExport.Chapter("Two", 9), new MarkdownExport.Chapter("Unknown", -1));
        assertEquals("One.1", MarkdownExport.chapterOf(toc, 4).title);
        assertEquals("Two", MarkdownExport.chapterOf(toc, 9).title);
        assertEquals("Book", MarkdownExport.chapterOf(toc, 2).title);
        assertEquals(null, MarkdownExport.chapterOf(toc.subList(1, 4), 2));
    }

    @Test
    public void aStudiedBookHasItsOutlineFirst() {
        MarkdownExport.Book book = new MarkdownExport.Book("T", "", "t.epub", "2026-10-18",
                Arrays.asList(new MarkdownExport.Chapter("One", 1)));
        String md = MarkdownExport.build(book, Arrays.asList(new MarkdownExport.Item(2, 0, "q", "", "#ffd400", "highlight")),
                null, null, "**Thesis:** x\n\n- **1. One**\n");
        assertTrue(md.contains("# T\n\n## Outline\n\n**Thesis:** x\n\n- **1. One**\n\n## One\n"));
        // no outline: no section
        assertFalse(MarkdownExport.build(book, null, null, null, " ").contains("## Outline"));
    }

    @Test
    public void nothingToExport() {
        assertTrue(MarkdownExport.isEmpty(null, Collections.<MarkdownExport.Bookmark>emptyList(), null));
        assertFalse(MarkdownExport.isEmpty(null, Collections.singletonList(new MarkdownExport.Bookmark(1, "")), null));
    }
}
