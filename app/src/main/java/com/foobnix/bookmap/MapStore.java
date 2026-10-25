package com.foobnix.bookmap;

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
import java.util.HashMap;
import java.util.Map;

/**
 * The book map of each book, one file per book in the synced profile folder
 * ({@code profile.<name>/map/<book file name>.json}), merged part by part by WebDAV sync
 * ({@code ProfileMerge}). A change gives every part it touched a new {@code t}, so the merge
 * keeps the newer one.
 */
public class MapStore {

    public static final String FOLDER = "map";

    /** The sync's lock for merged files: a sync merging this file waits, and is waited for. */
    private static final Object LOCK = com.foobnix.webdav.FolderSync.LOCAL_FILES;

    public interface Change {
        void apply(MapBook book);
    }

    private final File profile;

    MapStore(File profile) {
        this.profile = profile;
    }

    /** The store of the current profile. */
    public static MapStore get() {
        return new MapStore(AppProfile.SYNC_FOLDER_PROFILE);
    }

    /** Where the map of a book is kept, relative to the profile folder. */
    public static String profilePath(String bookName) {
        return FOLDER + "/" + AiStore.fileName(bookName);
    }

    /** Whether a profile file (relative path) holds the map of a book. */
    public static boolean isMapFile(String relativePath) {
        return relativePath != null && relativePath.replace('\\', '/').startsWith(FOLDER + "/")
                && relativePath.endsWith(".json");
    }

    private File file(String bookName) {
        return new File(new File(profile, FOLDER), AiStore.fileName(bookName));
    }

    /** Whether the book has a map file with something in it (no parsing: for menus). */
    public boolean has(String bookName) {
        final File f = file(bookName);
        return f.isFile() && f.length() > 2;
    }

    /** The map of a book; empty when it has none or its file cannot be read. */
    public MapBook load(String bookName) {
        synchronized (LOCK) {
            try {
                return MapBook.fromJson(read(file(bookName)));
            } catch (Exception e) {
                // half written by a sync: as no map until a good copy comes
                return new MapBook();
            }
        }
    }

    /**
     * Applies a change to the map of a book and writes it.
     *
     * @throws IOException also when the file is there but cannot be read: writing would drop chapters
     */
    public MapBook update(String bookName, Change change) throws IOException {
        final MapBook book;
        synchronized (LOCK) {
            final File file = file(bookName);
            try {
                book = MapBook.fromJson(read(file));
            } catch (JSONException e) {
                throw new IOException("unreadable " + file + ": " + e.getMessage());
            }
            final Map<String, String> before = snapshot(book);
            change.apply(book);
            stamp(book, before, System.currentTimeMillis());

            final File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("cannot create " + folder);
            }
            final File temp = new File(folder, file.getName() + ".tmp");
            try {
                write(temp, book.toJson().toString());
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            // in one step: a sync listing the folder meanwhile must not take the file for deleted
            com.foobnix.webdav.FolderSync.replace(temp, file);
        }
        com.foobnix.webdav.SyncEvents.readingChanged();
        return book;
    }

    /** Each part's JSON without its time, to see what a change touched. */
    static Map<String, String> snapshot(MapBook book) {
        final Map<String, String> out = new HashMap<>();
        try {
            out.put(MapBook.BOOK, withoutTime(book.bookJson()));
            out.put(MapBook.VIEW, withoutTime(book.viewJson()));
            for (MapBook.Chapter c : book.chapters.values()) {
                out.put(c.key, withoutTime(c.toJson()));
            }
        } catch (JSONException e) {
            // nothing to compare with: everything counts as changed
        }
        return out;
    }

    /** A new {@code t} for every part that differs from {@code before}: later than its old one. */
    static void stamp(MapBook book, Map<String, String> before, long now) {
        try {
            if (!same(withoutTime(book.bookJson()), before.get(MapBook.BOOK))) {
                book.t = Math.max(now, book.t + 1);
            }
            if (!same(withoutTime(book.viewJson()), before.get(MapBook.VIEW))) {
                book.viewT = Math.max(now, book.viewT + 1);
            }
            for (MapBook.Chapter c : book.chapters.values()) {
                if (!same(withoutTime(c.toJson()), before.get(c.key))) {
                    c.t = Math.max(now, c.t + 1);
                }
            }
        } catch (JSONException e) {
            book.t = Math.max(now, book.t + 1);
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String withoutTime(JSONObject o) {
        if (o == null) {
            return null;
        }
        o.remove("t");
        return o.toString();
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

    private static void write(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
