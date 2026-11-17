package com.foobnix.assist.zoom;

import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import java.io.IOException;

/**
 * A chapter's zoom levels in the book's assist file, as {@code zoom:<chapter key>}, and the book's
 * reading goal for Semantic zoom, as {@code zoom:goal}. Off the main thread.
 */
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

    /** The book's reading goal for Semantic zoom; null when the reader has not chosen one. */
    static ZoomGoal loadGoal(String bookName) {
        try {
            return ZoomGoal.fromJson(AssistStore.get().item(bookName, ZoomGoal.KEY));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    static void saveGoal(String bookName, ZoomGoal goal) throws IOException {
        try {
            AssistStore.get().put(bookName, ZoomGoal.KEY, goal.toJson());
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * The reading goal the book has for its key passages (F-004, ✦ › Key passages › Reading goal):
     * the default of the goal screen's own words. Only read here, never written; empty when none.
     */
    static String bookGoal(String bookName) {
        try {
            final String goal = com.foobnix.ai.AiStore.get().load(bookName).goal;
            return goal == null ? "" : goal.trim();
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }
}
