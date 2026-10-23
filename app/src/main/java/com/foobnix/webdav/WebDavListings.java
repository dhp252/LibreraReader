package com.foobnix.webdav;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server folders as the WebDAV tab last listed them, kept in memory and in the app cache: a
 * folder seen before, even before the app was restarted, shows at once while it is listed again,
 * and one listed moments ago (a subfolder listed ahead of a tap) is not listed again. Listings of
 * another server or user are never read.
 */
public class WebDavListings {

    /** A folder's entries as listed at {@link #time}. */
    public static final class Listing {
        public final long time;
        public final List<WebDavClient.Entry> entries;

        Listing(long time, List<WebDavClient.Entry> entries) {
            this.time = time;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public boolean olderThan(long ms, long now) {
            return now - time > ms;
        }
    }

    private final File dir;
    private final String account;
    private final Map<String, Listing> memory = new ConcurrentHashMap<>();

    /**
     * @param dir     where the listings are kept on disk
     * @param account the server and user they are listed from ("url|user")
     */
    public WebDavListings(File dir, String account) {
        this.dir = dir;
        this.account = account;
    }

    /** The folder as last listed; null when it never was. Reads the disk: not on the UI thread. */
    public Listing get(String folder) {
        Listing known = memory.get(folder);
        if (known == null) {
            known = read(folder);
            if (known != null) {
                memory.put(folder, known);
            }
        }
        return known;
    }

    /** Keeps a fresh listing of the folder (kept in memory even when the disk refuses it). */
    public Listing put(String folder, List<WebDavClient.Entry> entries, long time) {
        final Listing listing = new Listing(time, entries);
        memory.put(folder, listing);
        write(folder, listing);
        return listing;
    }

    /**
     * Changes one entry of a folder kept, keeping its listing time: the entry at {@code path} is
     * dropped and {@code entry}, when not null, added (a cover just made, a book just deleted).
     */
    public void replace(String folder, String path, WebDavClient.Entry entry) {
        final Listing known = get(folder);
        if (known == null) {
            return;
        }
        final List<WebDavClient.Entry> entries = new ArrayList<>();
        for (WebDavClient.Entry e : known.entries) {
            if (!e.path.equals(path)) {
                entries.add(e);
            }
        }
        if (entry != null) {
            entries.add(entry);
        }
        put(folder, entries, known.time);
    }

    private File fileOf(String folder) {
        return new File(dir, Integer.toHexString((account + "|" + folder).hashCode()) + ".json");
    }

    private Listing read(String folder) {
        final File file = fileOf(folder);
        if (!file.isFile()) {
            return null;
        }
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
            return fromJson(new String(data, 0, read, StandardCharsets.UTF_8), account, folder);
        } catch (IOException | JSONException e) {
            // listed again from the server
            return null;
        }
    }

    private void write(String folder, Listing listing) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        final File file = fileOf(folder);
        final File temp = new File(dir, file.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(toJson(listing, account, folder).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            temp.delete();
        }
    }

    static String toJson(Listing listing, String account, String folder) throws JSONException {
        final JSONArray entries = new JSONArray();
        for (WebDavClient.Entry e : listing.entries) {
            final JSONObject o = new JSONObject();
            o.put("p", e.path);
            o.put("d", e.directory);
            o.put("s", e.size);
            o.put("m", e.lastModified);
            if (e.etag != null) {
                o.put("e", e.etag);
            }
            entries.put(o);
        }
        final JSONObject o = new JSONObject();
        o.put("account", account);
        o.put("folder", folder);
        o.put("time", listing.time);
        o.put("entries", entries);
        return o.toString();
    }

    /** Null when the text is the listing of another folder or account (a hash shared by two). */
    static Listing fromJson(String json, String account, String folder) throws JSONException {
        final JSONObject o = new JSONObject(json);
        if (!account.equals(o.optString("account")) || !folder.equals(o.optString("folder"))) {
            return null;
        }
        final JSONArray array = o.getJSONArray("entries");
        final List<WebDavClient.Entry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            final JSONObject e = array.getJSONObject(i);
            entries.add(new WebDavClient.Entry(e.getString("p"), e.optBoolean("d"), e.optLong("s"), e.optLong("m"),
                    e.has("e") ? e.getString("e") : null));
        }
        return new Listing(o.getLong("time"), entries);
    }
}
