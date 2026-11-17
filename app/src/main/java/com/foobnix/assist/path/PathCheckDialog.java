package com.foobnix.assist.path;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "2-minute check · Skip what you already know" (canvas artboard PathCheck): one scenario
 * question per chapter of the path (not the goal chapter), made in one request from the chapter
 * lines; two minutes for all of them. A right answer takes the chapter off the path ("Skipped.
 * That saves 7 min." with Undo), a wrong one or "Not sure" keeps it. Every answer is saved into
 * the stored path at once, so closing the check loses nothing.
 */
final class PathCheckDialog implements PathJob.Screen {

    /** Two minutes for the whole check. */
    static final int SECONDS = 120;

    private final Activity a;
    private final AiJobs.Target target;
    /** The path screen's own data: what the check changes shows there. */
    private final PathData data;
    private final int wpm;
    private final Runnable onChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Dialog dialog;
    private LinearLayout content;
    private LinearLayout bottom;
    private TextView timeLabel;

    private PathJob job;
    private String status = "";
    private String error;
    /** The chapters asked, by key, in the path's order; null until the questions are there. */
    private List<String> queue;
    private int index;
    private int picked = -1;
    private int secondsLeft = SECONDS;
    private boolean timeUp;
    /** The chapter the last answer was about, and whether it went off the path. */
    private String lastKey;
    private boolean lastKnown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!alive() || finished()) {
                return;
            }
            secondsLeft--;
            if (secondsLeft <= 0) {
                secondsLeft = 0;
                timeUp = true;
                render();
                return;
            }
            showTime();
            handler.postDelayed(this, 1000);
        }
    };

    private PathCheckDialog(Activity a, AiJobs.Target target, PathData data, int wpm, Runnable onChanged) {
        this.a = a;
        this.target = target;
        this.data = data;
        this.wpm = wpm;
        this.onChanged = onChanged;
    }

    /**
     * @param data      the path; the check changes it in place and in the store
     * @param onChanged told after every change (main thread)
     */
    static void show(Activity a, AiJobs.Target target, PathData data, int wpm, Runnable onChanged) {
        if (!Assist.PATH.isOn() || a == null || data == null || !data.canCheck()) {
            return;
        }
        new PathCheckDialog(a, target, data, wpm, onChanged).open();
    }

    private void open() {
        dialog = PathViews.fullScreen(a);
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CanvasLook.bg());
        root.addView(PathViews.topBar(a, R.drawable.glyphicons_599_menu_close, R.string.close, () -> dialog.dismiss(),
                a.getString(R.string.canvas_t20_check_title), a.getString(R.string.canvas_t20_check_subtitle)));

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(12));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.addView(bottom);

        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(tick);
            if (job != null) {
                job.cancel();
            }
        });
        PathViews.show(dialog, root);

        boolean missing = false;
        for (PathData.Chapter c : data.toCheck()) {
            missing |= c.question == null;
        }
        if (missing) {
            ask();
        } else {
            start();
        }
    }

    private boolean alive() {
        return dialog != null && dialog.isShowing() && !Apps.isDestroyedActivity(a);
    }

    private boolean finished() {
        return timeUp || (queue != null && index >= queue.size());
    }

    // ------------------------------------------------------------------ the questions

    private void ask() {
        error = null;
        status = "";
        job = PathJob.check(a, target, data, this);
        job.start();
        render();
    }

    @Override
    public void onStatus(String text) {
        if (alive()) {
            status = text;
            render();
        }
    }

    @Override
    public void onPath(PathData made, Map<String, int[]> ranges, int pageCount) {
        // not asked for here
    }

    @Override
    public void onQuestions(PathData withQuestions) {
        job = null;
        if (!alive()) {
            return;
        }
        if (withQuestions != data) {
            for (PathData.Chapter c : data.chapters) {
                final PathData.Chapter stored = withQuestions.chapter(c.key);
                if (c.question == null && stored != null) {
                    c.question = stored.question;
                }
            }
        }
        start();
    }

    @Override
    public void onNeedSkeleton() {
        // only the path's own lines are used
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (alive()) {
            dialog.dismiss();
            AiSettingsDialog.show(a, null);
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        if (alive()) {
            error = TextUtils.isEmpty(message) ? a.getString(R.string.canvas_t20_unreadable) : message;
            render();
        }
    }

    /** The questions are there: the two minutes start. */
    private void start() {
        queue = new ArrayList<>();
        for (PathData.Chapter c : data.toCheck()) {
            if (c.question != null) {
                queue.add(c.key);
            }
        }
        if (queue.isEmpty()) {
            queue = null;
            error = a.getString(R.string.canvas_t20_unreadable);
            render();
            return;
        }
        index = 0;
        picked = -1;
        secondsLeft = SECONDS;
        timeUp = false;
        render();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }

    // ------------------------------------------------------------------ answers

    private void answer(int option) {
        if (queue == null || finished()) {
            return;
        }
        final String key = queue.get(index);
        final String goal = data.goal;
        final boolean known = data.answer(key, option);
        PathStore.later(target.name, goal, d -> d.answer(key, option));
        lastKey = key;
        lastKnown = known;
        index++;
        picked = -1;
        changed();
    }

    private void undo() {
        final String key = lastKey;
        if (key != null && data.putBack(key)) {
            PathStore.later(target.name, data.goal, d -> d.putBack(key));
        }
        lastKey = null;
        changed();
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
        render();
    }

    // ------------------------------------------------------------------ drawing

    private void showTime() {
        if (timeLabel != null) {
            timeLabel.setText(a.getString(R.string.canvas_t20_time_left, secondsLeft / 60, secondsLeft % 60)
                    .toUpperCase(Locale.getDefault()));
        }
    }

    private void render() {
        if (dialog == null) {
            return;
        }
        content.removeAllViews();
        bottom.removeAllViews();
        timeLabel = null;
        if (queue == null) {
            renderWaiting();
            return;
        }
        renderProgress();
        renderBanner();
        if (finished()) {
            renderFinished();
        } else {
            renderQuestion();
        }
        renderBottom();
    }

    /** The questions are being made, or could not be. */
    private void renderWaiting() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), 0);
        box.addView(PathViews.text(a, a.getString(R.string.canvas_t20_check_intro), CanvasLook.TEXT_BODY, 500,
                CanvasLook.ink()));
        if (job != null) {
            box.addView(PathViews.busyRow(a, TextUtils.isEmpty(status) ? a.getString(R.string.canvas_t20_making_questions)
                    : status, () -> dialog.dismiss()), PathViews.wide(14));
        } else if (error != null) {
            box.addView(PathViews.text(a, error, 13, 400, CanvasLook.sensor()), PathViews.wide(12));
            final TextView retry = PathViews.primary(a, a.getString(R.string.study_retry));
            retry.setOnClickListener(v -> ask());
            box.addView(retry, PathViews.wide(48, 14));
        }
        content.addView(box);
    }

    /** One segment per question (done in ink, this one in the accent), and the time left. */
    private void renderProgress() {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(20), 0);
        final LinearLayout segments = new LinearLayout(a);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < queue.size(); i++) {
            final View s = new View(a);
            s.setBackground(CanvasParts.round(i < index ? CanvasLook.ink()
                    : i == index && !finished() ? CanvasLook.accent() : CanvasLook.line(), 0, dp(2)));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(4), 1);
            if (i > 0) {
                p.leftMargin = dp(4);
            }
            segments.addView(s, p);
        }
        row.addView(segments, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        timeLabel = PathViews.mono(a, "", CanvasLook.ink2());
        timeLabel.setTypeface(CanvasLook.mono(a, 400));
        final LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.leftMargin = dp(10);
        row.addView(timeLabel, timeParams);
        showTime();
        content.addView(row);
    }

    /** "You know I.2 · Self-interest / Skipped. That saves 7 min. / Undo", or that the chapter stays. */
    private void renderBanner() {
        final PathData.Chapter c = lastKey == null ? null : data.chapter(lastKey);
        if (c == null) {
            return;
        }
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(10), dp(4), dp(10));
        box.setBackground(CanvasParts.round(lastKnown ? CanvasLook.accentTint() : CanvasLook.surface2(), 0, dp(12)));
        if (lastKnown) {
            box.addView(PathViews.icon(a, R.drawable.glyphicons_194_circle_empty_check, CanvasLook.accent()),
                    new LinearLayout.LayoutParams(dp(20), dp(20)));
        }
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView title = PathViews.text(a, a.getString(lastKnown ? R.string.canvas_t20_you_know
                : R.string.canvas_t20_stays, PathSession.name(a, c)), 13, 600, CanvasLook.ink());
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        final int saved = PathData.minutes(c.chars, wpm);
        final String note = !lastKnown ? a.getString(R.string.canvas_t20_stays_note)
                : saved > 0 ? a.getString(R.string.canvas_t20_skipped_saves, PathText.duration(saved))
                : a.getString(R.string.canvas_t20_skipped_plain);
        texts.addView(PathViews.text(a, note, 12, 400, CanvasLook.ink2()));
        final LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textsParams.leftMargin = lastKnown ? dp(10) : 0;
        textsParams.rightMargin = dp(8);
        box.addView(texts, textsParams);
        if (lastKnown) {
            final TextView undo = PathViews.link(a, a.getString(R.string.canvas_t20_undo));
            undo.setOnClickListener(v -> undo());
            box.addView(undo);
        }
        final LinearLayout.LayoutParams p = PathViews.wide(16);
        p.leftMargin = dp(16);
        p.rightMargin = dp(16);
        content.addView(box, p);
    }

    /** QUESTION 2 OF 4 · CHECKS I.7, the scenario and its four options. */
    private void renderQuestion() {
        final PathData.Chapter c = data.chapter(queue.get(index));
        if (c == null || c.question == null) {
            // the path changed under the check: nothing more to ask
            index = queue.size();
            renderFinished();
            return;
        }
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(20), dp(20), 0);
        final String number = a.getString(R.string.canvas_t20_question_of, index + 1, queue.size())
                .toUpperCase(Locale.getDefault());
        // the code is the book's own numbering: shown as it is
        head.addView(PathViews.mono(a, c.code.isEmpty() ? number
                : number + " · " + a.getString(R.string.canvas_t20_checks).toUpperCase(Locale.getDefault()) + " " + c.code,
                CanvasLook.accent()));
        final TextView q = PathViews.text(a, c.question.text, 18, 600, CanvasLook.ink());
        q.setLineSpacing(dp(3), 1f);
        head.addView(q, PathViews.wide(8));
        content.addView(head);

        final LinearLayout options = new LinearLayout(a);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(16), dp(16), dp(16), 0);
        for (int i = 0; i < c.question.options.size(); i++) {
            final int option = i;
            final boolean on = i == picked;
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setMinimumHeight(dp(56));
            row.setBackground(CanvasParts.ripple(PathViews.outline(on ? CanvasLook.accentTint() : CanvasLook.bg(),
                    on ? CanvasLook.accent() : CanvasLook.line(), 2, dp(14))));
            final TextView letter = PathViews.text(a, String.valueOf((char) ('A' + i)), 13, 700,
                    on ? CanvasLook.onAccent() : CanvasLook.ink2());
            letter.setGravity(Gravity.CENTER);
            letter.setBackground(CanvasParts.round(on ? CanvasLook.accent() : CanvasLook.surface2(), 0, dp(8)));
            row.addView(letter, new LinearLayout.LayoutParams(dp(28), dp(28)));
            final TextView text = PathViews.text(a, c.question.options.get(i), CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
            final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textParams.leftMargin = dp(12);
            row.addView(text, textParams);
            row.setSelected(on);
            row.setContentDescription((char) ('A' + i) + ". " + c.question.options.get(i));
            row.setOnClickListener(v -> {
                picked = option;
                render();
            });
            options.addView(row, PathViews.wide(i == 0 ? 0 : 8));
        }
        content.addView(options);

        final TextView unsure = PathViews.text(a, a.getString(R.string.canvas_t20_not_sure), 14, 600, CanvasLook.ink2());
        unsure.setGravity(Gravity.CENTER);
        unsure.setPadding(dp(12), 0, dp(12), 0);
        unsure.setMinHeight(CanvasLook.target());
        unsure.setBackground(CanvasParts.ripple(null));
        unsure.setOnClickListener(v -> answer(PathData.NOT_SURE));
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(8);
        content.addView(unsure, p);
    }

    private void renderFinished() {
        handler.removeCallbacks(tick);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), 0);
        box.addView(PathViews.text(a, a.getString(timeUp ? R.string.canvas_t20_time_up : R.string.canvas_t20_check_done),
                18, 600, CanvasLook.ink()));
        final int off = data.known().size();
        final String note = off == 0 ? a.getString(R.string.canvas_t20_check_none)
                : off == 1 ? a.getString(R.string.canvas_t20_check_off_one)
                : a.getString(R.string.canvas_t20_check_off, off);
        final String rest = timeUp && index < queue.size() ? " " + a.getString(R.string.canvas_t20_time_up_note) : "";
        box.addView(PathViews.text(a, note + rest, 14, 400, CanvasLook.ink2()), PathViews.wide(8));
        content.addView(box);
    }

    /** "Your path now · 4 chapters · 1 h 37 min", and Check (or the way back). */
    private void renderBottom() {
        final LinearLayout now = new LinearLayout(a);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.addView(PathViews.text(a, a.getString(R.string.canvas_t20_path_now), 12, 400, CanvasLook.ink2()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final int count = data.active().size();
        final String chapters = count == 1 ? a.getString(R.string.canvas_t20_chapters_one)
                : a.getString(R.string.canvas_t20_chapters, count);
        final String time = PathText.duration(data.minutes(wpm));
        now.addView(PathViews.text(a, time.isEmpty() ? chapters : chapters + " · " + time, 12, 600, CanvasLook.ink()));
        bottom.addView(now);

        if (finished()) {
            final TextView back = PathViews.primary(a, a.getString(R.string.canvas_t20_back_to_path));
            back.setOnClickListener(v -> dialog.dismiss());
            bottom.addView(back, PathViews.wide(52, 10));
            return;
        }
        final TextView check = PathViews.primary(a, a.getString(R.string.canvas_t20_check));
        PathViews.enable(check, picked >= 0);
        check.setOnClickListener(v -> {
            if (picked >= 0) {
                answer(picked);
            }
        });
        bottom.addView(check, PathViews.wide(52, 10));
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
