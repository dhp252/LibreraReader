package com.foobnix.assist.zoom;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One chapter at the zoom levels of the canvas (ZoomLean, ZoomClaims, ZoomLine): its sections,
 * each with one line and a lean version made of sentences that carry the beginning of the
 * author's passage they condense, the chapter's claims by kind, and the chapter in one line. It is
 * the answer of one AI request ({@link #parse}), tied to the chapter's own text ({@link #place})
 * and kept as the {@code zoom:<chapter key>} item of the book's assist file. No Android here.
 */
public final class ZoomData {

    public static final String PREFIX = "zoom:";
    static final int MAX_SECTIONS = 9;
    static final int MAX_CLAIMS = 12;
    /** Names of parts kept in full, or squeezed, shown at most. */
    static final int MAX_CHANGES = 4;
    /** Characters of a claim's quote kept at most (cut at a word, so it is still found on its page). */
    static final int QUOTE_CHARS = 400;

    /** The store key of a chapter's zoom levels. */
    public static String key(String chapterKey) {
        return PREFIX + chapterKey;
    }

    /** One condensed sentence of the lean version. */
    public static final class Sentence {
        public String text = "";
        /** The beginning of the passage it condenses, as the book has it once placed. */
        public String quote = "";
        /** The passage's last words as the model gave them; used by {@link #place} only. */
        String end = "";
        /** The paragraph the passage starts in, from 1; 0 when it was not found in the chapter. */
        public int para;
        /** How many words the passage has in the book. */
        public int words;
        /** The page of the chapter it starts on, from 0, in the layout it was made in; -1 unknown. */
        public int page = -1;
        ZoomText.Place place;

        public boolean located() {
            return para > 0;
        }
    }

    /** A paragraph of the lean version, or a stretch of the chapter folded away. */
    public static final class Block {
        public boolean fold;
        /** Kept: why it was kept in detail ("best example"), may be empty. Fold: what was left out. */
        public String label = "";
        /** A fold: the beginning of what was left out, as the book has it once placed. */
        public String quote = "";
        public int para;
        /** A fold: how many words of the book it stands for; 0 when not known. */
        public int words;
        /** A fold: the share of the chapter the model said it is, in percent; 0 when not said. */
        public int percent;
        /** A fold: the page of the chapter it starts on, from 0; -1 unknown. */
        public int page = -1;
        public final List<Sentence> sentences = new ArrayList<>();
        ZoomText.Place place;

        public boolean located() {
            return para > 0;
        }
    }

    public static final class Section {
        public String title = "";
        /** The section in one sentence. */
        public String line = "";
        /** The question the section answers; may be empty. */
        public String question = "";
        /** Its first words, as the book has them once placed. */
        public String start = "";
        /** Its first and last paragraph, from 1; 0 when it was not found. */
        public int para;
        public int paraTo;
        /** The page of the chapter it starts on, from 0, in the layout it was made in; -1 unknown. */
        public int page = -1;
        public final List<Block> blocks = new ArrayList<>();

        public boolean located() {
            return para > 0;
        }

        public boolean hasLean() {
            for (Block b : blocks) {
                if (b.fold || !b.sentences.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Claim {
        public ZoomKind kind = ZoomKind.CLAIM;
        public String text = "";
        /** The best example in a few words; may be empty. */
        public String example = "";
        /** The sentence of the chapter that says it, as the book has it once placed; empty when not found. */
        public String quote = "";
        public int para;
        /** The page of the chapter the quote is on, from 0; -1 unknown. */
        public int page = -1;

        public boolean located() {
            return para > 0 && !quote.isEmpty();
        }
    }

    /** The chapter's title as shown when it was made. */
    public String title = "";
    /** The chapter in one sentence. */
    public String line = "";
    public final List<Section> sections = new ArrayList<>();
    public final List<Claim> claims = new ArrayList<>();
    /** Characters of the chapter the model was given. */
    public long chars;
    /** Characters of the whole chapter: more than {@link #chars} when only its first part was sent. */
    public long fullChars;
    /** Pages of the chapter in the layout it was made in: what the {@code page} fields count in. */
    public int pages;
    public String model = "";
    /** The reading goal it was condensed for ({@link ZoomGoal#id}); empty when made without one. */
    public String goal = "";
    /** What the goal kept in full and what it squeezed, as short names; empty without a goal. */
    public final List<String> kept = new ArrayList<>();
    public final List<String> squeezed = new ArrayList<>();

    /** Only the first part of a long chapter was sent. */
    public boolean partial() {
        return fullChars > chars && chars > 0;
    }

    /** The share of the chapter that was sent, in percent (1..100). */
    public int coveredPercent() {
        if (!partial()) {
            return 100;
        }
        return (int) Math.max(1, Math.min(99, Math.round(100.0 * chars / fullChars)));
    }

    public boolean hasLean() {
        for (Section s : sections) {
            if (s.hasLean()) {
                return true;
            }
        }
        return false;
    }

    /** Characters of the lean version's sentences. */
    public long leanChars() {
        long n = 0;
        for (Section s : sections) {
            for (Block b : s.blocks) {
                for (Sentence x : b.sentences) {
                    n += x.text.length() + 1;
                }
            }
        }
        return n;
    }

    /** Characters of the claims rows. */
    public long claimsChars() {
        long n = 0;
        for (Claim c : claims) {
            n += c.text.length() + c.example.length() + 1;
        }
        return n;
    }

    /**
     * The number after a row's label ("WHY · 2"): reasons are counted when the chapter gives more
     * than one; 0 for no number.
     */
    public int number(int claimIndex) {
        if (claimIndex < 0 || claimIndex >= claims.size() || claims.get(claimIndex).kind != ZoomKind.WHY) {
            return 0;
        }
        int total = 0;
        int n = 0;
        for (int i = 0; i < claims.size(); i++) {
            if (claims.get(i).kind == ZoomKind.WHY) {
                total++;
                if (i <= claimIndex) {
                    n++;
                }
            }
        }
        return total > 1 ? n : 0;
    }

    /**
     * The section the reader is in ("You're here"): the last one that starts at or before
     * {@code position}, how far the reader's page is into the chapter's pages (0..1); -1 when no
     * section's place is known.
     */
    public int sectionAt(float position) {
        int here = -1;
        for (int i = 0; i < sections.size(); i++) {
            final float at = at(sections.get(i).page);
            if (at >= 0 && (at <= position + 0.0001f || here < 0)) {
                here = i;
            }
        }
        return here;
    }

    /** How far into the chapter a page of it is, 0..1, whatever the layout; -1 when not known. */
    public float at(int page) {
        return pages > 0 && page >= 0 ? Math.min(1f, (float) page / pages) : -1f;
    }

    /**
     * The page of a chapter of {@code chapterPages} pages (any layout) that is as far into it as
     * {@code page} was in the layout the data was made in; 0 when not known.
     */
    public int pageIn(int page, int chapterPages) {
        final float at = at(page);
        if (at < 0 || chapterPages <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(chapterPages - 1, Math.round(at * chapterPages)));
    }

    // ------------------------------------------------------------------ the answer

    /**
     * The zoom levels in a model's answer: tolerant of code fences and text around the object, of
     * a lean version given beside the sections, of sentences given as plain strings; what is badly
     * formed is left out. Null when the answer holds neither a lean version nor claims.
     */
    public static ZoomData parse(String answer) {
        final JSONObject o = object(answer);
        if (o == null) {
            return null;
        }
        final ZoomData d = new ZoomData();
        d.line = str(o, "line", "one_line", "oneLine", "summary");
        final JSONArray sections = arr(o, "sections", "parts");
        if (sections != null) {
            for (int i = 0; i < sections.length() && d.sections.size() < MAX_SECTIONS; i++) {
                final Section s = section(sections.optJSONObject(i));
                if (s != null) {
                    d.sections.add(s);
                }
            }
        }
        // a lean version beside the sections: each paragraph names its section, else it is the first
        final JSONArray lean = arr(o, "lean", "paragraphs");
        if (lean != null) {
            for (int i = 0; i < lean.length(); i++) {
                final Object item = lean.opt(i);
                final Block b = block(item);
                if (b == null) {
                    continue;
                }
                if (d.sections.isEmpty()) {
                    d.sections.add(new Section());
                }
                int index = item instanceof JSONObject ? ((JSONObject) item).optInt("section", 1) - 1 : 0;
                index = Math.max(0, Math.min(d.sections.size() - 1, index));
                d.sections.get(index).blocks.add(b);
            }
        }
        final JSONArray claims = arr(o, "claims", "argument");
        if (claims != null) {
            for (int i = 0; i < claims.length() && d.claims.size() < MAX_CLAIMS; i++) {
                final Claim c = claim(claims.opt(i));
                if (c != null) {
                    d.claims.add(c);
                }
            }
        }
        if (d.line.isEmpty()) {
            for (Section s : d.sections) {
                if (!s.line.isEmpty()) {
                    d.line = s.line;
                    break;
                }
            }
        }
        names(d.kept, arr(o, "kept", "kept_in_full"));
        names(d.squeezed, arr(o, "squeezed", "folded"));
        return d.hasLean() || !d.claims.isEmpty() ? d : null;
    }

    /** Short names from a list of strings (or of objects with a name), at most {@link #MAX_CHANGES}. */
    private static void names(List<String> into, JSONArray list) {
        for (int i = 0; list != null && i < list.length() && into.size() < MAX_CHANGES; i++) {
            final Object item = list.opt(i);
            final String name = item instanceof String ? ((String) item).trim()
                    : item instanceof JSONObject ? str((JSONObject) item, "name", "title", "text") : "";
            if (!name.isEmpty() && !into.contains(name)) {
                into.add(name);
            }
        }
    }

    private static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int open = answer.indexOf('{');
        final int close = answer.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return null;
        }
        try {
            return new JSONObject(answer.substring(open, close + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    private static Section section(JSONObject o) {
        if (o == null) {
            return null;
        }
        final Section s = new Section();
        s.title = str(o, "title", "name");
        s.line = str(o, "line", "one_line", "summary");
        s.question = str(o, "question", "answers");
        s.start = str(o, "start", "quote", "from");
        final Object lean = first(o, "lean", "paragraphs", "blocks");
        if (lean instanceof JSONArray) {
            final JSONArray list = (JSONArray) lean;
            for (int i = 0; i < list.length(); i++) {
                final Block b = block(list.opt(i));
                if (b != null) {
                    s.blocks.add(b);
                }
            }
        } else if (lean instanceof String) {
            final Block b = block(lean);
            if (b != null) {
                s.blocks.add(b);
            }
        }
        return s.line.isEmpty() && s.title.isEmpty() && s.blocks.isEmpty() ? null : s;
    }

    /** A paragraph of sentences, a fold, a bare sentence, or a paragraph as one string. */
    private static Block block(Object item) {
        final Block b = new Block();
        if (item instanceof String) {
            add(b, sentence(item));
        } else if (item instanceof JSONArray) {
            sentences(b, (JSONArray) item);
        } else if (item instanceof JSONObject) {
            final JSONObject o = (JSONObject) item;
            final String folded = flag(str(o, "folded", "fold"));
            if (!folded.isEmpty()) {
                b.fold = true;
                b.label = folded;
                b.quote = str(o, "quote", "start", "from");
                b.percent = Math.max(0, Math.min(100, o.optInt("percent", 0)));
                return b;
            }
            b.label = flag(str(o, "kept", "label"));
            final JSONArray list = arr(o, "sentences", "lean");
            if (list != null) {
                sentences(b, list);
            } else {
                add(b, sentence(o));
            }
        }
        return b.sentences.isEmpty() ? null : b;
    }

    private static void sentences(Block b, JSONArray list) {
        for (int i = 0; i < list.length(); i++) {
            add(b, sentence(list.opt(i)));
        }
    }

    private static void add(Block b, Sentence s) {
        if (s != null) {
            b.sentences.add(s);
        }
    }

    private static Sentence sentence(Object item) {
        final Sentence s = new Sentence();
        if (item instanceof String) {
            s.text = ((String) item).trim();
        } else if (item instanceof JSONObject) {
            final JSONObject o = (JSONObject) item;
            s.text = str(o, "text", "sentence", "lean");
            s.quote = str(o, "quote", "from", "start");
            s.end = str(o, "end", "to");
        }
        return s.text.isEmpty() ? null : s;
    }

    private static Claim claim(Object item) {
        final Claim c = new Claim();
        if (item instanceof String) {
            c.text = ((String) item).trim();
        } else if (item instanceof JSONObject) {
            final JSONObject o = (JSONObject) item;
            c.kind = ZoomKind.of(str(o, "kind", "type", "role"));
            c.text = str(o, "text", "claim", "line");
            c.example = str(o, "example", "eg");
            c.quote = str(o, "quote");
        }
        return c.text.isEmpty() ? null : c;
    }

    /** A model's "kept": true or "folded": false is no label. */
    private static String flag(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s) ? "" : s;
    }

    /** The first of these names that holds a text or a number, trimmed; empty when none (also for a JSON null). */
    private static String str(JSONObject o, String... names) {
        final Object v = first(o, names);
        if (v instanceof String) {
            final String s = ((String) v).trim();
            return "null".equals(s) ? "" : s;
        }
        return v instanceof Number || v instanceof Boolean ? String.valueOf(v) : "";
    }

    private static JSONArray arr(JSONObject o, String... names) {
        for (String name : names) {
            final JSONArray a = o.optJSONArray(name);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    private static Object first(JSONObject o, String... names) {
        for (String name : names) {
            if (o.has(name) && !o.isNull(name)) {
                return o.opt(name);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the chapter's own words

    /**
     * Ties the answer to the chapter: every quote is looked for in the text and replaced by the
     * book's own words (a sentence keeps the beginning of its passage and how many words it runs),
     * with its paragraph number; a fold learns how many words it stands for (up to the next thing
     * that was found); a section learns its paragraphs and the page it starts on. What is not
     * found keeps paragraph 0 and cannot be jumped to.
     *
     * @param chapterPages pages of the whole chapter in the layout the text was read in
     */
    public void place(ZoomText text, int chapterPages) {
        pages = Math.max(chapterPages, text.pageCount());
        final List<ZoomText.Place> anchors = new ArrayList<>();
        final List<Block> folds = new ArrayList<>();
        final List<Integer> foldAnchor = new ArrayList<>();
        for (Section s : sections) {
            for (Block b : s.blocks) {
                if (b.fold) {
                    b.place = text.locate(b.quote);
                    b.para = b.place == null ? 0 : b.place.para;
                    b.quote = b.place == null ? "" : text.head(b.place);
                    b.page = b.place == null ? -1 : b.place.page;
                    b.words = 0;
                    if (b.place != null) {
                        folds.add(b);
                        foldAnchor.add(anchors.size());
                        anchors.add(b.place);
                    }
                    continue;
                }
                for (Sentence x : b.sentences) {
                    x.place = text.locateSpan(x.quote, x.end);
                    x.end = "";
                    if (x.place == null) {
                        x.para = 0;
                        x.words = 0;
                        x.page = -1;
                        x.quote = "";
                    } else {
                        x.para = x.place.para;
                        x.page = x.place.page;
                        x.words = x.place.words();
                        x.quote = text.head(x.place);
                        anchors.add(x.place);
                    }
                }
            }
        }
        // a fold runs to the next thing found after it, the last one to the end of the text
        for (int i = 0; i < folds.size(); i++) {
            final Block b = folds.get(i);
            int end = -1;
            for (int k = foldAnchor.get(i) + 1; k < anchors.size(); k++) {
                if (anchors.get(k).first > b.place.first) {
                    end = anchors.get(k).first;
                    break;
                }
            }
            if (end < 0 && foldAnchor.get(i) == anchors.size() - 1) {
                end = text.wordCount();
            }
            b.words = end > b.place.first ? end - b.place.first : 0;
        }

        // sections come in order: one found at or before the section before it was found wrongly
        int before = -1;
        for (Section s : sections) {
            ZoomText.Place p = text.locate(s.start);
            if (p == null || p.first <= before) {
                p = firstPlace(s, before);
            }
            if (p == null) {
                s.para = 0;
                s.paraTo = 0;
                s.page = -1;
                s.start = "";
                continue;
            }
            s.para = p.para;
            s.start = text.head(p);
            s.page = p.page;
            before = p.first;
        }
        for (int i = 0; i < sections.size(); i++) {
            final Section s = sections.get(i);
            if (!s.located()) {
                continue;
            }
            int to = text.paragraphCount();
            for (int k = i + 1; k < sections.size(); k++) {
                if (sections.get(k).located()) {
                    to = Math.max(s.para, sections.get(k).para - 1);
                    break;
                }
            }
            s.paraTo = Math.max(s.para, to);
        }

        for (Claim c : claims) {
            final ZoomText.Place p = text.locate(c.quote);
            c.para = p == null ? 0 : p.para;
            c.page = p == null ? -1 : p.page;
            c.quote = p == null ? "" : cut(text.text(p), QUOTE_CHARS);
        }
    }

    /** The first thing of a section found in the text after word {@code after}. */
    private static ZoomText.Place firstPlace(Section s, int after) {
        for (Block b : s.blocks) {
            if (b.fold) {
                if (b.place != null && b.place.first > after) {
                    return b.place;
                }
                continue;
            }
            for (Sentence x : b.sentences) {
                if (x.place != null && x.place.first > after) {
                    return x.place;
                }
            }
        }
        return null;
    }

    /** At most {@code max} characters, cut at a word. */
    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        final int space = s.lastIndexOf(' ', max);
        return s.substring(0, space > 0 ? space : max);
    }

    // ------------------------------------------------------------------ the stored item

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("line", line);
        o.put("chars", chars);
        o.put("fullChars", fullChars);
        o.put("pages", pages);
        o.put("model", model);
        if (!goal.isEmpty()) {
            o.put("goal", goal);
        }
        if (!kept.isEmpty()) {
            o.put("kept", new JSONArray(kept));
        }
        if (!squeezed.isEmpty()) {
            o.put("squeezed", new JSONArray(squeezed));
        }
        final JSONArray list = new JSONArray();
        for (Section s : sections) {
            final JSONObject so = new JSONObject();
            so.put("title", s.title);
            so.put("line", s.line);
            so.put("question", s.question);
            so.put("start", s.start);
            so.put("para", s.para);
            so.put("paraTo", s.paraTo);
            so.put("page", s.page);
            final JSONArray blocks = new JSONArray();
            for (Block b : s.blocks) {
                final JSONObject bo = new JSONObject();
                bo.put("label", b.label);
                if (b.fold) {
                    bo.put("fold", true);
                    bo.put("quote", b.quote);
                    bo.put("para", b.para);
                    bo.put("words", b.words);
                    bo.put("percent", b.percent);
                    bo.put("page", b.page);
                } else {
                    final JSONArray sentences = new JSONArray();
                    for (Sentence x : b.sentences) {
                        final JSONObject xo = new JSONObject();
                        xo.put("text", x.text);
                        xo.put("quote", x.quote);
                        xo.put("para", x.para);
                        xo.put("words", x.words);
                        xo.put("page", x.page);
                        sentences.put(xo);
                    }
                    bo.put("sentences", sentences);
                }
                blocks.put(bo);
            }
            so.put("blocks", blocks);
            list.put(so);
        }
        o.put("sections", list);
        final JSONArray rows = new JSONArray();
        for (Claim c : claims) {
            final JSONObject co = new JSONObject();
            co.put("kind", c.kind.id);
            co.put("text", c.text);
            co.put("example", c.example);
            co.put("quote", c.quote);
            co.put("para", c.para);
            co.put("page", c.page);
            rows.put(co);
        }
        o.put("claims", rows);
        return o;
    }

    /** The stored item; null when there is none or it holds nothing to show. */
    public static ZoomData fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        final ZoomData d = new ZoomData();
        d.title = o.optString("title", "");
        d.line = o.optString("line", "");
        d.chars = o.optLong("chars");
        d.fullChars = o.optLong("fullChars");
        d.pages = o.optInt("pages");
        d.model = o.optString("model", "");
        d.goal = o.optString("goal", "");
        names(d.kept, o.optJSONArray("kept"));
        names(d.squeezed, o.optJSONArray("squeezed"));
        final JSONArray list = o.optJSONArray("sections");
        for (int i = 0; list != null && i < list.length(); i++) {
            final JSONObject so = list.optJSONObject(i);
            if (so == null) {
                continue;
            }
            final Section s = new Section();
            s.title = so.optString("title", "");
            s.line = so.optString("line", "");
            s.question = so.optString("question", "");
            s.start = so.optString("start", "");
            s.para = so.optInt("para");
            s.paraTo = so.optInt("paraTo");
            s.page = so.optInt("page", -1);
            final JSONArray blocks = so.optJSONArray("blocks");
            for (int k = 0; blocks != null && k < blocks.length(); k++) {
                final JSONObject bo = blocks.optJSONObject(k);
                if (bo == null) {
                    continue;
                }
                final Block b = new Block();
                b.label = bo.optString("label", "");
                b.fold = bo.optBoolean("fold");
                if (b.fold) {
                    b.quote = bo.optString("quote", "");
                    b.para = bo.optInt("para");
                    b.words = bo.optInt("words");
                    b.percent = bo.optInt("percent");
                    b.page = bo.optInt("page", -1);
                }
                final JSONArray sentences = bo.optJSONArray("sentences");
                for (int m = 0; sentences != null && m < sentences.length(); m++) {
                    final JSONObject xo = sentences.optJSONObject(m);
                    if (xo == null) {
                        continue;
                    }
                    final Sentence x = new Sentence();
                    x.text = xo.optString("text", "");
                    x.quote = xo.optString("quote", "");
                    x.para = xo.optInt("para");
                    x.words = xo.optInt("words");
                    x.page = xo.optInt("page", -1);
                    if (!x.text.isEmpty()) {
                        b.sentences.add(x);
                    }
                }
                if (b.fold || !b.sentences.isEmpty()) {
                    s.blocks.add(b);
                }
            }
            d.sections.add(s);
        }
        final JSONArray rows = o.optJSONArray("claims");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            final JSONObject co = rows.optJSONObject(i);
            if (co == null) {
                continue;
            }
            final Claim c = new Claim();
            c.kind = ZoomKind.of(co.optString("kind", ""));
            c.text = co.optString("text", "");
            c.example = co.optString("example", "");
            c.quote = co.optString("quote", "");
            c.para = co.optInt("para");
            c.page = co.optInt("page", -1);
            if (!c.text.isEmpty()) {
                d.claims.add(c);
            }
        }
        return d.hasLean() || !d.claims.isEmpty() ? d : null;
    }
}
