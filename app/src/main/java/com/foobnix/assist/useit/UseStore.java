package com.foobnix.assist.useit;

import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads and writes experiments: {@code use:<id>} in the book's assist file, and the library's
 * index of the open ones ({@link UseIndex}). Every call reads or writes a file: call them on
 * {@link #IO}, never on the main thread.
 */
final class UseStore {

    /** One thread for the store, so a save and the list read after it keep their order. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private UseStore() {
    }

    /** Today as the device's local day. */
    static String today() {
        return UseDates.of(System.currentTimeMillis(), TimeZone.getDefault());
    }

    /** The book's experiments, in the order a list shows them on {@code today}. */
    static List<UseExperiment> list(String bookName, String today) {
        final List<UseExperiment> out = new ArrayList<>();
        try {
            for (Map.Entry<String, JSONObject> item : AssistStore.get().items(bookName, UseExperiment.PREFIX).entrySet()) {
                final UseExperiment e = UseExperiment.fromJson(item.getValue());
                if (e != null) {
                    out.add(e);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return UseIndex.sorted(out, today);
    }

    static UseExperiment get(String bookName, String id) {
        try {
            return UseExperiment.fromJson(AssistStore.get().item(bookName, UseExperiment.key(id)));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    /** Writes an experiment and brings the library's index in line with it. */
    static void save(String bookName, UseExperiment e) throws IOException {
        try {
            AssistStore.get().put(bookName, UseExperiment.key(e.id), e.toStore(today()));
            if (UseIndex.isOpen(e)) {
                AssistStore.get().put(AssistStore.LIBRARY, UseIndex.key(e.id), UseIndex.entry(bookName, e));
            } else {
                unindex(e.id);
            }
        } catch (JSONException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    static void delete(String bookName, String id) throws IOException {
        AssistStore.get().remove(bookName, UseExperiment.key(id));
        unindex(id);
    }

    /** Takes an experiment out of the library's index, when it is in it. */
    static void unindex(String id) throws IOException {
        if (AssistStore.get().item(AssistStore.LIBRARY, UseIndex.key(id)) != null) {
            AssistStore.get().remove(AssistStore.LIBRARY, UseIndex.key(id));
        }
    }

    /** The experiments of every book that are due on {@code today}, from the library's index. */
    static List<UseIndex.Entry> due(String today) {
        try {
            return UseIndex.due(AssistStore.get().items(AssistStore.LIBRARY, UseIndex.PREFIX), today);
        } catch (Exception e) {
            LOG.e(e);
            return new ArrayList<>();
        }
    }
}
