package com.foobnix.assist.lens;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The reader's lens (canvas "Explain it my way", Assist.LENS): the fields they know well and how
 * explanations should borrow from them. One for every book, kept in the library file of
 * {@code AssistStore} under {@link #KEY}; the reader's level in a book's subject is kept per book
 * under {@link #LEVEL_KEY}. No Android here.
 */
public final class LensProfile {

    /** The item of the library file. */
    public static final String KEY = "lens:profile";
    /** The item of a book's file: {@code {"level": "new"}}. */
    public static final String LEVEL_KEY = "lens:level";

    /** The fields offered as chips, in the canvas's order. */
    public static final String[] PRESETS = {"Machine learning", "Software engineering", "Statistics", "Product",
            "Finance", "Medicine", "Cooking", "Football"};
    /** How a preset reads inside a sentence and on the "Lens:" chip. */
    private static final String[] SHORT = {"ML", "software", "statistics", "product", "finance", "medicine",
            "cooking", "football"};

    /** Fields sent at most (the presets picked first, then the reader's own). */
    public static final int MAX_FIELDS = 6;
    /** Characters of one field of the reader's own. */
    public static final int FIELD_CHARS = 40;

    /** How much the reader knows of a book's subject. */
    public enum Level {
        NEW("new"), SOME("some"), EXPERT("expert");

        public final String code;

        Level(String code) {
            this.code = code;
        }

        /** A stored level; {@link #NEW} for anything else (the canvas's default). */
        public static Level of(String code) {
            for (Level l : values()) {
                if (l.code.equalsIgnoreCase(code == null ? "" : code.trim())) {
                    return l;
                }
            }
            return NEW;
        }

        public JSONObject toJson() throws JSONException {
            return new JSONObject().put("level", code);
        }

        public static Level fromJson(JSONObject o) {
            return o == null ? NEW : of(o.optString("level", ""));
        }
    }

    /** The presets picked, by name, in the order of {@link #PRESETS}. */
    public final List<String> fields = new ArrayList<>();
    /** The reader's own fields as typed ("Nursing, chess"). */
    public String own = "";
    /** "Analogies from my field". */
    public boolean analogies = true;
    /** "Plain words for old jargon". */
    public boolean plainWords = true;
    /** "Keep the author's key terms". */
    public boolean keepTerms;

    public boolean has(String preset) {
        return fields.contains(preset);
    }

    /** Picks or drops a preset, keeping the presets' order. */
    public void toggle(String preset) {
        if (!fields.remove(preset)) {
            fields.add(preset);
            sort();
        }
    }

    private void sort() {
        final List<String> ordered = new ArrayList<>();
        for (String p : PRESETS) {
            if (fields.contains(p)) {
                ordered.add(p);
            }
        }
        for (String f : fields) {
            if (!ordered.contains(f)) {
                ordered.add(f);
            }
        }
        fields.clear();
        fields.addAll(ordered);
    }

    /** The reader's own fields: split at commas, semicolons and line breaks, trimmed, each once. */
    public List<String> ownFields() {
        final List<String> out = new ArrayList<>();
        for (String part : (own == null ? "" : own).split("[,;\\n\\r]+")) {
            String s = part.replaceAll("\\s+", " ").trim();
            if (s.length() > FIELD_CHARS) {
                s = s.substring(0, FIELD_CHARS).trim();
            }
            if (!s.isEmpty() && !containsIgnoreCase(out, s) && !containsIgnoreCase(fields, s)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Every field of the lens, presets first, {@link #MAX_FIELDS} at most. */
    public List<String> allFields() {
        final List<String> all = new ArrayList<>();
        for (String f : fields) {
            if (f != null && !f.trim().isEmpty() && !containsIgnoreCase(all, f.trim())) {
                all.add(f.trim());
            }
        }
        all.addAll(ownFields());
        return all.size() > MAX_FIELDS ? new ArrayList<>(all.subList(0, MAX_FIELDS)) : all;
    }

    /** A lens exists once it names a field. */
    public boolean isSet() {
        return !allFields().isEmpty();
    }

    /** "Machine learning, Software engineering". */
    public String fieldsText() {
        final StringBuilder out = new StringBuilder();
        for (String f : allFields()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(f);
        }
        return out.toString();
    }

    /** The name on the "Lens:" chip: the first field in short, "+1" for each more ("ML +1"); empty without a field. */
    public String shortName() {
        final List<String> all = allFields();
        if (all.isEmpty()) {
            return "";
        }
        final String first = shortOf(all.get(0));
        final String name = first.isEmpty() ? "" : first.substring(0, 1).toUpperCase(Locale.ROOT) + first.substring(1);
        return all.size() == 1 ? name : name + " +" + (all.size() - 1);
    }

    /** The fields inside a sentence: "ML and software", "ML, software and finance"; empty without a field. */
    public String through() {
        final List<String> all = allFields();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                out.append(i == all.size() - 1 ? " and " : ", ");
            }
            out.append(shortOf(all.get(i)));
        }
        return out.toString();
    }

    /** A preset's short form; one of the reader's own stays as typed. */
    static String shortOf(String field) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equalsIgnoreCase(field)) {
                return SHORT[i];
            }
        }
        return field;
    }

    /**
     * Names what a stored bridge was made with: when the lens or the level changes, the bridge is
     * of another lens and can be mapped again.
     */
    public String signature(Level level) {
        return fieldsText().toLowerCase(Locale.ROOT) + "|" + (analogies ? "a" : "-") + (plainWords ? "p" : "-")
                + (keepTerms ? "k" : "-") + "|" + (level == null ? Level.NEW : level).code;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("fields", new JSONArray(fields));
        o.put("own", own == null ? "" : own);
        o.put("analogies", analogies);
        o.put("plainWords", plainWords);
        o.put("keepTerms", keepTerms);
        return o;
    }

    /** A stored lens; the canvas's defaults (no field yet) when there is none. */
    public static LensProfile fromJson(JSONObject o) {
        final LensProfile p = new LensProfile();
        if (o == null) {
            return p;
        }
        final JSONArray list = o.optJSONArray("fields");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                final String f = list.optString(i, "").trim();
                if (!f.isEmpty() && !containsIgnoreCase(p.fields, f)) {
                    p.fields.add(f);
                }
            }
            p.sort();
        }
        p.own = o.optString("own", "");
        p.analogies = o.optBoolean("analogies", true);
        p.plainWords = o.optBoolean("plainWords", true);
        p.keepTerms = o.optBoolean("keepTerms", false);
        return p;
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) {
            if (x.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
