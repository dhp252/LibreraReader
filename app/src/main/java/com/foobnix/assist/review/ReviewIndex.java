package com.foobnix.assist.review;

import com.foobnix.assist.useit.UseDates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The library's short list of what is in the review queue, so the main screen can say "6 ideas
 * to review" without reading every book's file. One item per book in the library assist file,
 * {@code review:index:<book name>} = {book, path, title, author, days: [the due day of each of its
 * ideas], seenDay, seen}: one item each, not one list, so two devices that review different books
 * both keep theirs when the files are merged item by item (the pattern of Use it's index). The
 * item only repeats what the book's own file says: it is rebuilt from that file whenever the
 * book's review items change, on whichever device sees the change, and removed with its last idea.
 * {@code path} is where the book is on the device that wrote the item; another device looks the
 * book up by name.
 */
public final class ReviewIndex {

    /** Key prefix of a book's entry in the library assist file. */
    public static final String PREFIX = "review:index:";

    private ReviewIndex() {
    }

    /** A book as the index knows it. */
    public static final class Book {
        /** The name its data is stored under. */
        public String name = "";
        public String path = "";
        public String title = "";
        public String author = "";
        /** The due day of each idea. */
        public final List<String> days = new ArrayList<>();
        /** The last day a card of this book was answered, and how many ideas were that day. */
        public String seenDay = "";
        public int seen;

        public int due(String today) {
            int n = 0;
            for (String d : days) {
                if (UseDates.reached(today, d)) {
                    n++;
                }
            }
            return n;
        }

        public int answered(String today) {
            return seenDay.equals(today) ? seen : 0;
        }
    }

    public static String key(String bookName) {
        return PREFIX + bookName;
    }

    /** The index item of a book with these ideas; null when it has none (then the item is removed). */
    public static JSONObject entry(String bookName, String path, String title, String author,
                                   Collection<ReviewItem> items) throws JSONException {
        if (items == null || items.isEmpty()) {
            return null;
        }
        final List<String> days = new ArrayList<>();
        String seenDay = "";
        for (ReviewItem i : items) {
            days.add(i.due);
            for (ReviewItem.Past p : i.history) {
                if (ReviewItem.REVIEW.equals(p.kind) && p.day.compareTo(seenDay) > 0) {
                    seenDay = p.day;
                }
            }
        }
        int seen = 0;
        if (!seenDay.isEmpty()) {
            for (ReviewItem i : items) {
                for (ReviewItem.Past p : i.history) {
                    if (ReviewItem.REVIEW.equals(p.kind) && p.day.equals(seenDay)) {
                        seen++;
                        break;
                    }
                }
            }
        }
        // sorted: the same ideas always give the same item, whatever order the file is read in
        Collections.sort(days);
        return new JSONObject().put("book", bookName).put("path", path == null ? "" : path)
                .put("title", title == null ? "" : title).put("author", author == null ? "" : author)
                .put("days", new JSONArray(days)).put("seenDay", seenDay).put("seen", seen);
    }

    /** Whether two index items say the same (the store's own {@code t} aside). */
    public static boolean same(JSONObject a, JSONObject b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.optString("book").equals(b.optString("book")) && a.optString("path").equals(b.optString("path"))
                && a.optString("title").equals(b.optString("title"))
                && a.optString("author").equals(b.optString("author"))
                && String.valueOf(a.optJSONArray("days")).equals(String.valueOf(b.optJSONArray("days")))
                && a.optString("seenDay").equals(b.optString("seenDay")) && a.optInt("seen") == b.optInt("seen");
    }

    public static Book fromJson(JSONObject o) {
        if (o == null || o.optString("book").isEmpty()) {
            return null;
        }
        final Book b = new Book();
        b.name = o.optString("book");
        b.path = o.optString("path", "");
        b.title = o.optString("title", "");
        b.author = o.optString("author", "");
        final JSONArray days = o.optJSONArray("days");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                if (UseDates.isDay(days.optString(i))) {
                    b.days.add(days.optString(i));
                }
            }
        }
        b.seenDay = o.optString("seenDay", "");
        b.seen = Math.max(0, o.optInt("seen"));
        return b;
    }

    /** The books of the library file's index items (their order does not matter). */
    public static List<Book> books(Map<String, JSONObject> items) {
        final List<Book> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (Map.Entry<String, JSONObject> item : items.entrySet()) {
            final Book b = item.getKey().startsWith(PREFIX) ? fromJson(item.getValue()) : null;
            if (b != null) {
                out.add(b);
            }
        }
        return out;
    }

    /**
     * How many ideas the main screen says are to review on {@code today}: those due in every
     * book, never more than is left of the day's {@link ReviewQueue#MAX_A_DAY}.
     */
    public static int toReview(Collection<Book> books, String today) {
        int due = 0;
        int answered = 0;
        for (Book b : books) {
            due += b.due(today);
            answered += b.answered(today);
        }
        return Math.max(0, Math.min(due, ReviewQueue.MAX_A_DAY - answered));
    }
}
