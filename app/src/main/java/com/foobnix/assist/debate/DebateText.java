package com.foobnix.assist.debate;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.xray.XrayIdea;

import java.io.Closeable;
import java.util.List;

/**
 * Where the author's quotes are looked for: the marked passages of the chapter's ideas first (no
 * book needed), then the book's own pages, the chapter first ({@link DebateQuotes#scan}). The
 * book is opened only when a quote is not among the passages. Off the main thread.
 */
final class DebateText implements DebateQuotes.Finder, DebateQuotes.Pages, Closeable {

    /** Stops the search when the screen is gone. */
    interface Stop {
        void check() throws AiJobs.Cancelled;
    }

    private final AiJobs.Target target;
    private final String chapterKey;
    private final String chapterTitle;
    private final List<XrayIdea> ideas;
    private final Stop stop;

    private BookText text;
    private boolean failed;
    private List<BookChapters.Entry> toc;
    private int from;
    private int to;

    DebateText(AiJobs.Target target, String chapterKey, String chapterTitle, List<XrayIdea> ideas, Stop stop) {
        this.target = target;
        this.chapterKey = chapterKey;
        this.chapterTitle = chapterTitle;
        this.ideas = ideas;
        this.stop = stop;
    }

    @Override
    public DebateQuotes.Found find(String quote) throws AiJobs.Cancelled {
        final DebateQuotes.Found marked = DebateQuotes.inIdeas(ideas, quote);
        if (marked != null || QuoteMatcher.key(quote) == null) {
            return marked;
        }
        try {
            if (!open()) {
                return null;
            }
            return DebateQuotes.scan(this, from, to, DebateQuotes.MAX_OTHER_PAGES, quote);
        } catch (AiJobs.Cancelled e) {
            throw e;
        } catch (Exception e) {
            // the book cannot be read: the quote stays unchecked, so it is shown without marks
            LOG.e(e);
            return null;
        }
    }

    private boolean open() {
        if (text != null || failed) {
            return text != null;
        }
        text = BookText.open(target.path, target.width, target.height, target.fontSize);
        if (text == null) {
            failed = true;
            return false;
        }
        toc = AiJobs.toc(target, text);
        BookChapters.Range range = null;
        for (BookChapters.Range r : BookChapters.chaptersOrBlocks(toc, text.pageCount(), BookChapters.BLOCK_PAGES)) {
            if (chapterKey.equals(r.key)) {
                range = r;
                break;
            }
        }
        if (range == null) {
            range = BookChapters.chapterAt(toc, target.page, text.pageCount(), BookChapters.BLOCK_PAGES);
        }
        from = range.from;
        to = range.to;
        return true;
    }

    /** The title of the chapter a found quote is in: the debate's chapter, or the one holding {@code page}. */
    String titleAt(int page) {
        if (text == null || page < 0 || (page >= from && page < to)) {
            return chapterTitle;
        }
        final BookChapters.Range r = BookChapters.chapterAt(toc, page, text.pageCount(), BookChapters.BLOCK_PAGES);
        return r == null || r.title == null ? "" : r.title;
    }

    @Override
    public int pageCount() {
        return text == null ? 0 : text.pageCount();
    }

    @Override
    public List<String> words(int page) throws AiJobs.Cancelled {
        stop.check();
        return BookText.words(text.text(page));
    }

    @Override
    public void close() {
        if (text != null) {
            text.close();
            text = null;
        }
    }
}
