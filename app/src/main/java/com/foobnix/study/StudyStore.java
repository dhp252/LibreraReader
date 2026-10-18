package com.foobnix.study;

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
 * Study data of each book, one file per book in the synced profile folder
 * ({@code profile.<name>/study/<book file name>.json}), merged chapter by chapter by WebDAV sync.
 * A change gives every object it touched a new {@code t}, so the merge keeps the newer one.
 */
public class StudyStore {

    public static final String FOLDER = "study";

    /** The sync's lock for merged files: a sync merging this file waits, and is waited for. */
    private static final Object LOCK = com.foobnix.webdav.FolderSync.LOCAL_FILES;

    public interface Change {
        void apply(StudyBook book);
    }

    private final File profile;

    StudyStore(File profile) {
        this.profile = profile;
    }

    /** The store of the current profile. */
    public static StudyStore get() {
        return new StudyStore(AppProfile.SYNC_FOLDER_PROFILE);
    }

    /** Where the study data of a book is kept, relative to the profile folder. */
    public static String profilePath(String bookName) {
        return FOLDER + "/" + AiStore.fileName(bookName);
    }

    /** Whether a profile file (relative path) holds the study data of a book. */
    public static boolean isStudyFile(String relativePath) {
        return relativePath != null && relativePath.replace('\\', '/').startsWith(FOLDER + "/")
                && relativePath.endsWith(".json");
    }

    private File file(String bookName) {
        return new File(new File(profile, FOLDER), AiStore.fileName(bookName));
    }

    /** The study data of a book; empty (not studied) when it has none or its file cannot be read. */
    public StudyBook load(String bookName) {
        synchronized (LOCK) {
            try {
                return StudyBook.fromJson(read(file(bookName)));
            } catch (Exception e) {
                // half written by a sync: as not studied until a good copy comes
                return new StudyBook();
            }
        }
    }

    /**
     * Applies a change to the study data of a book and writes it.
     *
     * @throws IOException also when the file is there but cannot be read: writing would drop answers
     */
    public StudyBook update(String bookName, Change change) throws IOException {
        final StudyBook book;
        synchronized (LOCK) {
            final File file = file(bookName);
            try {
                book = StudyBook.fromJson(read(file));
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
        // sent with the page and bookmarks once reading settles
        com.foobnix.webdav.SyncEvents.readingChanged();
        return book;
    }

    /** Each object's JSON without its time, to see what a change touched. */
    static Map<String, String> snapshot(StudyBook book) {
        final Map<String, String> out = new HashMap<>();
        try {
            out.put(StudyBook.BOOK, withoutTime(book.bookJson()));
            for (StudyBook.Chapter c : book.chapters.values()) {
                out.put(c.key, withoutTime(c.toJson()));
            }
        } catch (JSONException e) {
            // nothing to compare with: everything counts as changed
        }
        return out;
    }

    /** A new {@code t} for every object that differs from {@code before}: later than its old one. */
    static void stamp(StudyBook book, Map<String, String> before, long now) {
        try {
            if (!withoutTime(book.bookJson()).equals(before.get(StudyBook.BOOK))) {
                book.t = Math.max(now, book.t + 1);
            }
            for (StudyBook.Chapter c : book.chapters.values()) {
                if (!withoutTime(c.toJson()).equals(before.get(c.key))) {
                    c.t = Math.max(now, c.t + 1);
                }
            }
        } catch (JSONException e) {
            book.t = Math.max(now, book.t + 1);
        }
    }

    private static String withoutTime(JSONObject o) {
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
