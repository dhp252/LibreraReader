package com.foobnix.assist.recall;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;

/**
 * A small card at the bottom of the reader in the canvas look (the pattern of the pre-questions
 * offer): a mono label, a title, a line, buttons and ✕. Blank-page recall offers itself with it
 * at the end of a chapter; teach-back uses it to come back from a look at the book.
 * Filled anew each time: {@link #clear}, rows, buttons, then {@link #show}.
 */
public final class RecallCard {

    /** Room for the annotation toolbar when it is shown (as the study card). */
    private static final int TOOLBAR_DP = 76;

    private final Activity activity;
    private final LinearLayout card;
    private final LinearLayout body;
    private LinearLayout buttons;
    private Runnable onClose;

    public RecallCard(Activity activity) {
        this.activity = activity;
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(16);
        card.setPadding(pad, CanvasLook.dp(12), CanvasLook.dp(6), pad);
        card.setElevation(CanvasLook.dp(6));
        card.setClickable(true);   // taps on the card do not turn the page
        card.setVisibility(View.GONE);
        card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(),
                CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP + 2)));

        final LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        top.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = new TextView(activity);
        close.setText("✕");
        CanvasParts.text(close, 16, 400);
        close.setTextColor(CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(activity.getString(com.foobnix.pdf.info.R.string.close));
        close.setOnClickListener(v -> close());
        top.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.dp(32)));
        card.addView(top);

        final FrameLayout content = activity.findViewById(android.R.id.content);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(com.foobnix.android.utils.Dips.screenWidth() - CanvasLook.dp(24), CanvasLook.dp(520)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = CanvasLook.dp(12);
        content.addView(card, params);
    }

    /**
     * Whether another card of this kind (Study's, another feature's offer) is on the reader now:
     * a visible, raised, vertical box laid over the page that is not {@code mine}. The annotation
     * toolbar (a horizontal bar) and the sync note (not raised) are not cards.
     */
    public static boolean otherCardShowing(Activity activity, RecallCard mine) {
        final FrameLayout content = activity == null ? null : (FrameLayout) activity.findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        for (int i = 1; i < content.getChildCount(); i++) {
            final View v = content.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE || (mine != null && v == mine.card) || !(v instanceof LinearLayout)) {
                continue;
            }
            if (((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL && v.getElevation() > 0 && v.isClickable()) {
                return true;
            }
        }
        return false;
    }

    /** Empties the card. */
    public void clear() {
        body.removeAllViews();
        buttons = null;
        onClose = null;
    }

    /** A mono uppercase label ("END OF CHAPTER"). */
    public void label(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.label(t, true);
        t.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(6));
        body.addView(t);
    }

    public void title(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.text(t, 17, 600);
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(t);
    }

    public void line(String text) {
        final TextView t = new TextView(activity);
        t.setText(text);
        CanvasParts.text(t, 13, 400);
        t.setTextColor(CanvasLook.ink2());
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        t.setPadding(0, CanvasLook.dp(8), CanvasLook.dp(10), 0);
        body.addView(t);
    }

    /** A filled button ({@code primary}) or a plain one, on the card's button row. */
    public void button(String label, boolean primary, Runnable action) {
        if (buttons == null) {
            buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = CanvasLook.dp(12);
            body.addView(buttons, p);
        }
        final TextView b = new TextView(activity);
        b.setText(label);
        CanvasParts.text(b, 14, 600);
        b.setGravity(Gravity.CENTER);
        b.setPadding(CanvasLook.dp(18), 0, CanvasLook.dp(18), 0);
        b.setMinHeight(CanvasLook.target());
        if (primary) {
            b.setBackground(CanvasParts.primaryButton());
            b.setTextColor(CanvasLook.onAccent());
        } else {
            b.setBackground(CanvasParts.ripple(null));
            b.setTextColor(CanvasLook.ink2());
        }
        b.setOnClickListener(v -> action.run());
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                CanvasLook.target());
        p.rightMargin = CanvasLook.dp(6);
        buttons.addView(b, p);
    }

    /** Runs when the reader closes the card with ✕ (not when it is hidden from code). */
    public void onClose(Runnable r) {
        onClose = r;
    }

    public void show(boolean aboveToolbar) {
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = CanvasLook.dp(12 + (aboveToolbar ? TOOLBAR_DP : 0));
        card.setLayoutParams(params);
        card.animate().cancel();
        card.setAlpha(0);
        card.setVisibility(View.VISIBLE);
        card.bringToFront();
        card.animate().alpha(1).setDuration(180).start();
    }

    public boolean isShown() {
        return card.getVisibility() == View.VISIBLE;
    }

    public void hide() {
        if (card.getVisibility() != View.VISIBLE) {
            return;
        }
        card.animate().cancel();
        card.animate().alpha(0).setDuration(150).withEndAction(() -> card.setVisibility(View.GONE)).start();
    }

    private void close() {
        final Runnable r = onClose;
        hide();
        if (r != null) {
            r.run();
        }
    }

    public void remove() {
        final ViewGroup parent = (ViewGroup) card.getParent();
        if (parent != null) {
            parent.removeView(card);
        }
    }
}
