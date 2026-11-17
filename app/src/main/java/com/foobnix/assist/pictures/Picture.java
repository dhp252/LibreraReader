package com.foobnix.assist.pictures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One picture of a chapter: a process (steps in a row), a table (rows compared across columns)
 * or a causal loop (nodes round a circle, what holds it back), with the book's words it is drawn
 * from. Read from the model's answer and from the store by {@link #read}; {@link #check} then
 * keeps only what the text sent carries. Plain Java, no Android.
 */
public final class Picture {

    public enum Kind {
        PROCESS("process"), TABLE("table"), LOOP("loop");

        public final String code;

        Kind(String code) {
            this.code = code;
        }

        /** The kind a model named, or null ("flow", "steps", "comparison", "causal loop", "cycle" are understood too). */
        public static Kind of(String name) {
            final String s = name == null ? "" : name.trim().toLowerCase(Locale.US);
            if (s.contains("process") || s.contains("flow") || s.contains("step") || s.contains("sequence")) {
                return PROCESS;
            }
            if (s.contains("table") || s.contains("compar") || s.contains("grid") || s.contains("matrix")) {
                return TABLE;
            }
            if (s.contains("loop") || s.contains("cycle")) {
                return LOOP;
            }
            return null;
        }
    }

    public static final int MAX_STEPS = 12;
    public static final int MAX_COLUMNS = 6;
    public static final int MAX_ROWS = 8;
    public static final int MAX_NODES = 6;
    public static final int MAX_CARDS = 3;
    public static final int MAX_QUANTITIES = 3;

    /** A step of a process: a few words, and a short note beside them ("×2–3") or none. */
    public static final class Step {
        public String label;
        public String note;

        Step(String label, String note) {
            this.label = label;
            this.note = note;
        }
    }

    /** A row of a table: its name, a cell per column (empty: nothing said), and which cells are the best. */
    public static final class Row {
        public String label;
        public final List<String> cells = new ArrayList<>();
        public final List<Boolean> best = new ArrayList<>();

        Row(String label) {
            this.label = label;
        }
    }

    /** A "why it works" card. */
    public static final class Card {
        public String title;
        public String line;

        Card(String title, String line) {
            this.title = title;
            this.line = line;
        }
    }

    /** One of the quantities compared under a loop: its name, the value as written, and its size for the bar. */
    public static final class Quantity {
        public final String label;
        public final String value;
        public final double number;

        Quantity(String label, String value, double number) {
            this.label = label;
            this.value = value;
            this.number = number;
        }
    }

    public final Kind kind;
    public String title = "";
    /** The book's own words the picture is drawn from: the model's until {@link #check}, the book's after. */
    public String quote = "";
    /** 0-based page of the quote when it was found, or -1. */
    public int page = -1;
    public String cardsTitle = "";
    public final List<Card> cards = new ArrayList<>();

    // a process
    public final List<Step> steps = new ArrayList<>();
    public String caption = "";

    // a table
    /** The head of the column of row names ("Product"), or empty. */
    public String corner = "";
    public final List<String> columns = new ArrayList<>();
    public final List<Row> rows = new ArrayList<>();
    /** What a tinted cell means ("clear winner, in Smith's words"), or empty. */
    public String bestMeans = "";
    public String point = "";

    // a loop
    /** In order round the loop: each leads to the next, the last to the first. */
    public final List<String> nodes = new ArrayList<>();
    /** 1: reinforcing (R), -1: balancing (B), 0: the model did not say. */
    public int loopType;
    /** Two or three words under the mark ("feeds itself"), or empty. */
    public String loopLabel = "";
    /** What holds the loop back ("Transport"), or empty. */
    public String holds = "";
    /** What the compared quantities share ("Same six-week round trip"), or empty. */
    public String holdsBasis = "";
    public String holdsNote = "";
    public final List<Quantity> quantities = new ArrayList<>();

    Picture(Kind kind) {
        this.kind = kind;
    }

    // ------------------------------------------------------------------ reading

    /**
     * A picture from the model's answer or from the store; null when it is not one or lacks what
     * its kind needs. Lists are cut to their limits.
     *
     * @param kind the kind when the caller knows it (the answer named it by its key), else null
     */
    static Picture read(JSONObject o, Kind kind) {
        if (o == null) {
            return null;
        }
        final Kind named = Kind.of(o.optString("kind", ""));
        final Kind k = kind != null ? kind : named;
        if (k == null) {
            return null;
        }
        final Picture p = new Picture(k);
        p.title = PictureText.text(o, 80, "title", "name");
        p.quote = PictureText.oneLine(o.optString("quote", o.optString("from_the_text", "")));
        if (p.quote.length() > 1200) {
            p.quote = p.quote.substring(0, 1200);
        }
        p.page = o.optInt("page", -1);
        p.cardsTitle = PictureText.text(o, 60, "why_title");
        final JSONArray why = PictureText.list(o, "why", "why_it_works", "cards");
        for (int i = 0; why != null && i < why.length() && p.cards.size() < MAX_CARDS; i++) {
            final JSONObject c = why.optJSONObject(i);
            final Card card = new Card(PictureText.text(c, 30, "title", "name"), PictureText.text(c, 120, "line", "text", "note"));
            if (!card.title.isEmpty() && !card.line.isEmpty()) {
                p.cards.add(card);
            }
        }
        switch (k) {
            case PROCESS:
                p.readProcess(o);
                break;
            case TABLE:
                p.readTable(o);
                break;
            default:
                p.readLoop(o, named == null ? o.optString("kind", "") : "");
                break;
        }
        return p.isWhole() ? p : null;
    }

    private void readProcess(JSONObject o) {
        final JSONArray list = PictureText.list(o, "steps", "stages");
        for (int i = 0; list != null && i < list.length() && steps.size() < MAX_STEPS; i++) {
            final JSONObject s = list.optJSONObject(i);
            final String label = s != null ? PictureText.text(s, 40, "label", "name", "step", "text")
                    : PictureText.cut(PictureText.plain(list.opt(i)), 40);
            if (!label.isEmpty()) {
                steps.add(new Step(label, PictureText.text(s, 20, "note")));
            }
        }
        caption = PictureText.text(o, 200, "caption", "note");
    }

    private void readTable(JSONObject o) {
        corner = PictureText.text(o, 30, "corner", "rows_are");
        final JSONArray cols = PictureText.list(o, "columns", "headers");
        for (int i = 0; cols != null && i < cols.length() && columns.size() < MAX_COLUMNS; i++) {
            final JSONObject c = cols.optJSONObject(i);
            // an empty name keeps its place, so the cells stay under their columns
            columns.add(c != null ? PictureText.text(c, 40, "label", "name") : PictureText.cut(PictureText.plain(cols.opt(i)), 40));
        }
        final JSONArray list = PictureText.list(o, "rows");
        for (int i = 0; list != null && i < list.length() && rows.size() < MAX_ROWS; i++) {
            final JSONObject r = list.optJSONObject(i);
            if (r == null) {
                continue;
            }
            final Row row = new Row(PictureText.text(r, 60, "label", "name", "row"));
            final JSONArray cells = PictureText.list(r, "cells", "values");
            for (int c = 0; c < columns.size(); c++) {
                final Object cell = cells == null ? null : cells.opt(c);
                final JSONObject co = cell instanceof JSONObject ? (JSONObject) cell : null;
                row.cells.add(co != null ? PictureText.text(co, 80, "text", "value", "label") : PictureText.cut(PictureText.plain(cell), 80));
                row.best.add(co != null && co.optBoolean("best", false));
            }
            // "best": 1, or [0, 2]: the columns of this row's best cells, counted from 0
            final Object best = r.opt("best");
            if (best instanceof Number) {
                mark(row, ((Number) best).intValue());
            } else if (best instanceof JSONArray) {
                for (int b = 0; b < ((JSONArray) best).length(); b++) {
                    mark(row, ((JSONArray) best).optInt(b, -1));
                }
            }
            if (!row.label.isEmpty()) {
                rows.add(row);
            }
        }
        // "best": [[row, column], ...] for the whole table
        final JSONArray best = o.optJSONArray("best");
        for (int i = 0; best != null && i < best.length(); i++) {
            final JSONArray pair = best.optJSONArray(i);
            final JSONObject at = best.optJSONObject(i);
            final int r = pair != null ? pair.optInt(0, -1) : at != null ? at.optInt("row", -1) : -1;
            final int c = pair != null ? pair.optInt(1, -1) : at != null ? at.optInt("column", -1) : -1;
            if (r >= 0 && r < rows.size()) {
                mark(rows.get(r), c);
            }
        }
        bestMeans = PictureText.text(o, 80, "best_means", "legend");
        point = PictureText.text(o, 300, "point", "the_point");
    }

    private static void mark(Row row, int column) {
        if (column >= 0 && column < row.best.size() && !row.cells.get(column).isEmpty()) {
            row.best.set(column, true);
        }
    }

    private void readLoop(JSONObject o, String kindField) {
        final JSONArray list = PictureText.list(o, "nodes", "steps");
        for (int i = 0; list != null && i < list.length() && nodes.size() < MAX_NODES; i++) {
            final JSONObject n = list.optJSONObject(i);
            final String label = n != null ? PictureText.text(n, 60, "label", "name", "text")
                    : PictureText.cut(PictureText.plain(list.opt(i)), 60);
            if (!label.isEmpty()) {
                nodes.add(label);
            }
        }
        loopType = o.has("loop_type") ? o.optInt("loop_type", 0) : 0;
        if (loopType == 0) {
            // "type": "reinforcing"; a loop named by its key carries it as "kind"
            final String type = (PictureText.text(o, 30, "type", "loop", "polarity") + " " + kindField).toLowerCase(Locale.US);
            loopType = type.contains("balanc") ? -1 : type.contains("reinforc") ? 1 : 0;
        }
        loopType = Integer.signum(loopType);
        loopLabel = PictureText.text(o, 30, "label", "loop_label");
        final Object holdsBack = o.opt("holds_back");
        if (holdsBack instanceof JSONObject) {
            final JSONObject h = (JSONObject) holdsBack;
            holds = PictureText.text(h, 60, "what", "title", "name");
            holdsBasis = PictureText.text(h, 120, "basis", "same");
            holdsNote = PictureText.text(h, 240, "note", "so");
            final JSONArray compare = PictureText.list(h, "compare", "quantities");
            for (int i = 0; compare != null && i < compare.length() && quantities.size() < MAX_QUANTITIES; i++) {
                final JSONObject q = compare.optJSONObject(i);
                if (q == null) {
                    continue;
                }
                final double number = PictureText.number(q.opt("number"));
                final String label = PictureText.text(q, 60, "label", "name");
                final String value = PictureText.text(q, 30, "value", "text");
                if (!label.isEmpty() && !value.isEmpty() && !Double.isNaN(number) && !Double.isInfinite(number) && number >= 0) {
                    quantities.add(new Quantity(label, value, number));
                }
            }
        } else {
            holds = PictureText.cut(PictureText.plain(holdsBack), 60);
        }
    }

    /** Whether the picture has what its kind needs to be drawn. */
    boolean isWhole() {
        switch (kind) {
            case PROCESS:
                return steps.size() >= 2;
            case TABLE:
                return columns.size() >= 2 && rows.size() >= 2;
            default:
                return nodes.size() >= 2;
        }
    }

    // ------------------------------------------------------------------ the checks

    /** What a check took out of an answer. */
    static final class Left {
        /** Pictures left out: their words are not in the text sent. */
        int pictures;
        /** Lines, cells and quantities left out: they show a number the text sent does not have. */
        int numbers;
    }

    /**
     * Keeps the picture only when its quote is among the book's words sent (then {@link #quote}
     * becomes the book's words and {@link #page} their page), and takes out every line, cell and
     * quantity that shows a number the text sent does not have.
     *
     * @return false when the picture is to be left out
     */
    boolean check(PictureSource source, Left left) {
        final PictureSource.Found found = source.locate(quote);
        if (found == null) {
            left.pictures++;
            return false;
        }
        quote = found.text;
        page = found.page;
        final Set<String> known = source.numbers();
        title = keep(title, known, left);
        cardsTitle = keep(cardsTitle, known, left);
        for (int i = cards.size() - 1; i >= 0; i--) {
            final Card c = cards.get(i);
            c.title = keep(c.title, known, left);
            c.line = keep(c.line, known, left);
            if (c.title.isEmpty() || c.line.isEmpty()) {
                // half a card says nothing
                cards.remove(i);
            }
        }
        switch (kind) {
            case PROCESS:
                for (int i = steps.size() - 1; i >= 0; i--) {
                    final Step s = steps.get(i);
                    s.label = keep(s.label, known, left);
                    s.note = keep(s.note, known, left);
                    if (s.label.isEmpty()) {
                        steps.remove(i);
                    }
                }
                caption = keep(caption, known, left);
                break;
            case TABLE:
                corner = keep(corner, known, left);
                for (int c = columns.size() - 1; c >= 0; c--) {
                    final String name = columns.get(c);
                    if (name.isEmpty() || keep(name, known, left).isEmpty()) {
                        columns.remove(c);
                        for (Row r : rows) {
                            r.cells.remove(c);
                            r.best.remove(c);
                        }
                    }
                }
                for (int i = rows.size() - 1; i >= 0; i--) {
                    final Row r = rows.get(i);
                    r.label = keep(r.label, known, left);
                    if (r.label.isEmpty()) {
                        rows.remove(i);
                        continue;
                    }
                    for (int c = 0; c < r.cells.size(); c++) {
                        r.cells.set(c, keep(r.cells.get(c), known, left));
                        if (r.cells.get(c).isEmpty()) {
                            r.best.set(c, false);
                        }
                    }
                }
                bestMeans = keep(bestMeans, known, left);
                point = keep(point, known, left);
                break;
            default:
                for (String node : nodes) {
                    if (!PictureNumbers.allIn(node, known)) {
                        // a loop with a link taken out would say something else
                        left.numbers++;
                        left.pictures++;
                        return false;
                    }
                }
                loopLabel = keep(loopLabel, known, left);
                holds = keep(holds, known, left);
                holdsBasis = keep(holdsBasis, known, left);
                holdsNote = keep(holdsNote, known, left);
                for (int i = quantities.size() - 1; i >= 0; i--) {
                    final Quantity q = quantities.get(i);
                    if (!PictureNumbers.has(q.number, known) || !PictureNumbers.allIn(q.value, known)
                            || !PictureNumbers.allIn(q.label, known)) {
                        quantities.remove(i);
                        left.numbers++;
                    }
                }
                if (quantities.size() < 2) {
                    // one bar compares nothing
                    quantities.clear();
                }
                break;
        }
        if (!isWhole()) {
            left.pictures++;
            return false;
        }
        return true;
    }

    private static String keep(String line, Set<String> known, Left left) {
        if (line.isEmpty() || PictureNumbers.allIn(line, known)) {
            return line;
        }
        left.numbers++;
        return "";
    }

    /** Whether the card "what holds it back" has anything to show. */
    public boolean hasHolds() {
        return !holds.isEmpty() || !holdsNote.isEmpty() || !quantities.isEmpty();
    }

    /** The numbers of the compared quantities, in their order. */
    public double[] quantityNumbers() {
        final double[] out = new double[quantities.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = quantities.get(i).number;
        }
        return out;
    }

    // ------------------------------------------------------------------ the store

    JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("kind", kind.code);
        o.put("title", title);
        o.put("quote", quote);
        o.put("page", page);
        if (!cards.isEmpty()) {
            final JSONArray why = new JSONArray();
            for (Card c : cards) {
                why.put(new JSONObject().put("title", c.title).put("line", c.line));
            }
            o.put("why", why);
            o.put("why_title", cardsTitle);
        }
        switch (kind) {
            case PROCESS:
                final JSONArray list = new JSONArray();
                for (Step s : steps) {
                    list.put(new JSONObject().put("label", s.label).put("note", s.note));
                }
                o.put("steps", list);
                o.put("caption", caption);
                break;
            case TABLE:
                o.put("corner", corner);
                o.put("columns", new JSONArray(columns));
                final JSONArray rowList = new JSONArray();
                for (Row r : rows) {
                    final JSONArray best = new JSONArray();
                    for (int c = 0; c < r.best.size(); c++) {
                        if (r.best.get(c)) {
                            best.put(c);
                        }
                    }
                    rowList.put(new JSONObject().put("label", r.label).put("cells", new JSONArray(r.cells)).put("best", best));
                }
                o.put("rows", rowList);
                o.put("best_means", bestMeans);
                o.put("point", point);
                break;
            default:
                o.put("nodes", new JSONArray(nodes));
                o.put("loop_type", loopType);
                o.put("label", loopLabel);
                final JSONArray compare = new JSONArray();
                for (Quantity q : quantities) {
                    compare.put(new JSONObject().put("label", q.label).put("value", q.value).put("number", q.number));
                }
                o.put("holds_back", new JSONObject().put("what", holds).put("basis", holdsBasis).put("note", holdsNote)
                        .put("compare", compare));
                break;
        }
        return o;
    }
}
