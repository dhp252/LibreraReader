package com.foobnix.assist.review;

import com.foobnix.assist.useit.UseDates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Today's review as a list (canvas "Today's review · Mixed across 3 books"): the ideas of every
 * book that are due, oldest due first and the books mixed, {@link #MAX_A_DAY} a day at most; the
 * counts by reason, "Next: 3 tomorrow · 11 this week" and "~4 min". No Android here.
 */
public final class ReviewQueue {

    /** Cards a day at most, the ones answered today counted in. */
    public static final int MAX_A_DAY = 12;
    /** Seconds one card takes, for "about 4 min". */
    static final int SECONDS_A_CARD = 40;

    private ReviewQueue() {
    }

    /** An idea with the book it is from. */
    public static final class Entry {
        /** The name the book's data is stored under. */
        public final String bookName;
        /** Where the book is on this device, or empty. */
        public final String bookPath;
        public final String bookTitle;
        public final String author;
        public final ReviewItem item;

        public Entry(String bookName, String bookPath, String bookTitle, String author, ReviewItem item) {
            this.bookName = bookName == null ? "" : bookName;
            this.bookPath = bookPath == null ? "" : bookPath;
            this.bookTitle = bookTitle == null ? "" : bookTitle;
            this.author = author == null ? "" : author;
            this.item = item;
        }
    }

    /** Ideas answered on {@code today}: they count against the day's cards. */
    public static int answered(List<Entry> all, String today) {
        int n = 0;
        if (all == null) {
            return n;
        }
        for (Entry e : all) {
            for (ReviewItem.Past p : e.item.history) {
                if (ReviewItem.REVIEW.equals(p.kind) && p.day.equals(today)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /**
     * The ideas to review on {@code today}: those due, the longest waiting first; among ideas due
     * the same day the books take turns, the weaker memory first inside a book; cut to what is
     * left of the day's {@link #MAX_A_DAY}.
     */
    public static List<Entry> due(List<Entry> all, String today) {
        return due(all, today, Math.max(0, MAX_A_DAY - answered(all, today)));
    }

    static List<Entry> due(List<Entry> all, String today, int max) {
        final List<Entry> due = new ArrayList<>();
        if (all == null || !UseDates.isDay(today)) {
            return due;
        }
        for (Entry e : all) {
            if (e.item.isDue(today)) {
                due.add(e);
            }
        }
        // inside a book and a day: weaker first, then a stable order
        Collections.sort(due, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int c = a.item.due.compareTo(b.item.due);
                if (c == 0) {
                    c = a.bookName.compareTo(b.bookName);
                }
                if (c == 0) {
                    c = Integer.compare(a.item.strength, b.item.strength);
                }
                return c != 0 ? c : a.item.key().compareTo(b.item.key());
            }
        });
        // its turn inside its book and day: 0 for the first idea of each book, 1 for the second...
        final Map<Entry, Integer> turn = new HashMap<>();
        String day = null;
        String book = null;
        int n = 0;
        for (Entry e : due) {
            if (!e.item.due.equals(day) || !e.bookName.equals(book)) {
                day = e.item.due;
                book = e.bookName;
                n = 0;
            }
            turn.put(e, n++);
        }
        Collections.sort(due, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int c = a.item.due.compareTo(b.item.due);
                if (c == 0) {
                    c = Integer.compare(turn.get(a), turn.get(b));
                }
                if (c == 0) {
                    c = a.bookName.compareTo(b.bookName);
                }
                return c != 0 ? c : a.item.key().compareTo(b.item.key());
            }
        });
        return due.size() > max ? new ArrayList<>(due.subList(0, Math.max(0, max))) : due;
    }

    /** How many of {@code list} are in the queue for {@code reason}. */
    public static int count(List<Entry> list, String reason) {
        int n = 0;
        for (Entry e : list) {
            if (reason.equals(e.item.reason)) {
                n++;
            }
        }
        return n;
    }

    /** How many different books {@code list} is from. */
    public static int books(List<Entry> list) {
        final List<String> seen = new ArrayList<>();
        for (Entry e : list) {
            if (!seen.contains(e.bookName)) {
                seen.add(e.bookName);
            }
        }
        return seen.size();
    }

    /** {due tomorrow, due in the seven days after today} among ideas not due yet. */
    public static int[] next(List<Entry> all, String today) {
        final List<String> days = new ArrayList<>();
        for (Entry e : all) {
            days.add(e.item.due);
        }
        return nextDays(days, today);
    }

    static int[] nextDays(List<String> dueDays, String today) {
        int tomorrow = 0;
        int week = 0;
        for (String day : dueDays) {
            final int in = UseDates.isDay(day) && UseDates.isDay(today) ? UseDates.daysBetween(today, day) : 0;
            if (in == 1) {
                tomorrow++;
            }
            if (in >= 1 && in <= 7) {
                week++;
            }
        }
        return new int[]{tomorrow, week};
    }

    /** "about 4 min" for {@code cards} cards: never less than one minute. */
    public static int minutes(int cards) {
        return Math.max(1, Math.round(cards * SECONDS_A_CARD / 60f));
    }
}
