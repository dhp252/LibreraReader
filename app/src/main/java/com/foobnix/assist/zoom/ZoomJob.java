package com.foobnix.assist.zoom;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The one request of Semantic zoom for a chapter: its text in, the Lean, Claims and 1 line levels
 * out ({@link ZoomPrompts}, {@link ZoomData}), tied to the chapter's own words and stored with the
 * book's assist data. Sent only when the reader picks a level in the zoom view (Assist.ZOOM on); a
 * screen job, cancelled with the view.
 */
final class ZoomJob extends AiJobs.Job {

    /** Characters of a chapter sent at most; a longer chapter is condensed in its first part. */
    static final int MAX_CHARS = 40_000;
    /** Less text than this is not worth condensing. */
    static final int MIN_CHARS = 600;
    /** The screen hears of the answer growing every so many characters. */
    private static final int PROGRESS_STEP = 400;

    /** What the view hears, on the main thread. */
    interface Screen {
        /** @param chars characters of the answer so far */
        void onProgress(int chars);

        void onDone(ZoomData data);

        void onError(String message);

        void onNeedSettings();
    }

    /** Lets a long read stop between pages. */
    interface Stop {
        void check() throws AiJobs.Cancelled;
    }

    private final BookChapters.Range range;
    private final Screen screen;
    /** The reader's goal for the book, or null; {@code bookGoal} is the key passages' goal its words may follow. */
    private final ZoomGoal goal;
    private final String bookGoal;
    private int received;
    private int told;

    ZoomJob(Activity activity, AiJobs.Target target, BookChapters.Range range, ZoomGoal goal, String bookGoal, Screen screen) {
        super(activity, target);
        this.range = range;
        this.goal = goal;
        this.bookGoal = bookGoal;
        this.screen = screen;
    }

    /** Characters of a chapter sent at most with these settings. */
    static int maxChars(com.foobnix.ai.AiSettings settings) {
        return Math.min(MAX_CHARS, Math.max(MIN_CHARS, settings.maxChars - 4000));
    }

    /**
     * The chapter's text from its first page on, up to {@code max} characters: what the request
     * sends, and what the author's words are found in again later.
     */
    static ZoomText read(BookText text, BookChapters.Range range, int max, Stop stop) throws AiJobs.Cancelled {
        final List<String> pages = new ArrayList<>();
        final int from = Math.max(0, range.from);
        final int to = Math.min(text.pageCount(), range.to);
        int chars = 0;
        for (int p = from; p < to && chars < max; p++) {
            stop.check();
            final String s = BookText.plain(text.body(p));
            pages.add(s);
            chars += s.length() + 2;
        }
        return ZoomText.of(pages);
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        // an answer an outside Claude wrote for the whole chapter needs no API (com.foobnix.claude)
        final boolean claude = claudeWaiting(com.foobnix.claude.ClaudeFeature.ZOOM, range.key);
        if (!settings.isReady() && !claude) {
            ui(screen::onNeedSettings);
            return;
        }
        final int chapterPages;
        final ZoomText chapter;
        try (BookText text = open()) {
            chapterPages = Math.max(1, Math.min(text.pageCount(), range.to) - Math.max(0, range.from));
            chapter = read(text, range, claude ? Integer.MAX_VALUE : maxChars(settings), this::checkCancelled);
        }
        if (chapter.chars() < MIN_CHARS) {
            failWith(string(R.string.zoom_too_short));
            return;
        }
        final int pagesRead = Math.max(1, chapter.pageCount());
        final boolean partial = pagesRead < chapterPages;
        final String system = AiPrompts.withMaster(settings.masterPrompt,
                ZoomPrompts.system(settings.answerLanguage, goal != null));
        final String answer = askFor(com.foobnix.claude.ClaudeFeature.ZOOM, range.key, system, ZoomPrompts.user(target.title, chapterTitle(range), chapter.plain(), partial,
                goal == null ? null : goal.prompt(bookGoal)), delta -> progress(delta == null ? 0 : delta.length()));
        checkCancelled();
        final ZoomData data = ZoomData.parse(answer);
        if (data == null) {
            failWith(string(wasCutOff() ? R.string.zoom_cut_off : R.string.zoom_unreadable));
            return;
        }
        data.place(chapter, chapterPages);
        data.title = chapterTitle(range);
        data.chars = chapter.chars();
        data.fullChars = partial ? (long) chapter.chars() * chapterPages / pagesRead : chapter.chars();
        data.model = settings.model;
        data.goal = goal == null ? "" : goal.id(bookGoal);
        if (goal == null) {
            data.kept.clear();
            data.squeezed.clear();
        }
        ZoomStore.save(target.name, range.key, data);
        ui(() -> screen.onDone(data));
    }

    private void progress(int more) {
        received += more;
        if (received - told >= PROGRESS_STEP) {
            told = received;
            final int now = received;
            ui(() -> screen.onProgress(now));
        }
    }
}
