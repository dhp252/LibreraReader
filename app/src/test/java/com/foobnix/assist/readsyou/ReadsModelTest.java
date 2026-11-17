package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ReadsModelTest {

    private static final int WORDS = 300;
    private long now = 1_000;

    /** Reads for {@code ms} with a tick every second. */
    private void read(ReadsModel m, long ms) {
        for (long t = 0; t < ms; t += 1000) {
            now += Math.min(1000, ms - t);
            m.tick(now, true);
        }
    }

    private void idle(ReadsModel m, long ms) {
        for (long t = 0; t < ms; t += 1000) {
            now += Math.min(1000, ms - t);
            m.tick(now, false);
        }
    }

    private void show(ReadsModel m, int page) {
        m.tick(now, true);
        m.page(now, page, page - 1, WORDS);
    }

    /** Pages {@code from..to} read in order, {@code ms} each; ends on page {@code to + 1}. */
    private void readPages(ReadsModel m, int from, int to, long ms) {
        for (int p = from; p <= to; p++) {
            show(m, p);
            read(m, ms);
        }
        show(m, to + 1);
    }

    private ReadsModel started() {
        final ReadsModel m = new ReadsModel(0);
        m.tick(now, true);
        return m;
    }

    @Test
    public void paceIsLearnedFromTheReadersOwnPages() {
        final ReadsModel m = started();
        assertEquals(ReadsModel.State.LEARNING, m.state(now));
        readPages(m, 1, 5, 75_000);
        assertTrue(m.paceLearned());
        assertEquals(250, m.pace(), 0.01);
        assertEquals(ReadsModel.State.STEADY, m.state(now));
        assertEquals(5 * 75_000, m.sessionMs());
    }

    @Test
    public void theStoredPaceIsUsedUntilThisRunHasItsOwn() {
        final ReadsModel m = new ReadsModel(200);
        m.tick(now, true);
        show(m, 1);
        assertEquals(200, m.pace(), 0);
        assertFalse(m.paceLearned());
        assertEquals(ReadsModel.State.STEADY, m.state(now));
    }

    @Test
    public void goingBackAndForwardAgainIsAReReadOfThatPage() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        // on page 6: back to 5, ten seconds there, forward again
        read(m, 20_000);
        show(m, 5);
        assertFalse(m.rereading(now));
        read(m, 10_000);
        assertTrue(m.rereading(now));
        assertEquals(ReadsModel.State.REREAD, m.state(now));
        show(m, 6);
        final List<ReadsModel.Visit> visits = m.drain();
        assertEquals(2, visits.size());
        assertEquals(6, visits.get(0).page);
        assertFalse(visits.get(0).reread);
        assertEquals(5, visits.get(1).page);
        assertEquals(4, visits.get(1).tag);
        assertTrue(visits.get(1).reread);
        assertFalse(m.rereading(now));
    }

    @Test
    public void aGlanceBackIsNotAReRead() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        read(m, 20_000);
        show(m, 5);
        read(m, 2_000);
        show(m, 6);
        for (ReadsModel.Visit v : m.drain()) {
            assertFalse(v.reread);
        }
    }

    @Test
    public void aJumpBackIsNotAReRead() {
        final ReadsModel m = started();
        readPages(m, 20, 24, 75_000);
        m.drain();
        // from page 25 to page 3: a jump (the table of contents, a link)
        show(m, 3);
        read(m, 30_000);
        assertFalse(m.rereading(now));
        show(m, 4);
        for (ReadsModel.Visit v : m.drain()) {
            assertFalse(v.reread);
        }
    }

    @Test
    public void goingBackAfterTheWindowIsNotCounted() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        show(m, 5);
        read(m, ReadsRules.AWAY_MIN_MS);
        idle(m, ReadsRules.REREAD_WINDOW_MS);
        show(m, 6);
        for (ReadsModel.Visit v : m.drain()) {
            assertFalse(v.reread);
        }
    }

    @Test
    public void twoPagesBackAreBothReRead() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        show(m, 4);
        read(m, 30_000);
        show(m, 5);
        read(m, 30_000);
        show(m, 6);
        int rereads = 0;
        for (ReadsModel.Visit v : m.drain()) {
            rereads += v.reread ? 1 : 0;
        }
        assertEquals(2, rereads);
    }

    @Test
    public void reReadPagesDoNotChangeThePace() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        final int before = m.paceSamples();
        show(m, 5);
        read(m, 30_000);
        show(m, 6);
        // the back-and-forth gave no pace sample: page 6's first stay ended going back, page 5's was a re-read
        assertEquals(before, m.paceSamples());
    }

    @Test
    public void aPageMuchLongerThanThePaceIsALongPage() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        // page 6, 300 words at 250 ms: 75 s expected
        read(m, 180_000);
        assertFalse(m.longPage());
        assertEquals(ReadsModel.Help.NONE, m.help(now));
        read(m, 10_000);
        assertTrue(m.longPage());
        assertEquals(ReadsModel.State.LONG_PAGE, m.state(now));
        assertEquals(ReadsModel.Help.DWELL, m.help(now));
        show(m, 7);
        final List<ReadsModel.Visit> visits = m.drain();
        assertEquals(1, visits.size());
        assertTrue(visits.get(0).dwell);
        assertEquals(190_000, visits.get(0).ms);
    }

    @Test
    public void helpIsOfferedOncePerPage() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        read(m, 200_000);
        assertEquals(ReadsModel.Help.DWELL, m.help(now));
        m.helpOffered();
        assertEquals(ReadsModel.Help.NONE, m.help(now));
        // back on it later: still not again
        show(m, 7);
        read(m, 5_000);
        show(m, 6);
        read(m, 20_000);
        assertTrue(m.rereading(now));
        assertEquals(ReadsModel.Help.NONE, m.help(now));
    }

    @Test
    public void helpAfterAReReadWaitsAMoment() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        read(m, 10_000);
        show(m, 5);
        read(m, 5_000);
        assertTrue(m.rereading(now));
        assertEquals(ReadsModel.Help.NONE, m.help(now));
        read(m, 3_000);
        assertEquals(ReadsModel.Help.REREAD, m.help(now));
    }

    @Test
    public void noLongPageWithoutKnownWords() {
        final ReadsModel m = new ReadsModel(250);
        m.tick(now, true);
        m.page(now, 1, 0, -1);
        read(m, 200_000);
        assertFalse(m.wordsKnown());
        assertFalse(m.longPage());
        m.words(300);
        assertTrue(m.longPage());
    }

    @Test
    public void timeUnderADialogIsNotReading() {
        final ReadsModel m = started();
        show(m, 1);
        read(m, 30_000);
        idle(m, 60_000);
        read(m, 30_000);
        assertEquals(60_000, m.pageMs());
        assertEquals(60_000, m.sessionMs());
    }

    @Test
    public void aPauseStartsANewSession() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        assertEquals(5 * 75_000, m.sessionMs());
        idle(m, ReadsRules.BREAK_MS);
        assertEquals(0, m.sessionMs());
        // the pace learned before stays
        assertEquals(250, m.pace(), 0.01);
        read(m, 10_000);
        assertEquals(10_000, m.sessionMs());
    }

    @Test
    public void aShortPauseDoesNotBreakTheSession() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        idle(m, ReadsRules.BREAK_MS - 1_000);
        read(m, 10_000);
        assertEquals(5 * 75_000 + 10_000, m.sessionMs());
    }

    @Test
    public void aStoppedClockIsAPause() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        // the app did not run for ten minutes: one late tick
        now += 10 * 60_000;
        m.tick(now, true);
        assertEquals(0, m.sessionMs());
    }

    @Test
    public void aPageLeftOpenStopsCounting() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        final long session = m.sessionMs();
        // page 6 left open for half an hour: counted up to the limit, then the session ends
        read(m, 30 * 60_000);
        assertEquals(ReadsRules.awayLimit(WORDS, 250), m.pageMs());
        assertEquals(0, m.sessionMs());
        assertFalse(m.longPage());
        assertTrue(session > 0);
    }

    @Test
    public void slowdownOfTheSessionsLastPages() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 63_000);
        assertEquals(0, m.slowdown(), 0);
        readPages(m, 6, 10, 90_000);
        // 210 ms a word, then 300
        assertEquals(0.3, m.slowdown(), 0.0001);
        assertEquals(ReadsModel.State.SLOWING, m.state(now));
    }

    @Test
    public void breakCardNeedsALongSessionWithASlowdown() {
        final ReadsModel m = started();
        readPages(m, 1, 20, 63_000);
        readPages(m, 21, 25, 90_000);
        final long wall = 5_000_000_000L;
        // 28.5 minutes: slower, but not long enough
        assertTrue(ReadsRules.isSlowdown(m.slowdown()));
        assertFalse(m.wantsBreak(wall, 0));
        readPages(m, 26, 35, 90_000);
        assertTrue(m.sessionMs() >= ReadsRules.FATIGUE_MIN_MS);
        assertTrue(m.wantsBreak(wall, 0));
        assertFalse(m.wantsBreak(wall, wall + ReadsRules.SNOOZE_MS));
        m.breakTaken();
        assertFalse(m.wantsBreak(wall, 0));
        assertEquals(0, m.sessionMs());
    }

    @Test
    public void aSteadyLongSessionBringsNoBreakCard() {
        final ReadsModel m = started();
        readPages(m, 1, 40, 75_000);
        assertTrue(m.sessionMs() >= ReadsRules.FATIGUE_MIN_MS);
        assertFalse(m.wantsBreak(5_000_000_000L, 0));
    }

    @Test
    public void jumpsGiveNoPaceSample() {
        final ReadsModel m = started();
        show(m, 1);
        read(m, 75_000);
        show(m, 40);
        assertEquals(0, m.paceSamples());
    }

    @Test
    public void aNewLayoutClosesThePageAndForgetsOffers() {
        final ReadsModel m = started();
        readPages(m, 1, 5, 75_000);
        m.drain();
        read(m, 200_000);
        m.helpOffered();
        m.relaid(now);
        final List<ReadsModel.Visit> visits = m.drain();
        assertEquals(1, visits.size());
        assertEquals(6, visits.get(0).page);
        assertEquals(-1, m.page());
        // the same number is another page now
        show(m, 6);
        read(m, 200_000);
        assertEquals(ReadsModel.Help.DWELL, m.help(now));
    }

    @Test
    public void endClosesTheStayOnThePageShown() {
        final ReadsModel m = started();
        show(m, 3);
        read(m, 12_000);
        m.end(now);
        final List<ReadsModel.Visit> visits = m.drain();
        assertEquals(1, visits.size());
        assertEquals(12_000, visits.get(0).ms);
        assertEquals(2, visits.get(0).tag);
        assertTrue(m.drain().isEmpty());
    }

    @Test
    public void theSamePageShownAgainChangesNothing() {
        final ReadsModel m = started();
        show(m, 3);
        read(m, 12_000);
        m.page(now, 3, 2, 310);
        assertEquals(12_000, m.pageMs());
        assertTrue(m.drain().isEmpty());
    }
}
