package com.foobnix.assist.readsyou;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the page turns of one reading session say (Assist.READS_YOU), without the camera. It is
 * fed the page shown ({@link #page}) and the passing time ({@link #tick}) and works out:
 * <ul>
 * <li>re-reads: going back to an earlier page and forward again within a short time;</li>
 * <li>long dwells: a page taking much longer than the reader's own median pace for its words;</li>
 * <li>the unbroken session's length (a pause of {@link ReadsRules#BREAK_MS} starts a new one);</li>
 * <li>the slowdown: the session's latest pages against its first ones.</li>
 * </ul>
 * It knows pages, not lines: a re-read is a re-read of a page. Times are any steady clock in ms.
 * Every threshold is in {@link ReadsRules}. No Android here.
 */
public final class ReadsModel {

    /** What the strip says. */
    public enum State {
        /** Not enough pages yet to know the reader's pace. */
        LEARNING, STEADY, REREAD, LONG_PAGE, SLOWING
    }

    /** Why help may be offered on the page shown. */
    public enum Help {NONE, REREAD, DWELL}

    /** One finished stay on a page, to be added to the book's stored signals. */
    public static final class Visit {
        /** The view page (1-based) and the caller's tag for it (the book's own 0-based page). */
        public final int page;
        public final int tag;
        public final long ms;
        /** Words on the page, -1 when not known. */
        public final int words;
        public boolean reread;
        public boolean dwell;

        Visit(int page, int tag, long ms, int words) {
            this.page = page;
            this.tag = tag;
            this.ms = ms;
            this.words = words;
        }
    }

    /** The pace from earlier sessions (or the reading stats), used until this one has its own. */
    private double storedPace;
    /** The latest valid pages of this run, for the median pace. */
    private final List<Double> recent = new ArrayList<>();
    /** The valid pages of the unbroken session, in reading order. */
    private final List<Double> session = new ArrayList<>();
    private long sessionMs;
    private long idleMs;
    private long lastTick = -1;

    private int page = -1;
    private int tag = -1;
    private int words = -1;
    private long pageMs;

    /** The page the reader paged back from, -1 when not paging back; and when. */
    private int backFrom = -1;
    private long backAt;
    /** Pages help was offered on in this session: once each. */
    private final Set<Integer> offered = new HashSet<>();
    private final List<Visit> closed = new ArrayList<>();

    /** @param storedPace ms per word from earlier reading of this book, 0 when unknown */
    public ReadsModel(double storedPace) {
        this.storedPace = Math.max(0, storedPace);
    }

    /** The pace kept from earlier reading of this book became known (it is read from the book's file). */
    public void stored(double pace) {
        if (pace > 0) {
            storedPace = pace;
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * Time passed. Call about once a second, and right before {@link #page}.
     *
     * @param active the page is on screen and the reader reads it by hand: no dialog over it, the
     *               reader in front, no read-aloud or auto-scroll turning the pages
     */
    public void tick(long now, boolean active) {
        if (lastTick < 0) {
            lastTick = now;
            return;
        }
        final long dt = now - lastTick;
        lastTick = now;
        if (dt <= 0) {
            return;
        }
        final boolean reading = active && dt <= ReadsRules.TICK_GAP_MS && page >= 0
                && pageMs < ReadsRules.awayLimit(words, pace());
        if (reading) {
            pageMs += dt;
            sessionMs += dt;
            idleMs = 0;
        } else {
            idleMs += dt;
            if (idleMs >= ReadsRules.BREAK_MS) {
                rest();
            }
        }
    }

    /**
     * The reader shows a page.
     *
     * @param p     the view page, 1-based
     * @param tag   kept with the page's {@link Visit} (the book's own page)
     * @param words words on it, -1 when not counted yet
     */
    public void page(long now, int p, int tag, int words) {
        if (p == page) {
            this.tag = tag;
            if (words >= 0) {
                this.words = words;
            }
            return;
        }
        final int before = page;
        close(now, p);
        page = p;
        this.tag = tag;
        this.words = words;
        pageMs = 0;
        if (before < 0) {
            return;
        }
        if (p < before) {
            if (backFrom < 0 && before - p <= ReadsRules.BACK_MAX_PAGES) {
                backFrom = before;
                backAt = now;
            } else if (backFrom < 0 || backFrom - p > 2 * ReadsRules.BACK_MAX_PAGES) {
                // a jump back, not paging back
                backFrom = -1;
            }
        }
        if (backFrom >= 0 && (p >= backFrom || now - backAt > ReadsRules.REREAD_WINDOW_MS)) {
            backFrom = -1;
        }
    }

    /** The words of the page shown became known. */
    public void words(int words) {
        if (words >= 0) {
            this.words = words;
        }
    }

    public boolean wordsKnown() {
        return words >= 0;
    }

    private void close(long now, int next) {
        if (page < 0) {
            return;
        }
        final Visit v = new Visit(page, tag, pageMs, words);
        final double pace = pace();
        final boolean left = pageMs >= ReadsRules.awayLimit(words, pace);
        final boolean back = backFrom >= 0 && page < backFrom;
        v.dwell = ReadsRules.isDwell(pageMs, words, pace);
        v.reread = back && next > page && pageMs >= ReadsRules.REREAD_MIN_MS
                && now - backAt <= ReadsRules.REREAD_WINDOW_MS;
        final boolean turned = next > page && next - page <= ReadsRules.TURN_MAX;
        if (!back && turned && !left && ReadsRules.isSample(pageMs, words)) {
            final double sample = (double) pageMs / words;
            session.add(sample);
            recent.add(sample);
            while (recent.size() > ReadsRules.PACE_KEEP) {
                recent.remove(0);
            }
        }
        if (pageMs > 0 || v.reread) {
            closed.add(v);
        }
    }

    /** A pause: the unbroken session starts again. The pace learned so far stays. */
    private void rest() {
        if (recent.size() >= ReadsRules.PACE_MIN_SAMPLES) {
            storedPace = ReadsRules.median(recent);
        }
        sessionMs = 0;
        session.clear();
    }

    /** The reader took the break the card offered. */
    public void breakTaken() {
        rest();
        idleMs = 0;
    }

    /** The book was laid out anew (font, screen): page numbers before do not fit the ones after. */
    public void relaid(long now) {
        end(now);
        offered.clear();
    }

    /** Closes the stay on the page shown (the book is closing, or the pages changed under it). */
    public void end(long now) {
        if (page >= 0 && pageMs > 0) {
            final Visit v = new Visit(page, tag, pageMs, words);
            v.dwell = ReadsRules.isDwell(pageMs, words, pace());
            closed.add(v);
        }
        page = -1;
        tag = -1;
        words = -1;
        pageMs = 0;
        backFrom = -1;
    }

    // ------------------------------------------------------------------ what it says

    /** The reader's pace, ms per word: this run's median once it has enough pages, else the stored one; 0 unknown. */
    public double pace() {
        return recent.size() >= ReadsRules.PACE_MIN_SAMPLES ? ReadsRules.median(recent) : storedPace;
    }

    /** Whether {@link #pace} comes from enough pages of this run to be kept. */
    public boolean paceLearned() {
        return recent.size() >= ReadsRules.PACE_MIN_SAMPLES;
    }

    public int paceSamples() {
        return recent.size();
    }

    /** Reading time of the unbroken session, ms. */
    public long sessionMs() {
        return sessionMs;
    }

    /** Time on the page shown, ms. */
    public long pageMs() {
        return pageMs;
    }

    public int page() {
        return page;
    }

    public int tag() {
        return tag;
    }

    /** See {@link ReadsRules#slowdown}. */
    public double slowdown() {
        return ReadsRules.slowdown(session);
    }

    /** Back on an earlier page, long enough to be reading it again. */
    public boolean rereading(long now) {
        return backFrom >= 0 && page >= 0 && page < backFrom && pageMs >= ReadsRules.REREAD_MIN_MS
                && now - backAt <= ReadsRules.REREAD_WINDOW_MS;
    }

    /** The page shown is taking much longer than the reader's pace for its words. */
    public boolean longPage() {
        return page >= 0 && ReadsRules.isDwell(pageMs, words, pace());
    }

    public State state(long now) {
        if (rereading(now)) {
            return State.REREAD;
        }
        if (longPage()) {
            return State.LONG_PAGE;
        }
        if (pace() <= 0) {
            return State.LEARNING;
        }
        return ReadsRules.isSlowdown(slowdown()) ? State.SLOWING : State.STEADY;
    }

    /** Why help may be offered on the page shown now; NONE when it was already offered there. */
    public Help help(long now) {
        if (page < 0 || offered.contains(page)) {
            return Help.NONE;
        }
        if (rereading(now) && pageMs >= ReadsRules.REREAD_OFFER_MS) {
            return Help.REREAD;
        }
        return longPage() ? Help.DWELL : Help.NONE;
    }

    /** The offer was shown (or opened by hand): not again on this page in this session. */
    public void helpOffered() {
        if (page >= 0) {
            offered.add(page);
        }
    }

    /** See {@link ReadsRules#wantsBreak}. */
    public boolean wantsBreak(long wallNow, long snoozeUntil) {
        return ReadsRules.wantsBreak(sessionMs, slowdown(), wallNow, snoozeUntil);
    }

    /** The finished stays since the last call. */
    public List<Visit> drain() {
        final List<Visit> out = new ArrayList<>(closed);
        closed.clear();
        return out;
    }
}
