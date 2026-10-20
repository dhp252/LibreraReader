package com.foobnix.study;

import android.app.Activity;
import android.widget.Toast;

import com.foobnix.ai.AiHighlightDialog;
import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSummaryDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;
import com.foobnix.stats.ReadingTracker;
import com.foobnix.stats.StatsFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Study mode in the open book: its study data, its chapters in the reader's layout, and the cards
 * a page change brings ({@link StudyCards}). One at a time, like the reading tracker: created on
 * the first page of a book, closed when its reader finishes.
 */
public final class StudySession {

    /** Study data is written one change after the other. */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    /**
     * A book opens on one page and may go to its saved place (or another device's) right after:
     * the first cards wait until it settled, so a chapter passed on the way gets none.
     */
    private static final long SETTLE_MS = 1200;

    private static StudySession current;
    /** The next page change follows another device: a jump, not a page turn. */
    private static boolean nextJumpSync;

    private final String path;
    private final String name;
    private Activity owner;
    private DocumentController dc;
    private volatile StudyBook book = new StudyBook();
    /** Book pages (0-based) per chapter in the reader's layout; null until known. */
    private volatile List<BookChapters.Range> chapters;
    /** Cards come once the data is loaded and the book settled on its page (see {@link #SETTLE_MS}). */
    private boolean ready;
    /** The book page (0-based) shown last; -1 before the first. */
    private int lastPage = -1;
    /** When the book was read last (any device) before this time it was opened. */
    private final long lastRead;
    private StudyCardView card;
    /** Cards waiting behind the welcome-back card. */
    private List<StudyCards.Card> waiting = Collections.emptyList();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private StudySession(DocumentController dc, String path, long lastRead) {
        this.dc = dc;
        this.owner = dc.getActivity();
        this.path = path;
        this.name = AiHighlights.bookName(path);
        this.lastRead = lastRead;
    }

    // ------------------------------------------------------------------ hooks

    /**
     * The reader shows a page (DocumentController.saveCurrentPage).
     *
     * @param lastRead when the book's position changed last before this page, on any device
     */
    public static void pageShown(DocumentController dc, long lastRead) {
        try {
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0) {
                return;
            }
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                // cards are views: the main thread decides
                dc.getActivity().runOnUiThread(() -> pageShown(dc, lastRead));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || !current.path.equals(path)) {
                if (current != null) {
                    current.close();
                }
                current = new StudySession(dc, path, lastRead);
                current.load(true);
            } else if (current.dc != dc) {
                // the same book in a re-created reader (rotation): the card lived in the old one
                current.removeCard();
                current.dc = dc;
                current.owner = dc.getActivity();
            }
            current.onPage(bookPage(dc));
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The next page change is the open book following another device. */
    public static void nextJumpIsSync() {
        nextJumpSync = true;
    }

    /** The reader is gone; a re-created one (rotation) keeps the book. */
    public static void destroyed(Activity owner) {
        if (current != null && current.owner == owner) {
            current.removeCard();
            if (owner.isFinishing()) {
                current.close();
                current = null;
            }
        }
    }

    /** The study data of a book changed elsewhere (a sync, a preview): the open book reads it again. */
    public static void reloadIfOpen(String bookName) {
        final StudySession s = current;
        if (s != null && s.name.equals(bookName)) {
            s.post(() -> s.load(false));
        }
    }

    /** The session of the book this reader shows, or null. */
    public static StudySession of(DocumentController dc) {
        final StudySession s = current;
        return s != null && dc != null && dc.getCurrentBook() != null && s.path.equals(dc.getCurrentBook().getPath()) ? s : null;
    }

    // ------------------------------------------------------------------ state

    public StudyBook book() {
        return book;
    }

    /** The chapters to study (front matter left out), in order; empty until known. */
    public List<BookChapters.Range> studyChapters() {
        final List<BookChapters.Range> all = chapters;
        final List<BookChapters.Range> out = new ArrayList<>();
        if (all != null) {
            for (BookChapters.Range r : all) {
                if (!StudyChapters.isFront(r) && book.chapter(r.key) != null) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** The chapter the reader is in, or null. */
    public BookChapters.Range currentChapter() {
        final List<BookChapters.Range> all = chapters;
        final BookChapters.Range r = all == null || lastPage < 0 ? null : StudyChapters.at(all, lastPage);
        return r == null || StudyChapters.isFront(r) ? null : r;
    }

    public String bookName() {
        return name;
    }

    public DocumentController controller() {
        return dc;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    // ------------------------------------------------------------------ actions

    /** Changes the study data: at once here, then in the store (one write after the other). */
    public void change(StudyStore.Change change) {
        change.apply(book);
        WRITER.execute(() -> {
            try {
                final StudyBook b = StudyStore.get().update(name, change);
                post(() -> {
                    book = b;
                    fire();
                });
            } catch (Exception e) {
                LOG.e(e);
                post(() -> Toast.makeText(owner, owner.getString(R.string.study_not_saved, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
        fire();
    }

    public void setLabel(String key, String label) {
        change(b -> {
            final StudyBook.Chapter c = b.chapter(key);
            if (c != null) {
                c.label = label;
                c.labelMine = true;
            }
        });
    }

    public void stopStudying() {
        hideCard();
        change(b -> b.on = false);
    }

    public void goTo(BookChapters.Range range) {
        if (dc != null) {
            dc.onGoToPage(ReadingTracker.viewPage(dc, range.from + 1));
        }
    }

    /** The questions of a chapter to answer (its after card does not come again). */
    public void answer(BookChapters.Range range) {
        final StudyBook.Chapter c = book.chapter(range.key);
        if (c == null) {
            return;
        }
        if (c.afterShown == 0) {
            final long now = System.currentTimeMillis();
            change(b -> {
                final StudyBook.Chapter x = b.chapter(range.key);
                if (x != null && x.afterShown == 0) {
                    x.afterShown = now;
                }
            });
        }
        StudyAnswerDialog.show(owner, this, range);
    }

    /** The chapter's branch of the map built again (answers and feedback stay), or for the first time. */
    public void rebuild(BookChapters.Range range) {
        final StudyBook.Chapter c = book.chapter(range.key);
        if (c == null) {
            return;
        }
        Toast.makeText(owner, R.string.study_building_map, Toast.LENGTH_SHORT).show();
        final List<String> none = new ArrayList<>();
        for (int i = 0; i < c.questions.size(); i++) {
            none.add("");
        }
        new StudyJobs.CheckJob(owner, AiJobs.Target.of(dc), range, c.questions, none, "", new StudyJobs.CheckScreen() {
            @Override
            public void onStatus(String status) {
            }

            @Override
            public void onText(String visible) {
            }

            @Override
            public void onDone(StudyBook.Chapter chapter) {
                Toast.makeText(owner, owner.getString(R.string.study_added_to_map, StudyBook.Node.count(chapter.branch)),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(owner, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onNeedSettings() {
                com.foobnix.ai.AiSettingsDialog.show(owner, null);
            }
        }).start();
    }

    /** The chapter with this key in the reader's layout, or null. */
    public BookChapters.Range rangeOf(String key) {
        return range(chapters, key);
    }

    /**
     * To the words of a quote: on a fixed layout its page; in a reflowing book it is looked for
     * around the page it was found on (the layout may have changed since). Without words, the page
     * or the chapter's start.
     */
    public void jumpTo(String quote, int page, BookChapters.Range range) {
        if (dc == null) {
            return;
        }
        final int hint = Math.max(range.from, Math.min(range.to - 1, page >= 0 ? page : range.from));
        com.foobnix.ai.AiJump.toQuote(dc, quote, hint, Math.max(0, range.from - 2), range.to + 1);
    }

    /** The reader's notes (marks with a note) by the chapter they are in, for the outline. Off the main thread. */
    public java.util.Map<String, List<StudyBook.Node>> notes() {
        final java.util.Map<String, List<StudyBook.Node>> out = new java.util.HashMap<>();
        final List<BookChapters.Range> all = chapters;
        if (all == null) {
            return out;
        }
        final List<com.foobnix.annotation.Mark> marks = new ArrayList<>(com.foobnix.annotation.MarksStore.get().load(name).values());
        Collections.sort(marks, (x, y) -> Integer.compare(x.firstPage(), y.firstPage()));
        for (com.foobnix.annotation.Mark m : marks) {
            if (!m.hasNote()) {
                continue;
            }
            final BookChapters.Range r = StudyChapters.at(all, m.firstPage());
            if (r == null || StudyChapters.isFront(r)) {
                continue;
            }
            final StudyBook.Node note = new StudyBook.Node(m.note.replace("\r", "").replace("\n", " ").trim(),
                    Outline.NOTE, m.quote);
            note.page = m.firstPage();
            List<StudyBook.Node> list = out.get(r.key);
            if (list == null) {
                list = new ArrayList<>();
                out.put(r.key, list);
            }
            list.add(note);
        }
        return out;
    }

    public void summary(BookChapters.Range range) {
        AiSummaryDialog.show(owner, dc, range);
    }

    /** Marks the chapter by its ideas: must-read paragraphs and key sentences (design-2026-10-21-layered-marks.md). */
    public void prepare(BookChapters.Range range) {
        AiHighlightDialog.start(owner, dc, range);
    }

    /** The chapter has its ideas marked. */
    public boolean prepared(BookChapters.Range range) {
        final com.foobnix.ai.AiStore.Chapter c = AiHighlights.get().data().chapter(range.key);
        return c != null && !c.ideas.isEmpty();
    }

    public void ideas(BookChapters.Range range) {
        com.foobnix.ai.AiIdeasDialog.showKey(owner, dc, range.key);
    }

    /** Minutes for a chapter at the reader's speed, by its label. */
    public int minutes(StudyBook.Chapter c) {
        return StudyTime.minutes(c.chars, c.label, ReadingSpeed.wpm(name));
    }

    public String duration(int minutes) {
        return StatsFormat.duration(owner, minutes * 60_000L);
    }

    /** "Chapter title", or "Pages 21–40" for a part of a book without a TOC. */
    public String title(BookChapters.Range range) {
        return range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                : owner.getString(R.string.ai_pages, range.from + 1, range.to);
    }

    // ------------------------------------------------------------------ inside

    /** The book page (0-based) the reader shows first. */
    private static int bookPage(DocumentController dc) {
        return ReadingTracker.realPages(dc, dc.getCurentPageFirst1())[0] - 1;
    }

    private void load(boolean opening) {
        final boolean fixed = !dc.isTextFormat();
        final int docPages = ReadingTracker.realPageCount(dc);
        final AiJobs.Target target = AiJobs.Target.of(dc);
        // a reload keeps the chapters found already while the parts studied by chapter are the same
        final List<BookChapters.Range> known = opening ? null : chapters;
        final java.util.Set<String> knownSplit = new java.util.HashSet<>(book.split);
        new Thread(() -> {
            final StudyBook b = StudyStore.get().load(name);
            List<BookChapters.Range> ch = null;
            if (b.on && b.hasPreview()) {
                ch = known != null && !fixed && b.split.equals(knownSplit) ? known : fixed ? stored(b, docPages) : null;
                if (ch == null) {
                    ch = fromToc(target, b);
                }
            }
            final List<BookChapters.Range> found = ch;
            post(() -> {
                final boolean hadPreview = book.on && book.hasPreview();
                book = b;
                chapters = found;
                fire();
                if (opening) {
                    owner.getWindow().getDecorView().postDelayed(() -> {
                        if (current == this && !Apps.isDestroyedActivity(owner)) {
                            ready = true;
                            opened(lastPage);
                        }
                    }, SETTLE_MS);
                } else if (ready && !hadPreview && b.on && b.hasPreview() && lastPage >= 0) {
                    // the preview just came: the chapter being read starts here
                    showCards(StudyCards.next(-1, lastPage, true, 0, found, b));
                }
            });
        }, "@T Study").start();
    }

    /** The chapters as previewed: right for fixed layouts (PDF, DjVu) while the page count is the same. */
    private static List<BookChapters.Range> stored(StudyBook b, int docPages) {
        final List<BookChapters.Range> out = new ArrayList<>();
        for (StudyBook.Chapter c : b.chapters.values()) {
            if (c.from < 0 || c.to <= c.from || c.to > docPages) {
                return null;
            }
            out.add(new BookChapters.Range(c.from, c.to, c.title, c.key));
        }
        if (out.isEmpty()) {
            return null;
        }
        Collections.sort(out, (x, y) -> Integer.compare(x.from, y.from));
        if (out.get(0).from > 0) {
            out.add(0, new BookChapters.Range(0, out.get(0).from, "", BookChapters.FRONT));
        }
        return out;
    }

    /** The chapters in the reader's layout, from the TOC (reflowing books: pages move with the font). */
    private static List<BookChapters.Range> fromToc(AiJobs.Target target, StudyBook b) {
        try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
            if (text == null) {
                return null;
            }
            return StudyChapters.cut(AiJobs.toc(target, text), text.pageCount(), b.split);
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    private void onPage(int page) {
        if (!ready) {
            lastPage = page;
            nextJumpSync = false;
            return;
        }
        if (page == lastPage) {
            return;
        }
        final boolean jump = nextJumpSync;
        nextJumpSync = false;
        final List<BookChapters.Range> all = chapters;
        final int step = dc.isBookMode() && ReadingTracker.twoPages(dc) ? 4 : 2;
        final List<StudyCards.Card> cards = all == null ? Collections.<StudyCards.Card>emptyList()
                : StudyCards.next(lastPage, page, jump, step, all, book);
        lastPage = page;
        // a page change takes the card away: it was there for a page
        hideCard();
        waiting = Collections.emptyList();
        showCards(cards);
    }

    /** The first page of this time the book is open: welcome back first, else its cards. */
    private void opened(int page) {
        final StudyBook b = book;
        final List<BookChapters.Range> all = chapters;
        if (page < 0 || all == null || !b.on || !b.hasPreview()) {
            return;
        }
        final List<StudyCards.Card> cards = StudyCards.next(-1, page, false, 0, all, b);
        if (StudyCards.welcomeBack(lastRead, System.currentTimeMillis())) {
            welcome(cards);
        } else {
            showCards(cards);
        }
    }

    private void welcome(List<StudyCards.Card> after) {
        final StudyCardView v = card();
        v.clear();
        v.title(owner.getString(R.string.study_welcome_back), null);
        v.line(owner.getString(R.string.study_last_read_days, StudyCards.daysAway(lastRead, System.currentTimeMillis())));
        waiting = after;
        v.button(owner.getString(R.string.study_look_back), true, () -> {
            waiting = Collections.emptyList();
            StudyMap.show(owner, this, true);
        });
        v.button(owner.getString(R.string.study_continue), true, this::showWaiting);
        v.onClose(this::showWaiting);
        v.show(AnnotationSession.isShownFor(dc));
    }

    private void showWaiting() {
        final List<StudyCards.Card> w = waiting;
        waiting = Collections.emptyList();
        showCards(w);
    }

    /** Shows the cards (an after card and a before card share one); false when there was nothing to show. */
    private boolean showCards(List<StudyCards.Card> cards) {
        if (cards == null || cards.isEmpty() || Apps.isDestroyedActivity(owner)) {
            return false;
        }
        final StudyCardView v = card();
        v.clear();
        final List<BookChapters.Range> all = chapters;
        final List<String> after = new ArrayList<>();
        final List<String> before = new ArrayList<>();
        boolean any = false;
        for (StudyCards.Card c : cards) {
            final BookChapters.Range range = range(all, c.key);
            final StudyBook.Chapter ch = book.chapter(c.key);
            if (range == null || ch == null) {
                continue;
            }
            if (any) {
                v.divider();
            }
            any = true;
            if (c.kind == StudyCards.AFTER) {
                afterCard(v, range, ch);
                after.add(c.key);
            } else {
                beforeCard(v, range, ch);
                before.add(c.key);
            }
        }
        if (!any) {
            return false;
        }
        v.show(AnnotationSession.isShownFor(dc));
        final long now = System.currentTimeMillis();
        change(b -> {
            for (String key : after) {
                final StudyBook.Chapter c = b.chapter(key);
                if (c != null && c.afterShown == 0) {
                    c.afterShown = now;
                }
            }
            for (String key : before) {
                final StudyBook.Chapter c = b.chapter(key);
                if (c != null && c.beforeShown == 0) {
                    c.beforeShown = now;
                }
            }
        });
        return true;
    }

    private void beforeCard(StudyCardView v, BookChapters.Range range, StudyBook.Chapter ch) {
        v.title(owner.getString(R.string.study_chapter_n, number(range), title(range)), ch.label);
        final int minutes = minutes(ch);
        final String time = StudyBook.SKIP.equals(ch.label) ? owner.getString(R.string.study_label_skip)
                : owner.getString(R.string.study_about, duration(minutes));
        v.line(ch.reason.isEmpty() ? time : time + " · " + ch.reason);
        if (!StudyBook.SKIP.equals(ch.label)) {
            v.numbered(owner.getString(R.string.study_read_for), ch.questions);
        }
        if (!StudyBook.READ.equals(ch.label)) {
            v.button(owner.getString(R.string.study_summary), false, () -> summary(range));
        }
        if (!StudyBook.SKIP.equals(ch.label)) {
            if (prepared(range)) {
                v.button(owner.getString(R.string.study_ideas), false, () -> ideas(range));
            } else {
                v.button(owner.getString(R.string.study_prepare), true, () -> prepare(range));
            }
        }
        if (StudyBook.SKIP.equals(ch.label)) {
            final BookChapters.Range next = next(range);
            if (next != null) {
                v.button(owner.getString(R.string.study_next_chapter), true, () -> goTo(next));
            }
        }
    }

    private void afterCard(StudyCardView v, BookChapters.Range range, StudyBook.Chapter ch) {
        v.title(owner.getString(R.string.study_done_with, title(range)), null);
        v.line(ch.questions.isEmpty() ? owner.getString(R.string.study_after_no_questions)
                : owner.getString(R.string.study_after_questions, ch.questions.size()));
        v.button(owner.getString(ch.questions.isEmpty() ? R.string.study_add_to_map : R.string.study_answer), true,
                () -> answer(range));
        v.button(owner.getString(R.string.study_later), true, () -> {
        });
    }

    /** 1-based place among the studied chapters. */
    private int number(BookChapters.Range range) {
        final List<BookChapters.Range> list = studyChapters();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key.equals(range.key)) {
                return i + 1;
            }
        }
        return 0;
    }

    private BookChapters.Range next(BookChapters.Range range) {
        final List<BookChapters.Range> list = studyChapters();
        for (int i = 0; i + 1 < list.size(); i++) {
            if (list.get(i).key.equals(range.key)) {
                return list.get(i + 1);
            }
        }
        return null;
    }

    private static BookChapters.Range range(List<BookChapters.Range> all, String key) {
        if (all != null) {
            for (BookChapters.Range r : all) {
                if (r.key.equals(key)) {
                    return r;
                }
            }
        }
        return null;
    }

    private StudyCardView card() {
        if (card == null) {
            card = new StudyCardView(owner);
        }
        return card;
    }

    private void hideCard() {
        if (card != null) {
            card.hide();
        }
    }

    private void removeCard() {
        if (card != null) {
            card.remove();
            card = null;
        }
    }

    private void close() {
        removeCard();
        listeners.clear();
    }

    private void fire() {
        for (Runnable r : listeners) {
            r.run();
        }
    }

    private void post(Runnable r) {
        final Activity a = owner;
        if (a != null && !Apps.isDestroyedActivity(a)) {
            a.runOnUiThread(r);
        }
    }
}
