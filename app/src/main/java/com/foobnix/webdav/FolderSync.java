package com.foobnix.webdav;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Two-way sync of a local folder with a folder on a WebDAV server.
 * <p>
 * WebDAV has no trash and no change feed, so the state after the last successful sync is kept on
 * the device (one record per file: local modified time and size, remote ETag and size). Comparing
 * both sides with it tells apart changed here, changed there, deleted here, deleted there and new
 * on either side. A file changed on both sides goes to the newer one.
 * <p>
 * {@link Mode#ON_DEMAND} is for books: files that are only on the server are listed but not
 * downloaded, and removing a local copy only frees the space instead of deleting the server file.
 */
public class FolderSync {

    public enum Mode {
        /** Everything is mirrored both ways, including deletions. */
        MIRROR,
        /** Server-only files stay on the server; a removed local copy does not delete them. */
        ON_DEMAND
    }

    /** What the sync needs from the server; implemented by {@link WebDavClient}. */
    public interface Remote {
        List<WebDavClient.Entry> listRecursive(String folder) throws IOException;

        WebDavClient.Entry stat(String path) throws IOException;

        void download(String path, File target) throws IOException;

        /** {@code ifMatch} of {@link #upload}: there must be no file at the path yet. */
        String NO_FILE = "";

        /**
         * @param ifMatch the ETag the server copy must still have, {@link #NO_FILE} when there must be
         *                none, null for no condition; a server copy changed in between is a
         *                {@link WebDavClient.WebDavException} with {@link WebDavClient#PRECONDITION_FAILED}
         */
        void upload(File source, String path, String ifMatch) throws IOException;

        void delete(String path) throws IOException;
    }

    /** Which files take part, by path relative to the synced folder ("profile.Librera/Pixel 8/app-Progress.json"). */
    public interface Filter {
        boolean accept(String relativePath);
    }

    /**
     * Files merged item by item when both sides changed them, instead of the newer file winning
     * (the profile's lists, {@link ProfileMerge}). A copy of each such file as it stood after the
     * last sync is kept next to the state file: the merge compares both sides with it.
     */
    public interface Merger {
        boolean handles(String relativePath);

        /** @param base the file after the last sync, null when there is no copy */
        String merge(String relativePath, String base, String local, String remote) throws Exception;
    }

    /** State of one file as of the last sync. */
    static class Record {
        long localModified;
        long localSize;
        String remoteTag;
        long remoteSize;

        Record(long localModified, long localSize, String remoteTag, long remoteSize) {
            this.localModified = localModified;
            this.localSize = localSize;
            this.remoteTag = remoteTag;
            this.remoteSize = remoteSize;
        }
    }

    public static class Result {
        public int uploaded;
        public int downloaded;
        public int deletedLocal;
        public int deletedRemote;
        public int conflicts;
        /** Files missing here all at once, not deleted on the server but copied back. */
        public int keptOnServer;
        /** Files missing on the server all at once, not deleted here but copied back. */
        public int keptHere;
        /** The files this run changed on the device (downloaded or merged). */
        public final Set<String> changedHere = new TreeSet<>();
        public final List<String> errors = new ArrayList<>();

        public boolean changedLocal() {
            return downloaded > 0 || deletedLocal > 0;
        }

        @Override
        public String toString() {
            return "up " + uploaded + ", down " + downloaded + ", deleted here " + deletedLocal
                    + ", deleted there " + deletedRemote + ", conflicts " + conflicts
                    + (keptOnServer + keptHere > 0 ? ", kept from mass deletion " + (keptOnServer + keptHere) : "")
                    + (errors.isEmpty() ? "" : ", errors " + errors.size());
        }
    }

    /** More deletions than this in one run, and more than half of what was synced, are not trusted. */
    static final int SAFE_DELETES = 10;

    /**
     * Held while a file merged by item ({@link Merger}) is read or replaced on the device, by the
     * sync and by the stores that write such files (MarksStore, StudyStore): neither overwrites a
     * change the other has just made (a mark erased while a sync merged the file came back).
     */
    public static final Object LOCAL_FILES = new Object();

    /**
     * Puts {@code temp} in the place of {@code file}: in one step where the system can, so a sync
     * listing the folder never finds the file missing (and takes it for deleted).
     */
    public static void replace(File temp, File file) throws IOException {
        if (temp.renameTo(file)) {
            return;
        }
        // systems that cannot rename over a file (Windows, in the JVM tests)
        if (file.exists() && !file.delete()) {
            throw new IOException("cannot replace " + file);
        }
        if (!temp.renameTo(file)) {
            throw new IOException("cannot write " + file);
        }
    }

    private final Remote remote;
    private final File localRoot;
    private final String remoteRoot;
    private final File stateFile;
    private final Mode mode;
    private final Filter filter;
    /** The server files (relative paths) as listed by the last {@link #sync()}, null before one. */
    private Set<String> lastRemote;
    private Merger merger;

    /**
     * @param remoteRoot folder on the server relative to the client root, "" for the root itself
     * @param stateFile  where the state of the last sync is kept; must be outside {@code localRoot}
     */
    public FolderSync(Remote remote, File localRoot, String remoteRoot, File stateFile, Mode mode, Filter filter) {
        this.remote = remote;
        this.localRoot = localRoot;
        this.remoteRoot = WebDavClient.trimSlashes(remoteRoot);
        this.stateFile = stateFile;
        this.mode = mode;
        this.filter = filter;
    }

    /** Merges the files {@code merger} handles item by item when both sides changed them. */
    public FolderSync withMerger(Merger merger) {
        this.merger = merger;
        return this;
    }

    public Result sync() throws IOException {
        final Map<String, Record> state = loadState();
        final Map<String, WebDavClient.Entry> remoteFiles = new HashMap<>();
        List<WebDavClient.Entry> listed;
        boolean remoteMissing = false;
        try {
            listed = remote.listRecursive(remoteRoot);
        } catch (WebDavClient.WebDavException e) {
            if (e.code != 404) {
                throw e;
            }
            // first sync: the folder on the server is created by the first upload
            listed = new ArrayList<>();
            remoteMissing = true;
        }
        for (WebDavClient.Entry entry : listed) {
            if (entry.directory) {
                continue;
            }
            final String relative = relativeToRoot(entry.path);
            if (relative != null && accept(relative)) {
                remoteFiles.put(relative, entry);
            }
        }
        lastRemote = remoteMissing ? null : new TreeSet<>(remoteFiles.keySet());
        final Map<String, File> localFiles = listLocal();

        final TreeSet<String> paths = new TreeSet<>();
        paths.addAll(remoteFiles.keySet());
        paths.addAll(localFiles.keySet());
        for (String path : state.keySet()) {
            if (accept(path)) {
                paths.add(path);
            }
        }

        final Result result = new Result();
        keepFromMassDeletion(state, localFiles, remoteFiles, remoteMissing, result);
        for (String path : paths) {
            try {
                syncOneRetrying(path, localFiles.get(path), remoteFiles.get(path), state, result, true);
            } catch (IOException e) {
                // one bad file must not stop the others; it is retried next time
                result.errors.add(path + ": " + e.getMessage());
            }
            saveState(state);
        }
        return result;
    }

    /**
     * The files changed on this device since the last sync (or never synced), found from the saved
     * state alone: nothing is asked of the server. Files deleted here are not listed.
     */
    public Set<String> dirtyPaths() throws IOException {
        final Map<String, Record> state = loadState();
        final Set<String> dirty = new TreeSet<>();
        for (Map.Entry<String, File> e : listLocal().entrySet()) {
            final Record last = state.get(e.getKey());
            final File local = e.getValue();
            if (last == null || local.lastModified() != last.localModified || local.length() != last.localSize) {
                dirty.add(e.getKey());
            }
        }
        return dirty;
    }

    /**
     * Syncs these files alone (paths relative to the synced folder), each asked of the server on
     * its own instead of listing the folder: a few small requests for the files an event touched.
     * Nothing is deleted on either side: a file missing on one side is copied there or left for
     * the next full {@link #sync()}, whose guard against mass deletion decides
     * (.agents/planning/event-sync-design.md, E8).
     */
    public Result syncPaths(Collection<String> paths) throws IOException {
        final Map<String, Record> state = loadState();
        final Result result = new Result();
        for (String path : new TreeSet<>(paths)) {
            if (!accept(path)) {
                continue;
            }
            try {
                final File file = new File(localRoot, path);
                final WebDavClient.Entry remoteEntry = remote.stat(remotePath(path));
                syncOneRetrying(path, file.isFile() ? file : null, remoteEntry, state, result, false);
            } catch (IOException e) {
                result.errors.add(path + ": " + e.getMessage());
            }
            saveState(state);
        }
        return result;
    }

    /** The server copy changed between reading it and uploading over it. */
    private static final class ChangedMeanwhile extends IOException {
        final WebDavClient.Entry now;

        ChangedMeanwhile(String path, WebDavClient.Entry now) {
            super(path + " changed on the server meanwhile");
            this.now = now;
        }
    }

    /**
     * {@link #syncOne}; when an upload finds the server copy changed meanwhile (another device
     * wrote it), once more with that copy: now both sides changed, so the lists are merged.
     */
    private void syncOneRetrying(String path, File local, WebDavClient.Entry remoteEntry, Map<String, Record> state,
                                 Result result, boolean deletes) throws IOException {
        try {
            syncOne(path, local, remoteEntry, state, result, deletes);
        } catch (ChangedMeanwhile e) {
            final File file = new File(localRoot, path);
            syncOne(path, file.isFile() ? file : null, e.now, state, result, deletes);
        }
    }

    /**
     * A file synced before and now missing on one side is deleted on the other, except when most
     * of what was synced is missing on that side at once, or all of it: that is not the reader
     * deleting files, it is a fresh install, a moved or cleared Librera folder, a folder emptied on
     * the server. Those files are then treated as never synced, so they are copied back instead.
     */
    private void keepFromMassDeletion(Map<String, Record> state, Map<String, File> localFiles,
                                      Map<String, WebDavClient.Entry> remoteFiles, boolean remoteMissing, Result result) {
        if (state.isEmpty()) {
            return;
        }
        final List<String> goneHere = new ArrayList<>();
        final List<String> goneThere = new ArrayList<>();
        for (Map.Entry<String, Record> e : state.entrySet()) {
            final String path = e.getKey();
            if (!accept(path)) {
                continue;
            }
            final File local = localFiles.get(path);
            final WebDavClient.Entry remoteEntry = remoteFiles.get(path);
            if (local == null && remoteEntry != null && tag(remoteEntry).equals(e.getValue().remoteTag)) {
                goneHere.add(path);
            } else if (remoteEntry == null && local != null
                    && local.lastModified() == e.getValue().localModified && local.length() == e.getValue().localSize) {
                goneThere.add(path);
            }
        }
        // in ON_DEMAND mode a copy missing here is only a download removed: the server keeps it anyway
        if (mode == Mode.MIRROR && (localFiles.isEmpty() || isMass(goneHere.size(), state.size()))) {
            for (String path : goneHere) {
                state.remove(path);
            }
            result.keptOnServer += goneHere.size();
        }
        if (remoteMissing || isMass(goneThere.size(), state.size())) {
            for (String path : goneThere) {
                state.remove(path);
            }
            result.keptHere += goneThere.size();
        }
    }

    private static boolean isMass(int gone, int synced) {
        return gone > SAFE_DELETES && gone * 2 > synced;
    }

    /** @param deletes false: a file missing on one side is never taken for a deletion (targeted runs) */
    private void syncOne(String path, File local, WebDavClient.Entry remoteEntry, Map<String, Record> state,
                         Result result, boolean deletes) throws IOException {
        final Record last = state.get(path);
        final boolean localChanged = local != null
                && (last == null || local.lastModified() != last.localModified || local.length() != last.localSize);
        final boolean remoteChanged = remoteEntry != null
                && (last == null || !tag(remoteEntry).equals(last.remoteTag));

        if (local != null && remoteEntry != null) {
            if (last == null) {
                // on both sides, never synced: equal size counts as the same file, else the newer wins
                if (merges(path)) {
                    // two devices' lists before their first sync together: joined, nothing dropped
                    // (an equal size says nothing about two lists)
                    merge(path, local, remoteEntry, state, result);
                } else if (local.length() == remoteEntry.size) {
                    state.put(path, record(local, remoteEntry));
                } else if (remoteEntry.lastModified > local.lastModified()) {
                    download(path, remoteEntry, state, result);
                } else {
                    upload(path, local, remoteEntry, state, result);
                }
            } else if (localChanged && remoteChanged) {
                result.conflicts++;
                if (merges(path)) {
                    merge(path, local, remoteEntry, state, result);
                } else if (remoteEntry.lastModified > local.lastModified()) {
                    download(path, remoteEntry, state, result);
                } else {
                    upload(path, local, remoteEntry, state, result);
                }
            } else if (localChanged) {
                upload(path, local, remoteEntry, state, result);
            } else if (remoteChanged) {
                download(path, remoteEntry, state, result);
            }
        } else if (local != null) {
            if (last != null && !localChanged) {
                if (!deletes) {
                    return;
                }
                // synced before and gone from the server: deleted on another device
                if (!local.delete() && local.exists()) {
                    throw new IOException("cannot delete " + local);
                }
                state.remove(path);
                dropBase(path);
                result.deletedLocal++;
            } else {
                upload(path, local, null, state, result);
            }
        } else if (remoteEntry != null) {
            if (last != null && !remoteChanged) {
                if (!deletes) {
                    return;
                }
                if (mode == Mode.ON_DEMAND) {
                    // only the downloaded copy was removed here: the server keeps the file
                    state.remove(path);
                } else {
                    remote.delete(remotePath(path));
                    state.remove(path);
                    dropBase(path);
                    result.deletedRemote++;
                }
            } else if (mode == Mode.MIRROR) {
                download(path, remoteEntry, state, result);
            }
        } else {
            state.remove(path);
        }
    }

    private void download(String path, WebDavClient.Entry remoteEntry, Map<String, Record> state, Result result) throws IOException {
        final File target = new File(localRoot, path);
        if (merges(path)) {
            downloadMerged(path, target, remoteEntry, state, result);
            return;
        }
        remote.download(remotePath(path), target);
        if (remoteEntry.lastModified > 0) {
            // not all Android storage keeps it; the record below uses whatever stuck
            target.setLastModified(remoteEntry.lastModified);
        }
        state.put(path, record(target, remoteEntry));
        keepBase(path, target);
        result.downloaded++;
        result.changedHere.add(path);
    }

    /**
     * The server's copy of a file merged by item replaces this one, unless this device wrote the
     * file since the sync looked at it (a mark made or erased meanwhile): then both are merged
     * and the next sync uploads the result.
     */
    private void downloadMerged(String path, File target, WebDavClient.Entry remoteEntry, Map<String, Record> state,
                                Result result) throws IOException {
        final Record last = state.get(path);
        final File theirs = new File(baseFolder(), path + ".remote.tmp");
        theirs.getParentFile().mkdirs();
        try {
            remote.download(remotePath(path), theirs);
            final String remoteText = read(theirs);
            synchronized (LOCAL_FILES) {
                final boolean writtenMeanwhile = target.isFile() && (last == null
                        || target.lastModified() != last.localModified || target.length() != last.localSize);
                String merged = null;
                if (writtenMeanwhile) {
                    final File base = baseOf(path);
                    try {
                        merged = merger.merge(path, base.isFile() ? read(base) : null, read(target), remoteText);
                    } catch (Exception e) {
                        // damaged here: the server's copy is taken
                        merged = null;
                    }
                }
                writeAtomically(target, merged != null ? merged : remoteText);
                if (merged != null) {
                    // the server has its copy; this one differs from it and is uploaded next time
                    state.put(path, new Record(-1, -1, tag(remoteEntry), remoteEntry.size));
                } else {
                    if (remoteEntry.lastModified > 0) {
                        target.setLastModified(remoteEntry.lastModified);
                    }
                    state.put(path, record(target, remoteEntry));
                }
                writeAtomically(baseOf(path), remoteText);
            }
        } finally {
            theirs.delete();
        }
        result.downloaded++;
        result.changedHere.add(path);
    }

    /**
     * Uploads over the server copy {@code known} (null: none) only if it is still there unchanged:
     * a device writing the file in between is not overwritten ({@link ChangedMeanwhile}).
     */
    private void upload(String path, File local, WebDavClient.Entry known, Map<String, Record> state, Result result) throws IOException {
        if (!merges(path)) {
            send(path, local, known);
            state.put(path, record(local, uploadedEntry(path, local)));
            result.uploaded++;
            return;
        }
        // a file merged by item goes up as it is now: a change written during the upload is not
        // taken for sent (it would never reach the server, and another device's copy would
        // bring back what it changed)
        final File sent = new File(baseFolder(), path + ".upload.tmp");
        sent.getParentFile().mkdirs();
        final long modified;
        final long size;
        try {
            synchronized (LOCAL_FILES) {
                modified = local.lastModified();
                size = local.length();
                writeAtomically(sent, read(local));
            }
            send(path, sent, known);
            final WebDavClient.Entry uploaded = uploadedEntry(path, sent);
            state.put(path, new Record(modified, size, tag(uploaded), uploaded.size));
            writeAtomically(baseOf(path), read(sent));
        } finally {
            sent.delete();
        }
        result.uploaded++;
    }

    private void send(String path, File source, WebDavClient.Entry known) throws IOException {
        try {
            remote.upload(source, remotePath(path), ifMatch(known));
        } catch (WebDavClient.WebDavException e) {
            if (e.code != WebDavClient.PRECONDITION_FAILED) {
                throw e;
            }
            final WebDavClient.Entry now = remote.stat(remotePath(path));
            if (!sameFile(now, known)) {
                throw new ChangedMeanwhile(path, now);
            }
            // the copy is the one we saw, yet the server refused: it cannot compare its own tags
            remote.upload(source, remotePath(path), null);
        }
    }

    private WebDavClient.Entry uploadedEntry(String path, File source) throws IOException {
        final WebDavClient.Entry uploaded = remote.stat(remotePath(path));
        return uploaded != null ? uploaded : new WebDavClient.Entry(remotePath(path), false, source.length(), 0, null);
    }

    /** The condition for an upload over {@code known}: weak or missing ETags cannot be matched. */
    static String ifMatch(WebDavClient.Entry known) {
        if (known == null) {
            return Remote.NO_FILE;
        }
        if (known.etag == null || known.etag.startsWith("W/")) {
            return null;
        }
        return known.etag;
    }

    private static boolean sameFile(WebDavClient.Entry a, WebDavClient.Entry b) {
        if (a == null || b == null) {
            return a == b;
        }
        return tag(a).equals(tag(b));
    }

    private boolean merges(String path) {
        return merger != null && merger.handles(path);
    }

    /**
     * Both sides changed a file merged by item: the server's copy is merged with this one against
     * the copy kept after the last sync, written here and uploaded. A file that cannot be merged
     * (damaged JSON) falls back to the newer one winning.
     */
    private void merge(String path, File local, WebDavClient.Entry remoteEntry, Map<String, Record> state, Result result) throws IOException {
        final File theirs = new File(baseFolder(), path + ".remote.tmp");
        theirs.getParentFile().mkdirs();
        try {
            remote.download(remotePath(path), theirs);
            final File base = baseOf(path);
            // read and written in one go: a mark erased in between is not undone by the merge
            synchronized (LOCAL_FILES) {
                final String merged = merger.merge(path, base.isFile() ? read(base) : null, read(local), read(theirs));
                if (merged == null) {
                    throw new IOException("not merged");
                }
                writeAtomically(local, merged);
            }
        } catch (Exception e) {
            if (remoteEntry.lastModified > local.lastModified()) {
                download(path, remoteEntry, state, result);
            } else {
                upload(path, local, remoteEntry, state, result);
            }
            return;
        } finally {
            theirs.delete();
        }
        result.changedHere.add(path);
        upload(path, local, remoteEntry, state, result);
        result.downloaded++;
    }

    /** Where the copies of merged files after the last sync are kept: beside the state file. */
    private File baseFolder() {
        return new File(stateFile.getPath() + ".base");
    }

    private File baseOf(String path) {
        return new File(baseFolder(), path);
    }

    private void keepBase(String path, File file) throws IOException {
        if (!merges(path)) {
            return;
        }
        writeAtomically(baseOf(path), read(file));
    }

    private void dropBase(String path) {
        if (merges(path)) {
            baseOf(path).delete();
        }
    }

    private static void writeAtomically(File file, String text) throws IOException {
        final File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        // not the stores' "<name>.tmp": the two never share a half-written file
        final File temp = new File(file.getPath() + ".sync.tmp");
        try (OutputStream stream = new FileOutputStream(temp)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        }
        replace(temp, file);
    }

    /** Downloads one server file on request (books in {@link Mode#ON_DEMAND}) and starts tracking it. */
    public File downloadOnDemand(WebDavClient.Entry remoteEntry) throws IOException {
        final String relative = relativeToRoot(remoteEntry.path);
        if (relative == null) {
            throw new IOException("not below " + remoteRoot + ": " + remoteEntry.path);
        }
        final Map<String, Record> state = loadState();
        final Result ignored = new Result();
        download(relative, remoteEntry, state, ignored);
        saveState(state);
        return new File(localRoot, relative);
    }

    /**
     * The server files as the last {@link #sync()} listed them, relative to the synced folder;
     * null before a sync or when the folder was missing on the server.
     */
    public Set<String> getLastRemote() {
        return lastRemote;
    }

    /** Where a server file lives (or would live) on the device. */
    public File localFileOf(WebDavClient.Entry remoteEntry) {
        final String relative = relativeToRoot(remoteEntry.path);
        return relative == null ? null : new File(localRoot, relative);
    }

    private boolean accept(String relativePath) {
        final String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        // temporary and hidden files (downloads in progress are ".name.part"), and anything in a
        // hidden folder (the shared book covers in Books/.covers)
        if (name.endsWith(".part") || name.endsWith(".tmp") || relativePath.startsWith(".") || relativePath.contains("/.")) {
            return false;
        }
        return filter == null || filter.accept(relativePath);
    }

    private String relativeToRoot(String serverPath) {
        final String path = WebDavClient.trimSlashes(serverPath);
        if (remoteRoot.isEmpty()) {
            return path;
        }
        if (path.startsWith(remoteRoot + "/")) {
            return path.substring(remoteRoot.length() + 1);
        }
        return null;
    }

    private String remotePath(String relative) {
        return remoteRoot.isEmpty() ? relative : remoteRoot + "/" + relative;
    }

    private Map<String, File> listLocal() {
        final Map<String, File> files = new HashMap<>();
        if (!localRoot.isDirectory()) {
            return files;
        }
        final String rootPath = localRoot.getAbsolutePath();
        final Deque<File> pending = new ArrayDeque<>();
        pending.add(localRoot);
        while (!pending.isEmpty()) {
            final File[] children = pending.poll().listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (child.isDirectory()) {
                    pending.add(child);
                    continue;
                }
                final String relative = child.getAbsolutePath().substring(rootPath.length() + 1).replace(File.separatorChar, '/');
                if (accept(relative)) {
                    files.put(relative, child);
                }
            }
        }
        return files;
    }

    /** ETag when the server has one, else modified time and size. */
    static String tag(WebDavClient.Entry entry) {
        return entry.etag != null ? entry.etag : entry.lastModified + ":" + entry.size;
    }

    private static Record record(File local, WebDavClient.Entry remoteEntry) {
        return new Record(local.lastModified(), local.length(), tag(remoteEntry), remoteEntry.size);
    }

    private static final String STATE_HEADER = "librera-webdav-sync 1";

    /** One line per file: path, local modified, local size, remote tag, remote size (tab separated). */
    Map<String, Record> loadState() throws IOException {
        final Map<String, Record> state = new HashMap<>();
        if (!stateFile.exists()) {
            return state;
        }
        final String[] lines = read(stateFile).split("\n");
        if (lines.length == 0 || !STATE_HEADER.equals(lines[0].trim())) {
            // unknown or damaged state only costs one full comparison
            return state;
        }
        for (int i = 1; i < lines.length; i++) {
            final String[] f = lines[i].split("\t", -1);
            if (f.length != 5) {
                continue;
            }
            try {
                state.put(unescape(f[0]), new Record(Long.parseLong(f[1]), Long.parseLong(f[2]), unescape(f[3]), Long.parseLong(f[4])));
            } catch (NumberFormatException ignored) {
                // skip the damaged line, the file is compared afresh
            }
        }
        return state;
    }

    void saveState(Map<String, Record> state) throws IOException {
        final StringBuilder out = new StringBuilder(STATE_HEADER).append('\n');
        for (Map.Entry<String, Record> e : state.entrySet()) {
            final Record r = e.getValue();
            out.append(escape(e.getKey())).append('\t')
               .append(r.localModified).append('\t')
               .append(r.localSize).append('\t')
               .append(escape(r.remoteTag)).append('\t')
               .append(r.remoteSize).append('\n');
        }
        final File parent = stateFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final File temp = new File(stateFile.getPath() + ".tmp");
        try (OutputStream stream = new FileOutputStream(temp)) {
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (stateFile.exists() && !stateFile.delete()) {
            throw new IOException("cannot replace " + stateFile);
        }
        if (!temp.renameTo(stateFile)) {
            throw new IOException("cannot write " + stateFile);
        }
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String value) {
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                final char next = value.charAt(++i);
                out.append(next == 't' ? '\t' : next == 'n' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                final int read = in.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }
}
