package com.foobnix.assist.edition;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;

import java.util.List;
import java.util.Map;

/**
 * "Tidy my words": the edition's one AI request, sent only on that tap (Assist.EDITION on). It
 * carries the reader's own recall notes and the idea lines of each chapter, never book text
 * ({@link EditionPrompts}); the book is not opened. Each passage that comes back and still is the
 * reader's wording is stored as {@code edition:words:<chapter key>}; one that brings in too much
 * of its own is dropped and the chapter stays raw. A screen job: it stops with the edition screen.
 */
final class EditionJob extends AiJobs.Job {

    /** What the edition screen hears, on the main thread. */
    interface Screen {
        /**
         * @param kept           passages stored
         * @param changedTooMuch passages dropped because they were no longer the reader's words
         * @param asked          chapters that went into the request
         */
        void onTidied(int kept, int changedTooMuch, int asked);

        void onError(String message);

        void onNeedSettings();
    }

    private final List<EditionModel.Raw> raws;
    private final Screen screen;

    EditionJob(Activity activity, AiJobs.Target target, List<EditionModel.Raw> raws, Screen screen) {
        super(activity, target);
        this.raws = raws;
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
        final List<EditionModel.Raw> asked = EditionPrompts.fit(raws, Math.max(4000, settings.maxChars - 4000));
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, EditionPrompts.system(settings.answerLanguage)),
                EditionPrompts.user(target.title, asked), null);
        checkCancelled();
        final Map<Integer, String> passages = EditionPrompts.parse(answer, asked.size());
        if (passages.isEmpty()) {
            failWith(string(R.string.canvas_t26_tidy_unreadable));
            return;
        }
        int kept = 0;
        int dropped = 0;
        for (Map.Entry<Integer, String> e : passages.entrySet()) {
            final EditionModel.Raw raw = asked.get(e.getKey());
            if (EditionPrompts.keepsWording(raw.words, raw.ideas, e.getValue())) {
                AssistStore.get().put(target.name, EditionPrompts.key(raw.key),
                        EditionPrompts.item(e.getValue(), raw.words, settings.model, System.currentTimeMillis()));
                kept++;
            } else {
                dropped++;
            }
        }
        final int keptNow = kept;
        final int droppedNow = dropped;
        ui(() -> screen.onTidied(keptNow, droppedNow, asked.size()));
    }
}
