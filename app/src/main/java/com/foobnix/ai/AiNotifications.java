package com.foobnix.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;

import com.foobnix.android.utils.LOG;
import com.foobnix.librerax.LibreraX;
import com.foobnix.pdf.info.R;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * The notifications of long AI tasks (design-2026-10-25.md): the running one (what it does, how
 * far, Cancel), a waiting one between runs, and one when it is done or stopped (a tap opens the
 * book; a stopped task has Continue).
 */
public final class AiNotifications {

    static final String CHANNEL = "ai_work";
    static final String ACTION_CONTINUE = "com.foobnix.ai.CONTINUE";

    private AiNotifications() {
    }

    /** The running task's notification (WorkManager's foreground service shows it). */
    public static ForegroundInfo running(Context c, AiTask task, UUID work, String status, int step, int steps) {
        final NotificationCompat.Builder b = builder(c, task)
                .setContentText(status)
                .setOngoing(true)
                .setProgress(steps, Math.min(step, steps), steps <= 0)
                .addAction(0, c.getString(R.string.cancel), WorkManager.getInstance(c).createCancelPendingIntent(work));
        final Notification n = b.build();
        final int id = runningId(task);
        return Build.VERSION.SDK_INT >= 29
                ? new ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                : new ForegroundInfo(id, n);
    }

    /** Between two runs: why it waits; it goes on by itself. */
    public static void waiting(Context c, AiTask task, String status) {
        notify(c, resultId(task), builder(c, task).setContentText(status).setOngoing(false).build());
    }

    /** It runs again: the waiting notice goes. */
    public static void running(Context c, AiTask task) {
        cancel(c, resultId(task));
    }

    public static void done(Context c, AiTask task, String message) {
        notify(c, resultId(task), builder(c, task).setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).build());
    }

    /** Stopped for good: the reason, and Continue (a new run from the steps saved). */
    public static void stopped(Context c, AiTask task, String reason) {
        final String text = c.getString(R.string.ai_task_stopped, reason);
        final Intent again = new Intent(ACTION_CONTINUE).setClass(c, AiWork.Continue.class);
        for (Map.Entry<String, Object> e : task.toMap().entrySet()) {
            final Object v = e.getValue();
            if (v instanceof Integer) {
                again.putExtra(e.getKey(), (Integer) v);
            } else if (v instanceof Long) {
                again.putExtra(e.getKey(), (Long) v);
            } else if (v instanceof Boolean) {
                again.putExtra(e.getKey(), (Boolean) v);
            } else {
                again.putExtra(e.getKey(), String.valueOf(v));
            }
        }
        final PendingIntent cont = PendingIntent.getBroadcast(c, task.key().hashCode(), again,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notify(c, resultId(task), builder(c, task).setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true)
                .addAction(0, c.getString(R.string.ai_task_continue), cont).build());
    }

    public static String kindName(Context c, String kind) {
        if (AiTask.MAP.equals(kind)) {
            return c.getString(R.string.book_map);
        }
        if (AiTask.BOOK_SUMMARY.equals(kind)) {
            return c.getString(R.string.ai_task_summary);
        }
        if (AiTask.STUDY_PREVIEW.equals(kind)) {
            return c.getString(R.string.ai_task_preview);
        }
        return c.getString(R.string.ai_task_highlights);
    }

    private static NotificationCompat.Builder builder(Context c, AiTask task) {
        channel(c);
        return new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_librera)
                .setContentTitle(kindName(c, task.kind) + " · " + task.title)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openBook(c, task));
    }

    /** A tap opens the book in the reader, as a recent book of the widget does. */
    private static PendingIntent openBook(Context c, AiTask task) {
        final Intent open = new Intent(Intent.ACTION_VIEW, Uri.fromFile(new File(task.path)));
        open.setClassName(c, LibreraX.readerClass().getName());
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, task.key().hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            final NotificationChannel channel = new NotificationChannel(CHANNEL, c.getString(R.string.ai_task_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    /** Ids apart from the app's other notifications (TTS uses small fixed ones). */
    static int runningId(AiTask task) {
        return 0x40000000 | (task.key().hashCode() & 0x0fffffff);
    }

    static int resultId(AiTask task) {
        return 0x50000000 | (task.key().hashCode() & 0x0fffffff);
    }

    private static void notify(Context c, int id, Notification n) {
        try {
            final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(id, n);
            }
        } catch (SecurityException e) {
            // notifications not allowed (Android 13+): the screens still show it
            LOG.e(e);
        }
    }

    private static void cancel(Context c, int id) {
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(id);
        }
    }
}
