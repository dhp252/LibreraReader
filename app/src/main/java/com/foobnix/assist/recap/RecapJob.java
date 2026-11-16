package com.foobnix.assist.recap;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;

import org.json.JSONObject;

import java.util.List;

/**
 * The one recap request of a book's opening: reads only the pages before the reader's (the ones
 * right before it whole, then chapter openings, within {@link RecapLogic#budget}), asks the model
 * once, and keeps the answer in AssistStore ({@code recap:<bucket>}, with the page and the day).
 * Run as a job without a screen of its own, so it outlives a rotation; the open recap sheet gets
 * the result through {@link Screen}.
 */
final class RecapJob extends AiJobs.Job {

    /** Called on the main thread. */
    interface Screen {
        void onRecap(RecapPrompts.Recap recap);

        void onError(String message);

        void onNeedSettings();
    }

    /** Pages of a chapter opening read at most (a chapter starting with picture pages). */
    private static final int OPENING_PAGES = 6;

    private final String bucket;
    private final int days;
    /** The reader's page count, saved with the recap: the same page of the same layout reuses it. */
    private final int readerPages;
    private final long today;
    private final Screen screen;

    RecapJob(Context context, AiJobs.Target target, String bucket, int days, int readerPages, long today, Screen screen) {
        super(context, target);
        this.bucket = bucket;
        this.days = days;
        this.readerPages = readerPages;
        this.today = today;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        final String key = RecapLogic.key(bucket);
        final JSONObject saved = AssistStore.get().item(target.name, key);
        if (RecapLogic.reusable(saved, target.page, readerPages, today)) {
            final RecapPrompts.Recap again = RecapPrompts.fromJson(saved.optJSONObject(RecapLogic.RECAP));
            if (again != null) {
                ui(() -> screen.onRecap(again));
                return;
            }
        }
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final int budget = RecapLogic.budget(bucket, settings.maxChars);
        final List<RecapLogic.Excerpt> excerpts;
        final String chapter;
        final int page;
        final int pageCount;
        try (BookText text = open()) {
            pageCount = text.pageCount();
            page = Math.max(0, Math.min(target.page, pageCount));
            final List<BookChapters.Entry> toc = AiJobs.toc(target, text);
            final List<BookChapters.Range> chapters = BookChapters.chaptersOrBlocks(toc, pageCount, BookChapters.BLOCK_PAGES);
            final BookChapters.Range at = BookChapters.chapterAt(toc, page, pageCount, BookChapters.BLOCK_PAGES);
            chapter = at == null || at.title == null ? "" : at.title;

            // the pages right before the reader's, whole, newest first; never the reader's page or later
            final String[] plain = new String[pageCount];
            final int tailBudget = RecapLogic.tailBudget(bucket, budget);
            int used = 0;
            int tailStart = page;
            for (int p = page - 1; p >= 0 && used < tailBudget; p--) {
                checkCancelled();
                String t = BookText.plain(text.body(p));
                if (used > 0 && used + t.length() > tailBudget) {
                    break;
                }
                if (t.length() > tailBudget) {
                    t = RecapLogic.cut(t, tailBudget);
                }
                plain[p] = t;
                used += t.length();
                tailStart = p;
            }
            // then the openings of the chapters before them
            for (RecapLogic.Slice s : RecapLogic.openings(chapters, tailStart, budget - used)) {
                int left = s.maxChars;
                for (int p = s.from; p < s.to && p < s.from + OPENING_PAGES && left > 1; p++) {
                    checkCancelled();
                    final String t = RecapLogic.cut(BookText.plain(text.body(p)), left);
                    if (t.isEmpty()) {
                        continue;
                    }
                    plain[p] = t;
                    left -= t.length();
                }
            }
            excerpts = RecapLogic.excerpts(chapters, plain, tailStart, page);
        }
        if (excerpts.isEmpty()) {
            failWith(string(R.string.ai_no_text));
            return;
        }
        checkCancelled();
        final String system = AiPrompts.withMaster(settings.masterPrompt,
                RecapPrompts.system(settings.answerLanguage, bucket) + "\n" + AiPrompts.SIDE_MATTER);
        final String answer = ask(system, RecapPrompts.user(target.title, chapter, page + 1, pageCount, days, excerpts), null);
        final RecapPrompts.Recap recap = RecapPrompts.parse(answer);
        if (recap == null) {
            failWith(string(R.string.recap_t10_unreadable));
            return;
        }
        try {
            final JSONObject item = new JSONObject()
                    .put(RecapLogic.PAGE, target.page)
                    .put(RecapLogic.PAGES, readerPages)
                    .put(RecapLogic.DAY, today)
                    .put("model", settings.model)
                    .put(RecapLogic.RECAP, recap.toJson());
            AssistStore.get().put(target.name, key, item);
        } catch (Exception e) {
            // shown anyway; only the next opening today asks again
            LOG.e(e);
        }
        ui(() -> screen.onRecap(recap));
    }
}
