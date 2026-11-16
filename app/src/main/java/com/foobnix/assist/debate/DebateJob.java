package com.foobnix.assist.debate;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.R;

import java.util.Set;

/**
 * One round of a debate: one request holding the chapter's ideas with their marks, the debate so
 * far and the reader's message ({@link DebatePrompts}); the author's quotes in the answer are then
 * checked against the book ({@link DebateText}). A screen job: it stops with the debate screen.
 * Nothing is stored here.
 */
final class DebateJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        /**
         * @param position the author's position in a few words, or empty
         * @param crux     where the two really disagree (the last round), or empty
         */
        void onRound(DebateRound round, String position, String crux);

        void onError(String message);

        void onNeedSettings();
    }

    private final DebateSession session;
    private final String message;
    private final Screen screen;
    private final String system;
    private final String user;
    private final Set<Integer> ids;

    /** Made on the main thread, where the session is changed: the request's text is fixed here. */
    DebateJob(Activity activity, AiJobs.Target target, DebateSession session, String message, Screen screen) {
        super(activity, target);
        this.session = session;
        this.message = message;
        this.screen = screen;
        this.system = AiPrompts.withMaster(settings.masterPrompt,
                DebatePrompts.system(session.author.name, session.lastRound(), settings.answerLanguage));
        this.user = DebatePrompts.user(session, message, settings.maxChars);
        this.ids = session.ids();
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.RECALL.isOn()) {
            return;
        }
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final String answer = ask(system, user, null);
        checkCancelled();
        final DebateReply reply = DebateReply.parse(answer, ids);
        if (reply == null) {
            failWith(string(R.string.debate_unreadable));
            return;
        }
        final DebateQuotes.Checked checked;
        final String chapter;
        try (DebateText text = new DebateText(target, session.chapterKey, session.chapterTitle, session.ideas,
                this::checkCancelled)) {
            checked = DebateQuotes.check(reply.reply, reply.quote, text);
            chapter = checked.quote == null ? "" : text.titleAt(checked.page);
        }
        checkCancelled();
        final DebateRound round = DebateRound.of(session, message, reply, checked, chapter);
        ui(() -> screen.onRound(round, reply.position, reply.crux));
    }
}
