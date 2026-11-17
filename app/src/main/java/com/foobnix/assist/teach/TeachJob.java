package com.foobnix.assist.teach;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.pdf.info.R;

/**
 * One turn of a teach-back: one request with the chapter's ideas, the conversation so far and the
 * reader's last answer ({@link Teach#user}), sent when the reader sends an answer (Assist.RECALL
 * on). The book is not read: the ideas carry their passages. A screen job, it stops with the chat.
 */
final class TeachJob extends AiJobs.Job {

    /** What the chat hears, on the main thread. */
    interface Screen {
        void onReply(Teach.Reply reply);

        void onError(String message);

        void onNeedSettings();
    }

    private final String user;
    private final boolean last;
    private final Screen screen;

    /**
     * @param user the turn's message, built on the main thread from the session
     * @param last the reader's answer is the last one
     */
    TeachJob(Activity activity, AiJobs.Target target, String user, boolean last, Screen screen) {
        super(activity, target);
        this.user = user;
        this.last = last;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, Teach.system(settings.answerLanguage, last)),
                user, null);
        checkCancelled();
        final Teach.Reply reply = Teach.parse(answer);
        if (reply == null) {
            failWith(string(R.string.canvas_t18_unreadable));
            return;
        }
        ui(() -> screen.onReply(reply));
    }
}
