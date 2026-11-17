package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.ai.BookChapters;

import org.junit.Test;

public class FictionShieldTest {

    @Test
    public void theWindowEndsWithTheReadersPage() {
        // the reader shows page 13 (0-based): pages 0..13 may be read, page 14 may not
        assertEquals(14, FictionShield.end(13, 100, 100));
        final FictionShield.Window w = FictionShield.window(0, 13, 100, 100);
        assertEquals(0, w.from);
        assertEquals(14, w.end);
        assertTrue(w.holds(13));
        assertFalse(w.holds(14));
        assertFalse(w.holds(99));
    }

    @Test
    public void aPagePastTheBookOrAnUnknownPlaceReadsNoMoreThanTheBook() {
        assertEquals(100, FictionShield.end(250, 100, 100));
        assertEquals(0, FictionShield.end(-1, 100, 100));
        assertEquals(0, FictionShield.end(13, 0, 100));
        assertEquals(0, FictionShield.end(13, 100, 0));
        assertTrue(FictionShield.window(0, 13, 0, 100).isEmpty());
    }

    @Test
    public void anotherLayoutTakesTheSameShareRoundedDown() {
        // 14 of 100 pages read; the text has 150 pages: 21, never 22
        assertEquals(21, FictionShield.end(13, 100, 150));
        // 14 of 100 in 55 pages is 7.7: 7
        assertEquals(7, FictionShield.end(13, 100, 55));
        for (int pages = 1; pages < 400; pages++) {
            for (int page = 0; page < 100; page += 7) {
                final int end = FictionShield.end(page, 100, pages);
                assertTrue(end <= pages);
                // never a larger share of the book than the reader has read
                assertTrue(end / (double) pages <= (page + 1) / 100.0 + 1e-9);
            }
        }
    }

    @Test
    public void anUpdateStartsWhereTheStoredDataStopsAndIsEmptyWhenNothingIsNew() {
        final FictionShield.Window w = FictionShield.window(10, 13, 100, 100);
        assertEquals(10, w.from);
        assertEquals(14, w.end);
        assertTrue(FictionShield.window(14, 13, 100, 100).isEmpty());
        // stored data from past the reader's page (the reader went back): nothing, not a longer text
        final FictionShield.Window back = FictionShield.window(40, 13, 100, 100);
        assertTrue(back.isEmpty());
        assertEquals(14, back.end);
    }

    @Test
    public void theRestOfAWindowNeverPassesItsEnd() {
        final FictionShield.Window w = FictionShield.window(0, 13, 100, 100);
        assertEquals(8, FictionShield.rest(w, 8).from);
        assertEquals(14, FictionShield.rest(w, 8).end);
        assertTrue(FictionShield.rest(w, 30).isEmpty());
        assertEquals(14, FictionShield.rest(w, 30).end);
    }

    @Test
    public void aTaskCarriesTheReadersPlaceAsATicket() {
        final BookChapters.Range ticket = FictionShield.ticket(13, 100);
        assertEquals(14, FictionShield.window(0, ticket, 100).end);
        assertEquals(21, FictionShield.window(0, ticket, 150).end);
        assertEquals(5, FictionShield.window(5, ticket, 100).from);
        // anything that is not a ticket lets nothing through
        assertTrue(FictionShield.window(0, (BookChapters.Range) null, 100).isEmpty());
        assertTrue(FictionShield.window(0, new BookChapters.Range(0, 50, "Chapter I", "chapter i#0"), 100).isEmpty());
        assertTrue(FictionShield.window(0, new BookChapters.Range(0, 50, "", "@fiction:x"), 100).isEmpty());
    }

    @Test
    public void whereStoredDataStopsInTheSameAndInAnotherLayout() {
        assertEquals(14, FictionShield.storedEnd(14, 100, 0.14, 100));
        assertEquals(21, FictionShield.storedEnd(14, 100, 0.14, 150));
        assertEquals(7, FictionShield.storedEnd(14, 100, 0.14, 55));
        assertEquals(0, FictionShield.storedEnd(14, 100, 0.14, 0));
    }
}
