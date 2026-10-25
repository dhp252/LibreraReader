package com.foobnix.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs a long AI task (design-2026-10-25.md): behind a foreground notification, with the app's
 * context, one task at a time. WorkManager keeps the task: it runs again after the app was killed
 * or the phone restarted, waits for the network, and backs off after a failure that may pass; the
 * job skips the steps it saved before. Each task is its own unique work (a chain would cancel or
 * fail the tasks queued behind one); a permit lets one run at a time.
 */
public final class AiWork extends Worker {

    public static final String TAG = "ai-long";

    /** One long AI task at a time; the others wait their turn. */
    private static final Semaphore TURN = new Semaphore(1, true);

    private volatile AiJobs.Job job;

    public AiWork(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final AiTask task = AiTask.fromMap(getInputData().getKeyValueMap());
        if (task == null) {
            return Result.failure();
        }
        final Context app = getApplicationContext();
        final String key = task.key();
        try {
            if (!TURN.tryAcquire()) {
                AiProgress.waiting(key, app.getString(R.string.ai_task_waiting_turn));
                while (!TURN.tryAcquire(1, TimeUnit.SECONDS)) {
                    if (isStopped()) {
                        return Result.failure();
                    }
                }
            }
        } catch (InterruptedException e) {
            return Result.failure();
        }
        try {
            return run(app, task, key);
        } finally {
            TURN.release();
        }
    }

    private Result run(Context app, AiTask task, String key) {
        AiNotifications.running(app, task);
        foreground(app, task, app.getString(R.string.ai_task_starting), 0, 0);
        if (!AiSettings.load(app).isReady()) {
            final String message = app.getString(R.string.ai_task_needs_settings);
            AiProgress.ended(key, AiProgress.FAILED, message);
            AiNotifications.stopped(app, task, message);
            return Result.failure();
        }
        final AiJobs.Job j = job(app, task);
        job = j;
        j.setSteps((status, step, steps) -> foreground(app, task, status, step, steps));
        AiProgress.started(key, j);
        if (isStopped()) {
            j.cancel();
        }
        final AiJobs.Job.Outcome o = j.runNow();
        job = null;

        if (o.state == AiJobs.Job.Outcome.OK) {
            AiProgress.ended(key, AiProgress.DONE, o.message);
            if (o.message != null && !o.message.isEmpty()) {
                AiNotifications.done(app, task, o.message);
            }
            return Result.success();
        }
        if (o.state == AiJobs.Job.Outcome.CANCELLED || isStopped()) {
            if (getStopReason() == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
                AiProgress.ended(key, AiProgress.CANCELLED, app.getString(R.string.ai_task_cancelled));
            } else {
                // stopped by Android (its limits, the network gone): WorkManager runs it again
                final String message = app.getString(R.string.ai_task_paused);
                AiProgress.waiting(key, message);
                AiNotifications.waiting(app, task, message);
            }
            // the result of a stopped work is not used
            return Result.failure();
        }
        if (AiRetry.retryTask(o.retryable, getRunAttemptCount())) {
            final String message = app.getString(R.string.ai_task_waiting_network);
            AiProgress.waiting(key, message);
            AiNotifications.waiting(app, task, message);
            return Result.retry();
        }
        if (!o.shown) {
            j.failed(o.message);
        }
        AiProgress.ended(key, AiProgress.FAILED, o.message);
        AiNotifications.stopped(app, task, o.message);
        return Result.failure();
    }

    @Override
    public void onStopped() {
        final AiJobs.Job j = job;
        if (j != null) {
            j.cancel();
        }
    }

    /** The notification shows what the task does; Android 12+ may refuse it from the background. */
    private void foreground(Context app, AiTask task, String status, int step, int steps) {
        try {
            setForegroundAsync(AiNotifications.running(app, task, getId(), status, step, steps));
        } catch (Exception e) {
            // not in the foreground: the task still runs, in the job system's slices
            LOG.e(e);
        }
    }

    static AiJobs.Job job(Context app, AiTask task) {
        switch (task.kind) {
            case AiTask.MAP:
                return new com.foobnix.bookmap.MapJob(app, task.target(), task.bookOnly);
            case AiTask.BOOK_SUMMARY:
                return new AiJobs.SummaryJob(app, task.target(), task.regenerate);
            case AiTask.STUDY_PREVIEW:
                return new com.foobnix.study.StudyJobs.PreviewJob(app, task.target());
            default:
                return new AiJobs.HighlightJob(app, task.target(), task.wholeBook, task.chapter, task.redoBefore);
        }
    }

    // --- the queue ---

    /** Queues the task; the same task (book and kind) already queued or running is kept, not doubled. */
    public static void enqueue(Context c, AiTask task) {
        final Context app = c.getApplicationContext();
        AiProgress.queued(task.key());
        final Map<String, Object> values = task.toMap();
        final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AiWork.class)
                .setInputData(new Data.Builder().putAll(values).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(task.key(), ExistingWorkPolicy.KEEP, request);
    }

    /** Stops the task (its saved steps stay). */
    public static void cancel(Context c, String key) {
        WorkManager.getInstance(c.getApplicationContext()).cancelUniqueWork(key);
    }

    /** Whether the task is queued, waiting or running. Off the main thread. */
    public static boolean isQueued(Context c, String key) {
        try {
            for (WorkInfo w : WorkManager.getInstance(c.getApplicationContext()).getWorkInfosForUniqueWork(key).get()) {
                if (!w.getState().isFinished()) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return false;
    }

    /** Continue on a stopped task's notification: a new run from the steps it saved. */
    public static final class Continue extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle extras = intent.getExtras();
            if (extras == null) {
                return;
            }
            final Map<String, Object> values = new HashMap<>();
            for (String k : extras.keySet()) {
                values.put(k, extras.get(k));
            }
            final AiTask task = AiTask.fromMap(values);
            if (task != null) {
                AiNotifications.running(context, task);
                enqueue(context, task);
            }
        }
    }
}
