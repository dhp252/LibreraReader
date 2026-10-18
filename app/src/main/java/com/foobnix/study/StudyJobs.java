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

    /** What a check shows; called on the main thread. */
    public interface CheckScreen {
        void onStatus(String status);

        /** The feedback written so far (without the map's JSON). */
        void onText(String visible);

        void onDone(StudyBook.Chapter chapter);

        void onError(String message);

        void onNeedSettings();
    }

    /**
     * Check / Add to map: the chapter (read here, summarized by parts when it is too long), the
     * reader's answers, marks, notes and the AI passages go to the model; its feedback streams in;
     * the quotes it gives are found in the chapter (their page); feedback and branch are kept.
     */
    public static class CheckJob extends AiJobs.Job {
        private final BookChapters.Range range;
        private final List<String> questions;
        private final List<String> answers;
        private final String recall;
        private volatile CheckScreen screen;

        public CheckJob(Activity activity, AiJobs.Target target, BookChapters.Range range, List<String> questions,
                        List<String> answers, String recall, CheckScreen screen) {
            super(activity, target);
            this.range = range;
            this.questions = new ArrayList<>(questions);
            this.answers = new ArrayList<>(answers);
            this.recall = recall == null ? "" : recall;
            this.screen = screen;
        }

        public void setScreen(CheckScreen screen) {
            this.screen = screen;
        }

        private void status(String text) {
            ui(() -> {
                final CheckScreen s = screen;
                if (s != null) {
                    s.onStatus(text);
                }
            });
        }

        @Override
        protected void failed(String message) {
            ui(() -> {
                final CheckScreen s = screen;
                if (s != null) {
                    s.onError(message);
                } else {
                    android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        protected void work() throws Exception {
            if (!settings.isReady()) {
                ui(() -> {
                    final CheckScreen s = screen;
                    if (s != null) {
                        s.onNeedSettings();
                    }
                });
                return;
            }
            status(string(R.string.ai_reading_book));
            try (BookText text = open()) {
                final int pages = text.pageCount();
                final int from = Math.max(0, range.from);
                final int to = Math.min(pages, range.to);
                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                final List<String> words = new ArrayList<>();
                final List<Integer> wordPage = new ArrayList<>();
                for (int p = from; p < to; p++) {
                    checkCancelled();
                    final org.ebookdroid.droids.mupdf.codec.TextWord[][] page = text.text(p);
                    for (String w : BookText.words(page)) {
                        words.add(w);
                        wordPage.add(p);
                    }
                    plain[p] = BookText.plain(page);
                    lengths[p] = plain[p].length();
                }
                if (BookChapters.length(lengths, from, to) == 0) {
                    failed(string(R.string.ai_no_text));
                    return;
                }

                final AiStore.Book ai = AiStore.get().load(target.name);
                final String goal = ai.goal == null ? "" : ai.goal;
                final List<String> marks = marks(target.name, from, to);
                final List<String> passages = new ArrayList<>();
                for (AiStore.Chapter chapter : ai.highlights) {
                    for (AiStore.Quote q : chapter.quotes) {
                        if (q.page >= from && q.page < to && passages.size() < 30) {
                            passages.add("[" + q.category.code + "] " + q.text);
                        }
                    }
                }

                // the chapter whole when it fits, else its parts summarized
                final int room = Math.max(8000, settings.maxChars - 8000);
                final String chapterText;
                final boolean summarized = BookChapters.length(lengths, from, to) > room;
                if (!summarized) {
                    chapterText = join(plain, from, to);
                } else {
                    final List<BookChapters.Range> parts = BookChapters.parts(
                            java.util.Collections.singletonList(new BookChapters.Range(from, to, range.title)), lengths, room);
                    final StringBuilder all = new StringBuilder();
                    final String system = AiPrompts.withMaster(settings.masterPrompt, AiPrompts.summarySystem(settings.answerLanguage));
                    for (int i = 0; i < parts.size(); i++) {
                        status(string(R.string.ai_summarizing_part, i + 1, parts.size()));
                        final BookChapters.Range part = parts.get(i);
                        all.append("<part n=\"").append(i + 1).append("\">\n")
                                .append(ask(system, AiPrompts.partSummary(target.title, part.title, join(plain, part.from, part.to)), null))
                                .append("\n</part>\n");
                    }
                    chapterText = all.toString();
                }

                status(string(R.string.study_checking));
                final StringBuilder streamed = new StringBuilder();
                final String answer = ask(
                        AiPrompts.withMaster(settings.masterPrompt, StudyPrompts.checkSystem(settings.answerLanguage, !goal.trim().isEmpty())),
                        StudyPrompts.check(target.title, chapterTitle(range), goal, chapterText, summarized, questions, answers,
                                recall, marks, passages),
                        delta -> {
                            streamed.append(delta);
                            final String shown = StudyPrompts.visible(streamed.toString());
                            ui(() -> {
                                final CheckScreen s = screen;
                                if (s != null) {
                                    s.onText(shown);
                                }
                            });
                        });
                final StudyPrompts.Check check = StudyPrompts.parseCheck(answer);
                if (check.items.isEmpty() && check.branch.isEmpty() && check.feedback.isEmpty()) {
                    failed(string(wasCutOff() ? R.string.ai_cut_off : R.string.study_check_unreadable));
                    return;
                }
                place(check, words, wordPage);

                checkCancelled();
                final boolean answered = hasAnswers();
                final long now = System.currentTimeMillis();
                final String model = settings.model;
                final StudyBook book = StudyStore.get().update(target.name, b -> {
                    final StudyBook.Chapter c = b.chapter(range.key);
                    if (c == null) {
                        return;
                    }
                    c.answers.clear();
                    c.answers.addAll(answers);
                    c.recall = recall;
                    if (answered) {
                        final StudyBook.Feedback f = new StudyBook.Feedback();
                        f.items.addAll(check.items);
                        f.text = check.feedback;
                        f.recall = check.recall;
                        f.model = model;
                        f.time = now;
                        c.feedback = f;
                    }
                    if (!check.branch.isEmpty()) {
                        c.branch.clear();
                        c.branch.addAll(check.branch);
                    }
                    c.checked = now;
                    if (c.afterShown == 0) {
                        c.afterShown = now;
                    }
                });
                StudySession.reloadIfOpen(target.name);
                final StudyBook.Chapter done = book.chapter(range.key);
                if (done == null) {
                    // the chapter left the study data meanwhile (a new preview on another device)
                    failed(string(R.string.study_check_unreadable));
                    return;
                }
                ui(() -> {
                    final CheckScreen s = screen;
                    if (s != null && done != null) {
                        s.onDone(done);
                    }
                });
            }
        }

        private boolean hasAnswers() {
            if (!recall.trim().isEmpty()) {
                return true;
            }
            for (String a : answers) {
                if (a != null && !a.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** The reader's marks in the chapter for the request: "quote" and its note. */
        private static List<String> marks(String bookName, int from, int to) {
            final List<String> out = new ArrayList<>();
            for (com.foobnix.annotation.Mark m : com.foobnix.annotation.MarksStore.get().load(bookName).values()) {
                if (m.isInk() || out.size() >= 30) {
                    continue;
                }
                final int page = m.firstPage();
                if (page < from || page >= to) {
                    continue;
                }
                final String quote = m.quote == null ? "" : m.quote.trim();
                final String note = m.note == null ? "" : m.note.trim();
                if (quote.isEmpty() && note.isEmpty()) {
                    continue;
                }
                out.add((quote.isEmpty() ? "" : "\"" + quote + "\"") + (note.isEmpty() ? "" : " (note: " + note + ")"));
            }
            return out;
        }
    }

    /** The quotes of a check found in the chapter: the book's own words and their page; unmatched ones lose the link. */
    static void place(StudyPrompts.Check check, List<String> words, List<Integer> wordPage) {
        final List<Object> owners = new ArrayList<>();
        final List<String> quotes = new ArrayList<>();
        for (StudyBook.Item item : check.items) {
            if (item.quote != null && !item.quote.isEmpty()) {
                owners.add(item);
                quotes.add(item.quote);
            }
        }
        collect(check.branch, owners, quotes);
        if (quotes.isEmpty()) {
            return;
        }
        for (com.foobnix.ai.QuoteMatcher.Found f : com.foobnix.ai.QuoteMatcher.locateAll(words, quotes)) {
            final Object owner = owners.get(f.quote);
            final int page = wordPage.get(f.firstWord);
            if (owner instanceof StudyBook.Item) {
                ((StudyBook.Item) owner).quote = f.text;
                ((StudyBook.Item) owner).page = page;
            } else {
                ((StudyBook.Node) owner).quote = f.text;
                ((StudyBook.Node) owner).page = page;
            }
        }
    }

    private static void collect(List<StudyBook.Node> nodes, List<Object> owners, List<String> quotes) {
        for (StudyBook.Node n : nodes) {
            if (n.quote != null && !n.quote.isEmpty()) {
                owners.add(n);
                quotes.add(n.quote);
            }
            collect(n.kids, owners, quotes);
        }
    }

    /** What a streamed text answer shows; called on the main thread. */
    public interface TextScreen {
        void onText(String all);

        void onDone(String all);

        void onError(String message);

        void onNeedSettings();
    }

    /** "What do I still recall?": the outline and the reader's words, never the book's text. */
    public static class RecallJob extends AiJobs.Job {
        private final String outline;
        private final String remembered;
        private volatile TextScreen screen;

        public RecallJob(Activity activity, AiJobs.Target target, String outline, String remembered, TextScreen screen) {
            super(activity, target);
            this.outline = outline;
            this.remembered = remembered;
            this.screen = screen;
        }

        public void setScreen(TextScreen screen) {
            this.screen = screen;
        }

        @Override
        protected void failed(String message) {
            ui(() -> {
                final TextScreen s = screen;
                if (s != null) {
                    s.onError(message);
                }
            });
        }

        @Override
        protected void work() throws Exception {
            if (!settings.isReady()) {
                ui(() -> {
                    final TextScreen s = screen;
                    if (s != null) {
                        s.onNeedSettings();
                    }
                });
                return;
            }
            final StringBuilder all = new StringBuilder();
            final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, StudyPrompts.recallSystem(settings.answerLanguage)),
                    StudyPrompts.recall(target.title, outline, remembered), delta -> {
                        all.append(delta);
                        final String now = all.toString();
                        ui(() -> {
                            final TextScreen s = screen;
                            if (s != null) {
                                s.onText(now);
                            }
                        });
                    });
            ui(() -> {
                final TextScreen s = screen;
                if (s != null) {
                    s.onDone(answer.trim());
                }
            });
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
