package com.foobnix.assist.edition;

import android.app.Activity;
import android.os.Looper;

import com.foobnix.ai.AiJobs;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.assist.Assist;
import com.foobnix.assist.recall.RecallCard;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.lang.ref.WeakReference;

/**
 * "Your edition" (canvas "Your edition" and "Edition page"; Assist.EDITION): the book in a few
 * pages made only of what the reader produced in it. These are its ways in, each from one line
 * in an existing class, and all of them do nothing while the switch is off:
 * <ul>
 * <li>the ✦ menu's "Your edition" row ({@link #open});</li>
 * <li>a card over the page, once, when the reader turns to the book's last page for the first
 * time with something in the edition: "You finished it. Your edition is ready." (when another
 * card has the foot of the page then, it comes the next time the book is opened);</li>
 * <li>a card when the book is opened on or after the day of its refresher (six weeks after the
 * edition was opened): "Your 15-minute refresher is ready", once per refresher.</li>
 * </ul>
 * Nothing is scheduled: no notification, alarm, background work or calendar entry. The refresher
 * is a day in the book's {@code edition:book} item that is looked at when the book is opened.
 * Nothing here asks the AI.
 */
public final class Edition {

    /** A book opens on one page and may go to its saved place right after: the look waits. */
    private static final long SETTLE_MS = 1800;
    /** The reader gets a moment on the last page before the card comes (after recall's own offer). */
    private static final long OFFER_MS = 2600;

    /** The book (path) and reader this open was looked at for. */
    private static String seenPath;
    private static WeakReference<Activity> seenOwner = new WeakReference<>(null);
    /** The opening page has settled: page turns count from now. */
    private static boolean ready;
    /** The view page shown last (1-based). */
    private static int lastPage = -1;
    /** The book was already finished, or its card is out: the last page is not looked at again. */
    private static boolean finishedKnown;
    private static RecallCard card;
    private static int cardPage;

    private Edition() {
    }

    /** The ✦ menu's "Your edition" row. */
    public static void open(Activity a, DocumentController dc) {
        if (!Assist.EDITION.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        removeCard();
        EditionScreen.show(a, dc);
    }

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    public static void pageShown(DocumentController dc) {
        try {
            if (!Assist.EDITION.isOn() || dc == null || dc.getCurrentBook() == null || dc.getActivity() == null) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final Activity owner = dc.getActivity();
            final String path = dc.getCurrentBook().getPath();
            final int page = dc.getCurentPageFirst1();
            if (!path.equals(seenPath) || owner != seenOwner.get()) {
                opened(owner, dc, path);
                return;
            }
            if (card != null && card.isShown() && page != cardPage) {
                // a page turn takes the card away
                card.hide();
            }
            final int before = lastPage;
            lastPage = page;
            if (ready && !finishedKnown && before >= 1 && page > before && dc.getPageCount() > 1
                    && page >= dc.getPageCount()) {
                reachedEnd(owner, dc, path, page);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** A book was opened: the card it is owed, if any (the finished offer first, else a due refresher). */
    private static void opened(Activity owner, DocumentController dc, String path) {
        removeCard();
        seenPath = path;
        seenOwner = new WeakReference<>(owner);
        ready = false;
        finishedKnown = false;
        lastPage = dc.getCurentPageFirst1();
        final AiJobs.Target target = AiJobs.Target.of(dc);
        EditionData.IO.execute(() -> {
            EditionData found = null;
            try {
                found = EditionData.load(owner, target, false);
            } catch (Exception e) {
                LOG.e(e);
            }
            final EditionData data = found;
            owner.runOnUiThread(() -> owner.getWindow().getDecorView().postDelayed(() -> {
                if (!same(owner, path) || !Assist.EDITION.isOn()) {
                    return;
                }
                // the settled page is the opening page, not a turn
                ready = true;
                lastPage = dc.getCurentPageFirst1();
                if (data == null) {
                    return;
                }
                finishedKnown = !data.state.finished.isEmpty();
                if (data.model.isEmpty() || RecallCard.otherCardShowing(owner, card)) {
                    return;
                }
                if (data.state.offerOwed()) {
                    finishedCard(owner, dc, data.bookName);
                } else if (data.state.refresherDue(data.today)) {
                    refresherCard(owner, dc, data);
                }
            }, SETTLE_MS));
        });
    }

    /** The reader turned to the last page: with something in the edition, the offer, once. */
    private static void reachedEnd(Activity owner, DocumentController dc, String path, int page) {
        final AiJobs.Target target = AiJobs.Target.of(dc);
        EditionData.IO.execute(() -> {
            boolean first = false;
            try {
                final EditionData data = EditionData.load(owner, target, false);
                if (!data.model.isEmpty()) {
                    final boolean[] changed = new boolean[1];
                    EditionData.update(target.name, s -> changed[0] = s.finish(data.today));
                    first = changed[0];
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            if (!first) {
                return;
            }
            owner.runOnUiThread(() -> {
                finishedKnown = true;
                owner.getWindow().getDecorView().postDelayed(() -> {
                    // still on the last page, and no other card (recall's offer, Study's) at its foot
                    if (same(owner, path) && Assist.EDITION.isOn() && dc.getCurentPageFirst1() == page
                            && !RecallCard.otherCardShowing(owner, card)) {
                        finishedCard(owner, dc, target.name);
                    }
                }, OFFER_MS);
            });
        });
    }

    private static void finishedCard(Activity a, DocumentController dc, String bookName) {
        final RecallCard v = card(a);
        v.label(a.getString(R.string.canvas_t26_title));
        v.title(a.getString(R.string.canvas_t26_finished));
        v.line(a.getString(R.string.canvas_t26_finished_line));
        v.button(a.getString(R.string.canvas_t26_open_it), true, () -> {
            v.hide();
            EditionScreen.show(a, dc);
        });
        v.button(a.getString(R.string.canvas_t26_not_now), false, v::hide);
        cardPage = dc.getCurentPageFirst1();
        v.show(AnnotationSession.isShownFor(dc));
        // shown once: the ✦ menu's row is the way back
        EditionData.IO.execute(() -> EditionData.update(bookName, s -> {
            final boolean change = !s.offered;
            s.offered = true;
            return change;
        }));
    }

    private static void refresherCard(Activity a, DocumentController dc, EditionData data) {
        final RecallCard v = card(a);
        v.label(a.getString(R.string.canvas_t26_title));
        v.title(a.getString(R.string.canvas_t26_refresher_ready, data.model.minutes()));
        v.line(a.getString(R.string.canvas_t26_refresher_line));
        v.button(a.getString(R.string.canvas_t26_read_it), true, () -> {
            v.hide();
            EditionScreen.show(a, dc);
        });
        v.button(a.getString(R.string.canvas_t26_later), false, v::hide);
        cardPage = dc.getCurentPageFirst1();
        v.show(AnnotationSession.isShownFor(dc));
        // one card per refresher; reading the edition sets the next one
        EditionData.IO.execute(() -> EditionData.update(data.bookName, s -> {
            if (s.next.isEmpty() || s.next.equals(s.carded)) {
                return false;
            }
            s.refresherShown();
            return true;
        }));
    }

    private static RecallCard card(Activity a) {
        removeCard();
        card = new RecallCard(a);
        card.clear();
        return card;
    }

    private static void removeCard() {
        if (card != null) {
            card.remove();
            card = null;
        }
    }

    private static boolean same(Activity owner, String path) {
        return owner == seenOwner.get() && path.equals(seenPath) && !Apps.isDestroyedActivity(owner);
    }
}
