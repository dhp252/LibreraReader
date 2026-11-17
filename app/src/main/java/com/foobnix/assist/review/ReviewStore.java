package com.foobnix.assist.review;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.useit.UseDates;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.ui2.AppDB;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads and writes the review queue: {@code review:<chapter key>:<idea id>} items in each book's
 * assist file and the library's index of them ({@link ReviewIndex}); brings in what blank-page
 * recall stored (only read) when a book's file changed. Every call reads or writes files: call
 * them on {@link #IO}, never on the main thread. Nothing here runs unless a screen asks: there is
 * no timer, alarm or scheduled work (the owner's rule).
 */
final class ReviewStore {

    /** One thread for the store, so a save and the list read after it keep their order. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** When the assist folder was looked through last (this run of the app). */
    private static long lastScan;

    private ReviewStore() {
    }

    /** What the index says of a book beside its ideas. */
    static final class Meta {
        final String path;
        final String title;
        final String author;

        Meta(String path, String title, String author) {
            this.path = path == null ? "" : path;
            this.title = title == null ? "" : title;
            this.author = author == null ? "" : author;
        }
    }

    /** Today as the device's local day. */
    static String today() {
        return UseDates.of(System.currentTimeMillis(), TimeZone.getDefault());
    }

    /**
     * The name a book's review data goes by: its file name as the stores make it safe for a file
     * system, so the name can be read back from the assist folder. For nearly every book it is
     * the book's own file name.
     */
    static String name(String bookPath) {
        final String file = AiStore.fileName(AiHighlights.bookName(bookPath));
        return file.substring(0, file.length() - ".json".length());
    }

    /** The book's review items by key. */
    static Map<String, ReviewItem> items(String book) {
        final Map<String, ReviewItem> out = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(book, ReviewItem.PREFIX).entrySet()) {
                final ReviewItem item = ReviewItem.fromJson(e.getKey(), e.getValue());
                if (item != null) {
                    out.put(e.getKey(), item);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return out;
    }

    /** Writes one idea and brings the library's index in line with the book. */
    static void save(String book, ReviewItem item, Meta meta) throws IOException {
        try {
            AssistStore.get().put(book, item.key(), item.toJson());
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        reindex(book, items(book).values(), meta);
    }

    /**
     * Brings a book's queue in line with its file: the blank-page recall attempts not read yet
     * become review items, and the library's index entry is rebuilt when it says something else.
     *
     * @param meta the book's path, title and author when the caller knows them (the open book),
     *             else null: the index entry's own, or the library's
     * @return how many ideas are new in the queue
     */
    static int sync(String book, Meta meta) {
        int added = 0;
        try {
            final Map<String, ReviewItem> items = new LinkedHashMap<>();
            final Map<String, Recall.Attempt> attempts = new LinkedHashMap<>();
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(book, null).entrySet()) {
                if (e.getKey().startsWith(ReviewItem.PREFIX)) {
                    final ReviewItem item = ReviewItem.fromJson(e.getKey(), e.getValue());
                    if (item != null) {
                        items.put(e.getKey(), item);
                    }
                } else if (e.getKey().startsWith(Recall.PREFIX)) {
                    final Recall.Attempt attempt = Recall.Attempt.fromJson(e.getValue());
                    if (attempt != null && attempt.isDone()) {
                        attempts.put(e.getKey().substring(Recall.PREFIX.length()), attempt);
                    }
                }
            }
            for (Map.Entry<String, Recall.Attempt> e : attempts.entrySet()) {
                for (ReviewItem item : ReviewSources.fromRecall(e.getKey(), e.getValue(), items, TimeZone.getDefault())) {
                    if (!items.containsKey(item.key())) {
                        added++;
                    }
                    AssistStore.get().put(book, item.key(), item.toJson());
                    items.put(item.key(), item);
                }
            }
            reindex(book, items.values(), meta);
        } catch (Exception e) {
            LOG.e(e);
        }
        return added;
    }

    /** Rebuilds a book's entry in the library's index from its ideas; written only when it changed. */
    static void reindex(String book, Collection<ReviewItem> items, Meta meta) throws IOException {
        try {
            final String key = ReviewIndex.key(book);
            final JSONObject old = AssistStore.get().item(AssistStore.LIBRARY, key);
            if (items.isEmpty()) {
                if (old != null) {
                    AssistStore.get().remove(AssistStore.LIBRARY, key);
                }
                return;
            }
            Meta m = meta;
            if (m == null) {
                m = old != null ? new Meta(old.optString("path"), old.optString("title"), old.optString("author"))
                        : lookup(book);
            }
            final JSONObject now = ReviewIndex.entry(book, m.path, m.title, m.author, items);
            if (old != null) {
                old.remove(AssistStore.T);
            }
            if (!ReviewIndex.same(old, now)) {
                AssistStore.get().put(AssistStore.LIBRARY, key, now);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** A book of the library by its review name; a name alone when it is not on this device. */
    static Meta lookup(String book) {
        try {
            for (FileMeta meta : AppDB.get().getAll()) {
                final String path = meta == null ? null : meta.getPath();
                if (path != null && name(path).equals(book)) {
                    final String title = meta.getTitle() == null || meta.getTitle().trim().isEmpty() ? book
                            : meta.getTitle().trim();
                    return new Meta(path, title, meta.getAuthor() == null ? "" : meta.getAuthor().trim());
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return new Meta("", book, "");
    }

    /**
     * Looks through the books whose assist file changed since the last look in this run of the
     * app (all of them the first time): a recall checked in the reader, or review answers that
     * came from another device, reach the queue and the index here. Called when a review screen
     * or the main screen opens; nothing calls it on a timer.
     */
    static synchronized void scan() {
        try {
            final long started = System.currentTimeMillis();
            final File[] files = new File(AppProfile.SYNC_FOLDER_PROFILE, AssistStore.FOLDER).listFiles();
            if (files != null) {
                final String library = AiStore.fileName(AssistStore.LIBRARY);
                for (File f : files) {
                    final String file = f.getName();
                    // a little slack: file times are coarse on some file systems
                    if (f.isFile() && file.endsWith(".json") && !file.equals(library) && f.lastModified() >= lastScan - 2000) {
                        sync(file.substring(0, file.length() - ".json".length()), null);
                    }
                }
            }
            lastScan = started;
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The library's index. */
    static List<ReviewIndex.Book> books() {
        try {
            return ReviewIndex.books(AssistStore.get().items(AssistStore.LIBRARY, ReviewIndex.PREFIX));
        } catch (Exception e) {
            LOG.e(e);
            return new ArrayList<>();
        }
    }

    /** Every idea in the queue, of every book in the index. */
    static List<ReviewQueue.Entry> all() {
        final List<ReviewQueue.Entry> out = new ArrayList<>();
        for (ReviewIndex.Book b : books()) {
            for (ReviewItem item : items(b.name).values()) {
                out.add(new ReviewQueue.Entry(b.name, b.path, b.title.isEmpty() ? b.name : b.title, b.author, item));
            }
        }
        return out;
    }

    /**
     * Each book's stored ideas, for the wrong options of its cards: its review items, its key
     * passages (F-004) and the ideas of its recall attempts. All only read; no book is opened.
     */
    static Map<String, List<ReviewCards.Candidate>> pools(Collection<String> books) {
        final Map<String, List<ReviewCards.Candidate>> out = new HashMap<>();
        for (String book : books) {
            final List<ReviewCards.Candidate> pool = new ArrayList<>();
            try {
                for (ReviewItem item : items(book).values()) {
                    pool.add(new ReviewCards.Candidate(item.idea, item.chapterTitle));
                }
                for (AiStore.Chapter chapter : AiStore.get().load(book).highlights) {
                    for (AiStore.Idea idea : chapter.ideas) {
                        if (idea.text != null && idea.parent < 0) {
                            pool.add(new ReviewCards.Candidate(idea.text, chapter.title));
                        }
                    }
                }
                for (JSONObject o : AssistStore.get().items(book, Recall.PREFIX).values()) {
                    final Recall.Attempt attempt = Recall.Attempt.fromJson(o);
                    if (attempt != null) {
                        for (Recall.Idea idea : attempt.ideas) {
                            pool.add(new ReviewCards.Candidate(idea.text, attempt.title));
                        }
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            out.put(book, pool);
        }
        return out;
    }
}
