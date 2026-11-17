package com.foobnix.assist.recall;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import java.util.List;

/**
 * The requests of blank-page recall, one per tap (Assist.RECALL on):
 * <ul>
 * <li>the check: the reader's words against the chapter's key ideas; with stored ideas only they
 * are sent, else the chapter's text ({@link Recall#MAX_CHARS} at most) and the model picks the
 * ideas itself;</li>
 * <li>the ideas alone (a nudge or a teach-back on a chapter without stored ideas): the chapter's
 * text, once; the ideas are kept in the chapter's recall item, so later requests send only them.</li>
 * </ul>
 * A screen job: it stops with its screen. The result is stored before the screen hears of it.
 */
public final class RecallJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    public interface Screen {
        /** The check is stored: the chapter's new item. */
        void onChecked(Recall.Attempt attempt);

        /** The chapter's ideas were found and stored (the ideas-only request). */
        void onIdeas(Recall.Attempt item);

        void onError(String message);

        void onNeedSettings();
    }

    private final BookChapters.Range range;
    private final Screen screen;
    /** The known ideas, or empty: the text is read. */
    private final List<Recall.Idea> ideas;
    private final boolean fromPassages;
    /** The reader's words; null for the ideas-only request. */
    private final String words;
    private final int expected;
    private final boolean spoken;
    private final int seconds;

    private RecallJob(Activity activity, AiJobs.Target target, BookChapters.Range range, List<Recall.Idea> ideas,
                      boolean fromPassages, String words, int expected, boolean spoken, int seconds, Screen screen) {
        super(activity, target);
        this.range = range;
        this.ideas = ideas;
        this.fromPassages = fromPassages;
        this.words = words;
        this.expected = expected;
        this.spoken = spoken;
        this.seconds = seconds;
        this.screen = screen;
    }

    /**
     * The check of an attempt.
     *
     * @param ideas        the chapter's known ideas, or empty to send its text
     * @param fromPassages the ideas are the chapter's key passages
     */
    public static RecallJob check(Activity activity, AiJobs.Target target, BookChapters.Range range,
                                  List<Recall.Idea> ideas, boolean fromPassages, String words, int expected,
                                  boolean spoken, int seconds, Screen screen) {
        return new RecallJob(activity, target, range, ideas, fromPassages, words == null ? "" : words, expected,
                spoken, seconds, screen);
    }

    /** The chapter's key ideas from its text, for a chapter that has none stored. */
    public static RecallJob ideas(Activity activity, AiJobs.Target target, BookChapters.Range range, Screen screen) {
        return new RecallJob(activity, target, range, new java.util.ArrayList<Recall.Idea>(), false, null, 0, false, 0,
                screen);
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
        final String title = chapterTitle(range);
        final boolean withIdeas = !ideas.isEmpty();
        RecallChapter.Text text = null;
        if (!withIdeas) {
            final int max = Math.min(Recall.MAX_CHARS, Math.max(Recall.MIN_CHARS, settings.maxChars - 4000));
            try (BookText book = open()) {
                text = RecallChapter.read(book, range, max);
            }
            checkCancelled();
            if (text.plain.length() < Recall.MIN_CHARS) {
                failWith(string(R.string.canvas_t17_too_short));
                return;
            }
            if (text.plain.length() > max) {
                text.plain.setLength(max);
            }
        }
        final String system;
        final String user;
        if (words == null) {
            system = Recall.ideasSystem(settings.answerLanguage);
            user = Recall.ideasUser(target.title, title, text.plain.toString());
        } else {
            system = Recall.checkSystem(settings.answerLanguage, withIdeas);
            user = Recall.checkUser(target.title, title, ideas, text == null ? null : text.plain.toString(), words);
        }
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, system), user, null);
        checkCancelled();
        final Recall.Check check = words == null ? Recall.parseIdeas(answer) : Recall.parseCheck(answer, ideas);
        if (check == null) {
            failWith(string(R.string.canvas_t17_unreadable));
            return;
        }
        if (text != null) {
            Recall.place(check.ideas, text.words, text.wordPage);
        }
        final Recall.Attempt before = RecallChapter.load(target.name, range.key);
        final Recall.Attempt item = words == null
                ? Recall.withIdeas(before, range.title, check.ideas, check.question)
                : Recall.attempt(before, range.title, System.currentTimeMillis(), expected, words, spoken, seconds,
                check, fromPassages);
        RecallChapter.save(target.name, range.key, item);
        if (words == null) {
            ui(() -> screen.onIdeas(item));
        } else {
            ui(() -> screen.onChecked(item));
        }
    }
}
