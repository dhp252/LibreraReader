package com.foobnix.claude;

import com.foobnix.ai.AiStore;

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
import java.util.List;

/**
 * Answers an outside Claude wrote for a book (see {@code ClaudeSkill}): files in the book's desk,
 * {@code profile.<name>/claude/books/<book>/answers/*.json}, each
 * {@code {"format", "feature", "book", "chapter", "by", "made", "answer"}} where {@code answer} is
 * the reply the app's own prompt asks the API for. A feature takes the answer of its chapter instead
 * of asking the API ({@code AiJobs.Job#askFor}), then parses, places and stores it as any answer.
 * <p>
 * Taken answers move to {@code answers/used/}; answers the app cannot use move to
 * {@code answers/rejected/} beside a {@code .reason.txt}, so Claude sees what happened. The desk is
 * in the synced profile folder: both moves reach the server with the next sync.
 */
public final class ClaudeAnswers {

    public static final String FOLDER = "claude";
    public static final String BOOKS = "books";
    public static final String ANSWERS = "answers";
    public static final String USED = "used";
    public static final String REJECTED = "rejected";
    public static final String REASON = ".reason.txt";
    /** The chapter of an answer about the whole book (a book summary, the skeleton). */
    public static final String BOOK = AiStore.BOOK;

    /** One answer file. */
    public static final class Answer {
        public final File file;
        public final String feature;
        public final String chapter;
        public final int format;
        /** The reply, as the API would have given it. */
        public final String text;
        /** Who wrote it ("Claude Code"), shown where the app shows the model. */
        public final String by;
        public final String made;

        Answer(File file, String feature, String chapter, int format, String text, String by, String made) {
            this.file = file;
            this.feature = feature;
            this.chapter = chapter;
            this.format = format;
            this.text = text;
            this.by = by;
            this.made = made;
        }

        /** What the app keeps as the answer's model. */
        public String model() {
            return by == null || by.trim().isEmpty() ? "Claude (WebDAV)" : by.trim() + " (WebDAV)";
        }
    }

    private static final Object LOCK = com.foobnix.webdav.FolderSync.LOCAL_FILES;

    private ClaudeAnswers() {
    }

    /** The desk of a book: {@code <profile>/claude/books/<book file name>/}. */
    public static File desk(File profile, String bookName) {
        return new File(new File(new File(profile, FOLDER), BOOKS), folderName(bookName));
    }

    /** The book's file name made safe as a folder name (as its {@code assist/}, {@code ai/} files are named). */
    public static String folderName(String bookName) {
        final String file = AiStore.fileName(bookName);
        return file.substring(0, file.length() - ".json".length());
    }

    /** Whether a profile path (relative) is in a desk: kept as the newest copy, never merged by item. */
    public static boolean isDeskFile(String relativePath) {
        return relativePath != null && relativePath.replace('\\', '/').startsWith(FOLDER + "/");
    }

    /**
     * The answer waiting for {@code feature} on {@code chapter}, or null. Answers of that feature
     * and chapter that cannot be used (another format, no text) are moved to rejected on the way,
     * as are older ones a newer answer replaces; answers of other features stay.
     */
    public static Answer find(File desk, String feature, String chapter, int format) {
        synchronized (LOCK) {
            final List<Answer> found = new ArrayList<>();
            for (File f : files(desk)) {
                final Answer a;
                try {
                    a = read(f);
                } catch (Exception e) {
                    reject(f, "Not an answer file: " + e.getMessage()
                            + ". Write one JSON object with format, feature, book, chapter and answer.");
                    continue;
                }
                if (!feature.equals(a.feature) || !chapter.equals(a.chapter)) {
                    continue;
                }
                if (a.format != format) {
                    reject(f, "Written for format " + a.format + " of \"" + feature + "\"; this app reads format "
                            + format + ". Read formats.json and the feature file again, then write it anew.");
                    continue;
                }
                if (a.text.trim().isEmpty()) {
                    reject(f, "The \"answer\" is empty.");
                    continue;
                }
                found.add(a);
            }
            Answer newest = null;
            for (Answer a : found) {
                if (newest == null || newer(a, newest)) {
                    newest = a;
                }
            }
            for (Answer a : found) {
                if (a != newest) {
                    reject(a.file, "A newer answer for the same chapter replaced it: " + newest.file.getName());
                }
            }
            return newest;
        }
    }

    /** Whether a usable answer (this format, not empty) for {@code feature} on {@code chapter} waits. */
    public static boolean has(File desk, String feature, String chapter, int format) {
        synchronized (LOCK) {
            for (File f : files(desk)) {
                try {
                    final Answer a = read(f);
                    if (feature.equals(a.feature) && chapter.equals(a.chapter) && usable(a, format)) {
                        return true;
                    }
                } catch (Exception e) {
                    // rejected when a feature looks for its answer
                }
            }
            return false;
        }
    }

    /** The features with an answer waiting, for any chapter in {@code chapters} (null: any chapter). */
    public static List<String> waiting(File desk, List<String> chapters) {
        final List<String> out = new ArrayList<>();
        synchronized (LOCK) {
            for (File f : files(desk)) {
                try {
                    final Answer a = read(f);
                    if ((chapters == null || chapters.contains(a.chapter)) && !out.contains(a.feature)) {
                        out.add(a.feature);
                    }
                } catch (Exception e) {
                    // not an answer
                }
            }
        }
        return out;
    }

    /** The chapters with a usable answer of {@code feature} waiting, in no set order. */
    public static List<String> chapters(File desk, String feature, int format) {
        final List<String> out = new ArrayList<>();
        synchronized (LOCK) {
            for (File f : files(desk)) {
                try {
                    final Answer a = read(f);
                    if (feature.equals(a.feature) && usable(a, format) && !out.contains(a.chapter)) {
                        out.add(a.chapter);
                    }
                } catch (Exception e) {
                    // not an answer
                }
            }
        }
        return out;
    }

    /** The answer was parsed and stored: it moves to used/. */
    public static void used(Answer a) {
        synchronized (LOCK) {
            move(a.file, new File(new File(a.file.getParentFile(), USED), a.file.getName()));
        }
    }

    /** The answer could not be used: it moves to rejected/, with the reason beside it. */
    public static void rejected(Answer a, String reason) {
        synchronized (LOCK) {
            reject(a.file, reason);
        }
    }

    private static boolean usable(Answer a, int format) {
        return a.format == format && !a.text.trim().isEmpty();
    }

    static Answer read(File f) throws IOException, JSONException {
        final JSONObject o = new JSONObject(text(f));
        // a reply the prompt asks as JSON may stand as the JSON itself
        final Object answer = o.opt("answer");
        final String text = answer instanceof JSONObject || answer instanceof org.json.JSONArray ? answer.toString()
                : answer == null || answer == JSONObject.NULL ? "" : String.valueOf(answer);
        return new Answer(f, o.optString("feature", ""), o.optString("chapter", ""), o.optInt("format", -1),
                text, o.optString("by", ""), o.optString("made", ""));
    }

    private static boolean newer(Answer a, Answer than) {
        final int byMade = a.made.compareTo(than.made);
        if (byMade != 0) {
            return byMade > 0;
        }
        final long byTime = a.file.lastModified() - than.file.lastModified();
        return byTime != 0 ? byTime > 0 : a.file.getName().compareTo(than.file.getName()) > 0;
    }

    private static List<File> files(File desk) {
        final List<File> out = new ArrayList<>();
        final File[] all = new File(desk, ANSWERS).listFiles();
        if (all != null) {
            for (File f : all) {
                if (f.isFile() && f.getName().endsWith(".json") && !f.getName().startsWith(".")) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    private static void reject(File f, String reason) {
        final File folder = new File(f.getParentFile(), REJECTED);
        final File to = new File(folder, f.getName());
        if (move(f, to)) {
            try {
                write(new File(folder, f.getName() + REASON), reason + "\n");
            } catch (IOException e) {
                // the answer is out of the way all the same
            }
        }
    }

    private static boolean move(File from, File to) {
        final File folder = to.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return false;
        }
        if (to.exists() && !to.delete()) {
            return false;
        }
        final boolean moved = from.renameTo(to);
        if (moved) {
            final Runnable r = com.foobnix.webdav.FolderSync.afterLocalWrite;
            if (r != null) {
                r.run();
            }
        }
        return moved;
    }

    static String text(File file) throws IOException {
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

    static void write(File file, String text) throws IOException {
        final File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("cannot create " + folder);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
