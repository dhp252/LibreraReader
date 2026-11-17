package com.foobnix.assist.review;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * The frame of a review screen, full screen in the canvas look: a 56 dp bar (back or close, a
 * title over a subtitle), the content in a scroll view, and the actions pinned at the foot; plus
 * the small parts the screens share (the pattern of Use it's page).
 */
final class ReviewPage {

    /** The canvas's orange of an idea missed in recall (day). */
    private static final int MISSED_BAR = 0xFFF27420;
    private static final int MISSED_TINT = 0xFFFFF1E6;
    private static final int MISSED_LINE = 0xFFF3C6A5;
    private static final int MISSED_LINE_N = 0xFF7A4A24;

    final Activity a;
    final Dialog dialog;
    final TextView title;
    final TextView subtitle;
    /** Between the bar and the scrolling part: the session's progress. */
    final LinearLayout top;
    final ScrollView scroll;
    /** The scrolling part, with the canvas's 16 dp gutters. */
    final LinearLayout content;
    /** The pinned foot. */
    final LinearLayout actions;

    /** @param close a ✕ at the left of the bar (a session) instead of the back arrow */
    ReviewPage(Activity a, CharSequence titleText, CharSequence subtitleText, boolean close) {
        this.a = a;
        final boolean dark = CanvasLook.isNight();
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());

        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        final TextView back = text(a, close ? "✕" : "‹", close ? 18 : 28, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setBackground(CanvasParts.ripple(null));
        back.setContentDescription(a.getString(R.string.close));
        back.setOnClickListener(v -> dialog.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        title = text(a, titleText, 14, 600, CanvasLook.ink());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        subtitle = text(a, "", 12, 400, CanvasLook.muted());
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setGravity(Gravity.CENTER);
        titles.addView(subtitle);
        setSubtitle(subtitleText);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        // the bar's right end is empty, as wide as the button on the left: the title stays centred
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        page.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CanvasLook.BAR_DP)));

        top = new LinearLayout(a);
        top.setOrientation(LinearLayout.VERTICAL);
        page.addView(top);

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(12));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(16), dp(12), dp(16), dp(16));
        page.addView(actions);

        dialog.setContentView(page);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
    }

    void setSubtitle(CharSequence s) {
        subtitle.setText(s == null ? "" : s);
        subtitle.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** Empties the scrolling part and the foot. */
    void clear() {
        content.removeAllViews();
        actions.removeAllViews();
        scroll.scrollTo(0, 0);
    }

    /** Adds a view to the content with a top margin, full width. */
    <T extends View> T add(T view, int topDp) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        content.addView(view, p);
        return view;
    }

    /** A row of buttons at the foot: equally wide, 52 dp tall, 8 dp apart. */
    void actionRow(View... buttons) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (i > 0) {
                p.leftMargin = dp(8);
            }
            row.addView(buttons[i], p);
        }
        actions.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------ parts

    static int dp(int dp) {
        return CanvasLook.dp(dp);
    }

    static TextView text(Activity a, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.text(t, sp, weight);
        t.setTextColor(color);
        return t;
    }

    /** A JetBrains Mono uppercase label, in the accent or muted. */
    static TextView label(Activity a, CharSequence s, boolean accent) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.label(t, accent);
        return t;
    }

    static TextView primary(Activity a, CharSequence label) {
        final TextView b = text(a, label, 15, 600, CanvasLook.onAccent());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(CanvasParts.round(CanvasLook.accent(), 0, dp(14)));
        b.setForeground(CanvasParts.ripple(null));
        return b;
    }

    static TextView secondary(Activity a, CharSequence label) {
        final TextView b = text(a, label, 14, 600, CanvasLook.ink());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), dp(14)));
        b.setForeground(CanvasParts.ripple(null));
        return b;
    }

    /** A button that can be tapped or not, dimmed when not. */
    static void enable(View button, boolean on) {
        button.setEnabled(on);
        button.setAlpha(on ? 1f : 0.4f);
    }

    /** What a chip says: plain, the accent's, or the orange of "missed". */
    static final int CHIP_PLAIN = 0;
    static final int CHIP_ACCENT = 1;
    static final int CHIP_MISSED = 2;

    /** A small chip ("2 missed in recall"). */
    static TextView chip(Activity a, CharSequence s, int kind) {
        final TextView t = text(a, s, 12, 600, kind == CHIP_MISSED ? CanvasLook.sensor()
                : kind == CHIP_ACCENT ? CanvasLook.accent() : CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(8), 0, dp(8), 0);
        t.setMinHeight(dp(26));
        t.setBackground(kind == CHIP_MISSED ? CanvasParts.round(missedTint(), 0, dp(7))
                : kind == CHIP_ACCENT ? CanvasParts.round(CanvasLook.accentTint(), 0, dp(7))
                : CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(7)));
        return t;
    }

    /** Children in a row, 6 dp apart (the chips; they are few and short). */
    static LinearLayout row(Activity a, View... children) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < children.length; i++) {
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                p.leftMargin = dp(6);
            }
            row.addView(children[i], p);
        }
        return row;
    }

    /** The square with a book's letter: {@code tone} 0 the accent, 1 ink, 2 grey, so books differ. */
    static TextView square(Activity a, String letter, int tone, int sizeDp) {
        final boolean grey = tone % 3 == 2;
        final TextView t = text(a, letter, sizeDp >= 32 ? 13 : 12, 700, grey ? CanvasLook.ink() : CanvasLook.bg());
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(CanvasParts.round(tone % 3 == 0 ? CanvasLook.accent() : grey ? CanvasLook.line()
                : CanvasLook.ink(), 0, dp(sizeDp >= 32 ? 8 : 7)));
        if (tone % 3 == 0) {
            t.setTextColor(CanvasLook.onAccent());
        }
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    static int missedBar() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_N : MISSED_BAR;
    }

    static int missedTint() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_TINT_N : MISSED_TINT;
    }

    static int missedLine() {
        return CanvasLook.isNight() ? MISSED_LINE_N : MISSED_LINE;
    }
}
