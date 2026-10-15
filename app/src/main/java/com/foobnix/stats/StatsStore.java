package com.foobnix.stats;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the reading logs of every device ({@code <root>/<device>/YYYY-MM.jsonl}) into a
 * {@link StatsEngine}. A refresh reads only what was appended since the last one; a log that
 * shrank, vanished or changed before the part already read (cleared, replaced by a sync) makes
 * it start over from every file. Events before the time in {@link #CLEARED} are left out: "Clear
 * stats" writes it rather than deleting other devices' logs, which the sync's guard against
 * mass deletion would bring back.
 */
public class StatsStore {

    /** Holds the time of the last "Clear stats", in ms. */
    public static final String CLEARED = "cleared.txt";

    private final File root;
    private long cleared;
    private StatsEngine engine = new StatsEngine();
    /** Bytes of each file already read, by path relative to the root. */
    private final Map<String, Long> offsets = new HashMap<>();
    /** Per device, the last file read: an older file of the device growing means a rebuild. */
    private final Map<String, String> lastFile = new HashMap<>();
    /** Start of each file read: a file written anew (stats cleared) starts differently. */
    private final Map<String, String> heads = new HashMap<>();
    private int damagedLines;

    public StatsStore(File root) {
        this.root = root;
    }

    /**
     * Brings the summaries up to date with the files.
     *
     * @return true when anything new was read
     */
    public synchronized boolean refresh() {
        final List<String[]> files = list();
        final long clearedNow = readCleared(root);
        if (clearedNow != cleared || needsRebuild(files)) {
            reset();
            cleared = clearedNow;
        }
        boolean changed = false;
        for (String[] f : files) {
            final String device = f[0];
            final String path = f[0] + "/" + f[1];
            final File file = new File(root, path);
            final long from = offsets.getOrDefault(path, 0L);
            if (file.length() <= from) {
                continue;
            }
            try {
                final long read = feed(device, file, from);
                if (read > 0) {
                    offsets.put(path, from + read);
                    heads.putIfAbsent(path, head(file));
                    changed = true;
                }
            } catch (IOException e) {
                // left for the next refresh
                continue;
            }
            lastFile.put(device, f[1]);
        }
        return changed;
    }

    /** Forgets everything read: the next refresh reads every file from the start. */
    public synchronized void reset() {
        engine = new StatsEngine();
        offsets.clear();
        lastFile.clear();
        heads.clear();
        damagedLines = 0;
    }

    /** The given devices' summaries merged (all devices when null). */
    public synchronized StatsData data(Set<String> deviceIds) {
        return engine.merged(deviceIds);
    }

    /** Device id -> name, of every device with a log. */
    public synchronized Map<String, String> devices() {
        final Map<String, String> names = new HashMap<>();
        for (StatsData d : engine.perDevice().values()) {
            names.putAll(d.devices);
        }
        return names;
    }

    public synchronized int damagedLines() {
        return damagedLines;
    }

    /** [device, file name], devices and months in order. */
    private List<String[]> list() {
        final List<String[]> out = new ArrayList<>();
        final File[] devices = root.listFiles(File::isDirectory);
        if (devices == null) {
            return out;
        }
        Arrays.sort(devices);
        for (File device : devices) {
            final String[] names = device.list((dir, name) -> name.endsWith(".jsonl"));
            if (names == null) {
                continue;
            }
            Arrays.sort(names);
            for (String name : names) {
                out.add(new String[]{device.getName(), name});
            }
        }
        return out;
    }

    private boolean needsRebuild(List<String[]> files) {
        final Map<String, Long> present = new HashMap<>();
        for (String[] f : files) {
            present.put(f[0] + "/" + f[1], new File(root, f[0] + "/" + f[1]).length());
        }
        for (Map.Entry<String, Long> e : offsets.entrySet()) {
            final Long length = present.get(e.getKey());
            if (length == null || length < e.getValue()) {
                return true;
            }
            final String head = heads.get(e.getKey());
            if (head != null && !head.equals(head(new File(root, e.getKey())))) {
                return true;
            }
        }
        for (String[] f : files) {
            final String last = lastFile.get(f[0]);
            final String path = f[0] + "/" + f[1];
            // a month before the one a device is in got more lines
            if (last != null && f[1].compareTo(last) < 0 && present.get(path) > offsets.getOrDefault(path, 0L)) {
                return true;
            }
        }
        return false;
    }

    /** @return bytes consumed: whole lines only, a line still being written waits */
    private long feed(String device, File file, long from) throws IOException {
        final byte[] bytes = readFrom(file, from);
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] != '\n') {
            end--;
        }
        if (end == 0) {
            return 0;
        }
        final String text = new String(bytes, 0, end, StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            final StatsEvent e = StatsEvent.fromJson(line);
            if (e == null) {
                damagedLines++;
                continue;
            }
            if (e.ts < cleared && !StatsEvent.DEV.equals(e.type)) {
                continue;
            }
            engine.feed(device, e);
        }
        return end;
    }

    /** Time of the last "Clear stats", 0 when never. */
    public static long readCleared(File root) {
        final File file = new File(root, CLEARED);
        if (!file.isFile()) {
            return 0;
        }
        try {
            return Long.parseLong(new String(readFrom(file, 0), StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    public static void writeCleared(File root, long ts) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("no folder " + root);
        }
        try (java.io.OutputStream out = new java.io.FileOutputStream(new File(root, CLEARED))) {
            out.write(String.valueOf(ts).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The first line of a file (its device header, with the time it was started). */
    private static String head(File file) {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] buffer = new byte[256];
            final int n = in.read(buffer);
            if (n <= 0) {
                return "";
            }
            final String text = new String(buffer, 0, n, StandardCharsets.UTF_8);
            final int end = text.indexOf('\n');
            return end < 0 ? text : text.substring(0, end);
        } catch (IOException e) {
            return "";
        }
    }

    private static byte[] readFrom(File file, long from) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            long skipped = 0;
            while (skipped < from) {
                final long n = in.skip(from - skipped);
                if (n <= 0) {
                    break;
                }
                skipped += n;
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
