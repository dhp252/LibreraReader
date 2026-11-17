package com.foobnix.assist.skeleton;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;
import com.foobnix.study.SkimPack;

import java.util.List;

/**
 * Makes the skeleton of a book: its text read on the device, a skim of it (TOC, chapter openings
 * and endings, word counts: {@link SkimPack}) sent in one request, the answer kept in
 * {@link AssistStore} under {@link SkeletonData#KEY}. It reads the whole book, so it runs as a long
 * AI task (F-004 D-151, {@code AiTask.SKELETON}) and goes on with the screen or the book closed.
 */
public final class SkeletonJob extends AiJobs.Job {

    /** Room the prompt around the skim needs in a request. */
    private static final int PROMPT_ROOM = 4000;
    /** The skim is kept short: openings and endings say enough for one line each. */
    static final int MAX_SKIM = 150_000;
    static final int MIN_SKIM = 8000;

    /** What the screen shows while the skeleton is made; called on the main thread. */
    public interface Screen {
        void onStatus(String status);
    }

    private volatile Screen screen;
    private volatile String status = "";

    public SkeletonJob(Context context, AiJobs.Target target) {
        super(context, target);
    }

    @Override
    protected void setTaskScreen(Object screen) {
        this.screen = screen instanceof Screen ? (Screen) screen : null;
        if (this.screen != null && !status.isEmpty()) {
            status(status);
        }
    }

    @Override
    protected void showStatus(String text) {
        status(text);
    }

    private void status(String text) {
        status = text;
        step(text, 0, 0);
        ui(() -> {
            final Screen s = screen;
            if (s != null) {
                s.onStatus(text);
            }
        });
    }

    @Override
    protected void failed(String message) {
        // a task: AiProgress tells the screen how it ended, the notification says it otherwise
    }

    @Override
    protected void work() throws Exception {
        status(string(R.string.ai_reading_book));
        try (BookText text = open()) {
            final int pages = text.pageCount();
            final List<BookChapters.Entry> toc = AiJobs.toc(target, text);
            final String[] plain = new String[pages];
            final int[] lengths = new int[pages];
            read(text, 0, pages, plain, lengths, this::status);
            if (BookChapters.length(lengths, 0, pages) == 0) {
                failWith(string(R.string.canvas_t13_no_text));
                return;
            }
            final List<SkeletonParts.Part> parts = SkeletonParts.group(toc, pages);
            final List<BookChapters.Range> chapters = SkeletonParts.chapters(parts);
            if (chapters.isEmpty()) {
                failWith(string(R.string.canvas_t13_no_text));
                return;
            }
            final int budget = Math.max(MIN_SKIM, Math.min(MAX_SKIM, settings.maxChars - PROMPT_ROOM));
            final String pack = SkimPack.build(chapters, plain, budget);

            checkCancelled();
            status(string(R.string.canvas_t13_skimming));
            final String answer = ask(AiPrompts.withMaster(settings.masterPrompt,
                            SkeletonPrompts.system(settings.answerLanguage, SkeletonParts.hasGroups(parts))),
                    SkeletonPrompts.user(target.title, parts, chapters, pack), null);
            final SkeletonPrompts.Answer parsed = SkeletonPrompts.parse(answer);
            if (parsed.isEmpty()) {
                failWith(string(wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t13_unreadable));
                return;
            }
            checkCancelled();
            final SkeletonData data = SkeletonData.fromAnswer(parsed, parts, chapters, lengths, settings.model,
                    System.currentTimeMillis());
            AssistStore.get().put(target.name, SkeletonData.KEY, data.toJson());
            result = string(R.string.canvas_t13_ready);
        }
    }
}
