package com.foobnix.assist.debate;

import com.foobnix.ai.AiCategory;
import com.foobnix.assist.xray.XrayIdea;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DebateQuotesTest {

    private static final String STUPID = "generally becomes as stupid and ignorant as it is possible for a human creature to become";

    /** Finds only the quotes it was given; the page is the quote's place in the list. */
    private static DebateQuotes.Finder book(final String... quotes) {
        return quote -> {
            for (int i = 0; i < quotes.length; i++) {
                if (quotes[i].equalsIgnoreCase(quote.replaceAll("[.,]$", ""))) {
                    return new DebateQuotes.Found(quotes[i], 40 + i);
                }
            }
            return null;
        };
    }

    @Test
    public void aQuoteFoundInTheBookKeepsItsMarksAndGivesTheSource() throws Exception {
        final DebateQuotes.Checked c = DebateQuotes.check("There I agree with you. Such a man “" + STUPID
                + ".” My remedy was a little school in every parish.", "", book(STUPID));
        assertEquals(3, c.parts.size());
        assertFalse(c.parts.get(0).quoted);
        assertTrue(c.parts.get(1).quoted);
        assertEquals(STUPID, c.parts.get(1).text);
        assertEquals(" My remedy was a little school in every parish.", c.parts.get(2).text);
        assertEquals(STUPID, c.quote);
        assertEquals(40, c.page);
        assertEquals("There I agree with you. Such a man “" + STUPID + "” My remedy was a little school in every parish.",
                c.plain());
    }

    @Test
    public void aQuoteNotInTheBookLosesItsMarksAndHasNoSource() throws Exception {
        final DebateQuotes.Checked c = DebateQuotes.check("I wrote that \"the pin maker is the happiest of men\" and I meant it.",
                "the pin maker is the happiest of men", book(STUPID));
        assertEquals(1, c.parts.size());
        assertFalse(c.parts.get(0).quoted);
        assertEquals("I wrote that the pin maker is the happiest of men and I meant it.", c.parts.get(0).text);
        assertNull(c.quote);
        assertEquals(-1, c.page);
        assertFalse(c.plain().contains("“"));
        assertFalse(c.plain().contains("\""));
    }

    @Test
    public void everyQuotedSpanIsCheckedOnItsOwn() throws Exception {
        final DebateQuotes.Checked c = DebateQuotes.check("First «an invented line that is not there», then „" + STUPID + "“.",
                "", book(STUPID));
        assertEquals("First an invented line that is not there, then “" + STUPID + "”.", c.plain());
        assertEquals(STUPID, c.quote);
    }

    @Test
    public void aQuoteNamedBesideTheReplyIsCheckedWhereTheReplyHasItWithoutMarks() throws Exception {
        final DebateQuotes.Checked found = DebateQuotes.check("Such a man " + STUPID + ", I said.", STUPID, book(STUPID));
        assertEquals("Such a man “" + STUPID + "”, I said.", found.plain());
        assertEquals(STUPID, found.quote);

        // named, but not in the reply: nothing to show, so nothing is claimed
        final DebateQuotes.Checked absent = DebateQuotes.check("I only paraphrase here.", STUPID, book(STUPID));
        assertNull(absent.quote);
        assertEquals("I only paraphrase here.", absent.plain());

        // named with its own marks, and already quoted in the reply: checked once
        final DebateQuotes.Checked both = DebateQuotes.check("He “" + STUPID + "”.", "\"" + STUPID + "\"", book(STUPID));
        assertEquals("He “" + STUPID + "”.", both.plain());
    }

    @Test
    public void strayMarksAreDroppedAndNoFinderMeansNoQuotes() throws Exception {
        assertEquals("An open mark and nothing after", DebateQuotes.check("An “open mark and nothing after", "", book()).plain());
        assertEquals("A closing” mark alone".replace("”", ""), DebateQuotes.check("A closing” mark alone", "", book()).plain());
        final DebateQuotes.Checked c = DebateQuotes.check("He “" + STUPID + "”.", "", null);
        assertNull(c.quote);
        assertEquals("He " + STUPID + ".", c.plain());
        assertEquals("", DebateQuotes.check(null, null, book()).plain());
    }

    @Test
    public void theBooksOwnWordsAreShownNotTheModels() throws Exception {
        // the finder answers with the book's spelling
        final DebateQuotes.Checked c = DebateQuotes.check("He “GENERALLY becomes as stupid”.", "",
                quote -> new DebateQuotes.Found("generally becomes as stupid", 7));
        assertEquals("He “generally becomes as stupid”.", c.plain());
        assertEquals(7, c.page);
    }

    @Test
    public void aQuoteAmongTheMarkedPassagesIsFoundWithoutTheBook() {
        final List<XrayIdea> ideas = Arrays.asList(
                new XrayIdea(1, "Division of labour multiplies output", AiCategory.MAIN, -1, true, 3,
                        Collections.singletonList("The greatest improvement in the productive powers of labour"), null, 3),
                new XrayIdea(2, "It dulls the worker", AiCategory.CAVEAT, 1, true, 9,
                        Arrays.asList("something else entirely here", "The man whose whole life is spent in performing a few "
                                + "simple operations " + STUPID + "."), "something else entirely here", 9));
        final DebateQuotes.Found f = DebateQuotes.inIdeas(ideas, "Generally becomes as stupid and ignorant as it is possible "
                + "for a human creature to become.");
        assertNotNull(f);
        assertEquals(9, f.page);
        assertEquals(STUPID + ".", f.text);
        assertNull(DebateQuotes.inIdeas(ideas, "a sentence the book never had in any of its marks"));
        // too short to be told from chance
        assertNull(DebateQuotes.inIdeas(ideas, "stupid"));
        assertNull(DebateQuotes.inIdeas(null, STUPID));
    }

    /** A book of pages of words; counts the pages read. */
    private static final class Book implements DebateQuotes.Pages {
        final List<String> pages = new ArrayList<>();
        final List<Integer> read = new ArrayList<>();

        Book(int count) {
            for (int i = 0; i < count; i++) {
                pages.add("page " + i + " holds only plain filler words number " + i + " and nothing of note");
            }
        }

        @Override
        public int pageCount() {
            return pages.size();
        }

        @Override
        public List<String> words(int page) {
            read.add(page);
            return Arrays.asList(pages.get(page).split(" "));
        }
    }

    @Test
    public void theChapterIsReadFirstThenThePagesAroundIt() throws Exception {
        final Book book = new Book(30);
        book.pages.set(12, "In the chapter: " + STUPID + " and so on");
        final DebateQuotes.Found in = DebateQuotes.scan(book, 10, 15, 400, STUPID);
        assertNotNull(in);
        assertEquals(12, in.page);
        assertEquals(STUPID, in.text);
        assertFalse(book.read.contains(20));
        assertFalse(book.read.contains(5));

        final Book other = new Book(30);
        other.pages.set(25, "Much later: " + STUPID + " and so on");
        final DebateQuotes.Found far = DebateQuotes.scan(other, 10, 15, 400, STUPID);
        assertNotNull(far);
        assertEquals(25, far.page);

        final Book before = new Book(30);
        before.pages.set(2, "Early on: " + STUPID + " and so on");
        assertEquals(2, DebateQuotes.scan(before, 10, 15, 400, STUPID).page);
    }

    @Test
    public void aQuoteRunningOverAPageEdgeIsFoundOnItsFirstPage() throws Exception {
        final Book book = new Book(8);
        book.pages.set(3, "the page ends with generally becomes as stupid and ignorant");
        book.pages.set(4, "as it is possible for a human creature to become and the next page goes on");
        final DebateQuotes.Found f = DebateQuotes.scan(book, 0, 8, 0, STUPID);
        assertNotNull(f);
        assertEquals(3, f.page);
        assertEquals(STUPID, f.text);
    }

    @Test
    public void theSearchStopsAtItsPageBudgetAndSkipsShortQuotes() throws Exception {
        final Book book = new Book(200);
        book.pages.set(150, "Far away: " + STUPID);
        assertNull(DebateQuotes.scan(book, 10, 15, 20, STUPID));
        assertFalse(book.read.contains(150));
        assertNotNull(DebateQuotes.scan(book, 10, 15, 400, STUPID));

        final Book untouched = new Book(10);
        assertNull(DebateQuotes.scan(untouched, 0, 10, 400, "too short"));
        assertTrue(untouched.read.isEmpty());
        assertNull(DebateQuotes.scan(new Book(0), 0, 0, 400, STUPID));
        // a chapter range outside the book is clamped
        assertNull(DebateQuotes.scan(new Book(3), 50, 60, 400, STUPID));
    }
}
