package com.foobnix.assist.review;

import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.useit.UseDates;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * How ideas get into the review queue, from what other features already stored (their items are
 * only read): a chapter's blank-page recall ({@code recall:<chapter key>}: missed and unsure ideas
 * come back the next day, remembered ones after a longer first gap), a chapter's key-passage
 * ideas the reader adds by hand, and one note of "The book, updated" ("Quiz me later"). Each
 * method returns the items to write; an idea already in the queue keeps its history. No Android.
 */
public final class ReviewSources {

    private ReviewSources() {
    }

    /** The id of an idea of a recall attempt: the key passage's own id, or the check's own with an "r". */
    static String recallId(Recall.Attempt attempt, Recall.Idea idea) {
        return attempt.fromPassages ? String.valueOf(idea.id) : "r" + idea.id;
    }

    /**
     * The queue's items after a chapter's recall attempt: new ones for its checked ideas, and
     * changed ones for ideas already there when this attempt is newer than the one read before
     * (a miss costs one strength and brings the idea back the next day). Empty when the attempt
     * was not checked or was read already.
     *
     * @param existing the book's review items by key
     */
    public static List<ReviewItem> fromRecall(String chapterKey, Recall.Attempt attempt,
                                              Map<String, ReviewItem> existing, TimeZone zone) {
        final List<ReviewItem> out = new ArrayList<>();
        if (attempt == null || !attempt.isDone() || attempt.date <= 0 || chapterKey == null) {
            return out;
        }
        final String day = UseDates.of(attempt.date, zone);
        final String tomorrow = UseDates.addDays(day, ReviewSchedule.FIRST_GAP);
        for (Recall.Idea idea : attempt.ideas) {
            final boolean missed = Recall.MISSED.equals(idea.state);
            final boolean unsure = Recall.UNSURE.equals(idea.state);
            final boolean remembered = Recall.REMEMBERED.equals(idea.state);
            if (idea.text.isEmpty() || !(missed || unsure || remembered)) {
                continue;
            }
            final String id = recallId(attempt, idea);
            final ReviewItem before = existing == null ? null : existing.get(ReviewItem.key(chapterKey, id));
            if (before != null && before.recall >= attempt.date) {
                continue;
            }
            final ReviewItem item;
            if (before == null) {
                item = new ReviewItem();
                item.chapterKey = chapterKey;
                item.id = ReviewItem.id(id);
                item.idea = idea.text;
                item.chapterTitle = attempt.title;
                item.quote = idea.quote();
                item.page = idea.page();
                item.reason = missed ? ReviewItem.MISSED : unsure ? ReviewItem.UNSURE : ReviewItem.FADING;
                item.strength = missed ? 1 : unsure ? 2 : 3;
                item.gap = remembered ? ReviewSchedule.REMEMBERED_GAP : ReviewSchedule.FIRST_GAP;
                item.due = UseDates.addDays(day, item.gap);
                item.add(new ReviewItem.Past(day, ReviewItem.RECALL, remembered, item.gap));
            } else {
                item = before;
                if (remembered) {
                    item.add(new ReviewItem.Past(day, ReviewItem.RECALL, true, 0));
                } else {
                    if (missed) {
                        item.strength = ReviewItem.strength(item.strength - 1);
                    }
                    item.reason = missed ? ReviewItem.MISSED : ReviewItem.UNSURE;
                    if (item.due.compareTo(tomorrow) > 0) {
                        item.due = tomorrow;
                        item.gap = ReviewSchedule.FIRST_GAP;
                    }
                    item.add(new ReviewItem.Past(day, ReviewItem.RECALL, false, ReviewSchedule.FIRST_GAP));
                }
                if (item.quote.isEmpty() && !idea.quote().isEmpty()) {
                    item.quote = idea.quote();
                    item.page = idea.page();
                }
            }
            item.recall = attempt.date;
            out.add(item);
        }
        return out;
    }

    /**
     * A chapter's key-passage ideas added by hand ("Review this chapter's ideas"): each one not in
     * the queue yet enters it due today, as fading.
     */
    public static List<ReviewItem> fromPassages(String chapterKey, String chapterTitle, List<Recall.Idea> ideas,
                                                Map<String, ReviewItem> existing, String today) {
        final List<ReviewItem> out = new ArrayList<>();
        if (ideas == null || chapterKey == null || !UseDates.isDay(today)) {
            return out;
        }
        for (Recall.Idea idea : ideas) {
            final String id = String.valueOf(idea.id);
            if (idea.text.isEmpty() || (existing != null && existing.containsKey(ReviewItem.key(chapterKey, id)))) {
                continue;
            }
            final ReviewItem item = new ReviewItem();
            item.chapterKey = chapterKey;
            item.id = id;
            item.idea = idea.text;
            item.chapterTitle = chapterTitle == null ? "" : chapterTitle;
            item.quote = idea.quote();
            item.page = idea.page();
            item.reason = ReviewItem.FADING;
            item.strength = 2;
            item.due = today;
            item.add(new ReviewItem.Past(today, ReviewItem.ADDED, true, 0));
            out.add(item);
        }
        return out;
    }

    /** The id of a note's idea: the same note always gets the same one. */
    static String noteId(String title) {
        return "n" + Integer.toHexString(norm(title).hashCode());
    }

    /**
     * One note of "The book, updated" ("Quiz me later"): its idea enters the queue due tomorrow;
     * null when it has no title or is in the queue already.
     */
    public static ReviewItem fromNote(String chapterKey, String chapterTitle, String title, String quote, int page,
                                      Map<String, ReviewItem> existing, String today) {
        if (title == null || title.trim().isEmpty() || chapterKey == null || !UseDates.isDay(today)) {
            return null;
        }
        final String id = noteId(title);
        if (existing != null && existing.containsKey(ReviewItem.key(chapterKey, id))) {
            return null;
        }
        final ReviewItem item = new ReviewItem();
        item.chapterKey = chapterKey;
        item.id = id;
        item.idea = title.trim();
        item.chapterTitle = chapterTitle == null ? "" : chapterTitle;
        item.quote = quote == null ? "" : quote.trim();
        item.page = page;
        item.reason = ReviewItem.NOTE;
        item.strength = 1;
        item.gap = ReviewSchedule.FIRST_GAP;
        item.due = UseDates.addDays(today, ReviewSchedule.FIRST_GAP);
        item.add(new ReviewItem.Past(today, ReviewItem.ADDED, true, ReviewSchedule.FIRST_GAP));
        return item;
    }

    /** The id of a marked spot's idea: a key passage's own id, else one made of its text with an "s". */
    static String spotId(Recall.Idea idea) {
        return idea.id >= 0 ? String.valueOf(idea.id) : "s" + Integer.toHexString(norm(idea.text).hashCode());
    }

    /**
     * The ideas of the pages "The book reads you back" marked in a chapter (a re-read, a long
     * page, help asked, eyes off the page): each one not in the queue yet enters it due tomorrow,
     * weak. A key-passage idea keeps its own id, so it is not added a second time.
     */
    public static List<ReviewItem> fromSpots(String chapterKey, String chapterTitle, List<Recall.Idea> ideas,
                                             Map<String, ReviewItem> existing, String today) {
        final List<ReviewItem> out = new ArrayList<>();
        if (ideas == null || chapterKey == null || !UseDates.isDay(today)) {
            return out;
        }
        final java.util.Set<String> added = new java.util.HashSet<>();
        for (Recall.Idea idea : ideas) {
            if (idea == null || idea.text.isEmpty()) {
                continue;
            }
            final String id = spotId(idea);
            if ((existing != null && existing.containsKey(ReviewItem.key(chapterKey, id))) || !added.add(id)) {
                continue;
            }
            final ReviewItem item = new ReviewItem();
            item.chapterKey = chapterKey;
            item.id = id;
            item.idea = idea.text;
            item.chapterTitle = chapterTitle == null ? "" : chapterTitle;
            item.quote = idea.quote();
            item.page = idea.page();
            item.reason = ReviewItem.FADING;
            item.strength = 1;
            item.gap = ReviewSchedule.FIRST_GAP;
            item.due = UseDates.addDays(today, ReviewSchedule.FIRST_GAP);
            item.add(new ReviewItem.Past(today, ReviewItem.ADDED, true, ReviewSchedule.FIRST_GAP));
            out.add(item);
        }
        return out;
    }

    /** A text as two ideas are compared: letters and digits only, lower case. */
    public static String norm(String text) {
        final StringBuilder out = new StringBuilder();
        if (text != null) {
            final String lower = text.toLowerCase(Locale.ROOT);
            for (int i = 0; i < lower.length(); ) {
                final int c = lower.codePointAt(i);
                if (Character.isLetterOrDigit(c)) {
                    out.appendCodePoint(c);
                }
                i += Character.charCount(c);
            }
        }
        return out.toString();
    }
}
