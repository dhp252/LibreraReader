package com.foobnix.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.drive.GFile;
import com.foobnix.model.AppProfile;
import com.foobnix.model.ProfileMigration;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.work.WebDavSyncWorker;

import org.ebookdroid.common.settings.books.SharedBooks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * WebDAV sync: settings and runs.
 * <p>
 * The server keeps the layout of the local sync folder (Librera/): every {@code profile.*} folder
 * (progress, bookmarks, recent, favorites, tags, playlists, AI data, one set for all devices) is
 * mirrored both ways, its lists merged item by item when two devices changed them
 * ({@link ProfileMerge}, .agents/planning/unified-sync-design.md),
 * {@code Books/} on demand (uploaded from here, downloaded when opened from the WebDAV tab), with
 * their covers in {@code Books/.covers/} (see {@link WebDavCovers}). Settings shared by every device
 * (app, book view, AI) are merged in {@code profile.*}/.shared/ (see {@link SharedSettings}).
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
    private static final String KEY_MARKER_SEEN = "markerSeen";
    /**
     * In each profile folder on the server: rewritten by every device that sent something, so one
     * small request tells whether anything changed (.agents/planning/event-sync-design.md, E6).
     * Hidden: the profile mirror leaves it alone.
     */
    static final String MARKER = ".changed";
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

    /** WebDAV is on and a sync nobody asked for may run now ("Wi-Fi only" respected). */
    static boolean mayRunAuto(Context c) {
        return isEnabled(c) && !(BookCSS.get().isSyncWifiOnly && !Apps.isWifiEnabled(c));
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
            SyncEvents.fullQueued();
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

        // settings shared by every device (they live in a hidden folder the mirror below skips)
        SharedSettings.sync(c, client, summary);

        for (String profile : profiles) {
            deleteMigratedFolders(client, new File(root, profile), profile, summary);
            summary.add(profileSync(c, client, profile, null).sync(), profile);
        }
        final FolderSync books = books(c, client);
        summary.add(books.sync(), BOOKS);
        try {
            summary.add(WebDavCovers.sync(c, client, books), BOOKS);
        } catch (IOException e) {
            // covers are a nicety: the books and reading state are synced all the same
            summary.errors.append(WebDavCovers.COVERS).append(": ").append(e.getMessage()).append('\n');
        }

        try {
            if (summary.uploaded > 0) {
                touchMarker(c, client);
            } else {
                // everything is here now: what the marker says was seen
                seeMarker(c, client);
            }
        } catch (IOException e) {
            summary.errors.append(MARKER).append(": ").append(e.getMessage()).append('\n');
        }

        prefs(c).edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .putString(KEY_LAST_RESULT, summary.toString())
                .apply();
        return summary;
    }

    private static String currentProfile() {
        return AppProfile.PROFILE_PREFIX + AppProfile.getCurrent();
    }

    /**
     * Sends the files of the profile changed here since the last sync: found on the device, so
     * nothing at all is asked of the server when there are none.
     */
    static synchronized FolderSync.Result pushDirty(Context c) throws IOException {
        AppProfile.init(c);
        final WebDavClient client = client(c);
        final FolderSync profile = profileSync(c, client, currentProfile(), null);
        final Set<String> dirty = profile.dirtyPaths();
        // the reading log grows with every page: it goes when a reader closes (pushStats)
        dirty.removeIf(WebDav::isStats);
        if (dirty.isEmpty()) {
            return new FolderSync.Result();
        }
        final FolderSync.Result result = profile.syncPaths(dirty);
        if (result.uploaded > 0) {
            touchMarker(c, client);
        }
        log("push", result);
        return result;
    }

    /**
     * This device's reading log (F-007), changed files only. The change marker is not touched:
     * the other devices take the log at their next full sync instead of pulling at once.
     */
    static synchronized void pushStats(Context c) {
        try {
            AppProfile.init(c);
            final FolderSync profile = profileSync(c, client(c), currentProfile(), null);
            final Set<String> dirty = profile.dirtyPaths();
            dirty.removeIf(path -> !isStats(path));
            if (!dirty.isEmpty()) {
                log("stats", profile.syncPaths(dirty));
            }
        } catch (Exception e) {
            LOG.d("WebDav stats", e.getMessage());
        }
    }

    private static boolean isStats(String path) {
        return path.startsWith(com.foobnix.stats.StatsLog.FOLDER + "/");
    }

    /** The settings shared by every device, merged with the server's (three small files). */
    static synchronized void pushSettings(Context c) throws IOException {
        AppProfile.init(c);
        final WebDavClient client = client(c);
        final Summary summary = new Summary();
        SharedSettings.sync(c, client, summary);
        if (summary.uploaded > 0) {
            touchMarker(c, client);
        }
        if (summary.hasErrors()) {
            LOG.d("WebDav settings", summary.errors);
        }
    }

    /** The files a book is read with, relative to the profile folder: position, bookmarks, AI, marks, study. */
    static List<String> bookFiles(String bookPath) {
        return Arrays.asList(AppProfile.APP_PROGRESS_JSON, AppProfile.APP_BOOKMARKS_JSON,
                AiStore.profilePath(AiHighlights.bookName(bookPath)),
                com.foobnix.annotation.MarksStore.profilePath(AiHighlights.bookName(bookPath)),
                com.foobnix.study.StudyStore.profilePath(AiHighlights.bookName(bookPath)));
    }

    /**
     * Syncs the files of one book, both ways and nothing deleted: what another device did with
     * it comes here, what this one did goes up.
     */
    static synchronized FolderSync.Result pullBook(Context c, String bookPath) throws IOException {
        AppProfile.init(c);
        final WebDavClient client = client(c);
        final FolderSync.Result result = profileSync(c, client, currentProfile(), null).syncPaths(bookFiles(bookPath));
        if (result.uploaded > 0) {
            touchMarker(c, client);
        }
        log("book", result);
        return result;
    }

    /** True when another device sent something since this one last looked (one small request). */
    static synchronized boolean markerChanged(Context c) throws IOException {
        final WebDavClient.Entry entry = client(c).stat(currentProfile() + "/" + MARKER);
        final String tag = entry == null ? "" : FolderSync.tag(entry);
        return !tag.equals(prefs(c).getString(KEY_MARKER_SEEN, ""));
    }

    /** Tells the other devices this one sent something. */
    private static void touchMarker(Context c, WebDavClient client) throws IOException {
        final File temp = new File(c.getCacheDir(), "webdav" + MARKER);
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write((Build.MODEL + " " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        }
        try {
            client.upload(temp, currentProfile() + "/" + MARKER);
        } finally {
            temp.delete();
        }
        // a write by another device in this very moment is taken for ours: the 15 min sync catches it
        seeMarker(c, client);
    }

    private static void seeMarker(Context c, WebDavClient client) throws IOException {
        final WebDavClient.Entry entry = client.stat(currentProfile() + "/" + MARKER);
        prefs(c).edit().putString(KEY_MARKER_SEEN, entry == null ? "" : FolderSync.tag(entry)).apply();
    }

    /** Into the sync log the settings show, when something moved. */
    private static void log(String what, FolderSync.Result result) {
        if (result.uploaded + result.downloaded == 0 && result.errors.isEmpty()) {
            return;
        }
        synchronized (GFile.debugOut) {
            GFile.debugOut.append("\n WebDAV ").append(what).append(": ").append(result);
            for (String error : result.errors) {
                GFile.debugOut.append("\n  ").append(error);
            }
        }
    }

    /** The books on the server, mirrored on demand into Librera/Books. */
    public static FolderSync books(Context c, WebDavClient client) {
        return new FolderSync(client, AppProfile.SYNC_FOLDER_BOOKS, BOOKS, stateFile(c, BOOKS, AppProfile.SYNC_FOLDER_BOOKS), FolderSync.Mode.ON_DEMAND, null);
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

    private static FolderSync profileSync(Context c, WebDavClient client, String profile, FolderSync.Filter filter) {
        final File local = new File(AppProfile.SYNC_FOLDER_ROOT, profile);
        return new FolderSync(client, local, profile, stateFile(c, profile, local), FolderSync.Mode.MIRROR, filter)
                .withMerger(ProfileMerge.MERGER);
    }

    /**
     * The old per-device folders this device merged into the profile folder
     * ({@link ProfileMigration}): gone from the server too, so no device brings them back.
     */
    private static void deleteMigratedFolders(WebDavClient client, File local, String profile, Summary summary) {
        try {
            for (String name : ProfileMigration.pendingOnServer(local)) {
                client.deleteFolder(profile + "/" + name);
                ProfileMigration.deletedOnServer(local, name);
                summary.deletedRemote++;
            }
        } catch (IOException e) {
            summary.errors.append(profile).append(": ").append(e.getMessage()).append('\n');
        }
    }

    /** How long a book waits for its position from the server before it opens anyway. */
    private static final long OPEN_WAIT_MS = 3000;

    /**
     * Before a book opens: the shared reading positions alone are synced, so the book opens where
     * another device left it. Waits {@link #OPEN_WAIT_MS} at most; offline, Wi-Fi only without
     * Wi-Fi, or WebDAV off, it returns at once. Call from a background thread.
     */
    public static void refreshPositions(final Context c) {
        if (!isEnabled(c) || AppProfile.SYNC_FOLDER_ROOT == null
                || (BookCSS.get().isSyncWifiOnly && !Apps.isWifiEnabled(c))) {
            return;
        }
        final String profile = currentProfile();
        final Thread thread = new Thread(() -> {
            // one sync of a profile at a time: they share its state file
            synchronized (WebDav.class) {
                try {
                    final FolderSync.Result r = profileSync(c, client(c), profile, null)
                            .syncPaths(Collections.singleton(AppProfile.APP_PROGRESS_JSON));
                    if (r.changedLocal()) {
                        SharedBooks.preloadAll(true);
                    }
                } catch (Exception e) {
                    LOG.d("WebDav positions", e.getMessage());
                }
            }
        }, "@T webdav positions");
        thread.start();
        try {
            thread.join(OPEN_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The sync state of each folder, per server and local folder: another server, or the Librera
     * folder moved elsewhere, starts from scratch (copying, never deleting).
     */
    static File stateFile(Context c, String folder, File local) {
        final String key = getUrl(c) + "|" + getUser(c) + "|" + local.getAbsolutePath();
        return new File(new File(c.getFilesDir(), "webdav"), Integer.toHexString(key.hashCode()) + "-" + folder + ".txt");
    }
}
