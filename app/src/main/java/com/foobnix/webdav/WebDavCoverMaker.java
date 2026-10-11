package com.foobnix.webdav;

import android.content.Context;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.sys.ImageExtractor;

import org.ebookdroid.BookType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Covers and metadata for server books that no device has had (put on the server from a
 * computer, say), made while the WebDAV tab shows them: one book at a time is downloaded to the
 * cache, its cover and/or metadata made and uploaded to {@code Books/.covers/} like those a sync
 * makes, and the copy deleted. Each book is fetched once for all devices, since both are shared.
 * Design: .agents/planning/webdav-tab-design.md (D8-D12).
 */
public class WebDavCoverMaker {

    /** A shown book and what it lacks. */
    public static final class Need {
        final WebDavClient.Entry book;
        final boolean cover;
        final boolean meta;

        public Need(WebDavClient.Entry book, boolean cover, boolean meta) {
            this.book = book;
            this.cover = cover;
            this.meta = meta;
        }
    }

    public interface Listener {
        /**
         * On the maker's thread: {@code cover} is the new cover entry, {@code meta} the new
         * metadata JSON; either is null when it was not made.
         */
        void onMade(WebDavClient.Entry book, WebDavClient.Entry cover, String meta);
    }

    /** Asked before each book and while downloading: false stops the run. */
    public interface Wanted {
        boolean stillWanted();
    }

    interface Supported {
        boolean isBook(String name);
    }

    private final Object lock = new Object();
    private int generation;

    /**
     * Makes what {@code needs} lack, replacing any run still going. Returns at once; the work
     * runs on its own thread.
     */
    public void start(Context context, List<Need> needs, Wanted wanted, Listener listener) {
        final Context c = context.getApplicationContext();
        final int run;
        synchronized (lock) {
            run = ++generation;
        }
        final Wanted current = () -> isCurrent(run) && wanted.stillWanted();
        new Thread(() -> make(c, needs, current, listener), "@T WebDAV covers").start();
    }

    /** Stops the run going on, after the step it is in. */
    public void stop() {
        synchronized (lock) {
            generation++;
        }
    }

    private boolean isCurrent(int run) {
        synchronized (lock) {
            return run == generation;
        }
    }

    /** The key a failed book is remembered by: a new version of the file is tried afresh. */
    static String key(WebDavClient.Entry book) {
        return book.path + "|" + FolderSync.tag(book);
    }

    /** The books worth fetching, in the order shown (any size: the owner's choice, D12). */
    static List<Need> todo(List<Need> needs, Set<String> failed, Supported supported) {
        final List<Need> todo = new ArrayList<>();
        for (Need need : needs) {
            if (need.book.directory || !(need.cover || need.meta)) {
                continue;
            }
            if (!supported.isBook(need.book.getName()) || failed.contains(key(need.book))) {
                continue;
            }
            todo.add(need);
        }
        return todo;
    }

    private static synchronized void make(Context c, List<Need> needs, Wanted wanted, Listener listener) {
        // synchronized: a run replaced while it downloads finishes its step before the next starts
        if (!wanted.stillWanted()) {
            return;
        }
        final File failedFile = WebDav.stateFile(c, "covers-remote-failed", AppProfile.SYNC_FOLDER_BOOKS);
        final Set<String> failed = WebDavCovers.readLines(failedFile);
        final File folder = new File(c.getCacheDir(), "webdav-cover-books");
        final WebDavClient client = WebDavCovers.sharedClient(c);

        for (Need need : todo(needs, failed, BookType::isSupportedExtByPath)) {
            if (!wanted.stillWanted()) {
                return;
            }
            final WebDavClient.Entry book = need.book;
            final String coverPath = WebDavCovers.coverOf(book.path);
            final String metaPath = WebDavBookMeta.metaOf(book.path);
            if (coverPath == null || metaPath == null) {
                continue;
            }
            // the real name: some formats are told apart by their extension
            final File copy = new File(folder, book.getName());
            final File jpg = new File(folder, "cover.jpg");
            final File json = new File(folder, "meta.json");
            try {
                client.download(book.path, copy, () -> !wanted.stillWanted());
                // marked before reading: a book that crashes the codec is not fetched again
                failed.add(key(book));
                WebDavCovers.writeLines(failedFile, failed);

                WebDavClient.Entry cover = null;
                if (need.cover && WebDavCovers.render(c, copy, jpg)) {
                    client.upload(jpg, coverPath);
                    cover = client.stat(coverPath);
                }
                String meta = null;
                if (need.meta) {
                    final FileMeta read = ImageExtractor.getInstance(c).metaForSharing(copy.getPath());
                    if (read != null) {
                        meta = WebDavBookMeta.toJson(read);
                        writeText(json, meta);
                        client.upload(json, metaPath);
                    }
                }
                if (cover == null && meta == null) {
                    LOG.d("WebDAV cover and metadata not made", book.path);
                    continue;
                }
                failed.remove(key(book));
                WebDavCovers.writeLines(failedFile, failed);
                listener.onMade(book, cover, meta);
            } catch (IOException | org.json.JSONException e) {
                // offline, server error or cancelled: nothing is remembered, the next view tries again
                LOG.d("WebDAV cover", book.path, e.getMessage());
                failed.remove(key(book));
                try {
                    WebDavCovers.writeLines(failedFile, failed);
                } catch (IOException ignored) {
                    // the list stays as it was on disk
                }
                return;
            } finally {
                copy.delete();
                jpg.delete();
                json.delete();
            }
        }
    }

    static void writeText(File file, String text) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
