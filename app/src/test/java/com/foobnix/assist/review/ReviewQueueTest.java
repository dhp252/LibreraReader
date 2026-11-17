package com.foobnix.assist.review;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReviewQueueTest {

    private static final String TODAY = "2026-11-07";

    private static ReviewQueue.Entry entry(String book, String id, String due, int strength, String reason) {
        final ReviewItem i = new ReviewItem();
        i.chapterKey = "c#0";
        i.id = id;
        i.idea = "Idea " + id + " of " + book;
        i.due = due;
        i.strength = strength;
        i.reason = reason;
        return new ReviewQueue.Entry(book, "/books/" + book, "Title of " + book, "Adam Smith", i);
    }

    private static List<String> ids(List<ReviewQueue.Entry> list) {
        final List<String> out = new ArrayList<>();
        for (ReviewQueue.Entry e : list) {
            out.add(e.bookName + "/" + e.item.id);
        }
        return out;
    }

    @Test
    public void dueIsOldestFirstAndMixesBooks() {
        final List<ReviewQueue.Entry> all = new ArrayList<>(Arrays.asList(
                entry("smith", "1", TODAY, 3, ReviewItem.FADING),
                entry("smith", "2", TODAY, 1, ReviewItem.MISSED),
                entry("smith", "3", "2026-11-05", 2, ReviewItem.UNSURE),
                entry("verne", "1", TODAY, 2, ReviewItem.FADING),
                entry("verne", "2", "2026-11-08", 2, ReviewItem.FADING),
                entry("work", "1", TODAY, 4, ReviewItem.NOTE)));
        Collections.shuffle(all, new java.util.Random(7));
        final List<ReviewQueue.Entry> due = ReviewQueue.due(all, TODAY);
        // the overdue one first; then the books take turns, the weaker memory first inside a book
        assertEquals(Arrays.asList("smith/3", "smith/2", "verne/1", "work/1", "smith/1"), ids(due));
        assertEquals(3, ReviewQueue.books(due));
        assertEquals(1, ReviewQueue.count(due, ReviewItem.MISSED));
        assertEquals(1, ReviewQueue.count(due, ReviewItem.UNSURE));
        assertEquals(2, ReviewQueue.count(due, ReviewItem.FADING));
        assertEquals(1, ReviewQueue.count(due, ReviewItem.NOTE));
        assertArrayEquals(new int[]{1, 1}, ReviewQueue.next(all, TODAY));
    }

    @Test
    public void atMostTwelveADayCountingTheAnswered() {
        final List<ReviewQueue.Entry> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(entry("smith", String.valueOf(100 + i), TODAY, 2, ReviewItem.FADING));
        }
        assertEquals(12, ReviewQueue.due(all, TODAY).size());
        // five answered today: they left the due list, and seven more are left of the day
        for (int i = 0; i < 5; i++) {
            ReviewSchedule.answer(all.get(i).item, i % 2 == 0, TODAY);
        }
        assertEquals(5, ReviewQueue.answered(all, TODAY));
        assertEquals(7, ReviewQueue.due(all, TODAY).size());
        assertEquals(0, ReviewQueue.answered(all, "2026-11-08"));
        // the next day the two answered wrong are due again with the other fifteen, twelve at most
        assertEquals(12, ReviewQueue.due(all, "2026-11-08").size());
        assertTrue(ReviewQueue.due(all, "not a day").isEmpty());
        assertTrue(ReviewQueue.due(null, TODAY).isEmpty());
    }

    @Test
    public void nextAndMinutes() {
        assertArrayEquals(new int[]{2, 4}, ReviewQueue.nextDays(Arrays.asList("2026-11-08", "2026-11-08",
                "2026-11-10", "2026-11-14", "2026-11-15", "2026-11-07", "2026-11-01", "bad"), TODAY));
        assertEquals(4, ReviewQueue.minutes(6));
        assertEquals(1, ReviewQueue.minutes(1));
        assertEquals(1, ReviewQueue.minutes(0));
        assertEquals(8, ReviewQueue.minutes(12));
    }

    @Test
    public void theIndexRepeatsABooksIdeas() throws Exception {
        final ReviewItem a = entry("smith", "1", "2026-11-09", 2, ReviewItem.FADING).item;
        final ReviewItem b = entry("smith", "2", "2026-11-05", 1, ReviewItem.MISSED).item;
        final ReviewItem c = entry("smith", "3", TODAY, 1, ReviewItem.MISSED).item;
        ReviewSchedule.answer(c, false, TODAY);
        final JSONObject item = ReviewIndex.entry("smith.epub", "/books/smith.epub", "The Wealth of Nations",
                "Adam Smith", Arrays.asList(a, b, c));
        assertEquals("review:index:smith.epub", ReviewIndex.key("smith.epub"));
        final ReviewIndex.Book book = ReviewIndex.fromJson(new JSONObject(item.toString()));
        assertEquals("smith.epub", book.name);
        assertEquals("/books/smith.epub", book.path);
        assertEquals("The Wealth of Nations", book.title);
        assertEquals("Adam Smith", book.author);
        assertEquals(Arrays.asList("2026-11-05", "2026-11-08", "2026-11-09"), book.days);
        assertEquals(1, book.due(TODAY));
        assertEquals(1, book.answered(TODAY));
        assertEquals(0, book.answered("2026-11-08"));
        assertEquals(2, book.due("2026-11-08"));

        // the same ideas in another order give the same item; a changed day does not
        assertTrue(ReviewIndex.same(item, ReviewIndex.entry("smith.epub", "/books/smith.epub",
                "The Wealth of Nations", "Adam Smith", Arrays.asList(c, a, b))));
        a.due = "2026-11-10";
        assertFalse(ReviewIndex.same(item, ReviewIndex.entry("smith.epub", "/books/smith.epub",
                "The Wealth of Nations", "Adam Smith", Arrays.asList(a, b, c))));
        assertFalse(ReviewIndex.same(item, null));
        assertTrue(ReviewIndex.same(null, null));
        // a book without ideas has no item
        assertNull(ReviewIndex.entry("smith.epub", "", "", "", new ArrayList<ReviewItem>()));
        assertNull(ReviewIndex.fromJson(new JSONObject()));
    }

    @Test
    public void theMainScreenCountsWhatIsLeftOfTheDay() throws Exception {
        final Map<String, JSONObject> items = new HashMap<>();
        final List<ReviewItem> smith = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            smith.add(entry("smith", String.valueOf(i), TODAY, 2, ReviewItem.FADING).item);
        }
        final List<ReviewItem> verne = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            verne.add(entry("verne", String.valueOf(i), i < 4 ? TODAY : "2026-11-20", 2, ReviewItem.FADING).item);
        }
        items.put(ReviewIndex.key("smith"), ReviewIndex.entry("smith", "", "S", "", smith));
        items.put(ReviewIndex.key("verne"), ReviewIndex.entry("verne", "", "V", "", verne));
        items.put("use:index:1", new JSONObject().put("book", "x"));
        List<ReviewIndex.Book> books = ReviewIndex.books(items);
        assertEquals(2, books.size());
        // 13 due, 12 a day
        assertEquals(12, ReviewIndex.toReview(books, TODAY));
        assertEquals(0, ReviewIndex.toReview(books, "2026-11-06"));

        for (int i = 0; i < 5; i++) {
            ReviewSchedule.answer(smith.get(i), true, TODAY);
        }
        items.put(ReviewIndex.key("smith"), ReviewIndex.entry("smith", "", "S", "", smith));
        books = ReviewIndex.books(items);
        // 8 still due, and 7 left of the day's 12
        assertEquals(7, ReviewIndex.toReview(books, TODAY));
        assertEquals(0, ReviewIndex.toReview(new ArrayList<ReviewIndex.Book>(), TODAY));
    }
}
