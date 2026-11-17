package com.foobnix.assist.fiction;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingTracker;

/**
 * The open book for the fiction companion: the reader's place in the book's own pages (the view's
 * pages differ in two-page and half-page view) and the book's chapters in the reader's layout.
 */
final class FictionBook {

    private static String chaptersKey;
    private static FictionChapters chapters;

    private FictionBook() {
    }

    /** The book page (0-based) the reader shows: the first of two in two-page view. */
    static int readerPage(DocumentController dc) {
        try {
            return ReadingTracker.realPages(dc, dc.getCurentPageFirst1())[0] - 1;
        } catch (Exception e) {
            LOG.e(e);
            return -1;
        }
    }

    /** The book's own page count in the reader's layout. */
    static int readerPages(DocumentController dc) {
        try {
            return ReadingTracker.realPageCount(dc);
        } catch (Exception e) {
            LOG.e(e);
            return 0;
        }
    }

    /** The reader's place for a long task: all it may read ({@link FictionShield#ticket}). */
    static com.foobnix.ai.BookChapters.Range ticket(DocumentController dc) {
        return FictionShield.ticket(readerPage(dc), readerPages(dc));
    }

    /**
     * The chapters of the book in the layout of {@code t}; kept for the layout read last. Off the
     * main thread: it opens the book. Null when the book cannot be opened.
     */
    static FictionChapters chapters(AiJobs.Target t) {
        final String key = t.path + "|" + t.width + "x" + t.height + "|" + t.fontSize;
        synchronized (FictionBook.class) {
            if (key.equals(chaptersKey) && chapters != null) {
                return chapters;
            }
        }
        try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
            if (text == null) {
                return null;
            }
            final FictionChapters found = FictionChapters.of(AiJobs.toc(t, text), text.pageCount());
            synchronized (FictionBook.class) {
                chaptersKey = key;
                chapters = found;
            }
            return found;
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    /** The key of the book's who's who task. */
    static String castTask(String bookName) {
        return AiTask.key(bookName, AiTask.FICTION_CAST);
    }
}
