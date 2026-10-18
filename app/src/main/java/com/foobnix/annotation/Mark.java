package com.foobnix.annotation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One mark the reader made on a book: a highlight, underline, strikethrough or pen stroke, with an
 * optional note. Kept outside the book ({@link MarksStore}), never inside the file
 * (.agents/features/F-008-separate-marks/design-2026-10-18.md).
 * <p>
 * Where it is: on a fixed layout (PDF, XPS, DjVu) the lines it covers on each page ({@link Part},
 * page-relative 0..1 like {@code TextWord}); in a reflowing book (EPUB, FB2, MOBI...) its own words
 * and the letters around them ({@link Anchor}), found again on each page after a new layout; a pen
 * stroke as runs of one width ({@link Ink}).
 */
public final class Mark {

    public static final String HIGHLIGHT = "highlight";
    public static final String UNDERLINE = "underline";
    public static final String STRIKE = "strike";
    public static final String INK = "ink";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The lines of a mark on one page of a fixed layout. */
    public static final class Part {
        public final int page;
        /** Line rectangles, page-relative: left, top, right, bottom for each line. */
        public final float[] rects;

        public Part(int page, float[] rects) {
            this.page = page;
            this.rects = rects;
        }
    }

    /** Where the words of a mark are in a reflowing book. */
    public static final class Anchor {
        /** Letters and digits right before and after the quote ({@code TtsTextLocator.normalize}). */
        public final String prefix;
        public final String suffix;
        /** The 0-based page the quote started on, in a layout of {@link #pageCount} pages. */
        public final int page;
        public final int pageCount;

        public Anchor(String prefix, String suffix, int page, int pageCount) {
            this.prefix = prefix == null ? "" : prefix;
            this.suffix = suffix == null ? "" : suffix;
            this.page = page;
            this.pageCount = pageCount;
        }
    }

    /** A piece of a pen stroke on one page, drawn at one width. */
    public static final class Ink {
        public final int page;
        /** Line width as a part of the page width. */
        public final float width;
        /** x, y pairs, page-relative. */
        public final float[] points;

        public Ink(int page, float width, float[] points) {
            this.page = page;
            this.width = width;
            this.points = points;
        }
    }

    public final String id;
    public String kind = HIGHLIGHT;
    /** {@code #rrggbb}. */
    public String color = "#ffd400";
    /** The reader's note; empty when there is none. */
    public String note = "";
    /** The marked words as the book has them; empty for a pen stroke. */
    public String quote = "";
    public long created;
    /** The last change: the newer of two changes of one mark wins when devices merge. */
    public long time;

    public final List<Part> parts = new ArrayList<>();
    public Anchor anchor;
    public final List<Ink> ink = new ArrayList<>();

    public Mark(String id) {
        this.id = id;
    }

    /** A new mark with a fresh id, made now. */
    public static Mark create(String kind, String color) {
        final Mark mark = new Mark(newId());
        mark.kind = kind;
        mark.color = color;
        mark.created = System.currentTimeMillis();
        mark.time = mark.created;
        return mark;
    }

    static String newId() {
        final byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        final StringBuilder id = new StringBuilder("m");
        for (byte b : bytes) {
            id.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return id.toString();
    }

    public boolean hasNote() {
        return note != null && !note.trim().isEmpty();
    }

    public boolean isInk() {
        return INK.equals(kind);
    }

    /** Pinned to page positions (a fixed layout), not found by its words. */
    public boolean isOnPositions() {
        return anchor == null;
    }

    /** The first page it is on (for a reflowing book: where it was made). */
    public int firstPage() {
        int first = Integer.MAX_VALUE;
        for (Part part : parts) {
            first = Math.min(first, part.page);
        }
        for (Ink run : ink) {
            first = Math.min(first, run.page);
        }
        if (anchor != null) {
            first = Math.min(first, anchor.page);
        }
        return first == Integer.MAX_VALUE ? 0 : first;
    }

    /** The top of the mark on its first page (page-relative), to order marks of one page. */
    public float firstTop() {
        final int page = firstPage();
        float top = Float.MAX_VALUE;
        for (Part part : parts) {
            if (part.page == page) {
                for (int i = 1; i < part.rects.length; i += 4) {
                    top = Math.min(top, part.rects[i]);
                }
            }
        }
        for (Ink run : ink) {
            if (run.page == page) {
                for (int i = 1; i < run.points.length; i += 2) {
                    top = Math.min(top, run.points[i]);
                }
            }
        }
        return top == Float.MAX_VALUE ? 0 : top;
    }

    /** Marks this mark changed now (newer than before even when a clock went back). */
    public void touch() {
        time = Math.max(System.currentTimeMillis(), time + 1);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("t", time);
        o.put("ct", created);
        o.put("k", kind);
        o.put("col", color);
        if (hasNote()) {
            o.put("note", note);
        }
        if (quote != null && !quote.isEmpty()) {
            o.put("q", quote);
        }
        if (!parts.isEmpty()) {
            final JSONArray array = new JSONArray();
            for (Part part : parts) {
                array.put(new JSONObject().put("pg", part.page).put("r", floats(part.rects)));
            }
            o.put("pdf", array);
        }
        if (anchor != null) {
            o.put("txt", new JSONObject()
                    .put("pre", anchor.prefix)
                    .put("suf", anchor.suffix)
                    .put("pg", anchor.page)
                    .put("pc", anchor.pageCount));
        }
        if (!ink.isEmpty()) {
            final JSONArray array = new JSONArray();
            for (Ink run : ink) {
                array.put(new JSONObject().put("pg", run.page).put("w", (double) run.width).put("p", floats(run.points)));
            }
            o.put("ink", array);
        }
        return o;
    }

    /** @return null when the object is not a mark this version understands */
    public static Mark fromJson(String id, JSONObject o) {
        if (o == null || id == null) {
            return null;
        }
        final Mark mark = new Mark(id);
        mark.time = o.optLong("t", 0);
        mark.created = o.optLong("ct", mark.time);
        mark.kind = o.optString("k", HIGHLIGHT);
        mark.color = o.optString("col", "#ffd400");
        mark.note = o.optString("note", "");
        mark.quote = o.optString("q", "");
        final JSONArray pdf = o.optJSONArray("pdf");
        for (int i = 0; pdf != null && i < pdf.length(); i++) {
            final JSONObject part = pdf.optJSONObject(i);
            final float[] rects = part == null ? null : floats(part.optJSONArray("r"));
            if (rects != null && rects.length >= 4 && rects.length % 4 == 0) {
                mark.parts.add(new Part(part.optInt("pg", 0), rects));
            }
        }
        final JSONObject txt = o.optJSONObject("txt");
        if (txt != null) {
            mark.anchor = new Anchor(txt.optString("pre", ""), txt.optString("suf", ""),
                    txt.optInt("pg", 0), txt.optInt("pc", 0));
        }
        final JSONArray runs = o.optJSONArray("ink");
        for (int i = 0; runs != null && i < runs.length(); i++) {
            final JSONObject run = runs.optJSONObject(i);
            final float[] points = run == null ? null : floats(run.optJSONArray("p"));
            if (points != null && points.length >= 4 && points.length % 2 == 0) {
                mark.ink.add(new Ink(run.optInt("pg", 0), (float) run.optDouble("w", 0.003), points));
            }
        }
        final boolean placed = !mark.parts.isEmpty() || mark.anchor != null || !mark.ink.isEmpty();
        return placed ? mark : null;
    }

    private static JSONArray floats(float[] values) throws JSONException {
        final JSONArray array = new JSONArray();
        for (float v : values) {
            // 5 decimals: a hundred-thousandth of a page is far below a pixel
            array.put(Math.round(v * 100000) / 100000.0);
        }
        return array;
    }

    private static float[] floats(JSONArray array) {
        if (array == null) {
            return null;
        }
        final float[] values = new float[array.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) array.optDouble(i, 0);
        }
        return values;
    }
}
