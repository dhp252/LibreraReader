package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FictionViewTest {

    /** The first page after a reader on page {@code page} of the book's chapter {@code chapter} (1..37). */
    private static int end(int chapter, int page) {
        return 4 + (chapter - 1) * 10 + page + 1;
    }

    private static List<String> names(List<FictionCast.Person> people) {
        final List<String> out = new ArrayList<>();
        for (FictionCast.Person p : people) {
            out.add(p.name);
        }
        return out;
    }

    @Test
    public void atTheStoredPlaceEverythingShows() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        final FictionWhere where = FictionWhere.of(c, end(14, 3), cast.mark);
        assertEquals(FictionWhere.AT, where.state);
        final FictionView v = FictionView.build(cast, c, where);
        assertEquals(0, v.hidden);
        assertEquals(3, v.visible.size());
        // people in no group first, then the group
        assertEquals(2, v.groups.size());
        assertEquals("", v.groups.get(0).name);
        assertEquals("Aouda", v.groups.get(0).cards.get(0).person.name);
        assertEquals("Rescued by Fogg", v.groups.get(0).cards.get(0).line);
        assertEquals("A", v.groups.get(0).cards.get(0).initials);
        assertEquals("Reform Club", v.groups.get(1).name);
        assertEquals("PF", v.groups.get(1).cards.get(0).initials);
        assertEquals(1, v.alsoMet.size());
        assertEquals("The Parsee guide", v.alsoMet.get(0).person.name);
        assertEquals(2, v.ties.size());
        assertEquals("Phileas Fogg", v.ties.get(0).a);
        assertEquals("the £20,000 bet", v.ties.get(0).label);
        assertEquals("Reform Club", v.ties.get(0).b);
        assertEquals("Aouda", v.ties.get(1).b);
    }

    @Test
    public void anEarlierPageOfTheSameChapterIsStillAt() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        assertEquals(FictionWhere.AT, FictionWhere.of(c, end(14, 0), cast.mark).state);
    }

    @Test
    public void aReaderWhoReadOnIsPastAndStillSeesEveryone() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        final FictionWhere later = FictionWhere.of(c, end(16, 2), cast.mark);
        assertEquals(FictionWhere.PAST, later.state);
        assertEquals(16, later.reader);
        assertEquals(14, later.stored);
        assertEquals(0, FictionView.build(cast, c, later).hidden);
        // one page on in the same chapter is past too
        assertEquals(FictionWhere.PAST, FictionWhere.of(c, end(14, 5), cast.mark).state);
    }

    @Test
    public void aReaderWhoWentBackDoesNotSeePeopleMetLater() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        // back in the book's chapter 5: Aouda (first met in list chapter 13) and the guide (12) are ahead
        final FictionWhere where = FictionWhere.of(c, end(5, 2), cast.mark);
        assertEquals(FictionWhere.BEFORE, where.state);
        final FictionView v = FictionView.build(cast, c, where);
        assertEquals(2, v.hidden);
        assertEquals(1, v.visible.size());
        assertEquals("Phileas Fogg", v.visible.get(0).name);
        assertTrue(v.alsoMet.isEmpty());
        // the role was written at chapter 14: the card says where he was first met instead
        assertEquals("First met in Ch. 1", v.groups.get(0).cards.get(0).line);
        assertEquals("", v.groups.get(0).name);
        // the bet began in list chapter 4 (the book's chapter 3): shown; the tie to Aouda is not
        assertEquals(1, v.ties.size());
        assertEquals("Reform Club", v.ties.get(0).b);
        // his story so far stops at the reader's chapter
        final List<FictionCast.Moment> moments = FictionView.moments(cast, c, where, cast.person("fogg"));
        assertEquals(1, moments.size());
        assertEquals("Makes the bet at the club.", moments.get(0).line);
        // a selected name of someone not met yet matches nobody
        assertEquals(null, FictionNames.match(v.visible, "Aouda"));
    }

    @Test
    public void inTheChapterAPersonIsFirstMetTheyShow() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        // list chapter 13 is the book's chapter 12
        final FictionView v = FictionView.build(cast, c, FictionWhere.of(c, end(12, 0), cast.mark));
        assertTrue(names(v.visible).contains("Aouda"));
        assertTrue(names(v.visible).contains("The Parsee guide"));
        final FictionView before = FictionView.build(cast, c, FictionWhere.of(c, end(11, 9), cast.mark));
        assertFalse(names(before.visible).contains("Aouda"));
        assertTrue(names(before.visible).contains("The Parsee guide"));
    }

    @Test
    public void firstMetIsTheBooksOwnChapterNumber() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = FictionCastTest.verneCast();
        assertEquals("Ch. 12", FictionView.firstMet(cast, c, cast.person("aouda")));
        assertEquals("Ch. 1", FictionView.firstMet(cast, c, cast.person("fogg")));
    }

    @Test
    public void inAnotherLayoutAPageOfRoundingIsNotReadingOn() {
        final FictionCast cast = FictionCastTest.verneCast();
        // the same book laid out in twice the pages: chapters of 20 pages after 8 front pages
        final java.util.List<com.foobnix.ai.BookChapters.Entry> toc = new java.util.ArrayList<>();
        for (int i = 1; i <= 37; i++) {
            toc.add(new com.foobnix.ai.BookChapters.Entry("CHAPTER " + FictionChaptersTest.roman(i)
                    + ". IN WHICH SOMETHING HAPPENS", 0, 8 + (i - 1) * 20));
        }
        final FictionChapters wide = FictionChapters.of(toc, 748);
        final int stored = cast.mark.endIn(748);
        assertEquals(2 * cast.mark.end, stored);
        assertEquals(FictionWhere.AT, FictionWhere.of(wide, stored + 1, cast.mark).state);
        assertEquals(FictionWhere.PAST, FictionWhere.of(wide, stored + 2, cast.mark).state);
        // the chapters are found by their keys, whatever the pages
        assertEquals(14, FictionWhere.of(wide, stored, cast.mark).stored);
        assertEquals(FictionWhere.BEFORE, FictionWhere.of(wide, 8 + 4 * 20 + 3, cast.mark).state);
    }

    @Test
    public void whenEveryoneIsMinorTheyAreTheCards() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = new FictionCast();
        cast.merge(FictionCast.parse("{\"people\": [{\"id\": \"a\", \"name\": \"Anna\", \"minor\": true, \"first\": 1},"
                + "{\"id\": \"b\", \"name\": \"Boris\", \"minor\": true, \"first\": 1}]}", c, 1, 3));
        cast.mark = FictionMark.at(c, 20);
        final FictionView v = FictionView.build(cast, c, FictionWhere.of(c, 20, cast.mark));
        assertTrue(v.alsoMet.isEmpty());
        assertEquals(2, v.groups.get(0).cards.size());
        assertEquals("First met in the opening pages", v.groups.get(0).cards.get(0).line);
    }
}
