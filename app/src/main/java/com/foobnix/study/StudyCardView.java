package com.foobnix.study;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;

/**
 * The study card at the bottom of the reader: a dark bubble like the annotation notes, over the
 * page but small, with a ✕. Filled anew for every card: {@link #clear}, then rows and buttons,
 * then {@link #show}.
 */
final class StudyCardView {

    static final int READ_COLOR = 0xff5fb236;
    static final int SKIM_COLOR = 0xfff4a300;
    static final int SKIP_COLOR = 0xff9e9e9e;
    private static final int TEXT = Color.WHITE;
    private static final int DIM = 0xffcfcfcf;
    private static final int ACTION = 0xff8ab4f8;
    /** Room for the annotation toolbar when it is shown. */
    private static final int TOOLBAR_DP = 76;

    private final Activity activity;
    private final LinearLayout card;
    private final LinearLayout body;
    private LinearLayout buttons;
    private Runnable onClose;

    StudyCardView(Activity activity) {
        this.activity = activity;
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(14);
        card.setPadding(pad, Dips.dpToPx(10), Dips.dpToPx(6), Dips.dpToPx(6));
        final GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xf0303030);
        bubble.setCornerRadius(Dips.dpToPx(16));
        card.setBackground(bubble);
        card.setElevation(Dips.dpToPx(6));
        card.setClickable(true);   // taps on the card do not turn the page
        card.setVisibility(View.GONE);

        final LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        top.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = text("✕", 18, DIM, false);
        close.setPadding(Dips.dpToPx(10), 0, Dips.dpToPx(8), Dips.dpToPx(4));
        close.setContentDescription(activity.getString(com.foobnix.pdf.info.R.string.close));
        close.setOnClickListener(v -> close());
        top.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(top);

        final FrameLayout content = activity.findViewById(android.R.id.content);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(Dips.screenWidth() - Dips.dpToPx(24), Dips.dpToPx(520)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = Dips.dpToPx(12);
        content.addView(card, params);
    }

    void clear() {
        body.removeAllViews();
        buttons = null;
        onClose = null;
    }

    /** A bold first line, with the label's colored mark when {@code label} is set. */
    void title(String text, String label) {
        final TextView t = text(text, 15, TEXT, true);
        if (label != null) {
            final android.text.SpannableString s = new android.text.SpannableString(text + "  " + mark(label));
            s.setSpan(new android.text.style.ForegroundColorSpan(color(label)), text.length() + 2, s.length(), 0);
            t.setText(s);
        }
        body.addView(t);
    }

    void line(String text) {
        final TextView t = text(text, 14, DIM, false);
        t.setPadding(0, Dips.dpToPx(3), 0, 0);
        body.addView(t);
    }

    void numbered(String heading, java.util.List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        final TextView h = text(heading, 14, TEXT, true);
        h.setPadding(0, Dips.dpToPx(6), 0, 0);
        body.addView(h);
        for (int i = 0; i < items.size(); i++) {
            final TextView t = text((i + 1) + ". " + items.get(i), 14, TEXT, false);
            t.setPadding(Dips.dpToPx(4), Dips.dpToPx(2), 0, 0);
            body.addView(t);
        }
    }

    /** A thin rule between the end of one chapter and the start of the next. */
    void divider() {
        final View v = new View(activity);
        v.setBackgroundColor(0x40ffffff);
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dips.dpToPx(1));
        p.topMargin = Dips.dpToPx(8);
        p.bottomMargin = Dips.dpToPx(8);
        body.addView(v, p);
    }

    /** A text button on the card's last row; {@code closes} hides the card after it. */
    void button(String label, boolean closes, Runnable action) {
        if (buttons == null) {
            buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            final HorizontalScrollView scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(buttons);
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Dips.dpToPx(6);
            body.addView(scroll, p);
        }
        final TextView b = text(label, 14, ACTION, true);
        b.setPadding(0, Dips.dpToPx(8), Dips.dpToPx(18), Dips.dpToPx(6));
        final TypedValue ripple = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        b.setBackgroundResource(ripple.resourceId);
        b.setOnClickListener(v -> {
            if (closes) {
                hide();
            }
            action.run();
        });
        buttons.addView(b);
    }

    /** Runs when the reader closes the card with ✕ (not when a page turn hides it). */
    void onClose(Runnable r) {
        onClose = r;
    }

    void show(boolean aboveToolbar) {
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = Dips.dpToPx(12 + (aboveToolbar ? TOOLBAR_DP : 0));
        card.setLayoutParams(params);
        card.animate().cancel();
        card.setAlpha(0);
        card.setVisibility(View.VISIBLE);
        card.bringToFront();
        card.animate().alpha(1).setDuration(180).start();
    }

    boolean isShown() {
        return card.getVisibility() == View.VISIBLE;
    }

    void hide() {
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

    void remove() {
        final ViewGroup parent = (ViewGroup) card.getParent();
        if (parent != null) {
            parent.removeView(card);
        }
    }

    static String mark(String label) {
        if (StudyBook.SKIP.equals(label)) {
            return "○";
        }
        return StudyBook.SKIM.equals(label) ? "◐" : "●";
    }

    static int color(String label) {
        if (StudyBook.SKIP.equals(label)) {
            return SKIP_COLOR;
        }
        return StudyBook.SKIM.equals(label) ? SKIM_COLOR : READ_COLOR;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        final TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }
}
