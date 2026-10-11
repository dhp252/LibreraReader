package com.foobnix.webdav;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The part of WebDAV (RFC 4918) that sync needs: PROPFIND listing, GET, PUT, MKCOL and DELETE,
 * with Basic or Digest authentication. Paths are relative to the root URL the client was made
 * with, use "/" and are not URL-encoded ("Books/Война и мир.epub").
 */
public class WebDavClient implements FolderSync.Remote {

    private static final String DAV = "DAV:";
    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private static final MediaType BINARY = MediaType.parse("application/octet-stream");
    private static final String PROPFIND_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
            + "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/>"
            + "</d:prop></d:propfind>";

    /** A file or folder on the server. */
    public static class Entry {
        /** Relative to the root, without leading or trailing "/". */
        public final String path;
        public final boolean directory;
        public final long size;
        /** Server time in ms, 0 when the server does not say. */
        public final long lastModified;
        /** May be null: not every server sends one. */
        public final String etag;

        public Entry(String path, boolean directory, long size, long lastModified, String etag) {
            this.path = path;
            this.directory = directory;
            this.size = size;
            this.lastModified = lastModified;
            this.etag = etag;
        }

        public String getName() {
            final int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }

        @Override
        public String toString() {
            return (directory ? "[dir] " : "") + path + " " + size + " " + lastModified + " " + etag;
        }
    }

    /** An HTTP answer that is not a success. */
    public static class WebDavException extends IOException {
        public final int code;

        public WebDavException(int code, String message) {
            super(code + " " + message);
            this.code = code;
        }
    }

    private final OkHttpClient http;
    private final HttpUrl root;
    /** Folders known to exist (listed or created), so uploads do not repeat MKCOL for each file. */
    private final Set<String> knownFolders = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * @param url      the folder all paths are relative to, e.g.
     *                 https://cloud.example.com/remote.php/dav/files/me/Librera
     * @param user     empty for servers without authentication
     */
    public WebDavClient(String url, String user, String password) {
        this(buildHttp(user, password), url);
    }

    WebDavClient(OkHttpClient http, String url) {
        HttpUrl parsed = HttpUrl.parse(url.trim());
        if (parsed == null) {
            throw new IllegalArgumentException("Not an http(s) URL: " + url);
        }
        if (!parsed.encodedPath().endsWith("/")) {
            parsed = parsed.newBuilder().addPathSegment("").build();
        }
        this.http = http;
        this.root = parsed;
    }

    private static OkHttpClient buildHttp(String user, String password) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);
        if (user != null && !user.isEmpty()) {
            final Credentials credentials = new Credentials(user, password == null ? "" : password);
            final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();
            final DispatchingAuthenticator authenticator = new DispatchingAuthenticator.Builder()
                    .with("digest", new DigestAuthenticator(credentials))
                    .with("basic", new BasicAuthenticator(credentials))
                    .build();
            builder.authenticator(new CachingAuthenticatorDecorator(authenticator, authCache))
                   .addInterceptor(new AuthenticationCacheInterceptor(authCache));
        }
        return builder.build();
    }

    /** The direct children of a folder ("" for the root). */
    public List<Entry> list(String folder) throws IOException {
        final HttpUrl url = url(folder, true);
        final Request request = new Request.Builder()
                .url(url)
                .header("Depth", "1")
                .method("PROPFIND", RequestBody.create(XML, PROPFIND_BODY))
                .build();
        try (Response response = http.newCall(request).execute()) {
            check(response, "PROPFIND " + folder);
            final ResponseBody body = response.body();
            final List<Entry> all = parseMultistatus(body == null ? "" : body.string(), url, root);
            final List<Entry> children = new ArrayList<>();
            final String self = trimSlashes(folder);
            knownFolders.add(self);
            for (Entry entry : all) {
                if (entry.directory) {
                    knownFolders.add(entry.path);
                }
                if (!entry.path.equals(self)) {
                    children.add(entry);
                }
            }
            return children;
        }
    }

    /**
     * Every file and folder below {@code folder}, folder by folder: many servers refuse
     * "Depth: infinity". Hidden folders below it (".covers") are listed but not entered.
     */
    public List<Entry> listRecursive(String folder) throws IOException {
        final List<Entry> result = new ArrayList<>();
        final Deque<String> pending = new ArrayDeque<>();
        pending.add(trimSlashes(folder));
        while (!pending.isEmpty()) {
            for (Entry entry : list(pending.poll())) {
                result.add(entry);
                if (entry.directory && !entry.getName().startsWith(".")) {
                    pending.add(entry.path);
                }
            }
        }
        return result;
    }

    /** @return the entry, or null when nothing is at that path */
    public Entry stat(String path) throws IOException {
        final HttpUrl url = url(path, false);
        final Request request = new Request.Builder()
                .url(url)
                .header("Depth", "0")
                .method("PROPFIND", RequestBody.create(XML, PROPFIND_BODY))
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 404) {
                return null;
            }
            check(response, "PROPFIND " + path);
            final ResponseBody body = response.body();
            final List<Entry> entries = parseMultistatus(body == null ? "" : body.string(), url, root);
            return entries.isEmpty() ? null : entries.get(0);
        }
    }

    /** Downloads to a temporary file next to {@code target} and renames it, so a broken transfer never leaves half a file. */
    public void download(String path, File target) throws IOException {
        download(path, target, null);
    }

    /** Asked between chunks of a download: true stops it (an IOException, no file left). */
    public interface Cancel {
        boolean isCancelled();
    }

    public void download(String path, File target, Cancel cancel) throws IOException {
        final Request request = new Request.Builder().url(url(path, false)).get().build();
        final File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Cannot create " + parent);
        }
        final File temp = new File(parent, "." + target.getName() + ".part");
        try (Response response = http.newCall(request).execute()) {
            check(response, "GET " + path);
            final ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("GET " + path + ": empty body");
            }
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(temp)) {
                final byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancel != null && cancel.isCancelled()) {
                        throw new IOException("GET " + path + ": cancelled");
                    }
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("Cannot replace " + target);
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Cannot rename " + temp + " to " + target);
        }
    }

    /** Uploads a file, creating the missing parent folders. */
    public void upload(File source, String path) throws IOException {
        final String parent = parentOf(path);
        if (!parent.isEmpty()) {
            mkdirs(parent);
        }
        final Request request = new Request.Builder()
                .url(url(path, false))
                .put(RequestBody.create(BINARY, source))
                .build();
        try (Response response = http.newCall(request).execute()) {
            check(response, "PUT " + path);
        }
    }

    /**
     * Creates the folder and its missing parents below the root; existing folders are fine.
     * {@code ""} creates the root folder itself (its parent must exist).
     */
    public void mkdirs(String folder) throws IOException {
        if (trimSlashes(folder).isEmpty()) {
            if (!knownFolders.contains("")) {
                final Request request = new Request.Builder().url(root).method("MKCOL", null).build();
                try (Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful() && response.code() != 405) {
                        check(response, "MKCOL " + root);
                    }
                }
                knownFolders.add("");
            }
            return;
        }
        final String[] parts = trimSlashes(folder).split("/");
        final StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(part);
            final String path = current.toString();
            if (knownFolders.contains(path)) {
                continue;
            }
            final Request request = new Request.Builder()
                    .url(url(path, true))
                    .method("MKCOL", null)
                    .build();
            try (Response response = http.newCall(request).execute()) {
                // 405 Method Not Allowed: the folder exists already
                if (!response.isSuccessful() && response.code() != 405) {
                    check(response, "MKCOL " + path);
                }
            }
            knownFolders.add(path);
        }
    }

    /** Deletes a file or a folder with its content; a missing one is fine. */
    public void delete(String path) throws IOException {
        final Request request = new Request.Builder().url(url(path, false)).delete().build();
        try (Response response = http.newCall(request).execute()) {
            if (response.code() != 404) {
                check(response, "DELETE " + path);
            }
        }
    }

    HttpUrl url(String path, boolean folder) {
        final HttpUrl.Builder builder = root.newBuilder();
        // the root ends with an empty segment ("/"): replace it with the first real one
        final List<String> rootSegments = root.pathSegments();
        builder.removePathSegment(rootSegments.size() - 1);
        for (String part : trimSlashes(path).split("/")) {
            if (!part.isEmpty()) {
                builder.addPathSegment(part);
            }
        }
        if (folder) {
            builder.addPathSegment("");
        }
        final HttpUrl url = builder.build();
        return trimSlashes(path).isEmpty() ? root : url;
    }

    private static void check(Response response, String what) throws WebDavException {
        if (!response.isSuccessful()) {
            throw new WebDavException(response.code(), what + " " + response.message());
        }
    }

    static String trimSlashes(String path) {
        if (path == null) {
            return "";
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }

    public static String parentOf(String path) {
        final String trimmed = trimSlashes(path);
        final int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? "" : trimmed.substring(0, slash);
    }

    /** Parses a 207 Multi-Status answer into entries relative to {@code root}. */
    static List<Entry> parseMultistatus(String xml, HttpUrl requestUrl, HttpUrl root) throws IOException {
        final List<Entry> entries = new ArrayList<>();
        if (xml == null || xml.trim().isEmpty()) {
            return entries;
        }
        final Document document;
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // no DTDs or external entities from a server answer
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
                // not every parser knows the feature; entity expansion stays off below
            }
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        } catch (Exception e) {
            throw new IOException("Bad PROPFIND answer: " + e.getMessage(), e);
        }

        final List<String> rootSegments = withoutEmpty(root.pathSegments());
        final NodeList responses = document.getElementsByTagNameNS(DAV, "response");
        for (int i = 0; i < responses.getLength(); i++) {
            final Element response = (Element) responses.item(i);
            final String href = text(response, "href");
            if (href == null) {
                continue;
            }
            final HttpUrl resolved = requestUrl.resolve(href.trim());
            if (resolved == null) {
                continue;
            }
            final List<String> segments = withoutEmpty(resolved.pathSegments());
            if (segments.size() < rootSegments.size() || !segments.subList(0, rootSegments.size()).equals(rootSegments)) {
                continue;
            }
            final StringBuilder path = new StringBuilder();
            for (String segment : segments.subList(rootSegments.size(), segments.size())) {
                if (path.length() > 0) {
                    path.append('/');
                }
                path.append(segment);
            }

            Element ok = null;
            final NodeList propstats = response.getElementsByTagNameNS(DAV, "propstat");
            for (int k = 0; k < propstats.getLength(); k++) {
                final Element propstat = (Element) propstats.item(k);
                final String status = text(propstat, "status");
                if (status == null || status.contains(" 200")) {
                    ok = propstat;
                    break;
                }
            }
            if (ok == null) {
                continue;
            }
            final NodeList types = ok.getElementsByTagNameNS(DAV, "resourcetype");
            final boolean directory = types.getLength() > 0
                    && ((Element) types.item(0)).getElementsByTagNameNS(DAV, "collection").getLength() > 0;
            final long size = parseLong(text(ok, "getcontentlength"));
            final long modified = parseHttpDate(text(ok, "getlastmodified"));
            String etag = text(ok, "getetag");
            if (etag != null) {
                etag = etag.trim();
                if (etag.isEmpty()) {
                    etag = null;
                }
            }
            entries.add(new Entry(path.toString(), directory, size, modified, etag));
        }
        return entries;
    }

    private static List<String> withoutEmpty(List<String> segments) {
        final List<String> result = new ArrayList<>(segments.size());
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                result.add(segment);
            }
        }
        return result;
    }

    private static String text(Element parent, String localName) {
        final NodeList nodes = parent.getElementsByTagNameNS(DAV, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        final Node node = nodes.item(0);
        return node.getTextContent();
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** RFC 1123 dates as sent in getlastmodified ("Tue, 07 Oct 2026 10:15:00 GMT"); 0 when unreadable. */
    static long parseHttpDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        final SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            final Date date = format.parse(value.trim());
            return date == null ? 0 : date.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }
}
