package com.foobnix.assist.xray;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.pdf.info.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Rate the support": one request for a chapter, sending only its ideas and their marks' words
 * ({@link XrayPrompts}); the ratings are stored with the book's assist data. Started from the
 * Argument X-ray screen, it stops with it.
 */
final class XrayJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        void onRated(XrayRatings ratings);

        void onError(String message);

        void onNeedSettings();
    }

    private final AiStore.Chapter chapter;
    private final Screen screen;

    XrayJob(Activity activity, AiJobs.Target target, AiStore.Chapter chapter, Screen screen) {
        super(activity, target);
        this.chapter = chapter;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!settings.isReady() && !claudeWaiting(com.foobnix.claude.ClaudeFeature.XRAY, chapter.key)) {
            ui(screen::onNeedSettings);
            return;
        }
        final List<XrayIdea> ideas = XrayChapter.ideas(chapter);
        if (ideas.isEmpty()) {
            failWith(string(R.string.ai_ideas_none));
            return;
        }
        final Set<Integer> ids = new HashSet<>();
        for (XrayIdea idea : ideas) {
            ids.add(idea.id);
        }
        final String answer = askFor(com.foobnix.claude.ClaudeFeature.XRAY, chapter.key, AiPrompts.withMaster(settings.masterPrompt, XrayPrompts.system(settings.answerLanguage)),
                XrayPrompts.user(target.title, chapter.title, ideas), null);
        checkCancelled();
        final XrayRatings ratings = XrayRatings.parse(answer, ids);
        if (ratings == null) {
            failWith(string(R.string.xray_unreadable));
            return;
        }
        ratings.ideasTime = chapter.time;
        ratings.model = settings.model;
        XrayChapter.save(target.name, chapter.key, ratings);
        ui(() -> screen.onRated(ratings));
    }
}
