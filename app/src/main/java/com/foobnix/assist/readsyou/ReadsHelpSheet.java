package com.foobnix.assist.readsyou;

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
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.lens.LensProfile;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSheet;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingTracker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Want it another way?" (canvas "Struggle detected, help offered"): a sheet for the page the
 * reader is on, or the text they selected, with the tabs Plain / Example and, when the Lens is on
 * and set up, the reader's field ("ML terms"). The passage that would be sent is shown first;
 * each tab is one AI request on its first tap, and nothing is sent before a tap. The answers are
 * shown, not kept.
 */
final class ReadsHelpSheet implements ReadsHelpJob.Screen {

    /** Why the sheet opened. */
    static final int BY_HAND = 0;
    static final int REREAD = 1;
    static final int DWELL = 2;

    /** What the session hears. */
    interface Listener {
        /** The AI was asked for the first time on this sheet: the page becomes a spot to review. */
        void onHelpTaken();
    }

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final int why;
    private final long pageMs;
    private final Listener listener;

    private Dialog dialog;
    private LinearLayout content;
    private LinearLayout tabs;
    private final List<ReadsPrompts.Kind> kinds = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final Map<ReadsPrompts.Kind, String> answers = new EnumMap<>(ReadsPrompts.Kind.class);
    private ReadsPrompts.Kind chosen;
    private ReadsHelpJob job;
    private String passage = "";
    private boolean taken;

    private TextView resultLabel;
    private TextView result;
    private ProgressBar progress;
    private TextView status;
    private TextView sentNote;
    private TextView footNote;

    private ReadsHelpSheet(Activity a, DocumentController dc, int why, long pageMs, Listener listener) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.why = why;
        this.pageMs = pageMs;
        this.listener = listener;
    }

    /**
     * Opens the sheet for the page shown. Reads the page's text and the reader's lens from the
     * device; sends nothing.
     *
     * @param pageMs how long the page has been on screen, for the label of a long page
     */
    static void show(Activity a, DocumentController dc, int why, long pageMs, Listener listener) {
        if (!Assist.READS_YOU.isOn() || a == null || Apps.isDestroyedActivity(a) || dc == null
                || dc.getCurrentBook() == null) {
            return;
        }
        new ReadsHelpSheet(a, dc, why, pageMs, listener).open();
    }

    private void open() {
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        final String label = why == REREAD ? a.getString(R.string.canvas_t31_help_label_reread)
                : why == DWELL ? a.getString(R.string.canvas_t31_help_label_dwell, ReadsUi.clock(pageMs))
                : a.getString(R.string.canvas_t31_help_label_hand);
        content.addView(ReadsUi.mono(a, label, CanvasLook.sensor()));
        final TextView title = ReadsUi.text(a, a.getString(R.string.canvas_t31_help_title), 17, 600, CanvasLook.ink());
        title.setPadding(0, CanvasLook.dp(4), 0, 0);
        content.addView(title);

        tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(CanvasParts.segmentTrack());
        final int pad = CanvasLook.dp(3);
        tabs.setPadding(pad, pad, pad, pad);
        tabs.setVisibility(View.GONE);
        content.addView(tabs, ReadsUi.matchWrap(12));

        resultLabel = ReadsUi.mono(a, "", CanvasLook.accent());
        resultLabel.setVisibility(View.GONE);
        content.addView(resultLabel, ReadsUi.matchWrap(12));
        result = ReadsUi.text(a, "", CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
        result.setLineSpacing(CanvasLook.dp(3), 1f);
        result.setTextIsSelectable(true);
        result.setVisibility(View.GONE);
        content.addView(result, ReadsUi.matchWrap(4));

        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(a);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        status = ReadsUi.text(a, a.getString(R.string.canvas_t31_help_reading), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        status.setPadding(CanvasLook.dp(6), 0, 0, 0);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(line, ReadsUi.matchWrap(10));

        final ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        dialog = CanvasSheet.show(a, scroll);
        dialog.setOnDismissListener(d -> stop());

        // the passage and the lens: read from the device, nothing is sent
        final String selected = AppState.get().selectedText;
        final int[] real = ReadingTracker.realPages(dc, dc.getCurentPageFirst1());
        new Thread(() -> {
            final StringBuilder page = new StringBuilder();
            if (selected == null || selected.trim().isEmpty()) {
                try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                    if (text != null) {
                        for (int p = real[0] - 1; p <= real[1] - 1 && p < text.pageCount(); p++) {
                            if (p >= 0) {
                                page.append(BookText.plain(text.body(p))).append("\n\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.e(e);
                }
            }
            LensProfile lens = null;
            try {
                lens = ReadsHelpJob.lens();
            } catch (Exception e) {
                LOG.e(e);
            }
            final LensProfile profile = lens;
            final boolean fromSelection = selected != null && !selected.trim().isEmpty();
            final String sent = ReadsPrompts.passage(selected, page.toString());
            final boolean cut = !fromSelection && ReadsPrompts.isCut(page.toString());
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    ready(sent, fromSelection, cut, profile);
                }
            });
        }, "@T reads help").start();
    }

    /** The passage is known: the tabs, what would be sent, and the buttons. */
    private void ready(String sent, boolean fromSelection, boolean cut, LensProfile lens) {
        passage = sent;
        progress.setVisibility(View.GONE);
        status.setPadding(0, 0, 0, 0);
        if (passage.isEmpty()) {
            status.setText(R.string.canvas_t31_help_no_text);
            return;
        }
        status.setText("");
        status.setVisibility(View.GONE);

        kinds.add(ReadsPrompts.Kind.PLAIN);
        kinds.add(ReadsPrompts.Kind.EXAMPLE);
        addTab(a.getString(R.string.canvas_t31_help_plain));
        addTab(a.getString(R.string.canvas_t31_help_example));
        if (lens != null) {
            kinds.add(ReadsPrompts.Kind.FIELD);
            // the canvas's "ML terms": the reader's own field, named as the Lens names it
            addTab(a.getString(R.string.canvas_t31_help_field, lens.shortName()));
        }
        tabs.setVisibility(View.VISIBLE);

        final TextView sentLabel = ReadsUi.mono(a, a.getString(fromSelection ? R.string.canvas_t31_help_sent_selection
                : cut ? R.string.canvas_t31_help_sent_page_cut : R.string.canvas_t31_help_sent_page), CanvasLook.muted());
        content.addView(sentLabel, ReadsUi.matchWrap(14));
        final TextView words = ReadsUi.book(a, "“" + passage + "”", 13, CanvasLook.ink2());
        words.setMaxLines(4);
        words.setEllipsize(TextUtils.TruncateAt.END);
        words.setOnClickListener(v -> {
            // the whole passage, as it is sent
            words.setMaxLines(Integer.MAX_VALUE);
            words.setEllipsize(null);
        });
        content.addView(words, ReadsUi.matchWrap(4));
        sentNote = ReadsUi.text(a, a.getString(R.string.canvas_t31_help_sent_note), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        content.addView(sentNote, ReadsUi.matchWrap(6));

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView fuzzy = ReadsUi.button(a, a.getString(R.string.canvas_t31_help_fuzzy), false);
        fuzzy.setOnClickListener(v -> fuzzy());
        buttons.addView(fuzzy, new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1));
        final TextView got = ReadsUi.button(a, a.getString(R.string.canvas_t31_help_got_it), true);
        got.setOnClickListener(v -> dialog.dismiss());
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(got, right);
        content.addView(buttons, ReadsUi.matchWrap(14));

        footNote = ReadsUi.text(a, a.getString(R.string.canvas_t31_help_foot), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        footNote.setGravity(Gravity.CENTER);
        footNote.setVisibility(View.GONE);
        content.addView(footNote, ReadsUi.matchWrap(8));
    }

    private void addTab(String label) {
        final int index = tabViews.size();
        final TextView t = ReadsUi.text(a, label, 13, 600, CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(4), 0);
        t.setOnClickListener(v -> pick(index));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, CanvasLook.target(), 1);
        lp.leftMargin = index == 0 ? 0 : CanvasLook.dp(2);
        tabs.addView(t, lp);
        tabViews.add(t);
    }

    /** A tab was tapped: its answer when it has one, else its one request. */
    private void pick(int index) {
        if (job != null || index < 0 || index >= kinds.size()) {
            // one request at a time: the tabs wait for it
            return;
        }
        chosen = kinds.get(index);
        for (int i = 0; i < tabViews.size(); i++) {
            final boolean on = i == index;
            tabViews.get(i).setBackground(on ? CanvasParts.segmentOn() : null);
            tabViews.get(i).setTextColor(on ? CanvasLook.ink() : CanvasLook.ink2());
        }
        final String known = answers.get(chosen);
        if (known != null) {
            showAnswer(chosen, known);
            return;
        }
        if (!Assist.READS_YOU.isOn()) {
            return;
        }
        resultLabel.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
        busy(true, a.getString(R.string.canvas_t31_help_asking));
        job = new ReadsHelpJob(a, target, author(), passage, chosen, this);
        job.start();
    }

    private void fuzzy() {
        boolean left = false;
        for (ReadsPrompts.Kind k : kinds) {
            left |= !answers.containsKey(k);
        }
        busy(false, a.getString(taken ? (left ? R.string.canvas_t31_help_fuzzy_more : R.string.canvas_t31_help_fuzzy_all)
                : R.string.canvas_t31_help_fuzzy_first));
    }

    private void busy(boolean running, String message) {
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        status.setPadding(CanvasLook.dp(running ? 6 : 0), 0, 0, 0);
        status.setText(message);
        status.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    private void showAnswer(ReadsPrompts.Kind kind, String text) {
        busy(false, "");
        resultLabel.setText(kind == ReadsPrompts.Kind.EXAMPLE ? R.string.canvas_t31_help_by_ai_example
                : kind == ReadsPrompts.Kind.FIELD ? R.string.canvas_t31_help_by_ai_field : R.string.canvas_t31_help_by_ai_plain);
        resultLabel.setVisibility(View.VISIBLE);
        result.setText(text);
        result.setVisibility(View.VISIBLE);
    }

    private String author() {
        try {
            final String s = dc.getBookFileMeta().getAuthor();
            if (s == null) {
                return "";
            }
            final String first = s.split("[,;&]")[0].trim();
            return first.length() > 40 ? "" : first;
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
    }

    @Override
    public void onDone(ReadsPrompts.Kind kind, String text) {
        job = null;
        answers.put(kind, text);
        if (!taken) {
            taken = true;
            if (listener != null) {
                listener.onHelpTaken();
            }
        }
        if (!dialog.isShowing()) {
            return;
        }
        if (sentNote != null) {
            sentNote.setText(R.string.canvas_t31_help_sent_done);
        }
        if (footNote != null) {
            footNote.setVisibility(View.VISIBLE);
        }
        if (kind == chosen) {
            showAnswer(kind, text);
        }
    }

    @Override
    public void onError(ReadsPrompts.Kind kind, String message) {
        job = null;
        if (dialog.isShowing()) {
            busy(false, message);
        }
    }

    @Override
    public void onNeedSettings(ReadsPrompts.Kind kind) {
        job = null;
        if (dialog.isShowing()) {
            busy(false, a.getString(R.string.canvas_t31_help_need_key));
        }
        AiSettingsDialog.show(a, null);
    }
}
