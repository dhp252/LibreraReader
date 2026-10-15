package com.foobnix.webdav;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.LOG;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebDAV sync driven by what the reader does (.agents/planning/event-sync-design.md): each event
 * syncs the files it touched, a few small requests, on one background thread so runs never
 * overlap. When each falls due is {@link SyncPlan}'s; the full sync stays with
 * {@link WebDav#runSyncService}. Events before {@link #init}, or with WebDAV off, do nothing.
 */
public final class SyncEvents {

    private static final SyncPlan plan = new SyncPlan();
    private static ScheduledExecutorService worker;
    private static ScheduledFuture<?> next;
    private static Context app;
    /** The book a reader shows, null elsewhere: what a change on another device pulls. */
    private static volatile String shownBook;

    private SyncEvents() {
    }

    public static void init(Context c) {
        app = c.getApplicationContext();
    }

    /** The page or the bookmarks changed: sent once it settles. */
    public static void readingChanged() {
        synchronized (plan) {
            plan.changed(System.currentTimeMillis());
        }
        schedule();
    }

    /** An AI result was stored: sent at once. */
    public static void aiSaved() {
        synchronized (plan) {
            plan.changedNow(System.currentTimeMillis());
        }
        schedule();
    }

    public static void settingsChanged() {
        synchronized (plan) {
            plan.settingsChanged(System.currentTimeMillis());
        }
        schedule();
    }

    /** The app came to the foreground: everything, unless that was done a moment ago. */
    static void appForeground() {
        synchronized (plan) {
            plan.foreground(System.currentTimeMillis());
        }
        schedule();
    }

    /**
     * The app left the screen: what still waited goes as a background job (it outlives the app),
     * and files changed without an event of their own (tags, lists) on the way.
     */
    static void appBackground() {
        if (!enabled()) {
            return;
        }
        final boolean waiting;
        synchronized (plan) {
            waiting = plan.takeWaiting();
        }
        if (waiting) {
            WebDav.runSyncService(app, false);
        } else {
            run(() -> {
                if (WebDav.mayRunAuto(app)) {
                    WebDav.pushDirty(app);
                }
            });
        }
    }

    /** A reader shows this book (opened or back on screen): its files, now. */
    public static void bookShown(String path) {
        shownBook = path;
        synchronized (plan) {
            plan.pull(path);
        }
        schedule();
    }

    public static void bookHidden() {
        shownBook = null;
    }

    /**
     * A reader closed: this device's reading log goes up now. The log is left out of the
     * pushes while reading (it grows with every page) and of the change marker (the other
     * device reads it at its next full sync).
     */
    public static void statsWritten() {
        if (!enabled()) {
            return;
        }
        run(() -> {
            if (WebDav.mayRunAuto(app)) {
                WebDav.pushStats(app);
            }
        });
    }

    /** A full sync was queued: it takes whatever waited. */
    static void fullQueued() {
        synchronized (plan) {
            plan.fullQueued(System.currentTimeMillis());
        }
    }

    /** A full sync brought changes: the book shown takes them as from a pull of its own. */
    public static void fullSynced() {
        final String book = shownBook;
        if (book != null) {
            final Set<String> files = new HashSet<>(WebDav.bookFiles(book));
            new Handler(Looper.getMainLooper()).post(() -> BookSync.pulled(book, files));
        }
    }

    /** Asks the server whether another device sent something; if so, pulls what is shown. */
    static void poll() {
        if (!enabled()) {
            return;
        }
        run(() -> {
            if (!WebDav.mayRunAuto(app) || !WebDav.markerChanged(app)) {
                return;
            }
            final String book = shownBook;
            synchronized (plan) {
                if (book != null) {
                    plan.pull(book);
                } else {
                    plan.fullNow();
                }
            }
            schedule();
        });
    }

    private interface Job {
        void run() throws Exception;
    }

    private static boolean enabled() {
        return app != null && WebDav.isEnabled(app);
    }

    private static synchronized ScheduledExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "@T webdav events"));
        }
        return worker;
    }

    private static void run(Job job) {
        worker().execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                LOG.d("SyncEvents", e.getMessage());
            }
        });
    }

    private static void schedule() {
        if (!enabled()) {
            return;
        }
        synchronized (plan) {
            if (next != null) {
                next.cancel(false);
                next = null;
            }
            final long at = plan.nextDue();
            if (at == SyncPlan.NEVER) {
                return;
            }
            final long delay = Math.max(0, at - System.currentTimeMillis());
            next = worker().schedule(SyncEvents::runDue, delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void runDue() {
        final SyncPlan.Job job;
        synchronized (plan) {
            job = plan.take(System.currentTimeMillis());
        }
        try {
            // skipped for now (Wi-Fi only, off): the changes stay on the device for the next sync
            if (!job.isEmpty() && WebDav.mayRunAuto(app)) {
                if (job.full) {
                    WebDav.runSyncService(app, false);
                } else {
                    if (job.push) {
                        WebDav.pushDirty(app);
                    }
                    if (job.settings) {
                        WebDav.pushSettings(app);
                    }
                }
                for (String book : job.books) {
                    final FolderSync.Result result = WebDav.pullBook(app, book);
                    if (!result.changedHere.isEmpty()) {
                        final Set<String> changed = result.changedHere;
                        new Handler(Looper.getMainLooper()).post(() -> BookSync.pulled(book, changed));
                    }
                }
            }
        } catch (Exception e) {
            LOG.d("SyncEvents", e.getMessage());
        }
        schedule();
    }
}
