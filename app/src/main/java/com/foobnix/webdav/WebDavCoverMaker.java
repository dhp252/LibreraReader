package com.foobnix.webdav;

import android.content.Context;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;

import org.ebookdroid.BookType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Covers for server books that no device has had (put on the server from a computer, say), made
 * while the WebDAV tab shows them: one book at a time is downloaded to the cache, its cover
 * rendered and uploaded to {@code Books/.covers/} like the covers a sync makes, and the copy
 * deleted. Each book is fetched once for all devices, since the cover is shared. Books over
 * {@link #MAX_SIZE} keep the title tile. Design: .agents/planning/webdav-tab-design.md (D8-D11).
 */
public class WebDavCoverMaker {

    /** Larger books are not fetched just for a cover (owner's choice: 50 MB). */
    public static final long MAX_SIZE = 50L * 1024 * 1024;

    public interface Listener {
        /** On the maker's thread: {@code cover} is the new cover entry of the book at {@code book}. */
        void onCover(WebDavClient.Entry book, WebDavClient.Entry cover);
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
     * Makes the covers of {@code books} (server-only books without a cover, as shown), replacing
     * any run still going. Returns at once; the work runs on its own thread.
     */
    public void start(Context context, List<WebDavClient.Entry> books, Wanted wanted, Listener listener) {
        final Context c = context.getApplicationContext();
        final int run;
        synchronized (lock) {
            run = ++generation;
        }
        final Wanted current = () -> isCurrent(run) && wanted.stillWanted();
        new Thread(() -> make(c, books, current, listener), "@T WebDAV covers").start();
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

    /** The books worth fetching for a cover, in the order shown. */
    static List<WebDavClient.Entry> todo(List<WebDavClient.Entry> books, Set<String> failed, Supported supported) {
        final List<WebDavClient.Entry> todo = new ArrayList<>();
        for (WebDavClient.Entry book : books) {
            if (book.directory || book.size <= 0 || book.size > MAX_SIZE) {
                continue;
            }
            if (!supported.isBook(book.getName()) || failed.contains(key(book))) {
                continue;
            }
            todo.add(book);
        }
        return todo;
    }

    private static synchronized void make(Context c, List<WebDavClient.Entry> books, Wanted wanted, Listener listener) {
        // synchronized: a run replaced while it downloads finishes its step before the next starts
        if (!wanted.stillWanted()) {
            return;
        }
        final File failedFile = WebDav.stateFile(c, "covers-remote-failed", AppProfile.SYNC_FOLDER_BOOKS);
        final Set<String> failed = WebDavCovers.readLines(failedFile);
        final File folder = new File(c.getCacheDir(), "webdav-cover-books");
        final WebDavClient client = WebDavCovers.sharedClient(c);

        for (WebDavClient.Entry book : todo(books, failed, BookType::isSupportedExtByPath)) {
            if (!wanted.stillWanted()) {
                return;
            }
            final String cover = WebDavCovers.coverOf(book.path);
            if (cover == null) {
                continue;
            }
            // the real name: some formats are told apart by their extension
            final File copy = new File(folder, book.getName());
            final File jpg = new File(folder, "cover.jpg");
            try {
                client.download(book.path, copy, () -> !wanted.stillWanted());
                // marked before rendering: a book that crashes the codec is not fetched again
                failed.add(key(book));
                WebDavCovers.writeLines(failedFile, failed);
                if (!WebDavCovers.render(c, copy, jpg)) {
                    LOG.d("WebDAV cover not made", book.path);
                    continue;
                }
                client.upload(jpg, cover);
                failed.remove(key(book));
                WebDavCovers.writeLines(failedFile, failed);
                final WebDavClient.Entry made = client.stat(cover);
                if (made != null) {
                    listener.onCover(book, made);
                }
            } catch (IOException e) {
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
            }
        }
    }
}
