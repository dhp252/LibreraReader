package com.foobnix.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI settings, in private preferences (excluded from Android backup). They are shared between the
 * user's devices only by the WebDAV sync ({@code webdav.SharedSettings}), the API key encrypted with
 * the WebDAV password; never through AppState or Google Drive.
 */
public class AiSettings {

    public static final String CLAUDE = "claude";
    public static final String OPENAI = "openai";

    public static final String CLAUDE_URL = "https://api.anthropic.com";
    public static final String CLAUDE_MODEL = "claude-opus-5-5";
    public static final String OPENAI_URL = "https://api.openai.com/v1";
    /** Characters of book text per request: Claude reads 1M tokens; OpenAI-compatible servers are often local and small. */
    public static final int CLAUDE_MAX_CHARS = 600_000;
    public static final int OPENAI_MAX_CHARS = 24_000;

    private static final String PREFS = "ai";

    public String provider = CLAUDE;
    public String url = CLAUDE_URL;
    public String apiKey = "";
    public String model = CLAUDE_MODEL;
    public int maxChars = CLAUDE_MAX_CHARS;
    /** Language of summaries; empty: the language of the book. */
    public String answerLanguage = "";
    public boolean highlightsVisible = true;
    /** Within shown AI highlights: the bars beside must-read paragraphs, and the key sentences. */
    public boolean barsVisible = true;
    public boolean sentencesVisible = true;
    /** The user's own instructions, sent before Librera's for every AI action; empty: none. */
    public String masterPrompt = "";

    /** Preference names shared between devices (WebDAV); {@code *.key} are the API keys. */
    public static final String[] SHARED = {"provider",
            CLAUDE + ".url", CLAUDE + ".model", CLAUDE + ".maxChars", CLAUDE + ".key",
            OPENAI + ".url", OPENAI + ".model", OPENAI + ".maxChars", OPENAI + ".key",
            "answerLanguage", "highlightsVisible", "barsVisible", "sentencesVisible", "masterPrompt"};
    private static final String MODIFIED = "modified";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static AiSettings load(Context c) {
        return load(c, prefs(c).getString("provider", CLAUDE));
    }

    /** The settings with the URL, key and model kept for {@code provider}. */
    public static AiSettings load(Context c, String provider) {
        final SharedPreferences p = prefs(c);
        final AiSettings s = new AiSettings();
        s.provider = CLAUDE.equals(provider) ? CLAUDE : OPENAI;
        final boolean claude = CLAUDE.equals(s.provider);
        s.url = p.getString(s.provider + ".url", claude ? CLAUDE_URL : OPENAI_URL);
        s.apiKey = p.getString(s.provider + ".key", "");
        s.model = p.getString(s.provider + ".model", claude ? CLAUDE_MODEL : "");
        s.maxChars = p.getInt(s.provider + ".maxChars", claude ? CLAUDE_MAX_CHARS : OPENAI_MAX_CHARS);
        s.answerLanguage = p.getString("answerLanguage", "");
        s.highlightsVisible = p.getBoolean("highlightsVisible", true);
        s.barsVisible = p.getBoolean("barsVisible", true);
        s.sentencesVisible = p.getBoolean("sentencesVisible", true);
        s.masterPrompt = p.getString("masterPrompt", "");
        return s;
    }

    /** Each provider keeps its own URL, key and model, so switching back and forth loses nothing. */
    public void save(Context c) {
        prefs(c).edit()
                .putString("provider", provider)
                .putString(provider + ".url", url.trim())
                .putString(provider + ".key", apiKey.trim())
                .putString(provider + ".model", model.trim())
                .putInt(provider + ".maxChars", maxChars)
                .putString("answerLanguage", answerLanguage.trim())
                .putBoolean("highlightsVisible", highlightsVisible)
                .putBoolean("barsVisible", barsVisible)
                .putBoolean("sentencesVisible", sentencesVisible)
                .putString("masterPrompt", masterPrompt.trim())
                .putLong(MODIFIED, System.currentTimeMillis())
                .apply();
    }

    /** When the settings were last saved on this device (0: never). */
    public static long modified(Context c) {
        return prefs(c).getLong(MODIFIED, 0);
    }

    /** The shared settings saved on this device, as text; unset ones are left out. */
    public static Map<String, String> shared(Context c) {
        final Map<String, ?> all = prefs(c).getAll();
        final Map<String, String> out = new LinkedHashMap<>();
        for (String name : SHARED) {
            final Object value = all.get(name);
            if (value != null) {
                out.put(name, String.valueOf(value));
            }
        }
        return out;
    }

    /** Settings that came from another device; the last-saved time is left as it is. */
    public static void applyShared(Context c, Map<String, String> values) {
        final SharedPreferences.Editor edit = prefs(c).edit();
        for (Map.Entry<String, String> e : values.entrySet()) {
            final String name = e.getKey();
            final String value = e.getValue();
            try {
                if (name.endsWith(".maxChars")) {
                    edit.putInt(name, Integer.parseInt(value));
                } else if (name.endsWith("Visible")) {
                    edit.putBoolean(name, Boolean.parseBoolean(value));
                } else {
                    for (String shared : SHARED) {
                        if (shared.equals(name)) {
                            edit.putString(name, value);
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                // a damaged value: this device keeps its own
            }
        }
        edit.apply();
    }

    public boolean isClaude() {
        return CLAUDE.equals(provider);
    }

    /** A model is set, and a key unless the server is local (LM Studio, Ollama need none). */
    public boolean isReady() {
        if (model.trim().isEmpty() || url.trim().isEmpty()) {
            return false;
        }
        return isClaude() ? !apiKey.trim().isEmpty() : true;
    }
}
