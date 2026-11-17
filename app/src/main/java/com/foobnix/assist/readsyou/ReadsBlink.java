package com.foobnix.assist.readsyou;

import java.util.ArrayDeque;

/**
 * The reader's blink rate against their own baseline of this session, from Eye mode's "eyes
 * closed" result of each frame: a blink is an open frame followed by a closed one while the
 * camera sees the reader. Frames are thinned to one in {@link ReadsRules#BLINK_FRAME_MS} so the
 * camera's two frame rates count alike. The baseline is the blinks in the first
 * {@link ReadsRules#BLINK_WINDOW_MS} it watched; "blinking more" compares the last such window
 * with it, once the two do not overlap. At the camera's low frame rate many blinks are missed,
 * the same way in both windows: this is a comparison, not a count of blinks. No Android here.
 */
public final class ReadsBlink {

    private long last = -1;
    private boolean lastClosed;
    /** Time the camera watched open or closed eyes, ms. */
    private long watched;
    private int baseline;
    /** The blinks of the last window, by the watched time they happened at. */
    private final ArrayDeque<Long> recent = new ArrayDeque<>();

    /**
     * One frame.
     *
     * @param sees   the camera sees the reader looking at the screen (Eye mode's SEES)
     * @param closed Eye mode's "eyes closed" for the frame
     */
    public void frame(long t, boolean sees, boolean closed) {
        if (!sees) {
            last = -1;
            return;
        }
        if (last >= 0 && t - last < ReadsRules.BLINK_FRAME_MS) {
            return;
        }
        if (last >= 0 && t - last <= ReadsRules.BLINK_GAP_MS) {
            watched += t - last;
            if (closed && !lastClosed) {
                if (watched <= ReadsRules.BLINK_WINDOW_MS) {
                    baseline++;
                }
                recent.add(watched);
            }
        }
        while (!recent.isEmpty() && recent.peekFirst() <= watched - ReadsRules.BLINK_WINDOW_MS) {
            recent.pollFirst();
        }
        last = t;
        lastClosed = closed;
    }

    /** Time the camera watched the reader's eyes, ms. */
    public long watchedMs() {
        return watched;
    }

    /** Blinks seen in the baseline window. */
    public int baseline() {
        return baseline;
    }

    /** Blinks seen in the last window. */
    public int recent() {
        return recent.size();
    }

    /** Whether the last window can be compared with the baseline: both full, apart, and the baseline not too thin. */
    public boolean comparable() {
        return watched >= 2 * ReadsRules.BLINK_WINDOW_MS && baseline >= ReadsRules.BLINK_MIN;
    }

    /** Whether the reader blinks clearly more now than at the start of the session. */
    public boolean more() {
        return comparable() && recent.size() >= ReadsRules.BLINK_MORE * baseline;
    }

    /** A break was taken: the session, and with it the baseline, starts again. */
    public void reset() {
        last = -1;
        lastClosed = false;
        watched = 0;
        baseline = 0;
        recent.clear();
    }
}
