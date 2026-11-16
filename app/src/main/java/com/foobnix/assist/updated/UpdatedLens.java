package com.foobnix.assist.updated;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader's lens as "The book, updated" reads it: the library item {@code lens:profile} of the
 * assist store, written by the Lens feature, with a {@code fields} array of strings ("machine
 * learning", "nursing"). No item, or no fields, means no lens: no "In your terms" anywhere.
 */
public final class UpdatedLens {

    /** The key of the lens profile in the assist store's library file. */
    public static final String KEY = "lens:profile";
    static final int MAX_FIELDS = 5;
    static final int MAX_CHARS = 60;

    private UpdatedLens() {
    }

    /** The fields of a lens profile, cleaned; empty for no profile. */
    public static List<String> fields(JSONObject profile) {
        final List<String> out = new ArrayList<>();
        final JSONArray list = profile == null ? null : profile.optJSONArray("fields");
        if (list == null) {
            return out;
        }
        for (int i = 0; i < list.length() && out.size() < MAX_FIELDS; i++) {
            final Object o = list.opt(i);
            final String s = o instanceof String ? UpdatedText.cut(UpdatedText.clean((String) o), MAX_CHARS) : "";
            if (!s.isEmpty() && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }
}
