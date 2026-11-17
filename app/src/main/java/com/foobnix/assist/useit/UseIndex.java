package com.foobnix.assist.useit;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The library's list of experiments still open, so the main screen can say "1 experiment to check
 * in" without reading every book's file. One small item per open experiment in the library assist
 * file, {@code use:index:<id>} = {book, id, day, title, bookTitle}: one item each, not one list,
 * so two devices that start experiments both keep theirs when the files are merged item by item.
 * The item is written when an experiment is saved and removed when it is checked in or deleted.
 */
public final class UseIndex {

    /** Key prefix of an entry in the library assist file. */
    public static final String PREFIX = "use:index:";

    /** One open experiment as the library knows it. */
    public static final class Entry {
        /** The book's name: the file its assist data is stored under. */
        public final String book;
        public final String id;
        /** The check-in day. */
        public final String day;
        /** What is tried, in a sentence. */
        public final String title;
        public final String bookTitle;

        Entry(String book, String id, String day, String title, String bookTitle) {
            this.book = book;
            this.id = id;
            this.day = day;
            this.title = title;
            this.bookTitle = bookTitle;
        }
    }

    private UseIndex() {
    }

    public static String key(String id) {
        return PREFIX + id;
    }

    /** Whether an experiment belongs in the index: started and not checked in yet. */
    public static boolean isOpen(UseExperiment e) {
        return e != null && !UseExperiment.DONE.equals(e.state) && UseDates.isDay(e.checkDay);
    }

    /** The index item of an experiment of {@code bookName}. */
    public static JSONObject entry(String bookName, UseExperiment e) throws JSONException {
        return new JSONObject().put("book", bookName).put("id", e.id).put("day", e.checkDay)
                .put("title", e.tryText).put("bookTitle", e.bookTitle);
    }

    static Entry fromJson(JSONObject o) {
        if (o == null || o.optString("book").isEmpty() || o.optString("id").isEmpty()
                || !UseDates.isDay(o.optString("day"))) {
            return null;
        }
        return new Entry(o.optString("book"), o.optString("id"), o.optString("day"), o.optString("title"),
                o.optString("bookTitle"));
    }

    /**
     * The experiments due on {@code today} among the library file's index items, the longest
     * waiting first.
     *
     * @param items the items under {@link #PREFIX} (their order does not matter)
     */
    public static List<Entry> due(Map<String, JSONObject> items, String today) {
        final List<Entry> out = new ArrayList<>();
        if (items == null || !UseDates.isDay(today)) {
            return out;
        }
        for (Map.Entry<String, JSONObject> item : items.entrySet()) {
            if (!item.getKey().startsWith(PREFIX)) {
                continue;
            }
            final Entry e = fromJson(item.getValue());
            if (e != null && UseDates.reached(today, e.day)) {
                out.add(e);
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                final int byDay = a.day.compareTo(b.day);
                return byDay != 0 ? byDay : a.id.compareTo(b.id);
            }
        });
        return out;
    }

    /**
     * Experiments in the order a list shows them: due first (longest waiting first), then running
     * (soonest check-in first), then done (latest first).
     */
    public static List<UseExperiment> sorted(List<UseExperiment> all, final String today) {
        final List<UseExperiment> out = new ArrayList<>(all);
        Collections.sort(out, new Comparator<UseExperiment>() {
            @Override
            public int compare(UseExperiment a, UseExperiment b) {
                final int ra = rank(a.stateOn(today));
                final int rb = rank(b.stateOn(today));
                if (ra != rb) {
                    return ra - rb;
                }
                if (ra == 2) {
                    final int byDone = b.doneDay.compareTo(a.doneDay);
                    return byDone != 0 ? byDone : b.id.compareTo(a.id);
                }
                final int byDay = a.checkDay.compareTo(b.checkDay);
                return byDay != 0 ? byDay : a.id.compareTo(b.id);
            }
        });
        return out;
    }

    private static int rank(String state) {
        return UseExperiment.DUE.equals(state) ? 0 : UseExperiment.RUNNING.equals(state) ? 1 : 2;
    }
}
