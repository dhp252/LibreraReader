package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.TimeZone;

public class EditionDatesTest {

    private static final String[] MONTHS = new EditionWords().months;

    @Test
    public void theRefresherIsSixWeeksOn() {
        assertEquals("2026-12-29", EditionDates.refresherAfter("2026-11-17"));
        assertEquals("2027-01-12", EditionDates.refresherAfter("2026-12-01"));
        assertEquals("", EditionDates.refresherAfter("soon"));
    }

    @Test
    public void howFarADayIs() {
        assertEquals(EditionDates.Unit.WEEKS, EditionDates.until("2026-11-17", "2026-12-29").unit);
        assertEquals(6, EditionDates.until("2026-11-17", "2026-12-29").n);
        assertEquals(2, EditionDates.until("2026-11-17", "2026-12-01").n);
        assertEquals(EditionDates.Unit.DAYS, EditionDates.until("2026-11-17", "2026-11-30").unit);
        assertEquals(13, EditionDates.until("2026-11-17", "2026-11-30").n);
        assertEquals(EditionDates.Unit.TOMORROW, EditionDates.until("2026-11-17", "2026-11-18").unit);
        assertEquals(EditionDates.Unit.NOW, EditionDates.until("2026-11-17", "2026-11-17").unit);
        assertEquals(EditionDates.Unit.NOW, EditionDates.until("2026-11-17", "2026-10-01").unit);
        assertEquals(EditionDates.Unit.NOW, EditionDates.until("2026-11-17", "").unit);
    }

    @Test
    public void daysAsWritten() {
        assertEquals("Oct 27", EditionDates.shortDay("2026-10-27", "2026-11-17", MONTHS));
        assertEquals("Oct 27, 2025", EditionDates.shortDay("2025-10-27", "2026-11-17", MONTHS));
        assertEquals("", EditionDates.shortDay("2026-13-01", "2026-11-17", MONTHS));
        assertEquals("", EditionDates.shortDay("2026-10-27", "2026-11-17", new String[3]));
        assertEquals("Oct 26 – Nov 9", EditionDates.range("2026-10-26", "2026-11-09", "2026-11-17", MONTHS));
        assertEquals("Oct 26", EditionDates.range("2026-10-26", "", "2026-11-17", MONTHS));
        assertEquals("Nov 9", EditionDates.range("", "2026-11-09", "2026-11-17", MONTHS));
        assertEquals("Nov 9", EditionDates.range("2026-11-09", "2026-11-09", "2026-11-17", MONTHS));
        assertEquals("", EditionDates.range("", "", "2026-11-17", MONTHS));
        assertEquals("", EditionDates.dayOf(0, TimeZone.getTimeZone("UTC")));
        assertEquals("1970-01-02", EditionDates.dayOf(86_400_000L, TimeZone.getTimeZone("UTC")));
    }

    @Test
    public void theFirstOpenSetsTheRefresherAndLaterOpensLeaveIt() throws Exception {
        final EditionState s = new EditionState();
        assertFalse(s.refresherDue("2026-11-17"));
        assertTrue(s.open("2026-11-17"));
        assertEquals("2026-11-17", s.opened);
        assertEquals("2026-12-29", s.next);
        assertFalse(s.open("2026-11-20"));
        assertEquals("2026-12-29", s.next);
        assertFalse(s.refresherDue("2026-12-28"));
        assertFalse(s.open("not a day"));
        // stored and read back
        final EditionState read = EditionState.fromJson(new JSONObject(s.toJson().toString()));
        assertEquals("2026-11-17", read.opened);
        assertEquals("2026-12-29", read.next);
        assertFalse(read.offered);
        assertFalse(read.raw);
    }

    @Test
    public void aDueRefresherShowsItsCardOnceAndReadingSetsTheNext() {
        final EditionState s = new EditionState();
        s.open("2026-11-17");
        assertTrue(s.refresherDue("2026-12-29"));
        assertTrue(s.refresherDue("2027-01-05"));
        s.refresherShown();
        assertFalse(s.refresherDue("2027-01-05"));
        // the reader opens the edition on or after the day: that was the refresher
        assertTrue(s.open("2027-01-05"));
        assertEquals("2026-11-17", s.opened);
        assertEquals("2027-02-16", s.next);
        assertFalse(s.refresherDue("2027-01-06"));
        assertTrue(s.refresherDue("2027-02-16"));
    }

    @Test
    public void finishedOnceAndTheOfferOwedUntilShown() {
        final EditionState s = new EditionState();
        assertFalse(s.offerOwed());
        assertFalse(s.finish("someday"));
        assertTrue(s.finish("2026-11-17"));
        assertFalse(s.finish("2026-11-18"));
        assertEquals("2026-11-17", s.finished);
        assertTrue(s.offerOwed());
        s.offered = true;
        assertFalse(s.offerOwed());
    }

    @Test
    public void aBrokenItemReadsAsNew() throws Exception {
        final EditionState s = EditionState.fromJson(new JSONObject().put("opened", "yesterday").put("next", 5)
                .put("raw", true));
        assertEquals("", s.opened);
        assertEquals("", s.next);
        assertTrue(s.raw);
        assertEquals("", EditionState.fromJson(null).finished);
    }
}
