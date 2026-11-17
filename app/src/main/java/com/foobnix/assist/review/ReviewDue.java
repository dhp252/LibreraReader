package com.foobnix.assist.review;

import android.app.Activity;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.assist.Assist;
import com.foobnix.assist.recall.RecallCard;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Reviews come back in the app only (the owner's rule): no notification, no alarm, no scheduled
 * work, no calendar entry. When the main screen comes to the front a small banner says "6 ideas
 * to review · about 4 min" and opens Today's review; when a book is opened a card at the foot of
 * the reader says the same, with "Review now" and "Later". The banner's ✕ and "Later" hide them
 * until the app is started again. Both look only at those two moments.
 */
final class ReviewDue {

    /** A book opens on one page and may go to its saved place right after; other features' cards come first. */
    private static final long SETTLE_MS = 2400;
    private static final String BANNER_TAG = "canvasT24ReviewBanner";
    /** The tag of Use it's banner on the main screen ({@code UseDue}): ours stacks above it. */
    private static final String USE_IT_BANNER_TAG = "canvasT23UseItBanner";
    /** Where a banner sits above the foot of the main screen (above its tabs), as Use it's. */
    private static final int BANNER_BOTTOM_DP = 88;

    /** The book (path) and reader that were looked at: only the first page shown of an open counts. */
    private static String seenPath;
    private static WeakReference<Activity> seenOwner = new WeakReference<>(null);
    private static WeakReference<RecallCard> card = new WeakReference<>(null);
    /** The page the card was shown on, view page (1-based). */
    private static int cardPage;
    /** The reader said "Later" in a book, until the app is started again. */
    private static boolean later;
    private static boolean bannerDismissed;

    private ReviewDue() {
    }

    // ------------------------------------------------------------------ the open book

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    static void pageShown(final DocumentController dc) {
        if (!Assist.REVIEW.isOn() || dc == null || dc.getCurrentBook() == null || dc.getActivity() == null) {
            return;
        }
        try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final Activity owner = dc.getActivity();
            final String path = dc.getCurrentBook().getPath();
            if (path.equals(seenPath) && owner == seenOwner.get()) {
                // a page turn takes the card away
                if (card.get() != null && dc.getCurentPageFirst1() != cardPage) {
                    removeCard();
                }
                return;
            }
            removeCard();
            seenPath = path;
            seenOwner = new WeakReference<>(owner);
            final String book = ReviewStore.name(path);
            final ReviewStore.Meta meta = Review.meta(dc);
            ReviewStore.IO.execute(() -> {
                // what this book's recall stored since it was open last, and its index entry
                ReviewStore.sync(book, meta);
                final int due = ReviewIndex.toReview(ReviewStore.books(), ReviewStore.today());
                if (due <= 0 || later) {
                    return;
                }
                owner.runOnUiThread(() -> owner.getWindow().getDecorView().postDelayed(() -> {
                    if (owner == seenOwner.get() && path.equals(seenPath) && !Apps.isDestroyedActivity(owner)
                            && Assist.REVIEW.isOn() && !later && card.get() == null
                            // Study's card, a due experiment or another feature's offer has the foot of the page
                            && !RecallCard.otherCardShowing(owner, null)) {
                        showCard(owner, dc, due);
                    }
                }, SETTLE_MS));
            });
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void showCard(Activity a, DocumentController dc, int due) {
        final RecallCard v = new RecallCard(a);
        v.label(a.getString(R.string.canvas_t24_title));
        v.title(a.getString(R.string.canvas_t24_banner, ReviewWords.ideas(a, due), ReviewWords.about(a, due)));
        v.line(a.getString(R.string.canvas_t24_card_line));
        v.button(a.getString(R.string.canvas_t24_review_now), true, () -> {
            removeCard();
            Review.today(a, dc);
        });
        v.button(a.getString(R.string.canvas_t24_later), false, () -> {
            later = true;
            removeCard();
        });
        v.onClose(() -> {
            later = true;
            removeCard();
        });
        v.show(AnnotationSession.isShownFor(dc));
        card = new WeakReference<>(v);
        cardPage = dc.getCurentPageFirst1();
    }

    private static void removeCard() {
        final RecallCard v = card.get();
        card = new WeakReference<>(null);
        if (v != null) {
            v.remove();
        }
    }

    // ------------------------------------------------------------------ the main screen

    /**
     * The main screen comes to the front (MainTabs2.onResume): at most one small banner, "6 ideas
     * to review · about 4 min", from the library's index. With the switch off it only takes away
     * a banner that is still there.
     */
    static void libraryResumed(final Activity a) {
        try {
            if (a == null || Apps.isDestroyedActivity(a)) {
                return;
            }
            if (!Assist.REVIEW.isOn() || bannerDismissed) {
                removeBanner(a);
                return;
            }
            ReviewStore.IO.execute(() -> {
                // a recall checked in the reader just now, or answers from another device
                ReviewStore.scan();
                final int due = ReviewIndex.toReview(ReviewStore.books(), ReviewStore.today());
                a.runOnUiThread(() -> {
                    if (Apps.isDestroyedActivity(a)) {
                        return;
                    }
                    removeBanner(a);
                    if (due > 0 && Assist.REVIEW.isOn() && !bannerDismissed) {
                        showBanner(a, due);
                    }
                });
            });
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void showBanner(final Activity a, int due) {
        final FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final LinearLayout banner = new LinearLayout(a);
        banner.setTag(BANNER_TAG);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(14), 0, 0, 0);
        banner.setBackground(CanvasParts.round(CanvasLook.accentTint(), CanvasLook.accentLine(), dp(22)));
        banner.setElevation(dp(4));
        final TextView text = new TextView(a);
        CanvasParts.text(text, 13, 600);
        text.setTextColor(CanvasLook.accent());
        text.setText(a.getString(R.string.canvas_t24_banner, ReviewWords.ideas(a, due), ReviewWords.about(a, due)));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        banner.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        final TextView close = new TextView(a);
        CanvasParts.text(close, 14, 400);
        close.setTextColor(CanvasLook.accent());
        close.setGravity(Gravity.CENTER);
        close.setText("✕");
        close.setContentDescription(a.getString(R.string.close));
        close.setOnClickListener(v -> {
            bannerDismissed = true;
            removeBanner(a);
        });
        banner.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        banner.setOnClickListener(v -> Review.today(a, null));

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottom(content);
        content.addView(banner, params);

        // Use it's banner may come or go after ours (both are put up after a look at the store):
        // on every layout pass ours takes the place above it, or the foot place when it is alone
        final ViewTreeObserver.OnGlobalLayoutListener place = () -> {
            final int want = bottom(content);
            final FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) banner.getLayoutParams();
            if (p.bottomMargin != want) {
                p.bottomMargin = want;
                banner.setLayoutParams(p);
            }
        };
        banner.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnGlobalLayoutListener(place);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnGlobalLayoutListener(place);
            }
        });
        if (banner.isAttachedToWindow()) {
            banner.getViewTreeObserver().addOnGlobalLayoutListener(place);
        }
    }

    /** Our banner's distance from the foot: above Use it's banner when that one shows. */
    private static int bottom(View content) {
        final View other = content.findViewWithTag(USE_IT_BANNER_TAG);
        final boolean stacked = other != null && other.getVisibility() == View.VISIBLE;
        return dp(BANNER_BOTTOM_DP + (stacked ? CanvasLook.TARGET_DP + 8 : 0));
    }

    private static void removeBanner(Activity a) {
        final View content = a.findViewById(android.R.id.content);
        final View banner = content == null ? null : content.findViewWithTag(BANNER_TAG);
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
