package com.foobnix.assist.lens;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * The ladder of one idea of a chapter's bridge (canvas LensLadder, "Up and down the ladder"): four
 * rungs from the principle (abstract) down to the reader's world (concrete); "Boil it down" and
 * "Make it concrete" step between them. Opened by "Ladder" on a bridge row: that tap asks the AI
 * once for the idea's rungs, which are then kept in the row.
 */
final class LensLadderScreen implements LensJob.Screen<LensLadder> {

    private final Activity a;
    private final AiJobs.Target target;
    private final String author;
    private final BookChapters.Range range;
    private final int index;
    private final LensBridge.Row row;

    private Dialog dialog;
    private LinearLayout body;
    /** The rung shown as chosen, 4 (principle) to 1 (your world). */
    private int rung = LensLadder.WORLD;
    private String error;
    private LensJob.Ladder job;

    private LensLadderScreen(Activity a, AiJobs.Target target, String author, BookChapters.Range range, int index,
                             LensBridge.Row row) {
        this.a = a;
        this.target = target;
        this.author = author;
        this.range = range;
        this.index = index;
        this.row = row;
    }

    /** The ladder of row {@code index} of a chapter's bridge; asks for it when the row has none yet. */
    static void show(Activity a, AiJobs.Target target, String author, BookChapters.Range range, LensBridge bridge,
                     int index) {
        if (!Assist.LENS.isOn() || bridge == null || index < 0 || index >= bridge.rows.size()) {
            return;
        }
        new LensLadderScreen(a, target, author, range, index, bridge.rows.get(index)).open();
    }

    private void open() {
        final LinearLayout page = LensUi.page(a);
        dialog = LensUi.fullScreen(a, page);
        final String ends = a.getString(R.string.lens_ladder_ends);
        // the chapter's title as the book has it
        final String sub = TextUtils.isEmpty(range.title) ? ends : range.title.trim() + " · " + ends;
        page.addView(LensUi.topBar(a, a.getString(R.string.lens_ladder_title), sub, () -> dialog.dismiss(), null),
                LensUi.barParams());
        final ScrollView scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        if (row.ladder == null) {
            ask();
        } else {
            render();
        }
    }

    private void render() {
        body.removeAllViews();
        final TextView hint = LensUi.text(a, a.getString(R.string.lens_ladder_hint), 13, 400, CanvasLook.ink2());
        hint.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(hint, LensUi.row(20, 0, 20, 0));
        // which idea: its two sides from the bridge
        final TextView idea = LensUi.serif(a, row.idea, 14, CanvasLook.ink());
        body.addView(idea, LensUi.row(20, 10, 20, 0));

        final LensLadder ladder = row.ladder;
        if (ladder == null) {
            renderWaiting();
            return;
        }
        body.addView(LensUi.mono(a, "↑ " + a.getString(R.string.lens_abstract), 10, CanvasLook.muted(), false),
                LensUi.row(16, 14, 16, 0));

        final LinearLayout rail = new LinearLayout(a);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        final View bar = new View(a);
        bar.setBackground(CanvasParts.round(CanvasLook.lineStrong(), 0, CanvasLook.dp(2)));
        final LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(CanvasLook.dp(3), ViewGroup.LayoutParams.MATCH_PARENT);
        barLp.leftMargin = CanvasLook.dp(6);
        rail.addView(bar, barLp);
        final LinearLayout rungs = new LinearLayout(a);
        rungs.setOrientation(LinearLayout.VERTICAL);
        for (int n = LensLadder.RUNGS; n >= 1; n--) {
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = n == LensLadder.RUNGS ? 0 : CanvasLook.dp(14);
            rungs.addView(rungCard(n, ladder.rung(n)), lp);
        }
        final LinearLayout.LayoutParams rungsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rungsLp.leftMargin = CanvasLook.dp(16);
        rail.addView(rungs, rungsLp);
        body.addView(rail, LensUi.row(16, 6, 16, 6));

        body.addView(LensUi.mono(a, "↓ " + a.getString(R.string.lens_concrete), 10, CanvasLook.muted(), false),
                LensUi.row(16, 0, 16, 0));

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView up = LensUi.button(a, a.getString(R.string.lens_boil_down), false);
        up.setOnClickListener(v -> step(1));
        LensUi.enable(up, rung < LensLadder.RUNGS);
        final TextView down = LensUi.button(a, a.getString(R.string.lens_make_concrete), false);
        down.setOnClickListener(v -> step(-1));
        LensUi.enable(down, rung > 1);
        buttons.addView(up, new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1));
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(down, right);
        body.addView(buttons, LensUi.row(16, 12, 16, 0));

        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(16)));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14), CanvasLook.dp(14));
        card.addView(LensUi.mono(a, a.getString(R.string.lens_any_sentence), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false));
        final TextView how = LensUi.text(a, a.getString(R.string.lens_any_sentence_how), CanvasLook.TEXT_TITLE, 400, CanvasLook.ink());
        how.setLineSpacing(CanvasLook.dp(3), 1f);
        how.setPadding(0, CanvasLook.dp(8), 0, 0);
        card.addView(how);
        body.addView(card, LensUi.row(16, 14, 16, 0));
    }

    /** While the rungs are asked for, or after a failure: what goes on, and "Try again". */
    private void renderWaiting() {
        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if (job != null) {
            line.addView(new ProgressBar(a), new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        }
        final TextView status = LensUi.text(a, job != null ? a.getString(R.string.lens_ladder_building)
                : error == null ? "" : error, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        status.setPadding(CanvasLook.dp(job != null ? 6 : 0), 0, 0, 0);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(line, LensUi.row(20, 16, 20, 0));
        if (job == null) {
            final TextView again = LensUi.button(a, a.getString(R.string.lens_try_again), true);
            again.setOnClickListener(v -> ask());
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(48));
            lp.setMargins(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), 0);
            body.addView(again, lp);
        }
    }

    /** One rung as the canvas draws it; the chosen one in the accent tint. */
    private View rungCard(final int n, String text) {
        final boolean on = n == rung;
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(CanvasLook.dp(64));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(10), CanvasLook.dp(14), CanvasLook.dp(10));
        card.setBackground(CanvasParts.ripple(on
                ? LensUi.outline(CanvasLook.accentTint(), CanvasLook.accent(), 15, CanvasLook.RADIUS_DP, false)
                : LensUi.outline(CanvasLook.bg(), CanvasLook.lineStrong(), 10, CanvasLook.RADIUS_DP, false)));
        card.addView(LensUi.mono(a, n + " · " + a.getString(name(n)), 10, on ? CanvasLook.accent() : CanvasLook.muted(), on));
        // the reader's world is in the interface face, the book's side in the serif
        final TextView words = n == LensLadder.WORLD ? LensUi.text(a, text, 15, 400, CanvasLook.ink())
                : LensUi.serif(a, text, 15, CanvasLook.ink());
        words.setPadding(0, CanvasLook.dp(2), 0, 0);
        card.addView(words);
        card.setSelected(on);
        card.setOnClickListener(v -> {
            rung = n;
            render();
        });
        return card;
    }

    private static int name(int n) {
        switch (n) {
            case LensLadder.PRINCIPLE:
                return R.string.lens_rung_principle;
            case LensLadder.MECHANISM:
                return R.string.lens_rung_mechanism;
            case LensLadder.EXAMPLE:
                return R.string.lens_rung_example;
            default:
                return R.string.lens_rung_world;
        }
    }

    /** Up (+1, boil it down) or down (-1, make it concrete) one rung. */
    private void step(int by) {
        final int next = rung + by;
        if (next >= 1 && next <= LensLadder.RUNGS) {
            rung = next;
            render();
        }
    }

    // --- the request ---

    private void ask() {
        if (job != null || !Assist.LENS.isOn()) {
            return;
        }
        error = null;
        job = new LensJob.Ladder(a, target, author, range.key, range.title, index, row, this);
        render();
        job.start();
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
    }

    @Override
    public void onDone(LensLadder ladder) {
        job = null;
        row.ladder = ladder;
        if (dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        error = message;
        if (dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        error = a.getString(R.string.lens_need_key);
        if (dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }
}
