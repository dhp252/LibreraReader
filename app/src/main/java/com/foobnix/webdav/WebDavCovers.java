package com.foobnix.webdav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Book covers shared through the server, so a device can show the cover of a book it has not
 * downloaded: a device that has a book in Librera/Books uploads its cover once, as a small JPEG in
 * {@code Books/.covers/<book file name>.jpg}, and the others fetch only that. The covers folder has
 * no subfolders: a book moved to another folder keeps its cover, and the WebDAV tab lists one folder
 * for the covers of all its folders (books with the same file name share one cover, the owner's
 * choice).
 * <p>
 * The WebDAV tab shows a server-only book as a {@code FileMeta} whose path is an item path,
 * {@code webdav:<cover tag>:<server path>}: the cover tag changes when the cover does, and Glide
 * caches covers by path, so a new cover is fetched and an unchanged one never again.
 */
public class WebDavCovers {

    public static final String PREFIX = "webdav:";
    public static final String COVERS = WebDav.BOOKS + "/.covers";
    static final String JPG = ".jpg";
    /** Cover tag of a folder item. */
    private static final String FOLDER = "d";
    /** Cover tag of a book with no cover on the server. */
    private static final String NONE = "0";
    /** Width of an uploaded cover; the height follows the cover (about 1.5 times). */
    static final int WIDTH = 240;

    private static WebDavClient client;
    private static String clientKey;

    /** "Books/a/b.pdf" -> "Books/.covers/b.pdf.jpg"; null for a path outside Books. */
    public static String coverOf(String bookPath) {
        final String relative = relativeToBooks(bookPath);
        return relative == null ? null : COVERS + "/" + coverNameOf(fileNameOf(relative));
    }

    /** The cover file name of a book file name: "b.pdf" -> "b.pdf.jpg". */
    public static String coverNameOf(String bookName) {
        return bookName + JPG;
    }

    /** The book file name ("b.pdf") of a cover ("Books/.covers/b.pdf.jpg"), or null. */
    static String bookOfCover(String coverPath) {
        return bookOf(coverPath, JPG);
    }

    /** The book file name ("b.pdf") of a metadata file ("Books/.covers/b.pdf.json"), or null. */
    static String bookOfMeta(String metaPath) {
        return bookOf(metaPath, WebDavBookMeta.JSON);
    }

    /** Null for anything but a file of that kind right in the covers folder. */
    private static String bookOf(String path, String suffix) {
        final String trimmed = WebDavClient.trimSlashes(path);
        if (!trimmed.startsWith(COVERS + "/") || !trimmed.endsWith(suffix)) {
            return null;
        }
        final String book = trimmed.substring(COVERS.length() + 1, trimmed.length() - suffix.length());
        return book.isEmpty() || book.contains("/") ? null : book;
    }

    /** "a/b.pdf" -> "b.pdf". */
    static String fileNameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
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

    /** What a sync does with the covers. Books are told by their file name ("b.pdf"). */
    static class Plan {
        /** Books on this device whose cover is not on the server. */
        final List<String> upload = new ArrayList<>();
        /** Covers whose book is gone from the server (and not here, waiting to be uploaded). */
        final List<String> delete = new ArrayList<>();
    }

    /**
     * @param remoteBooks the file names of the books the books sync just listed on the server; null
     *                    when it could not list them. Covers are only deleted against a listing that
     *                    has books in it: a missing or emptied folder must not cost every cover.
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
        final List<String> folders = new ArrayList<>();
        try {
            for (WebDavClient.Entry entry : client.list(COVERS)) {
                if (entry.directory) {
                    folders.add(entry.path);
                    continue;
                }
                final String book = bookOfCover(entry.path);
                if (book != null) {
                    covers.add(book);
                }
                final String described = bookOfMeta(entry.path);
                if (described != null) {
                    metas.add(described);
                }
            }
        } catch (WebDavClient.WebDavException e) {
            if (e.code != 404) {
                throw e;
            }
        }
        for (String folder : folders) {
            flatten(client, folder, covers, metas, result);
        }
        final File failedFile = WebDav.stateFile(c, "covers-failed", AppProfile.SYNC_FOLDER_BOOKS);
        final Map<String, String> local = byFileName(localBooks(AppProfile.SYNC_FOLDER_BOOKS));
        final Set<String> remote = books.getLastRemote() == null ? null : byFileName(books.getLastRemote()).keySet();
        final Set<String> failed = readLines(failedFile);
        // a book removed here, or replaced under another name, is tried afresh when it is back
        failed.retainAll(local.keySet());
        final Plan plan = plan(local.keySet(), remote, covers, failed);

        for (String book : plan.upload) {
            final File source = new File(AppProfile.SYNC_FOLDER_BOOKS, local.get(book));
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

        final Plan metaPlan = plan(local.keySet(), remote, metas, java.util.Collections.<String>emptySet());
        final File json = new File(c.getCacheDir(), "webdav-meta-upload.json");
        for (String book : metaPlan.upload) {
            try {
                final FileMeta meta = FileMetaCore.createMetaIfNeed(new File(AppProfile.SYNC_FOLDER_BOOKS, local.get(book)).getPath(), false);
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

    /**
     * Moves the covers and metadata of the first layout, kept in folders like the books
     * ("Books/.covers/a/b.pdf.jpg"), into the covers folder itself, then deletes the folder: no
     * cover is made again. One already there for the same file name wins over the moved one.
     */
    static void flatten(WebDavClient client, String folder, Set<String> covers, Set<String> metas, FolderSync.Result result) {
        try {
            for (WebDavClient.Entry entry : client.listRecursive(folder)) {
                if (entry.directory) {
                    continue;
                }
                final String flat = COVERS + "/" + entry.getName();
                final String cover = bookOfCover(flat);
                final String meta = bookOfMeta(flat);
                final Set<String> kept = cover != null ? covers : meta != null ? metas : null;
                final String book = cover != null ? cover : meta;
                if (kept == null || kept.contains(book)) {
                    continue;
                }
                try {
                    client.move(entry.path, flat);
                    kept.add(book);
                } catch (WebDavClient.WebDavException e) {
                    // another device moved it first, or one came meanwhile: that one stays
                    if (e.code != 404 && e.code != WebDavClient.PRECONDITION_FAILED) {
                        throw e;
                    }
                }
            }
            client.deleteFolder(folder);
        } catch (IOException e) {
            result.errors.add(folder + ": " + e.getMessage());
        }
    }

    /** Book paths ("a/b.pdf") by their file name ("b.pdf"); the first path wins a shared name. */
    static Map<String, String> byFileName(Set<String> paths) {
        final Map<String, String> byName = new TreeMap<>();
        for (String path : new TreeSet<>(paths)) {
            final String name = fileNameOf(path);
            if (!byName.containsKey(name)) {
                byName.put(name, path);
            }
        }
        return byName;
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
        final Bitmap flat = onWhite(cover);
        try (OutputStream out = new FileOutputStream(jpg)) {
            if (!flat.compress(Bitmap.CompressFormat.JPEG, 80, out)) {
                throw new IOException("cannot encode the cover of " + book.getName());
            }
        } finally {
            if (flat != cover) {
                flat.recycle();
            }
            cover.recycle();
        }
        return true;
    }

    /**
     * The cover laid on white paper. A JPEG has no transparency: what is transparent in the cover
     * (the background of a PDF page drawn by Android's renderer, a PNG cover with a clear
     * background) would turn black.
     */
    private static Bitmap onWhite(Bitmap cover) {
        if (!cover.hasAlpha()) {
            return cover;
        }
        final Bitmap flat = Bitmap.createBitmap(cover.getWidth(), cover.getHeight(), Bitmap.Config.RGB_565);
        flat.eraseColor(Color.WHITE);
        new Canvas(flat).drawBitmap(cover, 0, 0, null);
        return flat;
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
        final String known = cachedMetaJson(c, meta);
        if (known != null) {
            return known;
        }
        final File cached = metaCacheOf(c, meta);
        final String tag = FolderSync.tag(meta).replace('\n', ' ');
        try {
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

    /** The metadata JSON of a server book as this device keeps it, without asking the server; null when not kept. */
    public static String cachedMetaJson(Context c, WebDavClient.Entry meta) {
        final File cached = metaCacheOf(c, meta);
        if (!cached.isFile()) {
            return null;
        }
        try {
            final String text = new String(readAll(cached), StandardCharsets.UTF_8);
            final int line = text.indexOf('\n');
            if (line > 0 && text.substring(0, line).equals(FolderSync.tag(meta).replace('\n', ' '))) {
                return text.substring(line + 1);
            }
        } catch (IOException e) {
            LOG.d("WebDAV metadata", meta.path, e.getMessage());
        }
        return null;
    }

    /** Keeps a metadata JSON this device has just uploaded ({@code meta} is its entry), so it is never fetched back. */
    static void keepMetaJson(Context c, WebDavClient.Entry meta, String json) {
        try {
            WebDavCoverMaker.writeText(metaCacheOf(c, meta), FolderSync.tag(meta).replace('\n', ' ') + "\n" + json);
        } catch (IOException e) {
            LOG.d("WebDAV metadata", meta.path, e.getMessage());
        }
    }

    private static File metaCacheOf(Context c, WebDavClient.Entry meta) {
        return new File(new File(c.getCacheDir(), "webdav-meta"), Integer.toHexString(meta.path.hashCode()) + WebDavBookMeta.JSON);
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

    /**
     * One client for the WebDAV tab and its covers, renewed when the settings change: its open
     * connections and the login it has done are kept between folders.
     */
    public static synchronized WebDavClient sharedClient(Context c) {
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
