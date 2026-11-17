package com.foobnix.assist.lens;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSegmented;
import com.foobnix.canvas.CanvasSwitchRow;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.List;

/**
 * The lens setup (canvas LensSetup, "Explain it my way"): the fields the reader knows well as
 * chips with "Your own", their level in this book's subject, the three switches, a preview and
 * Save. The lens is for every book (the library file of the assist store); the level is the
 * book's. Asks the AI nothing.
 */
public final class LensSetupScreen {

    private final Activity a;
    private final String bookName;
    private final String bookTitle;
    private final LensProfile profile;
    private final LensProfile.Level level;
    private final Runnable onSaved;

    private Dialog dialog;
    private final List<TextView> chips = new ArrayList<>();
    private TextView ownChip;
    private EditText ownField;
    private CanvasSegmented levels;
    private CanvasSwitchRow analogies;
    private CanvasSwitchRow plainWords;
    private CanvasSwitchRow keepTerms;
    private TextView previewYours;
    private TextView save;

    private LensSetupScreen(Activity a, String bookName, String bookTitle, LensProfile profile, LensProfile.Level level,
                            Runnable onSaved) {
        this.a = a;
        this.bookName = bookName;
        this.bookTitle = bookTitle;
        this.profile = profile;
        this.level = level;
        this.onSaved = onSaved;
    }

    /**
     * Opens the setup with the stored lens.
     *
     * @param dc      the open book, whose level is set too; null leaves the level out
     * @param onSaved run on the main thread after Save, or null
     */
    public static void show(final Activity a, final DocumentController dc, final Runnable onSaved) {
        if (!Assist.LENS.isOn() || a == null) {
            return;
        }
        final boolean book = dc != null && dc.getCurrentBook() != null;
        final String name = book ? AiHighlights.bookName(dc.getCurrentBook().getPath()) : null;
        final String title = book ? dc.getTitle() : null;
        new Thread(() -> {
            LensProfile p = new LensProfile();
            LensProfile.Level l = LensProfile.Level.NEW;
            try {
                p = LensStore.profile();
                l = LensStore.level(name);
            } catch (Exception e) {
                LOG.e(e);
            }
            final LensProfile profile = p;
            final LensProfile.Level level = l;
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> new LensSetupScreen(a, name, title, profile, level, onSaved).open());
            }
        }, "@T lens").start();
    }

    private void open() {
        final LinearLayout page = LensUi.page(a);
        dialog = LensUi.fullScreen(a, page);
        page.addView(LensUi.topBar(a, a.getString(R.string.lens_setup_title), a.getString(R.string.lens_setup_subtitle),
                () -> dialog.dismiss(), null), LensUi.barParams());

        final ScrollView scroll = new ScrollView(a);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(12));
        // the page takes the first focus, so the keyboard waits for a tap on "Your own"
        body.setFocusableInTouchMode(true);
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(LensUi.mono(a, a.getString(R.string.lens_setup_label), CanvasLook.TEXT_LABEL, CanvasLook.accent(), true),
                LensUi.row(20, 0, 20, 0));
        final TextView heading = LensUi.text(a, a.getString(R.string.lens_setup_heading), 22, 600, CanvasLook.ink());
        body.addView(heading, LensUi.row(20, 4, 20, 0));
        body.addView(LensUi.text(a, a.getString(R.string.lens_setup_hint), 13, 400, CanvasLook.ink2()),
                LensUi.row(20, 4, 20, 0));

        body.addView(fields(), LensUi.row(16, 14, 16, 0));
        ownField = new EditText(a);
        ownField.setBackground(CanvasParts.field());
        ownField.setHint(R.string.lens_own_hint);
        ownField.setHintTextColor(CanvasLook.muted());
        ownField.setSingleLine(true);
        ownField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ownField.setTypeface(CanvasLook.ui(a, 400));
        ownField.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        ownField.setTextColor(CanvasLook.ink());
        ownField.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        ownField.setMinHeight(CanvasLook.dp(48));
        ownField.setText(profile.own);
        ownField.setVisibility(TextUtils.isEmpty(profile.own) ? View.GONE : View.VISIBLE);
        ownField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                profile.own = s.toString();
                refresh();
            }
        });
        body.addView(ownField, LensUi.row(16, 10, 16, 0));

        if (bookName != null) {
            final LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.BOTTOM);
            head.addView(LensUi.text(a, a.getString(R.string.lens_level), CanvasLook.TEXT_TITLE, 600, CanvasLook.ink()));
            // the book's title as it is: the subject is the book's
            final TextView of = LensUi.text(a, bookTitle == null ? "" : bookTitle, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            of.setSingleLine(true);
            of.setEllipsize(TextUtils.TruncateAt.END);
            of.setPadding(CanvasLook.dp(8), 0, 0, 0);
            head.addView(of, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            body.addView(head, LensUi.row(16, 18, 16, 0));
            levels = CanvasSegmented.create(a, a.getString(R.string.lens_level_new), a.getString(R.string.lens_level_some),
                    a.getString(R.string.lens_level_expert));
            levels.select(level.ordinal());
            body.addView(levels.getView(), LensUi.row(16, 6, 16, 0));
        }

        analogies = new CanvasSwitchRow(a, a.getString(R.string.lens_analogies), null);
        analogies.setChecked(profile.analogies);
        analogies.setOnCheckedChangeListener((b, on) -> {
            profile.analogies = on;
            refresh();
        });
        body.addView(analogies, LensUi.row(0, 12, 0, 0));
        body.addView(LensUi.line(a, CanvasLook.surface2()), LensUi.row(16, 0, 16, 0));
        plainWords = new CanvasSwitchRow(a, a.getString(R.string.lens_plain_words), a.getString(R.string.lens_plain_words_hint));
        plainWords.setChecked(profile.plainWords);
        plainWords.setOnCheckedChangeListener((b, on) -> profile.plainWords = on);
        body.addView(plainWords);
        body.addView(LensUi.line(a, CanvasLook.surface2()), LensUi.row(16, 0, 16, 0));
        keepTerms = new CanvasSwitchRow(a, a.getString(R.string.lens_keep_terms), a.getString(R.string.lens_keep_terms_hint));
        keepTerms.setChecked(profile.keepTerms);
        keepTerms.setOnCheckedChangeListener((b, on) -> {
            profile.keepTerms = on;
            refresh();
        });
        body.addView(keepTerms);

        body.addView(preview(), LensUi.row(16, 12, 16, 0));

        save = LensUi.button(a, a.getString(R.string.lens_save), true);
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        save.setOnClickListener(v -> save());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52));
        lp.setMargins(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(16));
        page.addView(save, lp);

        refresh();
        dialog.show();
    }

    /** The preset chips and "Your own". */
    private View fields() {
        final LensFlow flow = new LensFlow(a, CanvasLook.dp(8));
        for (final String preset : LensProfile.PRESETS) {
            final TextView chip = chip(preset);
            chip.setOnClickListener(v -> {
                profile.toggle(preset);
                refresh();
            });
            chips.add(chip);
            flow.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.dp(40)));
        }
        ownChip = chip(a.getString(R.string.lens_own));
        ownChip.setOnClickListener(v -> {
            ownField.setVisibility(View.VISIBLE);
            ownField.requestFocus();
            final InputMethodManager keys = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keys != null) {
                keys.showSoftInput(ownField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        flow.addView(ownChip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.dp(40)));
        return flow;
    }

    private TextView chip(String name) {
        final TextView t = LensUi.text(a, name, CanvasLook.TEXT_TITLE, 400, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        return t;
    }

    private View preview() {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        card.addView(LensUi.mono(a, a.getString(R.string.lens_preview), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false));
        card.addView(previewLine(LensUi.text(a, LensPreview.AUTHOR, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()),
                LensUi.serif(a, "“" + LensPreview.PHRASE + "”", 15, CanvasLook.ink())));
        previewYours = LensUi.text(a, "", CanvasLook.TEXT_TITLE, 400, CanvasLook.ink());
        card.addView(previewLine(LensUi.text(a, a.getString(R.string.lens_preview_you), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent()),
                previewYours));
        return card;
    }

    private View previewLine(TextView who, TextView what) {
        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, CanvasLook.dp(8), 0, 0);
        line.addView(who, new LinearLayout.LayoutParams(CanvasLook.dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
        line.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return line;
    }

    /** Draws the chips, the first switch's line, the preview and Save from the lens as it stands. */
    private void refresh() {
        for (int i = 0; i < chips.size(); i++) {
            styleChip(chips.get(i), LensProfile.PRESETS[i], profile.has(LensProfile.PRESETS[i]), false);
        }
        final boolean own = !profile.ownFields().isEmpty();
        styleChip(ownChip, a.getString(R.string.lens_own), own, !own);
        final boolean set = profile.isSet();
        analogies.setSubtitle(set ? a.getString(R.string.lens_analogies_hint, profile.through())
                : a.getString(R.string.lens_analogies_hint_none));
        previewYours.setText(set ? LensPreview.yours(profile) : a.getString(R.string.lens_preview_none));
        previewYours.setTextColor(set ? CanvasLook.ink() : CanvasLook.muted());
        save.setAlpha(set ? 1f : 0.45f);
    }

    private void styleChip(TextView chip, String name, boolean on, boolean dashed) {
        chip.setText(on ? "✓ " + name : dashed ? "+ " + name : name);
        chip.setTypeface(CanvasLook.ui(a, on ? 600 : 400));
        chip.setTextColor(on ? CanvasLook.accent() : dashed ? CanvasLook.ink2() : CanvasLook.ink());
        chip.setSelected(on);
        chip.setBackground(CanvasParts.ripple(on
                ? LensUi.outline(CanvasLook.accentTint(), CanvasLook.accent(), 15, 20, false)
                : LensUi.outline(CanvasLook.bg(), dashed ? CanvasLook.muted() : CanvasLook.lineStrong(), 10, 20, dashed)));
    }

    private void save() {
        if (!profile.isSet()) {
            Toast.makeText(a, R.string.lens_pick_field, Toast.LENGTH_LONG).show();
            return;
        }
        profile.own = ownField.getText().toString().trim();
        final LensProfile.Level picked = levels == null ? null : LensProfile.Level.values()[Math.max(0, levels.getSelected())];
        LensUi.enable(save, false);
        new Thread(() -> {
            String error = null;
            try {
                LensStore.saveProfile(profile);
                if (picked != null && bookName != null) {
                    LensStore.saveLevel(bookName, picked);
                }
            } catch (Exception e) {
                LOG.e(e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String failed = error;
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (failed != null) {
                    LensUi.enable(save, true);
                    Toast.makeText(a, failed, Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
            });
        }, "@T lens").start();
    }
}
