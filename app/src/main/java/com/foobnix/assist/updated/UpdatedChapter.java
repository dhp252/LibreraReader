package com.foobnix.assist.updated;

import com.foobnix.ai.QuoteMatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * What "The book, updated" keeps for one chapter: its notes (each attached to a sentence found in
 * the chapter) and the strongest objection. Stored as the assist item {@code updated:<chapter key>}
 * of the book; parsed from the model's one answer by {@link #parse} and {@link #place}.
 */
public final class UpdatedChapter {

    public static final String PREFIX = "updated:";
    /** Notes kept per chapter at most. */
    public static final int MAX_NOTES = 6;

    public String title = "";
    public List<UpdatedNote> notes = new ArrayList<>();
    /** Null when the model gave none. */
    public UpdatedObjection objection;
    /** The layout {@link UpdatedNote#page} is counted in ({@link #layoutKey}). */
    public String layout = "";
    /** When the model was asked. */
    public long at;
    public String model = "";
    /** The year the book first came out, as the model knows it; 0 when it did not say. */
    public int firstPublished;
    /** The book's author as the library has it, for the labels; may be empty. */
    public String author = "";

    /** The key of a chapter's item in the book's assist file. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    /** Names the layout pages are counted in: a fixed layout's pages never move; a reflowing book's move with size and font. */
    public static String layoutKey(boolean fixed, int width, int height, int fontSize) {
        return fixed ? "fixed" : width + "x" + height + "@" + fontSize;
    }

    public boolean isEmpty() {
        return notes.isEmpty() && objection == null;
    }

    // ------------------------------------------------------------------ filters

    /** The notes of one kind, in the chapter's order; all of them for null. */
    public List<UpdatedNote> notes(UpdatedKind kind) {
        if (kind == null) {
            return new ArrayList<>(notes);
        }
        final List<UpdatedNote> out = new ArrayList<>();
        for (UpdatedNote n : notes) {
            if (n.kind == kind) {
                out.add(n);
            }
        }
        return out;
    }

    public int count(UpdatedKind kind) {
        return notes(kind).size();
    }

    /**
     * The filter after a tap on a kind's chip: that kind, or all again (null) when it was the
     * chosen one; a kind without notes changes nothing.
     */
    public UpdatedKind toggle(UpdatedKind chosen, UpdatedKind tapped) {
        if (tapped == null || count(tapped) == 0) {
            return chosen;
        }
        return tapped == chosen ? null : tapped;
    }

    /** Whether the notes' pages have to be found again for this layout. */
    public boolean needsPages(String layoutNow) {
        return !notes.isEmpty() && layoutNow != null && !layoutNow.equals(layout);
    }

    // ------------------------------------------------------------------ the store

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        final JSONArray list = new JSONArray();
        for (UpdatedNote n : notes) {
            list.put(n.toJson());
        }
        o.put("notes", list);
        if (objection != null) {
            o.put("objection", objection.toJson());
        }
        o.put("layout", layout);
        o.put("at", at);
        o.put("model", model);
        o.put("first_published", firstPublished);
        o.put("author", author);
        return o;
    }

    /** A stored chapter; null for no item. */
    public static UpdatedChapter fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final UpdatedChapter c = read(o);
        c.title = o.optString("title", "");
        c.layout = o.optString("layout", "");
        c.at = o.optLong("at", 0);
        c.model = o.optString("model", "");
        c.author = o.optString("author", "");
        return c;
    }

    // ------------------------------------------------------------------ the answer

    /**
     * The notes and the objection of a model's answer; tolerant of code fences, of text around the
     * JSON, of a bare list of notes and of other names for the fields. Badly formed notes are left
     * out. Null when no JSON can be read at all.
     */
    public static UpdatedChapter parse(String answer) {
        if (answer == null) {
            return null;
        }
        final int brace = answer.indexOf('{');
        final int end = answer.lastIndexOf('}');
        final int open = answer.indexOf('[');
        final int close = answer.lastIndexOf(']');
        // a bare list of notes: the list starts before any object does
        if (open >= 0 && close > open && (brace < 0 || open < brace)) {
            try {
                return read(new JSONObject().put("notes", new JSONArray(answer.substring(open, close + 1))));
            } catch (JSONException e) {
                // an object after all
            }
        }
        if (brace >= 0 && end > brace) {
            try {
                return read(new JSONObject(answer.substring(brace, end + 1)));
            } catch (JSONException e) {
                return null;
            }
        }
        return null;
    }

    private static UpdatedChapter read(JSONObject o) {
        final UpdatedChapter c = new UpdatedChapter();
        JSONArray list = o.optJSONArray("notes");
        if (list == null) {
            list = o.optJSONArray("annotations");
        }
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                final UpdatedNote n = UpdatedNote.fromJson(list.optJSONObject(i));
                if (n != null) {
                    c.notes.add(n);
                }
            }
        }
        JSONObject objection = o.optJSONObject("objection");
        if (objection == null) {
            objection = o.optJSONObject("strongest_objection");
        }
        c.objection = UpdatedObjection.fromJson(objection);
        c.firstPublished = publishedYear(o.opt("first_published"));
        return c;
    }

    /** A plausible year of first publication, else 0. */
    static int publishedYear(Object y) {
        long n = 0;
        if (y instanceof Number) {
            n = ((Number) y).longValue();
        } else if (y instanceof String && ((String) y).trim().matches("-?\\d{1,4}")) {
            n = Long.parseLong(((String) y).trim());
        }
        return n != 0 && n >= -3000 && n <= Calendar.getInstance().get(Calendar.YEAR) ? (int) n : 0;
    }

    /**
     * Keeps the notes whose sentence is in the chapter (at most {@link #MAX_NOTES}, one per
     * sentence), each with the sentence as the book has it and its pages, in the chapter's order;
     * the rest are left out. The twist's words are looked for too, and flagged when found.
     *
     * @param words    the chapter's words in reading order
     * @param wordPage the 0-based page of each word
     */
    public void place(List<String> words, List<Integer> wordPage) {
        final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
        final List<UpdatedNote> kept = new ArrayList<>();
        final List<Integer> starts = new ArrayList<>();
        final List<String> seen = new ArrayList<>();
        for (UpdatedNote n : notes) {
            if (kept.size() >= MAX_NOTES) {
                break;
            }
            final int[] range = QuoteMatcher.locate(all, n.quote);
            if (range == null) {
                continue;
            }
            final String text = QuoteMatcher.join(words.subList(range[0], range[1] + 1));
            final String key = QuoteMatcher.key(text);
            if (key == null || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            n.quote = text;
            n.page = wordPage.get(range[0]);
            n.lastPage = wordPage.get(range[1]);
            // in the chapter's order
            int at = kept.size();
            while (at > 0 && starts.get(at - 1) > range[0]) {
                at--;
            }
            kept.add(at, n);
            starts.add(at, range[0]);
        }
        notes = kept;
        if (objection != null) {
            objection.twistInChapter = false;
            objection.twistPage = -1;
            final int[] range = objection.twistQuote.isEmpty() ? null : QuoteMatcher.locate(all, objection.twistQuote);
            if (range != null) {
                objection.twistQuote = QuoteMatcher.join(words.subList(range[0], range[1] + 1));
                objection.twistInChapter = true;
                objection.twistPage = wordPage.get(range[0]);
            }
        }
    }

    /** Whether a quote is on a page (whole, or running over its top or bottom edge). */
    public static boolean onPage(List<String> pageWords, String quote) {
        final String key = QuoteMatcher.key(quote);
        return key != null && !QuoteMatcher.onPage(new QuoteMatcher.Words(pageWords), Collections.singletonList(key)).isEmpty();
    }
}
