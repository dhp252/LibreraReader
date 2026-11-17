package com.foobnix.assist.review;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.Assist;
import com.foobnix.dao2.FileMeta;

import java.util.List;

/**
 * The one request of "Find connections", sent only on that tap (Assist.REVIEW on): the open
 * book's stored key ideas and the stored ideas or chapter lines of up to 8 other books of the
 * library ({@link ReviewLinkBooks}, {@link ReviewLinkPrompts}). No book is opened and no book
 * text is sent. What comes back is kept in the library's assist file as {@code link:<id>} items
 * in state "new"; a connection the reader already decided on is left alone. A screen job: it
 * stops with its screen.
 */
final class ReviewLinkJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        /**
         * @param fresh the connections that are new on the list
         * @param found how many the answer held (some may have been on the list already)
         */
        void onLinks(List<ReviewLink> fresh, int found);

        /** Nothing was sent: the open book has no stored ideas ({@code mine}), or no other book has. */
        void onNothingToSend(boolean mine);

        void onNeedSettings();

        void onError(String message);
    }

    private final FileMeta meta;
    private final Screen screen;

    /** @param meta the open book's title, author and year as the library has them, or null */
    ReviewLinkJob(Activity activity, AiJobs.Target target, FileMeta meta, Screen screen) {
        super(activity, target);
        this.meta = meta;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.REVIEW.isOn()) {
            return;
        }
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final List<ReviewLinkPrompts.Idea> ideas = ReviewLinkPrompts.spread(ReviewLinkBooks.ideas(target.name),
                ReviewLinkPrompts.MAX_MINE);
        if (ideas.isEmpty()) {
            ui(() -> screen.onNothingToSend(true));
            return;
        }
        checkCancelled();
        final List<ReviewLinkPrompts.Book> others = ReviewLinkBooks.others(target.name);
        if (others.isEmpty()) {
            ui(() -> screen.onNothingToSend(false));
            return;
        }
        checkCancelled();
        final ReviewLinkPrompts.Book mine = ReviewLinkBooks.book(meta, target.name, ideas);
        final String system = AiPrompts.withMaster(settings.masterPrompt, ReviewLinkPrompts.system(settings.answerLanguage));
        final String reply = ask(system, ReviewLinkPrompts.user(mine, others), null);
        checkCancelled();
        final List<ReviewLink> found = ReviewLinkPrompts.parse(reply, mine, others, ReviewStore.today());
        final List<ReviewLink> fresh = ReviewStore.keep(found);
        ui(() -> screen.onLinks(fresh, found.size()));
    }
}
