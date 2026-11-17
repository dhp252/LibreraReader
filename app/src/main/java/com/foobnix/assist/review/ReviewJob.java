package com.foobnix.assist.review;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.Assist;

import java.util.List;
import java.util.Map;

/**
 * The one request of a review session, sent when the reader taps Start (Assist.REVIEW on): the
 * due ideas, their books' titles and the stored ideas shown as wrong options ({@link
 * ReviewPrompts}). No book is opened and no book text is sent, so the job needs no open book
 * (its target is null). A screen job: it stops with its screen. Whatever goes wrong, the session
 * still runs: its cards then ask "Which idea is this?".
 */
final class ReviewJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        /** The scenarios by card number (1-based); a card not in it has none. */
        void onTexts(Map<Integer, ReviewPrompts.Texts> texts);

        /** No scenarios: the AI is not set up ({@code message} null) or the request failed. */
        void onNoTexts(String message);
    }

    private final List<ReviewCards.Card> cards;
    private final Screen screen;

    ReviewJob(Activity activity, List<ReviewCards.Card> cards, Screen screen) {
        super(activity, null);
        this.cards = cards;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onNoTexts(message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.REVIEW.isOn()) {
            return;
        }
        if (!settings.isReady()) {
            ui(() -> screen.onNoTexts(null));
            return;
        }
        final String system = AiPrompts.withMaster(settings.masterPrompt, ReviewPrompts.system(settings.answerLanguage));
        final String reply = ask(system, ReviewPrompts.user(cards), null);
        checkCancelled();
        final Map<Integer, ReviewPrompts.Texts> texts = ReviewPrompts.parse(reply);
        ui(() -> screen.onTexts(texts));
    }
}
