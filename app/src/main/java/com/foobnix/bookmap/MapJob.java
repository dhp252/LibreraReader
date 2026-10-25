package com.foobnix.bookmap;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the map of a book (design-2026-10-25.md): each chapter not mapped yet is read once
 * (its text, or with the setting on the ideas key passages already found in it), its moments are
 * found in the book and saved at once, then one whole-book request picks the lanes, links and
 * key moments. Cancelled, the next run carries on from the chapters saved.
 */
public final class MapJob extends AiJobs.Job {

    /** Text sent per request: long chapters go in parts (as key passages). */
    static final int PART_CHARS = 20000;

    /** What the map screen shows of a build; called on the main thread. */
    public interface Screen {
        void onStatus(String status);

        void onDone(String message);

        void onError(String message);

        void onNeedSettings();
    }

    /** A chapter to map, its place in the book, and where its moments come from. */
    static final class Todo {
        final BookChapters.Range range;
        final int n;
        final boolean fromIdeas;

        Todo(BookChapters.Range range, int n, boolean fromIdeas) {
            this.range = range;
            this.n = n;
            this.fromIdeas = fromIdeas;
        }
    }

    private final boolean bookOnly;
    private volatile Screen screen;
    private volatile String status = "";

    /**
     * The task of a build (F-004 design-2026-10-25.md): it goes on with the book closed and
     * resumes from the chapters saved.
     *
     * @param bookOnly only the whole-book request (Rebuild map, Update map)
     */
    public MapJob(Context context, AiJobs.Target target, boolean bookOnly) {
        super(context, target);
        this.bookOnly = bookOnly;
    }

    public String getStatus() {
        return status;
    }

    /** The progress is shown again, or hidden with null: the build goes on either way. */
    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    @Override
    protected void setTaskScreen(Object screen) {
        setScreen(screen instanceof Screen ? (Screen) screen : null);
        if (this.screen != null && !status.isEmpty()) {
            status(status);
        }
    }

    @Override
    protected void showStatus(String text) {
        status(text);
    }

    /**
     * The chapters still to map, in the book's order: those without a saved map chapter. With
     * {@code fromIdeas}, a chapter whose key passages have ideas sends those instead of its text.
     */
    static List<Todo> todo(List<BookChapters.Range> ranges, MapBook saved, AiStore.Book ai, boolean fromIdeas) {
        final List<Todo> out = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            final BookChapters.Range r = ranges.get(i);
            if (saved.chapter(r.key) != null) {
                continue;
            }
            final AiStore.Chapter marked = ai == null ? null : ai.chapter(r.key);
            out.add(new Todo(r, i + 1, fromIdeas && marked != null && !marked.ideas.isEmpty()));
        }
        return out;
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

    private void done(String message) {
        result = message;
        ui(() -> {
            final Screen s = screen;
            if (s != null) {
                s.onDone(message);
            } else {
                tell(message);
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
                tell(message);
            }
        });
    }

    @Override
    protected void work() throws Exception {
        if (!settings.isReady()) {
            ui(() -> {
                final Screen s = screen;
                if (s != null) {
                    s.onNeedSettings();
                }
            });
            return;
        }
        final MapStore store = MapStore.get();
        int read = 0;
        int found = 0;
        int dropped = 0;
        int withoutText = 0;
        boolean cutOff = false;
        if (!bookOnly) {
            status(string(R.string.ai_reading_book));
            try (BookText text = open()) {
                final int pages = text.pageCount();
                final List<BookChapters.Range> ranges = BookChapters.chaptersOrBlocks(AiJobs.toc(target, text), pages,
                        BookChapters.BLOCK_PAGES);
                final AiStore.Book ai = AiStore.get().load(target.name);
                final List<Todo> todo = todo(ranges, store.load(target.name), ai, settings.mapFromIdeas);
                final String chapterSystem = AiPrompts.withMaster(settings.masterPrompt, MapPrompts.chapterSystem(settings.answerLanguage));
                final String ideasSystem = AiPrompts.withMaster(settings.masterPrompt, MapPrompts.ideasSystem(settings.answerLanguage));
                final String[] plain = new String[pages];
                final int[] lengths = new int[pages];
                for (int i = 0; i < todo.size(); i++) {
                    final Todo t = todo.get(i);
                    final BookChapters.Range r = t.range;
                    status(string(R.string.map_progress, i + 1, todo.size(), found));
                    step(status, i + 1, todo.size() + 1);

                    final List<String> words = new ArrayList<>();
                    final List<Integer> wordPage = new ArrayList<>();
                    for (int p = r.from; p < r.to; p++) {
                        checkCancelled();
                        final TextWord[][] page = text.text(p);
                        for (String w : BookText.words(page)) {
                            words.add(w);
                            wordPage.add(p);
                        }
                        plain[p] = BookText.plain(page);
                        lengths[p] = plain[p].length();
                    }
                    final long chars = BookChapters.length(lengths, r.from, r.to);
                    final String title = chapterTitle(r);
                    final MapPrompts.Extract extract = new MapPrompts.Extract();
                    List<MapPrompts.Draft> drafts = Collections.emptyList();
                    if (chars > 0 && t.fromIdeas) {
                        final AiStore.Chapter marked = ai.chapter(r.key);
                        final MapPrompts.Extract e = MapPrompts.parseIdeas(ask(ideasSystem,
                                MapPrompts.ideas(target.title, title, marked.ideas), null));
                        e.cutOff |= wasCutOff();
                        extract.add(e);
                        drafts = MapPlace.fromIdeas(marked, extract.moments);
                    } else if (chars > 0) {
                        final List<BookChapters.Range> parts = BookChapters.parts(Collections.singletonList(r), lengths,
                                Math.min(settings.maxChars, PART_CHARS));
                        for (int k = 0; k < parts.size(); k++) {
                            final BookChapters.Range part = parts.get(k);
                            final MapPrompts.Extract e = MapPrompts.parseChapter(ask(chapterSystem,
                                    MapPrompts.chapter(target.title, title, k + 1, parts.size(), join(plain, part.from, part.to)), null));
                            e.cutOff |= wasCutOff();
                            extract.add(e);
                        }
                        drafts = extract.moments;
                    }
                    final MapPlace.Placed placed = MapPlace.place(words, wordPage, drafts);
                    for (int p = r.from; p < r.to; p++) {
                        plain[p] = null;
                    }
                    checkCancelled();
                    final boolean noText = chars == 0;
                    store.update(target.name, b -> {
                        final MapBook.Chapter c = b.chapterOrNew(r.key);
                        c.n = t.n;
                        c.title = title;
                        c.from = r.from;
                        c.to = r.to;
                        c.chars = chars;
                        c.source = t.fromIdeas ? MapBook.IDEAS : MapBook.OWN;
                        c.model = settings.model;
                        c.cutOff = extract.cutOff;
                        c.noText = noText;
                        c.dropped = placed.dropped;
                        c.threads.clear();
                        c.threads.addAll(extract.threads);
                        c.moments.clear();
                        c.moments.addAll(placed.moments);
                    });
                    if (noText) {
                        withoutText++;
                        continue;
                    }
                    read++;
                    found += placed.moments.size();
                    dropped += placed.dropped;
                    cutOff |= extract.cutOff;
                }
            }
        }

        // the whole book, from the saved chapters only
        checkCancelled();
        final List<MapBook.Chapter> chapters = new ArrayList<>();
        for (MapBook.Chapter c : store.load(target.name).ordered()) {
            if (!c.moments.isEmpty()) {
                chapters.add(c);
            }
        }
        if (chapters.isEmpty()) {
            done(withoutText > 0 ? string(R.string.ai_chapters_without_text, withoutText) : string(R.string.map_none));
            return;
        }
        status(string(R.string.map_drawing));
        step(status, 0, 0);
        final MapPrompts.Plan plan = MapPrompts.parseBook(ask(
                AiPrompts.withMaster(settings.masterPrompt, MapPrompts.bookSystem(settings.answerLanguage)),
                MapPrompts.book(target.title, chapters, settings.maxChars), null));
        checkCancelled();
        if (plan == null) {
            failWith(string(R.string.map_book_failed));
            return;
        }
        final List<String> seen = new ArrayList<>();
        for (MapBook.Chapter c : chapters) {
            seen.add(c.key);
        }
        store.update(target.name, b -> {
            b.premise = plan.premise;
            b.lanes.clear();
            b.lanes.addAll(plan.lanes);
            b.arcs.clear();
            b.arcs.addAll(plan.arcs);
            b.key.clear();
            b.key.addAll(plan.key);
            b.seen.clear();
            b.seen.addAll(seen);
            b.model = settings.model;
        });

        final StringBuilder message = new StringBuilder(bookOnly || read == 0
                ? string(R.string.map_updated)
                : string(R.string.map_done, read, found));
        if (dropped > 0) {
            message.append(' ').append(string(R.string.map_dropped, dropped));
        }
        if (cutOff) {
            message.append(' ').append(string(R.string.map_cut_off));
        }
        if (withoutText > 0) {
            message.append(' ').append(string(R.string.ai_chapters_without_text, withoutText));
        }
        done(message.toString());
    }
}
