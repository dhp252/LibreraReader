package com.foobnix.ai;

import android.app.Activity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AiProgressTest {

    private static final String KEY = AiTask.key("Book.epub", AiTask.HIGHLIGHTS);

    /** A job: what screens it was handed. */
    private static final class Job implements AiProgress.Attachable {
        final List<Object> screens = new ArrayList<>();

        @Override
        public void attach(Object screen, Activity owner) {
            screens.add(screen);
        }
    }

    /** A screen that hears waits and ends. */
    private static final class Screen implements AiProgress.TaskScreen {
        final List<String> heard = new ArrayList<>();

        @Override
        public void onWaiting(String status) {
            heard.add("waiting: " + status);
        }

        @Override
        public void onEnded(int state, String message) {
            heard.add("ended " + state + ": " + message);
        }
    }

    @Before
    public void setUp() {
        AiProgress.main = Runnable::run;
        AiProgress.clear();
    }

    @After
    public void tearDown() {
        AiProgress.clear();
    }

    @Test
    public void aScreenOpenedBeforeTheJobStartsGetsItWhenItStarts() {
        final Screen screen = new Screen();
        AiProgress.attach(KEY, null, screen);
        assertEquals(AiProgress.WAITING, AiProgress.state(KEY));
        assertFalse(AiProgress.isRunning(KEY));

        final Job job = new Job();
        AiProgress.started(KEY, job);
        assertEquals(1, job.screens.size());
        assertSame(screen, job.screens.get(0));
        assertTrue(AiProgress.isRunning(KEY));
        assertEquals(KEY, AiProgress.running());
    }

    @Test
    public void aScreenOpenedWhileItRunsGetsItAtOnceAndLeavesItWhenClosed() {
        final Job job = new Job();
        AiProgress.started(KEY, job);
        final Screen screen = new Screen();
        AiProgress.attach(KEY, null, screen);
        assertSame(screen, job.screens.get(0));

        AiProgress.detach(KEY, new Screen());
        assertEquals("another screen leaving changes nothing", 1, job.screens.size());
        AiProgress.detach(KEY, screen);
        assertNull(job.screens.get(1));
    }

    @Test
    public void theScreenHearsWaitsBetweenRunsAndTheEnd() {
        final Screen screen = new Screen();
        AiProgress.attach(KEY, null, screen);
        final Job first = new Job();
        AiProgress.started(KEY, first);
        AiProgress.waiting(KEY, "Waiting for the network");
        assertEquals(AiProgress.WAITING, AiProgress.state(KEY));
        assertEquals("Waiting for the network", AiProgress.status(KEY));
        assertNull(AiProgress.running());

        // the next run is a new job: the screen goes to it
        final Job second = new Job();
        AiProgress.started(KEY, second);
        assertSame(screen, second.screens.get(0));
        AiProgress.ended(KEY, AiProgress.CANCELLED, "Cancelled");
        assertEquals(AiProgress.CANCELLED, AiProgress.state(KEY));
        assertFalse(AiProgress.isRunning(KEY));
        assertEquals("[waiting: Waiting for the network, ended 4: Cancelled]", screen.heard.toString());
    }

    @Test
    public void tasksOfOtherBooksAreApart() {
        AiProgress.started(KEY, new Job());
        final String other = AiTask.key("Other.epub", AiTask.HIGHLIGHTS);
        assertEquals(-1, AiProgress.state(other));
        assertFalse(AiProgress.isRunning(other));
    }

    @Test
    public void listenersHearEveryChangeAndBusyMeansQueuedOrRunning() {
        final List<String> heard = new ArrayList<>();
        final AiProgress.Listener l = (key, state) -> heard.add(key + "=" + state);
        AiProgress.listen(l);
        assertFalse(AiProgress.isBusy(KEY));
        AiProgress.queued(KEY);
        assertTrue(AiProgress.isBusy(KEY));
        AiProgress.started(KEY, new Job());
        assertTrue(AiProgress.isBusy(KEY));
        AiProgress.waiting(KEY, "network");
        AiProgress.ended(KEY, AiProgress.DONE, "done");
        assertFalse(AiProgress.isBusy(KEY));
        AiProgress.unlisten(l);
        AiProgress.queued(KEY);
        assertEquals("[" + KEY + "=0, " + KEY + "=1, " + KEY + "=0, " + KEY + "=2]", heard.toString());
    }
}
