package com.foobnix.annotation;

import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.LOG;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Changes to the marks of a book, written one after the other on a thread of their own; the open
 * book's marks are then drawn again.
 */
final class MarkEdits {

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "@T marks write");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Done {
        /** On the main thread; {@code error} is null when the change was written. */
        void done(Exception error);
    }

    private MarkEdits() {
    }

    static void apply(final String book, final MarksStore.Change change, final Done done) {
        if (book == null) {
            if (done != null) {
                done.done(new IllegalStateException("no book"));
            }
            return;
        }
        WRITER.execute(() -> {
            Exception error = null;
            try {
                final Map<String, Mark> marks = MarksStore.get().update(book, change);
                MarksLayer.get().set(book, marks);
            } catch (Exception e) {
                LOG.e(e);
                error = e;
            }
            final Exception result = error;
            if (done != null) {
                MAIN.post(() -> done.done(result));
            }
        });
    }

    static void add(String book, final Mark mark, Done done) {
        apply(book, marks -> marks.put(mark.id, mark), done);
    }

    static void remove(String book, final String id, Done done) {
        apply(book, marks -> marks.remove(id), done);
    }
}
