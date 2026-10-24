package com.foobnix.eye;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A face trace for tuning Eye mode: one JSON line per analysed frame plus the reader's events, in
 * {@code files/eye/trace-<time>.jsonl} (no images, not backed up, never synced). The owner shares
 * the last one by hand from the Eye mode settings; the JVM tests replay them.
 */
final class TraceRecorder {

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "EyeTrace"));
    private BufferedWriter out;
    private int lines;

    static File folder(Context c) {
        return new File(c.getFilesDir(), "eye");
    }

    /** @return null when the file cannot be made */
    static TraceRecorder start(Context c) {
        final File folder = folder(c);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            LOG.d("TraceRecorder cannot create", folder);
            return null;
        }
        prune(folder, EyeRules.TRACES_KEPT - 1);
        final String name = "trace-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".jsonl";
        final TraceRecorder t = new TraceRecorder();
        try {
            t.out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(folder, name)), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.e(e);
            return null;
        }
        final JSONObject head = new JSONObject();
        try {
            head.put("ev", "start");
            head.put("v", 1);
            // gx / gy: the eyes' angles in the head put through the head's place and turn (D-133)
            head.put("gaze", "geometry");
            head.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            head.put("sdk", Build.VERSION.SDK_INT);
        } catch (JSONException e) {
            LOG.e(e);
        }
        t.write(head.toString());
        return t;
    }

    void frame(FaceFrame f, String state) {
        write(f.toJson(state));
    }

    /** A reader event at uptime {@code t}: {@code key=value} pairs after the name. */
    void event(long t, String name, Object... pairs) {
        final JSONObject o = new JSONObject();
        try {
            o.put("ev", name);
            o.put("t", t);
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                o.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        } catch (JSONException e) {
            LOG.e(e);
        }
        write(o.toString());
    }

    /** Writes out what is buffered, then runs {@code then} on the main thread. */
    void flush(Runnable then) {
        if (writer.isShutdown()) {
            then.run();
            return;
        }
        writer.execute(() -> {
            try {
                if (out != null) {
                    out.flush();
                }
            } catch (IOException e) {
                LOG.e(e);
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(then);
        });
    }

    void close() {
        writer.execute(() -> {
            try {
                if (out != null) {
                    out.close();
                    out = null;
                }
            } catch (IOException e) {
                LOG.e(e);
            }
        });
        writer.shutdown();
    }

    private void write(String line) {
        if (writer.isShutdown()) {
            return;
        }
        writer.execute(() -> {
            if (out == null) {
                return;
            }
            try {
                out.write(line);
                out.write('\n');
                if (++lines % 50 == 0) {
                    out.flush();
                }
            } catch (IOException e) {
                LOG.e(e);
            }
        });
    }

    /** The newest trace, null when there is none. */
    static File latest(Context c) {
        final File[] files = traces(folder(c));
        return files.length == 0 ? null : files[files.length - 1];
    }

    static void share(Activity a) {
        final File file = latest(a);
        if (file == null) {
            Toast.makeText(a, R.string.eye_no_trace, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final Uri uri = FileProvider.getUriForFile(a, Apps.getPackageName(a) + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri(file.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(intent, a.getString(R.string.share)));
        } catch (Exception e) {
            LOG.e(e);
            Toast.makeText(a, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static File[] traces(File folder) {
        final File[] files = folder.listFiles((dir, name) -> name.startsWith("trace-") && name.endsWith(".jsonl"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (x, y) -> x.getName().compareTo(y.getName()));
        return files;
    }

    private static void prune(File folder, int keep) {
        final File[] files = traces(folder);
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) {
                LOG.d("TraceRecorder cannot delete", files[i]);
            }
        }
    }
}
