package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;
import com.foobnix.study.StudyBook;

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
 * What the skeleton of a book holds, by chapter and part keys (the same on every device and in
 * every layout): the thesis in one line and in one paragraph, one line per part and per chapter,
 * which part (or chapter) holds the conclusion, which are optional background, and each chapter's
 * length. Kept in {@code AssistStore} under {@link #KEY}; one made from a study preview is never
 * stored (the study file is only read).
 */
public final class SkeletonData {

    /** The AssistStore item of a book's skeleton. */
    public static final String KEY = "skeleton:book";

    public String line = "";
    public String paragraph = "";
    /** "Smith saves his conclusion for Book IV." */
    public String note = "";
    /** Key of the part (or, in a book without parts, the chapter) holding the conclusion; empty = none. */
    public String conclusion = "";
    public final Map<String, String> partLines = new LinkedHashMap<>();
    /** Keys of the parts (or chapters) that are optional background. */
    public final Set<String> optional = new LinkedHashSet<>();
    public final Map<String, String> chapterLines = new LinkedHashMap<>();
    /** Characters of text per chapter key. */
    public final Map<String, Long> chars = new LinkedHashMap<>();
    public String model = "";
    public long made;
    /** Taken from the book's study preview (thesis and chapter lines only). */
    public boolean fromStudy;

    public boolean isEmpty() {
        return line.isEmpty() && paragraph.isEmpty() && chapterLines.isEmpty();
    }

    /** The model's answer, its numbers turned into keys. */
    public static SkeletonData fromAnswer(SkeletonPrompts.Answer a, List<SkeletonParts.Part> parts,
                                          List<BookChapters.Range> chapters, int[] pageChars, String model, long now) {
        final SkeletonData d = new SkeletonData();
        d.line = a.line.isEmpty() ? SkeletonText.firstSentence(a.paragraph) : a.line;
        d.paragraph = a.paragraph.isEmpty() ? a.line : a.paragraph;
        d.note = a.conclusionNote;
        final boolean grouped = SkeletonParts.hasGroups(parts);
        if (grouped) {
            if (a.conclusion >= 0 && a.conclusion < parts.size()) {
                d.conclusion = parts.get(a.conclusion).range.key;
            }
            for (Map.Entry<Integer, String> e : a.partLines.entrySet()) {
                if (e.getKey() < parts.size()) {
                    d.partLines.put(parts.get(e.getKey()).range.key, e.getValue());
                }
            }
            for (Integer n : a.optionalParts) {
                if (n < parts.size()) {
                    d.optional.add(parts.get(n).range.key);
                }
            }
        } else {
            if (a.conclusion >= 0 && a.conclusion < chapters.size()) {
                d.conclusion = chapters.get(a.conclusion).key;
            }
            for (Integer n : a.optionalChapters) {
                if (n < chapters.size()) {
                    d.optional.add(chapters.get(n).key);
                }
            }
        }
        if (d.conclusion.isEmpty()) {
            d.note = "";
        }
        // the conclusion is never optional
        d.optional.remove(d.conclusion);
        for (Map.Entry<Integer, String> e : a.chapterLines.entrySet()) {
            if (e.getKey() < chapters.size()) {
                d.chapterLines.put(chapters.get(e.getKey()).key, e.getValue());
            }
        }
        for (BookChapters.Range r : chapters) {
            d.chars.put(r.key, pageChars == null ? 0 : BookChapters.length(pageChars, r.from, r.to));
        }
        d.model = model == null ? "" : model;
        d.made = now;
        return d;
    }

    /** The thesis and chapter lines of a study preview (F-009), read only. */
    public static SkeletonData fromStudy(StudyBook b) {
        final SkeletonData d = new SkeletonData();
        if (b == null || !b.hasPreview()) {
            return d;
        }
        final String thesis = b.thesis == null ? "" : b.thesis.trim();
        d.line = SkeletonText.firstSentence(thesis);
        d.paragraph = thesis;
        for (StudyBook.Chapter c : b.chapters.values()) {
            if (c.line != null && !c.line.trim().isEmpty()) {
                d.chapterLines.put(c.key, c.line.trim());
            }
            if (c.chars > 0) {
                d.chars.put(c.key, c.chars);
            }
        }
        d.model = b.model == null ? "" : b.model;
        d.made = b.previewed;
        d.fromStudy = true;
        return d;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject()
                .put("line", line)
                .put("paragraph", paragraph)
                .put("note", note)
                .put("conclusion", conclusion)
                .put("parts", strings(partLines))
                .put("optional", new JSONArray(new ArrayList<>(optional)))
                .put("chapters", strings(chapterLines))
                .put("model", model)
                .put("made", made);
        final JSONObject c = new JSONObject();
        for (Map.Entry<String, Long> e : chars.entrySet()) {
            c.put(e.getKey(), e.getValue());
        }
        o.put("chars", c);
        return o;
    }

    /** A stored skeleton; empty when {@code o} is null. */
    public static SkeletonData fromJson(JSONObject o) {
        final SkeletonData d = new SkeletonData();
        if (o == null) {
            return d;
        }
        d.line = o.optString("line", "");
        d.paragraph = o.optString("paragraph", "");
        d.note = o.optString("note", "");
        d.conclusion = o.optString("conclusion", "");
        strings(o.optJSONObject("parts"), d.partLines);
        final JSONArray optional = o.optJSONArray("optional");
        for (int i = 0; optional != null && i < optional.length(); i++) {
            d.optional.add(optional.optString(i, ""));
        }
        strings(o.optJSONObject("chapters"), d.chapterLines);
        final JSONObject c = o.optJSONObject("chars");
        if (c != null) {
            final Iterator<String> keys = c.keys();
            while (keys.hasNext()) {
                final String k = keys.next();
                d.chars.put(k, c.optLong(k, 0));
            }
        }
        d.model = o.optString("model", "");
        d.made = o.optLong("made", 0);
        return d;
    }

    private static JSONObject strings(Map<String, String> map) throws JSONException {
        final JSONObject o = new JSONObject();
        for (Map.Entry<String, String> e : map.entrySet()) {
            o.put(e.getKey(), e.getValue());
        }
        return o;
    }

    private static void strings(JSONObject o, Map<String, String> into) {
        if (o == null) {
            return;
        }
        final Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            final String k = keys.next();
            into.put(k, o.optString(k, ""));
        }
    }
}
