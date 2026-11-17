package com.foobnix.assist.zoom;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Why the reader reads a book (canvas ZoomGoal), kept per book as the {@code zoom:goal} item of
 * its assist file and sent with the zoom request, so the model decides what stays in full and what
 * is squeezed. The reader's own words are not stored a second time when they are the reading goal
 * the book already has for its key passages (F-004, the AI file): then only the kind is kept here
 * and the words are read from there. No Android here.
 */
public final class ZoomGoal {

    public static final String KEY = "zoom:goal";

    /** The four goals of the canvas. */
    public enum Kind {
        IDEAS("ideas", "The reader wants the big ideas: keep every claim and the single best example for each; "
                + "squeeze everything else."),
        WORK("work", "The reader wants to use the book at work: keep in full whatever they could act on (people and "
                + "teams, process, tools, decisions); squeeze history and scholarly detail."),
        DISCUSS("discuss", "The reader is preparing to discuss the book: keep the arguments, the objections and "
                + "caveats, and lines worth quoting; squeeze routine description."),
        STUDY("study", "The reader studies the book closely: keep every argument, example and qualification, and cut "
                + "only plain repetition; the lean version may then be most of the original length.");

        /** Its name in the stored item. */
        public final String id;
        /** What it asks of the model. */
        final String ask;

        Kind(String id, String ask) {
            this.id = id;
            this.ask = ask;
        }

        /** The kind stored under a name; {@link #IDEAS} when unknown. */
        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return IDEAS;
        }
    }

    /** Characters of the reader's own words sent and stored at most. */
    static final int MAX_WORDS = 300;

    public final Kind kind;
    /**
     * The reader's own words as typed for Semantic zoom (may be empty: none); null when they follow
     * the book's reading goal of the key passages.
     */
    public final String text;

    public ZoomGoal(Kind kind, String text) {
        this.kind = kind == null ? Kind.IDEAS : kind;
        this.text = text == null ? null : clean(text);
    }

    /**
     * The goal the reader chose on the goal screen. Words left as the book's reading goal are not
     * kept a second time.
     *
     * @param typed    what the own-words field holds
     * @param bookGoal the book's reading goal of the key passages; null or empty when it has none
     */
    public static ZoomGoal chosen(Kind kind, String typed, String bookGoal) {
        final String words = clean(typed);
        return new ZoomGoal(kind, words.equals(clean(bookGoal)) ? null : words);
    }

    /** The reader's own words: typed here, else the book's reading goal; empty when neither. */
    public String words(String bookGoal) {
        return text != null ? text : clean(bookGoal);
    }

    /** Names the goal a chapter was condensed for: the same again means its levels still fit. */
    public String id(String bookGoal) {
        final String words = words(bookGoal);
        return words.isEmpty() ? kind.id : kind.id + "|" + words;
    }

    /** The goal as the request states it. */
    public String prompt(String bookGoal) {
        final String words = words(bookGoal);
        return kind.ask + (words.isEmpty() ? "" : " In the reader's own words, they read it for: " + words);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("kind", kind.id);
        if (text != null) {
            o.put("text", text);
        }
        return o;
    }

    /** The stored goal; null when the book has none. */
    public static ZoomGoal fromJson(JSONObject o) {
        if (o == null || !o.has("kind")) {
            return null;
        }
        final Object text = o.isNull("text") ? null : o.opt("text");
        return new ZoomGoal(Kind.of(o.optString("kind", "")), text instanceof String ? (String) text : null);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        final String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > MAX_WORDS ? t.substring(0, MAX_WORDS).trim() : t;
    }
}
