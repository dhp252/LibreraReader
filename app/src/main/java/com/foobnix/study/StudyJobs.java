package com.foobnix.study;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Study mode's AI work on the open book, built like {@link AiJobs}: off the main thread, cancellable. */
public final class StudyJobs {

    /** Room the prompt around the skim needs in a request. */
    private static final int PROMPT_ROOM = 4000;

    private StudyJobs() {
    }

    /** What a job's progress shows; called on the main thread. */
    public interface Screen {
        void onStatus(String status);

        void onDone(StudyBook book);

        void onError(String message);

        void onNeedSettings();
    }

    /**
     * The preview: the book's text read on the device, a skim of it sent once, and the answer kept
     * as the book's chapters (thesis, line, label, reason, questions). Studying is switched on only
     * when it worked. Labels the reader set, and the questions of chapters already answered, stay.
     */
    public static class PreviewJob extends AiJobs.Job {
        private volatile Screen screen;
        private volatile String status = "";
        private volatile boolean finished;

        public PreviewJob(Activity activity, AiJobs.Target target, Screen screen) {
            super(activity, target);
            this.screen = screen;
        }

        /** The progress is shown again (or hidden with null): the preview goes on either way. */
        public void setScreen(Screen screen) {
            this.screen = screen;
        }

        public String getStatus() {
            return status;
        }

        public boolean isRunning() {
            return !finished && !cancelled;
        }

        private void status(String text) {
            status = text;
            ui(() -> {
                final Screen s = screen;
                if (s != null) {
                    s.onStatus(text);
                }
            });
        }

        @Override
        protected void failed(String message) {
            ui(() -> {
                final Screen s = screen;
                if (s != null) {
                    s.onError(message);
                } else {
                    android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        protected void work() throws Exception {
            try {
                preview();
            } finally {
                finished = true;
            }
        }

        private void preview() throws Exception {
            if (!settings.isReady()) {
                ui(() -> {
                    final Screen s = screen;
                    if (s != null) {
                        s.onNeedSettings();
                    }
                });
                return;
            }
            status(string(R.string.ai_reading_book));
            try (BookText text = open()) {
                final int pages = text.pageCount();
                final List<BookChapters.Entry> toc = AiJobs.toc(target, text);
                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                read(text, 0, pages, plain, lengths, this::status);
                if (BookChapters.length(lengths, 0, pages) == 0) {
                    failed(string(R.string.study_no_text));
                    return;
                }
                final Set<String> split = StudyChapters.longParts(toc, pages, lengths);
                final List<BookChapters.Range> chapters = StudyChapters.cut(toc, pages, split);
                final String goal = AiStore.get().load(target.name).goal;
                final String pack = SkimPack.build(chapters, plain, Math.max(8000, settings.maxChars - PROMPT_ROOM));

                checkCancelled();
                status(string(R.string.study_skimming));
                final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, StudyPrompts.previewSystem(settings.answerLanguage)),
                        StudyPrompts.preview(target.title, goal, pack), null);
                final StudyPrompts.Preview preview = StudyPrompts.parsePreview(answer);
                if (preview.chapters.isEmpty()) {
                    failed(string(wasCutOff() ? R.string.ai_cut_off : R.string.study_preview_unreadable));
                    return;
                }
                checkCancelled();
                final StudyBook book = StudyStore.get().update(target.name,
                        b -> apply(b, preview, chapters, lengths, split, goal, settings.model));
                StudySession.reloadIfOpen(target.name);
                ui(() -> {
                    final Screen s = screen;
                    if (s != null) {
                        s.onDone(book);
                    } else {
                        android.widget.Toast.makeText(activity, R.string.study_ready, android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    /** The preview into the book's study data (kept apart for the reader's own labels and answers). */
    static void apply(StudyBook b, StudyPrompts.Preview preview, List<BookChapters.Range> chapters, int[] lengths,
                      Set<String> split, String goal, String model) {
        b.on = true;
        b.thesis = preview.thesis;
        b.goal = goal == null ? "" : goal;
        b.model = model;
        b.previewed = System.currentTimeMillis();
        b.split.clear();
        b.split.addAll(split);

        final Set<String> keys = new HashSet<>();
        for (int i = 0; i < chapters.size(); i++) {
            final BookChapters.Range range = chapters.get(i);
            if (StudyChapters.isFront(range)) {
                continue;
            }
            keys.add(range.key);
            final StudyBook.Chapter c = b.chapterOrNew(range.key);
            c.title = range.title == null ? "" : range.title;
            c.index = i;
            c.from = range.from;
            c.to = range.to;
            c.chars = BookChapters.length(lengths, range.from, range.to);
            final StudyPrompts.Planned planned = preview.chapters.get(i);
            if (planned == null) {
                continue;
            }
            c.line = planned.line;
            if (!c.labelMine) {
                c.label = planned.label;
            }
            c.reason = planned.reason;
            if (c.checked == 0 && !c.hasAnswers() && !planned.questions.isEmpty()) {
                c.questions.clear();
                c.questions.addAll(planned.questions);
            }
        }
        // chapters no longer in the book's cut go, unless the reader wrote something in them
        for (StudyBook.Chapter c : new ArrayList<>(b.chapters.values())) {
            if (!keys.contains(c.key) && c.checked == 0 && !c.hasAnswers()) {
                b.chapters.remove(c.key);
            }
        }
    }
}
