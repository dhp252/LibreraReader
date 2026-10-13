package com.foobnix.webdav;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncPlanTest {

    private final SyncPlan plan = new SyncPlan();

    @Test
    public void nothingWaitsAtFirst() {
        assertEquals(SyncPlan.NEVER, plan.nextDue());
        assertTrue(plan.take(1_000).isEmpty());
    }

    @Test
    public void aPageGoesUpOnceItStays() {
        plan.changed(1_000);
        plan.changed(5_000);
        assertEquals(15_000, plan.nextDue());
        assertTrue(plan.take(14_999).isEmpty());

        final SyncPlan.Job job = plan.take(15_000);
        assertTrue(job.push);
        assertFalse(job.settings);
        assertEquals(SyncPlan.NEVER, plan.nextDue());
    }

    @Test
    public void steadyReadingStillGoesUpWithinAMinute() {
        for (long t = 0; t <= 120_000; t += 8_000) {
            plan.changed(t);
            assertTrue("due at " + plan.nextDue(), plan.nextDue() <= SyncPlan.MAX_WAIT_MS);
        }
        assertTrue(plan.take(SyncPlan.MAX_WAIT_MS).push);
    }

    @Test
    public void anAiResultGoesUpAtOnce() {
        plan.changed(1_000);
        plan.changedNow(2_000);
        assertEquals(2_000, plan.nextDue());
        assertTrue(plan.take(2_000).push);
    }

    @Test
    public void settingsWaitLonger() {
        plan.settingsChanged(0);
        assertEquals(SyncPlan.SETTINGS_SETTLE_MS, plan.nextDue());
        final SyncPlan.Job job = plan.take(SyncPlan.SETTINGS_SETTLE_MS);
        assertTrue(job.settings);
        assertFalse(job.push);
    }

    @Test
    public void comingBackSyncsEverythingAtMostOnceAMinute() {
        plan.foreground(100_000);
        assertEquals(0, plan.nextDue());
        assertTrue(plan.take(100_000).full);

        plan.foreground(130_000);
        assertEquals(SyncPlan.NEVER, plan.nextDue());

        plan.foreground(160_000);
        assertTrue(plan.take(160_000).full);
    }

    @Test
    public void aFullSyncTakesWhatWaited() {
        plan.changed(0);
        plan.settingsChanged(0);
        plan.fullQueued(1_000);
        assertEquals(SyncPlan.NEVER, plan.nextDue());
        // and counts for the once-a-minute rule
        plan.foreground(30_000);
        assertEquals(SyncPlan.NEVER, plan.nextDue());
    }

    @Test
    public void booksArePulledAtOnceAndOnce() {
        plan.pull("/a.pdf");
        plan.pull("/a.pdf");
        assertEquals(0, plan.nextDue());
        assertEquals(Collections.singleton("/a.pdf"), plan.take(5).books);
        assertTrue(plan.take(6).isEmpty());
    }

    @Test
    public void leavingHandsOverWhatWaited() {
        assertFalse(plan.takeWaiting());
        plan.changed(0);
        assertTrue(plan.takeWaiting());
        assertEquals(SyncPlan.NEVER, plan.nextDue());
    }

    @Test
    public void jumpRule() {
        // the other device read later, elsewhere: go there
        assertEquals(JumpRule.JUMP, JumpRule.decide(1_000, 10, 2_000, 50, false));
        // backwards too (most recent read wins)
        assertEquals(JumpRule.JUMP, JumpRule.decide(1_000, 50, 2_000, 10, false));
        // a page turned here after it: stay
        assertEquals(JumpRule.STAY, JumpRule.decide(3_000, 10, 2_000, 50, false));
        // the same page, or the same record (a jump keeps the other device's time): stay
        assertEquals(JumpRule.STAY, JumpRule.decide(1_000, 50, 2_000, 50, false));
        assertEquals(JumpRule.STAY, JumpRule.decide(2_000, 10, 2_000, 50, false));
        // reading aloud, auto-scroll, drawing: only say where
        assertEquals(JumpRule.OFFER, JumpRule.decide(1_000, 10, 2_000, 50, true));
        assertEquals(JumpRule.STAY, JumpRule.decide(1_000, 10, 2_000, 0, false));
    }
}
