package com.foobnix.assist.fiction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The timeline and the places of a story as far as the reader has read ({@code fiction:timeline}
 * in the book's AssistStore file): the key moments so far, each with the time as the book gives
 * it and its place; the places in order of visit; and, only when the book itself states a plan
 * or a deadline (a journey in legs, a countdown), where the story stands against it. Free of
 * Android.
 * <p>
 * Like the cast, it carries the place in the book it was built from ({@link #mark}), and a
 * moment or place the model puts in a chapter after the last one it was sent is dropped. A
 * moment's quote is kept only as the book's own words ({@link FictionQuotes}); coordinates are
 * the model's outside knowledge and are kept only for places it calls real.
 */
public final class FictionTimeline {

    public static final String KEY = "fiction:timeline";

    static final int MAX_MOMENTS = 150;
    static final int MAX_PLACES = 120;
    static final int MAX_LEGS = 40;

    public static final String DONE = "done";
    public static final String HERE = "here";
    public static final String AHEAD = "ahead";

    public static final class Moment {
        public int ch;
        public String key = "";
        /** The time as the book gives it ("Day 7", "2 October"), or "". */
        public String time = "";
        public String place = "";
        public String line = "";
        /** The book's own words when they were found in the text; else "". */
        public String quote = "";
        /** The page (0-based, in the mark's layout) the quote was found on; -1 without one. */
        public int page = -1;
    }

    /** One leg of the plan the book states: what was planned and, once travelled, what it took. */
    public static final class Leg {
        public String from = "";
        public String to = "";
        /** Planned length in the plan's unit; 0 when the book does not say. */
        public double plan;
        /** What it took so far; -1 for a leg not begun. */
        public double actual = -1;
        /** {@link #DONE}, {@link #HERE} or {@link #AHEAD}. */
        public String state = AHEAD;
        public String note = "";
    }

    public static final class Plan {
        /** What the plan is, in a few words. */
        public String what = "";
        /** "day", "hour"… as the book counts. */
        public String unit = "";
        public double total;
        /** How far the count is at the mark; 0 when the text does not say. */
        public double now;
        /** "On schedule", "Two days behind"…: the model's words. */
        public String status = "";
        public String note = "";
        /** Where the book states it: "the Daily Telegraph's estimate". */
        public String source = "";
        public int sourceCh;
        public String sourceKey = "";
        public final List<Leg> legs = new ArrayList<>();
    }

    public static final class Place {
        public String name = "";
        /** The chapter the story first reaches it in. */
        public int ch;
        public String key = "";
        /** A place of the real world (the model's knowledge); invented places are never located. */
        public boolean real;
        /** It has coordinates. */
        public boolean located;
        public double lat;
        public double lon;
        public String quote = "";
        public int page = -1;
    }

    public FictionMark mark = new FictionMark();
    public String model = "";
    public boolean story = true;
    public final List<Moment> moments = new ArrayList<>();
    /** Null when the book states no plan or deadline. */
    public Plan plan;
    /** The places so far, in order of visit. */
    public final List<Place> places = new ArrayList<>();
    /** Places the plan names that the story has not reached: only a name and, when real, coordinates. */
    public final List<Place> stops = new ArrayList<>();
    /** Where the story is at the mark: "On the train from Benares to Calcutta". */
    public String here = "";
    /** The time at the mark as the book gives it: "Day 22". */
    public String hereTime = "";
    /** "Next, by the plan: …"; only with a plan. */
    public String next = "";

    public boolean isEmpty() {
        return moments.isEmpty() && places.isEmpty() && plan == null;
    }

    // ------------------------------------------------------------------ the stored item

    public JSONObject toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("v", 1);
            mark.write(o);
            o.put("model", model);
            final JSONArray ms = new JSONArray();
            for (Moment m : moments) {
                ms.put(new JSONObject().put("ch", m.ch).put("key", m.key).put("time", m.time).put("place", m.place)
                        .put("line", m.line).put("quote", m.quote).put("page", m.page));
            }
            o.put("moments", ms);
            if (plan != null) {
                final JSONObject p = new JSONObject();
                p.put("what", plan.what).put("unit", plan.unit).put("total", plan.total).put("now", plan.now)
                        .put("status", plan.status).put("note", plan.note).put("source", plan.source)
                        .put("sourceCh", plan.sourceCh).put("sourceKey", plan.sourceKey);
                final JSONArray legs = new JSONArray();
                for (Leg l : plan.legs) {
                    legs.put(new JSONObject().put("from", l.from).put("to", l.to).put("plan", l.plan)
                            .put("actual", l.actual).put("state", l.state).put("note", l.note));
                }
                p.put("legs", legs);
                o.put("plan", p);
            }
            o.put("places", places(places));
            o.put("stops", places(stops));
            o.put("here", here).put("hereTime", hereTime).put("next", next);
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONArray places(List<Place> list) throws JSONException {
        final JSONArray out = new JSONArray();
        for (Place p : list) {
            final JSONObject j = new JSONObject().put("name", p.name).put("ch", p.ch).put("key", p.key)
                    .put("real", p.real).put("quote", p.quote).put("page", p.page);
            if (p.located) {
                j.put("lat", p.lat).put("lon", p.lon);
            }
            out.put(j);
        }
        return out;
    }

    /** The stored timeline; empty (never null) when there is none or it cannot be read. */
    public static FictionTimeline fromJson(JSONObject o) {
        final FictionTimeline t = new FictionTimeline();
        if (o == null) {
            return t;
        }
        t.mark = FictionMark.read(o);
        t.model = o.optString("model", "");
        final JSONArray ms = o.optJSONArray("moments");
        for (int i = 0; ms != null && i < ms.length(); i++) {
            final JSONObject j = ms.optJSONObject(i);
            final String line = j == null ? "" : FictionCast.clean(j.optString("line"));
            if (line.isEmpty()) {
                continue;
            }
            final Moment m = new Moment();
            m.ch = Math.max(0, j.optInt("ch"));
            m.key = j.optString("key", "");
            m.time = FictionCast.clean(j.optString("time"));
            m.place = FictionCast.clean(j.optString("place"));
            m.line = line;
            m.quote = FictionCast.clean(j.optString("quote"));
            m.page = m.quote.isEmpty() ? -1 : j.optInt("page", -1);
            t.moments.add(m);
        }
        final JSONObject p = o.optJSONObject("plan");
        if (p != null) {
            final Plan plan = new Plan();
            plan.what = FictionCast.clean(p.optString("what"));
            plan.unit = FictionCast.clean(p.optString("unit"));
            plan.total = Math.max(0, p.optDouble("total", 0));
            plan.now = Math.max(0, p.optDouble("now", 0));
            plan.status = FictionCast.clean(p.optString("status"));
            plan.note = FictionCast.clean(p.optString("note"));
            plan.source = FictionCast.clean(p.optString("source"));
            plan.sourceCh = Math.max(0, p.optInt("sourceCh"));
            plan.sourceKey = p.optString("sourceKey", "");
            final JSONArray legs = p.optJSONArray("legs");
            for (int i = 0; legs != null && i < legs.length(); i++) {
                final JSONObject j = legs.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                final Leg l = new Leg();
                l.from = FictionCast.clean(j.optString("from"));
                l.to = FictionCast.clean(j.optString("to"));
                l.plan = Math.max(0, j.optDouble("plan", 0));
                l.actual = j.optDouble("actual", -1);
                l.state = state(j.optString("state"));
                if (l.state.isEmpty()) {
                    l.state = AHEAD;
                }
                l.note = FictionCast.clean(j.optString("note"));
                if (!l.from.isEmpty() || !l.to.isEmpty()) {
                    plan.legs.add(l);
                }
            }
            if (!plan.legs.isEmpty() || plan.total > 0) {
                t.plan = plan;
            }
        }
        readPlaces(o.optJSONArray("places"), t.places);
        readPlaces(o.optJSONArray("stops"), t.stops);
        t.here = FictionCast.clean(o.optString("here"));
        t.hereTime = FictionCast.clean(o.optString("hereTime"));
        t.next = FictionCast.clean(o.optString("next"));
        return t;
    }

    private static void readPlaces(JSONArray a, List<Place> into) {
        for (int i = 0; a != null && i < a.length(); i++) {
            final JSONObject j = a.optJSONObject(i);
            final String name = j == null ? "" : FictionCast.clean(j.optString("name"));
            if (name.isEmpty()) {
                continue;
            }
            final Place p = new Place();
            p.name = name;
            p.ch = Math.max(0, j.optInt("ch"));
            p.key = j.optString("key", "");
            p.real = j.optBoolean("real");
            locate(p, j);
            p.quote = FictionCast.clean(j.optString("quote"));
            p.page = p.quote.isEmpty() ? -1 : j.optInt("page", -1);
            into.add(p);
        }
    }

    /** Coordinates only for a real place, and only when both are numbers on the globe. */
    private static void locate(Place p, JSONObject j) {
        final double lat = number(j, "lat", "latitude");
        final double lon = number(j, "lon", "lng", "longitude");
        p.located = p.real && !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
                && !(lat == 0 && lon == 0);
        p.lat = p.located ? lat : 0;
        p.lon = p.located ? lon : 0;
    }

    // ------------------------------------------------------------------ the model's answer

    /**
     * The model's answer to a timeline request, read tolerantly; null when it cannot be read.
     * Quotes are kept as the model gave them: the caller checks them against the text
     * ({@link FictionQuotes}) before anything is stored.
     *
     * @param lastN number of the last chapter of the text that was sent: a moment or place put
     *              after it is dropped, and so is a plan said to be stated after it
     */
    public static FictionTimeline parse(String answer, FictionChapters chapters, int lastN) {
        final JSONObject o = FictionCast.object(answer);
        if (o == null) {
            return null;
        }
        final FictionTimeline t = new FictionTimeline();
        final Object story = o.opt("story");
        if (Boolean.FALSE.equals(story) || "false".equalsIgnoreCase(String.valueOf(story))) {
            t.story = false;
            return t;
        }
        final JSONArray ms = o.optJSONArray("moments");
        for (int i = 0; ms != null && i < ms.length() && t.moments.size() < MAX_MOMENTS; i++) {
            final JSONObject j = ms.optJSONObject(i);
            final String line = j == null ? "" : FictionCast.clean(FictionCast.first(j, "line", "text"));
            if (line.isEmpty()) {
                continue;
            }
            int ch = FictionCast.number(j, "ch", "chapter");
            if (ch > lastN) {
                continue;
            }
            final Moment m = new Moment();
            m.ch = ch < 1 ? lastN : ch;
            m.key = chapters.key(m.ch);
            m.time = FictionCast.clean(FictionCast.first(j, "time", "when", "day"));
            m.place = FictionCast.clean(FictionCast.first(j, "place", "where"));
            m.line = line;
            m.quote = FictionCast.clean(j.optString("quote"));
            t.moments.add(m);
        }
        final JSONObject p = o.optJSONObject("plan");
        if (p != null) {
            final Plan plan = new Plan();
            plan.what = FictionCast.clean(p.optString("what"));
            plan.unit = FictionCast.clean(p.optString("unit")).toLowerCase(Locale.ROOT);
            plan.total = positive(number(p, "total"));
            plan.now = positive(number(p, "now"));
            plan.status = FictionCast.clean(p.optString("status"));
            plan.note = FictionCast.clean(p.optString("note"));
            plan.source = FictionCast.clean(p.optString("source"));
            plan.sourceCh = Math.max(0, FictionCast.number(p, "source_ch", "sourceCh"));
            plan.sourceKey = chapters.key(plan.sourceCh);
            final JSONArray legs = p.optJSONArray("legs");
            boolean begun = true;
            for (int i = 0; legs != null && i < legs.length() && plan.legs.size() < MAX_LEGS; i++) {
                final JSONObject j = legs.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                final Leg l = new Leg();
                l.from = FictionCast.clean(j.optString("from"));
                l.to = FictionCast.clean(j.optString("to"));
                if (l.from.isEmpty() && l.to.isEmpty()) {
                    continue;
                }
                l.plan = positive(number(j, "plan", "planned"));
                final double actual = number(j, "actual");
                l.note = FictionCast.clean(j.optString("note"));
                final boolean hasActual = !Double.isNaN(actual);
                String state = state(j.optString("state"));
                if (state.isEmpty()) {
                    state = hasActual ? DONE : AHEAD;
                }
                // after the leg the story is on, nothing has been travelled: no "actual" survives
                if (!begun || AHEAD.equals(state)) {
                    l.state = AHEAD;
                    l.actual = -1;
                    l.note = "";
                    begun = false;
                } else {
                    l.state = state;
                    l.actual = hasActual ? Math.max(0, actual) : -1;
                    if (HERE.equals(state)) {
                        begun = false;
                    }
                }
                plan.legs.add(l);
            }
            if (plan.sourceCh <= lastN && (!plan.legs.isEmpty() || plan.total > 0)) {
                t.plan = plan;
            }
        }
        final JSONArray ps = o.optJSONArray("places");
        for (int i = 0; ps != null && i < ps.length() && t.places.size() < MAX_PLACES; i++) {
            final JSONObject j = ps.optJSONObject(i);
            final String name = j == null ? "" : FictionCast.clean(j.optString("name"));
            if (name.isEmpty()) {
                continue;
            }
            final int ch = FictionCast.number(j, "ch", "chapter");
            if (ch > lastN) {
                continue;
            }
            final Place place = new Place();
            place.name = name;
            place.ch = ch < 1 ? lastN : ch;
            place.key = chapters.key(place.ch);
            place.real = truth(j.opt("real"));
            locate(place, j);
            place.quote = FictionCast.clean(j.optString("quote"));
            t.places.add(place);
        }
        if (t.plan != null) {
            final JSONArray ss = o.optJSONArray("plan_places");
            for (int i = 0; ss != null && i < ss.length() && t.stops.size() < MAX_PLACES; i++) {
                final JSONObject j = ss.optJSONObject(i);
                final String name = j == null ? "" : FictionCast.clean(j.optString("name"));
                if (name.isEmpty()) {
                    continue;
                }
                final Place stop = new Place();
                stop.name = name;
                stop.real = truth(j.opt("real"));
                locate(stop, j);
                t.stops.add(stop);
            }
        }
        final Object here = o.opt("here");
        if (here instanceof JSONObject) {
            t.here = FictionCast.clean(FictionCast.first((JSONObject) here, "line", "text", "place"));
            t.hereTime = FictionCast.clean(FictionCast.first((JSONObject) here, "time", "when"));
        } else if (here instanceof String) {
            t.here = FictionCast.clean((String) here);
        }
        if (t.plan != null) {
            t.next = FictionCast.clean(o.optString("next"));
        }
        return t;
    }

    // ------------------------------------------------------------------ an update

    /**
     * Takes in what a later part of the text gave: its moments and places are added after the
     * ones here; a plan in it takes the place of the plan here (the model gets the stored one and
     * gives it back whole); where the story is now is always the update's. The mark is not
     * touched: the caller sets it.
     */
    public void merge(FictionTimeline update) {
        if (update == null) {
            return;
        }
        for (Moment m : update.moments) {
            boolean have = false;
            for (Moment old : moments) {
                if (old.ch == m.ch && FictionCast.same(old.line, m.line)) {
                    have = true;
                    break;
                }
            }
            if (!have && moments.size() < MAX_MOMENTS) {
                moments.add(m);
            }
        }
        for (Place p : update.places) {
            boolean have = false;
            for (Place old : places) {
                if (old.ch == p.ch && FictionCast.same(old.name, p.name)) {
                    have = true;
                    break;
                }
            }
            // the same place twice in a row is one stay
            if (!have && !places.isEmpty() && FictionCast.same(places.get(places.size() - 1).name, p.name)) {
                have = true;
            }
            if (!have && places.size() < MAX_PLACES) {
                places.add(p);
            }
        }
        if (update.plan != null) {
            plan = update.plan;
            for (Place s : update.stops) {
                int at = -1;
                for (int i = 0; i < stops.size(); i++) {
                    if (FictionCast.same(stops.get(i).name, s.name)) {
                        at = i;
                        break;
                    }
                }
                if (at >= 0) {
                    stops.set(at, s);
                } else if (stops.size() < MAX_PLACES) {
                    stops.add(s);
                }
            }
        }
        here = update.here;
        hereTime = update.hereTime;
        next = plan == null ? "" : update.next;
    }

    /**
     * Brings the pages of the found quotes into another layout: they were found in a book of
     * {@code fromPages} pages, the text is now read in one of {@code toPages}. They are hints for
     * the jump to the quote, which looks around them.
     */
    public void rescale(int fromPages, int toPages) {
        if (fromPages <= 0 || toPages <= 0 || fromPages == toPages) {
            return;
        }
        for (Moment m : moments) {
            m.page = scaled(m.page, fromPages, toPages);
        }
        for (Place p : places) {
            p.page = scaled(p.page, fromPages, toPages);
        }
    }

    /** A page of a layout of {@code fromPages} pages in one of {@code toPages}; -1 stays -1. */
    public static int scaled(int page, int fromPages, int toPages) {
        if (page < 0 || fromPages <= 0 || toPages <= 0 || fromPages == toPages) {
            return page;
        }
        return (int) Math.max(0, Math.min(toPages - 1L, (long) page * toPages / fromPages));
    }

    /** A place by name (any case), among the places visited, then the plan's stops; null when unknown. */
    public Place place(String name) {
        for (Place p : places) {
            if (FictionCast.same(p.name, name)) {
                return p;
            }
        }
        for (Place p : stops) {
            if (FictionCast.same(p.name, name)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ texts

    /** "6½", "7", "4.3": an amount of days or hours as the timeline prints it. */
    public static String amount(double v) {
        final double halves = v * 2;
        if (Math.abs(halves - Math.rint(halves)) < 1e-6) {
            final long whole = (long) Math.floor(v + 1e-6);
            final boolean half = Math.rint(halves) % 2 != 0;
            return half ? (whole == 0 ? "½" : whole + "½") : String.valueOf(whole);
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** "d" for days, "h" for hours, "wk" for weeks, else the unit as the model gave it. */
    public static String unitShort(String unit) {
        final String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (u.startsWith("day")) {
            return "d";
        }
        if (u.startsWith("hour")) {
            return "h";
        }
        if (u.startsWith("week")) {
            return "wk";
        }
        return u;
    }

    /** What a leg's row says at its right: "6½ of 7 d" once begun, "13 d" ahead, "" when nothing is known. */
    public static String legText(Leg l, String unit) {
        final String u = unitShort(unit);
        final String tail = u.isEmpty() ? "" : " " + u;
        if (l.actual >= 0 && !AHEAD.equals(l.state)) {
            return l.plan > 0 ? amount(l.actual) + " of " + amount(l.plan) + tail : amount(l.actual) + tail;
        }
        return l.plan > 0 ? amount(l.plan) + tail : "";
    }

    /** "Day 22" for a count in days; "" when the plan has no count yet. */
    public static String count(Plan plan) {
        if (plan == null || plan.now <= 0) {
            return "";
        }
        final String u = plan.unit == null ? "" : plan.unit.trim();
        final String unit = u.isEmpty() ? "" : u.substring(0, 1).toUpperCase(Locale.ROOT) + u.substring(1);
        return (unit.isEmpty() ? "" : unit + " ") + amount(plan.now);
    }

    private static String state(String s) {
        final String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (v.equals(DONE) || v.equals("finished") || v.equals("complete") || v.equals("completed")) {
            return DONE;
        }
        if (v.equals(HERE) || v.equals("current") || v.equals("now") || v.equals("in progress")) {
            return HERE;
        }
        if (v.equals(AHEAD) || v.equals("future") || v.equals("planned") || v.equals("not started")) {
            return AHEAD;
        }
        return "";
    }

    private static boolean truth(Object v) {
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)) || "yes".equalsIgnoreCase(String.valueOf(v));
    }

    private static double positive(double v) {
        return Double.isNaN(v) || v < 0 ? 0 : v;
    }

    /** The first of the fields that holds a number (12, 6.5, "6.5", "6½ days"); NaN when none does. */
    static double number(JSONObject o, String... names) {
        for (String n : names) {
            final Object v = o.opt(n);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v instanceof String) {
                String s = ((String) v).trim().replace(',', '.');
                double half = 0;
                if (s.contains("½")) {
                    half = 0.5;
                    s = s.replace("½", " ");
                }
                final java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(s);
                if (m.find()) {
                    try {
                        final double d = Double.parseDouble(m.group());
                        return d < 0 ? d - half : d + half;
                    } catch (NumberFormatException e) {
                        // not a number: the next field
                    }
                } else if (half > 0) {
                    return half;
                }
            }
        }
        return Double.NaN;
    }
}
