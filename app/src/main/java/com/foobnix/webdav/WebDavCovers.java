package com.foobnix.webdav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.ui2.FileMetaCore;

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
    static final String JPG = ".jpg";
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

    public static boolean hasCover(String item) {
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
     * books on this device and deletes the covers of books gone from the server; the same for
     * their metadata ({@link WebDavBookMeta}), read from this device's library, no download.
     */
    static FolderSync.Result sync(Context c, WebDavClient client, FolderSync books) throws IOException {
        final FolderSync.Result result = new FolderSync.Result();
        final TreeSet<String> covers = new TreeSet<>();
        final TreeSet<String> metas = new TreeSet<>();
        try {
            for (WebDavClient.Entry entry : client.listRecursive(COVERS)) {
                final String book = entry.directory ? null : bookOfCover(entry.path);
                if (book != null) {
                    covers.add(book);
                }
                final String described = entry.directory ? null : bookOfMeta(entry.path);
                if (described != null) {
                    metas.add(described);
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

        final Plan metaPlan = plan(local, books.getLastRemote(), metas, java.util.Collections.<String>emptySet());
        final File json = new File(c.getCacheDir(), "webdav-meta-upload.json");
        for (String book : metaPlan.upload) {
            try {
                final FileMeta meta = FileMetaCore.createMetaIfNeed(new File(AppProfile.SYNC_FOLDER_BOOKS, book).getPath(), false);
                WebDavCoverMaker.writeText(json, WebDavBookMeta.toJson(meta));
                client.upload(json, COVERS + "/" + book + WebDavBookMeta.JSON);
                result.uploaded++;
            } catch (IOException | org.json.JSONException e) {
                result.errors.add(".covers/" + book + WebDavBookMeta.JSON + ": " + e.getMessage());
            } finally {
                json.delete();
            }
        }
        for (String book : metaPlan.delete) {
            try {
                client.delete(COVERS + "/" + book + WebDavBookMeta.JSON);
                result.deletedRemote++;
            } catch (IOException e) {
                result.errors.add(".covers/" + book + WebDavBookMeta.JSON + ": " + e.getMessage());
            }
        }
        return result;
    }

    /** The book path below Books ("a/b.pdf") of a metadata file ("Books/.covers/a/b.pdf.json"), or null. */
    static String bookOfMeta(String metaPath) {
        final String path = WebDavClient.trimSlashes(metaPath);
        if (!path.startsWith(COVERS + "/") || !path.endsWith(WebDavBookMeta.JSON)) {
            return null;
        }
        final String book = path.substring(COVERS.length() + 1, path.length() - WebDavBookMeta.JSON.length());
        return book.isEmpty() ? null : book;
    }

    /** The cover of a local book as a JPEG; false when the book could not be opened. */
    static boolean render(Context c, File book, File jpg) throws IOException {
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

    /**
     * The shared metadata JSON of a server book ({@code meta} is its entry in the covers folder),
     * kept in the cache by version, so a folder is fetched in full only once; null when it cannot
     * be fetched. Runs on a background thread.
     */
    public static String metaJson(Context c, WebDavClient.Entry meta) {
        final File cached = new File(new File(c.getCacheDir(), "webdav-meta"),
                Integer.toHexString(meta.path.hashCode()) + WebDavBookMeta.JSON);
        final String tag = FolderSync.tag(meta).replace('\n', ' ');
        try {
            if (cached.isFile()) {
                final String text = new String(readAll(cached), StandardCharsets.UTF_8);
                final int line = text.indexOf('\n');
                if (line > 0 && text.substring(0, line).equals(tag)) {
                    return text.substring(line + 1);
                }
            }
            final File temp = new File(c.getCacheDir(), "webdav-meta-" + Integer.toHexString(meta.path.hashCode()) + ".part");
            try {
                sharedClient(c).download(meta.path, temp);
                final String json = new String(readAll(temp), StandardCharsets.UTF_8);
                WebDavCoverMaker.writeText(cached, tag + "\n" + json);
                return json;
            } finally {
                temp.delete();
            }
        } catch (IOException e) {
            LOG.d("WebDAV metadata", meta.path, e.getMessage());
            return null;
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            final byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                final int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return read == data.length ? data : java.util.Arrays.copyOf(data, read);
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

    static Set<String> readLines(File file) {
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

    static void writeLines(File file, Set<String> lines) throws IOException {
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
