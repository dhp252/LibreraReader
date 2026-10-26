package com.foobnix.study;

import com.foobnix.ai.BookChapters;

import java.util.ArrayList;
import java.util.List;

/**
 * Which chapter cards a page change brings (F-009 design, "Card rules"): paging forward out of a
 * chapter gives its after card (not for Skip chapters) and the next one's before card; a jump
 * gives only the before card of where it lands. Every card comes once per chapter.
 */
public final class StudyCards {

    public static final int BEFORE = 1;
    public static final int AFTER = 2;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private StudyCards() {
    }

    public static final class Card {
        public final int kind;
        public final String key;

        Card(int kind, String key) {
            this.kind = kind;
            this.key = key;
        }

        @Override
        public String toString() {
            return (kind == BEFORE ? "before " : "after ") + key;
        }
    }

    /**
     * @param prevPage the page shown before (0-based), -1 when the book was just opened
     * @param page     the page shown now
     * @param jump     the reader jumped (TOC, search, link, another device) instead of paging
     * @param maxStep  the most pages one turn moves (more is a jump)
     * @param chapters the study chapters, front matter included
     */
    public static List<Card> next(int prevPage, int page, boolean jump, int maxStep,
                                  List<BookChapters.Range> chapters, StudyBook book) {
        final List<Card> cards = new ArrayList<>();
        if (book == null || !book.on || !book.hasPreview()) {
            return cards;
        }
        final int now = StudyChapters.indexAt(chapters, page);
        if (now < 0) {
            return cards;
        }
        final int step = page - prevPage;
        if (prevPage < 0 || jump || step > maxStep) {
            addBefore(cards, chapters.get(now), book);
            return cards;
        }
        if (step <= 0) {
            // paging back: the reader is looking at something again, nothing to say
            return cards;
        }
        final int was = StudyChapters.indexAt(chapters, prevPage);
        if (was < 0 || now <= was) {
            return cards;
        }
        final BookChapters.Range left = chapters.get(was);
        final StudyBook.Chapter done = StudyChapters.isOutside(left) ? null : book.chapter(left.key);
        if (done != null && !StudyBook.SKIP.equals(done.label) && done.afterShown == 0 && done.checked == 0) {
            cards.add(new Card(AFTER, left.key));
        }
        addBefore(cards, chapters.get(now), book);
        return cards;
    }

    private static void addBefore(List<Card> cards, BookChapters.Range range, StudyBook book) {
        final StudyBook.Chapter c = StudyChapters.isOutside(range) ? null : book.chapter(range.key);
        if (c != null && c.beforeShown == 0 && c.checked == 0) {
            cards.add(new Card(BEFORE, range.key));
        }
    }

    /** Whether the reader comes back after long enough to be offered the map first. */
    public static boolean welcomeBack(long lastReadMs, long nowMs) {
        return lastReadMs > 0 && nowMs - lastReadMs >= StudyRules.WELCOME_DAYS * DAY_MS;
    }

    /** Whole days between two times. */
    public static int daysAway(long lastReadMs, long nowMs) {
        return (int) Math.max(0, (nowMs - lastReadMs) / DAY_MS);
    }
}
