package com.foobnix.annotation;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The marks of each book, one file per book in the synced profile folder
 * ({@code profile.<name>/marks/<book file name>.json}): a JSON object {@code {id: mark}} that
 * WebDAV sync merges mark by mark (ProfileMerge), so marks two devices made both stay and a
 * deletion reaches every device.
 */
public class MarksStore {

    public static final String FOLDER = "marks";

    private static final Object LOCK = new Object();

    public interface Change {
        void apply(Map<String, Mark> marks);
    }

    private final File profile;

    MarksStore(File profile) {
        this.profile = profile;
    }

    /** The store of the current profile. */
    public static MarksStore get() {
        return new MarksStore(AppProfile.SYNC_FOLDER_PROFILE);
    }

    /** Where the marks of a book are kept, relative to the profile folder. */
    public static String profilePath(String bookName) {
        return FOLDER + "/" + AiStore.fileName(bookName);
    }

    /** Whether a profile file (relative path) holds the marks of a book. */
    public static boolean isMarksFile(String relativePath) {
        return relativePath != null && relativePath.replace('\\', '/').startsWith(FOLDER + "/")
                && relativePath.endsWith(".json");
    }

    private File file(String bookName) {
        return new File(new File(profile, FOLDER), AiStore.fileName(bookName));
    }

    /** The marks of a book by id, oldest first; empty when it has none or its file cannot be read. */
    public Map<String, Mark> load(String bookName) {
        synchronized (LOCK) {
            try {
                return read(file(bookName));
            } catch (Exception e) {
                // half written by a sync: nothing drawn until a good copy comes
                return new LinkedHashMap<>();
            }
        }
    }

    /**
     * Applies a change to the marks of a book and writes them.
     *
     * @throws IOException also when the file is there but cannot be read: writing would drop marks
     */
    public Map<String, Mark> update(String bookName, Change change) throws IOException {
        final Map<String, Mark> marks;
        synchronized (LOCK) {
            final File file = file(bookName);
            try {
                marks = read(file);
            } catch (JSONException e) {
                throw new IOException("unreadable " + file + ": " + e.getMessage());
            }
            change.apply(marks);
            final File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("cannot create " + folder);
            }
            final File temp = new File(folder, file.getName() + ".tmp");
            try {
                write(temp, toJson(marks).toString());
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("cannot replace " + file);
            }
            if (!temp.renameTo(file)) {
                throw new IOException("cannot write " + file);
            }
        }
        // sent with the page and bookmarks once reading settles
        com.foobnix.webdav.SyncEvents.readingChanged();
        return marks;
    }

    static Map<String, Mark> read(File file) throws IOException, JSONException {
        final Map<String, Mark> marks = new LinkedHashMap<>();
        if (!file.isFile()) {
            return marks;
        }
        final String text = readText(file);
        if (text.trim().isEmpty()) {
            return marks;
        }
        final JSONObject root = new JSONObject(text);
        final List<Mark> list = new ArrayList<>();
        final Iterator<String> ids = root.keys();
        while (ids.hasNext()) {
            final String id = ids.next();
            final Mark mark = Mark.fromJson(id, root.optJSONObject(id));
            if (mark != null) {
                list.add(mark);
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(a.created, b.created));
        for (Mark mark : list) {
            marks.put(mark.id, mark);
        }
        return marks;
    }

    static JSONObject toJson(Map<String, Mark> marks) throws JSONException {
        final JSONObject root = new JSONObject();
        for (Mark mark : marks.values()) {
            root.put(mark.id, mark.toJson());
        }
        return root;
    }

    private static String readText(File file) throws IOException {
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
