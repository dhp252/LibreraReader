package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * Progress of a key passages run, a task of its own (F-004 design-2026-10-25.md): it goes on with
 * the dialog or the book closed and comes back when the dialog opens again; Cancel stops it.
 * Chapters already marked keep their highlights either way.
 */
public class AiHighlightDialog implements AiJobs.HighlightScreen, AiProgress.TaskScreen {

    private final Activity activity;
    private final String key;
    private AlertDialog dialog;
    private TextView status;
    private ProgressBar progress;
    private boolean ended;

    private AiHighlightDialog(Activity activity, String key) {
        this.activity = activity;
        this.key = key;
    }

    /** Starts a run, or shows the one of this book going on. */
    public static void start(Activity activity, DocumentController dc, boolean wholeBook) {
        start(activity, dc, wholeBook, null);
    }

    /** Key passages of one chapter (study mode's chapters), wherever the reader is. */
    public static void start(Activity activity, DocumentController dc, BookChapters.Range chapter) {
        start(activity, dc, false, chapter);
    }

    /** Whether a key passages task of this book is queued or running (in this process). */
    public static boolean isBusy(DocumentController dc) {
        if (dc == null || dc.getCurrentBook() == null) {
            return false;
        }
        final String key = AiTask.key(AiHighlights.bookName(dc.getCurrentBook().getPath()), AiTask.HIGHLIGHTS);
        final int state = AiProgress.state(key);
        return state == AiProgress.RUNNING || state == AiProgress.WAITING;
    }

    /** The progress of this book's run going on. */
    public static void showRunning(Activity activity, DocumentController dc) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        open(activity, AiTask.key(AiHighlights.bookName(dc.getCurrentBook().getPath()), AiTask.HIGHLIGHTS));
    }

    private static void start(Activity activity, DocumentController dc, boolean wholeBook, BookChapters.Range chapter) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        if (isBusy(dc)) {
            showRunning(activity, dc);
            return;
        }
        if (!AiSettings.load(activity).isReady()) {
            AiSettingsDialog.show(activity, null);
            return;
        }
        final AiJobs.Target target = AiJobs.Target.of(dc);
        // the first run on a book asks what the reader wants from it ("apply" is judged against it)
        new Thread(() -> {
            final String goal = AiStore.get().load(target.name).goal;
            activity.runOnUiThread(() -> {
                if (goal == null) {
                    AiLegend.askGoal(activity, target.name, null, () -> run(activity, target, wholeBook, chapter));
                } else {
                    run(activity, target, wholeBook, chapter);
                }
            });
        }, "@T AI goal").start();
    }

    private static void run(Activity activity, AiJobs.Target target, boolean wholeBook, BookChapters.Range chapter) {
        final AiTask task = AiTask.highlights(target, wholeBook, chapter);
        AiWork.enqueue(activity, task);
        open(activity, task.key());
    }

    private static void open(Activity activity, String key) {
        final AiHighlightDialog screen = new AiHighlightDialog(activity, key);
        screen.show();
        AiProgress.attach(key, activity, screen);
    }

    private void show() {
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
        status.setText(!now.isEmpty() ? now : activity.getString(AiProgress.isRunning(key)
                ? R.string.ai_reading_book : R.string.ai_task_queued));
        row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        view.addView(row);
        final TextView note = new TextView(activity);
        note.setText(R.string.ai_task_background);
        note.setAlpha(0.7f);
        note.setPadding(0, pad / 2, 0, 0);
        view.addView(note);

        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.ai_key_passages)
                .setView(view)
                .setPositiveButton(R.string.close, (d, w) -> AiProgress.detach(key, this))
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    AiProgress.detach(key, this);
                    AiWork.cancel(activity, key);
                    Toast.makeText(activity, R.string.ai_task_cancelled, Toast.LENGTH_SHORT).show();
                })
                .create();
        // back or a tap outside closes the progress; the run goes on
        dialog.setOnCancelListener(d -> AiProgress.detach(key, this));
        dialog.show();
    }

    @Override
    public void onStatus(String message) {
        if (!ended) {
            status.setText(message);
        }
    }

    @Override
    public void onWaiting(String message) {
        onStatus(message);
    }

    @Override
    public void onFinished(String message) {
        if (ended) {
            return;
        }
        ended = true;
        AiProgress.detach(key, this);
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
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
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
        }
    }

    @Override
    public void onEnded(int state, String message) {
        if (state == AiProgress.DONE && message != null) {
            onFinished(message);
        } else if (state == AiProgress.FAILED || state == AiProgress.CANCELLED) {
            onError(message);
        }
    }

    @Override
    public void onNeedSettings() {
        ended = true;
        AiProgress.detach(key, this);
        dialog.dismiss();
        AiSettingsDialog.show(activity, null);
    }
}
