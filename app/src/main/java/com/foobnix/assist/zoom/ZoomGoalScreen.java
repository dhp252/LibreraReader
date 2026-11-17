package com.foobnix.assist.zoom;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * "Why are you reading this?" (canvas ZoomGoal): the four goals, the reader's own words, and, once
 * the chapter was condensed for the goal shown, what that changed ("15 → 6 min", kept in full,
 * squeezed). "Start reading" hands the goal back to the zoom view; closing changes nothing. The
 * screen itself sends and stores nothing.
 */
final class ZoomGoalScreen {

    /** What "Start reading" hands back. */
    interface OnStart {
        void onGoal(ZoomGoal goal);
    }

    private static final ZoomGoal.Kind[] KINDS = ZoomGoal.Kind.values();
    private static final int[] TITLES = {R.string.zoom_goal_ideas, R.string.zoom_goal_work, R.string.zoom_goal_discuss,
            R.string.zoom_goal_study};
    private static final int[] INFOS = {R.string.zoom_goal_ideas_info, R.string.zoom_goal_work_info,
            R.string.zoom_goal_discuss_info, R.string.zoom_goal_study_info};

    private final Activity a;
    private final String bookGoal;
    /** The chapter's levels, when it has them, and its times in minutes. */
    private final ZoomData data;
    private final int fullMinutes;
    private final int leanMinutes;
    private ZoomGoal.Kind kind;

    private final LinearLayout[] options = new LinearLayout[KINDS.length];
    private final View[] radios = new View[KINDS.length];
    private EditText words;
    private LinearLayout changes;

    private ZoomGoalScreen(Activity a, ZoomGoal goal, String bookGoal, ZoomData data, int fullMinutes, int leanMinutes) {
        this.a = a;
        this.bookGoal = bookGoal == null ? "" : bookGoal;
        this.data = data;
        this.fullMinutes = fullMinutes;
        this.leanMinutes = leanMinutes;
        this.kind = goal == null ? ZoomGoal.Kind.IDEAS : goal.kind;
    }

    /** The title of a goal, for the zoom bar. */
    static int title(ZoomGoal.Kind kind) {
        return TITLES[kind.ordinal()];
    }

    /**
     * @param goal     the book's goal so far, or null
     * @param bookGoal the reading goal of the book's key passages: the default of the own words
     * @param data     the chapter's levels, or null before its first request
     */
    static Dialog show(Activity a, String bookTitle, ZoomGoal goal, String bookGoal, ZoomData data, int fullMinutes,
                       int leanMinutes, OnStart onStart) {
        final ZoomGoalScreen s = new ZoomGoalScreen(a, goal, bookGoal, data, fullMinutes, leanMinutes);
        return s.open(bookTitle, goal, onStart);
    }

    private Dialog open(String bookTitle, ZoomGoal goal, OnStart onStart) {
        final boolean dark = CanvasLook.isNight();
        final Dialog dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);

        // top bar: close, the book, "Reading goal"
        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView close = ZoomLook.text(a, "✕", 18, 400, CanvasLook.ink());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(a.getString(R.string.close));
        close.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        final TextView title = ZoomLook.text(a, bookTitle, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title);
        final TextView sub = ZoomLook.text(a, a.getString(R.string.zoom_goal_title), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        sub.setGravity(Gravity.CENTER);
        titles.addView(sub);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        page.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));

        final ScrollView scroll = new ScrollView(a);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final TextView label = ZoomLook.mono(a, a.getString(R.string.zoom_title), CanvasLook.TEXT_LABEL, CanvasLook.accent(), true);
        label.setPadding(CanvasLook.dp(4), 0, 0, 0);
        body.addView(label);
        final TextView question = ZoomLook.text(a, a.getString(R.string.zoom_goal_question), 22, 600, CanvasLook.ink());
        question.setPadding(CanvasLook.dp(4), CanvasLook.dp(4), 0, 0);
        body.addView(question);
        final TextView why = ZoomLook.text(a, a.getString(R.string.zoom_goal_why), 13, 400, CanvasLook.ink2());
        why.setPadding(CanvasLook.dp(4), CanvasLook.dp(4), 0, CanvasLook.dp(6));
        body.addView(why);

        for (int i = 0; i < KINDS.length; i++) {
            body.addView(option(i), top(8));
        }

        // the reader's own words; they start as the book's reading goal when it has one
        final LinearLayout own = new LinearLayout(a);
        own.setOrientation(LinearLayout.HORIZONTAL);
        own.addView(ZoomLook.text(a, a.getString(R.string.zoom_goal_own), 13, 600, CanvasLook.ink()));
        final TextView optional = ZoomLook.text(a, a.getString(R.string.zoom_goal_optional), 13, 400, CanvasLook.muted());
        optional.setPadding(CanvasLook.dp(4), 0, 0, 0);
        own.addView(optional);
        body.addView(own, top(14));
        words = new EditText(a);
        words.setTypeface(CanvasLook.ui(a, 400));
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        words.setTextColor(CanvasLook.ink());
        words.setHintTextColor(CanvasLook.muted());
        words.setHint(R.string.zoom_goal_own_hint);
        words.setSingleLine(true);
        words.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        words.setMinHeight(CanvasLook.dp(48));
        words.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        words.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.radius()));
        final String start = goal == null ? this.bookGoal : goal.words(this.bookGoal);
        words.setText(start);
        words.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showChanges();
            }
        });
        body.addView(words, top(6));
        if (!this.bookGoal.isEmpty() && start.equals(this.bookGoal)) {
            final TextView from = ZoomLook.text(a, a.getString(R.string.zoom_goal_from_book), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            from.setPadding(CanvasLook.dp(4), CanvasLook.dp(4), 0, 0);
            body.addView(from);
        }

        changes = new LinearLayout(a);
        changes.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(14);
        changes.setPadding(pad, pad, pad, pad);
        changes.setBackground(CanvasParts.round(CanvasLook.surface(), 0, CanvasLook.dp(16)));
        body.addView(changes, top(14));

        // the button stays at the bottom
        final LinearLayout foot = new LinearLayout(a);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(gutter, CanvasLook.dp(12), gutter, CanvasLook.dp(16));
        final TextView go = ZoomLook.button(a, a.getString(R.string.zoom_goal_start) + "  ›", true);
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        go.setOnClickListener(v -> {
            final ZoomGoal chosen = chosen();
            dialog.dismiss();
            onStart.onGoal(chosen);
        });
        foot.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
        final TextView note = ZoomLook.text(a, a.getString(R.string.zoom_goal_change_note), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, CanvasLook.dp(8), 0, 0);
        foot.addView(note);
        page.addView(foot);

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        select(kind);
        dialog.show();
        return dialog;
    }

    private ZoomGoal chosen() {
        return ZoomGoal.chosen(kind, words.getText().toString(), bookGoal);
    }

    /** One goal: a radio mark, its name, what it keeps. */
    private View option(int index) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.dp(62));
        row.setPadding(CanvasLook.dp(14), CanvasLook.dp(10), CanvasLook.dp(14), CanvasLook.dp(10));
        final View radio = new View(a);
        final LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20));
        rlp.rightMargin = CanvasLook.dp(12);
        row.addView(radio, rlp);
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(ZoomLook.text(a, a.getString(TITLES[index]), CanvasLook.TEXT_BODY, 600, CanvasLook.ink()));
        final TextView info = ZoomLook.text(a, a.getString(INFOS[index]), 13, 400, CanvasLook.ink2());
        info.setPadding(0, CanvasLook.dp(2), 0, 0);
        texts.addView(info);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(v -> select(KINDS[index]));
        options[index] = row;
        radios[index] = radio;
        return row;
    }

    private void select(ZoomGoal.Kind chosen) {
        kind = chosen;
        final int radius = CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP);
        for (int i = 0; i < KINDS.length; i++) {
            final boolean on = KINDS[i] == kind;
            final GradientDrawable bg = CanvasParts.round(on ? CanvasLook.accentTint() : CanvasLook.bg(), 0, radius);
            bg.setStroke(on ? Math.max(1, CanvasLook.dp(3) / 2) : Math.max(1, CanvasLook.dp(1)),
                    on ? CanvasLook.accent() : CanvasLook.line());
            options[i].setBackground(CanvasParts.ripple(bg));
            options[i].setSelected(on);
            final GradientDrawable mark = new GradientDrawable();
            mark.setShape(GradientDrawable.OVAL);
            mark.setColor(on ? CanvasLook.onAccent() : 0);
            mark.setStroke(CanvasLook.dp(on ? 6 : 2), on ? CanvasLook.accent() : CanvasLook.lineStrong());
            radios[i].setBackground(mark);
        }
        showChanges();
    }

    /**
     * "WHAT THIS CHANGES IN THIS CHAPTER": only for the goal the chapter was condensed for; for
     * another choice it says that it shows after the chapter is condensed for it.
     */
    private void showChanges() {
        if (changes == null || words == null) {
            return;
        }
        changes.removeAllViews();
        final boolean shown = data != null && data.goal.equals(chosen().id(bookGoal))
                && (!data.kept.isEmpty() || !data.squeezed.isEmpty());
        if (!shown) {
            changes.addView(ZoomLook.text(a, a.getString(R.string.zoom_goal_changes_later), 13, 400, CanvasLook.muted()));
            return;
        }
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ZoomLook.mono(a, a.getString(R.string.zoom_goal_changes), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final String time = ZoomTime.change(fullMinutes, leanMinutes);
        if (!time.isEmpty()) {
            head.addView(ZoomLook.text(a, time, 13, 600, CanvasLook.ink()));
        }
        changes.addView(head);
        final LinearLayout columns = new LinearLayout(a);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(0, CanvasLook.dp(10), 0, 0);
        columns.addView(column(R.string.zoom_goal_kept, data.kept, CanvasLook.accent(), CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        right.leftMargin = CanvasLook.dp(12);
        columns.addView(column(R.string.zoom_goal_squeezed, data.squeezed, CanvasLook.ink2(), CanvasLook.ink2()), right);
        changes.addView(columns);
    }

    private View column(int title, java.util.List<String> names, int titleColor, int color) {
        final LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(ZoomLook.text(a, a.getString(title), CanvasLook.TEXT_SMALL, 600, titleColor));
        for (String name : names) {
            final TextView t = ZoomLook.text(a, name, 13, 400, color);
            t.setPadding(0, CanvasLook.dp(6), 0, 0);
            c.addView(t);
        }
        return c;
    }

    private static LinearLayout.LayoutParams top(int dp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(dp);
        return lp;
    }
}
