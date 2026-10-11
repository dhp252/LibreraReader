package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * Progress of a key passages run: Hide lets it go on in the background (the result comes as a
 * message), Cancel stops it. Chapters already marked keep their highlights either way.
 */
public class AiHighlightDialog implements AiJobs.HighlightScreen {

    private final Activity activity;
    private final AiJobs.HighlightJob job;
    private AlertDialog dialog;
    private TextView status;
    private ProgressBar progress;

    private AiHighlightDialog(Activity activity, AiJobs.HighlightJob job) {
        this.activity = activity;
        this.job = job;
    }

    /** Starts a run, or shows the one going on. */
    public static void start(Activity activity, DocumentController dc, boolean wholeBook) {
        final AiJobs.HighlightJob runningJob = AiJobs.runningHighlights();
        if (runningJob != null) {
            showRunning(activity, runningJob);
            return;
        }
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.HighlightJob job = new AiJobs.HighlightJob(activity, AiJobs.Target.of(dc), wholeBook, null);
        final AiHighlightDialog screen = new AiHighlightDialog(activity, job);
        job.setScreen(screen);
        screen.open();
        job.start();
    }

    public static void showRunning(Activity activity, AiJobs.HighlightJob job) {
        final AiHighlightDialog screen = new AiHighlightDialog(activity, job);
        job.setScreen(screen);
        screen.open();
        screen.onStatus(job.getStatus());
    }

    private void open() {
        final LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        final int pad = Dips.dpToPx(16);
        view.setPadding(pad, pad, pad, pad / 2);
        progress = new ProgressBar(activity);
        view.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(32), Dips.dpToPx(32)));
        status = new TextView(activity);
        status.setPadding(pad, 0, 0, 0);
        view.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.ai_key_passages)
                .setView(view)
                .setPositiveButton(R.string.ai_hide, (d, w) -> job.setScreen(null))
                .setNegativeButton(R.string.cancel, (d, w) -> job.cancel())
                .create();
        // back or a tap outside hides the progress; the run goes on
        dialog.setOnCancelListener(d -> job.setScreen(null));
        dialog.show();
    }

    @Override
    public void onStatus(String message) {
        status.setText(message);
    }

    @Override
    public void onFinished(String message) {
        job.setScreen(null);
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(String message) {
        job.setScreen(null);
        progress.setVisibility(android.view.View.GONE);
        status.setText(message);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(android.view.View.GONE);
        }
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.close);
        }
    }

    @Override
    public void onNeedSettings() {
        job.setScreen(null);
        dialog.dismiss();
        AiSettingsDialog.show(activity, null);
    }
}
