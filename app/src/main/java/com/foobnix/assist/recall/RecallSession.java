package com.foobnix.assist.recall;

import android.app.Activity;
import android.os.Looper;

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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blank-page recall in the open book (Assist.RECALL): when the reader turns to the last page of a
 * chapter of a book with a table of contents, a small card offers "What did this chapter say?"
 * (never forced; once per chapter; not while Study's card or another offer shows; a page turn
 * takes it away). {@link #pageShown} is its only hook, called only while the switch is on.
 * Nothing is sent to the AI from here: the request waits for "Check" on the recall screen.
 */
public final class RecallSession {

    /** A book may open on one page and go to its saved place right after (as Study's cards). */
    private static final long SETTLE_MS = 1200;
    /** The reader gets a moment on the last page before the card comes. */
    private static final long OFFER_MS = 1500;

    private static RecallSession current;

    private final String path;
    private final String name;
    private WeakReference<DocumentController> dcRef;
    private WeakReference<Activity> ownerRef;

    /** Chapters with a recall item (an attempt or a "not now"), and those offered since the book opened. */
    private final Set<String> seen = new HashSet<>();
    private boolean ready;
    private int prevFrom = -1;
    private int prevTo = -1;
    private int lastFrom = -1;
    private int lastTo = -1;
    /** The chapters in the reader's layout (table of contents only); null until read. */
    private List<BookChapters.Range> chapters;
    private String chaptersLayout = "";
    private boolean reading;
    private Runnable pendingOffer;
    private RecallCard card;

    private RecallSession(DocumentController dc, String path) {
        this.path = path;
        this.name = AiHighlights.bookName(path);
        attach(dc);
    }

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    public static void pageShown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0 || dc.getActivity() == null
                    || !Assist.RECALL.isOn()) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || !current.path.equals(path)) {
                if (current != null) {
                    current.removeCard();
                }
                current = new RecallSession(dc, path);
                current.load();
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

    /** A chapter got its recall item (an attempt, a skip): no offer for it any more. */
    static void done(String bookPath, String chapterKey) {
        final RecallSession s = current;
        if (s != null && s.path.equals(bookPath) && chapterKey != null) {
            s.seen.add(chapterKey);
        }
    }

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

    private void load() {
        final String book = name;
        new Thread(() -> {
            final Set<String> stored = new HashSet<>();
            for (String key : AssistStore.get().items(book, Recall.PREFIX).keySet()) {
                stored.add(key.substring(Recall.PREFIX.length()));
            }
            post(() -> {
                seen.addAll(stored);
                final Activity a = owner();
                if (a != null) {
                    a.getWindow().getDecorView().postDelayed(() -> {
                        if (current == this && owner() != null) {
                            // the settled page is the opening page, not a turn
                            ready = true;
                            prevFrom = -1;
                            prevTo = -1;
                        }
                    }, SETTLE_MS);
                }
            });
        }, "@T Recall").start();
    }

    private void onPage(DocumentController dc) {
        final int[] real = ReadingTracker.realPages(dc, dc.getCurentPageFirst1());
        final int from = real[0] - 1;
        final int to = Math.max(from, real[1] - 1);
        if (from == lastFrom && to == lastTo) {
            return;
        }
        prevFrom = lastFrom;
        prevTo = lastTo;
        lastFrom = from;
        lastTo = to;
        // a page turn takes the offer away
        cancelPending();
        if (card != null) {
            card.hide();
        }
        if (ready) {
            check(dc);
        }
    }

    /** The pages shown now: an offer when they end a chapter the reader paged through. */
    private void check(DocumentController dc) {
        if (prevFrom < 0 || lastFrom <= prevFrom) {
            // the opening page, a jump back: nothing was just read to its end
            return;
        }
        final String layout = layoutOf(dc);
        if (chapters == null || !layout.equals(chaptersLayout)) {
            readChapters(dc, layout);
            return;
        }
        final BookChapters.Range r = chapterAt(lastFrom);
        if (r == null || !Recall.reachedEnd(prevFrom, prevTo, lastFrom, lastTo, r.from, r.to)
                || !Recall.offerable(r.key, r.title, SideMatter.isSideTitle(r.title), seen.contains(r.key))) {
            return;
        }
        final Activity a = owner();
        if (a == null) {
            return;
        }
        final int page = lastFrom;
        pendingOffer = () -> {
            pendingOffer = null;
            final DocumentController now = dc();
            if (current != this || owner() == null || now == null || lastFrom != page || !Assist.RECALL.isOn()
                    || seen.contains(r.key)) {
                return;
            }
            // Study's card, or another feature's offer, has the bottom of the page now
            if (RecallCard.otherCardShowing(a, card)) {
                return;
            }
            offer(a, now, r);
        };
        a.getWindow().getDecorView().postDelayed(pendingOffer, OFFER_MS);
    }

    private static String layoutOf(DocumentController dc) {
        final AiJobs.Target t = AiJobs.Target.of(dc);
        return dc.isTextFormat() ? t.width + "x" + t.height + "@" + t.fontSize : "fixed";
    }

    private BookChapters.Range chapterAt(int page) {
        for (BookChapters.Range r : chapters) {
            if (page >= r.from && page < r.to) {
                return r;
            }
        }
        return null;
    }

    /**
     * The chapters from the table of contents in the reader's layout (none without one: such a
     * book gets no offers), read on the first forward page turn and again after a new layout;
     * then the pages shown are looked at again.
     */
    private void readChapters(DocumentController dc, String layout) {
        if (reading) {
            return;
        }
        reading = true;
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final int page = lastFrom;
        new Thread(() -> {
            List<BookChapters.Range> found = Collections.emptyList();
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    found = BookChapters.chapters(AiJobs.toc(t, text), text.pageCount());
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final List<BookChapters.Range> list = found;
            post(() -> {
                reading = false;
                chapters = list;
                chaptersLayout = layout;
                final DocumentController now = dc();
                if (now != null && ready && lastFrom == page && layout.equals(layoutOf(now))) {
                    check(now);
                }
            });
        }, "@T Recall").start();
    }

    private void offer(Activity a, DocumentController dc, BookChapters.Range r) {
        seen.add(r.key);
        if (card == null) {
            card = new RecallCard(a);
        }
        final RecallCard v = card;
        v.clear();
        v.label(a.getString(R.string.canvas_t17_offer_label));
        v.title(a.getString(R.string.canvas_t17_question));
        v.line(a.getString(R.string.canvas_t17_offer_line));
        v.button(a.getString(R.string.canvas_t17_offer_go), true, () -> {
            v.hide();
            RecallScreen.show(a, dc, r);
        });
        final Runnable notNow = () -> {
            v.hide();
            RecallScreen.decline(name, r);
        };
        v.button(a.getString(R.string.canvas_t17_not_now), false, notNow);
        v.onClose(() -> RecallScreen.decline(name, r));
        v.show(AnnotationSession.isShownFor(dc));
    }

    private void cancelPending() {
        if (pendingOffer != null) {
            final Activity a = owner();
            if (a != null) {
                a.getWindow().getDecorView().removeCallbacks(pendingOffer);
            }
            pendingOffer = null;
        }
    }

    private void removeCard() {
        cancelPending();
        if (card != null) {
            card.remove();
            card = null;
        }
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
