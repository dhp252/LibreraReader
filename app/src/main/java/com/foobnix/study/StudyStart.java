package com.foobnix.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiLegend;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * Starting to study a book: the AI settings and the reading goal first, then the preview with its
 * progress (Hide lets it go on, Cancel stops it). A book studied before comes back without a new
 * preview.
 */
public final class StudyStart {

    private static StudyJobs.PreviewJob running;

    private StudyStart() {
    }

    /**
     * @param redo a new preview even when the book has one (Redo preview, a new goal)
     */
    public static void begin(Activity a, DocumentController dc, boolean redo) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        if (!AiSettings.load(a).isReady()) {
            AiSettingsDialog.show(a, null);
            return;
        }
        if (running != null && running.isRunning()) {
            new Progress(a, dc, running).open();
            return;
        }
        final AiJobs.Target target = AiJobs.Target.of(dc);
        new Thread(() -> {
            final String goal = AiStore.get().load(target.name).goal;
            final StudyBook book = StudyStore.get().load(target.name);
            a.runOnUiThread(() -> {
                if (!redo && book.hasPreview()) {
                    turnOn(a, dc, target.name);
                } else if (goal == null) {
                    AiLegend.askGoal(a, target.name, null, () -> run(a, dc));
                } else {
                    run(a, dc);
                }
            });
        }, "@T Study").start();
    }

    /**
     * After the goal of a book was edited: a studied book whose labels were judged against another
     * goal is offered a new preview.
     */
    public static void goalChanged(Activity a, DocumentController dc) {
        final StudySession s = StudySession.of(dc);
        if (s == null || !s.book().on || !s.book().hasPreview()) {
            return;
        }
        final String name = s.bookName();
        final String judged = s.book().goal;
        new Thread(() -> {
            final String goal = AiStore.get().load(name).goal;
            if (goal == null || goal.trim().equals(judged == null ? "" : judged.trim())) {
                return;
            }
            a.runOnUiThread(() -> new AlertDialog.Builder(a)
                    .setMessage(R.string.study_relabel)
                    .setPositiveButton(R.string.study_relabel_yes, (d, w) -> begin(a, dc, true))
                    .setNegativeButton(R.string.study_relabel_no, null)
                    .show());
        }, "@T Study goal").start();
    }

    private static void turnOn(Activity a, DocumentController dc, String name) {
        new Thread(() -> {
            try {
                StudyStore.get().update(name, b -> b.on = true);
                StudySession.reloadIfOpen(name);
                a.runOnUiThread(() -> StudyPanel.show(a, dc));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T Study").start();
    }

    private static void run(Activity a, DocumentController dc) {
        if (running != null && running.isRunning()) {
            return;
        }
        final StudyJobs.PreviewJob job = new StudyJobs.PreviewJob(a, AiJobs.Target.of(dc), null);
        running = job;
        new Progress(a, dc, job).open();
        job.start();
    }

    /** The preview's progress; on success the Study panel opens. */
    private static final class Progress implements StudyJobs.Screen {
        private final Activity activity;
        private final DocumentController dc;
        private final StudyJobs.PreviewJob job;
        private AlertDialog dialog;
        private TextView status;
        private ProgressBar progress;

        Progress(Activity activity, DocumentController dc, StudyJobs.PreviewJob job) {
            this.activity = activity;
            this.dc = dc;
            this.job = job;
        }

        void open() {
            final LinearLayout view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            final int pad = Dips.dpToPx(16);
            view.setPadding(pad, pad, pad, pad / 2);
            progress = new ProgressBar(activity);
            view.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(32), Dips.dpToPx(32)));
            status = new TextView(activity);
            status.setPadding(pad, 0, 0, 0);
            status.setText(job.getStatus());
            view.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.study_this_book)
                    .setView(view)
                    .setPositiveButton(R.string.ai_hide, (d, w) -> job.setScreen(null))
                    .setNegativeButton(R.string.cancel, (d, w) -> job.cancel())
                    .create();
            // back or a tap outside hides the progress; the preview goes on
            dialog.setOnCancelListener(d -> job.setScreen(null));
            job.setScreen(this);
            dialog.show();
        }

        @Override
        public void onStatus(String text) {
            status.setText(text);
        }

        @Override
        public void onDone(StudyBook book) {
            job.setScreen(null);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            StudyPanel.show(activity, dc);
        }

        @Override
        public void onError(String message) {
            job.setScreen(null);
            progress.setVisibility(View.GONE);
            status.setText(message);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.close);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.study_retry);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                run(activity, dc);
            });
        }

        @Override
        public void onNeedSettings() {
            job.setScreen(null);
            dialog.dismiss();
            AiSettingsDialog.show(activity, null);
        }
    }
}
