package com.foobnix.webdav;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.LOG;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Before an AI request, what the server holds for the book comes first: when another device (or
 * anything else that edits the synced folder) sent something since this device last looked, the
 * book's files are pulled, so a result made elsewhere is used instead of asking the API again.
 * At most once a minute per book, and never more than a few seconds of waiting: offline, or with a
 * slow server, the job goes on as before.
 */
public final class ServerFirst {

    static final long EVERY_MS = 60_000;
    static final long WAIT_MS = 5_000;

    private static final Map<String, Long> checked = new HashMap<>();
    private static final ExecutorService pulls = Executors.newSingleThreadExecutor(r -> new Thread(r, "@T webdav first"));

    private ServerFirst() {
    }

    /** Blocks the calling (background) thread for at most {@link #WAIT_MS}; never throws. */
    public static void pull(Context c, String bookPath) {
        try {
            if (c == null || bookPath == null || !WebDav.mayRunAuto(c)) {
                return;
            }
            final long now = System.currentTimeMillis();
            synchronized (checked) {
                if (!due(checked.get(bookPath), now)) {
                    return;
                }
                checked.put(bookPath, now);
            }
            final Context app = c.getApplicationContext();
            final Future<?> run = pulls.submit(() -> {
                try {
                    if (!WebDav.markerChanged(app)) {
                        return;
                    }
                    final FolderSync.Result result = WebDav.pullBook(app, bookPath);
                    if (!result.changedHere.isEmpty()) {
                        final Set<String> changed = result.changedHere;
                        new Handler(Looper.getMainLooper()).post(() -> BookSync.pulled(bookPath, changed));
                    }
                } catch (Exception e) {
                    LOG.d("ServerFirst", e.getMessage());
                }
            });
            try {
                run.get(WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // the pull goes on in the background; this request does not wait for it
            }
        } catch (Exception e) {
            LOG.d("ServerFirst", e.getMessage());
        }
    }

    /** Whether a book last checked at {@code last} (null: never) is checked again at {@code now}. */
    static boolean due(Long last, long now) {
        return last == null || now - last >= EVERY_MS || now < last;
    }
}
