package com.foobnix.study;

import com.foobnix.ai.AiCategory;
import com.foobnix.annotation.ColorNames;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The outline of a studied book: the book, its chapters in order, each chapter's branch (claims,
 * then their evidence, caveats, examples...) and the reader's notes. One tree for the Outline
 * view, the mind map ({@link #toMarkmap}) and the Markdown export ({@link #toMarkdown}).
 */
public final class Outline {

    public static final String BOOK = "book";
    public static final String CHAPTER = "chapter";
    public static final String NOTE = "note";
    /** A chapter's one line, while it has no branch. */
    public static final String LINE = "line";

    private Outline() {
    }

    public static final class Item {
        /** "b", "c3", "c3.1.2": what a tap on the map names. */
        public final String id;
        public final String text;
        /** {@link #BOOK}, {@link #CHAPTER}, {@link #LINE}, {@link #NOTE} or an AI category code. */
        public final String category;
        /** Words of the book it comes from, or empty. */
        public final String quote;
        /** 0-based page hint, -1 when none. */
        public final int page;
        /** The chapter it is in (empty for the book). */
        public final String chapter;
        /** A skipped chapter: drawn faint. */
        public final boolean dim;
        /** Under the text: the thesis of the book, a label of a chapter. */
        public String detail = "";
        public final List<Item> kids = new ArrayList<>();

        Item(String id, String text, String category, String quote, int page, String chapter, boolean dim) {
            this.id = id;
            this.text = text;
            this.category = category;
            this.quote = quote == null ? "" : quote;
            this.page = page;
            this.chapter = chapter;
            this.dim = dim;
        }

        public int count() {
            int n = 1;
            for (Item k : kids) {
                n += k.count();
            }
            return n;
        }
    }

    /**
     * @param notes the reader's notes as nodes, by chapter key
     */
    public static Item build(String title, StudyBook book, Map<String, List<StudyBook.Node>> notes) {
        final Item root = new Item("b", title == null ? "" : title, BOOK, "", -1, "", false);
        root.detail = book.thesis == null ? "" : book.thesis;
        final List<StudyBook.Chapter> chapters = new ArrayList<>(book.chapters.values());
        Collections.sort(chapters, (a, b) -> Integer.compare(a.index, b.index));
        int n = 0;
        for (StudyBook.Chapter c : chapters) {
            n++;
            final String id = "c" + n;
            final boolean skipped = StudyBook.SKIP.equals(c.label) && c.branch.isEmpty();
            final Item chapter = new Item(id, n + ". " + (c.title == null || c.title.trim().isEmpty() ? "…" : c.title.trim()),
                    CHAPTER, "", c.from, c.key, skipped);
            chapter.detail = c.label;
            if (c.branch.isEmpty() && c.line != null && !c.line.trim().isEmpty()) {
                chapter.kids.add(new Item(id + ".0", c.line.trim(), LINE, "", -1, c.key, skipped));
            }
            add(chapter, c.branch, c.key);
            final List<StudyBook.Node> own = notes == null ? null : notes.get(c.key);
            if (own != null) {
                for (StudyBook.Node note : own) {
                    chapter.kids.add(new Item(id + "." + (chapter.kids.size() + 1), note.text, NOTE, note.quote,
                            note.page, c.key, false));
                }
            }
            root.kids.add(chapter);
        }
        return root;
    }

    private static void add(Item parent, List<StudyBook.Node> nodes, String chapter) {
        for (StudyBook.Node node : nodes) {
            final Item item = new Item(parent.id + "." + (parent.kids.size() + 1), node.text, node.category,
                    node.quote, node.page, chapter, false);
            add(item, node.kids, chapter);
            parent.kids.add(item);
        }
    }

    /** The item with this id, or null. */
    public static Item find(Item root, String id) {
        if (root == null || id == null) {
            return null;
        }
        if (id.equals(root.id)) {
            return root;
        }
        for (Item k : root.kids) {
            final Item found = find(k, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether the reader has anything in the outline beyond the preview's lines. */
    public static boolean hasBranches(StudyBook book) {
        for (StudyBook.Chapter c : book.chapters.values()) {
            if (!c.branch.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Color of an item: chapters by their label, ideas by their category, notes the reader's yellow. */
    public static int color(Item item) {
        if (CHAPTER.equals(item.category)) {
            return StudyBook.labelColor(item.detail);
        }
        if (NOTE.equals(item.category)) {
            return AiCategory.READER_YELLOW;
        }
        final AiCategory c = AiCategory.of(item.category);
        return c != null ? c.color : 0xff888888;
    }

    /** "🟥 Main idea", "🟨 Note"; empty for chapters and lines. */
    public static String label(Item item) {
        if (NOTE.equals(item.category)) {
            return ColorNames.emoji(ColorNames.hex(AiCategory.READER_YELLOW)) + " Note";
        }
        final AiCategory c = AiCategory.of(item.category);
        return c == null ? "" : ColorNames.emoji(ColorNames.hex(c.color)) + " " + c.promptName;
    }

    // ------------------------------------------------------------------ Markdown

    /** The outline as a Markdown section body (no heading): thesis, then a nested list. */
    public static String toMarkdown(Item root) {
        final StringBuilder md = new StringBuilder();
        if (!root.detail.isEmpty()) {
            md.append("**Thesis:** ").append(oneLine(root.detail)).append("\n\n");
        }
        for (Item chapter : root.kids) {
            md.append("- **").append(oneLine(chapter.text)).append("**");
            if (chapter.page >= 0) {
                md.append(" · p. ").append(chapter.page + 1);
            }
            if (chapter.dim) {
                md.append(" · skipped");
            }
            md.append('\n');
            for (Item k : chapter.kids) {
                markdown(md, k, 1);
            }
        }
        return md.toString();
    }

    private static void markdown(StringBuilder md, Item item, int depth) {
        for (int i = 0; i < depth; i++) {
            md.append("  ");
        }
        md.append("- ");
        if (LINE.equals(item.category)) {
            md.append('*').append(oneLine(item.text)).append('*');
        } else {
            final String label = label(item);
            if (!label.isEmpty()) {
                md.append(label).append(": ");
            }
            md.append(oneLine(item.text));
            if (item.page >= 0) {
                md.append(" · p. ").append(item.page + 1);
            }
        }
        md.append('\n');
        for (Item k : item.kids) {
            markdown(md, k, depth + 1);
        }
    }

    // ------------------------------------------------------------------ mind map

    /** The tree markmap draws: {content (HTML), children, payload: {id, color}}. */
    public static JSONObject toMarkmap(Item root) throws JSONException {
        return markmap(root);
    }

    private static JSONObject markmap(Item item) throws JSONException {
        final StringBuilder html = new StringBuilder();
        if (BOOK.equals(item.category) || CHAPTER.equals(item.category)) {
            html.append("<b>").append(escape(item.text)).append("</b>");
            if (BOOK.equals(item.category) && !item.detail.isEmpty()) {
                html.append("<br><i>").append(escape(item.detail)).append("</i>");
            }
        } else if (LINE.equals(item.category)) {
            html.append("<i>").append(escape(item.text)).append("</i>");
        } else {
            html.append(escape(item.text));
        }
        final String content = item.dim ? "<span style=\"opacity:.5\">" + html + "</span>" : html.toString();
        final JSONObject o = new JSONObject()
                .put("content", content)
                .put("payload", new JSONObject().put("id", item.id).put("color", ColorNames.hex(color(item))));
        final JSONArray kids = new JSONArray();
        for (Item k : item.kids) {
            kids.put(markmap(k));
        }
        o.put("children", kids);
        return o;
    }

    static String escape(String s) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; s != null && i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '&':
                    out.append("&amp;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ").trim();
    }
}
