package com.foobnix.webdav;

import android.content.Context;
import android.content.res.AssetManager;

import com.foobnix.android.utils.LOG;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts a skill on the server that tells an AI agent how the app stores its AI results (the
 * {@code ai/} and {@code assist/} files of each book), so one run outside the app can write them
 * like another device would (F-016). The skill comes from the APK's {@code assets/ai-skill/}; it is
 * uploaded when the server has none, or one of an older version, and otherwise left as it is.
 */
public final class AiSkill {

    /** Where agents look for skills, relative to the WebDAV root (outside every synced profile). */
    public static final String FOLDER = ".claude/skills/yggdrasil-ai-data";
    static final String ASSETS = "ai-skill";
    static final String VERSION_FILE = "skill.json";

    private AiSkill() {
    }

    /** @return files uploaded (0 when the server's skill is current, or on any error) */
    static int sendIfMissing(Context c, WebDavClient client) {
        try {
            final AssetManager assets = c.getAssets();
            final String mine = read(assets, ASSETS + "/" + VERSION_FILE);
            String remote = null;
            final File temp = new File(c.getCacheDir(), "ai-skill.tmp");
            try {
                if (client.stat(FOLDER + "/" + VERSION_FILE) != null) {
                    client.download(FOLDER + "/" + VERSION_FILE, temp);
                    remote = readFile(temp);
                }
                if (!needsUpload(remote, version(mine))) {
                    return 0;
                }
                int sent = 0;
                // the version file last: a skill cut off halfway is sent again next time
                for (String path : files(assets, ASSETS)) {
                    if (!path.equals(VERSION_FILE)) {
                        copy(assets, ASSETS + "/" + path, temp);
                        client.upload(temp, FOLDER + "/" + path);
                        sent++;
                    }
                }
                copy(assets, ASSETS + "/" + VERSION_FILE, temp);
                client.upload(temp, FOLDER + "/" + VERSION_FILE);
                return sent + 1;
            } finally {
                temp.delete();
            }
        } catch (Exception e) {
            LOG.d("AiSkill", e.getMessage());
            return 0;
        }
    }

    /** {@code remote}: the server's skill.json, null when it has none. */
    static boolean needsUpload(String remote, int mine) {
        return remote == null || version(remote) < mine;
    }

    /** The {@code version} of a skill.json, 0 when unreadable. */
    static int version(String json) {
        try {
            return new JSONObject(json).optInt("version", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** The files under an asset folder, as paths relative to it. */
    private static List<String> files(AssetManager assets, String folder) throws IOException {
        final List<String> out = new ArrayList<>();
        walk(assets, folder, "", out);
        return out;
    }

    private static void walk(AssetManager assets, String folder, String prefix, List<String> out) throws IOException {
        final String[] names = assets.list(folder);
        for (int i = 0; names != null && i < names.length; i++) {
            final String[] inside = assets.list(folder + "/" + names[i]);
            if (inside != null && inside.length > 0) {
                walk(assets, folder + "/" + names[i], prefix + names[i] + "/", out);
            } else {
                out.add(prefix + names[i]);
            }
        }
    }

    private static void copy(AssetManager assets, String path, File target) throws IOException {
        try (InputStream in = assets.open(path); OutputStream out = new FileOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static String read(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static String readFile(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
