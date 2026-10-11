package com.foobnix.ai;

import android.app.Activity;
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
    static List<BookChapters.Entry> toc(Target t, BookText text) {
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

    private static class Cancelled extends Exception {
    }

    /** A job on a thread of its own; its results go to the main thread while it is not cancelled. */
    abstract static class Job {
        final Activity activity;
        final Target target;
        final AiSettings settings;
        volatile boolean cancelled;
        private volatile AiClient client;
        String stopReason;

        Job(Activity activity, Target target) {
            this.activity = activity;
            this.target = target;
            this.settings = AiSettings.load(activity);
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

        public void cancel() {
            cancelled = true;
            final AiClient c = client;
            if (c != null) {
                c.cancel();
            }
        }

        abstract void work() throws Exception;

        abstract void failed(String message);

        void checkCancelled() throws Cancelled {
            if (cancelled || Apps.isDestroyedActivity(activity)) {
                throw new Cancelled();
            }
        }

        /** One request; {@code output} gets the answer as it streams, or null. */
        String ask(String system, String user, AiClient.Output output) throws IOException, Cancelled {
            checkCancelled();
            final AiClient c = new AiClient(settings);
            client = c;
            try {
                final String answer = c.complete(system, user, output);
                stopReason = c.getStopReason();
                if ("content_filter".equals(stopReason)) {
                    throw new AiClient.AiException(activity.getString(R.string.ai_declined), true);
                }
                return answer;
            } catch (AiClient.AiException e) {
                checkCancelled();
                throw e;
            } catch (IOException e) {
                // no answer at all: the server was not reached, or the connection broke
                checkCancelled();
                throw new AiClient.AiException(string(R.string.ai_network,
                        TxtUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()));
            } finally {
                client = null;
            }
        }

        boolean wasCutOff() {
            return "max_tokens".equals(stopReason) || "length".equals(stopReason);
        }

        BookText open() throws IOException {
            final BookText text = BookText.open(target.path, target.width, target.height, target.fontSize);
            if (text == null) {
                throw new AiClient.AiException(activity.getString(R.string.ai_cannot_open));
            }
            return text;
        }

        void ui(Runnable r) {
            if (cancelled) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!cancelled && !Apps.isDestroyedActivity(activity)) {
                    r.run();
                }
            });
        }

        void toast(String text) {
            ui(() -> Toast.makeText(activity, text, Toast.LENGTH_LONG).show());
        }

        String string(int res, Object... args) {
            return activity.getString(res, args);
        }

        String message(Throwable e) {
            if (e instanceof AiClient.AiException && ((AiClient.AiException) e).refusal
                    && !string(R.string.ai_declined).equals(e.getMessage())) {
                return string(R.string.ai_declined) + ": " + e.getMessage();
            }
            return TxtUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
        }

        String chapterTitle(BookChapters.Range range) {
            return TxtUtils.isNotEmpty(range.title) ? range.title : string(R.string.ai_pages, range.from + 1, range.to);
        }

        /** Plain text of pages from..to into {@code plain} and {@code lengths}, both indexed by page. */
        void read(BookText text, int from, int to, String[] plain, int[] lengths, Progress progress) throws Cancelled {
            for (int p = from; p < to; p++) {
                checkCancelled();
                if (progress != null && (p - from) % 10 == 0) {
                    progress.on(string(R.string.ai_reading_pages, p - from + 1, to - from));
                }
                plain[p] = BookText.plain(text.text(p));
                lengths[p] = plain[p].length();
            }
        }

        static String join(String[] plain, int from, int to) {
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

    interface Progress {
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
        private final SummaryScreen screen;

        public SummaryJob(Activity activity, Target target, boolean wholeBook, boolean regenerate, SummaryScreen screen) {
            super(activity, target);
            this.wholeBook = wholeBook;
            this.regenerate = regenerate;
            this.screen = screen;
        }

        @Override
        void failed(String message) {
            ui(() -> screen.onError(message));
        }

        private void status(String status) {
            ui(() -> screen.onStatus(status));
        }

        @Override
        void work() throws Exception {
            if (!regenerate) {
                // a saved summary opens at once when its chapter is known without reading the book
                final BookChapters.Range known = wholeBook ? null : knownChapter(target);
                final String key = wholeBook ? AiStore.BOOK : known == null ? null : known.key;
                final AiStore.Summary saved = key == null ? null : AiStore.get().load(target.name).summary(key);
                if (saved != null) {
                    final String title = wholeBook ? target.title : chapterTitle(known);
                    ui(() -> {
                        screen.onTitle(title);
                        screen.onDone(saved, true, null);
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
                        : BookChapters.chapterAt(toc, target.page, pages, BookChapters.BLOCK_PAGES);
                final String title = wholeBook ? target.title : chapterTitle(range);
                ui(() -> screen.onTitle(title));

                if (!regenerate) {
                    final AiStore.Summary saved = AiStore.get().load(target.name).summary(range.key);
                    if (saved != null) {
                        ui(() -> screen.onDone(saved, true, null));
                        return;
                    }
                }
                if (!settings.isReady()) {
                    ui(screen::onNeedSettings);
                    return;
                }

                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                read(text, range.from, range.to, plain, lengths, this::status);
                final long total = BookChapters.length(lengths, range.from, range.to);
                if (total == 0) {
                    failed(string(R.string.ai_no_text));
                    return;
                }

                final String system = AiPrompts.summarySystem(settings.answerLanguage);
                final AiClient.Output stream = delta -> ui(() -> screen.onText(delta));
                final String answer;
                if (total <= settings.maxChars) {
                    final String all = join(plain, range.from, range.to);
                    status(string(R.string.ai_writing));
                    ui(screen::onAnswerStart);
                    answer = ask(system, wholeBook ? AiPrompts.bookSummary(target.title, all)
                            : AiPrompts.chapterSummary(target.title, title, all), stream);
                } else {
                    // too long for one request: the parts first, then their summaries into one
                    final List<BookChapters.Range> chapters = wholeBook
                            ? BookChapters.chaptersOrBlocks(toc, pages, BookChapters.BLOCK_PAGES)
                            : Collections.singletonList(range);
                    final List<BookChapters.Range> parts = BookChapters.parts(chapters, lengths, settings.maxChars);
                    List<String> summaries = new ArrayList<>();
                    for (int i = 0; i < parts.size(); i++) {
                        status(string(R.string.ai_summarizing_part, i + 1, parts.size()));
                        final BookChapters.Range part = parts.get(i);
                        summaries.add(ask(system, AiPrompts.partSummary(target.title, part.title,
                                join(plain, part.from, part.to)), null));
                    }
                    while (summaries.size() > 1 && length(summaries) > settings.maxChars) {
                        final List<String> condensed = condense(system, summaries);
                        if (condensed.size() >= summaries.size()) {
                            break;
                        }
                        summaries = condensed;
                    }
                    status(string(R.string.ai_writing));
                    ui(screen::onAnswerStart);
                    answer = ask(system, AiPrompts.joinSummaries(target.title,
                            wholeBook ? AiPrompts.BOOK_TASK : AiPrompts.CHAPTER_TASK, summaries), stream);
                }

                final AiStore.Summary summary = new AiStore.Summary(range.key, title, answer.trim(),
                        settings.model, System.currentTimeMillis());
                if (wasCutOff()) {
                    ui(() -> screen.onDone(summary, false, string(R.string.ai_cut_off)));
                    return;
                }
                AiHighlights.get().set(target.name, AiStore.get().update(target.name, book -> book.putSummary(summary)));
                ui(() -> screen.onDone(summary, false, null));
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

    // --- key passages ---

    /** What the key passages progress shows; called on the main thread. */
    public interface HighlightScreen {
        void onStatus(String status);

        void onFinished(String message);

        void onError(String message);

        void onNeedSettings();
    }

    /** Key passages of the chapter at the reader's page, or of every chapter not marked yet. */
    public static class HighlightJob extends Job {
        private final boolean wholeBook;
        private volatile HighlightScreen screen;
        private volatile String status = "";

        public HighlightJob(Activity activity, Target target, boolean wholeBook, HighlightScreen screen) {
            super(activity, target);
            this.wholeBook = wholeBook;
            this.screen = screen;
        }

        public String getStatus() {
            return status;
        }

        /** The progress is shown again (or hidden with null): the run goes on either way. */
        public void setScreen(HighlightScreen screen) {
            this.screen = screen;
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
            ui(() -> {
                final HighlightScreen s = screen;
                if (s != null) {
                    s.onFinished(message);
                } else {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        void failed(String message) {
            ui(() -> {
                final HighlightScreen s = screen;
                if (s != null) {
                    s.onError(message);
                } else {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        void work() throws Exception {
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
                        if (done.chapter(chapter.key) == null) {
                            todo.add(chapter);
                        }
                    }
                    if (todo.isEmpty()) {
                        finished(string(R.string.ai_all_done));
                        return;
                    }
                } else {
                    todo.add(BookChapters.chapterAt(toc, target.page, pages, BookChapters.BLOCK_PAGES));
                }

                int passages = 0;
                int chapters = 0;
                int withoutText = 0;
                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                for (int i = 0; i < todo.size(); i++) {
                    final BookChapters.Range chapter = todo.get(i);
                    status(string(R.string.ai_chapter_progress, i + 1, todo.size(), passages));

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

                    final List<String> quotes = new ArrayList<>();
                    for (BookChapters.Range part : BookChapters.parts(Collections.singletonList(chapter), lengths, settings.maxChars)) {
                        final String partText = join(plain, part.from, part.to);
                        final String answer = ask(AiPrompts.keyPassagesSystem(), AiPrompts.keyPassages(target.title,
                                chapterTitle(chapter), partText, AiPrompts.passageCount(partText.length())), null);
                        quotes.addAll(AiPrompts.parseQuotes(answer));
                    }
                    for (int p = chapter.from; p < chapter.to; p++) {
                        plain[p] = null;
                    }

                    final List<AiStore.Quote> found = new ArrayList<>();
                    for (QuoteMatcher.Found f : QuoteMatcher.locateAll(words, quotes)) {
                        found.add(new AiStore.Quote(f.text, wordPage.get(f.firstWord)));
                    }
                    if (found.isEmpty()) {
                        continue;
                    }
                    checkCancelled();
                    final AiStore.Book book = AiStore.get().update(target.name, b -> b.putChapter(new AiStore.Chapter(
                            chapter.key, chapter.title, settings.model, System.currentTimeMillis(), found)));
                    AiHighlights.get().set(target.name, book);
                    passages += found.size();
                    chapters++;
                }

                final StringBuilder message = new StringBuilder(passages == 0
                        ? string(R.string.ai_highlights_none)
                        : string(R.string.ai_highlights_done, passages, chapters));
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
        void failed(String message) {
            toast(message);
        }

        @Override
        void work() throws Exception {
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
        void failed(String message) {
            toast(message);
        }

        @Override
        void work() throws Exception {
            final AiStore.Book data = AiStore.get().load(target.name);
            final Map<Integer, List<String>> keysByPage = new TreeMap<>();
            for (AiStore.Chapter chapter : data.highlights) {
                for (AiStore.Quote quote : chapter.quotes) {
                    final String key = QuoteMatcher.key(quote.text);
                    if (key != null && quote.page >= 0) {
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
            final int color = AiHighlights.parseColor(settings.highlightColor);
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
                    final List<RectF> lines = new ArrayList<>();
                    for (String key : entry.getValue()) {
                        for (int[] range : QuoteMatcher.onPage(pageWords, Collections.singletonList(key))) {
                            lines.addAll(BookText.lines(words.subList(range[0], range[1] + 1)));
                            placed.add(key);
                        }
                    }
                    if (!lines.isEmpty()) {
                        ui(() -> dc.addHighlightLines(page, lines, color));
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
                    b.putChapter(new AiStore.Chapter(chapter.key, chapter.title, chapter.model, chapter.time, left));
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
