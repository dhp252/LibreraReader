package com.foobnix.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The reader's ✦ button turns while a long AI task of the open book is queued or runs, so a
 * hidden progress is still seen; the ✦ menu marks the task and opens its progress again.
 */
public final class AiBusyButton implements AiProgress.Listener {

    /** One reader at a time: a new one (another book, a rotation) takes over. */
    private static AiBusyButton current;

    private final Activity activity;
    private final View button;
    private final DocumentController dc;
    private ObjectAnimator spin;

    private AiBusyButton(Activity activity, View button, DocumentController dc) {
        this.activity = activity;
        this.button = button;
        this.dc = dc;
    }

    public static void attach(Activity activity, View button, DocumentController dc) {
        if (button == null || dc == null) {
            return;
        }
        if (current != null) {
            current.stop();
        }
        final AiBusyButton b = new AiBusyButton(activity, button, dc);
        current = b;
        AiProgress.listen(b);
        b.update();
        // a book in scroll mode is known a little after the reader's views
        button.postDelayed(b::recover, 3000);
    }

    /** The open book's task of this kind is queued or runs. */
    public static boolean isBusy(DocumentController dc, String kind) {
        final String book = book(dc);
        return book != null && AiProgress.isBusy(AiTask.key(book, kind));
    }

    private static String book(DocumentController dc) {
        return dc == null || dc.getCurrentBook() == null ? null : AiHighlights.bookName(dc.getCurrentBook().getPath());
    }

    /**
     * After the app was killed, WorkManager still holds the tasks but this process knows nothing
     * until one runs: the queued ones are counted as waiting.
     */
    private void recover() {
        final String book = book(dc);
        if (book == null || current != this) {
            return;
        }
        new Thread(() -> {
            for (String kind : AiTask.KINDS) {
                final String key = AiTask.key(book, kind);
                if (AiProgress.state(key) < 0 && AiWork.isQueued(activity, key)) {
                    AiProgress.queued(key);
                }
            }
        }, "@T AI tasks").start();
    }

    @Override
    public void onChanged(String key, int state) {
        if (Apps.isDestroyedActivity(activity)) {
            stop();
            return;
        }
        final String book = book(dc);
        if (book != null && key.endsWith(":" + book)) {
            update();
        }
    }

    private void update() {
        boolean busy = false;
        for (String kind : AiTask.KINDS) {
            busy |= isBusy(dc, kind);
        }
        try {
            if (busy && spin == null) {
                spin = ObjectAnimator.ofFloat(button, View.ROTATION, 0f, 360f);
                spin.setDuration(2400);
                spin.setRepeatCount(ValueAnimator.INFINITE);
                spin.setInterpolator(new LinearInterpolator());
                spin.start();
            } else if (!busy && spin != null) {
                spin.cancel();
                spin = null;
                button.setRotation(0f);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private void stop() {
        AiProgress.unlisten(this);
        if (spin != null) {
            spin.cancel();
            spin = null;
            button.setRotation(0f);
        }
        if (current == this) {
            current = null;
        }
    }
}
