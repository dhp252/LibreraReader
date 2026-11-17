package com.foobnix.assist.useit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One "Use it" experiment (Assist.USE_IT): an idea of a chapter tried in the reader's own work for
 * 1, 2 or 4 weeks, with up to three things measured before and during, and a verdict at the
 * check-in. Stored in the book's assist file as {@code use:<id>}. Days are local days
 * ({@link UseDates}); "due" is not a stored fact but follows from the check-in day and today
 * ({@link #stateOn}), so no alarm or scheduler is needed: the app looks when it is opened.
 */
public final class UseExperiment {

    /** Key prefix of an experiment in the book's assist file. */
    public static final String PREFIX = "use:";

    public static final String RUNNING = "running";
    public static final String DUE = "due";
    public static final String DONE = "done";

    public static final String HELD = "held";
    public static final String MOSTLY = "mostly";
    public static final String DIDNT = "didnt";

    public static final int MAX_MEASURES = 3;
    public static final int DEFAULT_WEEKS = 2;
    /** The lengths offered, in weeks. */
    static final int[] WEEKS = {1, 2, 4};

    /** One thing the reader counts or rates before and during the experiment. */
    public static final class Measure {
        public String name = "";
        /** A rating scale like "1–5", or empty for a count. */
        public String scale = "";
        /** A higher value is the good direction (tickets closed); false for task switches, boredom. */
        public boolean higherBetter = true;
        /** NaN until entered. */
        public double before = Double.NaN;
        public double during = Double.NaN;

        public Measure() {
        }

        public Measure(String name, String scale, boolean higherBetter) {
            this.name = clean(name);
            this.scale = clean(scale);
            this.higherBetter = higherBetter;
        }

        /** "Boredom, 1–5": the name, with its scale when it has one the name does not already say. */
        public String label() {
            // "1-5" and "1–5" are the same scale
            return scale.isEmpty() || name.replace('-', '–').contains(scale.replace('-', '–')) ? name : name + ", " + scale;
        }

        public boolean hasBoth() {
            return !Double.isNaN(before) && !Double.isNaN(during);
        }

        /** Before to during, or null until both are entered. */
        public Diff diff() {
            return hasBoth() ? new Diff(before, during, higherBetter) : null;
        }

        /** How long this value's bar is against the larger of the two values, 0 to 1. */
        public float share(double value) {
            if (Double.isNaN(value)) {
                return 0f;
            }
            double max = Math.abs(value);
            if (!Double.isNaN(before)) {
                max = Math.max(max, Math.abs(before));
            }
            if (!Double.isNaN(during)) {
                max = Math.max(max, Math.abs(during));
            }
            return max == 0 ? 0f : (float) Math.min(1.0, Math.abs(value) / max);
        }

        JSONObject toJson() throws JSONException {
            final JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("scale", scale);
            o.put("higher", higherBetter);
            if (!Double.isNaN(before)) {
                o.put("before", before);
            }
            if (!Double.isNaN(during)) {
                o.put("during", during);
            }
            return o;
        }

        static Measure fromJson(JSONObject o) {
            final Measure m = new Measure(o.optString("name"), o.optString("scale"), o.optBoolean("higher", true));
            m.before = o.has("before") ? finite(o.optDouble("before")) : Double.NaN;
            m.during = o.has("during") ? finite(o.optDouble("during")) : Double.NaN;
            return m;
        }
    }

    /** The change of a measure from before to during. */
    public static final class Diff {
        public final double delta;
        /** It moved against the good direction. */
        public final boolean worse;
        public final boolean same;
        /** "+7", "−5" (a real minus sign), "0". */
        public final String text;

        Diff(double before, double during, boolean higherBetter) {
            // rounded so 0.1 + 0.2 kinds of noise do not show as a change
            delta = Math.round((during - before) * 100.0) / 100.0;
            same = delta == 0;
            worse = !same && (delta > 0) != higherBetter;
            text = same ? "0" : (delta > 0 ? "+" : "−") + number(Math.abs(delta));
        }
    }

    public String id = "";
    /** The idea of the book the experiment tests, in a sentence. */
    public String idea = "";
    public String chapterKey = "";
    public String chapterTitle = "";
    public String bookTitle = "";
    public String author = "";
    /** The reader's answer to "Where would this change something you're doing this month?" */
    public String answer = "";
    public String tryText = "";
    public String predicts = "";
    /** The book's own caveat about this, or empty. */
    public String warning = "";
    /** Words of the book that carry the warning, or empty. */
    public String quote = "";
    /** The quote was found in the book's text: only then is it shown in quote marks. */
    public boolean quoted;
    public final List<Measure> measures = new ArrayList<>();
    public int weeks = DEFAULT_WEEKS;
    public String startDay = "";
    public String checkDay = "";
    /** {@link #RUNNING} or {@link #DONE} as last written ({@link #DUE} when it was due then); read it through {@link #stateOn}. */
    public String state = RUNNING;
    /** {@link #HELD}, {@link #MOSTLY}, {@link #DIDNT}, or empty before the check-in. */
    public String verdict = "";
    public String surprise = "";
    /** The day of the check-in, or empty. */
    public String doneDay = "";

    /** An id that sorts by creation time and does not collide between two devices. */
    public static String newId(long now, int random) {
        return Long.toString(Math.max(0, now), 36) + Integer.toString((random & 0x7fffffff) % 1296 + 1296, 36).substring(1);
    }

    /** The key of an experiment in the book's assist file. */
    public static String key(String id) {
        return PREFIX + id;
    }

    /** 1, 2 or 4; anything else becomes the default 2. */
    public static int weeks(int weeks) {
        for (int w : WEEKS) {
            if (w == weeks) {
                return w;
            }
        }
        return DEFAULT_WEEKS;
    }

    /** The check-in day of an experiment started on {@code startDay} and run {@code weeks} weeks. */
    public static String checkDay(String startDay, int weeks) {
        return UseDates.addDays(startDay, 7 * weeks(weeks));
    }

    /** Starts it today: the check-in is 7 days a week later. */
    public void start(String today, int weeks) {
        this.weeks = weeks(weeks);
        startDay = today;
        checkDay = checkDay(today, this.weeks);
        state = RUNNING;
        verdict = "";
        doneDay = "";
    }

    /** The check-in: the verdict and what surprised the reader; the day is kept from the first check-in. */
    public void finish(String today, String verdict, String surprise) {
        this.verdict = verdict(verdict);
        this.surprise = clean(surprise);
        if (!DONE.equals(state) || doneDay.isEmpty()) {
            doneDay = today == null ? "" : today;
        }
        state = DONE;
    }

    /** What it is on a day: done once checked in, due from its check-in day on, else running. */
    public String stateOn(String today) {
        if (DONE.equals(state)) {
            return DONE;
        }
        return UseDates.reached(today, checkDay) ? DUE : RUNNING;
    }

    public boolean isDue(String today) {
        return DUE.equals(stateOn(today));
    }

    /** Days until the check-in from {@code today}; 0 or less when due. */
    public int daysLeft(String today) {
        return UseDates.daysBetween(today, checkDay);
    }

    /** Every measure has a name: what can be started. */
    public boolean isComplete() {
        if (tryText.trim().isEmpty()) {
            return false;
        }
        for (Measure m : measures) {
            if (m.name.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** {@link #HELD}, {@link #MOSTLY} or {@link #DIDNT}; anything else is empty. */
    static String verdict(String v) {
        return HELD.equals(v) || MOSTLY.equals(v) || DIDNT.equals(v) ? v : "";
    }

    /**
     * The family name a card names the author by ("Smith predicts"): the last word of the first
     * author, or what stands before the comma in "Smith, Adam"; empty when there is no author.
     */
    public static String shortAuthor(String author) {
        if (author == null) {
            return "";
        }
        String first = author.trim();
        final int and = first.toLowerCase(java.util.Locale.US).indexOf(" and ");
        if (and > 0) {
            first = first.substring(0, and);
        }
        for (char c : new char[]{';', '&', '/'}) {
            final int at = first.indexOf(c);
            if (at > 0) {
                first = first.substring(0, at);
            }
        }
        final int comma = first.indexOf(',');
        if (comma > 0) {
            // "Smith, Adam", or "Adam Smith, David Hume": the part before the comma either way
            first = first.substring(0, comma);
        }
        first = first.trim();
        final int space = first.lastIndexOf(' ');
        return space < 0 ? first : first.substring(space + 1);
    }

    /**
     * A value as the reader types and reads it: whole numbers without a fraction, else up to two
     * decimals; empty for a value not entered.
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        final double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
            return Long.toString((long) rounded);
        }
        String s = String.format(java.util.Locale.US, "%.2f", rounded);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** What the reader typed as a number ("31", "3,5", " 4.25 ", "−2"); NaN when it is not one. */
    public static double parseNumber(String text) {
        if (text == null) {
            return Double.NaN;
        }
        final String s = text.trim().replace(',', '.').replace('−', '-').replace(" ", "");
        if (s.isEmpty()) {
            return Double.NaN;
        }
        try {
            return finite(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double finite(double v) {
        return Double.isInfinite(v) ? Double.NaN : v;
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("idea", idea);
        o.put("chapter", chapterKey);
        o.put("chapterTitle", chapterTitle);
        o.put("book", bookTitle);
        o.put("author", author);
        o.put("answer", answer);
        o.put("try", tryText);
        o.put("predicts", predicts);
        o.put("warning", warning);
        o.put("quote", quote);
        o.put("quoted", quoted);
        final JSONArray list = new JSONArray();
        for (Measure m : measures) {
            list.put(m.toJson());
        }
        o.put("measures", list);
        o.put("weeks", weeks);
        o.put("start", startDay);
        o.put("checkin", checkDay);
        o.put("state", state);
        o.put("verdict", verdict);
        o.put("surprise", surprise);
        o.put("doneDay", doneDay);
        return o;
    }

    /** An experiment as stored, or null when the item is not one (no id, no "try"). */
    public static UseExperiment fromJson(JSONObject o) {
        if (o == null || o.optString("id").isEmpty() || o.optString("try").trim().isEmpty()) {
            return null;
        }
        final UseExperiment e = new UseExperiment();
        e.id = o.optString("id");
        e.idea = o.optString("idea");
        e.chapterKey = o.optString("chapter");
        e.chapterTitle = o.optString("chapterTitle");
        e.bookTitle = o.optString("book");
        e.author = o.optString("author");
        e.answer = o.optString("answer");
        e.tryText = o.optString("try");
        e.predicts = o.optString("predicts");
        e.warning = o.optString("warning");
        e.quote = o.optString("quote");
        e.quoted = o.optBoolean("quoted");
        final JSONArray list = o.optJSONArray("measures");
        for (int i = 0; list != null && i < list.length() && e.measures.size() < MAX_MEASURES; i++) {
            final JSONObject m = list.optJSONObject(i);
            if (m != null) {
                e.measures.add(Measure.fromJson(m));
            }
        }
        e.weeks = weeks(o.optInt("weeks", DEFAULT_WEEKS));
        e.startDay = o.optString("start");
        e.checkDay = o.optString("checkin");
        e.state = DONE.equals(o.optString("state")) ? DONE : RUNNING;
        e.verdict = verdict(o.optString("verdict"));
        e.surprise = o.optString("surprise");
        e.doneDay = o.optString("doneDay");
        return e;
    }

    /** The item to store: {@link #toJson} with the state it has on {@code today} (running, due or done). */
    public JSONObject toStore(String today) throws JSONException {
        return toJson().put("state", stateOn(today));
    }
}
