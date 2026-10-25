package com.foobnix.ai;

import com.foobnix.model.AppProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Summaries and AI highlights, one JSON file per book in the synced profile folder, the way
 * Librera keeps progress and bookmarks: every device writes its own copy
 * ({@code profile.<name>/device.<model>/ai/<book>.json}) and the newest copy of any device is the
 * book's data. A change starts from that newest copy, so nothing another device added is lost.
 */
public class AiStore {

    public static final String FOLDER = "ai";
    /** Key of the summary of the whole book. */
    public static final String BOOK = "@book";

    private static final Object LOCK = new Object();
    /** 2: quotes carry a category (AiCategory); highlights of version 1 have none and are dropped. */
    private static final int VERSION = 2;

    public static class Summary {
        public final String key;
        public final String title;
        public final String text;
        public final String model;
        public final long time;

        public Summary(String key, String title, String text, String model, long time) {
            this.key = key;
            this.title = title;
            this.text = text;
            this.model = model;
            this.time = time;
        }
    }

    public static class Quote {
        /** The words as the book has them. */
        public final String text;
        /** 0-based page where the quote started when it was picked (a hint: EPUB pages move). */
        public final int page;
        /** Why the AI picked it, and its color. */
        public final AiCategory category;
        /** The {@link Idea#id} it carries, or -1 (key passages from before ideas). */
        public final int idea;

        public Quote(String text, int page, AiCategory category) {
            this(text, page, category, -1);
        }

        public Quote(String text, int page, AiCategory category, int idea) {
            this.text = text;
            this.page = page;
            this.category = category;
            this.idea = idea;
        }
    }

    /** One idea of a chapter, as the AI listed it; its marks are the quotes and paragraphs naming its id. */
    public static class Idea {
        /** Unique in its chapter. */
        public final int id;
        public final String text;
        public final AiCategory category;
        /** The id of the idea it supports (a main idea's evidence, caveat...), or -1. */
        public final int parent;
        /** Whether a mark of it was found in the book when it was picked (else "not located"). */
        public final boolean marked;
        /** 0-based page of its first mark when it was picked, or -1. */
        public final int page;

        public Idea(int id, String text, AiCategory category, int parent, boolean marked, int page) {
            this.id = id;
            this.text = text;
            this.category = category;
            this.parent = parent;
            this.marked = marked;
            this.page = page;
        }
    }

    /** The AI highlights of one chapter. */
    public static class Chapter {
        public final String key;
        public final String title;
        public final String model;
        public final long time;
        /** Key sentences: filled. */
        public final List<Quote> quotes;
        /** The ideas the marks belong to, in reading order; empty for key passages from before ideas. */
        public final List<Idea> ideas;
        /** Must-read paragraphs, each the book's whole words: a bar beside them. */
        public final List<Quote> paragraphs;
        /** The AI's answer was cut off: ideas after the cut are missing. */
        public final boolean incomplete;

        public Chapter(String key, String title, String model, long time, List<Quote> quotes) {
            this(key, title, model, time, quotes, new ArrayList<Idea>(), new ArrayList<Quote>(), false);
        }

        public Chapter(String key, String title, String model, long time, List<Quote> quotes,
                       List<Idea> ideas, List<Quote> paragraphs, boolean incomplete) {
            this.key = key;
            this.title = title;
            this.model = model;
            this.time = time;
            this.quotes = quotes;
            this.ideas = ideas;
            this.paragraphs = paragraphs;
            this.incomplete = incomplete;
        }

        /** The same chapter with other key sentences (the rest kept). */
        public Chapter withQuotes(List<Quote> quotes) {
            return new Chapter(key, title, model, time, quotes, ideas, paragraphs, incomplete);
        }

        public Idea idea(int id) {
            for (Idea idea : ideas) {
                if (idea.id == id) {
                    return idea;
                }
            }
            return null;
        }

        public int notLocated() {
            int n = 0;
            for (Idea idea : ideas) {
                if (!idea.marked) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Everything stored for one book. */
    public static class Book {
        public long updated;
        /** What the reader wants from the book (for "apply"); null until asked, empty when none was given. */
        public String goal;
        public final List<Summary> summaries = new ArrayList<>();
        public final List<Chapter> highlights = new ArrayList<>();
        /** The chapters of the whole book, as the last key passages run counted them. */
        public final List<String> chapterKeys = new ArrayList<>();
        /** Chapters with nothing to mark (no text, or the AI found no idea): done as well. */
        public final List<String> emptyKeys = new ArrayList<>();

        public Summary summary(String key) {
            for (Summary s : summaries) {
                if (s.key.equals(key)) {
                    return s;
                }
            }
            return null;
        }

        public void putSummary(Summary summary) {
            removeByKey(summaries, summary.key);
            summaries.add(summary);
        }

        public Chapter chapter(String key) {
            for (Chapter c : highlights) {
                if (c.key.equals(key)) {
                    return c;
                }
            }
            return null;
        }

        public void putChapter(Chapter chapter) {
            removeByKey(highlights, chapter.key);
            highlights.add(chapter);
            emptyKeys.remove(chapter.key);
        }

        /** Whether a chapter needs no key passages run: marked by ideas, or nothing to mark. */
        public boolean isDone(String key) {
            final Chapter c = chapter(key);
            return c != null && !c.ideas.isEmpty() || emptyKeys.contains(key);
        }

        /** Every chapter of the book has its key passages (the menu's ✓). */
        public boolean allMarked() {
            if (chapterKeys.isEmpty()) {
                return false;
            }
            for (String key : chapterKeys) {
                if (!isDone(key)) {
                    return false;
                }
            }
            return true;
        }

        public boolean removeChapter(String key) {
            return removeByKey(highlights, key);
        }

        public int quoteCount() {
            int n = 0;
            for (Chapter c : highlights) {
                n += c.quotes.size();
            }
            return n;
        }

        public List<String> quoteTexts() {
            final List<String> texts = new ArrayList<>();
            for (Chapter c : highlights) {
                for (Quote q : c.quotes) {
                    texts.add(q.text);
                }
            }
            return texts;
        }

        private static boolean removeByKey(List<?> list, String key) {
            boolean removed = false;
            for (Iterator<?> it = list.iterator(); it.hasNext(); ) {
                final Object o = it.next();
                final String k = o instanceof Summary ? ((Summary) o).key : ((Chapter) o).key;
                if (k.equals(key)) {
                    it.remove();
                    removed = true;
                }
            }
            return removed;
        }

        String toJson() throws JSONException {
            final JSONObject root = new JSONObject();
            root.put("version", VERSION);
            root.put("updated", updated);
            if (goal != null) {
                root.put("goal", goal);
            }
            final JSONArray s = new JSONArray();
            for (Summary summary : summaries) {
                s.put(new JSONObject()
                        .put("key", summary.key)
                        .put("title", summary.title)
                        .put("text", summary.text)
                        .put("model", summary.model)
                        .put("time", summary.time));
            }
            root.put("summaries", s);
            final JSONArray h = new JSONArray();
            for (Chapter chapter : highlights) {
                final JSONArray quotes = new JSONArray();
                for (Quote q : chapter.quotes) {
                    quotes.put(quoteJson(q));
                }
                final JSONObject c = new JSONObject()
                        .put("key", chapter.key)
                        .put("title", chapter.title)
                        .put("model", chapter.model)
                        .put("time", chapter.time)
                        .put("quotes", quotes);
                // apart from quotes: an older version draws sentences only, never a whole paragraph
                if (!chapter.ideas.isEmpty()) {
                    final JSONArray ideas = new JSONArray();
                    for (Idea idea : chapter.ideas) {
                        ideas.put(new JSONObject().put("id", idea.id).put("text", idea.text)
                                .put("category", idea.category.code).put("parent", idea.parent)
                                .put("marked", idea.marked).put("page", idea.page));
                    }
                    c.put("ideas", ideas);
                }
                if (!chapter.paragraphs.isEmpty()) {
                    final JSONArray paragraphs = new JSONArray();
                    for (Quote q : chapter.paragraphs) {
                        paragraphs.put(quoteJson(q));
                    }
                    c.put("paragraphs", paragraphs);
                }
                if (chapter.incomplete) {
                    c.put("incomplete", true);
                }
                h.put(c);
            }
            root.put("highlights", h);
            if (!chapterKeys.isEmpty()) {
                root.put("chapterKeys", new JSONArray(chapterKeys));
            }
            if (!emptyKeys.isEmpty()) {
                root.put("emptyKeys", new JSONArray(emptyKeys));
            }
            return root.toString();
        }

        static Book fromJson(String json) throws JSONException {
            final JSONObject root = new JSONObject(json);
            final Book book = new Book();
            book.updated = root.optLong("updated", 0);
            book.goal = root.has("goal") ? root.optString("goal", "") : null;
            final JSONArray s = root.optJSONArray("summaries");
            for (int i = 0; s != null && i < s.length(); i++) {
                final JSONObject o = s.getJSONObject(i);
                book.summaries.add(new Summary(o.getString("key"), o.optString("title", ""),
                        o.optString("text", ""), o.optString("model", ""), o.optLong("time", 0)));
            }
            // highlights from before categories are dropped (the owner's choice)
            final JSONArray h = root.optInt("version", 1) < 2 ? null : root.optJSONArray("highlights");
            for (int i = 0; h != null && i < h.length(); i++) {
                final JSONObject o = h.getJSONObject(i);
                final List<Idea> ideas = new ArrayList<>();
                final JSONArray d = o.optJSONArray("ideas");
                for (int k = 0; d != null && k < d.length(); k++) {
                    final JSONObject idea = d.getJSONObject(k);
                    final AiCategory category = AiCategory.of(idea.optString("category", null));
                    if (category != null) {
                        ideas.add(new Idea(idea.getInt("id"), idea.optString("text", ""), category,
                                idea.optInt("parent", -1), idea.optBoolean("marked", true), idea.optInt("page", -1)));
                    }
                }
                book.highlights.add(new Chapter(o.getString("key"), o.optString("title", ""),
                        o.optString("model", ""), o.optLong("time", 0), quotes(o.optJSONArray("quotes")),
                        ideas, quotes(o.optJSONArray("paragraphs")), o.optBoolean("incomplete", false)));
            }
            strings(root.optJSONArray("chapterKeys"), book.chapterKeys);
            strings(root.optJSONArray("emptyKeys"), book.emptyKeys);
            return book;
        }

        private static void strings(JSONArray a, List<String> into) {
            for (int i = 0; a != null && i < a.length(); i++) {
                into.add(a.optString(i, ""));
            }
        }

        private static JSONObject quoteJson(Quote q) throws JSONException {
            final JSONObject o = new JSONObject().put("text", q.text).put("page", q.page).put("category", q.category.code);
            if (q.idea >= 0) {
                o.put("idea", q.idea);
            }
            return o;
        }

        private static List<Quote> quotes(JSONArray q) throws JSONException {
            final List<Quote> quotes = new ArrayList<>();
            for (int k = 0; q != null && k < q.length(); k++) {
                final JSONObject quote = q.getJSONObject(k);
                final AiCategory category = AiCategory.of(quote.optString("category", null));
                if (category != null) {
                    quotes.add(new Quote(quote.getString("text"), quote.optInt("page", -1), category, quote.optInt("idea", -1)));
                }
            }
            return quotes;
        }
    }

    public interface Change {
        void apply(Book book);
    }

    private final File profile;
    private final String device;

    /**
     * @param profile the profile folder
     * @param device  the folder in it this device writes to; empty for the profile folder itself,
     *                which every device shares now (.agents/planning/unified-sync-design.md)
     */
    AiStore(File profile, String device) {
        this.profile = profile;
        this.device = device;
    }

    /** The store of the current profile. */
    public static AiStore get() {
        return new AiStore(AppProfile.SYNC_FOLDER_PROFILE, "");
    }

    private File ownFolder() {
        return device.isEmpty() ? new File(profile, FOLDER) : new File(new File(profile, device), FOLDER);
    }

    /** The data of a book: the newest copy of any device, or empty. */
    public Book load(String bookName) {
        synchronized (LOCK) {
            Book newest = null;
            final File[] devices = profile == null ? null : profile.listFiles();
            if (devices != null) {
                final String name = fileName(bookName);
                final List<File> folders = new ArrayList<>();
                folders.add(ownFolder());
                for (File folder : devices) {
                    if (folder.isDirectory() && folder.getName().startsWith(AppProfile.DEVICE_PREFIX)) {
                        folders.add(new File(folder, FOLDER));
                    }
                }
                for (File folder : folders) {
                    final File file = new File(folder, name);
                    if (!file.isFile()) {
                        continue;
                    }
                    try {
                        final Book book = Book.fromJson(read(file));
                        if (newest == null || book.updated > newest.updated) {
                            newest = book;
                        }
                    } catch (Exception e) {
                        // a copy half written by a sync does not hide the others
                    }
                }
            }
            return newest == null ? new Book() : newest;
        }
    }

    /** Applies a change to the newest data of the book and writes it as this device's copy. */
    public Book update(String bookName, Change change) throws IOException {
        synchronized (LOCK) {
            final Book book = load(bookName);
            change.apply(book);
            // newer than what it started from, even when another device's clock runs ahead
            book.updated = Math.max(System.currentTimeMillis(), book.updated + 1);
            final File folder = ownFolder();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("cannot create " + folder);
            }
            final File file = new File(folder, fileName(bookName));
            final File temp = new File(folder, file.getName() + ".tmp");
            try {
                write(temp, book.toJson());
            } catch (JSONException e) {
                throw new IOException(e.getMessage());
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("cannot replace " + file);
            }
            if (!temp.renameTo(file)) {
                throw new IOException("cannot write " + file);
            }
            // another device shows it as soon as it asks
            com.foobnix.webdav.SyncEvents.aiSaved();
            return book;
        }
    }

    /** Where the data of a book is kept, relative to the profile folder (what syncing the book takes). */
    public static String profilePath(String bookName) {
        return FOLDER + "/" + fileName(bookName);
    }

    /** File name for a book: its own name made safe for any file system, long names shortened. */
    public static String fileName(String bookName) {
        String name = bookName == null ? "book" : bookName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isEmpty()) {
            name = "book";
        }
        if (name.length() > 120) {
            name = name.substring(0, 100) + "-" + Integer.toHexString(bookName.hashCode());
        }
        return name + ".json";
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
