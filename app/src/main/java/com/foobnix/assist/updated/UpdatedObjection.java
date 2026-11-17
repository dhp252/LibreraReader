package com.foobnix.assist.updated;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The strongest objection to a chapter's main claim (canvas UpdatedObjection): the claim, the
 * objection and how strong it is, the twist (where the author answers or concedes it, with the
 * book's words when the model gave them), who pushed it further, and where the two really
 * disagree. All from the model's own knowledge.
 */
public final class UpdatedObjection {

    public enum Strength {
        STRONG("strong", 3), MODERATE("moderate", 2), WEAK("weak", 1), UNKNOWN("", 0);

        public final String code;
        public final int bars;

        Strength(String code, int bars) {
            this.code = code;
            this.bars = bars;
        }

        public static Strength of(String s) {
            final String k = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            if (k.startsWith("strong") || k.startsWith("serious")) {
                return STRONG;
            }
            if (k.startsWith("moderate") || k.startsWith("medium") || k.startsWith("fair") || k.startsWith("partial")) {
                return MODERATE;
            }
            if (k.startsWith("weak") || k.startsWith("minor")) {
                return WEAK;
            }
            return UNKNOWN;
        }
    }

    public String claim = "";
    public String objection = "";
    public Strength strength = Strength.UNKNOWN;

    /** A few words: "Smith agreed, in Book V". Empty when there is no twist. */
    public String twistHead = "";
    /** The book's words the twist rests on, as the model gave them; empty when none. */
    public String twistQuote = "";
    public String twistNote = "";
    /** The twist's words were found in the chapter that was sent (then {@link #twistPage} is their page). */
    public boolean twistInChapter;
    public int twistPage = -1;

    public String pushedWho = "";
    public String pushedWork = "";
    public String pushedYear = "";
    public String pushedText = "";

    /** Where they really disagree: the author's side and the other side, or one plain text. */
    public String authorSide = "";
    public String otherSide = "";
    public String disagree = "";

    public boolean hasTwist() {
        return !twistHead.isEmpty() || !twistQuote.isEmpty() || !twistNote.isEmpty();
    }

    public boolean hasPushed() {
        return !pushedText.isEmpty();
    }

    public boolean hasDisagree() {
        return !disagree.isEmpty() || (!authorSide.isEmpty() && !otherSide.isEmpty());
    }

    JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("claim", claim);
        o.put("objection", objection);
        o.put("strength", strength.code);
        if (hasTwist()) {
            o.put("twist", new JSONObject().put("head", twistHead).put("quote", twistQuote).put("note", twistNote)
                    .put("in_chapter", twistInChapter).put("page", twistPage));
        }
        if (hasPushed()) {
            o.put("pushed", new JSONObject().put("who", pushedWho).put("work", pushedWork).put("year", pushedYear)
                    .put("text", pushedText));
        }
        if (hasDisagree()) {
            o.put("disagree", new JSONObject().put("author", authorSide).put("other", otherSide).put("text", disagree));
        }
        return o;
    }

    /** The objection of an answer or of the store; null without a claim and an objection. */
    static UpdatedObjection fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final UpdatedObjection x = new UpdatedObjection();
        x.claim = UpdatedText.clean(o.optString("claim", ""));
        x.objection = UpdatedText.clean(o.optString("objection", ""));
        if (x.claim.isEmpty() || x.objection.isEmpty()) {
            return null;
        }
        x.strength = Strength.of(o.optString("strength", ""));

        final Object twist = o.opt("twist");
        if (twist instanceof JSONObject) {
            final JSONObject t = (JSONObject) twist;
            x.twistHead = UpdatedText.given(t.optString("head", t.optString("title", "")));
            x.twistQuote = UpdatedText.unquote(UpdatedText.given(t.optString("quote", "")));
            x.twistNote = UpdatedText.given(t.optString("note", t.optString("text", "")));
            x.twistInChapter = t.optBoolean("in_chapter", false);
            x.twistPage = t.optInt("page", -1);
        } else if (twist instanceof String) {
            x.twistNote = UpdatedText.given((String) twist);
        }

        final JSONObject p = o.optJSONObject("pushed");
        if (p != null) {
            x.pushedText = UpdatedText.given(p.optString("text", ""));
            if (!x.pushedText.isEmpty()) {
                x.pushedWho = UpdatedText.given(p.optString("who", p.optString("author", "")));
                x.pushedWork = UpdatedText.given(p.optString("work", ""));
                x.pushedYear = UpdatedText.year(p.opt("year"));
            }
        }

        final Object d = o.opt("disagree");
        if (d instanceof JSONObject) {
            final JSONObject dj = (JSONObject) d;
            x.authorSide = UpdatedText.given(dj.optString("author", ""));
            x.otherSide = UpdatedText.given(dj.optString("other", ""));
            x.disagree = UpdatedText.given(dj.optString("text", ""));
        } else if (d instanceof String) {
            x.disagree = UpdatedText.given((String) d);
        }
        return x;
    }
}
