package com.foobnix.assist.recap;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

import java.util.List;

/**
 * The recap as a bottom sheet: the canvas look when it is on (CanvasLook tokens and parts), else
 * the same sheet in a plain style. Rebuilt from the {@link Recap} state on every change.
 * Short view: "Welcome back · 2 days away / Your 20-second recap" with last time, where you
 * stopped and your highlight. Longer view: "Previously on…" with 30 sec / 2 min / 5 min, the story
 * (or argument) so far by chapter, who's who, where things stand, and a warm-up.
 */
final class RecapSheet {

    private static Typeface serif;
    private static Typeface serifItalic;

    private final Activity a;
    private final Recap s;
    private final boolean canvas;
    private final boolean dark;
    private final Dialog dialog;
    private final LinearLayout body;
    private final LinearLayout actions;
    private final ScrollView scroll;
    /** Taken away by the app (rotation, another book), not by the reader. */
    private boolean quiet;

    RecapSheet(Activity a, Recap s) {
        this.a = a;
        this.s = s;
        this.canvas = CanvasLook.isOn();
        this.dark = canvas ? CanvasLook.isNight() : !AppState.get().isDayNotInvert;
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(16));
        root.setBackground(canvas ? CanvasParts.sheet() : topRounded(bg(), dp(12)));

        final View handle = new View(a);
        handle.setBackground(CanvasParts.round(line(), 0, dp(2)));
        final LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(12);
        root.addView(handle, hp);

        scroll = new MaxScroll(a, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.72f));
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(14);
        root.addView(actions, ap);

        dialog.setContentView(root);
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        com.foobnix.android.utils.SafeArea.fit(dialog, root, dark);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            if (!quiet) {
                s.sheetClosed();
            }
        });
    }

    void show() {
        render();
        try {
            dialog.show();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    void dismissQuietly() {
        quiet = true;
        try {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private void close() {
        try {
            dialog.dismiss();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // ------------------------------------------------------------------ content

    void render() {
        body.removeAllViews();
        actions.removeAllViews();
        if (s.longView) {
            longView();
        } else {
            shortView();
        }
    }

    private void shortView() {
        body.addView(label(a.getString(R.string.recap_t10_welcome_back, away()), true));
        add(body, text(a.getString(R.string.recap_t10_short_title), 19, 600, ink()), 2);
        state();
        final RecapPrompts.Recap r = s.recap;
        if (r != null && !r.lastTime.isEmpty()) {
            section(R.string.recap_t10_last_time, text(r.lastTime, 14, 400, ink()));
        }
        section(R.string.recap_t10_stopped_at, text(stoppedAt(), 14, 400, ink()));
        markSection();

        mapButton();
        final LinearLayout row = row();
        row.addView(button(a.getString(R.string.recap_t10_longer), false, v -> {
            s.longView = true;
            render();
            scroll.scrollTo(0, 0);
        }), weight(0));
        row.addView(button(a.getString(R.string.recap_t10_continue), true, v -> {
            close();
            s.continueReading();
        }), weight(8));
        actions.addView(row);
        final TextView sized = text(a.getString(R.string.recap_t10_sized_for, away()), 12, 400, muted());
        add(actions, sized, 10);
    }

    private void longView() {
        final RecapPrompts.Recap r = s.recap;
        final boolean fiction = r == null || r.isFiction();
        body.addView(label(a.getString(fiction ? R.string.recap_t10_previously : R.string.recap_t10_argument, away()), true));
        add(body, text(a.getString(R.string.recap_t10_long_title), 22, 600, ink()), 4);
        segments();
        state();
        if (r != null && !r.story.isEmpty()) {
            final LinearLayout lines = new LinearLayout(a);
            lines.setOrientation(LinearLayout.VERTICAL);
            for (RecapPrompts.Line l : r.lines(s.level)) {
                add(lines, storyLine(l), 12);
            }
            add(body, lines, 4);
        } else {
            section(R.string.recap_t10_stopped_at, text(stoppedAt(), 14, 400, ink()));
            markSection();
        }
        if (r != null && s.level >= RecapLogic.LEVEL_2M && !r.cast.isEmpty()) {
            cast(r);
        }
        if (r != null && (!r.headline.isEmpty() || !r.detail.isEmpty())) {
            status(r);
        }
        if (r != null && s.warmupOpen && !r.warmup.isEmpty()) {
            warmup(r.warmup);
        }

        mapButton();
        final LinearLayout row = row();
        if (r != null && !r.warmup.isEmpty()) {
            row.addView(button(a.getResources().getQuantityString(R.plurals.recap_t10_warm_up, r.warmup.size(), r.warmup.size()),
                    false, v -> {
                        s.warmupOpen = !s.warmupOpen;
                        render();
                        if (s.warmupOpen) {
                            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                        }
                    }), weight(0));
        }
        final String at = RecapLogic.shortChapter(s.chapterTitle);
        row.addView(button(at.isEmpty() ? a.getString(R.string.recap_t10_continue) : a.getString(R.string.recap_t10_continue_at, at),
                true, v -> {
                    close();
                    s.continueReading();
                }), weight(row.getChildCount() > 0 ? 8 : 0));
        actions.addView(row);
    }

    /** Loading, an error, or no AI set up. */
    private void state() {
        if (s.loading) {
            final LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleSmall);
            row.addView(bar, new LinearLayout.LayoutParams(dp(18), dp(18)));
            final TextView t = text(a.getString(R.string.recap_t10_reading_back), 13, 400, muted());
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            p.leftMargin = dp(8);
            row.addView(t, p);
            add(body, row, 12);
        } else if (s.error != null) {
            add(body, text(s.error, 13, 400, CanvasLook.sensor()), 12);
            add(body, link(a.getString(R.string.recap_t10_retry), v -> s.askAgain()), 2);
        } else if (s.needSettings && s.recap == null) {
            add(body, text(a.getString(R.string.recap_t10_no_ai), 13, 400, muted()), 12);
            add(body, link(a.getString(R.string.recap_t10_ai_settings), v -> s.openAiSettings()), 2);
        }
    }

    private void section(int labelRes, View content) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView l = label(a.getString(labelRes), false);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        box.addView(l);
        add(box, content, 2);
        add(body, box, 10);
    }

    private void markSection() {
        final RecapLogic.MarkInfo m = s.mark;
        if (m == null) {
            return;
        }
        if (m.hasNote()) {
            final LinearLayout both = new LinearLayout(a);
            both.setOrientation(LinearLayout.VERTICAL);
            both.addView(text(m.note, 14, 400, ink()));
            if (!m.quote.isEmpty()) {
                add(both, quote(m.quote), 4);
            }
            section(R.string.recap_t10_your_note, both);
        } else {
            section(R.string.recap_t10_your_highlight, quote(m.quote));
        }
    }

    private TextView quote(String q) {
        final TextView t = text("“" + RecapLogic.cut(q, 400) + "”", 14, 400, ink2());
        t.setTypeface(serif(true));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private void mapButton() {
        if (s.studyMap == null) {
            return;
        }
        final TextView map = button(a.getString(R.string.recap_t10_open_map), false, v -> {
            close();
            s.openMap();
        });
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        p.bottomMargin = dp(8);
        actions.addView(map, p);
    }

    private void segments() {
        final LinearLayout track = row();
        track.setPadding(dp(3), dp(3), dp(3), dp(3));
        track.setBackground(canvas ? CanvasParts.segmentTrack() : CanvasParts.round(surface2(), 0, dp(12)));
        final int[] levels = {RecapLogic.LEVEL_30S, RecapLogic.LEVEL_2M, RecapLogic.LEVEL_5M};
        final int[] names = {R.string.recap_t10_30s, R.string.recap_t10_2m, R.string.recap_t10_5m};
        for (int i = 0; i < levels.length; i++) {
            final int level = levels[i];
            final boolean on = s.level == level;
            final TextView t = text(a.getString(names[i]), 13, 600, on ? ink() : ink2());
            t.setGravity(Gravity.CENTER);
            t.setSelected(on);
            if (on) {
                t.setBackground(canvas ? CanvasParts.segmentOn() : CanvasParts.round(bg(), 0, dp(9)));
            }
            t.setOnClickListener(v -> {
                s.level = level;
                render();
            });
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (i > 0) {
                p.leftMargin = dp(2);
            }
            track.addView(t, p);
        }
        add(body, track, 12);
    }

    private View storyLine(RecapPrompts.Line l) {
        final LinearLayout row = row();
        row.setGravity(Gravity.TOP);
        final View dot = new View(a);
        dot.setBackground(oval(accent()));
        final LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotParams.topMargin = dp(8);
        dotParams.rightMargin = dp(12);
        row.addView(dot, dotParams);
        final TextView t = text(l.text, 15, 400, ink());
        t.setTypeface(serif(false));
        t.setLineSpacing(0, 1.1f);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!l.chapter.isEmpty()) {
            final TextView ch = text(l.chapter, 11, 400, muted());
            ch.setTypeface(mono());
            ch.setMaxWidth(dp(96));
            final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.leftMargin = dp(12);
            cp.topMargin = dp(3);
            row.addView(ch, cp);
        }
        return row;
    }

    private void cast(RecapPrompts.Recap r) {
        final LinearLayout head = row();
        head.setGravity(Gravity.BOTTOM);
        final TextView l = label(a.getString(r.isFiction() ? R.string.recap_t10_whos_who : R.string.recap_t10_key_ideas), false);
        head.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(text(a.getString(R.string.recap_t10_tap_card), 12, 400, muted()));
        add(body, head, 18);
        LinearLayout row = null;
        for (int i = 0; i < r.cast.size(); i++) {
            if (i % 4 == 0) {
                row = row();
                add(body, row, i == 0 ? 8 : 6);
            }
            final RecapPrompts.Person p = r.cast.get(i);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % 4 > 0) {
                lp.leftMargin = dp(6);
            }
            row.addView(chip(p, i), lp);
        }
        // a short last row keeps its chips the size of the others
        if (row != null) {
            for (int i = r.cast.size() % 4; i > 0 && i < 4; i++) {
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
                lp.leftMargin = dp(6);
                row.addView(new View(a), lp);
            }
        }
    }

    private View chip(RecapPrompts.Person p, int index) {
        final LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setMinimumHeight(dp(72));
        c.setPadding(dp(4), dp(8), dp(4), dp(8));
        c.setBackground(CanvasParts.ripple(CanvasParts.round(bg(), line(), dp(12))));
        c.addView(avatar(p, index, 32));
        final TextView name = text(p.name, 12, 600, ink());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        add(c, name, 6);
        c.setContentDescription(p.name);
        c.setOnClickListener(v -> personCard(p, index));
        return c;
    }

    private TextView avatar(RecapPrompts.Person p, int index, int size) {
        final int[][] colors = {
                {ink(), bg()},
                {CanvasLook.claimTint(), accent()},
                {CanvasLook.sensorTint(), CanvasLook.sensor()},
                {surface2(), ink()}};
        final int[] c = colors[index % colors.length];
        final TextView t = text(p.initials, 13, 700, c[1]);
        t.setGravity(Gravity.CENTER);
        t.setBackground(oval(c[0]));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        t.setLayoutParams(lp);
        return t;
    }

    /** A who's-who card as of the reader's page. */
    private void personCard(RecapPrompts.Person p, int index) {
        final Dialog d = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(12));
        box.setBackground(CanvasParts.round(bg(), line(), dp(canvas ? CanvasLook.RADIUS_LARGE_DP : 8)));
        final LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(avatar(p, index, 36));
        final TextView name = text(p.name, 16, 600, ink());
        final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        np.leftMargin = dp(12);
        head.addView(name, np);
        box.addView(head);
        if (!p.card.isEmpty()) {
            final TextView card = text(p.card, 14, 400, ink());
            card.setLineSpacing(0, 1.15f);
            add(box, card, 12);
        }
        final TextView asOf = label(a.getString(R.string.recap_t10_as_of, Math.max(1, s.page + 1)), false);
        asOf.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        add(box, asOf, 12);
        final TextView ok = link(a.getString(R.string.close), v -> d.dismiss());
        final LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.gravity = Gravity.END;
        op.topMargin = dp(4);
        box.addView(ok, op);
        d.setContentView(box);
        final Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.setCanceledOnTouchOutside(true);
        try {
            d.show();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private void status(RecapPrompts.Recap r) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(CanvasParts.round(canvas ? CanvasLook.surface() : surface2(), 0, dp(14)));
        if (!r.headline.isEmpty()) {
            box.addView(text(r.headline, 15, 600, ink()));
        }
        // how far into the book the reader is
        final float done = s.pages > 0 ? Math.max(0f, Math.min(1f, (s.page + 1) / (float) s.pages)) : 0f;
        final LinearLayout track = row();
        track.setBackground(CanvasParts.round(line(), 0, dp(3)));
        final View fill = new View(a);
        fill.setBackground(CanvasParts.round(accent(), 0, dp(3)));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), done));
        track.addView(new View(a), new LinearLayout.LayoutParams(0, dp(6), 1f - done));
        add(box, track, 8);
        if (!r.detail.isEmpty()) {
            add(box, text(r.detail, 12, 400, ink2()), 8);
        }
        add(body, box, 14);
    }

    private void warmup(List<RecapPrompts.Question> questions) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(label(a.getString(R.string.recap_t10_warm_up_label), true));
        for (RecapPrompts.Question q : questions) {
            add(box, text(q.q, 14, 500, ink()), 8);
            if (!q.a.isEmpty()) {
                final TextView answer = link(a.getString(R.string.recap_t10_show_answer), null);
                answer.setOnClickListener(v -> {
                    answer.setText(q.a);
                    answer.setTextColor(ink2());
                    answer.setTypeface(canvas ? CanvasLook.ui(a, 400) : Typeface.DEFAULT);
                    answer.setOnClickListener(null);
                    answer.setClickable(false);
                });
                add(box, answer, 2);
            }
        }
        add(body, box, 16);
    }

    // ------------------------------------------------------------------ words

    private String away() {
        final int[] away = RecapLogic.away(s.days);
        final int plural = away[1] == RecapLogic.UNIT_MONTHS ? R.plurals.recap_t10_months
                : away[1] == RecapLogic.UNIT_WEEKS ? R.plurals.recap_t10_weeks : R.plurals.recap_t10_days;
        return a.getResources().getQuantityString(plural, away[0], away[0]);
    }

    private String stoppedAt() {
        final RecapPrompts.Recap r = s.recap;
        if (r != null && !r.stoppedAt.isEmpty()) {
            return r.stoppedAt;
        }
        final String page = a.getString(R.string.recap_t10_page_of, Math.max(1, s.page + 1), Math.max(1, s.pages));
        return s.chapterTitle == null || s.chapterTitle.isEmpty() ? page : s.chapterTitle + " · " + page;
    }

    // ------------------------------------------------------------------ the look

    private int bg() {
        return canvas ? CanvasLook.bg() : dark ? 0xFF1B1E24 : 0xFFFFFFFF;
    }

    private int ink() {
        return canvas ? CanvasLook.ink() : dark ? 0xFFE9EBEF : 0xFF202124;
    }

    private int ink2() {
        return canvas ? CanvasLook.ink2() : dark ? 0xFFC3C7CF : 0xFF464C57;
    }

    private int muted() {
        return canvas ? CanvasLook.muted() : dark ? 0xFF9AA0AA : 0xFF5F6368;
    }

    private int line() {
        return canvas ? CanvasLook.line() : dark ? 0xFF3A3E48 : 0xFFDADCE0;
    }

    private int surface2() {
        return canvas ? CanvasLook.surface2() : dark ? 0xFF23262D : 0xFFF1F3F4;
    }

    private int accent() {
        return canvas ? CanvasLook.accent() : TintUtil.color;
    }

    private int dp(int dp) {
        return CanvasLook.dp(dp);
    }

    private TextView text(String value, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(canvas ? CanvasLook.ui(a, weight) : weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    /** A small uppercase label. */
    private TextView label(String value, boolean accent) {
        final TextView t = new TextView(a);
        t.setText(value);
        if (canvas) {
            CanvasParts.label(t, accent);
        } else {
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t.setAllCaps(true);
            t.setLetterSpacing(0.05f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(accent ? accent() : muted());
        }
        return t;
    }

    private TextView link(String value, View.OnClickListener click) {
        final TextView t = text(value, 14, 600, accent());
        t.setMinHeight(dp(36));
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (click != null) {
            t.setOnClickListener(click);
        }
        return t;
    }

    private TextView button(String value, boolean primary, View.OnClickListener click) {
        final TextView b = text(value, 14, 600, primary ? (canvas ? CanvasLook.onAccent() : Color.WHITE) : ink());
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(44));
        final Drawable back;
        if (canvas) {
            back = primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton();
        } else {
            back = CanvasParts.ripple(primary ? CanvasParts.round(accent(), 0, dp(6)) : CanvasParts.round(bg(), line(), dp(6)));
        }
        b.setBackground(back);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout row() {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        final ViewGroup.LayoutParams given = child.getLayoutParams();
        final LinearLayout.LayoutParams p = given instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) given
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topMargin);
        parent.addView(child, p);
    }

    private static GradientDrawable oval(int color) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private static GradientDrawable topRounded(int color, int radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        d.setColor(color);
        return d;
    }

    private Typeface mono() {
        return canvas ? CanvasLook.mono(a, 400) : Typeface.MONOSPACE;
    }

    /** Literata (the canvas's reading face, bundled for the reading background) in the canvas look; serif otherwise. */
    private Typeface serif(boolean italic) {
        if (!canvas) {
            return Typeface.create(Typeface.SERIF, italic ? Typeface.ITALIC : Typeface.NORMAL);
        }
        synchronized (RecapSheet.class) {
            if (serif == null) {
                serif = asset(a, "fonts-fork/Literata-Regular.ttf", Typeface.SERIF);
                serifItalic = asset(a, "fonts-fork/Literata-Italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            }
            return italic ? serifItalic : serif;
        }
    }

    private static Typeface asset(Context c, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getApplicationContext().getAssets(), path);
        } catch (Exception e) {
            LOG.e(e);
            return fallback;
        }
    }

    /** A scroll view no taller than a share of the screen, so the buttons stay in sight. */
    private static final class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int mode = MeasureSpec.getMode(heightMeasureSpec);
            final int size = MeasureSpec.getSize(heightMeasureSpec);
            final int limit = mode == MeasureSpec.UNSPECIFIED ? max : Math.min(max, size);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
}
