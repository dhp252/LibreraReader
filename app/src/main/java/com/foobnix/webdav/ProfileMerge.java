package com.foobnix.webdav;

import com.foobnix.annotation.MarksStore;
import com.foobnix.study.StudyStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item-by-item merge of the profile files two devices changed between syncs (a book's position, a
 * bookmark, a book in the recent, favourite or excluded list, a tag), three-way against the copy
 * kept after the last sync: what one side changed or removed since then is applied, so neither
 * device's changes are lost and a deletion reaches every device. Both sides changed one item: the
 * newer by its own time; changed on one side and removed on the other: the change stays.
 * Design: .agents/planning/unified-sync-design.md.
 */
public final class ProfileMerge {

    static final String PROGRESS = "app-Progress.json";
    static final String BOOKMARKS = "app-Bookmarks.json";
    static final String RECENT = "app-Recent.json";
    static final String FAVORITE = "app-Favorite.json";
    static final String EXCLUDE = "app-Exclude.json";
    static final String TAGS = "app-Tags2.json";

    private ProfileMerge() {
    }

    /** For {@link FolderSync#withMerger}. */
    public static final FolderSync.Merger MERGER = new FolderSync.Merger() {
        @Override
        public boolean handles(String relativePath) {
            return ProfileMerge.handles(relativePath);
        }

        @Override
        public String merge(String relativePath, String base, String local, String remote) throws Exception {
            return ProfileMerge.merge(relativePath, base, local, remote);
        }
    };

    /** Whether the file (a path relative to the profile folder, or a name) is merged by item. */
    public static boolean handles(String path) {
        final String name = nameOf(path);
        return PROGRESS.equals(name) || BOOKMARKS.equals(name) || RECENT.equals(name) || FAVORITE.equals(name)
                || EXCLUDE.equals(name) || TAGS.equals(name) || MarksStore.isMarksFile(path)
                || StudyStore.isStudyFile(path);
    }

    /**
     * @param base   the file as both sides had it after the last sync, null when there is none
     * @param local  this device's file now
     * @param remote the server's file now
     * @return the merged file, or null when the file is not one merged by item
     */
    public static String merge(String path, String base, String local, String remote) throws JSONException {
        final String name = nameOf(path);
        // a book's marks (F-008): {id: mark}, the newer change of one mark by its "t"; a book's
        // study data (F-009): {"@book" | chapter key: object}, the same way
        if (PROGRESS.equals(name) || BOOKMARKS.equals(name) || MarksStore.isMarksFile(path)
                || StudyStore.isStudyFile(path)) {
            return mergeObjects(base, local, remote, "t").toString();
        }
        if (RECENT.equals(name) || FAVORITE.equals(name) || EXCLUDE.equals(name)) {
            return mergeBookList(base, local, remote).toString();
        }
        if (TAGS.equals(name)) {
            return mergeTags(base, local, remote).toString();
        }
        return null;
    }

    /** {key: record}; both sides changed a record: the one with the larger {@code timeField}. */
    static JSONObject mergeObjects(String base, String local, String remote, String timeField) throws JSONException {
        final Map<String, Object> b = entries(object(base));
        final Map<String, Object> l = entries(object(local));
        final Map<String, Object> r = entries(object(remote));
        final JSONObject out = new JSONObject();
        for (String key : keys(l, r, b)) {
            final Object value = pick(b.get(key), l.get(key), r.get(key), timeField);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    /** [{name, path, time}], one per book (by file name, as upstream matches them across devices). */
    static JSONArray mergeBookList(String base, String local, String remote) throws JSONException {
        final Map<String, Object> b = bookList(base);
        final Map<String, Object> l = bookList(local);
        final Map<String, Object> r = bookList(remote);
        final JSONArray out = new JSONArray();
        for (String key : keys(l, r, b)) {
            final Object value = pick(b.get(key), l.get(key), r.get(key), "time");
            if (value != null) {
                out.put(value);
            }
        }
        return out;
    }

    /** {tag: [book paths]}; each tag's books merged as a set. */
    static JSONObject mergeTags(String base, String local, String remote) throws JSONException {
        final Map<String, Object> b = entries(object(base));
        final Map<String, Object> l = entries(object(local));
        final Map<String, Object> r = entries(object(remote));
        final JSONObject out = new JSONObject();
        for (String tag : keys(l, r, b)) {
            final boolean inL = l.containsKey(tag);
            final boolean inR = r.containsKey(tag);
            final boolean inB = b.containsKey(tag);
            if (!inL || !inR) {
                // a tag removed on one side is gone if the other left it as it was, else the change stays
                final Object other = inL ? l.get(tag) : r.get(tag);
                if ((inL || inR) && !(inB && same(b.get(tag), other))) {
                    out.put(tag, other);
                }
                continue;
            }
            final Set<String> books = mergeSet(strings(b.get(tag)), strings(l.get(tag)), strings(r.get(tag)));
            out.put(tag, new JSONArray(new ArrayList<>(books)));
        }
        return out;
    }

    /** Elements added on either side kept, removed on either side removed. */
    static Set<String> mergeSet(Set<String> base, Set<String> local, Set<String> remote) {
        final Set<String> out = new LinkedHashSet<>();
        for (String s : local) {
            if (remote.contains(s) || !base.contains(s)) {
                out.add(s);
            }
        }
        for (String s : remote) {
            if (!base.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /** The value one item ends with; null when it is deleted. */
    static Object pick(Object base, Object local, Object remote, String timeField) {
        if (same(local, remote)) {
            return local;
        }
        if (same(local, base)) {
            return remote;
        }
        if (same(remote, base)) {
            return local;
        }
        // changed on both sides (or new on both): a change beats a deletion, else the newer
        if (local == null) {
            return remote;
        }
        if (remote == null) {
            return local;
        }
        return time(remote, timeField) > time(local, timeField) ? remote : local;
    }

    private static long time(Object value, String field) {
        return value instanceof JSONObject ? ((JSONObject) value).optLong(field, 0) : 0;
    }

    /** Equal as data: key order and number formatting do not count. */
    static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return normal(a).equals(normal(b));
    }

    private static Object normal(Object value) {
        if (value instanceof JSONObject) {
            final JSONObject o = (JSONObject) value;
            final Map<String, Object> sorted = new java.util.TreeMap<>();
            final Iterator<String> it = o.keys();
            while (it.hasNext()) {
                final String k = it.next();
                sorted.put(k, normal(o.opt(k)));
            }
            return sorted;
        }
        if (value instanceof JSONArray) {
            final JSONArray a = (JSONArray) value;
            final List<Object> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                list.add(normal(a.opt(i)));
            }
            return list;
        }
        if (value instanceof Number) {
            final double d = ((Number) value).doubleValue();
            return d == Math.rint(d) ? (Object) (long) d : (Object) d;
        }
        return value == null || value == JSONObject.NULL ? "null" : value.toString();
    }

    private static Set<String> keys(Map<String, Object> l, Map<String, Object> r, Map<String, Object> b) {
        final Set<String> keys = new LinkedHashSet<>(r.keySet());
        keys.addAll(l.keySet());
        keys.addAll(b.keySet());
        return keys;
    }

    private static JSONObject object(String text) throws JSONException {
        if (text == null || text.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(text);
    }

    private static Map<String, Object> entries(JSONObject o) {
        final Map<String, Object> map = new LinkedHashMap<>();
        final Iterator<String> it = o.keys();
        while (it.hasNext()) {
            final String k = it.next();
            map.put(k, o.opt(k));
        }
        return map;
    }

    private static Map<String, Object> bookList(String text) throws JSONException {
        final Map<String, Object> map = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return map;
        }
        final JSONArray array = new JSONArray(text);
        for (int i = 0; i < array.length(); i++) {
            final JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            final String key = nameOf(item.optString("path", item.optString("name", "")));
            if (key.isEmpty()) {
                continue;
            }
            final Object held = map.get(key);
            // the same book twice in one file: the newer entry
            if (held == null || time(item, "time") > time(held, "time")) {
                map.put(key, item);
            }
        }
        return map;
    }

    private static Set<String> strings(Object value) {
        final Set<String> out = new LinkedHashSet<>();
        if (value instanceof JSONArray) {
            final JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                out.add(a.optString(i));
            }
        }
        return out;
    }

    static String nameOf(String path) {
        final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
