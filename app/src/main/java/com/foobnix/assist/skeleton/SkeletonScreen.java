package com.foobnix.assist.skeleton;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiMenu;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;
import com.foobnix.stats.ReadingTracker;
import com.foobnix.study.StudyStore;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Before you start · See the whole argument first" (canvas artboards SkeletonLine, Skeleton,
 * SkeletonMap): a full-screen sheet over the book with the thesis in 1 line or 1 paragraph, and a
 * chapter map of the book's parts as cards (YOU'RE HERE, the conclusion's part to read first,
 * optional background dashed; a tap opens a part's chapters, a tap on a chapter goes there).
 * <p>
 * Its data is the AI skeleton ({@link SkeletonData#KEY}), else the book's study preview (read
 * only), else nothing yet: one tap makes it ({@link SkeletonJob}, a long AI task). Only shown while
 * {@link Assist#SKELETON} is on.
 */
public final class SkeletonScreen implements SkeletonJob.Screen, AiProgress.TaskScreen {

    static final int LINE = 0;
    static final int PARAGRAPH = 1;
    static final int MAP = 2;

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final String key;
    private final boolean dark;

    private Dialog dialog;
    private TextView byline;
    private LinearLayout segments;
    private LinearLayout panelBody;
    private LinearLayout below;

    private int mode = LINE;
    private boolean loaded;
    private SkeletonData data;
    private SkeletonModel model;
    /** The table of contents could not be read: no chapter map. */
    private boolean noParts;
    private String status = "";
    private String error;
    /** Cancel was tapped: a task only queued never says it ended. */
    private boolean stopped;
    private final Set<String> opened = new HashSet<>();

    private SkeletonScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.key = AiTask.key(target.name, AiTask.SKELETON);
        this.dark = CanvasLook.isNight();
    }

    /** Opens the skeleton of the book the reader shows (nothing while the feature is off). */
    public static void show(Activity a, DocumentController dc) {
        if (!Assist.SKELETON.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new SkeletonScreen(a, dc).open();
    }

    // ------------------------------------------------------------------ frame

    private void open() {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CanvasLook.bg());
        root.addView(topBar());

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, dp(16));
        scroll.addView(page, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // BEFORE YOU START / See the whole argument first
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(4), dp(20), 0);
        final TextView label = new TextView(a);
        CanvasParts.label(label, true);
        label.setText(R.string.canvas_t13_before_you_start);
        head.addView(label);
        final TextView title = new TextView(a);
        CanvasParts.text(title, 22, 600);
        title.setText(R.string.canvas_t13_title);
        title.setPadding(0, dp(4), 0, 0);
        head.addView(title);
        page.addView(head);

        // the panel: segments, then the thesis or the conclusion's note
        final LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), dp(16)));
        segments = segments();
        panel.addView(segments);
        panelBody = new LinearLayout(a);
        panelBody.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(14);
        panel.addView(panelBody, bodyParams);
        final LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.setMargins(dp(16), dp(14), dp(16), 0);
        page.addView(panel, panelParams);

        // the chapter map's cards
        below = new LinearLayout(a);
        below.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams belowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        belowParams.setMargins(dp(16), dp(16), dp(16), 0);
        page.addView(below, belowParams);

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        final View pre = preQuestions();
        if (pre != null) {
            root.addView(pre);
        }

        dialog.setContentView(root);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, root, dark);
        dialog.setOnDismissListener(d -> AiProgress.detach(key, this));
        dialog.show();

        if (AiProgress.isBusy(key)) {
            AiProgress.attach(key, a, this);
        }
        render();
        load();
    }

    private View topBar() {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setMinimumHeight(dp(CanvasLook.BAR_DP));

        final ImageView back = icon(R.drawable.glyphicons_223_chevron_left, R.string.canvas_t13_back);
        back.setOnClickListener(v -> dialog.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout middle = new LinearLayout(a);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView name = new TextView(a);
        CanvasParts.text(name, CanvasLook.TEXT_TITLE, 600);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(target.title);
        middle.addView(name);
        byline = new TextView(a);
        CanvasParts.secondary(byline);
        byline.setGravity(Gravity.CENTER);
        byline.setSingleLine(true);
        byline.setEllipsize(TextUtils.TruncateAt.END);
        byline.setVisibility(View.GONE);
        middle.addView(byline);
        bar.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final ImageView more = icon(R.drawable.glyphicons_498_more_vertical, R.string.canvas_t13_more);
        more.setOnClickListener(this::more);
        bar.addView(more, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    private void more(View anchor) {
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        menu.getMenu(R.drawable.ic_ai_sparkle, R.string.canvas_t13_make_ai, () -> {
            if (data != null && !data.isEmpty() && !data.fromStudy) {
                // it costs a new request
                AiMenu.confirmRedo(a, R.string.canvas_t13_redo_confirm, this::make);
            } else {
                make();
            }
        });
        menu.show();
    }

    /** 1 line / 1 paragraph / Chapter map. */
    private LinearLayout segments() {
        final LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setPadding(dp(3), dp(3), dp(3), dp(3));
        track.setBackground(CanvasParts.segmentTrack());
        track.setContentDescription(a.getString(R.string.canvas_t13_length));
        final int[] names = {R.string.canvas_t13_one_line, R.string.canvas_t13_one_paragraph, R.string.canvas_t13_chapter_map};
        for (int i = 0; i < names.length; i++) {
            final int m = i;
            final TextView s = new TextView(a);
            CanvasParts.text(s, 13, 600);
            s.setGravity(Gravity.CENTER);
            s.setSingleLine(true);
            s.setText(names[i]);
            s.setOnClickListener(v -> {
                mode = m;
                render();
            });
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, CanvasLook.target(), 1);
            if (i > 0) {
                p.leftMargin = dp(2);
            }
            track.addView(s, p);
        }
        return track;
    }

    /** "First, three quick guesses…" when pre-questions are on (they come after the sheet closes). */
    private View preQuestions() {
        if (!Assist.PRE_QUESTIONS.isOn()) {
            return null;
        }
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(16));
        box.setBackgroundColor(CanvasLook.bg());
        final TextView text = new TextView(a);
        CanvasParts.text(text, 13, 400);
        text.setTextColor(CanvasLook.ink2());
        text.setText(R.string.canvas_t13_pre_questions_text);
        box.addView(text);
        final TextView button = primary(a.getString(R.string.canvas_t13_pre_questions) + "  →");
        button.setOnClickListener(v -> dialog.dismiss());
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.topMargin = dp(10);
        box.addView(button, p);
        return box;
    }

    // ------------------------------------------------------------------ data

    private void load() {
        final int page = bookPage();
        new Thread(() -> {
            SkeletonData d = SkeletonData.fromJson(AssistStore.get().item(target.name, SkeletonData.KEY));
            if (d.isEmpty()) {
                // the study preview's thesis and lines; its file is only read
                d = SkeletonData.fromStudy(StudyStore.get().load(target.name));
            }
            List<SkeletonParts.Part> parts = null;
            if (!d.isEmpty()) {
                try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                    if (text != null) {
                        parts = SkeletonParts.group(AiJobs.toc(target, text), text.pageCount());
                    }
                } catch (Exception e) {
                    LOG.e(e);
                }
            }
            final String by = authorAndYear();
            final SkeletonData found = d;
            final SkeletonModel m = d.isEmpty() ? null : SkeletonModel.build(
                    parts == null ? Collections.<SkeletonParts.Part>emptyList() : parts, d, page, ReadingSpeed.wpm(target.name));
            final boolean none = parts == null || parts.isEmpty();
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a) || !dialog.isShowing()) {
                    return;
                }
                loaded = true;
                data = found;
                model = m;
                noParts = none;
                if (!TextUtils.isEmpty(by)) {
                    byline.setText(by);
                    byline.setVisibility(View.VISIBLE);
                }
                render();
            });
        }, "@T Skeleton").start();
    }

    /** "Adam Smith · 1776" from the library's record of the book. */
    private String authorAndYear() {
        try {
            final com.foobnix.dao2.FileMeta meta = com.foobnix.ui2.AppDB.get().load(target.path);
            if (meta == null) {
                return "";
            }
            final String author = meta.getAuthor() == null ? "" : meta.getAuthor().trim();
            final Integer year = meta.getYear();
            if (year != null && year > 0) {
                return author.isEmpty() ? String.valueOf(year) : author + " · " + year;
            }
            return author;
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    /** The book page (0-based) the reader shows, in the layout the AI reads the book in. */
    private int bookPage() {
        try {
            return ReadingTracker.realPages(dc, dc.getCurentPageFirst1())[0] - 1;
        } catch (Exception e) {
            return target.page;
        }
    }

    private void make() {
        if (!AiSettings.load(a).isReady()) {
            AiSettingsDialog.show(a, null);
            return;
        }
        if (!AiProgress.isRunning(key)) {
            // queued again when only waiting: the newest request replaces it (AiWork.enqueue)
            AiWork.enqueue(a, AiTask.skeleton(AiJobs.Target.of(dc)));
        }
        error = null;
        status = "";
        stopped = false;
        AiProgress.attach(key, a, this);
        render();
    }

    private void cancel() {
        AiWork.cancel(a, key);
        stopped = true;
        status = "";
        render();
    }

    private boolean busy() {
        return !stopped && AiProgress.isBusy(key);
    }

    // ------------------------------------------------------------------ task

    @Override
    public void onStatus(String text) {
        status = text;
        render();
    }

    @Override
    public void onWaiting(String text) {
        status = text;
        render();
    }

    @Override
    public void onEnded(int state, String message) {
        if (Apps.isDestroyedActivity(a) || dialog == null || !dialog.isShowing()) {
            return;
        }
        status = "";
        if (state == AiProgress.DONE) {
            error = null;
            opened.clear();
            load();
        } else if (state == AiProgress.FAILED) {
            error = TextUtils.isEmpty(message) ? a.getString(R.string.canvas_t13_unreadable) : message;
        }
        render();
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        if (dialog == null) {
            return;
        }
        for (int i = 0; i < segments.getChildCount(); i++) {
            final TextView s = (TextView) segments.getChildAt(i);
            final boolean on = i == mode;
            s.setBackground(on ? CanvasParts.segmentOn() : null);
            s.setElevation(on ? dp(1) : 0);
            s.setTextColor(on ? CanvasLook.ink() : CanvasLook.ink2());
            s.setSelected(on);
        }
        panelBody.removeAllViews();
        below.removeAllViews();
        final boolean busy = busy();
        if (!loaded) {
            panelBody.addView(muted(a.getString(R.string.canvas_t13_loading)));
            return;
        }
        if (model == null) {
            renderEmpty(busy);
            return;
        }
        if (mode == LINE) {
            final TextView line = serif(model.line.isEmpty() ? model.paragraph : model.line, 22, 32);
            panelBody.addView(line);
            if (!model.totals.isEmpty()) {
                final TextView totals = muted(TextUtils.join("   ·   ", model.totals));
                totals.setPadding(dp(2), dp(14), dp(2), 0);
                panelBody.addView(totals);
            }
        } else if (mode == PARAGRAPH) {
            panelBody.addView(serif(model.paragraph.isEmpty() ? model.line : model.paragraph, 15, 22));
        } else {
            renderMap();
        }
        if (data != null && data.fromStudy && mode != MAP) {
            final TextView note = muted(a.getString(R.string.canvas_t13_from_study));
            note.setPadding(0, dp(12), 0, 0);
            panelBody.addView(note);
        }
        if (busy || error != null) {
            panelBody.addView(progressRow(busy));
        }
    }

    /** No skeleton yet: what it is, and one tap to make it (or its progress). */
    private void renderEmpty(boolean busy) {
        final TextView what = new TextView(a);
        CanvasParts.text(what, CanvasLook.TEXT_BODY, 500);
        what.setText(R.string.canvas_t13_empty);
        panelBody.addView(what);
        final TextView note = muted(a.getString(R.string.canvas_t13_empty_note));
        note.setPadding(0, dp(6), 0, 0);
        panelBody.addView(note);
        if (busy || error != null) {
            panelBody.addView(progressRow(busy));
        }
        if (!busy) {
            final TextView make = primary(a.getString(error != null ? R.string.study_retry : R.string.canvas_t13_make));
            make.setOnClickListener(v -> make());
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            p.topMargin = dp(14);
            panelBody.addView(make, p);
        }
    }

    /** The task's status with Cancel, or why it failed. */
    private View progressRow(boolean busy) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        if (busy) {
            final ProgressBar spin = new ProgressBar(a);
            spin.setIndeterminateTintList(ColorStateList.valueOf(CanvasLook.accent()));
            row.addView(spin, new LinearLayout.LayoutParams(dp(20), dp(20)));
        }
        final String text = busy ? (!TextUtils.isEmpty(status) ? status
                : !AiProgress.status(key).isEmpty() ? AiProgress.status(key) : a.getString(R.string.ai_task_queued))
                : error;
        final TextView t = muted(text);
        t.setPadding(busy ? dp(10) : 0, 0, dp(8), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (busy) {
            final TextView stop = new TextView(a);
            CanvasParts.text(stop, 13, 600);
            stop.setTextColor(CanvasLook.accent());
            stop.setGravity(Gravity.CENTER);
            stop.setPadding(dp(10), 0, dp(10), 0);
            stop.setText(R.string.cancel);
            stop.setMinHeight(CanvasLook.target());
            stop.setBackground(CanvasParts.ripple(null));
            stop.setOnClickListener(v -> cancel());
            row.addView(stop);
        }
        return row;
    }

    private void renderMap() {
        if (noParts || model.parts.isEmpty()) {
            panelBody.addView(muted(a.getString(R.string.canvas_t13_no_map)));
            return;
        }
        // where the conclusion is, and a way to read it first
        if (model.conclusion >= 0) {
            final SkeletonModel.PartView c = model.parts.get(model.conclusion);
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            final TextView note = new TextView(a);
            CanvasParts.text(note, 13, 400);
            note.setText(!model.note.isEmpty() ? model.note
                    : a.getString(R.string.canvas_t13_conclusion_in, name(c)));
            row.addView(note, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final TextView first = new TextView(a);
            CanvasParts.text(first, 13, 600);
            first.setTextColor(CanvasLook.accent());
            first.setGravity(Gravity.CENTER);
            first.setPadding(dp(12), 0, dp(12), 0);
            first.setText("⇅  " + a.getString(R.string.canvas_t13_read_it_first));
            first.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.accentLine(), CanvasLook.radius())));
            first.setOnClickListener(v -> goTo(c.from));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.target());
            p.leftMargin = dp(10);
            row.addView(first, p);
            panelBody.addView(row);
        } else {
            panelBody.addView(muted(TextUtils.join("   ·   ", model.totals)));
        }

        for (int i = 0; i < model.parts.size(); i++) {
            final View card = card(model.parts.get(i));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                p.topMargin = dp(10);
            }
            below.addView(card, p);
        }
        // the legend
        final TextView legend = muted(a.getString(R.string.canvas_t13_legend_optional) + "   ·   "
                + a.getString(model.grouped ? R.string.canvas_t13_tap_part : R.string.canvas_t13_tap_chapter));
        legend.setTextColor(CanvasLook.ink2());
        legend.setPadding(dp(4), dp(14), dp(4), 0);
        below.addView(legend);
    }

    /** A part of the chapter map: "BOOK I · 11 CH · YOU'RE HERE", its line; open, its chapters. */
    private View card(SkeletonModel.PartView part) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setMinimumHeight(CanvasLook.target());
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(CanvasLook.radius());
        if (part.conclusion) {
            shape.setColor(CanvasLook.accentTint());
            shape.setStroke(Math.max(2, Math.round(dp(3) / 2f)), CanvasLook.accent());
        } else if (part.optional) {
            shape.setColor(CanvasLook.surface());
            shape.setStroke(Math.max(1, dp(1)), CanvasLook.lineStrong(), dp(5), dp(4));
        } else {
            shape.setColor(CanvasLook.bg());
            shape.setStroke(Math.max(1, dp(1)), CanvasLook.lineStrong());
        }
        card.setBackground(CanvasParts.ripple(shape));

        final SpannableStringBuilder label = new SpannableStringBuilder();
        label.append(upper(TextUtils.isEmpty(part.label) ? name(part) : part.label));
        if (part.chapterCount > 0) {
            label.append(" · ").append(upper(a.getString(R.string.canvas_t13_chapters_short, part.chapterCount)));
        }
        if (part.conclusion) {
            label.append(" · ").append(upper(a.getString(R.string.canvas_t13_conclusion)));
            label.setSpan(new ForegroundColorSpan(CanvasLook.accent()), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (part.here) {
            label.append(" · ");
            final int from = label.length();
            label.append(upper(a.getString(R.string.canvas_t13_here)));
            label.setSpan(new ForegroundColorSpan(CanvasLook.accent()), from, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final TextView l = new TextView(a);
        l.setTypeface(CanvasLook.mono(a, part.conclusion || part.here ? 500 : 400));
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        l.setLetterSpacing(0.05f);
        l.setTextColor(CanvasLook.muted());
        l.setMaxLines(2);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setText(label);
        card.addView(l);

        final String lineText = !part.line.isEmpty() ? part.line
                : !part.label.equals(part.title) ? part.title : "";
        if (!lineText.isEmpty()) {
            final TextView line = new TextView(a);
            CanvasParts.text(line, part.optional ? 12 : 13, 400);
            if (part.optional) {
                line.setTextColor(CanvasLook.ink2());
            }
            line.setText(lineText);
            line.setPadding(0, dp(2), 0, 0);
            card.addView(line);
        }
        if (part.optional) {
            final TextView optional = new TextView(a);
            CanvasParts.text(optional, 11, 400);
            optional.setTextColor(CanvasLook.muted());
            optional.setText(R.string.canvas_t13_optional);
            card.addView(optional);
        }

        final boolean group = part.chapterCount > 0;
        if (group && opened.contains(part.key)) {
            for (SkeletonModel.ChapterView c : part.chapters) {
                card.addView(chapterRow(c));
            }
        }
        card.setOnClickListener(v -> {
            if (!group) {
                goTo(part.from);
            } else {
                if (!opened.remove(part.key)) {
                    opened.add(part.key);
                }
                render();
            }
        });
        return card;
    }

    private View chapterRow(SkeletonModel.ChapterView c) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setMinimumHeight(CanvasLook.target());
        row.setBackground(CanvasParts.ripple(null));
        final View rule = new View(a);
        rule.setBackgroundColor(CanvasLook.line());

        final TextView title = new TextView(a);
        CanvasParts.text(title, 13, 600);
        title.setText(!TextUtils.isEmpty(c.title) ? c.title : a.getString(R.string.ai_pages, c.from + 1, c.to));
        row.addView(title);
        if (!c.line.isEmpty()) {
            final TextView line = new TextView(a);
            CanvasParts.text(line, 12, 400);
            line.setTextColor(CanvasLook.ink2());
            line.setText(c.line);
            row.addView(line);
        }
        if (c.here) {
            final TextView here = new TextView(a);
            CanvasParts.label(here, true);
            here.setText(R.string.canvas_t13_reading_now);
            here.setPadding(0, dp(2), 0, 0);
            row.addView(here);
        }
        row.setOnClickListener(v -> goTo(c.from));

        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1)));
        ruleParams.topMargin = dp(6);
        box.addView(rule, ruleParams);
        box.addView(row);
        return box;
    }

    /** Goes to a page of the book (0-based, the AI's layout) and closes the sheet. */
    private void goTo(int page) {
        try {
            dc.onGoToPage(ReadingTracker.viewPage(dc, Math.max(0, page) + 1));
        } catch (Exception e) {
            LOG.e(e);
        }
        dialog.dismiss();
    }

    // ------------------------------------------------------------------ small views

    private String name(SkeletonModel.PartView p) {
        if (!TextUtils.isEmpty(p.label)) {
            return p.label;
        }
        return a.getString(R.string.ai_pages, p.from + 1, p.to);
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.getDefault());
    }

    private TextView serif(String text, float sp, int lineDp) {
        final TextView t = new TextView(a);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(CanvasLook.ink());
        t.setLineSpacing(Math.max(0, dp(lineDp) - t.getPaint().getFontMetricsInt(null)), 1f);
        t.setTextIsSelectable(true);
        t.setPadding(dp(2), dp(4), dp(2), 0);
        t.setText(text);
        return t;
    }

    private TextView muted(String text) {
        final TextView t = new TextView(a);
        CanvasParts.secondary(t);
        t.setText(text);
        return t;
    }

    private TextView primary(String text) {
        final TextView b = new TextView(a);
        CanvasParts.text(b, CanvasLook.TEXT_BODY, 600);
        b.setTextColor(CanvasLook.onAccent());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(CanvasParts.primaryButton());
        b.setText(text);
        return b;
    }

    private ImageView icon(int drawable, int description) {
        final ImageView v = new ImageView(a);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(CanvasLook.ink()));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setPadding(dp(11), dp(11), dp(11), dp(11));
        v.setContentDescription(a.getString(description));
        v.setBackground(CanvasParts.ripple(null));
        return v;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
