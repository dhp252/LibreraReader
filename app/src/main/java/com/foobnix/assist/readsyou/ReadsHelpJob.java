package com.foobnix.assist.readsyou;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.lens.LensProfile;
import com.foobnix.assist.lens.LensPrompts;
import com.foobnix.pdf.info.R;

/**
 * One request of "Say it another way?": the passage shown on the sheet, said in plain words, with
 * an example, or in the reader's field. Started only by a tap on its tab, stopped with the sheet,
 * and nothing of it is kept. The field request is the Lens's "make it concrete" sentence request
 * ({@link LensPrompts#sentenceSystem}) with the reader's stored lens; it runs only with
 * Assist.LENS on and a lens set up.
 */
final class ReadsHelpJob extends AiJobs.Job {

    /** What the sheet hears, on the main thread. */
    interface Screen {
        void onDone(ReadsPrompts.Kind kind, String text);

        void onError(ReadsPrompts.Kind kind, String message);

        void onNeedSettings(ReadsPrompts.Kind kind);
    }

    private final String author;
    private final String passage;
    private final ReadsPrompts.Kind kind;
    private final Screen screen;

    ReadsHelpJob(Activity activity, AiJobs.Target target, String author, String passage, ReadsPrompts.Kind kind,
                 Screen screen) {
        super(activity, target);
        this.author = author;
        this.passage = passage;
        this.kind = kind;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(kind, message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.READS_YOU.isOn() || passage == null || passage.isEmpty()) {
            return;
        }
        if (!settings.isReady()) {
            ui(() -> screen.onNeedSettings(kind));
            return;
        }
        final String system;
        if (kind == ReadsPrompts.Kind.FIELD) {
            final LensProfile profile = lens();
            if (profile == null) {
                failWith(string(R.string.canvas_t31_help_no_lens));
                return;
            }
            final LensProfile.Level level = LensProfile.Level.fromJson(
                    AssistStore.get().item(target.name, LensProfile.LEVEL_KEY));
            system = LensPrompts.sentenceSystem(profile, level, settings.answerLanguage, true);
        } else {
            system = ReadsPrompts.system(kind, settings.answerLanguage);
        }
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, system),
                ReadsPrompts.user(target.title, author, passage), null);
        checkCancelled();
        final String text = ReadsPrompts.answer(answer);
        if (text.isEmpty()) {
            failWith(string(R.string.canvas_t31_help_unreadable));
            return;
        }
        ui(() -> screen.onDone(kind, text));
    }

    /** The reader's lens when the Lens is on and set up, else null. Off the main thread. */
    static LensProfile lens() {
        if (!Assist.LENS.isOn()) {
            return null;
        }
        final LensProfile profile = LensProfile.fromJson(AssistStore.get().item(AssistStore.LIBRARY, LensProfile.KEY));
        return profile != null && profile.isSet() ? profile : null;
    }
}
