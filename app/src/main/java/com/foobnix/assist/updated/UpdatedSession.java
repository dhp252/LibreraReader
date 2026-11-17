package com.foobnix.assist.updated;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "The book, updated" in the open book (Assist.UPDATED): the stored notes of its chapters, kept
 * here for the margin marks ({@link UpdatedMarks}). One session at a time, for the book shown;
 * {@link #pageShown} is its only hook, called only while the switch is on. It asks the AI nothing:
 * that happens in {@link UpdatedScreen}, on a tap.
 */
public final class UpdatedSession {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static UpdatedSession current;

    private final String path;
    private final String name;
    /** The book's chapters with notes, by chapter key (main thread). */
    private final Map<String, UpdatedChapter> items = new HashMap<>();

    private UpdatedSession(String path) {
        this.path = path;
        this.name = AiHighlights.bookName(path);
    }

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    public static void pageShown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null || !Assist.UPDATED.isOn()) {
                return;
            }
            final Activity a = dc.getActivity();
            if (Looper.myLooper() != Looper.getMainLooper()) {
                if (a != null) {
                    a.runOnUiThread(() -> pageShown(dc));
                }
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current != null && current.path.equals(path)) {
                return;
            }
            // another book: its notes take the place of the last one's
            UpdatedMarks.set(null);
            final UpdatedSession session = new UpdatedSession(path);
            current = session;
            session.load();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private void load() {
        new Thread(() -> {
            final Map<String, UpdatedChapter> stored = new HashMap<>();
            try {
                for (Map.Entry<String, JSONObject> e : AssistStore.get().items(name, UpdatedChapter.PREFIX).entrySet()) {
                    final UpdatedChapter c = UpdatedChapter.fromJson(e.getValue());
                    if (c != null) {
                        stored.put(e.getKey().substring(UpdatedChapter.PREFIX.length()), c);
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            MAIN.post(() -> {
                if (current != this) {
                    return;
                }
                // a chapter checked while this was read is newer than the file's copy
                for (Map.Entry<String, UpdatedChapter> e : stored.entrySet()) {
                    if (!items.containsKey(e.getKey())) {
                        items.put(e.getKey(), e.getValue());
                    }
                }
                marks();
            });
        }, "@T Updated").start();
    }

    /** A chapter's notes were made or made again (main thread): the marks follow. */
    static void changed(String bookPath, String chapterKey, UpdatedChapter chapter) {
        if (current == null || !current.path.equals(bookPath)) {
            current = new UpdatedSession(bookPath);
            current.load();
        }
        current.items.put(chapterKey, chapter);
        current.marks();
    }

    private void marks() {
        final List<UpdatedNote> all = new ArrayList<>();
        for (UpdatedChapter c : items.values()) {
            all.addAll(c.notes);
        }
        UpdatedMarks.set(all);
    }

    /**
     * The fields of the reader's lens, for "In your terms": the Lens feature's profile in the
     * library file of the assist store. Empty when Lens is off or has no profile. Off the main thread.
     */
    static List<String> lensFields() {
        try {
            if (!Assist.LENS.isOn()) {
                return Collections.emptyList();
            }
            return UpdatedLens.fields(AssistStore.get().item(AssistStore.LIBRARY, UpdatedLens.KEY));
        } catch (Exception e) {
            LOG.e(e);
            return Collections.emptyList();
        }
    }
}
