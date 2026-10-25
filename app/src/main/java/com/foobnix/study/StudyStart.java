package com.foobnix.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
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
 * progress. The preview is a task of its own (F-004 design-2026-10-25.md): it goes on with the
 * dialog or the book closed; Cancel stops it. A book studied before comes back without a new
 * preview.
 */
public final class StudyStart {

    private StudyStart() {
    }

    private static String key(DocumentController dc) {
        return AiTask.key(com.foobnix.ai.AiHighlights.bookName(dc.getCurrentBook().getPath()), AiTask.STUDY_PREVIEW);
    }

    private static boolean busy(String key) {
        final int state = AiProgress.state(key);
        return state == AiProgress.RUNNING || state == AiProgress.WAITING;
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
        if (busy(key(dc))) {
            new Progress(a, dc, key(dc)).open();
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
        final String key = key(dc);
        if (!busy(key)) {
            AiWork.enqueue(a, AiTask.studyPreview(AiJobs.Target.of(dc)));
        }
        new Progress(a, dc, key).open();
    }

    /** The preview's progress; on success the Study panel opens. */
    private static final class Progress implements StudyJobs.Screen, AiProgress.TaskScreen {
        private final Activity activity;
        private final DocumentController dc;
        private final String key;
        private AlertDialog dialog;
        private TextView status;
        private ProgressBar progress;
        private boolean ended;

        Progress(Activity activity, DocumentController dc, String key) {
            this.activity = activity;
            this.dc = dc;
            this.key = key;
        }

        void open() {
            final LinearLayout view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.VERTICAL);
            final int pad = Dips.dpToPx(16);
            view.setPadding(pad, pad, pad, pad / 2);
            final LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            progress = new ProgressBar(activity);
            row.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(32), Dips.dpToPx(32)));
            status = new TextView(activity);
            status.setPadding(pad, 0, 0, 0);
            final String now = AiProgress.status(key);
            status.setText(!now.isEmpty() ? now : activity.getString(R.string.ai_task_queued));
            row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            view.addView(row);
            final TextView note = new TextView(activity);
            note.setText(R.string.ai_task_background_hide);
            note.setAlpha(0.7f);
            note.setPadding(0, pad / 2, 0, 0);
            view.addView(note);

            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.study_this_book)
                    .setView(view)
                    .setPositiveButton(R.string.ai_task_hide, (d, w) -> AiProgress.detach(key, this))
                    .setNegativeButton(R.string.cancel, (d, w) -> {
                        AiProgress.detach(key, this);
                        AiWork.cancel(activity, key);
                    })
                    .create();
            // back or a tap outside closes the progress; the preview goes on
            dialog.setOnCancelListener(d -> AiProgress.detach(key, this));
            dialog.show();
            AiProgress.attach(key, activity, this);
        }

        @Override
        public void onStatus(String text) {
            if (!ended) {
                status.setText(text);
            }
        }

        @Override
        public void onWaiting(String text) {
            onStatus(text);
        }

        @Override
        public void onEnded(int state, String message) {
            if (state == AiProgress.FAILED || state == AiProgress.CANCELLED) {
                onError(message);
            }
        }

        @Override
        public void onDone(StudyBook book) {
            ended = true;
            AiProgress.detach(key, this);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            StudyPanel.show(activity, dc);
        }

        @Override
        public void onError(String message) {
            if (ended) {
                return;
            }
            ended = true;
            AiProgress.detach(key, this);
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
            ended = true;
            AiProgress.detach(key, this);
            dialog.dismiss();
            AiSettingsDialog.show(activity, null);
        }
    }
}
