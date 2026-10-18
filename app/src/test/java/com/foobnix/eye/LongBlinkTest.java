package com.foobnix.eye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Long blinks at 10 frames a second. */
public class LongBlinkTest {

    static final long STEP = 100;

    long t = 1_000;
    final LongBlink b = new LongBlink();

    static FaceFrame eyes(long t, float blink, float ear) {
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.face = true;
        f.blinkL = blink;
        f.blinkR = blink;
        f.earL = ear;
        f.earR = ear;
        return f;
    }

    /** Frames for {@code ms}; @return how many fired. */
    int run(long ms, float blink, float ear) {
        int fired = 0;
        for (long end = t + ms; t < end; t += STEP) {
            if (b.update(eyes(t, blink, ear), true)) {
                fired++;
            }
        }
        return fired;
    }

    int open(long ms) {
        return run(ms, 0.2f, 0.28f);
    }

    int closed(long ms) {
        return run(ms, 0.8f, 0.08f);
    }

    @Test
    public void learnsTheOpenEyes() {
        assertFalse(b.knowsOpen());
        open(1_000);
        assertTrue(b.knowsOpen());
        assertEquals(1f, b.openShareL(eyes(0, 0.2f, 0.28f)), 0.01);
    }

    @Test
    public void naturalBlinksNeverTurn() {
        open(1_000);
        for (int frames = 1; frames <= 4; frames++) {
            assertEquals(0, closed(frames * STEP) + open(1_000));
            assertEquals(LongBlink.Result.SHORT, b.last());
        }
    }

    @Test
    public void aSecondWithTheEyesClosedTurnsOnOpening() {
        open(1_000);
        assertEquals(0, closed(800));
        assertEquals(1, open(300));
        assertEquals(LongBlink.Result.LONG, b.last());
        assertEquals(800, b.lastMs());
    }

    @Test
    public void restingTheEyesDoesNotTurn() {
        open(1_000);
        closed(2_500);
        assertEquals(0, open(500));
        assertEquals(LongBlink.Result.TOO_LONG, b.last());
    }

    @Test
    public void oneOpenFrameInsideAClosureDoesNotSplitIt() {
        open(1_000);
        closed(400);
        open(100);
        closed(400);
        assertEquals(1, open(300));
        assertEquals(900, b.lastMs());
    }

    @Test
    public void lookingDownAtTheBottomLinesIsNotClosed() {
        // the owner's trace: eyeBlink 0.7 while the eyes only narrow (aspect ratio 0.7 of open)
        open(1_000);
        assertEquals(0, run(1_000, 0.7f, 0.2f) + open(500));
        assertFalse(b.isClosed(eyes(0, 0.7f, 0.2f)));
    }

    @Test
    public void readingTheBottomLinesOfATabletIsNotClosed() {
        // the owner's second trace: eyeBlink 0.63, eyes at 0.4 of open while reading the last lines
        open(1_000);
        assertEquals(0, run(1_500, 0.63f, 0.11f) + open(500));
        assertFalse(b.isClosed(eyes(0, 0.63f, 0.11f)));
    }

    @Test
    public void aClosureGoesOnWhileTheLidsOpenInSteps() {
        open(1_000);
        // deep for 0.3 s, then a shallower 0.4 of open for 0.5 s: one 0.8 s closure
        closed(300);
        run(500, 0.6f, 0.11f);
        assertEquals(1, open(300));
        assertEquals(800, b.lastMs());
    }

    @Test
    public void withTheReadersProfileTheirBottomGazeIsOpenAndTheirClosureClosed() {
        // the owner's tablet trace: bottom gaze 0.10 / 0.065, closed 0.05 / 0.03
        b.setProfile(new EyeProfile(0.24f, 0.2f, 0.10f, 0.065f, 0.05f, 0.03f));
        final FaceFrame bottom = eyes(0, 0.63f, 0.10f);
        bottom.earR = 0.065f;
        assertFalse(b.isClosed(bottom));
        final FaceFrame shut = eyes(0, 0.72f, 0.05f);
        shut.earR = 0.03f;
        assertTrue(b.isClosed(shut));
        assertTrue(b.knowsOpen());
    }

    @Test
    public void theTabletsRealLongBlinkStaysOneClosure() {
        // the owner's tablet trace at 87 s: 0.06/0.04, 0.05/0.04, 0.05/0.03, 0.07/0.04, 0.08/0.05, then open
        b.setProfile(new EyeProfile(0.24f, 0.2f, 0.10f, 0.065f, 0.05f, 0.03f));
        final float[][] ears = {{0.06f, 0.04f}, {0.05f, 0.04f}, {0.05f, 0.03f}, {0.07f, 0.04f}, {0.08f, 0.05f}, {0.07f, 0.05f}};
        long t = 1_000;
        for (float[] e : ears) {
            final FaceFrame f = eyes(t, 0.7f, e[0]);
            f.earR = e[1];
            assertFalse(b.update(f, true));
            t += 150;
        }
        final FaceFrame open = eyes(t, 0.4f, 0.2f);
        b.update(open, true);
        open.t = t + 150;
        assertTrue(b.update(open, true));
        assertEquals(900, b.lastMs());
    }

    @Test
    public void eyesThatCannotBeToldApartNeverClose() {
        b.setProfile(new EyeProfile(0.24f, 0.2f, 0.06f, 0.05f, 0.055f, 0.048f));
        assertTrue(b.isBlind());
        assertFalse(b.isClosed(eyes(0, 0.9f, 0.0f)));
    }

    @Test
    public void beforeTheOpenEyesAreKnownOnlyAHighBlinkCounts() {
        assertFalse(b.isClosed(eyes(0, 0.6f, 0.05f)));
        assertTrue(b.isClosed(eyes(0, 0.8f, 0.05f)));
    }

    @Test
    public void notTwiceInASecond() {
        open(1_000);
        closed(700);
        assertEquals(1, open(200));
        closed(700);
        assertEquals(0, open(200));
        assertEquals(LongBlink.Result.COOLDOWN, b.last());
    }

    @Test
    public void closedFramesTeachNothing() {
        open(1_000);
        closed(1_500);
        open(200);
        assertEquals(1f, b.openShareL(eyes(0, 0.2f, 0.28f)), 0.01);
    }
}
