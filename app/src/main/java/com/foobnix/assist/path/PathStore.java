package com.foobnix.assist.path;

import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A book's path in {@code AssistStore} ({@link PathData#KEY}): read, and changed one step at a time. */
final class PathStore {

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();

    interface Change {
        void apply(PathData data);
    }

    private PathStore() {
    }

    /** The stored path of a book; empty when it has none. Off the main thread. */
    static PathData load(String book) {
        return PathData.fromJson(AssistStore.get().item(book, PathData.KEY));
    }

    /**
     * Applies a change to the stored path and writes it, when it still is the path to {@code goal}
     * (another device may have replaced it).
     *
     * @return the path as written, or null when no path to that goal is stored (nothing is written)
     */
    static PathData update(String book, String goal, Change change) throws IOException {
        synchronized (LOCK) {
            final PathData stored = load(book);
            if (stored.isEmpty() || !stored.goal.equals(goal)) {
                return null;
            }
            change.apply(stored);
            try {
                AssistStore.get().put(book, PathData.KEY, stored.toJson());
            } catch (org.json.JSONException e) {
                throw new IOException(e.getMessage());
            }
            return stored;
        }
    }

    /** The same from the main thread: written in the background, in the order asked. */
    static void later(String book, String goal, Change change) {
        WRITER.execute(() -> {
            try {
                update(book, goal, change);
            } catch (Exception e) {
                LOG.e(e);
            }
        });
    }
}
