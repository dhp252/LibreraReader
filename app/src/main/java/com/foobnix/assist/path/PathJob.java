package com.foobnix.assist.path;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookText;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.skeleton.SkeletonParts;
import com.foobnix.pdf.info.R;
import com.foobnix.study.StudyStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The two requests of "Shortest path", one each: the path to the reader's goal from the book's
 * chapter lines (the stored skeleton, else the study preview's lines, both only read), and the
 * 2-minute check's questions for the chapters on the path. No text of the book is sent; the
 * table of contents is read on the device to put the chapters in order and name them. Started
 * from the path screens, a job stops with its screen.
 */
final class PathJob extends AiJobs.Job {

    /** What the screens hear, on the main thread. */
    interface Screen {
        void onStatus(String status);

        /** The path was made and stored; {@code ranges} are its chapters in the reader's layout. */
        void onPath(PathData data, Map<String, int[]> ranges, int pageCount);

        /** The check's questions were made and stored with the path. */
        void onQuestions(PathData data);

        /** The book has neither a skeleton nor a study preview with chapter lines. */
        void onNeedSkeleton();

        void onNeedSettings();

        void onError(String message);
    }

    private final Screen screen;
    private final String goal;
    /** The path to make questions for, or null to make a path. */
    private final PathData check;

    private PathJob(Activity activity, AiJobs.Target target, String goal, PathData check, Screen screen) {
        super(activity, target);
        this.goal = goal;
        this.check = check;
        this.screen = screen;
    }

    /** Finds the path to {@code goal}. */
    static PathJob resolve(Activity a, AiJobs.Target target, String goal, Screen screen) {
        return new PathJob(a, target, goal, null, screen);
    }

    /** Makes the check's questions for the chapters of {@code data} that have none. */
    static PathJob check(Activity a, AiJobs.Target target, PathData data, Screen screen) {
        return new PathJob(a, target, data.goal, data, screen);
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void showStatus(String text) {
        status(text);
    }

    private void status(String text) {
        ui(() -> screen.onStatus(text));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.PATH.isOn()) {
            return;
        }
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        if (check != null) {
            questions();
        } else {
            path();
        }
    }

    private void path() throws Exception {
        SkeletonData skeleton = SkeletonData.fromJson(AssistStore.get().item(target.name, SkeletonData.KEY));
        if (skeleton.chapterLines.isEmpty()) {
            // the study preview's lines; its file is only read
            skeleton = SkeletonData.fromStudy(StudyStore.get().load(target.name));
        }
        if (skeleton.chapterLines.isEmpty()) {
            ui(screen::onNeedSkeleton);
            return;
        }
        status(string(R.string.canvas_t20_reading_contents));
        final List<SkeletonParts.Part> parts;
        final int pages;
        try (BookText text = open()) {
            pages = text.pageCount();
            parts = SkeletonParts.group(AiJobs.toc(target, text), pages);
        }
        final List<PathChapters.Entry> list = PathChapters.list(parts, skeleton);
        if (!PathChapters.hasLines(list)) {
            ui(screen::onNeedSkeleton);
            return;
        }
        checkCancelled();
        status(string(R.string.canvas_t20_finding));
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, PathPrompts.system(settings.answerLanguage)),
                PathPrompts.user(target.title, goal, list, settings.maxChars), null);
        checkCancelled();
        final PathPrompts.Answer parsed = PathPrompts.parse(answer);
        final PathData data = PathData.fromAnswer(goal, parsed, list, settings.model, System.currentTimeMillis());
        if (data.isEmpty()) {
            failWith(string(parsed.read ? R.string.canvas_t20_not_in_book
                    : wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t20_unreadable));
            return;
        }
        AssistStore.get().put(target.name, PathData.KEY, data.toJson());
        final Map<String, int[]> ranges = PathChapters.ranges(parts);
        ui(() -> screen.onPath(data, ranges, pages));
    }

    private void questions() throws Exception {
        final List<PathData.Chapter> ask = new ArrayList<>();
        for (PathData.Chapter c : check.toCheck()) {
            if (c.question == null) {
                ask.add(c);
            }
        }
        if (ask.isEmpty()) {
            ui(() -> screen.onQuestions(check));
            return;
        }
        status(string(R.string.canvas_t20_making_questions));
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, PathPrompts.checkSystem(settings.answerLanguage)),
                PathPrompts.checkUser(target.title, check.goal, ask), null);
        checkCancelled();
        final Map<Integer, PathPrompts.Question> found = PathPrompts.parseCheck(answer);
        final Random random = new Random();
        final List<String> keys = new ArrayList<>();
        final List<PathPrompts.Question> questions = new ArrayList<>();
        for (Map.Entry<Integer, PathPrompts.Question> e : found.entrySet()) {
            if (e.getKey() < ask.size()) {
                // models favour one place for the right option
                final List<Integer> order = new ArrayList<>(java.util.Arrays.asList(0, 1, 2, 3));
                Collections.shuffle(order, random);
                keys.add(ask.get(e.getKey()).key);
                questions.add(e.getValue().reordered(new int[]{order.get(0), order.get(1), order.get(2), order.get(3)}));
            }
        }
        if (keys.isEmpty()) {
            failWith(string(wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t20_unreadable));
            return;
        }
        // onto the path as it is stored now: a chapter may have been read meanwhile
        final PathStore.Change change = d -> {
            for (int i = 0; i < keys.size(); i++) {
                final PathData.Chapter c = d.chapter(keys.get(i));
                if (c != null) {
                    c.question = questions.get(i);
                }
            }
        };
        final PathData stored = PathStore.update(target.name, check.goal, change);
        if (stored == null) {
            // the stored path is another one by now: the questions serve this screen only
            change.apply(check);
        }
        ui(() -> screen.onQuestions(stored == null ? check : stored));
    }
}
