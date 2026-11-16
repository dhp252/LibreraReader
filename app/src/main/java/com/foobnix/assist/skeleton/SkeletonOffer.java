package com.foobnix.assist.skeleton;

import android.app.Activity;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.lang.ref.WeakReference;

/**
 * The first time a book is opened (no reading progress on any device yet), a small card at the
 * bottom of the reader offers its skeleton: "See the whole argument first?" Show me / Not now.
 * Never forced: it goes away at a tap, with ✕, or when the reader turns a page.
 */
public final class SkeletonOffer {

    /** A book opens on one page and may go to its saved place right after: the card waits. */
    private static final long SETTLE_MS = 1500;

    /** The book (path) and reader this open was looked at for; a page change of the same book does nothing. */
    private static String seenPath;
    private static WeakReference<Activity> seenOwner = new WeakReference<>(null);
    private static WeakReference<View> card = new WeakReference<>(null);
    /** The page the card was offered on, view page (1-based). */
    private static int offeredPage;

    private SkeletonOffer() {
    }

    /**
     * The reader shows a page (DocumentController.saveCurrentPage).
     *
     * @param lastRead when the book's position changed last before this page, on any device; 0 = never read
     */
    public static void pageShown(DocumentController dc, long lastRead) {
        if (!Assist.SKELETON.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc, lastRead));
                return;
            }
            final Activity owner = dc.getActivity();
            final String path = dc.getCurrentBook().getPath();
            if (path.equals(seenPath) && owner == seenOwner.get()) {
                // a page turn takes the offer away
                if (card.get() != null && dc.getCurentPageFirst1() != offeredPage) {
                    remove();
                }
                return;
            }
            remove();
            seenPath = path;
            seenOwner = new WeakReference<>(owner);
            if (lastRead != 0) {
                return;
            }
            offeredPage = dc.getCurentPageFirst1();
            owner.getWindow().getDecorView().postDelayed(() -> {
                if (owner == seenOwner.get() && path.equals(seenPath) && !Apps.isDestroyedActivity(owner)
                        && Assist.SKELETON.isOn() && dc.getCurentPageFirst1() == offeredPage) {
                    show(owner, dc);
                }
            }, SETTLE_MS);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void show(Activity a, DocumentController dc) {
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
        label.setText(R.string.canvas_t13_before_you_start);
        texts.addView(label);
        final TextView title = new TextView(a);
        CanvasParts.text(title, CanvasLook.TEXT_BODY, 600);
        title.setText(R.string.canvas_t13_offer);
        title.setPadding(0, dp(2), 0, 0);
        texts.addView(title);
        final TextView note = new TextView(a);
        CanvasParts.secondary(note);
        note.setText(R.string.canvas_t13_offer_note);
        texts.addView(note);
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = new TextView(a);
        CanvasParts.text(close, 16, 400);
        close.setTextColor(CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setText("✕");
        close.setContentDescription(a.getString(R.string.close));
        close.setBackground(CanvasParts.ripple(null));
        close.setOnClickListener(v -> remove());
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
        yes.setText(R.string.canvas_t13_offer_yes);
        yes.setOnClickListener(v -> {
            remove();
            SkeletonScreen.show(a, dc);
        });
        buttons.addView(yes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        final TextView no = new TextView(a);
        CanvasParts.text(no, 14, 600);
        no.setTextColor(CanvasLook.accent());
        no.setGravity(Gravity.CENTER);
        no.setPadding(dp(16), 0, dp(16), 0);
        no.setBackground(CanvasParts.ripple(null));
        no.setText(R.string.canvas_t13_offer_no);
        no.setOnClickListener(v -> remove());
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
    }

    private static void remove() {
        final View v = card.get();
        card = new WeakReference<>(null);
        if (v != null && v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
