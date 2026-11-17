package com.foobnix.assist.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ReviewLinkTest {

    private static ReviewLink.Side side(String book, String title, int year, String chapter, String id, String idea) {
        final ReviewLink.Side s = new ReviewLink.Side();
        s.book = book;
        s.bookTitle = title;
        s.year = year;
        s.chapterKey = chapter;
        s.ideaId = id;
        s.idea = idea;
        return s;
    }

    private static ReviewLink link() {
        final ReviewLink l = new ReviewLink();
        l.a = side("wealth.epub", "The Wealth of Nations", 1776, "Chapter I#1", "3", "Time lost switching tasks");
        l.b = side("deep.epub", "Deep Work", 2016, "@pages:20", "line", "Attention residue");
        l.id = ReviewLink.id(l.a, l.b);
        l.relation = ReviewLink.SAME;
        l.why = "Both say switching costs attention.";
        l.day = "2026-11-07";
        return l;
    }

    @Test
    public void theStoredNamesAreTheContract() throws Exception {
        final ReviewLink l = link();
        l.sentence = "Switching is never free.";
        l.state = ReviewLink.ADDED;
        final JSONObject o = l.toJson();
        assertTrue(l.key().startsWith("link:"));
        final JSONObject a = o.getJSONObject("a");
        assertEquals("wealth.epub", a.getString("book"));
        assertEquals("The Wealth of Nations", a.getString("bookTitle"));
        assertEquals("Chapter I#1", a.getString("chapterKey"));
        assertEquals("3", a.getString("ideaId"));
        assertEquals("Time lost switching tasks", a.getString("idea"));
        assertEquals("deep.epub", o.getJSONObject("b").getString("book"));
        assertEquals("same", o.getString("relation"));
        assertEquals("Both say switching costs attention.", o.getString("why"));
        assertEquals("Switching is never free.", o.getString("sentence"));
        assertEquals("added", o.getString("state"));
    }

    @Test
    public void aLinkReadsBackAsItWasWritten() throws Exception {
        final ReviewLink l = link();
        l.also.add(new ReviewLink.Outside(ReviewLink.DISAGREES, "Marx, Capital: specialists end up crippled"));
        final ReviewLink back = ReviewLink.fromJson(l.key(), new JSONObject(l.toJson().toString()));
        assertNotNull(back);
        assertEquals(l.id, back.id);
        assertEquals("Chapter I#1", back.a.chapterKey);
        assertEquals("@pages:20", back.b.chapterKey);
        assertEquals("line", back.b.ideaId);
        assertEquals(1776, back.a.year);
        assertEquals(240, back.yearsApart());
        assertTrue(back.isNew());
        assertEquals(1, back.also.size());
        assertEquals(ReviewLink.DISAGREES, back.also.get(0).relation);
        assertEquals("2026-11-07", back.day);
    }

    @Test
    public void theIdIsTheSameFromEitherEnd() {
        final ReviewLink l = link();
        assertEquals(ReviewLink.id(l.a, l.b), ReviewLink.id(l.b, l.a));
        final ReviewLink.Side other = side("deep.epub", "Deep Work", 2016, "@pages:20", "4", "Another idea");
        assertFalse(ReviewLink.id(l.a, l.b).equals(ReviewLink.id(l.a, other)));
        assertFalse(ReviewLink.id(l.a, l.b).contains(":"));
    }

    @Test
    public void aRelationIsOneOfThreeOrNothing() {
        assertEquals(ReviewLink.SAME, ReviewLink.relation(" Same idea "));
        assertEquals(ReviewLink.DISAGREES, ReviewLink.relation("disagree"));
        assertEquals(ReviewLink.DISAGREES, ReviewLink.relation("Contradicts"));
        assertEquals(ReviewLink.EXTENDS, ReviewLink.relation("extends"));
        assertEquals(ReviewLink.EXTENDS, ReviewLink.relation("builds on"));
        assertNull(ReviewLink.relation("related"));
        assertNull(ReviewLink.relation(null));
    }

    @Test
    public void whatIsNotALinkIsNotRead() throws Exception {
        final JSONObject o = link().toJson();
        assertNull(ReviewLink.fromJson("review:x:1", o));
        assertNull(ReviewLink.fromJson("link:", o));
        assertNull(ReviewLink.fromJson("link:1", null));
        final JSONObject noB = new JSONObject(o.toString());
        noB.remove("b");
        assertNull(ReviewLink.fromJson("link:1", noB));
        final JSONObject odd = new JSONObject(o.toString()).put("relation", "maybe");
        assertNull(ReviewLink.fromJson("link:1", odd));
    }

    @Test
    public void anUnknownStateIsNewAndYearsApartNeedsBothYears() throws Exception {
        final JSONObject o = link().toJson().put("state", "seen");
        o.getJSONObject("b").put("year", 0);
        final ReviewLink l = ReviewLink.fromJson("link:1", o);
        assertTrue(l.isNew());
        assertEquals(0, l.yearsApart());
    }

    @Test
    public void outsideWorksAreAtMostTwoAndNeverTheSameIdea() throws Exception {
        final JSONArray also = new JSONArray()
                .put(new JSONObject().put("relation", "same").put("text", "x"))
                .put(new JSONObject().put("relation", "extends").put("text", "Babbage, 1832: pay each step"))
                .put(new JSONObject().put("relation", "disagrees").put("text", ""))
                .put(new JSONObject().put("relation", "disagrees").put("text", "Marx: crippled"))
                .put(new JSONObject().put("relation", "extends").put("text", "a third"));
        final ReviewLink l = ReviewLink.fromJson("link:1", link().toJson().put("also", also));
        assertEquals(2, l.also.size());
        assertEquals(ReviewLink.EXTENDS, l.also.get(0).relation);
        assertEquals("Marx: crippled", l.also.get(1).text);
    }
}
