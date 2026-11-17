package com.foobnix.assist.lens;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSheet;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * "Works on any sentence" (canvas LensLadder): for text selected in the book, a sheet with the
 * sentence and two buttons, "Boil it down" (up the ladder) and "Make it concrete" (down); each is
 * one request on its tap, and the answer shows under the sentence. Nothing is kept.
 */
public final class LensSentence implements LensJob.Screen<String> {

    private final Activity a;
    private final DocumentController dc;
    private final String sentence;

    private Dialog dialog;
    private TextView resultLabel;
    private TextView result;
    private ProgressBar progress;
    private TextView status;
    private TextView up;
    private TextView down;
    private LensJob.Sentence job;
    private boolean askedConcrete;

    private LensSentence(Activity a, DocumentController dc, String sentence) {
        this.a = a;
        this.dc = dc;
        this.sentence = sentence;
    }

    /**
     * The one entry of the text-selection box: an "In your terms" chip at the start of its row of
     * dictionaries. Nothing is added with Assist.LENS off.
     *
     * @param line     the box's row of dictionary apps
     * @param selected holds the selected text (the reader may have edited it)
     * @param close    closes the selection box
     */
    public static void addTo(LinearLayout line, final DocumentController dc, final TextView selected, final Runnable close) {
        if (!Assist.LENS.isOn() || line == null || dc == null || selected == null) {
            return;
        }
        final TextView chip = LensUi.text(line.getContext(), line.getContext().getString(R.string.lens_in_your_terms), 13, 600,
                CanvasLook.accent());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        chip.setBackground(CanvasParts.ripple(CanvasParts.chip(true)));
        chip.setOnClickListener(v -> {
            final String text = LensPrompts.sentence(selected.getText().toString());
            if (text.isEmpty()) {
                return;
            }
            if (close != null) {
                close.run();
            }
            dc.clearSelectedText();
            show(dc.getActivity(), dc, text);
        });
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target());
        lp.rightMargin = CanvasLook.dp(8);
        line.addView(chip, 0, lp);
    }

    /** The sheet for one selected text; the lens setup first when there is no lens yet. */
    public static void show(final Activity a, final DocumentController dc, final String sentence) {
        if (!Assist.LENS.isOn() || a == null || dc == null || dc.getCurrentBook() == null || TextUtils.isEmpty(sentence)) {
            return;
        }
        new Thread(() -> {
            boolean set = false;
            try {
                set = LensStore.profile().isSet();
            } catch (Exception e) {
                LOG.e(e);
            }
            final boolean hasLens = set;
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (hasLens) {
                    new LensSentence(a, dc, sentence).open();
                } else {
                    LensSetupScreen.show(a, dc, () -> new LensSentence(a, dc, sentence).open());
                }
            });
        }, "@T lens").start();
    }

    private void open() {
        final LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(LensUi.mono(a, a.getString(R.string.lens_in_your_terms), CanvasLook.TEXT_LABEL, CanvasLook.accent(), true));

        final TextView original = LensUi.serif(a, "“" + sentence + "”", 14, CanvasLook.ink2());
        original.setLineSpacing(0, 1.3f);
        original.setMaxLines(8);
        original.setEllipsize(TextUtils.TruncateAt.END);
        original.setPadding(0, CanvasLook.dp(8), 0, 0);
        content.addView(original);

        resultLabel = LensUi.text(a, "", CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        resultLabel.setPadding(0, CanvasLook.dp(12), 0, 0);
        resultLabel.setVisibility(View.GONE);
        content.addView(resultLabel);
        result = LensUi.text(a, "", CanvasLook.TEXT_TITLE, 400, CanvasLook.ink());
        result.setLineSpacing(CanvasLook.dp(3), 1f);
        result.setPadding(0, CanvasLook.dp(4), 0, 0);
        result.setTextIsSelectable(true);
        result.setVisibility(View.GONE);
        content.addView(result);

        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, CanvasLook.dp(10), 0, 0);
        progress = new ProgressBar(a);
        progress.setVisibility(View.GONE);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        status = LensUi.text(a, a.getString(R.string.lens_sentence_note), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(line);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        up = LensUi.button(a, a.getString(R.string.lens_boil_down_up), false);
        up.setOnClickListener(v -> ask(false));
        down = LensUi.button(a, a.getString(R.string.lens_make_concrete_down), false);
        down.setOnClickListener(v -> ask(true));
        buttons.addView(up, new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1));
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(down, right);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(10);
        content.addView(buttons, lp);

        // a long answer scrolls inside the sheet
        final ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        dialog = CanvasSheet.show(a, scroll);
        dialog.setOnDismissListener(d -> stop());
    }

    /** One request for the tapped direction. */
    private void ask(boolean concrete) {
        if (job != null || !Assist.LENS.isOn()) {
            return;
        }
        askedConcrete = concrete;
        job = new LensJob.Sentence(a, AiJobs.Target.of(dc), LensBridgeScreen.author(dc), sentence, concrete, this);
        busy(true, a.getString(concrete ? R.string.lens_making_concrete : R.string.lens_boiling_down));
        job.start();
    }

    private void busy(boolean running, String message) {
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        status.setPadding(CanvasLook.dp(running ? 6 : 0), 0, 0, 0);
        status.setText(message);
        status.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        LensUi.enable(up, !running);
        LensUi.enable(down, !running);
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
    }

    @Override
    public void onDone(String text) {
        job = null;
        if (!dialog.isShowing()) {
            return;
        }
        busy(false, "");
        resultLabel.setText(askedConcrete ? R.string.lens_make_concrete_down : R.string.lens_boil_down_up);
        resultLabel.setVisibility(View.VISIBLE);
        result.setText(text);
        result.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(String message) {
        job = null;
        if (dialog.isShowing()) {
            busy(false, message);
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (dialog.isShowing()) {
            busy(false, a.getString(R.string.lens_need_key));
        }
        AiSettingsDialog.show(a, null);
    }
}
