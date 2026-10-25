package com.foobnix.ai;

import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.widget.Toast;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.BookType;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * AI work on the open book, off the main thread: summaries, key passages, removing them and saving
 * them into the PDF. Every job reads the book through its own {@link BookText}, reports to its
 * screen on the main thread, and stops when it is cancelled or the book is closed.
 */
public final class AiJobs {

    private static final Set<Job> running = new CopyOnWriteArraySet<>();

    private AiJobs() {
    }

    /** The open book and where the reader is in it. */
    public static class Target {
        public final String path;
        /** File name: what the book's AI data is stored under. */
        public final String name;
        public final String title;
        public final int width;
        public final int height;
        public final int fontSize;
        /** The page the reader shows, 0-based. */
        public final int page;
        public final boolean pdf;

        Target(String path, String title, int width, int height, int fontSize, int page) {
            this.path = path;
            this.name = AiHighlights.bookName(path);
            this.title = TxtUtils.isNotEmpty(title) ? title : new File(path).getName();
            this.width = width;
            this.height = height;
            this.fontSize = fontSize;
            this.page = Math.max(0, page);
            this.pdf = BookType.PDF.is(path);
        }

        public static Target of(DocumentController dc) {
            // the page and the layout the reader gives read-aloud, so pages are counted alike
            return new Target(dc.getCurrentBook().getPath(), dc.getTitle(), dc.getBookWidth(), dc.getBookHeight(),
                    BookCSS.get().fontSizeSp, dc.getCurentPageFirst1() - 1);
        }
    }

    /** TOC and page count of the book last read, with the layout they hold for. */
    private static final class Layout {
        final String key;
        final List<BookChapters.Entry> toc;
        final int pages;

        Layout(String key, List<BookChapters.Entry> toc, int pages) {
            this.key = key;
            this.toc = toc;
            this.pages = pages;
        }
    }

    private static volatile Layout layout;

    private static String layoutKey(Target t) {
        return t.path + "|" + t.width + "x" + t.height + "|" + t.fontSize;
    }

    /**
     * The chapter at the reader's page when the book was read before in the same layout, else
     * null: a saved summary then opens without reading the book again.
     */
    public static BookChapters.Range knownChapter(Target t) {
        final Layout known = layout;
        if (known == null || !known.key.equals(layoutKey(t))) {
            return null;
        }
        return BookChapters.chapterAt(known.toc, t.page, known.pages, BookChapters.BLOCK_PAGES);
    }

    /** The TOC of the book, kept for {@link #knownChapter}. */
    public static List<BookChapters.Entry> toc(Target t, BookText text) {
        final List<BookChapters.Entry> toc = text.toc();
        layout = new Layout(layoutKey(t), toc, text.pageCount());
        return toc;
    }

    /** Stops the jobs of a reader screen that is going away. */
    public static void cancelFor(Activity activity) {
        for (Job job : running) {
            if (job.activity == activity) {
                job.cancel();
            }
        }
    }

    /** The key passages run going on in the background, if any. */
    public static HighlightJob runningHighlights() {
        for (Job job : running) {
            if (job instanceof HighlightJob && !job.cancelled) {
                return (HighlightJob) job;
            }
        }
        return null;
    }

    public static class Cancelled extends Exception {
    }

    /**
     * A job on a thread of its own; its results go to the main thread while it is not cancelled.
     * Started from a reader screen it belongs to that screen; run as a task ({@link AiWork}) it
     * has none, a screen may attach to it, and a request that fails for a passing reason is asked
     * again ({@link AiRetry}).
     */
    public abstract static class Job implements AiProgress.Attachable {
        /** The reader screen it was started from, or null for a task. */
        protected final Activity activity;
        /** The application. */
        protected final Context context;
        protected final Target target;
        protected final AiSettings settings;
        /** Run as a task: no screen of its own. */
        protected final boolean background;
        protected volatile boolean cancelled;
        private volatile AiClient client;
        protected String stopReason;
        /** The activity of the screen attached to a task: its callbacks stop when it is gone. */
        private volatile Activity screenOwner;
        private volatile Steps steps;
        /** What a task's done notification says. */
        protected volatile String result;
        /** A failure the job already reported itself (then the task stops without retrying). */
        private volatile String reported;

        protected Job(Activity activity, Target target) {
            this.activity = activity;
            this.context = activity.getApplicationContext();
            this.target = target;
            this.settings = AiSettings.load(activity);
            this.background = false;
        }

        /** A task: run by {@link AiWork} with the application, no screen. */
        protected Job(Context context, Target target) {
            this.activity = null;
            this.context = context.getApplicationContext();
            this.target = target;
            this.settings = AiSettings.load(this.context);
            this.background = true;
        }

        public void start() {
            running.add(this);
            new Thread(() -> {
                try {
                    work();
                } catch (Cancelled e) {
                    // the screen is gone or the reader asked to stop: nothing to report
                } catch (Throwable e) {
                    LOG.e(e);
                    if (!cancelled) {
                        failed(message(e));
                    }
                } finally {
                    running.remove(this);
                }
            }, "@T AI").start();
        }

        /** How a task's run ended. */
        public static final class Outcome {
            public static final int OK = 0;
            public static final int CANCELLED = 1;
            public static final int FAILED = 2;

            public final int state;
            public final String message;
            public final boolean retryable;
            /** The job already told its screen (a failure it found itself). */
            public final boolean shown;

            Outcome(int state, String message, boolean retryable, boolean shown) {
                this.state = state;
                this.message = message;
                this.retryable = retryable;
                this.shown = shown;
            }
        }

        /** Runs the job in the caller's thread (a task's worker) and says how it ended. */
        public Outcome runNow() {
            running.add(this);
            try {
                work();
                if (cancelled) {
                    return new Outcome(Outcome.CANCELLED, null, false, false);
                }
                return reported != null ? new Outcome(Outcome.FAILED, reported, false, true)
                        : new Outcome(Outcome.OK, result, false, false);
            } catch (Cancelled e) {
                return new Outcome(Outcome.CANCELLED, null, false, false);
            } catch (Throwable e) {
                LOG.e(e);
                return cancelled ? new Outcome(Outcome.CANCELLED, null, false, false)
                        : new Outcome(Outcome.FAILED, message(e), AiRetry.retryable(e), false);
            } finally {
                running.remove(this);
            }
        }

        public void cancel() {
            cancelled = true;
            final AiClient c = client;
            if (c != null) {
                c.cancel();
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        protected abstract void work() throws Exception;

        protected abstract void failed(String message);

        /** A failure the job found itself (no text, an unreadable answer): shown, and a task stops. */
        protected final void failWith(String message) {
            reported = message;
            failed(message);
        }

        /**
         * A screen attaches to a task (or leaves it, with null). {@code owner} is its activity: when
         * that is gone, the callbacks stop reaching it.
         */
        @Override
        public void attach(Object screen, Activity owner) {
            screenOwner = owner;
            setTaskScreen(screen);
        }

        /** The job's own screen type, when {@code screen} is one (or null); tasks override this. */
        protected void setTaskScreen(Object screen) {
        }

        /** What the screen shows while the job waits or retries; tasks override this. */
        protected void showStatus(String status) {
        }

        /** A task's progress for its notification: what it does, step {@code step} of {@code steps} (0: unknown). */
        public interface Steps {
            void on(String status, int step, int steps);
        }

        public void setSteps(Steps steps) {
            this.steps = steps;
        }

        protected void step(String status, int step, int total) {
            final Steps s = steps;
            if (s != null) {
                s.on(status, step, total);
            }
        }

        /** Before a request is asked again: the screen is told; a streamed answer starts over. */
        protected void retrying(String status) {
            showStatus(status);
            step(status, 0, 0);
        }

        protected void checkCancelled() throws Cancelled {
            if (cancelled || (activity != null && Apps.isDestroyedActivity(activity))) {
                throw new Cancelled();
            }
        }

        /**
         * One request; {@code output} gets the answer as it streams, or null. A task asks again
         * after a failure that may pass (the network, a busy server), twice, with waits between.
         */
        protected String ask(String system, String user, AiClient.Output output) throws IOException, Cancelled {
            for (int failures = 1; ; failures++) {
                try {
                    return askOnce(system, user, output);
                } catch (AiClient.AiException e) {
                    final long wait = background ? AiRetry.inTaskWait(e, failures) : -1;
                    if (wait < 0) {
                        throw e;
                    }
                    LOG.d("AI task: asking again in", wait, "ms after", e.getMessage());
                    retrying(string(R.string.ai_retrying));
                    pause(wait);
                }
            }
        }

        private String askOnce(String system, String user, AiClient.Output output) throws IOException, Cancelled {
            checkCancelled();
            final AiClient c = new AiClient(settings);
            client = c;
            try {
                final String answer = c.complete(system, user, output);
                stopReason = c.getStopReason();
                if (c.getUsage() != null) {
                    LOG.d("AI usage:", c.getUsage());
                }
                if ("content_filter".equals(stopReason)) {
                    throw new AiClient.AiException(string(R.string.ai_declined), true);
                }
                return answer;
            } catch (AiClient.AiException e) {
                checkCancelled();
                throw e;
            } catch (IOException e) {
                // no answer at all: the server was not reached, or the connection broke
                checkCancelled();
                throw new AiClient.AiException(string(R.string.ai_network,
                        TxtUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()), false, true);
            } finally {
                client = null;
            }
        }

        /** Waits, but stops at once when cancelled. */
        private void pause(long millis) throws Cancelled {
            final long until = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < until) {
                checkCancelled();
                try {
                    Thread.sleep(Math.min(250, Math.max(1, until - System.currentTimeMillis())));
                } catch (InterruptedException e) {
                    throw new Cancelled();
                }
            }
        }

        protected boolean wasCutOff() {
            return "max_tokens".equals(stopReason) || "length".equals(stopReason);
        }

        protected BookText open() throws IOException {
            final BookText text = BookText.open(target.path, target.width, target.height, target.fontSize);
            if (text == null) {
                throw new AiClient.AiException(string(R.string.ai_cannot_open));
            }
            return text;
        }

        protected void ui(Runnable r) {
            if (cancelled) {
                return;
            }
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!cancelled && !Apps.isDestroyedActivity(activity)) {
                        r.run();
                    }
                });
                return;
            }
            AiProgress.main(() -> {
                final Activity owner = screenOwner;
                if (!cancelled && (owner == null || !Apps.isDestroyedActivity(owner))) {
                    r.run();
                }
            });
        }

        /** A message for the reader when no screen shows it: a toast from a screen's job; a task's notification says it instead. */
        protected void tell(String text) {
            if (activity != null) {
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            }
        }

        protected void toast(String text) {
            ui(() -> tell(text));
        }

        protected String string(int res, Object... args) {
            return context.getString(res, args);
        }

        protected String message(Throwable e) {
            if (e instanceof AiClient.AiException && ((AiClient.AiException) e).refusal
                    && !string(R.string.ai_declined).equals(e.getMessage())) {
                return string(R.string.ai_declined) + ": " + e.getMessage();
            }
            return TxtUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
        }

        protected String chapterTitle(BookChapters.Range range) {
            return TxtUtils.isNotEmpty(range.title) ? range.title : string(R.string.ai_pages, range.from + 1, range.to);
        }

        /** Plain text of pages from..to into {@code plain} and {@code lengths}, both indexed by page. */
        protected void read(BookText text, int from, int to, String[] plain, int[] lengths, Progress progress) throws Cancelled {
            for (int p = from; p < to; p++) {
                checkCancelled();
                if (progress != null && (p - from) % 10 == 0) {
                    progress.on(string(R.string.ai_reading_pages, p - from + 1, to - from));
                }
                plain[p] = BookText.plain(text.text(p));
                lengths[p] = plain[p].length();
            }
        }

        protected static String join(String[] plain, int from, int to) {
            final StringBuilder out = new StringBuilder();
            for (int p = from; p < to; p++) {
                if (TxtUtils.isNotEmpty(plain[p])) {
                    if (out.length() > 0) {
                        out.append("\n\n");
                    }
                    out.append(plain[p]);
                }
            }
            return out.toString();
        }
    }

    public interface Progress {
        void on(String status);
    }

    // --- summaries ---

    /** What a summary dialog shows; called on the main thread. */
    public interface SummaryScreen {
        void onTitle(String title);

        void onStatus(String status);

        /** The final answer starts: what was shown before goes. */
        void onAnswerStart();

        void onText(String delta);

        /** @param note why it was not saved (cut off), or null */
        void onDone(AiStore.Summary summary, boolean fromStore, String note);

        void onError(String message);

        void onNeedSettings();
    }

    /** Summary of the chapter at the reader's page, or of the whole book. */
    public static class SummaryJob extends Job {
        private final boolean wholeBook;
        private final boolean regenerate;
        private volatile SummaryScreen screen;
        /** A chapter given by the caller (a study chapter), else the one at the reader's page. */
        private final BookChapters.Range given;

        public SummaryJob(Activity activity, Target target, boolean wholeBook, boolean regenerate, SummaryScreen screen) {
            this(activity, target, wholeBook, regenerate, screen, null);
        }

        public SummaryJob(Activity activity, Target target, boolean wholeBook, boolean regenerate, SummaryScreen screen,
                          BookChapters.Range given) {
            super(activity, target);
            this.wholeBook = wholeBook;
            this.regenerate = regenerate;
            this.screen = screen;
            this.given = given;
        }

        /** The task of a whole-book summary (design-2026-10-25.md): its part summaries are kept as they come. */
        public SummaryJob(android.content.Context context, Target target, boolean regenerate) {
            super(context, target);
            this.wholeBook = true;
            this.regenerate = regenerate;
            this.given = null;
        }

        @Override
        protected void setTaskScreen(Object screen) {
            this.screen = screen instanceof SummaryScreen ? (SummaryScreen) screen : null;
        }

        @Override
        protected void showStatus(String text) {
            status(text);
        }

        @Override
        protected void retrying(String status) {
            super.retrying(status);
            // what streamed before the failure goes: the answer comes again from its start
            show(SummaryScreen::onAnswerStart);
        }

        /** Something for the screen, when one is attached. */
        private void show(java.util.function.Consumer<SummaryScreen> call) {
            ui(() -> {
                final SummaryScreen s = screen;
                if (s != null) {
                    call.accept(s);
                }
            });
        }

        @Override
        protected void failed(String message) {
            show(s -> s.onError(message));
        }

        private void status(String status) {
            show(s -> s.onStatus(status));
        }

        @Override
        protected void work() throws Exception {
            if (!regenerate) {
                // a saved summary opens at once when its chapter is known without reading the book
                final BookChapters.Range known = wholeBook ? null : given != null ? given : knownChapter(target);
                final String key = wholeBook ? AiStore.BOOK : known == null ? null : known.key;
                final AiStore.Summary saved = key == null ? null : AiStore.get().load(target.name).summary(key);
                if (saved != null) {
                    final String title = wholeBook ? target.title : chapterTitle(known);
                    show(s -> {
                        s.onTitle(title);
                        s.onDone(saved, true, null);
                    });
                    return;
                }
            }
            status(string(R.string.ai_reading_book));
            try (BookText text = open()) {
                final int pages = text.pageCount();
                final List<BookChapters.Entry> toc = toc(target, text);
                final BookChapters.Range range = wholeBook
                        ? new BookChapters.Range(0, pages, target.title, AiStore.BOOK)
                        : given != null ? given : BookChapters.chapterAt(toc, target.page, pages, BookChapters.BLOCK_PAGES);
                final String title = wholeBook ? target.title : chapterTitle(range);
                show(s -> s.onTitle(title));

                if (!regenerate) {
                    final AiStore.Summary saved = AiStore.get().load(target.name).summary(range.key);
                    if (saved != null) {
                        show(s -> s.onDone(saved, true, null));
                        return;
                    }
                }
                if (!settings.isReady()) {
                    show(SummaryScreen::onNeedSettings);
                    return;
                }

                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                read(text, range.from, range.to, plain, lengths, this::status);
                final long total = BookChapters.length(lengths, range.from, range.to);
                if (total == 0) {
                    failWith(string(R.string.ai_no_text));
                    return;
                }

                final String system = AiPrompts.withMaster(settings.masterPrompt, AiPrompts.summarySystem(settings.answerLanguage));
                final AiClient.Output stream = delta -> show(s -> s.onText(delta));
                final String answer;
                if (total <= settings.maxChars) {
                    final String all = join(plain, range.from, range.to);
                    status(string(R.string.ai_writing));
                    show(SummaryScreen::onAnswerStart);
                    answer = ask(system, wholeBook ? AiPrompts.bookSummary(target.title, all)
                            : AiPrompts.chapterSummary(target.title, title, all), stream);
                } else {
                    // too long for one request: the parts first, then their summaries into one
                    final List<BookChapters.Range> chapters = wholeBook
                            ? BookChapters.chaptersOrBlocks(toc, pages, BookChapters.BLOCK_PAGES)
                            : Collections.singletonList(range);
                    final List<BookChapters.Range> parts = BookChapters.parts(chapters, lengths, settings.maxChars);
                    // a whole book's parts are kept as they come: a run broken off goes on from the next one
                    final AiParts kept = wholeBook ? AiParts.open(AiParts.folder(context), target.name,
                            AiParts.key(target, settings.model, settings.maxChars, parts.size())) : null;
                    List<String> summaries = new ArrayList<>();
                    for (int i = 0; i < parts.size(); i++) {
                        final String done = kept == null ? null : kept.get(i);
                        if (done != null) {
                            summaries.add(done);
                            continue;
                        }
                        status(string(R.string.ai_summarizing_part, i + 1, parts.size()));
                        step(string(R.string.ai_summarizing_part, i + 1, parts.size()), i + 1, parts.size());
                        final BookChapters.Range part = parts.get(i);
                        final String summary = ask(system, AiPrompts.partSummary(target.title, part.title,
                                join(plain, part.from, part.to)), null);
                        summaries.add(summary);
                        if (kept != null) {
                            kept.put(i, summary);
                        }
                    }
                    while (summaries.size() > 1 && length(summaries) > settings.maxChars) {
                        final List<String> condensed = condense(system, summaries);
                        if (condensed.size() >= summaries.size()) {
                            break;
                        }
                        summaries = condensed;
                    }
                    status(string(R.string.ai_writing));
                    show(SummaryScreen::onAnswerStart);
                    answer = ask(system, AiPrompts.joinSummaries(target.title,
                            wholeBook ? AiPrompts.BOOK_TASK : AiPrompts.CHAPTER_TASK, summaries), stream);
                }

                final AiStore.Summary summary = new AiStore.Summary(range.key, title, answer.trim(),
                        settings.model, System.currentTimeMillis());
                if (wasCutOff()) {
                    result = string(R.string.ai_cut_off);
                    show(s -> s.onDone(summary, false, string(R.string.ai_cut_off)));
                    return;
                }
                AiHighlights.get().set(target.name, AiStore.get().update(target.name, book -> book.putSummary(summary)));
                if (wholeBook) {
                    AiParts.open(AiParts.folder(context), target.name, "").clear();
                }
                result = string(R.string.ai_task_summary_done);
                show(s -> s.onDone(summary, false, null));
            }
        }

        /** Groups of summaries that fit in one request, each group summarized into one. */
        private List<String> condense(String system, List<String> summaries) throws IOException, Cancelled {
            final List<String> out = new ArrayList<>();
            List<String> group = new ArrayList<>();
            long size = 0;
            for (String s : summaries) {
                if (!group.isEmpty() && size + s.length() > settings.maxChars) {
                    out.add(group.size() == 1 ? group.get(0)
                            : ask(system, AiPrompts.joinSummaries(target.title, AiPrompts.PARTS_TASK, group), null));
                    group = new ArrayList<>();
                    size = 0;
                }
                group.add(s);
                size += s.length();
            }
            if (!group.isEmpty()) {
                out.add(group.size() == 1 ? group.get(0)
                        : ask(system, AiPrompts.joinSummaries(target.title, AiPrompts.PARTS_TASK, group), null));
            }
            return out;
        }

        private static long length(List<String> texts) {
            long n = 0;
            for (String t : texts) {
                n += t.length();
            }
            return n;
        }
    }

    // --- explaining a mark ---

    /** What the explanation of a mark shows; called on the main thread. */
    public interface ExplainScreen {
        void onText(String delta);

        void onDone();

        void onError(String message);

        void onNeedSettings();
    }

    /** One request about a tapped mark, streamed and not stored. */
    public static class ExplainJob extends Job {
        private final String user;
        private final ExplainScreen screen;

        public ExplainJob(Activity activity, Target target, String user, ExplainScreen screen) {
            super(activity, target);
            this.user = user;
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
            ask(AiPrompts.withMaster(settings.masterPrompt, AiPrompts.explainSystem(settings.answerLanguage)), user,
                    delta -> ui(() -> screen.onText(delta)));
            ui(screen::onDone);
        }
    }

    // --- key passages ---

    /** What the key passages progress shows; called on the main thread. */
    public interface HighlightScreen {
        void onStatus(String status);

        void onFinished(String message);

        void onError(String message);

        void onNeedSettings();
    }

    /** Text of a chapter asked for its ideas at once: a model lists fewer of them from longer text. */
    static final int IDEAS_PART_CHARS = 20000;

    /**
     * Key passages of the chapter at the reader's page, or of every chapter not marked yet: every
     * idea, marked by its key sentences and must-read paragraphs (IdeaMarks).
     */
    public static class HighlightJob extends Job {
        private final boolean wholeBook;
        private volatile HighlightScreen screen;
        private volatile String status = "";
        /** A chapter given by the caller (a study chapter), else the one at the reader's page. */
        private final BookChapters.Range given;

        public HighlightJob(Activity activity, Target target, boolean wholeBook, HighlightScreen screen) {
            this(activity, target, wholeBook, screen, null);
        }

        public HighlightJob(Activity activity, Target target, boolean wholeBook, HighlightScreen screen,
                            BookChapters.Range given) {
            super(activity, target);
            this.wholeBook = wholeBook;
            this.screen = screen;
            this.given = given;
        }

        /** The task of a key passages run (design-2026-10-25.md). */
        public HighlightJob(android.content.Context context, Target target, boolean wholeBook, BookChapters.Range given) {
            super(context, target);
            this.wholeBook = wholeBook;
            this.given = given;
        }

        public String getStatus() {
            return status;
        }

        /** The progress is shown again (or hidden with null): the run goes on either way. */
        public void setScreen(HighlightScreen screen) {
            this.screen = screen;
        }

        @Override
        protected void setTaskScreen(Object screen) {
            setScreen(screen instanceof HighlightScreen ? (HighlightScreen) screen : null);
            if (this.screen != null && !status.isEmpty()) {
                status(status);
            }
        }

        @Override
        protected void showStatus(String text) {
            status(text);
        }

        private void status(String text) {
            status = text;
            ui(() -> {
                final HighlightScreen s = screen;
                if (s != null) {
                    s.onStatus(text);
                }
            });
        }

        private void finished(String message) {
            result = message;
            ui(() -> {
                final HighlightScreen s = screen;
                if (s != null) {
                    s.onFinished(message);
                } else {
                    tell(message);
                }
            });
        }

        @Override
        protected void failed(String message) {
            ui(() -> {
                final HighlightScreen s = screen;
                if (s != null) {
                    s.onError(message);
                } else {
                    tell(message);
                }
            });
        }

        @Override
        protected void work() throws Exception {
            if (!settings.isReady()) {
                ui(() -> {
                    final HighlightScreen s = screen;
                    if (s != null) {
                        s.onNeedSettings();
                    }
                });
                return;
            }
            status(string(R.string.ai_reading_book));
            try (BookText text = open()) {
                final int pages = text.pageCount();
                final List<BookChapters.Entry> toc = toc(target, text);
                final List<BookChapters.Range> todo = new ArrayList<>();
                if (wholeBook) {
                    final AiStore.Book done = AiStore.get().load(target.name);
                    for (BookChapters.Range chapter : BookChapters.chaptersOrBlocks(toc, pages, BookChapters.BLOCK_PAGES)) {
                        // key passages from before ideas are done again
                        final AiStore.Chapter had = done.chapter(chapter.key);
                        if (had == null || had.ideas.isEmpty()) {
                            todo.add(chapter);
                        }
                    }
                    if (todo.isEmpty()) {
                        finished(string(R.string.ai_all_done));
                        return;
                    }
                } else {
                    todo.add(given != null ? given : BookChapters.chapterAt(toc, target.page, pages, BookChapters.BLOCK_PAGES));
                }

                // the reader's goal decides whether "apply" is asked for (AiCategory)
                final String goal = AiStore.get().load(target.name).goal;
                final boolean hasGoal = goal != null && !goal.trim().isEmpty();
                final String system = AiPrompts.withMaster(settings.masterPrompt, AiPrompts.ideasSystem(hasGoal, settings.answerLanguage));
                final String repairSystem = AiPrompts.withMaster(settings.masterPrompt, AiPrompts.repairSystem());
                int marked = 0;
                int notLocated = 0;
                int chapters = 0;
                int withoutText = 0;
                boolean anyCutOff = false;
                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                for (int i = 0; i < todo.size(); i++) {
                    final BookChapters.Range chapter = todo.get(i);
                    status(string(R.string.ai_chapter_progress, i + 1, todo.size(), marked));
                    step(status, i + 1, todo.size());

                    final List<String> words = new ArrayList<>();
                    final List<Integer> wordPage = new ArrayList<>();
                    for (int p = chapter.from; p < chapter.to; p++) {
                        checkCancelled();
                        final TextWord[][] page = text.text(p);
                        for (String w : BookText.words(page)) {
                            words.add(w);
                            wordPage.add(p);
                        }
                        plain[p] = BookText.plain(page);
                        lengths[p] = plain[p].length();
                    }
                    if (BookChapters.length(lengths, chapter.from, chapter.to) == 0) {
                        withoutText++;
                        continue;
                    }

                    // every idea of each part, with its anchors (design-2026-10-21-layered-marks.md)
                    final List<BookChapters.Range> parts = BookChapters.parts(Collections.singletonList(chapter), lengths,
                            Math.min(settings.maxChars, IDEAS_PART_CHARS));
                    final List<List<AiPrompts.Idea>> partIdeas = new ArrayList<>();
                    boolean cutOff = false;
                    for (BookChapters.Range part : parts) {
                        final AiPrompts.Ideas answer = AiPrompts.parseIdeas(ask(system, AiPrompts.ideas(target.title,
                                chapterTitle(chapter), join(plain, part.from, part.to), hasGoal ? goal : ""), null));
                        cutOff |= answer.cutOff || wasCutOff();
                        final List<AiPrompts.Idea> ideas = new ArrayList<>();
                        for (AiPrompts.Idea idea : answer.ideas) {
                            // without a goal there is nothing to judge "apply" against
                            if (idea.category != AiCategory.APPLY || hasGoal) {
                                ideas.add(idea);
                            }
                        }
                        partIdeas.add(ideas);
                    }
                    final List<AiPrompts.Idea> ideas = IdeaMarks.join(partIdeas);
                    final IdeaMarks.Placed placed = IdeaMarks.place(words, wordPage, ideas);

                    // one more try, part by part, for the ideas none of whose quotes are in the book
                    int first = 0;
                    for (int k = 0; k < parts.size(); k++) {
                        final List<AiPrompts.Idea> missing = IdeaMarks.unmarked(ideas.subList(first, first + partIdeas.get(k).size()), placed);
                        first += partIdeas.get(k).size();
                        if (missing.isEmpty()) {
                            continue;
                        }
                        status(string(R.string.ai_ideas_finding, i + 1, todo.size(), missing.size()));
                        final BookChapters.Range part = parts.get(k);
                        IdeaMarks.repair(placed, words, wordPage, missing, AiPrompts.parseRepair(ask(repairSystem,
                                AiPrompts.repair(target.title, chapterTitle(chapter), join(plain, part.from, part.to), missing), null)));
                    }
                    for (int p = chapter.from; p < chapter.to; p++) {
                        plain[p] = null;
                    }
                    if (ideas.isEmpty()) {
                        anyCutOff |= cutOff;
                        continue;
                    }
                    checkCancelled();
                    final AiStore.Chapter done = IdeaMarks.chapter(chapter.key, chapter.title, settings.model,
                            System.currentTimeMillis(), ideas, placed, wordPage, cutOff);
                    final AiStore.Book book = AiStore.get().update(target.name, b -> b.putChapter(done));
                    AiHighlights.get().set(target.name, book);
                    marked += ideas.size() - done.notLocated();
                    notLocated += done.notLocated();
                    anyCutOff |= cutOff;
                    chapters++;
                }

                final StringBuilder message = new StringBuilder(marked == 0
                        ? string(R.string.ai_highlights_none)
                        : string(R.string.ai_highlights_done, marked, chapters));
                if (notLocated > 0) {
                    message.append(' ').append(string(R.string.ai_ideas_not_located, notLocated));
                }
                if (anyCutOff) {
                    message.append(' ').append(string(R.string.ai_ideas_cut_off));
                }
                if (withoutText > 0) {
                    message.append(' ').append(string(R.string.ai_chapters_without_text, withoutText));
                }
                finished(message.toString());
            }
        }
    }

    // --- removing, saving into the PDF ---

    /** Removes the AI highlights of the chapter at the reader's page, or of the whole book. */
    public static class RemoveJob extends Job {
        private final boolean wholeBook;

        public RemoveJob(Activity activity, Target target, boolean wholeBook) {
            super(activity, target);
            this.wholeBook = wholeBook;
        }

        @Override
        protected void failed(String message) {
            toast(message);
        }

        @Override
        protected void work() throws Exception {
            final AiStore.Book book;
            if (wholeBook) {
                book = AiStore.get().update(target.name, b -> b.highlights.clear());
            } else {
                final String key;
                try (BookText text = open()) {
                    key = BookChapters.chapterAt(toc(target, text), target.page, text.pageCount(), BookChapters.BLOCK_PAGES).key;
                }
                book = AiStore.get().update(target.name, b -> b.removeChapter(key));
            }
            AiHighlights.get().set(target.name, book);
            toast(string(R.string.ai_removed));
        }
    }

    /**
     * Turns the AI highlights into highlight annotations of the PDF, in the AI color. What went
     * into the PDF is no longer drawn by Librera; its chapters stay known as done.
     */
    public static class SaveIntoPdfJob extends Job {
        private final DocumentController dc;

        public SaveIntoPdfJob(Activity activity, DocumentController dc) {
            super(activity, Target.of(dc));
            this.dc = dc;
        }

        @Override
        protected void failed(String message) {
            toast(message);
        }

        @Override
        protected void work() throws Exception {
            final AiStore.Book data = AiStore.get().load(target.name);
            final Map<Integer, List<String>> keysByPage = new TreeMap<>();
            final Map<String, AiCategory> categoryOf = new HashMap<>();
            for (AiStore.Chapter chapter : data.highlights) {
                for (AiStore.Quote quote : chapter.quotes) {
                    final String key = QuoteMatcher.key(quote.text);
                    if (key != null && quote.page >= 0) {
                        categoryOf.put(key, quote.category);
                        // a quote may run over to the next page
                        add(keysByPage, quote.page, key);
                        add(keysByPage, quote.page + 1, key);
                    }
                }
            }
            if (keysByPage.isEmpty()) {
                toast(string(R.string.ai_no_highlights));
                return;
            }
            final Set<String> placed = new HashSet<>();
            try (BookText text = open()) {
                for (Map.Entry<Integer, List<String>> entry : keysByPage.entrySet()) {
                    checkCancelled();
                    final int page = entry.getKey();
                    if (page >= text.pageCount()) {
                        continue;
                    }
                    final List<TextWord> words = TTSHighlight.words(text.text(page));
                    final List<String> strings = new ArrayList<>(words.size());
                    for (TextWord w : words) {
                        strings.add(w.getWord());
                    }
                    final QuoteMatcher.Words pageWords = new QuoteMatcher.Words(strings);
                    // one highlight per category color on the page
                    final Map<AiCategory, List<RectF>> lines = new EnumMap<>(AiCategory.class);
                    for (String key : entry.getValue()) {
                        for (int[] range : QuoteMatcher.onPage(pageWords, Collections.singletonList(key))) {
                            final AiCategory category = categoryOf.get(key);
                            List<RectF> list = lines.get(category);
                            if (list == null) {
                                list = new ArrayList<>();
                                lines.put(category, list);
                            }
                            list.addAll(BookText.lines(words.subList(range[0], range[1] + 1)));
                            placed.add(key);
                        }
                    }
                    for (Map.Entry<AiCategory, List<RectF>> category : lines.entrySet()) {
                        final int color = category.getKey().color;
                        final List<RectF> rects = category.getValue();
                        ui(() -> dc.addHighlightLines(page, rects, color));
                    }
                }
            }
            checkCancelled();
            final AiStore.Book book = AiStore.get().update(target.name, b -> {
                for (AiStore.Chapter chapter : new ArrayList<>(b.highlights)) {
                    final List<AiStore.Quote> left = new ArrayList<>();
                    for (AiStore.Quote quote : chapter.quotes) {
                        if (!placed.contains(QuoteMatcher.key(quote.text))) {
                            left.add(quote);
                        }
                    }
                    b.putChapter(chapter.withQuotes(left));
                }
            });
            AiHighlights.get().set(target.name, book);
            final int count = placed.size();
            ui(() -> {
                dc.saveAnnotationsToFile();
                Toast.makeText(activity, string(R.string.ai_saved_into_pdf, count), Toast.LENGTH_LONG).show();
            });
        }

        private static void add(Map<Integer, List<String>> map, int page, String key) {
            List<String> keys = map.get(page);
            if (keys == null) {
                keys = new ArrayList<>();
                map.put(page, keys);
            }
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
    }
}
