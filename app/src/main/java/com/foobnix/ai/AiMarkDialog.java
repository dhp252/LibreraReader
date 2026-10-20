package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * A tap on an AI mark: its category, the idea it carries, and Explain (one request, streamed, not
 * stored); All ideas opens the chapter's ideas (design-2026-10-21-layered-marks.md).
 */
public class AiMarkDialog implements AiJobs.ExplainScreen {

    private final Activity activity;
    private final DocumentController dc;
    private final AiHighlights.Mark mark;

    private AlertDialog dialog;
    private ProgressBar progress;
    private TextView status;
    private TextView text;
    private AiJobs.ExplainJob job;
    private boolean running;
    private boolean explained;

    private AiMarkDialog(Activity activity, DocumentController dc, AiHighlights.Mark mark) {
        this.activity = activity;
        this.dc = dc;
        this.mark = mark;
    }

    public static void show(Activity activity, DocumentController dc, AiHighlights.Band band) {
        if (activity == null || dc == null || dc.getCurrentBook() == null || band == null) {
            return;
        }
        final AiHighlights.Mark mark = AiHighlights.get().mark(band);
        if (mark != null) {
            new AiMarkDialog(activity, dc, mark).open();
        }
    }

    private void open() {
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_summary, null, false);
        progress = view.findViewById(R.id.aiProgress);
        status = view.findViewById(R.id.aiStatus);
        text = view.findViewById(R.id.aiText);
        progress.setVisibility(View.GONE);
        text.setText(about());

        final String title = activity.getString(R.string.ai_marked_as, activity.getString(AiLegend.nameOf(mark.quote.category)));
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(mark.paragraph ? title + " · " + activity.getString(R.string.ai_paragraph) : title)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.ai_explain, null);
        if (!mark.chapter.ideas.isEmpty()) {
            builder.setNegativeButton(R.string.ai_all_ideas, null);
        }
        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (running) {
                    stop();
                    onError("");
                } else {
                    dialog.dismiss();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> explain());
            final Button ideas = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (ideas != null) {
                ideas.setOnClickListener(v -> {
                    dialog.dismiss();
                    AiIdeasDialog.show(activity, dc, mark.chapter);
                });
            }
            updateButtons();
        });
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
    }

    /** The idea the mark carries and the one it supports; the marked words for key passages without ideas. */
    private CharSequence about() {
        final SpannableStringBuilder out = new SpannableStringBuilder();
        if (mark.idea == null) {
            out.append(mark.quote.text);
            return out;
        }
        bold(out, mark.idea.text);
        final AiStore.Idea parent = mark.idea.parent >= 0 ? mark.chapter.idea(mark.idea.parent) : null;
        if (parent != null) {
            final int start = out.length();
            out.append("\n").append(activity.getString(R.string.ai_idea_supports, parent.text));
            out.setSpan(new ForegroundColorSpan(0x99888888), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private static void bold(SpannableStringBuilder out, String s) {
        final int start = out.length();
        out.append(s);
        out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void explain() {
        stop();
        running = true;
        explained = true;
        text.setText(about());
        text.append("\n\n");
        status.setText("");
        progress.setVisibility(View.VISIBLE);
        updateButtons();
        final AiStore.Idea parent = mark.idea != null && mark.idea.parent >= 0 ? mark.chapter.idea(mark.idea.parent) : null;
        final String user = AiPrompts.explain(dc.getTitle(), mark.chapter.title, mark.idea == null ? "" : mark.idea.text,
                parent == null ? "" : parent.text, mark.quote.text, context());
        job = new AiJobs.ExplainJob(activity, AiJobs.Target.of(dc), user, this);
        job.start();
    }

    /** Around a key sentence: the must-read paragraph holding it, else the other marks of its idea. */
    private String context() {
        if (mark.paragraph) {
            return "";
        }
        final String key = QuoteMatcher.key(mark.quote.text);
        for (AiStore.Quote p : mark.chapter.paragraphs) {
            final String pk = QuoteMatcher.key(p.text);
            if (key != null && pk != null && pk.contains(key)) {
                return p.text;
            }
        }
        final StringBuilder out = new StringBuilder();
        if (mark.idea != null) {
            for (AiStore.Quote q : mark.chapter.quotes) {
                if (q.idea == mark.idea.id && q != mark.quote) {
                    out.append(q.text).append('\n');
                }
            }
        }
        return out.toString();
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
        running = false;
    }

    private void updateButtons() {
        final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive != null) {
            positive.setText(running ? R.string.cancel : R.string.close);
        }
        if (neutral != null) {
            neutral.setEnabled(!running);
            neutral.setText(explained ? R.string.ai_regenerate : R.string.ai_explain);
        }
    }

    // --- AiJobs.ExplainScreen, on the main thread ---

    @Override
    public void onText(String delta) {
        text.append(delta);
    }

    @Override
    public void onDone() {
        running = false;
        job = null;
        progress.setVisibility(View.GONE);
        updateButtons();
    }

    @Override
    public void onError(String message) {
        running = false;
        job = null;
        progress.setVisibility(View.GONE);
        // what streamed before a refusal or a broken connection is not an answer
        text.setText(about());
        status.setText(message);
        updateButtons();
    }

    @Override
    public void onNeedSettings() {
        dialog.dismiss();
        AiSettingsDialog.show(activity, null);
    }
}
