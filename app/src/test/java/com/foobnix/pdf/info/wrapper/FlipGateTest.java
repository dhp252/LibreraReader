package com.foobnix.pdf.info.wrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlipGateTest {

    // Surface.ROTATION_*
    private static final int R0 = 0, R180 = 2;

    /** Feeds readings every 50 ms; returns how many turns were asked. */
    private static int replay(FlipGate gate, long from, long to, int degrees, int rotation) {
        int turns = 0;
        for (long t = from; t < to; t += 50) {
            if (gate.onSensor(t, degrees, rotation)) {
                turns++;
            }
        }
        return turns;
    }

    @Test
    public void turnedOverAsksOnceWhileTheDisplayIsStillTurning() {
        final FlipGate gate = new FlipGate();
        // held upside down; the display stays at rotation 0 for 600 ms after the request
        assertEquals(1, replay(gate, 0, 1000, 180, R0));
        // the display has turned: upside down no longer
        assertEquals(0, replay(gate, 1000, 3000, 180, R180));
    }

    @Test
    public void aQuickTiltThroughUpsideDownDoesNothing() {
        final FlipGate gate = new FlipGate();
        assertEquals(0, replay(gate, 0, FlipGate.HOLD_MS - 50, 180, R0));
        assertEquals(0, replay(gate, 300, 1000, 0, R0));
    }

    @Test
    public void flipsBackWhenHeldUprightAgain() {
        final FlipGate gate = new FlipGate();
        assertEquals(1, replay(gate, 0, 500, 180, R0));
        assertEquals(0, replay(gate, 500, 2000, 180, R180));
        assertEquals(1, replay(gate, 2000, 2500, 0, R180));
    }

    @Test
    public void aRefusedTurnIsAskedAgainAfterTheWait() {
        final FlipGate gate = new FlipGate();
        // asked at 300 ms (after the hold)
        assertEquals(1, replay(gate, 0, 400, 180, R0));
        final long waitEnds = 300 + FlipGate.SETTLE_MS;
        // the display never turns: nothing until the wait is over, then once more after a hold
        assertEquals(0, replay(gate, 400, waitEnds, 180, R0));
        assertEquals(1, replay(gate, waitEnds, waitEnds + FlipGate.HOLD_MS + 100, 180, R0));
    }

    @Test
    public void resetForgetsAPendingTurn() {
        final FlipGate gate = new FlipGate();
        assertEquals(1, replay(gate, 0, 400, 180, R0));
        gate.reset();
        assertFalse(gate.onSensor(500, 180, R0));
        assertTrue(replay(gate, 550, 900, 180, R0) == 1);
    }
}
