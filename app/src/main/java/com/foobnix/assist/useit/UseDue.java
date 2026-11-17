package com.foobnix.assist.useit;

import android.app.Activity;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Check-ins come back in the app only (D-192): no notification, no alarm, nothing scheduled.
 * When a book with a due experiment is opened, a card at the foot of the reader says "Your
 * experiment is due: ..." with Check in / Later; on the main screen a small banner says "1
 * experiment to check in" and opens the list. "Later" and the banner's ✕ hide them until the app
 * is started again. Both look only when the reader opens something.
 */
final class UseDue {

    /** A book opens on one page and may go to its saved place right after: the card waits. */
    private static final long SETTLE_MS = 1500;
    private static final String BANNER_TAG = "canvasT23UseItBanner";

    /** The book (path) and reader that were looked at: only the first page shown of an open counts. */
    private static String seenPath;
    private static WeakReference<Activity> seenOwner = new WeakReference<>(null);
    private static WeakReference<View> card = new WeakReference<>(null);
    /** The page the card was shown on, view page (1-based). */
    private static int cardPage;
    /** Experiments the reader said "Later" to, until the app is started again. */
    private static final Set<String> later = new HashSet<>();
    private static boolean bannerDismissed;

    private UseDue() {
    }

    // ------------------------------------------------------------------ the open book

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    static void pageShown(DocumentController dc) {
        if (!Assist.USE_IT.isOn() || dc == null || dc.getCurrentBook() == null || dc.getActivity() == null) {
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
                // a page turn takes the card away; it comes back the next time the book is opened
                if (card.get() != null && dc.getCurentPageFirst1() != cardPage) {
                    removeCard();
                }
                return;
            }
            removeCard();
            seenPath = path;
            seenOwner = new WeakReference<>(owner);
            final String bookName = AiHighlights.bookName(path);
            UseStore.IO.execute(() -> {
                final String today = UseStore.today();
                UseExperiment found = null;
                for (UseExperiment e : UseStore.list(bookName, today)) {
                    if (e.isDue(today)) {
                        found = e;
                        break;
                    }
                }
                final UseExperiment due = found;
                if (due == null) {
                    return;
                }
                owner.runOnUiThread(() -> owner.getWindow().getDecorView().postDelayed(() -> {
                    if (owner == seenOwner.get() && path.equals(seenPath) && !Apps.isDestroyedActivity(owner)
                            && Assist.USE_IT.isOn() && !later.contains(due.id) && card.get() == null) {
                        showCard(owner, dc, bookName, due);
                    }
                }, SETTLE_MS));
            });
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void showCard(Activity a, DocumentController dc, String bookName, UseExperiment due) {
        final FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(8), dp(8));
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(16)));
        box.setElevation(dp(6));
        // taps on the card do not turn the page
        box.setClickable(true);

        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView label = new TextView(a);
        CanvasParts.label(label, true);
        label.setText(R.string.canvas_t23_title);
        texts.addView(label);
        final TextView title = new TextView(a);
        CanvasParts.text(title, CanvasLook.TEXT_BODY, 600);
        title.setText(a.getString(R.string.canvas_t23_due_card, due.tryText));
        title.setMaxLines(4);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(2), 1f);
        title.setPadding(0, dp(2), 0, 0);
        texts.addView(title);
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = new TextView(a);
        CanvasParts.text(close, 16, 400);
        close.setTextColor(CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setText("✕");
        close.setContentDescription(a.getString(R.string.canvas_t23_later));
        close.setBackground(CanvasParts.ripple(null));
        close.setOnClickListener(v -> later(due));
        top.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        box.addView(top);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        final TextView yes = new TextView(a);
        CanvasParts.text(yes, 14, 600);
        yes.setTextColor(CanvasLook.onAccent());
        yes.setGravity(Gravity.CENTER);
        yes.setPadding(dp(16), 0, dp(16), 0);
        yes.setBackground(CanvasParts.primaryButton());
        yes.setText(R.string.canvas_t23_check_in);
        yes.setOnClickListener(v -> {
            removeCard();
            UseCheckinScreen.show(a, bookName, due, null);
        });
        buttons.addView(yes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        final TextView no = new TextView(a);
        CanvasParts.text(no, 14, 600);
        no.setTextColor(CanvasLook.accent());
        no.setGravity(Gravity.CENTER);
        no.setPadding(dp(16), 0, dp(16), 0);
        no.setBackground(CanvasParts.ripple(null));
        no.setText(R.string.canvas_t23_later);
        no.setOnClickListener(v -> later(due));
        final LinearLayout.LayoutParams noParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                CanvasLook.target());
        noParams.leftMargin = dp(8);
        buttons.addView(no, noParams);
        final LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(10);
        box.addView(buttons, buttonsParams);

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(Dips.screenWidth() - dp(24), dp(520)), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(24);
        content.addView(box, params);
        box.setAlpha(0);
        box.animate().alpha(1).setDuration(180).start();
        card = new WeakReference<>(box);
        cardPage = dc.getCurentPageFirst1();
    }

    /** "Later": not shown again for this experiment until the app is started again. */
    private static void later(UseExperiment due) {
        later.add(due.id);
        removeCard();
    }

    private static void removeCard() {
        final View v = card.get();
        card = new WeakReference<>(null);
        if (v != null && v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    // ------------------------------------------------------------------ the main screen

    /**
     * The main screen comes to the front (MainTabs2.onResume): at most one small banner, "1
     * experiment to check in", from the library's index. With the switch off it only takes away a
     * banner that is still there.
     */
    static void libraryResumed(Activity a) {
        try {
            if (a == null || Apps.isDestroyedActivity(a)) {
                return;
            }
            if (!Assist.USE_IT.isOn() || bannerDismissed) {
                removeBanner(a);
                return;
            }
            UseStore.IO.execute(() -> {
                final List<UseIndex.Entry> due = UseStore.due(UseStore.today());
                a.runOnUiThread(() -> {
                    if (Apps.isDestroyedActivity(a)) {
                        return;
                    }
                    removeBanner(a);
                    if (!due.isEmpty() && Assist.USE_IT.isOn() && !bannerDismissed) {
                        showBanner(a, due);
                    }
                });
            });
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void showBanner(Activity a, List<UseIndex.Entry> due) {
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
        text.setText(due.size() == 1 ? a.getString(R.string.canvas_t23_banner_one)
                : a.getString(R.string.canvas_t23_banner_many, due.size()));
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
        banner.setOnClickListener(v -> UseListScreen.showDue(a, due, () -> libraryResumed(a)));

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        // above the tabs at the foot of the main screen
        params.bottomMargin = dp(88);
        content.addView(banner, params);
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
