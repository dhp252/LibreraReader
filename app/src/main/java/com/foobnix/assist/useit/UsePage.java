package com.foobnix.assist.useit;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The frame of a "Use it" screen, full screen in the canvas look: a 56 dp bar (back, a title over
 * a subtitle, a slot on the right), the content in a scroll view, and the actions pinned at the
 * foot; plus the small parts the three screens share (texts, fields, buttons, days as words).
 */
final class UsePage {

    final Activity a;
    final Dialog dialog;
    final TextView title;
    final TextView subtitle;
    /** The right end of the bar: empty unless a screen puts a button there. */
    final LinearLayout right;
    final ScrollView scroll;
    /** The scrolling part, with the canvas's 16 dp gutters. */
    final LinearLayout content;
    /** The pinned foot. */
    final LinearLayout actions;

    UsePage(Activity a, CharSequence titleText, CharSequence subtitleText) {
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
        final TextView back = text(a, "‹", 28, 400, CanvasLook.ink());
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
        right = new LinearLayout(a);
        right.setGravity(Gravity.CENTER);
        right.setMinimumWidth(CanvasLook.target());
        bar.addView(right, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        page.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CanvasLook.BAR_DP)));

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

    /** Adds a view to the content with a top margin, full width. */
    <T extends View> T add(T view, int topDp) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        content.addView(view, p);
        return view;
    }

    /** A row of buttons at the foot: each {weight} wide, 52 dp tall, 8 dp apart. */
    void actionRow(View[] buttons, float[] weights) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), weights[i]);
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

    /** A JetBrains Mono uppercase label in the accent. */
    static TextView label(Activity a, CharSequence s) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.label(t, true);
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

    /** The canvas's text field: the page colour with a strong hairline, 14 dp corners. */
    static EditText field(Activity a, CharSequence value, CharSequence hint, boolean multiLine) {
        final EditText e = new EditText(a);
        CanvasParts.text(e, 15, 400);
        e.setHintTextColor(CanvasLook.muted());
        e.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), dp(14)));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multiLine ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiLine) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setLineSpacing(dp(2), 1f);
        } else {
            e.setSingleLine(true);
        }
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        return e;
    }

    /** A small field for one number (a measure's value). */
    static EditText number(Activity a, double value, CharSequence hint) {
        final EditText e = new EditText(a);
        CanvasParts.text(e, 14, 600);
        e.setHintTextColor(CanvasLook.muted());
        e.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), dp(10)));
        e.setPadding(dp(8), 0, dp(8), 0);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(UseExperiment.number(value));
        e.setHint(hint);
        e.setMinimumHeight(CanvasLook.target());
        return e;
    }

    /** What a field tells when its text changes. */
    interface Text {
        void on(String text);
    }

    static void onText(EditText e, final Text listener) {
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                listener.on(s.toString());
            }
        });
    }

    /** Children side by side, wrapping to the next line when the row is full (the measure chips). */
    static final class Flow extends ViewGroup {
        private final int gap;

        Flow(Activity a, int gapDp) {
            super(a);
            gap = dp(gapDp);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            final int max = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                    : MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int row = 0;
            int widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View c = getChildAt(i);
                if (c.getVisibility() == GONE) {
                    continue;
                }
                c.measure(MeasureSpec.makeMeasureSpec(Math.max(0, max), max == Integer.MAX_VALUE
                        ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + c.getMeasuredWidth() > max) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                x += c.getMeasuredWidth() + gap;
                row = Math.max(row, c.getMeasuredHeight());
                widest = Math.max(widest, x - gap);
            }
            setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec),
                    resolveSize(y + row + getPaddingTop() + getPaddingBottom(), heightSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int max = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View c = getChildAt(i);
                if (c.getVisibility() == GONE) {
                    continue;
                }
                if (x > 0 && x + c.getMeasuredWidth() > max) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                c.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + c.getMeasuredWidth(),
                        getPaddingTop() + y + c.getMeasuredHeight());
                x += c.getMeasuredWidth() + gap;
                row = Math.max(row, c.getMeasuredHeight());
            }
        }
    }

    /** A chip that is on or off (the measure's direction). */
    static TextView chip(Activity a, CharSequence label) {
        final TextView t = text(a, label, 12, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setMinHeight(dp(28));
        t.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(8)));
        return t;
    }

    /** One of the feature's small line icons, in a colour. */
    static ImageView icon(Activity a, int drawable, int color) {
        final ImageView i = new ImageView(a);
        i.setImageResource(drawable);
        i.setColorFilter(color);
        i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return i;
    }

    /** A rounded box with the accent border 1.5 dp wide (the experiment card). */
    static GradientDrawable accentBorder(Activity a, int radiusDp) {
        final GradientDrawable d = CanvasParts.round(CanvasLook.bg(), CanvasLook.accent(), dp(radiusDp));
        d.setStroke(Math.max(2, Math.round(1.5f * a.getResources().getDisplayMetrics().density)), CanvasLook.accent());
        return d;
    }

    /** A local day in words, by a pattern ("EEE, MMM d": Mon, Nov 9); the day itself when it cannot be read. */
    static String day(String day, String pattern) {
        final int[] p = UseDates.parts(day);
        if (p == null) {
            return day == null ? "" : day;
        }
        final Calendar c = Calendar.getInstance();
        c.clear();
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0);
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(c.getTime());
    }

    /** "Mon, Nov 9". */
    static String dayLong(String day) {
        return day(day, "EEE, MMM d");
    }

    /** "Oct 26 – Nov 9". */
    static String span(String from, String to) {
        return day(from, "MMM d") + " – " + day(to, "MMM d");
    }
}
