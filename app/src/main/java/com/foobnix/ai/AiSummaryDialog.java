package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.text.DateFormat;
import java.util.Date;

/**
 * A summary of the chapter at the reader's page or of the whole book: the saved one, or a new one
 * shown as the model writes it. Copy, share, and Regenerate to ask again.
 */
public class AiSummaryDialog implements AiJobs.SummaryScreen {

    private final Activity activity;
    private final AiJobs.Target target;
    private final boolean wholeBook;
    /** A study chapter to summarize, else the chapter at the reader's page. */
    private final BookChapters.Range chapter;

    private AlertDialog dialog;
    private ProgressBar progress;
    private TextView status;
    private TextView text;
    private ImageView copy;
    private ImageView share;
    private AiJobs.SummaryJob job;
    private boolean running;

    private AiSummaryDialog(Activity activity, AiJobs.Target target, boolean wholeBook, BookChapters.Range chapter) {
        this.activity = activity;
        this.target = target;
        this.wholeBook = wholeBook;
        this.chapter = chapter;
    }

    public static void show(Activity activity, DocumentController dc, boolean wholeBook) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new AiSummaryDialog(activity, AiJobs.Target.of(dc), wholeBook, null).open();
    }

    /** The summary of one chapter, wherever the reader is (study mode's chapters). */
    public static void show(Activity activity, DocumentController dc, BookChapters.Range chapter) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new AiSummaryDialog(activity, AiJobs.Target.of(dc), false, chapter).open();
    }

    private void open() {
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_summary, null, false);
        progress = view.findViewById(R.id.aiProgress);
        status = view.findViewById(R.id.aiStatus);
        text = view.findViewById(R.id.aiText);
        copy = view.findViewById(R.id.aiCopy);
        share = view.findViewById(R.id.aiShare);
        TintUtil.setTintImageWithAlpha(copy, TintUtil.color);
        TintUtil.setTintImageWithAlpha(share, TintUtil.color);
        copy.setOnClickListener(v -> copy());
        share.setOnClickListener(v -> share());

        dialog = new AlertDialog.Builder(activity)
                .setTitle(wholeBook ? target.title : activity.getString(R.string.ai_summary_chapter))
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.ai_regenerate, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> start(true));
            updateButtons();
        });
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        start(false);
    }

    private void start(boolean regenerate) {
        stop();
        running = true;
        text.setText("");
        status.setText("");
        progress.setVisibility(View.VISIBLE);
        updateButtons();
        job = new AiJobs.SummaryJob(activity, target, wholeBook, regenerate, this, chapter);
        job.start();
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
    }

    private void updateButtons() {
        final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive != null) {
            positive.setText(running ? R.string.cancel : R.string.close);
        }
        if (neutral != null) {
            neutral.setEnabled(!running);
        }
        final int tools = !running && text.length() > 0 ? View.VISIBLE : View.GONE;
        copy.setVisibility(tools);
        share.setVisibility(tools);
    }

    private void copy() {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(target.title, text.getText()));
            Toast.makeText(activity, R.string.copy_text, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, target.title);
        intent.putExtra(Intent.EXTRA_TEXT, text.getText().toString());
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)));
    }

    // --- AiJobs.SummaryScreen, on the main thread ---

    @Override
    public void onTitle(String title) {
        dialog.setTitle(title);
    }

    @Override
    public void onStatus(String message) {
        status.setText(message);
    }

    @Override
    public void onAnswerStart() {
        text.setText("");
    }

    @Override
    public void onText(String delta) {
        text.append(delta);
    }

    @Override
    public void onDone(AiStore.Summary summary, boolean fromStore, String note) {
        running = false;
        progress.setVisibility(View.GONE);
        text.setText(summary.text);
        if (note != null) {
            status.setText(note);
        } else {
            final String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(summary.time));
            status.setText(activity.getString(R.string.ai_saved_on, date, summary.model));
        }
        updateButtons();
    }

    @Override
    public void onError(String message) {
        running = false;
        progress.setVisibility(View.GONE);
        // what streamed before a refusal or a broken connection is not an answer
        text.setText("");
        status.setText(message);
        updateButtons();
    }

    @Override
    public void onNeedSettings() {
        dialog.dismiss();
        AiSettingsDialog.show(activity, null);
    }
}
