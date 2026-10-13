package com.foobnix.webdav;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * WebDAV sync tied to the app being on screen: everything when it comes back
 * ({@link SyncEvents#appForeground}), what waited when it leaves, a look at the server's change
 * marker every {@link #POLL_MS} (.agents/planning/event-sync-design.md) and a quiet full sync
 * every {@link #EVERY_MS} as a safety net (.agents/planning/unified-sync-design.md, D10). Nothing
 * runs while the app is in the background.
 */
public final class ForegroundSync implements Application.ActivityLifecycleCallbacks {

    static final long EVERY_MS = 15 * 60 * 1000L;
    static final long POLL_MS = 60 * 1000L;

    private final Application app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int started;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            WebDav.runSyncService(app, false);
            handler.postDelayed(this, EVERY_MS);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            SyncEvents.poll();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private ForegroundSync(Application app) {
        this.app = app;
    }

    public static void register(Application app) {
        app.registerActivityLifecycleCallbacks(new ForegroundSync(app));
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (started++ == 0) {
            SyncEvents.appForeground();
            // coming back already syncs: the first quiet one comes a period later
            handler.postDelayed(tick, EVERY_MS);
            handler.postDelayed(poll, POLL_MS);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (started > 0 && --started == 0) {
            handler.removeCallbacks(tick);
            handler.removeCallbacks(poll);
            SyncEvents.appBackground();
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
        BookSync.hidden(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
