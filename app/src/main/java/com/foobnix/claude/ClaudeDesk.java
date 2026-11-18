package com.foobnix.claude;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.SideMatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * A book's desk for an outside Claude ({@code profile.<name>/claude/books/<book>/}): what Claude
 * needs to answer for it without opening the book file or guessing the app's chapter names.
 * <ul>
 * <li>{@code book.json}: the book, the reader's page, the chapters with the app's keys, the
 * skeleton's parts, the inputs some features send (goals, Lens).</li>
 * <li>{@code text.txt}: the text the app reads, page by page, with chapter and page lines.</li>
 * <li>{@code prompts/<feature>.txt}: the system prompt the API would get for this book.</li>
 * </ul>
 * Written by {@link ClaudeBook} on the device; this class holds the pure part (no Android).
 */
public final class ClaudeDesk {

    public static final String BOOK_JSON = "book.json";
    public static final String TEXT = "text.txt";
    public static final String PROMPTS = "prompts";
    /** Version of book.json and text.txt (not of the answers: see ClaudeFeature#format). */
    public static final int DESK_FORMAT = 1;

    private ClaudeDesk() {
    }

    /** What book.json says about a book. */
    public static final class Book {
        public String book = "";
        public String title = "";
        public String author = "";
        public int year;
        /** The book file on the server ({@code Books/...}), or null when it is only on the device. */
        public String file;
        public String devicePath = "";
        public long size;
        public long modified;
        public int pages;
        /** 0-based page the reader is on (features about what was read stop before it). */
        public int readerPage;
        /** The layout pages are counted in: width x height @ font size. */
        public String layout = "";
        /** The reader's goal for the book's key passages (empty: none). */
        public String goal = "";
        /** What the Semantic zoom goal adds to its request, or null when no zoom goal is set. */
        public String zoomGoal;
        public List<BookChapters.Range> chapters;
        /** Characters of text in each chapter, by key. */
        public Map<String, Long> chars;
        /** Skeleton first: its parts (title, chapter numbers) and its own chapter list, or null. */
        public List<String[]> skeletonParts;
        public List<BookChapters.Range> skeletonChapters;
        /** The features this book's prompts were written for (ClaudeFeature ids). */
        public List<String> features;
        public long prepared;
    }

    /** book.json of a book. */
    public static JSONObject bookJson(Book b) throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("format", DESK_FORMAT);
        o.put("book", b.book);
        o.put("title", b.title);
        o.put("author", b.author);
        if (b.year > 0) {
            o.put("year", b.year);
        }
        o.put("file", b.file == null ? JSONObject.NULL : b.file);
        o.put("devicePath", b.devicePath);
        o.put("source", new JSONObject().put("size", b.size).put("modified", b.modified));
        o.put("prepared", iso(b.prepared));
        o.put("layout", b.layout);
        o.put("pages", b.pages);
        o.put("readerPage", b.readerPage);
        o.put("goal", b.goal == null ? "" : b.goal);
        if (b.zoomGoal != null) {
            o.put("zoomGoal", b.zoomGoal);
        }
        o.put("features", new JSONArray(b.features));
        o.put("categories", categories());
        final JSONArray chapters = new JSONArray();
        for (int i = 0; i < b.chapters.size(); i++) {
            final BookChapters.Range r = b.chapters.get(i);
            final Long chars = b.chars == null ? null : b.chars.get(r.key);
            chapters.put(new JSONObject()
                    .put("n", i)
                    .put("key", r.key)
                    .put("title", r.title == null ? "" : r.title)
                    .put("from", r.from)
                    .put("to", r.to)
                    .put("side", SideMatter.isSideTitle(r.title))
                    .put("chars", chars == null ? 0 : chars));
        }
        o.put("chapters", chapters);
        if (b.skeletonParts != null && b.skeletonChapters != null) {
            final JSONArray parts = new JSONArray();
            for (int i = 0; i < b.skeletonParts.size(); i++) {
                parts.put(new JSONObject().put("n", i).put("title", b.skeletonParts.get(i)[0])
                        .put("chapters", b.skeletonParts.get(i)[1]));
            }
            final JSONArray list = new JSONArray();
            for (int i = 0; i < b.skeletonChapters.size(); i++) {
                final BookChapters.Range r = b.skeletonChapters.get(i);
                list.put(new JSONObject().put("n", i).put("key", r.key).put("title", r.title == null ? "" : r.title)
                        .put("from", r.from).put("to", r.to));
            }
            o.put("skeleton", new JSONObject().put("parts", parts).put("chapters", list));
        }
        return o;
    }

    /** The idea categories the key passages prompt names, by code: what Claude may write. */
    private static JSONArray categories() {
        final JSONArray a = new JSONArray();
        for (AiCategory c : AiCategory.values()) {
            a.put(c.code);
        }
        return a;
    }

    /**
     * text.txt: every page's text (null or empty pages left out) under its chapter, with
     * {@code === chapter <n>: <title> [<key>] ===} and {@code --- page <p> ---} lines (p 0-based,
     * as in book.json). Side matter chapters are marked and their text left out.
     */
    public static String text(List<BookChapters.Range> chapters, String[] plain) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) {
            final BookChapters.Range r = chapters.get(i);
            final boolean side = SideMatter.isSideTitle(r.title);
            out.append("=== chapter ").append(i).append(": ").append(oneLine(r.title)).append(" [").append(r.key)
                    .append("]").append(side ? " (side matter: no text)" : "").append(" ===\n");
            if (side) {
                out.append('\n');
                continue;
            }
            for (int p = Math.max(0, r.from); p < r.to && p < plain.length; p++) {
                final String s = plain[p];
                if (s == null || s.trim().isEmpty()) {
                    continue;
                }
                out.append("--- page ").append(p).append(" ---\n").append(s.trim()).append("\n");
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Whether the desk's text was made from this very file in this layout (else it is made again). */
    public static boolean textCurrent(File desk, long size, long modified, String layout) {
        final File text = new File(desk, TEXT);
        final File json = new File(desk, BOOK_JSON);
        if (!text.isFile() || !json.isFile()) {
            return false;
        }
        try {
            final JSONObject o = new JSONObject(ClaudeAnswers.text(json));
            final JSONObject source = o.optJSONObject("source");
            return o.optInt("format") == DESK_FORMAT && source != null && source.optLong("size") == size
                    && source.optLong("modified") == modified && layout.equals(o.optString("layout"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes a desk file when its text differs from what is there (an unchanged desk is not sent
     * to the server again). Returns whether it wrote.
     */
    public static boolean write(File file, String text) throws IOException {
        if (file.isFile()) {
            try {
                if (ClaudeAnswers.text(file).equals(text)) {
                    return false;
                }
            } catch (IOException e) {
                // written anew
            }
        }
        final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        ClaudeAnswers.write(temp, text);
        com.foobnix.webdav.FolderSync.replace(temp, file);
        return true;
    }

    /** The time book.json and answers use: ISO 8601, UTC. */
    public static String iso(long time) {
        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(time));
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
