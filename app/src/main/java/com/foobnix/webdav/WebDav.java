package com.foobnix.webdav;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.work.WebDavSyncWorker;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

/**
 * WebDAV sync: settings and runs.
 * <p>
 * The server keeps the layout of the local sync folder (Librera/): every {@code profile.*} folder
 * (progress, bookmarks, recent, favorites, tags, playlists of each device) is mirrored both ways,
 * {@code Books/} on demand (uploaded from here, downloaded when opened, see {@link WebDavBooksDialog}).
 * <p>
 * Server, user and password stay on this device: private preferences, never AppState, which
 * is itself synced.
 */
public class WebDav {

    public static final String BOOKS = "Books";
    private static final String PREFS = "webdav";
    private static final String KEY_URL = "url";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_SYNC = "lastSync";
    private static final String KEY_LAST_RESULT = "lastResult";
    private static final String WORK_NAME = "webdav-sync";

    /** What one run did, over all synced folders. */
    public static class Summary {
        public int uploaded, downloaded, deletedLocal, deletedRemote, conflicts;
        public final StringBuilder errors = new StringBuilder();

        void add(FolderSync.Result r, String folder) {
            uploaded += r.uploaded;
            downloaded += r.downloaded;
            deletedLocal += r.deletedLocal;
            deletedRemote += r.deletedRemote;
            conflicts += r.conflicts;
            for (String error : r.errors) {
                errors.append(folder).append('/').append(error).append('\n');
            }
        }

        public boolean changedLocal() {
            return downloaded > 0 || deletedLocal > 0;
        }

        public boolean hasErrors() {
            return errors.length() > 0;
        }

        @Override
        public String toString() {
            return "↑" + uploaded + " ↓" + downloaded + " ✕" + (deletedLocal + deletedRemote)
                    + (conflicts > 0 ? " !" + conflicts : "") + (hasErrors() ? "\n" + errors.toString().trim() : "");
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getUrl(Context c) {
        return prefs(c).getString(KEY_URL, "");
    }

    public static String getUser(Context c) {
        return prefs(c).getString(KEY_USER, "");
    }

    public static String getPassword(Context c) {
        return prefs(c).getString(KEY_PASSWORD, "");
    }

    public static boolean isEnabled(Context c) {
        return c != null && prefs(c).getBoolean(KEY_ENABLED, false) && !getUrl(c).trim().isEmpty();
    }

    public static void save(Context c, String url, String user, String password, boolean enabled) {
        prefs(c).edit()
                .putString(KEY_URL, url.trim())
                .putString(KEY_USER, user.trim())
                .putString(KEY_PASSWORD, password)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    /** "Today 10:21 - ↑2 ↓5 ✕0", or empty before the first run. */
    public static String getLastSyncInfo(Context c) {
        final long time = prefs(c).getLong(KEY_LAST_SYNC, 0);
        if (time == 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time))
                + " - " + prefs(c).getString(KEY_LAST_RESULT, "");
    }

    public static WebDavClient client(Context c) {
        return new WebDavClient(getUrl(c), getUser(c), getPassword(c));
    }

    /** Queues a background run when WebDAV sync is on, like GFile.runSyncService does for Google Drive. */
    public static void runSyncService(Context c, boolean force) {
        try {
            if (!isEnabled(c)) {
                return;
            }
            if (!force && BookCSS.get().isSyncWifiOnly && !Apps.isWifiEnabled(c)) {
                LOG.d("WebDav", "wifi not available");
                return;
            }
            final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WebDavSyncWorker.class).build();
            WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** One run on the calling (background) thread: every profile folder, then the books. */
    public static synchronized Summary syncNow(Context c) throws IOException {
        AppProfile.init(c);
        AppProfile.save(c);
        final File root = AppProfile.SYNC_FOLDER_ROOT;
        final WebDavClient client = client(c);
        final Summary summary = new Summary();

        final TreeSet<String> profiles = new TreeSet<>();
        final File[] local = root.listFiles();
        if (local != null) {
            for (File f : local) {
                if (f.isDirectory() && f.getName().startsWith(AppProfile.PROFILE_PREFIX)) {
                    profiles.add(f.getName());
                }
            }
        }
        try {
            for (WebDavClient.Entry entry : client.list("")) {
                if (entry.directory && entry.getName().startsWith(AppProfile.PROFILE_PREFIX)) {
                    profiles.add(entry.getName());
                }
            }
        } catch (WebDavClient.WebDavException e) {
            if (e.code != 404) {
                throw e;
            }
            client.mkdirs("");
        }

        for (String profile : profiles) {
            summary.add(new FolderSync(client, new File(root, profile), profile, stateFile(c, profile),
                    FolderSync.Mode.MIRROR, null).sync(), profile);
        }
        summary.add(books(c, client).sync(), BOOKS);

        prefs(c).edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .putString(KEY_LAST_RESULT, summary.toString())
                .apply();
        return summary;
    }

    /** The books on the server, mirrored on demand into Librera/Books. */
    public static FolderSync books(Context c, WebDavClient client) {
        return new FolderSync(client, AppProfile.SYNC_FOLDER_BOOKS, BOOKS, stateFile(c, BOOKS), FolderSync.Mode.ON_DEMAND, null);
    }

    /** Checks the settings: the folder can be listed (it is created when missing). */
    public static String testConnection(String url, String user, String password) {
        try {
            final WebDavClient client = new WebDavClient(url, user, password);
            List<WebDavClient.Entry> entries;
            try {
                entries = client.list("");
            } catch (WebDavClient.WebDavException e) {
                if (e.code != 404) {
                    throw e;
                }
                client.mkdirs("");
                entries = client.list("");
            }
            return null == entries ? "" : String.valueOf(entries.size());
        } catch (IllegalArgumentException e) {
            return "!" + e.getMessage();
        } catch (WebDavClient.WebDavException e) {
            return "!" + (e.code == 401 ? "401 wrong user or password" : e.getMessage());
        } catch (IOException e) {
            return "!" + e.getMessage();
        }
    }

    /** The sync state of each folder, per server: another server starts from scratch. */
    static File stateFile(Context c, String folder) {
        final String server = Integer.toHexString((getUrl(c) + "|" + getUser(c)).hashCode());
        return new File(new File(c.getFilesDir(), "webdav"), server + "-" + folder + ".txt");
    }
}
