package com.foobnix.assist.prequestions;

import android.app.Activity;
import android.os.Looper;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.SideMatter;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingTracker;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pre-questions in the open book (Assist.PRE_QUESTIONS): on entering a chapter for the first time,
 * a small card offers "3 quick guesses before this chapter" (never forced; not while Study's own
 * card shows); one request makes the questions ({@link PreQJob}), the reader guesses
 * ({@link PreQDialog}), and while reading, the page holding an answer brings its answer card and
 * the sentence is underlined ({@link PreQMarks}). One session at a time, for the book shown;
 * {@link #pageShown} is its only hook, called only while the switch is on.
 */
public final class PreQSession {

    /** A book may open on one page and go to its saved place right after (as Study's cards). */
    private static final long SETTLE_MS = 1200;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    private static final int CARD_NONE = 0;
    private static final int CARD_OFFER = 1;
    private static final int CARD_BUSY = 2;
    private static final int CARD_REVEAL = 3;

    private static PreQSession current;

    private final String path;
    private final String name;
    private WeakReference<DocumentController> dcRef;
    private WeakReference<Activity> ownerRef;

    /** The chapters in the reader's layout (TOC only: a book without one gets no offers); null until known. */
    private List<BookChapters.Range> chapters;
    private String chaptersLayout = "";
    private boolean readingChapters;
    /** The book's pre-questions by chapter key (main thread). */
    private final Map<String, PreQ.Chapter> items = new HashMap<>();
    private boolean ready;
    private int lastFrom = -1;
    private int lastTo = -1;
    private String lastChapter;
    private Runnable pendingOffer;
    private PreQCard card;
    private int cardKind = CARD_NONE;
    private PreQJob job;
    /** Answers reached and waiting behind the one shown: {chapter key, question index}. */
    private final List<Object[]> reveals = new ArrayList<>();
    private final Set<String> relocating = new HashSet<>();

    private PreQSession(DocumentController dc, String path) {
        this.path = path;
        this.name = AiHighlights.bookName(path);
        attach(dc);
    }

    // ------------------------------------------------------------------ the hook

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    public static void pageShown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0 || dc.getActivity() == null
                    || !Assist.PRE_QUESTIONS.isOn()) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || !current.path.equals(path)) {
                if (current != null) {
                    current.close();
                }
                current = new PreQSession(dc, path);
                current.load(dc);
            } else if (current.dc() != dc) {
                // the same book in a re-created reader: the card lived in the old one
                current.removeCard();
                current.attach(dc);
            }
            current.onPage(dc);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // ------------------------------------------------------------------ inside

    private void attach(DocumentController dc) {
        dcRef = new WeakReference<>(dc);
        ownerRef = new WeakReference<>(dc.getActivity());
    }

    private DocumentController dc() {
        return dcRef.get();
    }

    private Activity owner() {
        final Activity a = ownerRef.get();
        return a == null || Apps.isDestroyedActivity(a) ? null : a;
    }

    private static String layoutOf(DocumentController dc) {
        final AiJobs.Target t = AiJobs.Target.of(dc);
        return PreQ.layoutKey(!dc.isTextFormat(), t.width, t.height, t.fontSize);
    }

    private void load(DocumentController dc) {
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final String layout = layoutOf(dc);
        readingChapters = true;
        new Thread(() -> {
            final Map<String, PreQ.Chapter> stored = new HashMap<>();
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(name, PreQ.PREFIX).entrySet()) {
                stored.put(e.getKey().substring(PreQ.PREFIX.length()), PreQ.Chapter.fromJson(e.getValue()));
            }
            final List<BookChapters.Range> found = readChapters(target);
            post(() -> {
                readingChapters = false;
                items.putAll(stored);
                chapters = found;
                chaptersLayout = layout;
                marks();
                final Activity a = owner();
                if (a != null) {
                    a.getWindow().getDecorView().postDelayed(() -> {
                        if (current == this && owner() != null) {
                            ready = true;
                            settled();
                        }
                    }, SETTLE_MS);
                }
            });
        }, "@T PreQ").start();
    }

    /** The chapters from the TOC in the layout of {@code t}; empty without a TOC. Off the main thread. */
    private static List<BookChapters.Range> readChapters(AiJobs.Target t) {
        try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
            return text == null ? Collections.<BookChapters.Range>emptyList()
                    : BookChapters.chapters(AiJobs.toc(t, text), text.pageCount());
        } catch (Exception e) {
            LOG.e(e);
            return Collections.emptyList();
        }
    }

    private void onPage(DocumentController dc) {
        final int[] real = ReadingTracker.realPages(dc, dc.getCurentPageFirst1());
        final int from = real[0] - 1;
        final int to = Math.max(from, real[1] - 1);
        final String layout = layoutOf(dc);
        if (!readingChapters && chapters != null && !layout.equals(chaptersLayout)) {
            // a reflowing book laid out anew (font, size): its chapters moved
            readingChapters = true;
            final AiJobs.Target target = AiJobs.Target.of(dc);
            new Thread(() -> {
                final List<BookChapters.Range> found = readChapters(target);
                post(() -> {
                    readingChapters = false;
                    chapters = found;
                    chaptersLayout = layout;
                });
            }, "@T PreQ").start();
        }
        if (from == lastFrom && to == lastTo) {
            return;
        }
        lastFrom = from;
        lastTo = to;
        if (ready) {
            settled();
        }
    }

    /** The pages shown now, once the book settled: answers reached first, else an offer on entering a chapter. */
    private void settled() {
        final DocumentController dc = dc();
        if (dc == null || lastFrom < 0) {
            return;
        }
        if (pendingOffer != null) {
            final Activity a = owner();
            if (a != null) {
                a.getWindow().getDecorView().removeCallbacks(pendingOffer);
            }
            pendingOffer = null;
        }
        if (cardKind == CARD_OFFER || cardKind == CARD_REVEAL) {
            // a card is there for a page
            hideCard();
            reveals.clear();
        }
        final String layout = layoutOf(dc);
        if (reveal(layout)) {
            lastChapter = chapterKeyAt(lastFrom);
            return;
        }
        final BookChapters.Range r = chapterAt(lastFrom);
        final String key = r == null ? null : r.key;
        if (key != null && !key.equals(lastChapter)) {
            scheduleOffer(r);
        }
        lastChapter = key;
    }

    private BookChapters.Range chapterAt(int page) {
        final List<BookChapters.Range> all = chapters;
        if (all == null) {
            return null;
        }
        for (BookChapters.Range r : all) {
            if (page >= r.from && page < r.to) {
                return r;
            }
        }
        return null;
    }

    private String chapterKeyAt(int page) {
        final BookChapters.Range r = chapterAt(page);
        return r == null ? null : r.key;
    }

    private BookChapters.Range rangeOf(String key) {
        final List<BookChapters.Range> all = chapters;
        if (all != null) {
            for (BookChapters.Range r : all) {
                if (r.key.equals(key)) {
                    return r;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ answers reached

    /** Flags the answers on the pages shown and shows the first; true when there was one. */
    private boolean reveal(String layout) {
        final long now = System.currentTimeMillis();
        final List<Object[]> reached = new ArrayList<>();
        for (Map.Entry<String, PreQ.Chapter> e : items.entrySet()) {
            final PreQ.Chapter c = e.getValue();
            if (c.needsPages(layout)) {
                relocate(e.getKey(), c, layout);
                continue;
            }
            final List<Integer> on = c.revealsOn(lastFrom, lastTo, layout);
            if (on.isEmpty()) {
                continue;
            }
            for (int i : on) {
                c.questions.get(i).found = now;
                reached.add(new Object[]{e.getKey(), i});
            }
            save(e.getKey(), c);
        }
        if (reached.isEmpty()) {
            return false;
        }
        reveals.addAll(reached);
        showNextReveal();
        return true;
    }

    private void showNextReveal() {
        final Activity a = owner();
        final DocumentController dc = dc();
        if (reveals.isEmpty() || a == null || dc == null) {
            reveals.clear();
            hideCard();
            return;
        }
        final Object[] next = reveals.remove(0);
        final PreQ.Chapter c = items.get((String) next[0]);
        final int i = (Integer) next[1];
        if (c == null || i >= c.questions.size()) {
            showNextReveal();
            return;
        }
        final PreQ.Question q = c.questions.get(i);
        final PreQCard v = card(a);
        v.clear(true);
        v.label(a.getString(R.string.preq_answers_your, i + 1), true);
        v.title(headline(a, q));
        final Double guess = q.guessNumber();
        if (q.number != null && guess != null) {
            final PreQ.Comparison cmp = PreQ.compare(guess, q.number);
            v.bar(a.getString(R.string.preq_your_guess), PreQ.format(guess), cmp.guessShare, false);
            v.bar(q.label.isEmpty() ? a.getString(R.string.preq_the_book) : q.label, PreQ.format(q.number),
                    cmp.answerShare, true);
        }
        if (!q.why.isEmpty()) {
            v.line(q.why);
        }
        v.button(a.getString(R.string.preq_got_it), true, this::showNextReveal);
        v.status(a.getString(R.string.preq_found_n_of, c.foundCount(), c.questions.size()));
        v.onClose(() -> {
            reveals.clear();
            cardKind = CARD_NONE;
        });
        cardKind = CARD_REVEAL;
        v.show(AnnotationSession.isShownFor(dc));
    }

    /** "You guessed about 2,000. It's about 48,000, 24 times more." */
    static String headline(Activity a, PreQ.Question q) {
        final String answer = PreQ.inSentence(q.answerText());
        if (q.skipped || q.guessText().isEmpty()) {
            return a.getString(R.string.preq_reveal_skipped, answer);
        }
        final String guess = PreQ.inSentence(q.guessText());
        if (q.isRight()) {
            return a.getString(R.string.preq_reveal_right, guess);
        }
        final Double g = q.guessNumber();
        if (q.number != null && g != null) {
            final PreQ.Comparison c = PreQ.compare(g, q.number);
            switch (c.kind) {
                case PreQ.Comparison.RIGHT:
                    return a.getString(R.string.preq_reveal_spot_on, guess, answer);
                case PreQ.Comparison.CLOSE:
                    return a.getString(R.string.preq_reveal_close, guess, answer);
                case PreQ.Comparison.MORE:
                    if (!c.times.isEmpty()) {
                        return a.getString(R.string.preq_reveal_more, guess, answer, c.times);
                    }
                    break;
                case PreQ.Comparison.LESS:
                    if (!c.times.isEmpty()) {
                        return a.getString(R.string.preq_reveal_less, guess, answer, c.times);
                    }
                    break;
                default:
                    break;
            }
        }
        return a.getString(q.guess < 0 ? R.string.preq_reveal_own : R.string.preq_reveal_wrong, guess, answer);
    }

    /** A reflowing book in a new layout: the answers' pages found again, then the pages shown checked. */
    private void relocate(String key, PreQ.Chapter c, String layout) {
        final BookChapters.Range r = rangeOf(key);
        final DocumentController dc = dc();
        if (r == null || dc == null || !relocating.add(key + "|" + layout)) {
            return;
        }
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final List<String> quotes = new ArrayList<>();
        for (PreQ.Question q : c.questions) {
            quotes.add(q.quote);
        }
        new Thread(() -> {
            final int[][] pages = new int[quotes.size()][];
            try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                if (text == null) {
                    return;
                }
                final int from = Math.max(0, r.from - 2);
                final int to = Math.min(text.pageCount(), r.to + 2);
                for (int p = from; p < to; p++) {
                    final List<String> words = BookText.words(text.text(p));
                    for (int i = 0; i < quotes.size(); i++) {
                        if (PreQ.onPage(words, quotes.get(i))) {
                            if (pages[i] == null) {
                                pages[i] = new int[]{p, p};
                            } else if (pages[i][1] == p - 1) {
                                pages[i][1] = p;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
                return;
            }
            post(() -> {
                final PreQ.Chapter now = items.get(key);
                if (now == null || now.questions.size() != pages.length) {
                    return;
                }
                for (int i = 0; i < pages.length; i++) {
                    now.questions.get(i).page = pages[i] == null ? -1 : pages[i][0];
                    now.questions.get(i).lastPage = pages[i] == null ? -1 : pages[i][1];
                }
                now.layout = layout;
                save(key, now);
                final DocumentController d = dc();
                if (ready && d != null && layout.equals(layoutOf(d)) && cardKind != CARD_REVEAL) {
                    reveal(layout);
                }
            });
        }, "@T PreQ pages").start();
    }

    // ------------------------------------------------------------------ the offer

    private void scheduleOffer(BookChapters.Range r) {
        final PreQ.Chapter stored = items.get(r.key);
        if (job != null || !PreQ.offerable(r.key, r.title, SideMatter.isSideTitle(r.title), stored)) {
            return;
        }
        final Activity a = owner();
        if (a == null) {
            return;
        }
        pendingOffer = () -> {
            pendingOffer = null;
            if (current != this || owner() == null || job != null || !r.key.equals(lastChapter)
                    || !Assist.PRE_QUESTIONS.isOn()) {
                return;
            }
            // Study's before card has the chapter's own questions: the offer waits for another time
            if (com.foobnix.study.StudySession.cardShowing()) {
                return;
            }
            offer(r);
        };
        a.getWindow().getDecorView().postDelayed(pendingOffer, SETTLE_MS);
    }

    private void offer(BookChapters.Range r) {
        final Activity a = owner();
        final DocumentController dc = dc();
        if (a == null || dc == null) {
            return;
        }
        PreQ.Chapter c = items.get(r.key);
        final boolean resume = c != null && PreQ.READY.equals(c.state);
        if (c == null) {
            c = new PreQ.Chapter();
            c.title = r.title;
            c.state = PreQ.OFFERED;
            save(r.key, c);
        }
        final PreQ.Chapter chapter = c;
        final PreQCard v = card(a);
        v.clear(false);
        v.label(a.getString(R.string.preq_before_label), true);
        v.title(a.getString(resume ? R.string.preq_offer_resume : R.string.preq_offer_title));
        v.line(a.getString(R.string.preq_offer_line));
        v.button(a.getString(resume ? R.string.preq_continue : R.string.preq_offer_go), true, () -> {
            if (resume) {
                hideCard();
                PreQDialog.show(a, this, r.key, chapter, title(a, r));
            } else {
                ask(r);
            }
        });
        final Runnable notNow = () -> {
            hideCard();
            if (PreQ.OFFERED.equals(chapter.state)) {
                chapter.state = PreQ.DECLINED;
                save(r.key, chapter);
            }
        };
        v.button(a.getString(R.string.preq_not_now), false, notNow);
        v.onClose(() -> {
            cardKind = CARD_NONE;
            if (PreQ.OFFERED.equals(chapter.state)) {
                chapter.state = PreQ.DECLINED;
                save(r.key, chapter);
            }
        });
        cardKind = CARD_OFFER;
        v.show(AnnotationSession.isShownFor(dc));
    }

    /** The request for the chapter's questions; the card says it is writing them. */
    private void ask(BookChapters.Range r) {
        final Activity a = owner();
        final DocumentController dc = dc();
        if (a == null || dc == null || job != null) {
            return;
        }
        final String layout = layoutOf(dc);
        final PreQCard v = card(a);
        v.clear(false);
        v.label(a.getString(R.string.preq_before_label), true);
        v.title(a.getString(R.string.preq_writing));
        v.line(title(a, r));
        v.button(a.getString(R.string.preq_cancel), false, () -> {
            final PreQJob j = job;
            job = null;
            if (j != null) {
                j.cancel();
            }
            hideCard();
        });
        v.onClose(() -> {
            final PreQJob j = job;
            job = null;
            cardKind = CARD_NONE;
            if (j != null) {
                j.cancel();
            }
        });
        cardKind = CARD_BUSY;
        v.show(AnnotationSession.isShownFor(dc));

        job = new PreQJob(a, AiJobs.Target.of(dc), r, new PreQJob.Screen() {
            @Override
            public void onDone(List<PreQ.Question> questions) {
                job = null;
                if (cardKind == CARD_BUSY) {
                    hideCard();
                }
                PreQ.Chapter c = items.get(r.key);
                if (c == null) {
                    c = new PreQ.Chapter();
                }
                c.title = r.title;
                if (questions.isEmpty()) {
                    c.state = PreQ.DECLINED;
                    save(r.key, c);
                    Toast.makeText(a, R.string.preq_none, Toast.LENGTH_LONG).show();
                    return;
                }
                c.questions = questions;
                c.state = PreQ.READY;
                c.layout = layout;
                save(r.key, c);
                PreQDialog.show(a, PreQSession.this, r.key, c, title(a, r));
            }

            @Override
            public void onError(String message) {
                job = null;
                if (cardKind != CARD_BUSY || owner() == null) {
                    Toast.makeText(a, message, Toast.LENGTH_LONG).show();
                    return;
                }
                v.clear(false);
                v.label(a.getString(R.string.preq_before_label), true);
                v.title(a.getString(R.string.preq_failed));
                v.line(message);
                v.button(a.getString(R.string.preq_try_again), true, () -> ask(r));
                v.button(a.getString(R.string.preq_not_now), false, () -> hideCard());
                v.onClose(() -> cardKind = CARD_NONE);
                // now a card like the offer: the next page takes it away
                cardKind = CARD_OFFER;
            }

            @Override
            public void onNeedSettings() {
                job = null;
                hideCard();
                com.foobnix.ai.AiSettingsDialog.show(a, null);
            }
        });
        job.start();
    }

    private static String title(Activity a, BookChapters.Range r) {
        return r.title != null && !r.title.trim().isEmpty() ? r.title.trim() : a.getString(R.string.ai_pages, r.from + 1, r.to);
    }

    // ------------------------------------------------------------------ data

    /** Keeps a chapter's pre-questions: here at once, in the store one write after the other. */
    void save(String chapterKey, PreQ.Chapter c) {
        items.put(chapterKey, c);
        marks();
        final String json;
        try {
            json = c.toJson().toString();
        } catch (Exception e) {
            LOG.e(e);
            return;
        }
        final String book = name;
        WRITER.execute(() -> {
            try {
                AssistStore.get().put(book, PreQ.key(chapterKey), new JSONObject(json));
            } catch (Exception e) {
                LOG.e(e);
                post(() -> {
                    final Activity a = owner();
                    if (a != null) {
                        Toast.makeText(a, a.getString(R.string.preq_not_saved, String.valueOf(e.getMessage())),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /** The answer sentences of locked-in chapters, for the pages to underline. */
    private void marks() {
        final List<String> keys = new ArrayList<>();
        for (PreQ.Chapter c : items.values()) {
            keys.addAll(c.keys());
        }
        PreQMarks.set(keys);
    }

    // ------------------------------------------------------------------ the card

    private PreQCard card(Activity a) {
        if (card == null) {
            card = new PreQCard(a);
        }
        return card;
    }

    private void hideCard() {
        cardKind = CARD_NONE;
        if (card != null) {
            card.hide();
        }
    }

    private void removeCard() {
        cardKind = CARD_NONE;
        reveals.clear();
        if (card != null) {
            card.remove();
            card = null;
        }
    }

    private void close() {
        final PreQJob j = job;
        job = null;
        if (j != null) {
            j.cancel();
        }
        removeCard();
        PreQMarks.set(null);
    }

    private void post(Runnable r) {
        final Activity a = owner();
        if (a != null) {
            a.runOnUiThread(() -> {
                if (current == this) {
                    r.run();
                }
            });
        }
    }
}
