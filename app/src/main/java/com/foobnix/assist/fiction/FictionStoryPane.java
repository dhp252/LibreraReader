package com.foobnix.assist.fiction;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;
import java.util.Locale;

/**
 * The Timeline and Map tabs of the fiction companion (canvas artboards FictionTimeline and
 * FictionMap): the key moments so far with the time as the book gives it, the plan against what
 * really happened when the book states a plan, and the places so far on a map drawn on the
 * device.
 * <p>
 * Both come from one request on a tap ({@link FictionTimelineJob}), from the text up to the
 * reader's page only, and show under the chapter they were built for. A reader who has read on
 * is offered the update; one who went back sees only the moments and places of the chapters up
 * to theirs ({@link FictionStoryView}).
 */
final class FictionStoryPane implements FictionJob.Screen, AiProgress.TaskScreen {

    /** The screen the tabs live in. */
    interface Host {
        /** Draw the tab again. */
        void redraw();

        /** Read the stored data again, then draw. */
        void reload();

        /** Close the screen: the reader goes to a place in the book. */
        void close();
    }

    private final Activity a;
    private final DocumentController dc;
    private final String key;
    private final Host host;

    private FictionTimeline timeline = new FictionTimeline();
    private FictionChapters chapters;
    private FictionWhere where;
    private int readerEnd;
    private String status = "";
    private String error;
    private boolean stopped;
    /** The map's layers. */
    private boolean showRoute = true;
    private boolean showPlan = true;

    FictionStoryPane(Activity a, DocumentController dc, AiJobs.Target target, Host host) {
        this.a = a;
        this.dc = dc;
        this.key = FictionBook.timelineTask(target.name);
        this.host = host;
    }

    // ------------------------------------------------------------------ data and task

    void set(FictionTimeline timeline, FictionChapters chapters, int readerEnd) {
        this.timeline = timeline == null ? new FictionTimeline() : timeline;
        this.chapters = chapters;
        this.readerEnd = readerEnd;
        this.where = chapters == null ? null : FictionWhere.of(chapters, readerEnd, this.timeline.mark);
    }

    boolean hasData() {
        return !timeline.isEmpty();
    }

    /** Follows a task that is already under way. */
    void attach() {
        if (AiProgress.isBusy(key)) {
            AiProgress.attach(key, a, this);
        }
    }

    void detach() {
        AiProgress.detach(key, this);
    }

    /** "As of Ch. 14 · no spoilers": what the bar says under the tab's name; "" with nothing stored. */
    String subtitle() {
        if (timeline.isEmpty() || chapters == null || where == null) {
            return "";
        }
        final String built = chapters.label(Math.max(0, where.stored));
        return a.getString(where.state == FictionWhere.BEFORE ? R.string.canvas_t30_as_of_back : R.string.canvas_t30_as_of,
                built);
    }

    /** Builds the timeline and places, or brings them up to the reader's page; {@code again}: from the start. */
    void make(boolean again) {
        if (!Assist.FICTION.isOn()) {
            return;
        }
        if (!AiSettings.load(a).isReady()) {
            AiSettingsDialog.show(a, null);
            return;
        }
        if (!AiProgress.isRunning(key)) {
            AiWork.enqueue(a, AiTask.fictionTimeline(AiJobs.Target.of(dc), FictionBook.ticket(dc), again));
        }
        error = null;
        status = "";
        stopped = false;
        AiProgress.attach(key, a, this);
        host.redraw();
    }

    private void cancel() {
        AiWork.cancel(a, key);
        stopped = true;
        status = "";
        host.redraw();
    }

    private boolean busy() {
        return !stopped && AiProgress.isBusy(key);
    }

    @Override
    public void onStatus(String text) {
        status = text;
        host.redraw();
    }

    @Override
    public void onWaiting(String text) {
        status = text;
        host.redraw();
    }

    @Override
    public void onEnded(int state, String message) {
        if (Apps.isDestroyedActivity(a)) {
            return;
        }
        status = "";
        if (state == AiProgress.DONE) {
            error = null;
        } else if (state == AiProgress.FAILED) {
            error = TextUtils.isEmpty(message) ? a.getString(R.string.canvas_t29_unreadable) : message;
        }
        // a long build may have saved its first parts
        host.reload();
    }

    // ------------------------------------------------------------------ drawing

    /** Fills {@code body} with the Timeline tab, or the Map tab. */
    void render(LinearLayout body, boolean map) {
        if (chapters == null || where == null) {
            return;
        }
        final boolean busy = busy();
        if (timeline.isEmpty()) {
            renderEmpty(body, busy);
            return;
        }
        renderState(body, busy);
        final FictionStoryView view = FictionStoryView.build(timeline, chapters, where);
        if (map) {
            renderMap(body, view);
        } else {
            renderTimeline(body, view);
        }
        if (view.hidden > 0) {
            body.addView(muted(a.getResources().getQuantityString(R.plurals.canvas_t30_hidden, view.hidden, view.hidden)),
                    FictionUi.row(2, 14, 2, 0));
        }
        body.addView(muted(a.getString(R.string.canvas_t30_foot)), FictionUi.row(2, 18, 2, 0));
    }

    /** Nothing stored: what it is, what one tap sends, and the tap (or its progress). */
    private void renderEmpty(LinearLayout body, boolean busy) {
        body.addView(label(a.getString(R.string.canvas_t29_spoiler_safe), true), FictionUi.row(2, 12, 0, 0));
        body.addView(FictionUi.text(a, a.getString(R.string.canvas_t30_empty), 20, 600, CanvasLook.ink()),
                FictionUi.row(2, 4, 0, 0));
        final int percent = chapters.pages() <= 0 ? 0 : Math.round(100f * readerEnd / chapters.pages());
        final TextView note = FictionUi.text(a, a.getString(R.string.canvas_t30_empty_note,
                chapters.label(Math.max(0, where.reader)), percent), CanvasLook.TEXT_TITLE, 400, CanvasLook.ink2());
        note.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(note, FictionUi.row(2, 8, 0, 0));
        if (busy || error != null) {
            body.addView(progress(busy), FictionUi.row(2, 12, 0, 0));
        }
        if (!busy) {
            final TextView make = FictionUi.button(a, a.getString(error != null ? R.string.study_retry
                    : R.string.canvas_t30_build), true);
            make.setOnClickListener(v -> make(false));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    CanvasLook.dp(52));
            lp.topMargin = CanvasLook.dp(16);
            body.addView(make, lp);
        }
    }

    /** What to do when the reader is not where the data was built: the update, or what is hidden. */
    private void renderState(LinearLayout body, boolean busy) {
        if (where.state == FictionWhere.BEFORE) {
            body.addView(muted(a.getString(R.string.canvas_t30_back_note, chapters.label(where.reader),
                    chapters.label(Math.max(0, where.stored)))), FictionUi.row(2, 6, 2, 0));
        } else if (where.state == FictionWhere.PAST && !busy) {
            final LinearLayout card = FictionUi.column(a);
            card.setBackground(CanvasParts.accentCard());
            card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(12));
            final boolean sameChapter = where.reader == where.stored;
            final String now = chapters.label(Math.max(0, where.reader));
            final int more = where.readerEnd - where.storedEnd;
            card.addView(FictionUi.text(a, sameChapter
                            ? a.getResources().getQuantityString(R.plurals.canvas_t29_read_on_pages, more, more)
                            : a.getString(R.string.canvas_t29_read_on, now), CanvasLook.TEXT_TITLE, 400, CanvasLook.ink()));
            final TextView update = FictionUi.button(a, sameChapter ? a.getString(R.string.canvas_t29_update_page)
                    : a.getString(R.string.canvas_t29_update_to, now), true);
            update.setOnClickListener(v -> make(false));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    CanvasLook.dp(48));
            lp.topMargin = CanvasLook.dp(10);
            card.addView(update, lp);
            body.addView(card, FictionUi.row(0, 8, 0, 0));
        }
        if (busy || error != null) {
            body.addView(progress(busy), FictionUi.row(2, 10, 0, 0));
        }
    }

    private View progress(boolean busy) {
        final String text = busy ? (!TextUtils.isEmpty(status) ? status
                : !AiProgress.status(key).isEmpty() ? AiProgress.status(key) : a.getString(R.string.ai_task_queued))
                : error;
        return FictionUi.progressRow(a, busy, text, this::cancel);
    }

    // ------------------------------------------------------------------ the timeline

    private void renderTimeline(LinearLayout body, FictionStoryView view) {
        final FictionTimeline.Plan plan = view.progress ? timeline.plan : null;
        if (plan != null) {
            body.addView(planCard(plan), FictionUi.row(0, 8, 0, 0));
            if (!plan.legs.isEmpty()) {
                body.addView(legsHead(plan), FictionUi.row(0, 18, 0, 6));
                final double longest = FictionStoryView.longest(plan);
                for (int i = 0; i < plan.legs.size(); i++) {
                    body.addView(legRow(plan.legs.get(i), plan.unit, longest, i == plan.legs.size() - 1));
                }
            }
        }
        body.addView(label(a.getString(R.string.canvas_t30_moments), false), FictionUi.row(0, 16, 0, 4));
        if (view.moments.isEmpty()) {
            body.addView(muted(a.getString(R.string.canvas_t30_no_moments)), FictionUi.row(0, 4, 0, 0));
        }
        for (FictionTimeline.Moment m : view.moments) {
            body.addView(momentRow(view, m));
        }
    }

    /** "Day 22 of 80 · On schedule", how far that is, and one sentence on why. */
    private View planCard(FictionTimeline.Plan plan) {
        final LinearLayout card = FictionUi.column(a);
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14));
        card.setBackground(CanvasParts.round(CanvasLook.surface(), 0, CanvasLook.dp(16)));
        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final String count = FictionTimeline.count(plan);
        final SpannableStringBuilder s = new SpannableStringBuilder(count.isEmpty() ? plan.what : count);
        if (!count.isEmpty() && plan.total > 0) {
            final int from = s.length();
            s.append(" ").append(a.getString(R.string.canvas_t30_of_total, FictionTimeline.amount(plan.total)));
            s.setSpan(new RelativeSizeSpan(15f / 24f), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(CanvasLook.ink2()), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        top.addView(FictionUi.text(a, s, count.isEmpty() ? 17 : 24, 700, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!plan.status.isEmpty()) {
            final TextView pill = FictionUi.text(a, plan.status, CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
            pill.setGravity(Gravity.CENTER);
            pill.setMaxLines(2);
            pill.setPadding(CanvasLook.dp(10), CanvasLook.dp(4), CanvasLook.dp(10), CanvasLook.dp(4));
            pill.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(13)));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = CanvasLook.dp(8);
            top.addView(pill, lp);
        }
        card.addView(top);
        if (!count.isEmpty() && !plan.what.isEmpty()) {
            card.addView(FictionUi.text(a, plan.what, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()));
        }
        if (plan.total > 0 && plan.now > 0) {
            final Bars bar = new Bars(a, 1, FictionStoryView.done(plan), false, false);
            bar.track = true;
            card.addView(bar, heightRow(8, 8));
        }
        if (!plan.note.isEmpty()) {
            final TextView note = FictionUi.text(a, plan.note, 13, 400, CanvasLook.ink2());
            card.addView(note, FictionUi.row(0, 8, 0, 0));
        }
        return card;
    }

    /** "Plan vs actual, by leg", where the book states the plan, and what the two bars are. */
    private View legsHead(FictionTimeline.Plan plan) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        final LinearLayout texts = FictionUi.column(a);
        texts.addView(FictionUi.text(a, a.getString(R.string.canvas_t30_plan_legs), CanvasLook.TEXT_BODY, 600, CanvasLook.ink()));
        final String source = planSource(plan);
        if (!source.isEmpty()) {
            texts.addView(FictionUi.text(a, source, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final LinearLayout legend = FictionUi.column(a);
        legend.setGravity(Gravity.END);
        legend.addView(legendRow(a.getString(R.string.canvas_t30_legend_plan), 1, -1));
        legend.addView(legendRow(a.getString(R.string.canvas_t30_legend_actual), -1, 1));
        row.addView(legend);
        return row;
    }

    /** "Plan: the Daily Telegraph's estimate, Ch. 3"; the source in the book's (the model's) words. */
    private String planSource(FictionTimeline.Plan plan) {
        final String ch = chapters.label(chapters.indexOf(plan.sourceKey, plan.sourceCh, timeline.mark.chapters));
        if (plan.source.isEmpty()) {
            return ch.isEmpty() ? "" : a.getString(R.string.canvas_t30_plan_stated, ch);
        }
        return ch.isEmpty() ? plan.source : a.getString(R.string.canvas_t30_plan_source, plan.source, ch);
    }

    private View legendRow(String text, float plan, float actual) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new Bars(a, plan, actual, false, false), new LinearLayout.LayoutParams(CanvasLook.dp(16), CanvasLook.dp(8)));
        final TextView t = FictionUi.text(a, text, 11, 400, CanvasLook.ink2());
        t.setPadding(CanvasLook.dp(6), 0, 0, 0);
        row.addView(t);
        return row;
    }

    /** One leg: "London → Suez", the planned bar over the actual one, "6½ of 7 d". */
    private View legRow(FictionTimeline.Leg leg, String unit, double longest, boolean last) {
        final boolean here = FictionTimeline.HERE.equals(leg.state);
        final boolean ahead = FictionTimeline.AHEAD.equals(leg.state);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.dp(here ? 52 : ahead ? 40 : 44));
        row.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(4));
        if (here) {
            row.setBackgroundColor(CanvasLook.surface());
        }

        final LinearLayout names = FictionUi.column(a);
        final String name = leg.from.isEmpty() ? leg.to : leg.to.isEmpty() ? leg.from : leg.from + " → " + leg.to;
        names.addView(FictionUi.text(a, name, CanvasLook.TEXT_SMALL, here ? 600 : 400, ahead ? CanvasLook.muted() : CanvasLook.ink()));
        if (here) {
            names.addView(FictionUi.text(a, a.getString(R.string.canvas_t30_leg_here), 11, 600, CanvasLook.accent()));
        }
        row.addView(names, new LinearLayout.LayoutParams(CanvasLook.dp(112), ViewGroup.LayoutParams.WRAP_CONTENT));

        final float plan = longest <= 0 || leg.plan <= 0 ? -1 : (float) (leg.plan / longest);
        final float actual = ahead || longest <= 0 || leg.actual < 0 ? -1 : (float) (leg.actual / longest);
        final LinearLayout.LayoutParams bars = new LinearLayout.LayoutParams(0, CanvasLook.dp(actual >= 0 ? 19 : 8), 1);
        bars.leftMargin = CanvasLook.dp(8);
        bars.rightMargin = CanvasLook.dp(8);
        row.addView(new Bars(a, plan, actual, here, ahead), bars);

        final LinearLayout right = FictionUi.column(a);
        right.setGravity(Gravity.END);
        right.setMinimumWidth(CanvasLook.dp(64));
        final String amount = FictionTimeline.legText(leg, unit);
        final TextView t = FictionUi.text(a, here && leg.actual >= 0 && !amount.isEmpty() ? "~" + amount : amount,
                CanvasLook.TEXT_SMALL, 400, ahead ? CanvasLook.muted() : CanvasLook.ink2());
        t.setGravity(Gravity.END);
        right.addView(t);
        if (!leg.note.isEmpty()) {
            final TextView note = FictionUi.text(a, leg.note, 11, 400, CanvasLook.sensor());
            note.setGravity(Gravity.END);
            note.setMaxWidth(CanvasLook.dp(96));
            right.addView(note);
        }
        row.addView(right, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (last) {
            return row;
        }
        final LinearLayout lined = FictionUi.column(a);
        lined.addView(row);
        final View line = new View(a);
        line.setBackgroundColor(CanvasLook.line());
        lined.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));
        return lined;
    }

    /**
     * A key moment: the time as the book gives it (else the chapter), "Suez · Fix spots Fogg…", and
     * the book's own words in quote marks when they were found. Then a tap goes there.
     */
    private View momentRow(FictionStoryView view, FictionTimeline.Moment m) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, CanvasLook.dp(6), 0, CanvasLook.dp(6));
        final boolean timed = !m.time.isEmpty();
        // a time label goes in capitals like the canvas's "DAY 7"; a chapter's name stays as the book has it
        final String when = timed ? m.time.toUpperCase(Locale.getDefault())
                : chapters.label(chapters.indexOf(m.key, m.ch, timeline.mark.chapters));
        final TextView time = FictionUi.mono(a, when, 11, timed ? CanvasLook.accent() : CanvasLook.muted(), false);
        time.setLetterSpacing(0f);
        time.setMaxLines(3);
        time.setPadding(0, CanvasLook.dp(2), 0, 0);
        row.addView(time, new LinearLayout.LayoutParams(CanvasLook.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout texts = FictionUi.column(a);
        texts.setPadding(CanvasLook.dp(10), 0, 0, 0);
        final String line = m.place.isEmpty() ? m.line : m.line.isEmpty() ? m.place : m.place + " · " + m.line;
        texts.addView(FictionUi.text(a, line, 13, 400, CanvasLook.ink()));
        final FictionStoryView.Jump jump = view.jump(m);
        if (jump != null) {
            // the book's own words, found in its text: only then in quote marks
            final TextView quote = FictionUi.serif(a, "“" + jump.quote + "”", 13, CanvasLook.ink2());
            quote.setPadding(0, CanvasLook.dp(2), 0, 0);
            texts.addView(quote);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (jump != null) {
            row.setMinimumHeight(CanvasLook.target());
            row.setBackground(CanvasParts.ripple(null));
            row.setContentDescription(when + ". " + line + ". " + a.getString(R.string.canvas_t30_moment_jump));
            row.setOnClickListener(v -> go(jump));
        }
        return row;
    }

    // ------------------------------------------------------------------ the map

    private void renderMap(LinearLayout body, FictionStoryView view) {
        final FictionTimeline.Plan plan = view.progress ? timeline.plan : null;
        final FictionMapModel model = FictionMapModel.of(view.places, timeline, plan != null, view.progress);
        final List<FictionTimeline.Place> chips = view.chips();
        if (!model.dots.isEmpty()) {
            final FictionMapView mapView = new FictionMapView(a);
            mapView.set(model);
            mapView.layers(showRoute, showPlan);
            final StringBuilder names = new StringBuilder();
            for (FictionTimeline.Place p : chips) {
                names.append(names.length() == 0 ? "" : ", ").append(p.name);
            }
            mapView.setContentDescription(a.getString(R.string.canvas_t30_map_desc, names));
            body.addView(mapView, FictionUi.row(0, 8, 0, 0));

            final LinearLayout layers = new LinearLayout(a);
            layers.setOrientation(LinearLayout.HORIZONTAL);
            final TextView route = layerChip(a.getString(R.string.canvas_t30_route), showRoute);
            route.setOnClickListener(v -> {
                showRoute = !showRoute;
                host.redraw();
            });
            layers.addView(route);
            if (!model.plan.isEmpty()) {
                final String from = chapters.label(chapters.indexOf(plan.sourceKey, plan.sourceCh, timeline.mark.chapters));
                final TextView planChip = layerChip(from.isEmpty() ? a.getString(R.string.canvas_t30_plan_layer)
                        : a.getString(R.string.canvas_t30_plan_from, from), showPlan);
                planChip.setOnClickListener(v -> {
                    showPlan = !showPlan;
                    host.redraw();
                });
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        CanvasLook.target());
                lp.leftMargin = CanvasLook.dp(8);
                layers.addView(planChip, lp);
            }
            body.addView(layers, FictionUi.row(0, 12, 0, 0));
            if (model.geographic) {
                // the coordinates are the model's knowledge of the world, not the book's: said so
                body.addView(FictionUi.text(a, a.getString(R.string.canvas_t30_rough), CanvasLook.TEXT_SMALL, 600,
                        CanvasLook.ink2()), FictionUi.row(2, 10, 2, 0));
                body.addView(muted(a.getString(R.string.canvas_t30_rough_note)), FictionUi.row(2, 2, 2, 0));
            } else {
                body.addView(muted(a.getString(R.string.canvas_t30_schematic)), FictionUi.row(2, 10, 2, 0));
            }
        }
        if (view.progress && !timeline.here.isEmpty()) {
            body.addView(hereCard(plan), FictionUi.row(0, 12, 0, 0));
        }
        body.addView(label(a.getString(R.string.canvas_t30_places), false), FictionUi.row(0, 16, 0, 8));
        if (chips.isEmpty()) {
            body.addView(muted(a.getString(R.string.canvas_t30_no_places)));
            return;
        }
        final FictionFlow flow = new FictionFlow(a, 6);
        for (FictionTimeline.Place p : chips) {
            flow.addView(placeChip(p, view.jump(p)));
        }
        body.addView(flow, FictionUi.row(0, 0, 0, 0));
    }

    /** A layer of the map: on in the accent, off plain. */
    private TextView layerChip(String text, boolean on) {
        final TextView t = FictionUi.text(a, text, 13, 600, on ? CanvasLook.accent() : CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        t.setBackground(CanvasParts.round(on ? CanvasLook.accentTint() : CanvasLook.bg(),
                on ? CanvasLook.accent() : CanvasLook.line(), CanvasLook.dp(22)));
        t.setSelected(on);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        return t;
    }

    /** "YOU ARE HERE · DAY 22", where the story is, and the next leg as the plan states it. */
    private View hereCard(FictionTimeline.Plan plan) {
        final LinearLayout card = FictionUi.column(a);
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14));
        card.setBackground(CanvasParts.round(CanvasLook.surface(), 0, CanvasLook.dp(16)));
        card.addView(label(timeline.hereTime.isEmpty() ? a.getString(R.string.canvas_t30_here)
                : a.getString(R.string.canvas_t30_here_time, timeline.hereTime), true));
        card.addView(FictionUi.text(a, timeline.here, CanvasLook.TEXT_BODY, 600, CanvasLook.ink()), FictionUi.row(0, 6, 0, 0));
        if (plan != null && !timeline.next.isEmpty()) {
            card.addView(FictionUi.text(a, timeline.next, 13, 400, CanvasLook.ink2()), FictionUi.row(0, 6, 0, 0));
        }
        return card;
    }

    /** A place so far; with words found in the book, a tap goes to them. */
    private View placeChip(FictionTimeline.Place p, FictionStoryView.Jump jump) {
        final TextView t = FictionUi.text(a, p.name, CanvasLook.TEXT_SMALL, 400, jump != null ? CanvasLook.ink() : CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMinHeight(CanvasLook.dp(32));
        t.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        final android.graphics.drawable.Drawable shape = CanvasParts.round(CanvasLook.bg(),
                jump != null ? CanvasLook.lineStrong() : CanvasLook.line(), CanvasLook.dp(8));
        if (jump != null) {
            t.setBackground(CanvasParts.ripple(shape));
            t.setContentDescription(a.getString(R.string.canvas_t30_place_jump, p.name));
            t.setOnClickListener(v -> go(jump));
        } else {
            t.setBackground(shape);
        }
        return t;
    }

    /** Goes to the book's own words and closes the screen. */
    private void go(FictionStoryView.Jump jump) {
        AiJump.toQuote(dc, jump.quote, jump.page, -1, -1);
        host.close();
    }

    // ------------------------------------------------------------------ small views

    private TextView label(String text, boolean accent) {
        final TextView t = new TextView(a);
        CanvasParts.label(t, accent);
        t.setText(text);
        return t;
    }

    private TextView muted(String text) {
        final TextView t = new TextView(a);
        CanvasParts.secondary(t);
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams heightRow(int heightDp, int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(heightDp));
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }

    /**
     * The two bars of a leg: the plan as an outline, what it took filled, each a share of the
     * view's width. A share below 0 is not drawn. Also the plan's progress bar ({@link #track}).
     */
    private static final class Bars extends View {
        private final float plan;
        private final float actual;
        private final boolean here;
        private final boolean ahead;
        /** A filled track the whole width under the actual bar: the progress bar. */
        boolean track;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bars(Context c, float plan, float actual, boolean here, boolean ahead) {
            super(c);
            this.plan = plan;
            this.actual = actual;
            this.here = here;
            this.ahead = ahead;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth();
            final float bar = CanvasLook.dp(8);
            final float radius = track ? bar / 2 : CanvasLook.dp(3);
            final float line = Math.max(1f, CanvasLook.dp(3) / 2f);
            final float least = CanvasLook.dp(6);
            float top = 0;
            if (track) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CanvasLook.lineStrong());
                r.set(0, 0, w, bar);
                canvas.drawRoundRect(r, radius, radius, paint);
            } else if (plan >= 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(line);
                paint.setColor(ahead ? CanvasLook.lineStrong() : CanvasLook.muted());
                r.set(line / 2, line / 2, Math.max(least, Math.min(1f, plan) * w) - line / 2, bar - line / 2);
                canvas.drawRoundRect(r, radius, radius, paint);
                top = bar + CanvasLook.dp(3);
            }
            if (actual >= 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CanvasLook.accent());
                final float end = Math.max(track ? 0 : least, Math.min(1f, actual) * w);
                r.set(0, top, end, top + bar);
                canvas.drawRoundRect(r, radius, radius, paint);
                if (here) {
                    // the leg is under way: an open, dashed end
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(line);
                    paint.setPathEffect(new DashPathEffect(new float[]{CanvasLook.dp(3), CanvasLook.dp(2)}, 0));
                    r.set(end, top + line / 2, Math.min(w - line / 2, end + CanvasLook.dp(8)), top + bar - line / 2);
                    canvas.drawRoundRect(r, radius, radius, paint);
                    paint.setPathEffect(null);
                }
            }
        }
    }
}
