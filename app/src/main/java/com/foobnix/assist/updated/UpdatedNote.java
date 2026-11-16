package com.foobnix.assist.updated;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One note of "The book, updated": what later knowledge adds to one sentence of a chapter. Written
 * by the model from its own knowledge, so it always carries how settled it is and its source, or
 * no source at all ({@link #source} null: the sheet then says "No source given").
 */
public final class UpdatedNote {

    /** The work a note rests on, as the model named it. Never checked by the app. */
    public static final class Source {
        public String work = "";
        public String author = "";
        /** As written: "1832", "c. 1755"; empty when not given. */
        public String year = "";

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("work", work).put("author", author).put("year", year);
        }

        /** A source needs at least the work's title; anything less is no source. */
        static Source fromJson(JSONObject o) {
            if (o == null) {
                return null;
            }
            final Source s = new Source();
            s.work = UpdatedText.given(o.optString("work", o.optString("title", "")));
            s.author = UpdatedText.given(o.optString("author", o.optString("who", "")));
            s.year = UpdatedText.year(o.opt("year"));
            return s.work.isEmpty() ? null : s;
        }
    }

    public UpdatedKind kind = UpdatedKind.NEWER;
    /** The sentence of the chapter the note attaches to, in the book's own words. */
    public String quote = "";
    public String title = "";
    public String body = "";
    public UpdatedSettled settled = UpdatedSettled.UNKNOWN;
    /** A few words after the rating ("now called the Babbage principle"); may be empty. */
    public String settledNote = "";
    /** Null when the model gave none. */
    public Source source;
    /** The point in the terms of the reader's lens; empty without a lens or when it did not fit. */
    public String terms = "";
    /** 0-based pages of the quote in the layout of the chapter item; -1 when not known. */
    public int page = -1;
    public int lastPage = -1;

    public boolean hasSource() {
        return source != null && !source.work.isEmpty();
    }

    JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("kind", kind.code);
        o.put("quote", quote);
        o.put("title", title);
        o.put("body", body);
        o.put("settled", settled.code);
        o.put("settled_note", settledNote);
        if (hasSource()) {
            o.put("source", source.toJson());
        }
        if (!terms.isEmpty()) {
            o.put("terms", terms);
        }
        o.put("page", page);
        o.put("last", lastPage);
        return o;
    }

    /** A note of an answer or of the store; null when it has no kind, no quote or nothing to say. */
    static UpdatedNote fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final UpdatedNote n = new UpdatedNote();
        final UpdatedKind kind = UpdatedKind.of(o.optString("kind", o.optString("type", "")));
        n.quote = UpdatedText.clean(o.optString("quote", o.optString("sentence", "")));
        n.title = UpdatedText.clean(o.optString("title", ""));
        n.body = UpdatedText.clean(o.optString("body", o.optString("text", o.optString("note", ""))));
        if (kind == null || n.quote.isEmpty() || (n.title.isEmpty() && n.body.isEmpty())) {
            return null;
        }
        n.kind = kind;
        if (n.title.isEmpty()) {
            n.title = UpdatedText.cut(n.body, 60);
        }
        n.settled = UpdatedSettled.of(o.optString("settled", o.optString("how_settled", "")));
        n.settledNote = UpdatedText.given(o.optString("settled_note", ""));
        n.source = Source.fromJson(o.optJSONObject("source"));
        n.terms = UpdatedText.given(o.optString("terms", o.optString("in_your_terms", "")));
        n.page = o.optInt("page", -1);
        n.lastPage = o.optInt("last", n.page);
        return n;
    }
}
