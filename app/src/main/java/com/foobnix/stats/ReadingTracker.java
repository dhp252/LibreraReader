package com.foobnix.stats;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.dao2.FileMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.tts.TTSEngine;
import com.foobnix.webdav.SyncEvents;

/**
 * Logs what the reader does with the open book ({@link StatsLog}): opening it, how long each
 * page stays on screen (dialogs and time off screen not counted), jumps, coming back and leaving.
 * One book at a time, owned by its reader activity; a re-created activity (rotation) takes the
 * tracker over instead of opening the book again.
 */
public final class ReadingTracker {

    private static final long TICK_MS = 1000;
    /** A move of more pages than this at once is a jump, not a page turn (two-page view turns two). */
    private static final int TURN_MAX = 2;

    private static ReadingTracker current;

    private Activity owner;
    private DocumentController dc;
    private final String path;
    private final String book;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private int page = -1;
    private int pages;
    /** Time the page was on screen without a dialog over it. */
    private long shownMs;
    private long lastTick;
    private boolean onScreen;
    private boolean byTts;
    private boolean byAutoScroll;
    private String chapter;
    private String jumpVia;

    private ReadingTracker(Activity owner, DocumentController dc, String path) {
        this.owner = owner;
        this.dc = dc;
        this.path = path;
        this.book = ExtUtils.getFileName(path);
    }

    /** The reader shows a page (called on every page change and UI update of both view modes). */
    public static void pageShown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0) {
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || !current.path.equals(path)) {
                if (current != null) {
                    current.close();
                }
                current = new ReadingTracker(dc.getActivity(), dc, path);
                current.open();
            } else if (current.dc != dc) {
                // the same book in a re-created reader: its pause went to the old one
                current.owner = dc.getActivity();
                current.dc = dc;
                if (!current.onScreen) {
                    current.log(StatsEvent.RESUME);
                    current.startTicking();
                }
            }
            current.onPage(dc.getCurentPageFirst1(), dc.getPageCount());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public static void resumed(Activity owner) {
        if (current != null && current.owner == owner && !current.onScreen) {
            current.log(StatsEvent.RESUME);
            current.startTicking();
        }
    }

    public static void paused(Activity owner) {
        if (current != null && current.owner == owner && current.onScreen) {
            current.tick();
            current.stopTicking();
            current.log(StatsEvent.PAUSE);
        }
    }

    /** The reader is gone; a re-created one (rotation) keeps the book open. */
    public static void destroyed(Activity owner) {
        if (current != null && current.owner == owner && owner.isFinishing()) {
            current.close();
            current = null;
        }
    }

    /** A highlight, note, bookmark, search or lookup in the open book. */
    public static void count(String type) {
        if (current != null) {
            current.log(type);
        }
    }

    /** The next jump is made to follow another device: not the reader going back. */
    public static void nextJumpVia(String via) {
        if (current != null) {
            current.jumpVia = via;
        }
    }

    private void open() {
        final StatsEvent e = event(StatsEvent.OPEN);
        try {
            final FileMeta meta = dc.getBookFileMeta();
            if (meta != null) {
                e.title = meta.getTitle();
                e.author = meta.getAuthor();
                e.lang = meta.getLang();
            }
        } catch (Exception ex) {
            LOG.e(ex);
        }
        e.ext = ExtUtils.getFileExtension(path);
        e.pages = dc.getPageCount();
        e.percent = dc.getPercentage();
        StatsLog.log(e);
        WordCounter.start(dc.getActivity(), path, dc.getBookWidth(), dc.getBookHeight(), BookCSS.get().fontSizeSp,
                realPageCount(dc), e.lang);
        StatsLog.refresh(null);
        startTicking();
    }

    private void close() {
        stopTicking();
        flushPage();
        final StatsEvent e = event(StatsEvent.CLOSE);
        e.percent = pages > 0 ? Math.min(1f, page / (float) pages) : -1;
        StatsLog.log(e);
        StatsLog.refresh(SyncEvents::statsWritten);
    }

    private void onPage(int p, int n) {
        if (pages > 0 && n != pages) {
            // laid out anew (font size, screen size): the counts of the old pages do not fit
            WordCounter.start(dc.getActivity(), path, dc.getBookWidth(), dc.getBookHeight(), BookCSS.get().fontSizeSp,
                    realPageCount(dc), null);
        }
        if (p == page) {
            pages = n;
            return;
        }
        flushPage();
        if (page > 0 && Math.abs(p - page) > TURN_MAX) {
            final StatsEvent jump = event(StatsEvent.JUMP);
            jump.from = page;
            jump.to = p;
            jump.via = jumpVia;
            StatsLog.log(jump);
        }
        jumpVia = null;
        page = p;
        pages = n;
        shownMs = 0;
        lastTick = System.currentTimeMillis();
        byTts = false;
        byAutoScroll = false;
        try {
            chapter = dc.getCurrentChapter();
        } catch (Exception e) {
            chapter = null;
        }
    }

    private void flushPage() {
        if (page <= 0) {
            return;
        }
        if (onScreen) {
            tick();
        }
        final StatsEvent e = event(StatsEvent.PAGE);
        e.page = page;
        e.pages = pages;
        e.words = viewWords(page);
        e.raw = shownMs;
        e.chapter = chapter == null || chapter.trim().isEmpty() ? null : chapter.trim();
        e.mode = byTts ? StatsEvent.MODE_TTS : byAutoScroll ? StatsEvent.MODE_AUTO : StatsEvent.MODE_READ;
        StatsLog.log(e);
        shownMs = 0;
    }

    private void tick() {
        final long now = System.currentTimeMillis();
        if (onScreen && !dc.isVisibleDialog()) {
            shownMs += Math.max(0, now - lastTick);
        }
        lastTick = now;
        if (TTSEngine.get().isPlaying()) {
            byTts = true;
        }
        if (AppState.get().isAutoScroll) {
            byAutoScroll = true;
        }
    }

    private void startTicking() {
        onScreen = true;
        lastTick = System.currentTimeMillis();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
    }

    private void stopTicking() {
        onScreen = false;
        handler.removeCallbacks(ticker);
    }

    private void log(String type) {
        final StatsEvent e = event(type);
        if (page > 0) {
            e.page = page;
        }
        StatsLog.log(e);
    }

    private StatsEvent event(String type) {
        return StatsLog.event(type, book);
    }

    // ---------------------------------------------------------------- pages of the view vs the book

    /** Book mode shows two pages, or half a page, per view page in these modes. */
    private static boolean isDouble(DocumentController dc) {
        return dc.isBookMode() && AppSP.get().isDouble;
    }

    private static boolean isCut(DocumentController dc) {
        return dc.isBookMode() && AppSP.get().isCut;
    }

    /** The book's own page count (the view's differs in two-page and half-page view). */
    static int realPageCount(DocumentController dc) {
        if ((isDouble(dc) || isCut(dc)) && dc.getCodecDocument() != null) {
            return dc.getCodecDocument().getPageCount();
        }
        return dc.getPageCount();
    }

    /** The book's first and last page (1-based) shown on this view page. */
    static int[] realPages(DocumentController dc, int view) {
        if (isCut(dc)) {
            final int real = (view + 1) / 2;
            return new int[]{real, real};
        }
        if (isDouble(dc)) {
            if (AppSP.get().isDoubleCoverAlone) {
                return view == 1 ? new int[]{1, 1} : new int[]{2 * view - 2, 2 * view - 1};
            }
            return new int[]{2 * view - 1, 2 * view};
        }
        return new int[]{view, view};
    }

    private int viewWords(int view) {
        final int[] real = realPages(dc, view);
        int total = 0;
        for (int p = real[0]; p <= real[1]; p++) {
            final int w = WordCounter.words(path, p);
            if (w < 0) {
                return -1;
            }
            total += w;
        }
        return isCut(dc) ? total / 2 : total;
    }

    /** Words from the view page shown to the end of view page {@code lastView}, -1 when not known. */
    public static long wordsLeft(DocumentController dc, int lastView) {
        final String path = dc.getCurrentBook().getPath();
        final int from = realPages(dc, dc.getCurentPageFirst1())[0];
        final int to = realPages(dc, Math.max(dc.getCurentPageFirst1(), lastView))[1];
        return WordCounter.wordsBetween(path, from, to);
    }
}
