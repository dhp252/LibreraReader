package com.foobnix.assist.path;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.assist.skeleton.SkeletonScreen;
import com.foobnix.assist.skeleton.SkeletonText;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Shortest path" (canvas artboard PathResolve): the reader types what they want to understand,
 * one AI request picks the few chapters to read, in order, each needed for the next, and the
 * screen shows them with a one-line why and the minutes each takes, against the time the whole
 * book takes. "Read in this order" goes to the first chapter not read yet; "2-minute check"
 * ({@link PathCheckDialog}) takes off what the reader knows already. A full-screen dialog over the
 * book, only while {@link Assist#PATH} is on.
 */
public final class PathScreen implements PathJob.Screen {

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    /** A goal to look up as soon as the screen is up (from the skeleton's block), or null. */
    private final String firstGoal;

    private Dialog dialog;
    private EditText goalField;
    private LinearLayout body;
    private LinearLayout bottom;
    private TextView find;

    private boolean loaded;
    /** The book's path; empty when it has none. */
    private PathData data = new PathData();
    private Map<String, int[]> ranges;
    private int pageCount;
    private boolean readingRanges;
    /** A chapter to go to once the book's contents are read. */
    private String pendingJump;
    private int wpm;
    private PathJob job;
    private String status = "";
    private String error;
    private boolean needSkeleton;
    private boolean showKnown;

    private PathScreen(Activity a, DocumentController dc, String firstGoal) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.firstGoal = firstGoal == null ? null : firstGoal.trim();
    }

    /** Opens the path of the book the reader shows (nothing while the feature is off). */
    public static void show(Activity a, DocumentController dc) {
        show(a, dc, null);
    }

    /** The same, looking up the path to {@code goal} at once when it is not the stored one. */
    public static void show(Activity a, DocumentController dc, String goal) {
        if (!Assist.PATH.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new PathScreen(a, dc, goal).open();
    }

    // ------------------------------------------------------------------ frame

    private void open() {
        dialog = PathViews.fullScreen(a);
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CanvasLook.bg());
        root.addView(PathViews.topBar(a, R.drawable.glyphicons_223_chevron_left, R.string.canvas_t20_back,
                () -> dialog.dismiss(), a.getString(R.string.canvas_t20_shortest_path), target.title));

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // I want to understand [ the invisible hand ]
        final TextView label = PathViews.text(a, a.getString(R.string.canvas_t20_i_want), 13, 600, CanvasLook.ink());
        page.addView(label);
        goalField = PathViews.field(a, a.getString(R.string.canvas_t20_goal_hint), true);
        goalField.setBackground(PathViews.outline(CanvasLook.bg(), CanvasLook.accent(), 2, dp(12)));
        final Drawable search = a.getDrawable(R.drawable.glyphicons_28_search);
        if (search != null) {
            final Drawable icon = search.mutate();
            icon.setTint(CanvasLook.muted());
            icon.setBounds(0, 0, dp(18), dp(18));
            goalField.setCompoundDrawablesRelative(icon, null, null, null);
            goalField.setCompoundDrawablePadding(dp(10));
        }
        goalField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        goalField.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                resolve();
                return true;
            }
            return false;
        });
        goalField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showFind();
            }
        });
        page.addView(goalField, PathViews.wide(6));

        find = PathViews.primary(a, a.getString(R.string.canvas_t20_find));
        find.setOnClickListener(v -> resolve());
        page.addView(find, PathViews.wide(48, 10));

        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        page.addView(body, PathViews.wide());
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.addView(bottom);

        dialog.setOnDismissListener(d -> {
            if (job != null) {
                job.cancel();
            }
        });
        PathViews.show(dialog, root);
        render();
        load();
    }

    private boolean alive() {
        return dialog != null && dialog.isShowing() && !Apps.isDestroyedActivity(a);
    }

    // ------------------------------------------------------------------ data

    private void load() {
        new Thread(() -> {
            final PathData stored = PathStore.load(target.name);
            final int speed = ReadingSpeed.wpm(target.name);
            a.runOnUiThread(() -> {
                if (!alive()) {
                    return;
                }
                loaded = true;
                data = stored;
                wpm = speed;
                final boolean lookUp = !TextUtils.isEmpty(firstGoal) && !firstGoal.equalsIgnoreCase(stored.goal);
                goalField.setText(lookUp ? firstGoal : stored.goal);
                goalField.setSelection(goalField.getText().length());
                if (lookUp) {
                    resolve();
                } else if (!stored.isEmpty()) {
                    readRanges();
                }
                render();
            });
        }, "@T Path").start();
    }

    /** Where the path's chapters are in the reader's layout: for "reading", and to go to them. */
    private void readRanges() {
        if (readingRanges) {
            return;
        }
        readingRanges = true;
        new Thread(() -> {
            final PathSession.Ranges found = PathSession.read(target);
            a.runOnUiThread(() -> {
                readingRanges = false;
                if (!alive()) {
                    return;
                }
                if (found != null) {
                    ranges = found.byKey;
                    pageCount = found.pageCount;
                    PathSession.changed(dc, data, ranges, pageCount);
                }
                final String jump = pendingJump;
                pendingJump = null;
                if (jump != null) {
                    if (found == null || !goTo(jump)) {
                        error = a.getString(R.string.canvas_t20_no_place);
                    }
                }
                render();
            });
        }, "@T Path").start();
    }

    private String typed() {
        return goalField.getText().toString().trim().replaceAll("\\s+", " ");
    }

    private void resolve() {
        final String goal = typed();
        if (goal.isEmpty() || job != null || !loaded) {
            return;
        }
        PathViews.hideKeyboard(goalField);
        error = null;
        needSkeleton = false;
        status = "";
        job = PathJob.resolve(a, target, goal, this);
        job.start();
        render();
    }

    // ------------------------------------------------------------------ the job

    @Override
    public void onStatus(String text) {
        if (alive()) {
            status = text;
            render();
        }
    }

    @Override
    public void onPath(PathData made, Map<String, int[]> madeRanges, int pages) {
        job = null;
        // the reading session follows the new path, also when this screen is gone
        PathSession.changed(dc, made, madeRanges, pages);
        if (!alive()) {
            return;
        }
        data = made;
        ranges = madeRanges;
        pageCount = pages;
        showKnown = false;
        render();
    }

    @Override
    public void onQuestions(PathData withQuestions) {
        // the check's own screen asks for them
    }

    @Override
    public void onNeedSkeleton() {
        job = null;
        if (alive()) {
            needSkeleton = true;
            render();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (alive()) {
            render();
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

    // ------------------------------------------------------------------ drawing

    /** "Find the shortest path" shows while what is typed is not the path on screen. */
    private void showFind() {
        if (find == null) {
            return;
        }
        final String goal = typed();
        final boolean other = loaded && job == null && !goal.isEmpty() && (data.isEmpty() || !goal.equals(data.goal));
        find.setVisibility(other ? View.VISIBLE : View.GONE);
    }

    private void render() {
        if (dialog == null) {
            return;
        }
        showFind();
        body.removeAllViews();
        bottom.removeAllViews();
        bottom.setVisibility(View.GONE);
        if (!loaded) {
            body.addView(PathViews.muted(a, a.getString(R.string.canvas_t20_loading)), PathViews.wide(14));
            return;
        }
        if (job != null) {
            body.addView(PathViews.busyRow(a, TextUtils.isEmpty(status) ? a.getString(R.string.canvas_t20_finding) : status,
                    () -> {
                        if (job != null) {
                            job.cancel();
                            job = null;
                        }
                        status = "";
                        render();
                    }), PathViews.wide(12));
        }
        if (error != null) {
            final TextView e = PathViews.text(a, error, 13, 400, CanvasLook.sensor());
            body.addView(e, PathViews.wide(12));
        }
        if (needSkeleton) {
            renderNeedSkeleton();
        }
        if (data.isEmpty()) {
            if (job == null && !needSkeleton) {
                final TextView what = PathViews.text(a, a.getString(R.string.canvas_t20_empty), CanvasLook.TEXT_BODY, 500,
                        CanvasLook.ink());
                body.addView(what, PathViews.wide(16));
                body.addView(PathViews.muted(a, a.getString(R.string.canvas_t20_empty_note)), PathViews.wide(6));
            }
            return;
        }
        renderTotals();
        renderChapters();
        renderSkipped();
        renderBottom();
    }

    /** The book has no chapter lines yet: say so and offer the skeleton's button. */
    private void renderNeedSkeleton() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), dp(16)));
        box.addView(PathViews.text(a, a.getString(R.string.canvas_t20_need_skeleton), CanvasLook.TEXT_BODY, 500,
                CanvasLook.ink()));
        final boolean skeletonOn = Assist.SKELETON.isOn();
        box.addView(PathViews.muted(a, a.getString(skeletonOn ? R.string.canvas_t20_need_skeleton_note
                : R.string.canvas_t20_need_skeleton_off)), PathViews.wide(6));
        if (skeletonOn) {
            final TextView make = PathViews.primary(a, a.getString(R.string.canvas_t20_make_skeleton));
            make.setOnClickListener(v -> {
                dialog.dismiss();
                SkeletonScreen.show(a, dc);
            });
            box.addView(make, PathViews.wide(48, 12));
        }
        body.addView(box, PathViews.wide(14));
    }

    /** "5 of 32 chapters · 1 h 45 min / instead of about 27½ h for the whole book". */
    private void renderTotals() {
        final List<PathData.Chapter> on = data.active();
        final int minutes = data.minutes(wpm);
        final int bookMinutes = data.bookMinutes(wpm);

        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(CanvasParts.round(CanvasLook.surface(), 0, dp(16)));

        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        final TextView count = PathViews.text(a, a.getString(data.total == 1 ? R.string.canvas_t20_of_chapter
                : R.string.canvas_t20_of_chapters, on.size(), data.total), 20, 700, CanvasLook.ink());
        top.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (minutes > 0) {
            top.addView(PathViews.text(a, PathText.duration(minutes), 13, 400, CanvasLook.ink2()));
        }
        box.addView(top);

        // the path's share of the book: of its time, else of its chapters
        final float share = minutes > 0 && bookMinutes > 0 ? data.share(wpm)
                : data.total > 0 ? on.size() / (float) data.total : 0;
        final LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(CanvasParts.round(CanvasLook.line(), 0, dp(4)));
        track.setWeightSum(1f);
        final View fill = new View(a);
        fill.setBackground(CanvasParts.round(CanvasLook.accent(), 0, dp(4)));
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(0.02f, Math.min(1f, share))));
        box.addView(track, PathViews.wide(8, 8));

        if (minutes > 0 && bookMinutes > 0) {
            box.addView(PathViews.muted(a, a.getString(R.string.canvas_t20_instead_of, SkeletonText.duration(bookMinutes))),
                    PathViews.wide(8));
        }
        body.addView(box, PathViews.wide(16));
    }

    /** READ IN THIS ORDER · EACH ONE IS NEEDED FOR THE NEXT, and the chapters on their line. */
    private void renderChapters() {
        final List<PathData.Chapter> on = data.active();
        final TextView label = PathViews.mono(a, a.getString(on.size() > 1 ? R.string.canvas_t20_read_in_order
                : R.string.canvas_t20_read_this).toUpperCase(Locale.getDefault()), CanvasLook.muted());
        final LinearLayout.LayoutParams labelParams = PathViews.wide(14);
        labelParams.bottomMargin = dp(6);
        body.addView(label, labelParams);

        final PathData.Chapter reading = ranges == null ? null : data.at(ranges, PathSession.shown(dc)[0]);
        for (int i = 0; i < on.size(); i++) {
            final PathData.Chapter c = on.get(i);
            final boolean goal = data.isGoal(c);
            final boolean here = c == reading;

            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setBackground(CanvasParts.ripple(null));

            final int kind = here || c.done ? PathViews.Dot.FILLED : goal ? PathViews.Dot.GOAL : PathViews.Dot.RING;
            row.addView(new PathViews.Dot(a, kind, i == 0, i == on.size() - 1,
                            here ? CanvasLook.accent() : CanvasLook.muted()),
                    new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.MATCH_PARENT));

            final TextView code = PathViews.mono(a, c.code.isEmpty() ? String.valueOf(i + 1) : c.code, CanvasLook.muted());
            code.setTypeface(CanvasLook.mono(a, 400));
            code.setMinWidth(dp(34));
            code.setMaxWidth(dp(60));
            final LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            codeParams.leftMargin = dp(12);
            row.addView(code, codeParams);

            final LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(0, dp(8), 0, dp(8));
            final String title = !c.title.isEmpty() ? c.title : rangeName(c);
            final TextView t = PathViews.text(a, title, 14, goal ? 700 : 600, CanvasLook.ink());
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(t);
            final String why = !c.why.isEmpty() ? c.why : c.line;
            if (!why.isEmpty()) {
                final TextView w = PathViews.muted(a, why);
                w.setMaxLines(2);
                w.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(w);
            }
            final LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textsParams.leftMargin = dp(12);
            row.addView(texts, textsParams);

            final LinearLayout right = new LinearLayout(a);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            final String time = PathText.duration(PathData.minutes(c.chars, wpm));
            if (!time.isEmpty()) {
                right.addView(PathViews.text(a, time, 12, 400, CanvasLook.ink2()));
            }
            final int tag = here ? R.string.canvas_t20_reading : goal ? R.string.canvas_t20_goal
                    : c.done ? R.string.canvas_t20_done : 0;
            if (tag != 0) {
                right.addView(PathViews.text(a, a.getString(tag), 11, 600,
                        c.done && !here && !goal ? CanvasLook.muted() : CanvasLook.accent()));
            }
            final LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rightParams.leftMargin = dp(8);
            row.addView(right, rightParams);

            row.setOnClickListener(v -> jump(c));
            body.addView(row, PathViews.wide());
        }
    }

    /** "Pages 21–40" for a chapter the book gives no title (a book without a table of contents). */
    private String rangeName(PathData.Chapter c) {
        final int[] r = ranges == null ? null : ranges.get(c.key);
        return r == null ? (c.line.isEmpty() ? a.getString(R.string.canvas_t20_chapter) : c.line)
                : a.getString(R.string.ai_pages, r[0] + 1, r[1]);
    }

    /** "27 chapters skipped · wages, rent, banking…", and what the check took off, to put back. */
    private void renderSkipped() {
        final int skipped = data.skippedCount();
        final List<PathData.Chapter> known = data.known();
        if (skipped <= 0) {
            return;
        }
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setMinimumHeight(dp(48));
        box.setBackground(PathViews.dashed(CanvasLook.surface(), CanvasLook.lineStrong(), dp(12)));
        if (!known.isEmpty()) {
            box.addView(PathViews.icon(a, showKnown ? R.drawable.glyphicons_221_chevron_down
                    : R.drawable.glyphicons_224_chevron_right, CanvasLook.ink2()), new LinearLayout.LayoutParams(dp(16), dp(16)));
        }
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(PathViews.text(a, skipped == 1 ? a.getString(R.string.canvas_t20_skipped_one) : a.getString(R.string.canvas_t20_skipped, skipped),
                13, 600, CanvasLook.ink()));
        if (!data.skipped.isEmpty()) {
            final TextView words = PathViews.text(a, data.skipped, 12, 400, CanvasLook.ink2());
            words.setMaxLines(2);
            words.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(words);
        }
        final LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textsParams.leftMargin = known.isEmpty() ? 0 : dp(10);
        box.addView(texts, textsParams);
        if (!known.isEmpty()) {
            box.setOnClickListener(v -> {
                showKnown = !showKnown;
                render();
            });
        }
        body.addView(box, PathViews.wide(8));

        if (!showKnown) {
            return;
        }
        // taken off by the 2-minute check
        for (PathData.Chapter c : known) {
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, 0, 0);
            final LinearLayout names = new LinearLayout(a);
            names.setOrientation(LinearLayout.VERTICAL);
            final TextView t = PathViews.text(a, PathSession.name(a, c), 13, 600, CanvasLook.ink());
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(t);
            names.addView(PathViews.muted(a, a.getString(R.string.canvas_t20_you_know_it)));
            row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final TextView back = PathViews.link(a, a.getString(R.string.canvas_t20_put_back));
            back.setOnClickListener(v -> {
                final String key = c.key;
                if (data.putBack(key)) {
                    PathStore.later(target.name, data.goal, d -> d.putBack(key));
                    PathSession.changed(dc, data, ranges, pageCount);
                    render();
                }
            });
            row.addView(back);
            body.addView(row, PathViews.wide(4));
        }
    }

    private void renderBottom() {
        bottom.setVisibility(View.VISIBLE);
        if (data.canCheck()) {
            // Know some already? 2-minute check
            final LinearLayout check = new LinearLayout(a);
            check.setOrientation(LinearLayout.HORIZONTAL);
            check.setGravity(Gravity.CENTER);
            check.setBackground(CanvasParts.secondaryButton());
            check.addView(PathViews.icon(a, R.drawable.glyphicons_55_clock, CanvasLook.ink()),
                    new LinearLayout.LayoutParams(dp(16), dp(16)));
            final TextView text = PathViews.text(a, a.getString(R.string.canvas_t20_check_button), 14, 600, CanvasLook.ink());
            text.setPadding(dp(8), 0, 0, 0);
            check.addView(text);
            check.setOnClickListener(v -> PathCheckDialog.show(a, target, data, wpm, () -> {
                PathSession.changed(dc, data, ranges, pageCount);
                if (alive()) {
                    render();
                }
            }));
            final LinearLayout.LayoutParams p = PathViews.wide(48, 0);
            p.bottomMargin = dp(8);
            bottom.addView(check, p);
        }
        final boolean wait = pendingJump != null;
        final TextView read = PathViews.primary(a, wait ? a.getString(R.string.canvas_t20_loading)
                : a.getString(data.allDone() ? R.string.canvas_t20_read_goal_again : R.string.canvas_t20_read_in_this_order)
                + "  →");
        PathViews.enable(read, !wait && job == null);
        read.setOnClickListener(v -> jump(data.firstUnread()));
        bottom.addView(read, PathViews.wide(52, 0));
    }

    // ------------------------------------------------------------------ going there

    /** Goes to a chapter of the path, once the book's contents are read in the reader's layout. */
    private void jump(PathData.Chapter c) {
        if (c == null || job != null) {
            return;
        }
        if (ranges == null) {
            pendingJump = c.key;
            readRanges();
            render();
            return;
        }
        if (!goTo(c.key)) {
            error = a.getString(R.string.canvas_t20_no_place);
            render();
        }
    }

    private boolean goTo(String key) {
        final int[] r = ranges == null ? null : ranges.get(key);
        if (r == null) {
            return false;
        }
        PathSession.goTo(dc, r[0]);
        dialog.dismiss();
        return true;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
