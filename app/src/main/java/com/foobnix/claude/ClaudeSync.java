package com.foobnix.claude;

import android.content.Context;
import android.content.SharedPreferences;

import com.foobnix.android.utils.LOG;
import com.foobnix.webdav.WebDavClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Puts the skill ({@link ClaudeSkill}) on the WebDAV server at each full sync, while Work with
 * Claude through WebDAV is on: only when its content changed since this device last sent it, and
 * never over the skill of a newer app (another device may run one).
 */
public final class ClaudeSync {

    private static final String PREFS = "claude";
    private static final String KEY_SENT = "skillSent";

    private ClaudeSync() {
    }

    /** @return files uploaded */
    public static int sendSkill(Context c, WebDavClient client, String serverKey) {
        try {
            if (!Claude.isOn(c)) {
                return 0;
            }
            final Map<String, String> files = ClaudeSkill.files(asset(c, "claude/SKILL.md"), versionName(c));
            final String mark = serverKey + "|" + Integer.toHexString(files.toString().hashCode());
            final SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (mark.equals(prefs.getString(KEY_SENT, ""))) {
                return 0;
            }
            final String formats = ClaudeSkill.FOLDER + "/" + ClaudeSkill.FORMATS;
            final File temp = new File(c.getCacheDir(), "claude-skill.tmp");
            try {
                if (client.stat(formats) != null) {
                    client.download(formats, temp);
                    if (ClaudeSkill.newerThanMine(ClaudeAnswers.text(temp))) {
                        LOG.d("Claude skill", "a newer app wrote it: left as it is");
                        prefs.edit().putString(KEY_SENT, mark).apply();
                        return 0;
                    }
                }
                int sent = 0;
                for (Map.Entry<String, String> e : files.entrySet()) {
                    ClaudeAnswers.write(temp, e.getValue());
                    client.upload(temp, ClaudeSkill.FOLDER + "/" + e.getKey());
                    sent++;
                }
                prefs.edit().putString(KEY_SENT, mark).apply();
                return sent;
            } finally {
                temp.delete();
            }
        } catch (Exception e) {
            LOG.d("Claude skill", e.getMessage());
            return 0;
        }
    }

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String asset(Context c, String path) throws IOException {
        try (InputStream in = c.getAssets().open(path)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
