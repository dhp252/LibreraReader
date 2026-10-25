package com.foobnix.ai;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.Apps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Where a long AI task stands, for the screens that open while it runs (design-2026-10-25.md):
 * per task key, its state, the running job (in this process) and the screen attached to it. A
 * screen attached before the job starts gets it when it starts; a screen whose activity is gone is
 * left out. The job talks to the screen itself; this only hands the screen over.
 */
public final class AiProgress {

    public static final int WAITING = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;
    public static final int CANCELLED = 4;

    /** What a running job lets a screen do. */
    public interface Attachable {
        void attach(Object screen, Activity owner);
    }

    /** A screen that also wants to hear when its task waits (between runs) or ends (cancelled). */
    public interface TaskScreen {
        void onWaiting(String status);

        void onEnded(int state, String message);
    }

    /** Hears a task change state (queued, runs, waits, ended), on the main thread: the reader's ✦ button. */
    public interface Listener {
        void onChanged(String key, int state);
    }

    private static final class Entry {
        int state = WAITING;
        String status = "";
        String message;
        Attachable job;
        Object screen;
        Activity owner;
    }

    /** The main thread (a plain call in tests). */
    static volatile Executor main;

    private static final Map<String, Entry> entries = new HashMap<>();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile String running;

    private AiProgress() {
    }

    public static void main(Runnable r) {
        Executor m = main;
        if (m == null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            m = handler::post;
            main = m;
        }
        m.execute(r);
    }

    private static Entry entry(String key) {
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry();
            entries.put(key, e);
        }
        return e;
    }

    // --- the task's side (AiWork) ---

    /** The task was queued (or was already): waiting until it runs, unless it runs now. */
    public static void queued(String key) {
        synchronized (entries) {
            final Entry e = entry(key);
            if (e.state != RUNNING) {
                e.state = WAITING;
                e.status = "";
                e.message = null;
            }
        }
        changed(key);
    }

    /** The task's job runs: a screen waiting for it gets it. */
    public static void started(String key, Attachable job) {
        final Object screen;
        final Activity owner;
        synchronized (entries) {
            final Entry e = entry(key);
            e.state = RUNNING;
            e.status = "";
            e.message = null;
            e.job = job;
            screen = alive(e) ? e.screen : null;
            owner = e.owner;
            running = key;
        }
        if (screen != null) {
            job.attach(screen, owner);
        }
        changed(key);
    }

    /** Between two runs (network, a restart): the screen hears why it waits. */
    public static void waiting(String key, String status) {
        final Object screen;
        synchronized (entries) {
            final Entry e = entry(key);
            e.state = WAITING;
            e.status = status;
            e.job = null;
            screen = alive(e) ? e.screen : null;
            if (key.equals(running)) {
                running = null;
            }
        }
        if (screen instanceof TaskScreen) {
            main(() -> ((TaskScreen) screen).onWaiting(status));
        }
        changed(key);
    }

    /** The task ended (done, failed, cancelled); the screen hears it when it wants to. */
    public static void ended(String key, int state, String message) {
        final Object screen;
        synchronized (entries) {
            final Entry e = entry(key);
            e.state = state;
            e.message = message;
            e.job = null;
            screen = alive(e) ? e.screen : null;
            if (key.equals(running)) {
                running = null;
            }
        }
        if (screen instanceof TaskScreen) {
            main(() -> ((TaskScreen) screen).onEnded(state, message));
        }
        changed(key);
    }

    public static void listen(Listener l) {
        listeners.add(l);
    }

    public static void unlisten(Listener l) {
        listeners.remove(l);
    }

    private static void changed(String key) {
        if (listeners.isEmpty()) {
            return;
        }
        main(() -> {
            final int state = state(key);
            for (Listener l : listeners) {
                l.onChanged(key, state);
            }
        });
    }

    // --- a screen's side ---

    /** A screen shows the task: it gets the running job now, or when it starts. */
    public static void attach(String key, Activity owner, Object screen) {
        final Attachable job;
        synchronized (entries) {
            final Entry e = entry(key);
            e.screen = screen;
            e.owner = owner;
            job = e.job;
        }
        if (job != null) {
            job.attach(screen, owner);
        }
    }

    /** The screen closes: the task goes on without it. */
    public static void detach(String key, Object screen) {
        final Attachable job;
        synchronized (entries) {
            final Entry e = entries.get(key);
            if (e == null || e.screen != screen) {
                return;
            }
            e.screen = null;
            e.owner = null;
            job = e.job;
        }
        if (job != null) {
            job.attach(null, null);
        }
    }

    public static int state(String key) {
        synchronized (entries) {
            final Entry e = entries.get(key);
            return e == null ? -1 : e.state;
        }
    }

    public static String status(String key) {
        synchronized (entries) {
            final Entry e = entries.get(key);
            return e == null ? "" : e.status;
        }
    }

    /** Whether the task is queued, waiting or running. */
    public static boolean isBusy(String key) {
        final int state = state(key);
        return state == RUNNING || state == WAITING;
    }

    /** Whether the task's job runs in this process now. */
    public static boolean isRunning(String key) {
        synchronized (entries) {
            final Entry e = entries.get(key);
            return e != null && e.state == RUNNING && e.job != null;
        }
    }

    /** The key of the task running now, or null. */
    public static String running() {
        return running;
    }

    private static boolean alive(Entry e) {
        return e.screen != null && (e.owner == null || !Apps.isDestroyedActivity(e.owner));
    }

    /** For tests: forget everything. */
    static void clear() {
        synchronized (entries) {
            entries.clear();
            running = null;
        }
        listeners.clear();
    }
}
