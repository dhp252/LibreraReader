package com.foobnix.assist.fiction;

import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import org.json.JSONObject;

import java.io.IOException;

/**
 * The stored fiction data of a book, in its AssistStore file: {@link FictionCast#KEY}. The cast
 * read last is kept in memory, so the text-selection box can ask "is this a name?" without
 * reading the file each time; every write here replaces it.
 */
final class FictionStore {

    private static String cachedBook;
    private static FictionCast cachedCast;

    private FictionStore() {
    }

    /** The stored item as it is in the file (with its {@code t}), or null. */
    static JSONObject castItem(String book) {
        return AssistStore.get().item(book, FictionCast.KEY);
    }

    /** The stored cast, read from the file now; empty (never null) when there is none. */
    static FictionCast loadCast(String book) {
        final FictionCast cast = FictionCast.fromJson(castItem(book));
        remember(book, cast);
        return cast;
    }

    /** The stored cast, from memory when this book's was read before. */
    static FictionCast cast(String book) {
        synchronized (FictionStore.class) {
            if (book != null && book.equals(cachedBook) && cachedCast != null) {
                return cachedCast;
            }
        }
        return loadCast(book);
    }

    static void saveCast(String book, FictionCast cast) throws IOException {
        AssistStore.get().put(book, FictionCast.KEY, cast.toJson());
        // a copy: the job goes on changing its own
        remember(book, FictionCast.fromJson(cast.toJson()));
    }

    /** Forgets the cast kept in memory (another device's copy may have come with a sync). */
    static void forget() {
        synchronized (FictionStore.class) {
            cachedBook = null;
            cachedCast = null;
        }
    }

    private static void remember(String book, FictionCast cast) {
        synchronized (FictionStore.class) {
            cachedBook = book;
            cachedCast = cast;
        }
    }

    /** When the stored item was written (ms), 0 when there is none. */
    static long time(JSONObject item) {
        try {
            return item == null ? 0 : item.optLong(AssistStore.T);
        } catch (Exception e) {
            LOG.e(e);
            return 0;
        }
    }
}
