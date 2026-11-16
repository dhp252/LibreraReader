package com.foobnix.assist;

import com.foobnix.ai.AiStore;
import com.foobnix.model.AppProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The data of the assisted-reading features, one file per book in the synced profile folder
 * ({@code profile.<name>/assist/<book file name>.json}), and one for the whole library
 * ({@link #LIBRARY}: the lens, the review queue). Nothing else in the profile changes, so deleting
 * the folder, or an older APK, leaves the app as it was.
 * <p>
 * A file is {@code {key: item}}; each item carries {@code t} (ms) and is merged item by item by
 * WebDAV sync, the newer {@code t} winning (like study and marks). Keys are
 * {@code <feature>:<id>}, e.g. {@code recap:last}, {@code preq:<chapter key>}. A removed item stays
 * as {@code {"del": true, "t": ...}} so the removal syncs too.
 */
public class AssistStore {

    public static final String FOLDER = "assist";
    /** The "book" name of the library-wide file. */
    public static final String LIBRARY = "@library";
    public static final String T = "t";
    public static final String DEL = "del";

    private static final Object LOCK = com.foobnix.webdav.FolderSync.LOCAL_FILES;

    private final File profile;

    AssistStore(File profile) {
        this.profile = profile;
    }

    /** The store of the current profile. */
    public static AssistStore get() {
        return new AssistStore(AppProfile.SYNC_FOLDER_PROFILE);
    }

    /** Where a book's assist data is kept, relative to the profile folder. */
    public static String profilePath(String bookName) {
        return FOLDER + "/" + AiStore.fileName(bookName);
    }

    /** Whether a profile file (relative path) holds assist data. */
    public static boolean isAssistFile(String relativePath) {
        return relativePath != null && relativePath.replace('\\', '/').startsWith(FOLDER + "/")
                && relativePath.endsWith(".json");
    }

    File file(String bookName) {
        return new File(new File(profile, FOLDER), AiStore.fileName(bookName));
    }

    /** One item (a copy), or null when there is none, it was removed, or the file cannot be read. */
    public JSONObject item(String bookName, String key) {
        synchronized (LOCK) {
            try {
                final JSONObject all = parse(read(file(bookName)));
                final JSONObject o = all.optJSONObject(key);
                return o == null || o.optBoolean(DEL) ? null : new JSONObject(o.toString());
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Every live item whose key starts with {@code prefix} (copies, in file order). */
    public Map<String, JSONObject> items(String bookName, String prefix) {
        final Map<String, JSONObject> out = new LinkedHashMap<>();
        synchronized (LOCK) {
            try {
                final JSONObject all = parse(read(file(bookName)));
                final Iterator<String> keys = all.keys();
                while (keys.hasNext()) {
                    final String k = keys.next();
                    final JSONObject o = all.optJSONObject(k);
                    if (o != null && !o.optBoolean(DEL) && (prefix == null || k.startsWith(prefix))) {
                        out.put(k, new JSONObject(o.toString()));
                    }
                }
            } catch (Exception e) {
                // half written by a sync: nothing until a good copy comes
            }
        }
        return out;
    }

    /** Writes an item, stamped with a new {@code t} later than the one it replaces. */
    public void put(String bookName, String key, JSONObject value) throws IOException {
        write(bookName, key, value, false);
    }

    /** Removes an item (kept as a removal so other devices remove it too). */
    public void remove(String bookName, String key) throws IOException {
        write(bookName, key, new JSONObject(), true);
    }

    private void write(String bookName, String key, JSONObject value, boolean del) throws IOException {
        synchronized (LOCK) {
            final File file = file(bookName);
            final JSONObject all;
            try {
                all = parse(read(file));
            } catch (JSONException e) {
                // writing would drop the other items
                throw new IOException("unreadable " + file + ": " + e.getMessage());
            }
            try {
                final JSONObject old = all.optJSONObject(key);
                final long oldT = old == null ? 0 : old.optLong(T);
                final JSONObject item = del ? new JSONObject().put(DEL, true) : new JSONObject(value.toString());
                item.put(T, Math.max(System.currentTimeMillis(), oldT + 1));
                all.put(key, item);
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            final File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("cannot create " + folder);
            }
            final File temp = new File(folder, file.getName() + ".tmp");
            try (OutputStream out = new FileOutputStream(temp)) {
                out.write(all.toString().getBytes(StandardCharsets.UTF_8));
            }
            com.foobnix.webdav.FolderSync.replace(temp, file);
        }
        com.foobnix.webdav.SyncEvents.readingChanged();
    }

    static JSONObject parse(String text) throws JSONException {
        return text == null || text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String read(File file) throws IOException {
        if (!file.isFile()) {
            return "";
        }
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
