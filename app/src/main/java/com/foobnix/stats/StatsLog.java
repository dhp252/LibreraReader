package com.foobnix.stats;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The reading log of this device: events appended, one line each, to
 * {@code profile.<name>/stats/<device id>/YYYY-MM.jsonl} on a background thread. The folder is
 * synced like the rest of the profile; each device writes only its own folder, so no two
 * devices ever change the same file. Also holds the {@link StatsStore} the screens read.
 */
public final class StatsLog {

    public static final String FOLDER = "stats";
    private static final String PREFS = "stats";

    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "@T stats log"));
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Context app;
    private static String deviceId;
    private static StatsStore store;
    private static File storeRoot;

    private StatsLog() {
    }

    public static void init(Context c) {
        app = c.getApplicationContext();
    }

    /**
     * This device's id: a random one kept in prefs that never leave the device (excluded from
     * backup), so two devices of the same model, or a restored backup, never share a log.
     */
    public static synchronized String deviceId() {
        if (deviceId == null && app != null) {
            final SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            deviceId = sp.getString("device", null);
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString().substring(0, 8);
                sp.edit().putString("device", deviceId).apply();
            }
        }
        return deviceId;
    }

    /** The stats folder of the current profile, null before the profile is set up. */
    public static File root() {
        final File profile = AppProfile.SYNC_FOLDER_PROFILE;
        return profile == null ? null : new File(profile, FOLDER);
    }

    /** A new event of the device's local time now. */
    public static StatsEvent event(String type, String book) {
        final long now = System.currentTimeMillis();
        return new StatsEvent(type, now, TimeZone.getDefault().getOffset(now) / 60_000, book);
    }

    public static void log(final StatsEvent e) {
        final File root = root();
        final String id = deviceId();
        if (root == null || id == null) {
            return;
        }
        writer.execute(() -> {
            try {
                final File folder = new File(root, id);
                final File file = new File(folder, Days.monthKey(StatsData.dayOf(e.ts, e.tz)) + ".jsonl");
                final boolean fresh = !file.isFile();
                if (fresh && !folder.isDirectory() && !folder.mkdirs()) {
                    LOG.d("StatsLog", "no folder", folder);
                    return;
                }
                try (OutputStream out = new FileOutputStream(file, true)) {
                    if (fresh) {
                        final StatsEvent header = new StatsEvent(StatsEvent.DEV, e.ts, e.tz, null);
                        header.id = id;
                        header.name = Build.MODEL;
                        out.write((header.toJson() + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    out.write((e.toJson() + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ex) {
                LOG.d("StatsLog", ex.getMessage());
            }
        });
    }

    /** The summaries of every device's log, as of the last {@link #refresh}. */
    public static synchronized StatsStore store() {
        final File root = root();
        if (root == null) {
            return null;
        }
        if (store == null || !root.equals(storeRoot)) {
            store = new StatsStore(root);
            storeRoot = root;
        }
        return store;
    }

    /**
     * Reads what the logs gained (after what was queued to be written), then runs
     * {@code done} on the main thread.
     */
    public static void refresh(final Runnable done) {
        writer.execute(() -> {
            final StatsStore s = store();
            if (s != null) {
                s.refresh();
            }
            ReadingSpeed.update(s);
            if (done != null) {
                main.post(done);
            }
        });
    }

    /** The summaries of these devices (all when null); empty before the profile is set up. */
    public static StatsData data(Set<String> deviceIds) {
        final StatsStore s = store();
        return s == null ? new StatsData() : s.data(deviceIds);
    }

    /**
     * Starts the stats afresh on every device: the time of the clear goes into
     * {@link StatsStore#CLEARED} (synced), which hides everything before it. This device's own
     * logs are deleted; the other devices' stay, hidden, since deleting most of a synced folder
     * at once looks like a lost folder to the sync, which copies the files back.
     */
    public static void clear(final Runnable done) {
        writer.execute(() -> {
            final File root = root();
            final String id = deviceId();
            if (root != null) {
                try {
                    StatsStore.writeCleared(root, System.currentTimeMillis());
                } catch (IOException e) {
                    LOG.d("StatsLog", e.getMessage());
                }
                if (id != null) {
                    deleteTree(new File(root, id));
                }
            }
            final StatsStore s = store();
            if (s != null) {
                s.reset();
            }
            ReadingSpeed.update(s);
            if (done != null) {
                main.post(done);
            }
        });
    }

    private static void deleteTree(File f) {
        final File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!f.delete()) {
            LOG.d("StatsLog", "not deleted", f);
        }
    }
}
