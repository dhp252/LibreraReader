package com.foobnix.bookmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the map's page draws, from the stored map: each moment on the lanes of its threads (the
 * names a lane merged), links and key moments that point at moments that exist, the chapter the
 * reader is in, and whether the spoiler cut starts on.
 */
public final class MapData {

    /** Characters on a page, for a rough count of requests before the book was read. */
    static final int CHARS_PER_PAGE = 1800;

    private MapData() {
    }

    /** Whether the map opens on the whole book: the reader's choice, else yes for a studied book. */
    public static boolean startWhole(MapBook book, boolean studied) {
        return book.whole != null ? book.whole : studied;
    }

    /** The last chapter shown: 0 for the whole book. */
    public static int cut(int readerChapter, boolean whole) {
        return whole || readerChapter <= 0 ? 0 : readerChapter;
    }

    /** The chapter number holding {@code page} (0-based) in the layout the map was built in; 0 when none. */
    public static int chapterAt(MapBook book, String chapterKey, int page) {
        final List<MapBook.Chapter> chapters = book.ordered();
        if (chapterKey != null) {
            final MapBook.Chapter byKey = book.chapter(chapterKey);
            if (byKey != null) {
                return byKey.n;
            }
        }
        int n = 0;
        for (MapBook.Chapter c : chapters) {
            if (page >= c.from) {
                n = c.n;
            }
        }
        return n == 0 && !chapters.isEmpty() ? chapters.get(0).n : n;
    }

    /** Whether chapters changed since the whole-book step ran: then "Update map". */
    public static boolean needsUpdate(MapBook book) {
        if (!book.hasBook()) {
            return false;
        }
        final Set<String> withMoments = new HashSet<>();
        for (MapBook.Chapter c : book.chapters.values()) {
            if (!c.moments.isEmpty()) {
                withMoments.add(c.key);
                if (c.t > book.t) {
                    return true;
                }
            }
        }
        return !withMoments.equals(new HashSet<>(book.seen));
    }

    /** A rough count of requests for a book of {@code pages} pages, before it was read. */
    public static int estimate(int pages, int partChars) {
        final long chars = (long) Math.max(1, pages) * CHARS_PER_PAGE;
        return (int) ((chars + partChars - 1) / Math.max(1, partChars)) + 1;
    }

    /** Moments the model gave whose quote was not found, in every chapter. */
    public static int dropped(MapBook book) {
        int n = 0;
        for (MapBook.Chapter c : book.chapters.values()) {
            n += c.dropped;
        }
        return n;
    }

    public static int moments(MapBook book) {
        int n = 0;
        for (MapBook.Chapter c : book.chapters.values()) {
            n += c.moments.size();
        }
        return n;
    }

    /**
     * The page's data.
     *
     * @param readerChapter the chapter the reader is in (0: not known)
     * @param whole         whether the map opens on the whole book
     */
    public static JSONObject join(String title, MapBook book, int readerChapter, boolean whole) throws JSONException {
        final JSONObject out = new JSONObject();
        out.put("title", title == null ? "" : title);
        out.put("premise", book.premise);
        out.put("reader", readerChapter);
        out.put("whole", whole);
        out.put("needsUpdate", needsUpdate(book));

        // a chapter thread name, any case, to the lane that merged it
        final Map<String, String> laneOf = new HashMap<>();
        final JSONArray lanes = new JSONArray();
        for (MapBook.Lane l : book.lanes) {
            lanes.put(new JSONObject().put("id", l.id).put("name", l.name).put("what", l.what));
            for (String name : l.merged) {
                final String k = norm(name);
                if (!laneOf.containsKey(k)) {
                    laneOf.put(k, l.id);
                }
            }
        }
        out.put("lanes", lanes);

        final JSONArray chapters = new JSONArray();
        final JSONArray moments = new JSONArray();
        final Set<String> ids = new HashSet<>();
        final List<String> fallbackKey = new ArrayList<>();
        for (MapBook.Chapter c : book.ordered()) {
            if (c.noText) {
                continue;
            }
            chapters.put(new JSONObject().put("n", c.n).put("title", c.title).put("words", Math.round(c.chars / 6.0)));
            // a name the chapter's moments use, any case, to the chapter thread it is (its name or an alias)
            final Map<String, String> canonical = new HashMap<>();
            for (MapBook.Thread t : c.threads) {
                canonical.put(norm(t.name), t.name);
                for (String alias : t.aliases) {
                    final String k = norm(alias);
                    if (!canonical.containsKey(k)) {
                        canonical.put(k, t.name);
                    }
                }
            }
            for (int i = 0; i < c.moments.size(); i++) {
                final MapBook.Moment m = c.moments.get(i);
                final String id = c.momentId(i);
                final Set<String> onLanes = new LinkedHashSet<>();
                final JSONArray others = new JSONArray();
                for (String name : m.threads) {
                    String lane = laneOf.get(norm(name));
                    if (lane == null && canonical.containsKey(norm(name))) {
                        lane = laneOf.get(norm(canonical.get(norm(name))));
                    }
                    if (lane != null) {
                        onLanes.add(lane);
                    } else {
                        others.put(name);
                    }
                }
                moments.put(new JSONObject().put("id", id).put("ch", c.n).put("kind", m.kind).put("label", m.label)
                        .put("quote", m.quote).put("page", m.page).put("at", m.at)
                        .put("lanes", new JSONArray(new ArrayList<>(onLanes))).put("others", others));
                ids.add(id);
                if (i == 0 || MapBook.TURN.equals(m.kind) || MapBook.RESOLVE.equals(m.kind)) {
                    fallbackKey.add(id);
                }
            }
        }
        out.put("chapters", chapters);
        out.put("moments", moments);

        final JSONArray arcs = new JSONArray();
        for (MapBook.Arc a : book.arcs) {
            if (ids.contains(a.from) && ids.contains(a.to)) {
                arcs.put(new JSONObject().put("from", a.from).put("to", a.to).put("type", a.type).put("why", a.why));
            }
        }
        out.put("arcs", arcs);
        final JSONArray key = new JSONArray();
        for (String k : book.key) {
            if (ids.contains(k)) {
                key.put(k);
            }
        }
        // no key moments from the model (an answer cut off before them): first, turns and payoffs
        out.put("key", key.length() > 0 ? key : new JSONArray(fallbackKey));
        return out;
    }

    private static String norm(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
