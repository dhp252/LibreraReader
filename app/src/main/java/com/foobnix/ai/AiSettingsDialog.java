package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Provider, server, key and model, the language of summaries and the color of AI highlights.
 * Everything here stays on the device ({@link AiSettings}); Test sends a tiny request.
 */
public class AiSettingsDialog {

    /** Highlight colors to pick from: none of them is a default annotation or read-aloud color. */
    private static final String[] COLORS = {"#a28ae5", "#5fb236", "#f19837", "#e56eee", "#2ec4b6", "#aaaaaa"};

    private final Activity activity;
    private final Runnable onSaved;
    private final Map<String, AiSettings> edited = new HashMap<>();
    private AiSettings settings;
    private String color;

    private RadioGroup provider;
    private EditText url;
    private EditText key;
    private EditText model;
    private EditText language;
    private EditText maxChars;
    private LinearLayout colors;
    private TextView result;

    private AiSettingsDialog(Activity activity, Runnable onSaved) {
        this.activity = activity;
        this.onSaved = onSaved;
    }

    public static void show(Activity activity, Runnable onSaved) {
        new AiSettingsDialog(activity, onSaved).open();
    }

    /** The AI section of the preferences: what is set up, and the way to these settings. */
    public static void bindPreferences(final Activity a, final View root) {
        final View header = root.findViewById(R.id.aiHeader);
        if (header == null) {
            return;
        }
        TintUtil.setSectionFillColor(header, TintUtil.color);
        root.findViewById(R.id.aiPrefSettings).setOnClickListener(v -> show(a, () -> updatePreferences(a, root)));
        updatePreferences(a, root);
    }

    private static void updatePreferences(Activity a, View root) {
        final TextView status = root.findViewById(R.id.aiPrefStatus);
        final AiSettings s = AiSettings.load(a);
        status.setText(s.isReady()
                ? a.getString(s.isClaude() ? R.string.ai_provider_claude : R.string.ai_provider_openai) + " · " + s.model
                : a.getString(R.string.webdav_not_set_up));
    }

    private void open() {
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_settings, null, false);
        provider = view.findViewById(R.id.aiProvider);
        url = view.findViewById(R.id.aiUrl);
        key = view.findViewById(R.id.aiKey);
        model = view.findViewById(R.id.aiModel);
        language = view.findViewById(R.id.aiLanguage);
        maxChars = view.findViewById(R.id.aiMaxChars);
        colors = view.findViewById(R.id.aiColors);
        result = view.findViewById(R.id.aiTestResult);
        final CheckBox showKey = view.findViewById(R.id.aiShowKey);
        showKey.setOnCheckedChangeListener((b, checked) -> {
            key.setInputType(InputType.TYPE_CLASS_TEXT | (checked
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            key.setSelection(key.length());
        });

        settings = AiSettings.load(activity);
        color = settings.highlightColor;
        fill();
        provider.check(settings.isClaude() ? R.id.aiClaude : R.id.aiOpenAi);
        provider.setOnCheckedChangeListener((group, checkedId) -> {
            // each provider keeps its own server, key and model, also what was typed for it here
            final String chosen = checkedId == R.id.aiClaude ? AiSettings.CLAUDE : AiSettings.OPENAI;
            if (!chosen.equals(settings.provider)) {
                capture();
                edited.put(settings.provider, settings);
                final AiSettings other = edited.containsKey(chosen) ? edited.get(chosen) : AiSettings.load(activity, chosen);
                other.answerLanguage = settings.answerLanguage;
                other.highlightsVisible = settings.highlightsVisible;
                settings = other;
                fill();
            }
        });
        showColors();

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.ai_settings)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.ai_test, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> test());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!read()) {
                    return;
                }
                settings.save(activity);
                AiHighlights.get().setColor(settings.highlightColor);
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
            });
        });
        dialog.show();
    }

    private void fill() {
        url.setText(settings.url);
        key.setText(settings.apiKey);
        key.setHint(settings.isClaude() ? R.string.ai_key : R.string.ai_key_optional);
        model.setText(settings.model);
        language.setText(settings.answerLanguage);
        maxChars.setText(String.valueOf(settings.maxChars));
        result.setVisibility(View.GONE);
    }

    /** The fields into {@link #settings}. */
    private void capture() {
        settings.url = url.getText().toString().trim();
        settings.apiKey = key.getText().toString().trim();
        settings.model = model.getText().toString().trim();
        settings.answerLanguage = language.getText().toString().trim();
        settings.highlightColor = color;
        try {
            settings.maxChars = Math.max(2000, Integer.parseInt(maxChars.getText().toString().trim()));
        } catch (NumberFormatException e) {
            settings.maxChars = settings.isClaude() ? AiSettings.CLAUDE_MAX_CHARS : AiSettings.OPENAI_MAX_CHARS;
        }
    }

    /** The fields into {@link #settings}; false (and a message) when the URL is no URL. */
    private boolean read() {
        capture();
        if (HttpUrl.parse(settings.url) == null) {
            result.setVisibility(View.VISIBLE);
            result.setText(activity.getString(R.string.fail) + ": " + activity.getString(R.string.ai_url));
            return false;
        }
        return true;
    }

    private void test() {
        if (!read()) {
            return;
        }
        final AiSettings testing = settings;
        result.setVisibility(View.VISIBLE);
        result.setText(R.string.please_wait);
        new Thread(() -> {
            String answer;
            try {
                answer = new AiClient(testing).complete("Reply with the single word OK.", "Hello", null).trim();
                if (answer.length() > 60) {
                    answer = answer.substring(0, 60) + "…";
                }
                answer = activity.getString(R.string.ai_test_ok, testing.model + ": " + answer);
            } catch (Exception e) {
                answer = activity.getString(R.string.fail) + ": " + (TxtUtils.isNotEmpty(e.getMessage())
                        ? e.getMessage() : e.getClass().getSimpleName());
            }
            final String text = answer;
            activity.runOnUiThread(() -> result.setText(text));
        }, "@T AI test").start();
    }

    private void showColors() {
        colors.removeAllViews();
        final int size = Dips.dpToPx(32);
        final int margin = Dips.dpToPx(6);
        for (final String c : COLORS) {
            final View swatch = new View(activity);
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(c));
            if (c.equalsIgnoreCase(color)) {
                circle.setStroke(Dips.dpToPx(3), Color.DKGRAY);
            }
            swatch.setBackground(circle);
            swatch.setContentDescription(c);
            swatch.setOnClickListener(v -> {
                color = c;
                showColors();
            });
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            colors.addView(swatch, params);
        }
    }
}
