package com.foobnix.assist.review;

import com.foobnix.assist.useit.UseDates;

/**
 * When an idea comes back (canvas "Comes back: Today, in 3 days, 8 days, 3 weeks; gaps grow as it
 * sticks"). A right answer moves it up the ladder 3 days, 8 days, 3 weeks, 6 weeks, 12 weeks (2 days
 * for an idea whose strength fell to nothing) and adds one to its strength; a wrong one brings it back tomorrow and takes one off. "Today" is
 * always passed in; nothing here reads a clock, and nothing is ever scheduled outside the app
 * (the owner's rule): a due day is only compared with today when a screen opens.
 */
public final class ReviewSchedule {

    /** The longest wait: 12 weeks. */
    public static final int MAX_GAP = 84;
    /** Days until an idea missed or half remembered in blank-page recall is asked. */
    public static final int FIRST_GAP = 1;
    /** Days until an idea remembered in blank-page recall is asked: the longer first gap. */
    public static final int REMEMBERED_GAP = 8;

    private ReviewSchedule() {
    }

    /**
     * The days to wait after a right answer given at {@code strength} (before the answer), when
     * the wait before it was {@code lastGap} days.
     */
    public static int gapAfterRight(int strength, int lastGap) {
        switch (ReviewItem.strength(strength)) {
            case 0:
                return 2;
            case 1:
                return 3;
            case 2:
                return 8;
            case 3:
                return 21;
            case 4:
                return 42;
            default:
                return Math.min(MAX_GAP, Math.max(42, lastGap) * 2);
        }
    }

    /**
     * Records the reader's first answer to an idea's card on {@code today} and moves the idea.
     * An answer to an idea that is not due yet ("Review it now") is kept in its history; a right
     * one then leaves its day and strength alone, so looking early cannot be used to climb.
     */
    public static void answer(ReviewItem item, boolean right, String today) {
        if (item == null || !UseDates.isDay(today)) {
            return;
        }
        final boolean early = UseDates.isDay(item.due) && !UseDates.reached(today, item.due);
        if (right) {
            if (early) {
                item.add(new ReviewItem.Past(today, ReviewItem.REVIEW, true, 0));
                return;
            }
            item.gap = gapAfterRight(item.strength, item.gap);
            item.strength = ReviewItem.strength(item.strength + 1);
            if (!ReviewItem.NOTE.equals(item.reason)) {
                item.reason = ReviewItem.FADING;
            }
        } else {
            item.strength = ReviewItem.strength(item.strength - 1);
            item.gap = FIRST_GAP;
        }
        item.due = UseDates.addDays(today, item.gap);
        item.add(new ReviewItem.Past(today, ReviewItem.REVIEW, right, item.gap));
    }

    /**
     * The waits ahead of an idea as the "Comes back" row shows them: the one it has now, then
     * {@code more} further ones if each answer is right.
     */
    public static int[] ahead(ReviewItem item, int more) {
        final int[] out = new int[Math.max(0, more) + 1];
        int strength = item.strength;
        int gap = Math.max(FIRST_GAP, item.gap);
        out[0] = gap;
        for (int i = 1; i < out.length; i++) {
            gap = gapAfterRight(strength, gap);
            strength = ReviewItem.strength(strength + 1);
            out[i] = gap;
        }
        return out;
    }
}
