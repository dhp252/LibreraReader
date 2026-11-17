package com.foobnix.assist.readsyou;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What "The book reads you back" keeps for a book beside its chapters' signals, in the book's
 * assist file under {@code reads:state}: the reader's pace in this book (so a long page can be
 * told from the first page on), until when the break card stays away after "Keep reading", and
 * how often the break card came and was taken. No Android here.
 */
public final class ReadsState {

    public static final String KEY = "reads:state";

    /** Median milliseconds per word over the reader's latest pages of this book; 0 unknown. */
    public double pace;
    /** Pages that median was taken over. */
    public int paceSamples;
    /** Wall-clock time (ms) before which no break card comes; 0 for none. */
    public long snoozeUntil;
    /** Times the break card came up, the 5-minute break was taken, and read-aloud was started from it. */
    public int breaksOffered;
    public int breaksTaken;
    public int listens;

    public ReadsState copy() {
        final ReadsState s = new ReadsState();
        s.pace = pace;
        s.paceSamples = paceSamples;
        s.snoozeUntil = snoozeUntil;
        s.breaksOffered = breaksOffered;
        s.breaksTaken = breaksTaken;
        s.listens = listens;
        return s;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("pace", Math.round(pace * 10) / 10.0);
        o.put("paceSamples", paceSamples);
        o.put("snoozeUntil", snoozeUntil);
        o.put("breaksOffered", breaksOffered);
        o.put("breaksTaken", breaksTaken);
        o.put("listens", listens);
        return o;
    }

    /** The stored state; a fresh one for null. */
    public static ReadsState fromJson(JSONObject o) {
        final ReadsState s = new ReadsState();
        if (o == null) {
            return s;
        }
        final double pace = o.optDouble("pace", 0);
        s.pace = Double.isNaN(pace) || pace < ReadsRules.PACE_FASTEST || pace > ReadsRules.PACE_SLOWEST ? 0 : pace;
        s.paceSamples = Math.max(0, o.optInt("paceSamples"));
        s.snoozeUntil = Math.max(0, o.optLong("snoozeUntil"));
        s.breaksOffered = Math.max(0, o.optInt("breaksOffered"));
        s.breaksTaken = Math.max(0, o.optInt("breaksTaken"));
        s.listens = Math.max(0, o.optInt("listens"));
        return s;
    }
}
