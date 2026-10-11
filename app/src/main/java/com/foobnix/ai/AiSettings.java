package com.foobnix.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI settings. They stay on this device: private preferences, never AppState, which is synced
 * (Google Drive, WebDAV) - the API key must not leave the device.
 */
public class AiSettings {

    public static final String CLAUDE = "claude";
    public static final String OPENAI = "openai";

    public static final String CLAUDE_URL = "https://api.anthropic.com";
    public static final String CLAUDE_MODEL = "claude-opus-5-5";
    public static final String OPENAI_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_HIGHLIGHT_COLOR = "#a28ae5";
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
    public String highlightColor = DEFAULT_HIGHLIGHT_COLOR;
    public boolean highlightsVisible = true;

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
        s.highlightColor = p.getString("highlightColor", DEFAULT_HIGHLIGHT_COLOR);
        s.highlightsVisible = p.getBoolean("highlightsVisible", true);
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
                .putString("highlightColor", highlightColor)
                .putBoolean("highlightsVisible", highlightsVisible)
                .apply();
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
