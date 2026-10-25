package com.foobnix.ai;

import android.content.Context;

import org.json.JSONArray;
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
 * The part summaries of a whole-book summary being made (design-2026-10-25.md), kept on this
 * device only ({@code files/ai-work/<book>.json}): a run broken off goes on from the next part. Kept
 * under a key (layout, model, part size, part count): a run with other parts starts fresh.
 */
public final class AiParts {

    private final File file;
    private final String key;
    private final List<String> parts = new ArrayList<>();

    private AiParts(File file, String key) {
        this.file = file;
        this.key = key;
    }

    public static File folder(Context context) {
        return new File(context.getFilesDir(), "ai-work");
    }

    /** What the parts depend on: the same key means the same parts. */
    public static String key(AiJobs.Target t, String model, int maxChars, int count) {
        return t.width + "x" + t.height + "|" + t.fontSize + "|" + model + "|" + maxChars + "|" + count;
    }

    /** The parts kept for {@code book} under {@code key}; none when they were kept under another. */
    public static AiParts open(File folder, String book, String key) {
        final AiParts out = new AiParts(new File(folder, AiStore.fileName(book)), key);
        if (!out.file.isFile()) {
            return out;
        }
        try {
            final JSONObject o = new JSONObject(read(out.file));
            if (key.equals(o.optString("key"))) {
                final JSONArray a = o.optJSONArray("parts");
                for (int i = 0; a != null && i < a.length(); i++) {
                    out.parts.add(a.isNull(i) ? null : a.optString(i, null));
                }
            }
        } catch (Exception e) {
            // half written (the app killed while writing): as if none were kept
        }
        return out;
    }

    /** Part {@code i}'s summary, or null when it has to be made. */
    public String get(int i) {
        return i >= 0 && i < parts.size() ? parts.get(i) : null;
    }

    public void put(int i, String summary) throws IOException {
        while (parts.size() <= i) {
            parts.add(null);
        }
        parts.set(i, summary);
        final JSONObject o = new JSONObject();
        try {
            final JSONArray a = new JSONArray();
            for (String p : parts) {
                a.put(p == null ? JSONObject.NULL : p);
            }
            o.put("key", key);
            o.put("parts", a);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        final File folder = file.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("cannot create " + folder);
        }
        final File temp = new File(folder, file.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(file)) {
            file.delete();
            if (!temp.renameTo(file)) {
                throw new IOException("cannot write " + file);
            }
        }
    }

    /** The summary is made: its parts go. */
    public void clear() {
        parts.clear();
        file.delete();
    }

    /** The parts still to make, of {@code count}. */
    public List<Integer> missing(int count) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (get(i) == null) {
                out.add(i);
            }
        }
        return out;
    }

    private static String read(File file) throws IOException {
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
