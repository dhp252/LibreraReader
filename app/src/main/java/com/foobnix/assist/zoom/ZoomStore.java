package com.foobnix.assist.zoom;

import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import java.io.IOException;

/** A chapter's zoom levels in the book's assist file, as {@code zoom:<chapter key>}. Off the main thread. */
final class ZoomStore {

    private ZoomStore() {
    }

    /** The stored levels of a chapter; null when it has none. */
    static ZoomData load(String bookName, String chapterKey) {
        try {
            return ZoomData.fromJson(AssistStore.get().item(bookName, ZoomData.key(chapterKey)));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    static void save(String bookName, String chapterKey, ZoomData data) throws IOException {
        try {
            AssistStore.get().put(bookName, ZoomData.key(chapterKey), data.toJson());
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }
}
