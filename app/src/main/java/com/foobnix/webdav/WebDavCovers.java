package com.foobnix.webdav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;
import com.foobnix.sys.ImageExtractor;

import org.ebookdroid.BookType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Book covers shared through the server, so a device can show the cover of a book it has not
 * downloaded: a device that has a book in Librera/Books uploads its cover once, as a small JPEG in
 * {@code Books/.covers/<book path>.jpg}, and the others fetch only that.
 * <p>
 * The WebDAV tab shows a server-only book as a {@code FileMeta} whose path is an item path,
 * {@code webdav:<cover tag>:<server path>}: the cover tag changes when the cover does, and Glide
 * caches covers by path, so a new cover is fetched and an unchanged one never again.
 */
public class WebDavCovers {

    public static final String PREFIX = "webdav:";
    static final String COVERS = WebDav.BOOKS + "/.covers";
    private static final String JPG = ".jpg";
    /** Cover tag of a folder item. */
    private static final String FOLDER = "d";
    /** Cover tag of a book with no cover on the server. */
    private static final String NONE = "0";
    /** Width of an uploaded cover; the height follows the cover (about 1.5 times). */
    static final int WIDTH = 240;

    private static WebDavClient client;
    private static String clientKey;

    /** "Books/a/b.pdf" -> "Books/.covers/a/b.pdf.jpg"; null for a path outside Books. */
    public static String coverOf(String bookPath) {
        final String relative = relativeToBooks(bookPath);
        return relative == null ? null : COVERS + "/" + relative + JPG;
    }

    /** Where the covers of a folder's books are: "Books/a" -> "Books/.covers/a". */
    public static String coversFolderOf(String folder) {
        final String relative = relativeToBooks(folder);
        return relative == null ? COVERS : COVERS + "/" + relative;
    }

    /** The cover file name of a book file name: "b.pdf" -> "b.pdf.jpg". */
    public static String coverNameOf(String bookName) {
        return bookName + JPG;
    }

    /** The book path below Books ("a/b.pdf") of a cover ("Books/.covers/a/b.pdf.jpg"), or null. */
    static String bookOfCover(String coverPath) {
        final String path = WebDavClient.trimSlashes(coverPath);
        if (!path.startsWith(COVERS + "/") || !path.endsWith(JPG)) {
            return null;
        }
        final String book = path.substring(COVERS.length() + 1, path.length() - JPG.length());
        return book.isEmpty() ? null : book;
    }

    /** "Books/a/b.pdf" -> "a/b.pdf"; null when not below Books. */
    static String relativeToBooks(String serverPath) {
        final String path = WebDavClient.trimSlashes(serverPath);
        return path.startsWith(WebDav.BOOKS + "/") && path.length() > WebDav.BOOKS.length() + 1
                ? path.substring(WebDav.BOOKS.length() + 1)
                : null;
    }

    /** Item path of a server book; {@code cover} is its cover entry, null when it has none. */
    public static String bookItem(String serverPath, WebDavClient.Entry cover) {
        final String tag = cover == null ? NONE : Integer.toHexString(FolderSync.tag(cover).hashCode());
        return PREFIX + tag + ":" + WebDavClient.trimSlashes(serverPath);
    }

    public static String folderItem(String serverPath) {
        return PREFIX + FOLDER + ":" + WebDavClient.trimSlashes(serverPath);
    }

    public static boolean isItem(String path) {
        return path != null && path.startsWith(PREFIX) && path.indexOf(':', PREFIX.length()) > 0;
    }

    public static boolean isFolderItem(String path) {
        return isItem(path) && path.startsWith(PREFIX + FOLDER + ":");
    }

    /** The server path of an item path. */
    public static String serverPathOf(String item) {
        return item.substring(item.indexOf(':', PREFIX.length()) + 1);
    }

    static boolean hasCover(String item) {
        return isItem(item) && !isFolderItem(item) && !item.startsWith(PREFIX + NONE + ":");
    }

    public static String nameOf(String item) {
        final String path = serverPathOf(item);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** What a sync does with the covers. Paths are book paths below Books ("a/b.pdf"). */
    static class Plan {
        /** Books on this device whose cover is not on the server. */
        final List<String> upload = new ArrayList<>();
        /** Covers whose book is gone from the server (and not here, waiting to be uploaded). */
        final List<String> delete = new ArrayList<>();
    }

    /**
     * @param remoteBooks the books the books sync just listed on the server; null when it could not
     *                    list them. Covers are only deleted against a listing that has books in it:
     *                    a missing or emptied folder must not cost every cover.
     */
    static Plan plan(Set<String> localBooks, Set<String> remoteBooks, Set<String> covers, Set<String> skip) {
        final Plan plan = new Plan();
        for (String book : localBooks) {
            if (!covers.contains(book) && !skip.contains(book)) {
                plan.upload.add(book);
            }
        }
        if (remoteBooks != null && !remoteBooks.isEmpty()) {
            for (String book : covers) {
                if (!remoteBooks.contains(book) && !localBooks.contains(book)) {
                    plan.delete.add(book);
                }
            }
        }
        return plan;
    }

    /**
     * The covers step of a sync, after the books themselves: uploads the missing covers of the
     * books on this device and deletes the covers of books gone from the server.
     */
    static FolderSync.Result sync(Context c, WebDavClient client, FolderSync books) throws IOException {
        final FolderSync.Result result = new FolderSync.Result();
        final TreeSet<String> covers = new TreeSet<>();
        try {
            for (WebDavClient.Entry entry : client.listRecursive(COVERS)) {
                final String book = entry.directory ? null : bookOfCover(entry.path);
                if (book != null) {
                    covers.add(book);
                }
            }
        } catch (WebDavClient.WebDavException e) {
            if (e.code != 404) {
                throw e;
            }
        }
        final File failedFile = WebDav.stateFile(c, "covers-failed", AppProfile.SYNC_FOLDER_BOOKS);
        final Set<String> local = localBooks(AppProfile.SYNC_FOLDER_BOOKS);
        final Set<String> failed = readLines(failedFile);
        // a book removed here, or replaced under another name, is tried afresh when it is back
        failed.retainAll(local);
        final Plan plan = plan(local, books.getLastRemote(), covers, failed);

        for (String book : plan.upload) {
            final File source = new File(AppProfile.SYNC_FOLDER_BOOKS, book);
            final File jpg = new File(c.getCacheDir(), "webdav-cover-upload.jpg");
            try {
                // marked first: a book that crashes the codec is not tried again on every sync
                failed.add(book);
                writeLines(failedFile, failed);
                if (!render(c, source, jpg)) {
                    continue;
                }
                client.upload(jpg, COVERS + "/" + book + JPG);
                failed.remove(book);
                writeLines(failedFile, failed);
                result.uploaded++;
            } catch (IOException e) {
                failed.remove(book);
                writeLines(failedFile, failed);
                result.errors.add(".covers/" + book + ": " + e.getMessage());
            } finally {
                jpg.delete();
            }
        }
        for (String book : plan.delete) {
            try {
                client.delete(COVERS + "/" + book + JPG);
                result.deletedRemote++;
            } catch (IOException e) {
                result.errors.add(".covers/" + book + ": " + e.getMessage());
            }
        }
        return result;
    }

    /** The cover of a local book as a JPEG; false when the book could not be opened. */
    private static boolean render(Context c, File book, File jpg) throws IOException {
        final Bitmap cover;
        try {
            cover = ImageExtractor.getInstance(c).coverForSharing(book.getPath(), WIDTH);
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }
        if (cover == null) {
            return false;
        }
        try (OutputStream out = new FileOutputStream(jpg)) {
            if (!cover.compress(Bitmap.CompressFormat.JPEG, 80, out)) {
                throw new IOException("cannot encode the cover of " + book.getName());
            }
        } finally {
            cover.recycle();
        }
        return true;
    }

    /** The books in Librera/Books, as paths below it. */
    static Set<String> localBooks(File root) {
        final TreeSet<String> books = new TreeSet<>();
        if (root == null || !root.isDirectory()) {
            return books;
        }
        final String rootPath = root.getAbsolutePath();
        final Deque<File> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            final File[] children = pending.poll().listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (child.getName().startsWith(".")) {
                    continue;
                }
                if (child.isDirectory()) {
                    pending.add(child);
                } else if (BookType.isSupportedExtByPath(child.getName())) {
                    books.add(child.getAbsolutePath().substring(rootPath.length() + 1).replace(File.separatorChar, '/'));
                }
            }
        }
        return books;
    }

    /**
     * The shared cover of a server book (an item path), decoded at about {@code width}; null when
     * the server has none or it cannot be fetched. Runs on Glide's loader thread.
     */
    public static Bitmap load(Context c, String item, int width) {
        if (!hasCover(item)) {
            return null;
        }
        final String cover = coverOf(serverPathOf(item));
        if (cover == null) {
            return null;
        }
        final File temp = new File(c.getCacheDir(), "webdav-cover-" + Integer.toHexString(item.hashCode()) + JPG);
        try {
            sharedClient(c).download(cover, temp);
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getPath(), bounds);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (width > 0 && bounds.outWidth / (options.inSampleSize * 2) >= width) {
                options.inSampleSize *= 2;
            }
            return BitmapFactory.decodeFile(temp.getPath(), options);
        } catch (Exception e) {
            LOG.d("WebDAV cover", cover, e.getMessage());
            return null;
        } finally {
            temp.delete();
        }
    }

    /** One client for all cover fetches, renewed when the settings change. */
    static synchronized WebDavClient sharedClient(Context c) {
        final String key = WebDav.getUrl(c) + "|" + WebDav.getUser(c) + "|" + WebDav.getPassword(c);
        if (client == null || !key.equals(clientKey)) {
            client = WebDav.client(c);
            clientKey = key;
        }
        return client;
    }

    private static Set<String> readLines(File file) {
        final TreeSet<String> lines = new TreeSet<>();
        if (!file.isFile()) {
            return lines;
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            LOG.e(e);
        }
        return lines;
    }

    private static void writeLines(File file, Set<String> lines) throws IOException {
        final File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        try (OutputStream stream = new FileOutputStream(file)) {
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
