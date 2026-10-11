package com.foobnix.webdav;

import android.content.Context;

import com.foobnix.ai.AiSettings;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.model.BookCSS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Settings shared by all the user's devices through WebDAV: app settings ({@link AppState}),
 * book-view settings ({@link BookCSS}) and AI settings ({@link AiSettings}, the key encrypted with
 * the WebDAV password). Each is a JSON file in {@code profile.<name>/.shared/} on the server,
 * merged setting by setting ({@link SettingsMerge}) against the copy this device merged last.
 * Design: .agents/planning/settings-sync-design.md.
 */
final class SharedSettings {

    static final String FOLDER = ".shared";
    static final String STATE = "app-State.json";
    static final String CSS = "app-CSS.json";
    static final String AI = "app-AI.json";

    /** Names of settings that belong to one device (D8): folders, paths, brightness... */
    private static final Pattern LOCAL_NAME = Pattern.compile("(?i)(.*path.*|.*folder|.*hash.*|.*bright.*|.*brigt.*)");
    private static final Set<String> LOCAL = new HashSet<>(Arrays.asList(
            "orientation", "pagesInMemory", "pageQuality", "isJavaZip", "isEnableAccessibility", "isEditMode",
            "isLoaded", "selectedText", "versionNew", "fontExtractTime", "tabPositionInRecentDialog",
            "widgetItemsCount", "searchQuery", "isFirstTime"));

    private SharedSettings() {
    }

    static boolean isDeviceLocal(String name) {
        return LOCAL.contains(name) || LOCAL_NAME.matcher(name).matches();
    }

    /** The settings step of a sync; problems are written into the summary, never thrown. */
    static void sync(Context c, WebDavClient client, WebDav.Summary summary) {
        final String folder = AppProfile.PROFILE_PREFIX + AppProfile.getCurrent() + "/" + FOLDER;
        syncObject(c, client, folder, STATE, AppState.get(), AppProfile.syncState, summary);
        syncObject(c, client, folder, CSS, BookCSS.get(), AppProfile.syncCSS, summary);
        try {
            syncAi(c, client, folder + "/" + AI);
        } catch (Exception e) {
            LOG.e(e);
            summary.errors.append(FOLDER).append('/').append(AI).append(": ").append(e.getMessage()).append('\n');
        }
    }

    private static void syncObject(Context c, WebDavClient client, String folder, String name, Object settings,
                                   File localFile, WebDav.Summary summary) {
        try {
            final String path = folder + "/" + name;
            final Remote remote = download(c, client, path);
            final File baseFile = WebDav.stateFile(c, "shared-" + name, AppProfile.SYNC_FOLDER_PROFILE);
            final Map<String, String> local = fieldsOf(settings);
            final long localTime = localFile == null ? 0 : localFile.lastModified();
            final SettingsMerge.Result result = SettingsMerge.merge(read(baseFile), local, remote.values,
                    remote.time == 0 || localTime > remote.time);

            if (!result.fromRemote.isEmpty()) {
                final Map<String, String> values = new HashMap<>();
                for (String key : result.fromRemote) {
                    values.put(key, result.merged.get(key));
                }
                apply(settings, values);
                if (settings instanceof AppState) {
                    AppState.get().save(c);
                } else if (settings instanceof BookCSS) {
                    BookCSS.get().save(c);
                }
                summary.downloaded++;
            }
            if (remote.values == null || !result.merged.equals(remote.values)) {
                upload(c, client, path, result.merged);
                summary.uploaded++;
            }
            write(baseFile, result.merged);
        } catch (Exception e) {
            LOG.e(e);
            summary.errors.append(FOLDER).append('/').append(name).append(": ").append(e.getMessage()).append('\n');
        }
    }

    /**
     * AI settings: merged with the keys standing as digests (the base never holds a key, D10),
     * keys decrypted from and encrypted for the server with the WebDAV password (D2, D9).
     */
    private static void syncAi(Context c, WebDavClient client, String path) throws Exception {
        final String password = WebDav.getPassword(c);
        final File baseFile = WebDav.stateFile(c, "shared-" + AI, AppProfile.SYNC_FOLDER_PROFILE);

        final Map<String, String> localPlain = AiSettings.shared(c);
        final Map<String, String> local = digestKeys(localPlain);

        final Remote remote = download(c, client, path);
        Map<String, String> remotePlain = null;
        Map<String, String> remoteCompared = null;
        if (remote.values != null) {
            remotePlain = new LinkedHashMap<>(remote.values);
            for (String name : AiSettings.SHARED) {
                if (isKey(name) && remotePlain.containsKey(name)) {
                    final String sealed = remotePlain.get(name);
                    final String plain = sealed.isEmpty() ? "" : SecretBox.open(password, sealed);
                    if (plain == null) {
                        // sealed with another WebDAV password: unreadable here, as if unchanged
                        remotePlain.remove(name);
                    } else {
                        remotePlain.put(name, plain);
                    }
                }
            }
            remoteCompared = digestKeys(remotePlain);
        }

        final long remoteTime = remote.time;
        final SettingsMerge.Result result = SettingsMerge.merge(read(baseFile), local, remoteCompared,
                remoteTime == 0 || AiSettings.modified(c) > remoteTime);

        // back from digests to keys: a key in the result is this device's or the server's
        final Map<String, String> mergedPlain = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : result.merged.entrySet()) {
            String value = e.getValue();
            if (isKey(e.getKey())) {
                if (value.equals(local.get(e.getKey()))) {
                    value = localPlain.get(e.getKey());
                } else if (remotePlain != null && value.equals(remoteCompared.get(e.getKey()))) {
                    value = remotePlain.get(e.getKey());
                }
            }
            mergedPlain.put(e.getKey(), value);
        }

        if (!result.fromRemote.isEmpty()) {
            final Map<String, String> values = new HashMap<>();
            for (String key : result.fromRemote) {
                values.put(key, mergedPlain.get(key));
            }
            AiSettings.applyShared(c, values);
        }
        if (remoteCompared == null || !result.merged.equals(remoteCompared)) {
            final Map<String, String> sealed = new LinkedHashMap<>(mergedPlain);
            for (String name : AiSettings.SHARED) {
                final String key = sealed.get(name);
                if (isKey(name) && key != null && !key.isEmpty()) {
                    sealed.put(name, SecretBox.seal(password, key));
                }
            }
            upload(c, client, path, sealed);
        }
        write(baseFile, result.merged);
    }

    private static boolean isKey(String name) {
        return name.endsWith(".key");
    }

    private static Map<String, String> digestKeys(Map<String, String> values) {
        final Map<String, String> out = new LinkedHashMap<>(values);
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (isKey(e.getKey()) && !e.getValue().isEmpty()) {
                e.setValue(SecretBox.digest(e.getValue()));
            }
        }
        return out;
    }

    /** The shared settings of a settings object, as text (same fields as its own JSON file). */
    static Map<String, String> fieldsOf(Object settings) throws IllegalAccessException {
        final Map<String, String> out = new LinkedHashMap<>();
        for (Field f : settings.getClass().getDeclaredFields()) {
            if (!isShared(f)) {
                continue;
            }
            final Object value = f.get(settings);
            if (value != null) {
                out.put(f.getName(), String.valueOf(value));
            }
        }
        return out;
    }

    /** Sets fields by name from text; a field this build lacks or a bad value is skipped. */
    static void apply(Object settings, Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            try {
                final Field f = settings.getClass().getDeclaredField(e.getKey());
                if (!isShared(f)) {
                    continue;
                }
                final String v = e.getValue();
                final Class<?> type = f.getType();
                if (type == int.class) {
                    f.setInt(settings, Integer.parseInt(v));
                } else if (type == long.class) {
                    f.setLong(settings, Long.parseLong(v));
                } else if (type == float.class) {
                    f.setFloat(settings, Float.parseFloat(v));
                } else if (type == boolean.class) {
                    f.setBoolean(settings, Boolean.parseBoolean(v));
                } else if (type == String.class) {
                    f.set(settings, v);
                }
            } catch (NoSuchFieldException | IllegalAccessException | NumberFormatException ignored) {
                // a setting of another build, or damaged: this device keeps its own
            }
        }
    }

    private static boolean isShared(Field f) {
        final int m = f.getModifiers();
        if (Modifier.isStatic(m) || Modifier.isPrivate(m) || Modifier.isTransient(m) || Modifier.isFinal(m)) {
            return false;
        }
        final Class<?> t = f.getType();
        if (t != int.class && t != long.class && t != float.class && t != boolean.class && t != String.class) {
            return false;
        }
        return !isDeviceLocal(f.getName());
    }

    private static final class Remote {
        Map<String, String> values;
        long time;
    }

    private static Remote download(Context c, WebDavClient client, String path) throws IOException, JSONException {
        final Remote remote = new Remote();
        final WebDavClient.Entry entry = client.stat(path);
        if (entry == null || entry.directory) {
            return remote;
        }
        final File temp = new File(c.getCacheDir(), "webdav-shared.json");
        try {
            client.download(path, temp);
            remote.values = parse(readText(temp));
            remote.time = entry.lastModified;
        } finally {
            temp.delete();
        }
        return remote;
    }

    private static void upload(Context c, WebDavClient client, String path, Map<String, String> values) throws IOException {
        final File temp = new File(c.getCacheDir(), "webdav-shared-up.json");
        try {
            writeText(temp, new JSONObject(values).toString());
            client.upload(temp, path);
        } finally {
            temp.delete();
        }
    }

    private static Map<String, String> read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return parse(readText(file));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    private static void write(File file, Map<String, String> values) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        writeText(file, new JSONObject(values).toString());
    }

    private static Map<String, String> parse(String json) throws JSONException {
        final JSONObject o = new JSONObject(json);
        final Map<String, String> out = new LinkedHashMap<>();
        final Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            if (!o.isNull(key)) {
                out.put(key, String.valueOf(o.get(key)));
            }
        }
        return out;
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                final int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
