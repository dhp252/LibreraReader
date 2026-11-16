package com.foobnix.assist.prequestions;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.Locale;

/**
 * The guesses before a chapter, full screen in the canvas look: one question at a time (canvas
 * "Pre-question": options, own words, how sure), then the locked-in list (canvas "Pre-questions
 * locked in"). The answers are not shown here; they come while reading. Each guess is saved as
 * it is locked in, so closing half way keeps what was given.
 */
final class PreQDialog {

    private final Activity a;
    private final PreQSession session;
    private final String chapterKey;
    private final PreQ.Chapter chapter;
    private final String chapterTitle;
    private final boolean dark = CanvasLook.isNight();

    private Dialog dialog;
    private TextView subtitle;
    private LinearLayout segments;
    private LinearLayout content;
    private LinearLayout actions;
    private ScrollView scroll;

    private int index;
    private int picked = PreQ.NONE;
    private int confidence = PreQ.GUESSING;
    private String own = "";

    private PreQDialog(Activity a, PreQSession session, String chapterKey, PreQ.Chapter chapter, String chapterTitle) {
        this.a = a;
        this.session = session;
        this.chapterKey = chapterKey;
        this.chapter = chapter;
        this.chapterTitle = chapterTitle;
    }

    static void show(Activity a, PreQSession session, String chapterKey, PreQ.Chapter chapter, String chapterTitle) {
        if (chapter.questions.isEmpty()) {
            return;
        }
        new PreQDialog(a, session, chapterKey, chapter, chapterTitle).open();
    }

    private void open() {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());

        // top bar: close, "Before <chapter>", "Pre-question 1 of 3"
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView close = text("✕", 18, 400, CanvasLook.ink());
        close.setGravity(Gravity.CENTER);
        close.setBackground(CanvasParts.ripple(null));
        close.setContentDescription(a.getString(R.string.close));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = text(a.getString(R.string.preq_before, chapterTitle), 14, 600, CanvasLook.ink());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        subtitle = text("", 12, 400, CanvasLook.muted());
        subtitle.setGravity(Gravity.CENTER);
        titles.addView(subtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        page.addView(bar);

        segments = new LinearLayout(a);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setPadding(CanvasLook.dp(20), CanvasLook.dp(4), CanvasLook.dp(20), 0);
        page.addView(segments);

        scroll = new ScrollView(a);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(CanvasLook.dp(16), 0, CanvasLook.dp(16), CanvasLook.dp(8));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(16));
        page.addView(actions);

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);

        index = Math.max(0, chapter.nextOpen());
        if (chapter.nextOpen() < 0) {
            index = chapter.questions.size();
        }
        render();
        dialog.show();
    }

    private int count() {
        return chapter.questions.size();
    }

    private void render() {
        content.removeAllViews();
        actions.removeAllViews();
        segments(index >= count() ? count() : index + 1);
        if (index >= count()) {
            lockedIn();
        } else {
            guess(chapter.questions.get(index));
        }
        scroll.scrollTo(0, 0);
    }

    private void segments(int done) {
        segments.removeAllViews();
        for (int i = 0; i < count(); i++) {
            final View s = new View(a);
            s.setBackground(CanvasParts.round(i < done ? CanvasLook.accent() : CanvasLook.line(), 0, CanvasLook.dp(2)));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, CanvasLook.dp(4), 1);
            if (i > 0) {
                p.leftMargin = CanvasLook.dp(4);
            }
            segments.addView(s, p);
        }
    }

    // ------------------------------------------------------------------ one question

    private void guess(PreQ.Question q) {
        subtitle.setText(a.getString(R.string.preq_n_of, index + 1, count()));
        picked = q.guess;
        own = q.own;
        confidence = q.confidence >= 0 ? q.confidence : PreQ.GUESSING;

        final TextView label = new TextView(a);
        label.setText(R.string.preq_guess_first);
        CanvasParts.label(label, true);
        label.setPadding(CanvasLook.dp(4), CanvasLook.dp(22), 0, 0);
        content.addView(label);
        final TextView question = text(q.text, 20, 600, CanvasLook.ink());
        question.setLineSpacing(CanvasLook.dp(4), 1f);
        question.setPadding(CanvasLook.dp(4), CanvasLook.dp(8), CanvasLook.dp(4), 0);
        content.addView(question);

        // the options, two to a row
        final TextView[] options = new TextView[q.options.size()];
        final TextView lock = primary(a.getString(R.string.preq_lock_in));
        final EditText words = new EditText(a);
        final Runnable refresh = () -> {
            for (int i = 0; i < options.length; i++) {
                option(options[i], i == picked);
            }
            lock.setEnabled(picked >= 0 || !own.trim().isEmpty());
            lock.setAlpha(lock.isEnabled() ? 1f : 0.4f);
        };
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = CanvasLook.dp(i == 0 ? 18 : 8);
                content.addView(row, rp);
            }
            final int at = i;
            final TextView o = text(q.options.get(i), 17, 600, CanvasLook.ink());
            o.setGravity(Gravity.CENTER);
            o.setMinHeight(CanvasLook.dp(64));
            o.setPadding(CanvasLook.dp(12), CanvasLook.dp(8), CanvasLook.dp(12), CanvasLook.dp(8));
            o.setOnClickListener(v -> {
                picked = picked == at ? PreQ.NONE : at;
                if (picked >= 0 && !own.isEmpty()) {
                    // one guess: an option or the reader's own words
                    own = "";
                    words.setText("");
                }
                refresh.run();
            });
            options[i] = o;
            final LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i % 2 == 1) {
                op.leftMargin = CanvasLook.dp(8);
            }
            row.addView(o, op);
        }
        if (options.length % 2 == 1 && row != null) {
            row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        }

        // or in the reader's own words
        words.setText(own);
        words.setHint(R.string.preq_own_words);
        CanvasParts.text(words, 15, 400);
        words.setHintTextColor(CanvasLook.muted());
        words.setBackground(CanvasParts.field());
        words.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        words.setSingleLine(true);
        words.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                own = s.toString();
                if (!own.trim().isEmpty()) {
                    picked = PreQ.NONE;
                }
                refresh.run();
            }
        });
        final LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = CanvasLook.dp(10);
        content.addView(words, wp);

        // how sure
        final TextView sure = text(a.getString(R.string.preq_how_sure), 14, 600, CanvasLook.ink());
        sure.setPadding(CanvasLook.dp(4), CanvasLook.dp(20), 0, CanvasLook.dp(6));
        content.addView(sure);
        content.addView(confidence());

        // why guess at all
        final TextView tip = text(a.getString(R.string.preq_tip), 13, 400, CanvasLook.ink2());
        tip.setLineSpacing(CanvasLook.dp(3), 1f);
        tip.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(14)));
        tip.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = CanvasLook.dp(20);
        content.addView(tip, tp);

        lock.setOnClickListener(v -> {
            if (picked < 0 && own.trim().isEmpty()) {
                return;
            }
            q.guess = picked;
            q.own = picked >= 0 ? "" : own.trim();
            q.confidence = confidence;
            q.skipped = false;
            next();
        });
        actions.addView(lock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
        final TextView skip = secondary(a.getString(R.string.preq_skip));
        skip.setOnClickListener(v -> {
            q.guess = PreQ.NONE;
            q.own = "";
            q.confidence = PreQ.NONE;
            q.skipped = true;
            next();
        });
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.target());
        sp.topMargin = CanvasLook.dp(4);
        actions.addView(skip, sp);
        refresh.run();
    }

    private void option(TextView o, boolean on) {
        final GradientDrawable d = CanvasParts.round(on ? CanvasLook.accentTint() : CanvasLook.bg(), 0, CanvasLook.dp(14));
        d.setStroke(Math.max(2, Math.round(1.5f * a.getResources().getDisplayMetrics().density)),
                on ? CanvasLook.accent() : CanvasLook.line());
        o.setBackground(d);
        o.setTextColor(on ? CanvasLook.accent() : CanvasLook.ink());
    }

    /** Guessing / Fairly sure / Certain, as a segmented control. */
    private View confidence() {
        final LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(CanvasParts.segmentTrack());
        final int pad = CanvasLook.dp(3);
        track.setPadding(pad, pad, pad, pad);
        final int[] names = {R.string.preq_guessing, R.string.preq_fairly_sure, R.string.preq_certain};
        final TextView[] parts = new TextView[names.length];
        final Runnable refresh = () -> {
            for (int i = 0; i < parts.length; i++) {
                final boolean on = i == confidence;
                parts[i].setBackground(on ? CanvasParts.segmentOn() : null);
                parts[i].setElevation(on ? CanvasLook.dp(1) : 0);
                parts[i].setTextColor(on ? CanvasLook.ink() : CanvasLook.ink2());
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int level = i;
            final TextView t = text(a.getString(names[i]), 13, 600, CanvasLook.ink2());
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                confidence = level;
                refresh.run();
            });
            parts[i] = t;
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, CanvasLook.target(), 1);
            if (i > 0) {
                p.leftMargin = CanvasLook.dp(2);
            }
            track.addView(t, p);
        }
        refresh.run();
        return track;
    }

    private void next() {
        index++;
        if (index >= count() || chapter.nextOpen() < 0) {
            chapter.state = PreQ.LOCKED;
            chapter.locked = System.currentTimeMillis();
            index = count();
        } else {
            index = chapter.nextOpen();
        }
        session.save(chapterKey, chapter);
        render();
    }

    // ------------------------------------------------------------------ locked in

    private void lockedIn() {
        subtitle.setText(a.getString(R.string.preq_all_in, count()));
        final TextView label = new TextView(a);
        label.setText(R.string.preq_locked_in);
        CanvasParts.label(label, true);
        label.setPadding(CanvasLook.dp(4), CanvasLook.dp(22), 0, 0);
        content.addView(label);
        final TextView h = text(a.getString(R.string.preq_saved), 22, 600, CanvasLook.ink());
        h.setPadding(CanvasLook.dp(4), CanvasLook.dp(4), 0, 0);
        content.addView(h);
        final TextView line = text(a.getString(R.string.preq_saved_line), 13, 400, CanvasLook.ink2());
        line.setPadding(CanvasLook.dp(4), CanvasLook.dp(4), CanvasLook.dp(4), CanvasLook.dp(8));
        content.addView(line);

        for (int i = 0; i < count(); i++) {
            final PreQ.Question q = chapter.questions.get(i);
            final LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(16)));
            card.setPadding(CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14));

            final LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            final TextView n = new TextView(a);
            n.setText(String.valueOf(i + 1));
            CanvasParts.label(n, false);
            n.setPadding(0, CanvasLook.dp(3), CanvasLook.dp(10), 0);
            top.addView(n);
            final TextView qt = text(q.shortText.isEmpty() ? q.text : q.shortText, 15, 600, CanvasLook.ink());
            top.addView(qt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(top);

            final LinearLayout bottom = new LinearLayout(a);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            bottom.setPadding(CanvasLook.dp(18), CanvasLook.dp(8), 0, 0);
            final TextView you = text("", 13, 400, CanvasLook.muted());
            you.setText(you(q));
            bottom.addView(you, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (q.page >= 0) {
                final TextView where = text(a.getString(R.string.preq_in_page, q.page + 1), 12, 400, CanvasLook.ink2());
                where.setPadding(CanvasLook.dp(8), 0, 0, 0);
                bottom.addView(where);
            }
            card.addView(bottom);

            final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = CanvasLook.dp(10);
            content.addView(card, cp);
        }

        final TextView start = primary(a.getString(R.string.preq_start_reading));
        start.setOnClickListener(v -> dialog.dismiss());
        actions.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
    }

    /** "You: about 2,000 · guessing", the guess in the accent. */
    private CharSequence you(PreQ.Question q) {
        final SpannableStringBuilder s = new SpannableStringBuilder(a.getString(R.string.preq_you));
        if (q.skipped || q.guessText().isEmpty()) {
            s.append(a.getString(R.string.preq_skipped));
            return s;
        }
        final int from = s.length();
        s.append(PreQ.inSentence(q.guessText()));
        s.setSpan(new ForegroundColorSpan(CanvasLook.accent()), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (q.confidence >= 0) {
            s.append(" · ").append(confidenceName(q.confidence).toLowerCase(Locale.getDefault()));
        }
        return s;
    }

    private String confidenceName(int level) {
        switch (level) {
            case PreQ.CERTAIN:
                return a.getString(R.string.preq_certain);
            case PreQ.FAIRLY_SURE:
                return a.getString(R.string.preq_fairly_sure);
            default:
                return a.getString(R.string.preq_guessing);
        }
    }

    // ------------------------------------------------------------------ parts

    private TextView primary(String label) {
        final TextView b = text(label, 15, 600, CanvasLook.onAccent());
        b.setGravity(Gravity.CENTER);
        b.setBackground(CanvasParts.primaryButton());
        return b;
    }

    private TextView secondary(String label) {
        final TextView b = text(label, 14, 600, CanvasLook.ink2());
        b.setGravity(Gravity.CENTER);
        b.setBackground(CanvasParts.ripple(null));
        return b;
    }

    private TextView text(String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.text(t, sp, weight);
        t.setTextColor(color);
        return t;
    }
}
