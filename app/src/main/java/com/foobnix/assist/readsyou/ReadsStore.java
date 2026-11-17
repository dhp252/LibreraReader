package com.foobnix.assist.readsyou;

import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where "The book reads you back" keeps its data: only the book's assist file, under
 * {@code reads:signals:<chapter key>} ({@link ReadsSignals}) and {@code reads:state}
 * ({@link ReadsState}). Nothing else is written anywhere and nothing is sent. Every call reads or
 * writes a file: call them on {@link #IO}, never on the main thread.
 */
final class ReadsStore {

    /** Every key of the feature starts with this. */
    static final String PREFIX = "reads:";

    /** One thread, so the writes of a session keep their order. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ReadsStore() {
    }

    static ReadsState state(String book) {
        try {
            return ReadsState.fromJson(AssistStore.get().item(book, ReadsState.KEY));
        } catch (Exception e) {
            LOG.e(e);
            return new ReadsState();
        }
    }

    static void saveState(String book, ReadsState state) {
        try {
            AssistStore.get().put(book, ReadsState.KEY, state.toJson());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** A chapter's stored signals; empty ones when it has none. */
    static ReadsSignals signals(String book, String chapterKey) {
        try {
            return ReadsSignals.fromJson(AssistStore.get().item(book, ReadsSignals.key(chapterKey)));
        } catch (Exception e) {
            LOG.e(e);
            return new ReadsSignals();
        }
    }

    static void saveSignals(String book, String chapterKey, ReadsSignals signals) {
        try {
            AssistStore.get().put(book, ReadsSignals.key(chapterKey), signals.toJson());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * Adds what was measured to the chapters the pages belong to: each chapter's item is read,
     * brought to this layout's page count, added to and written once.
     *
     * @param chapters the book's chapters (or page blocks) in the layout the pages count in
     */
    static void add(String book, List<BookChapters.Range> chapters, List<ReadsDelta> deltas) {
        final Map<BookChapters.Range, List<ReadsDelta>> byChapter = new LinkedHashMap<>();
        for (ReadsDelta d : deltas) {
            final BookChapters.Range r = chapterAt(chapters, d.page);
            if (r == null || d.isEmpty()) {
                continue;
            }
            List<ReadsDelta> list = byChapter.get(r);
            if (list == null) {
                list = new ArrayList<>();
                byChapter.put(r, list);
            }
            list.add(d);
        }
        for (Map.Entry<BookChapters.Range, List<ReadsDelta>> e : byChapter.entrySet()) {
            final BookChapters.Range r = e.getKey();
            final ReadsSignals s = signals(book, r.key);
            s.fit(r.pages());
            if (r.title != null && !r.title.trim().isEmpty()) {
                s.title = r.title.trim();
            }
            for (ReadsDelta d : e.getValue()) {
                s.apply(d.page - r.from, d);
            }
            saveSignals(book, r.key, s);
        }
    }

    static BookChapters.Range chapterAt(List<BookChapters.Range> chapters, int page) {
        if (chapters != null) {
            for (BookChapters.Range r : chapters) {
                if (page >= r.from && page < r.to) {
                    return r;
                }
            }
        }
        return null;
    }

    /** Everything the feature stored for the book, by key (the "What is stored" screen). */
    static Map<String, JSONObject> all(String book) {
        try {
            return AssistStore.get().items(book, PREFIX);
        } catch (Exception e) {
            LOG.e(e);
            return new LinkedHashMap<>();
        }
    }

    /** "Delete these signals": every item of the feature in the book's file. @return how many were removed */
    static int deleteAll(String book) {
        int n = 0;
        for (String key : all(book).keySet()) {
            try {
                AssistStore.get().remove(book, key);
                n++;
            } catch (Exception e) {
                LOG.e(e);
            }
        }
        return n;
    }
}
