package com.foobnix.assist.lens;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiMenu;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The concept bridge of the chapter at the reader's page (canvas LensBridge, "in your terms"): up
 * to six of its ideas in two columns, AUTHOR SAYS and IN YOUR TERMS, and the card WHERE THE
 * ANALOGY BREAKS. A stored bridge shows at once; "Map this chapter" asks the AI once for the
 * chapter. A tap on a row goes to its passage; "Ladder" opens the idea's ladder; the "Lens:" chip
 * opens the lens setup. Without a lens the setup opens first.
 */
public final class LensBridgeScreen implements LensJob.Screen<LensBridge> {

    /** The canvas's "where the analogy breaks" card, day. */
    private static final int BREAKS_FILL = 0xFFFFF8F3;
    private static final int BREAKS_LINE = 0xFFF3C6A5;

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final BookChapters.Range range;
    private final String author;
    /** The chapter has stored key-passage ideas: they are sent, not its text. */
    private final boolean hasIdeas;
    private LensProfile profile;
    private LensProfile.Level level;
    private LensBridge bridge;

    private Dialog dialog;
    private LinearLayout body;
    private TextView count;
    private TextView chip;
    private ProgressBar progress;
    private TextView status;
    private TextView action;
    private String error;
    private LensJob.Bridge job;

    private LensBridgeScreen(Activity a, DocumentController dc, AiJobs.Target target, BookChapters.Range range, String author,
                             boolean hasIdeas, LensProfile profile, LensProfile.Level level, LensBridge bridge) {
        this.a = a;
        this.dc = dc;
        this.target = target;
        this.range = range;
        this.author = author;
        this.hasIdeas = hasIdeas;
        this.profile = profile;
        this.level = level;
        this.bridge = bridge;
    }

    /** The ✦ menu's "In your terms": the bridge of the chapter at the reader's page; the lens setup first without a lens. */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.LENS.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final String author = author(dc);
        new Thread(() -> {
            LensProfile profile = null;
            LensProfile.Level level = LensProfile.Level.NEW;
            BookChapters.Range range = null;
            LensBridge stored = null;
            boolean ideas = false;
            try {
                profile = LensStore.profile();
                if (profile.isSet()) {
                    level = LensStore.level(t.name);
                    range = AiJobs.chapterAt(t);
                    if (range != null) {
                        stored = LensStore.bridge(t.name, range.key);
                        final AiStore.Chapter chapter = AiStore.get().load(t.name).chapter(range.key);
                        ideas = chapter != null && !chapter.ideas.isEmpty();
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final LensProfile p = profile;
            final LensProfile.Level l = level;
            final BookChapters.Range r = range;
            final LensBridge b = stored;
            final boolean hasIdeas = ideas;
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (p == null || !p.isSet()) {
                    // the first entry: say what you know well, then come back here
                    LensSetupScreen.show(a, dc, () -> showHere(a, dc));
                } else if (r == null) {
                    Toast.makeText(a, R.string.ai_cannot_open, Toast.LENGTH_LONG).show();
                } else {
                    new LensBridgeScreen(a, dc, t, r, author, hasIdeas, p, l, b).open();
                }
            });
        }, "@T lens").start();
    }

    /** The book's author as its record has it (the first of several), or empty. */
    static String author(DocumentController dc) {
        try {
            final String s = dc.getBookFileMeta().getAuthor();
            if (s == null) {
                return "";
            }
            final String first = s.split("[,;&]")[0].trim();
            return first.length() > 40 ? "" : first;
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    private void open() {
        final LinearLayout page = LensUi.page(a);
        dialog = LensUi.fullScreen(a, page);

        final ImageView change = new ImageView(a);
        change.setImageResource(R.drawable.canvas_t14_ic_lens);
        change.setColorFilter(CanvasLook.ink());
        change.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final int inset = CanvasLook.dp(11);
        change.setPadding(inset, inset, inset, inset);
        change.setContentDescription(a.getString(R.string.lens_change));
        change.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        change.setOnClickListener(v -> changeLens());
        final String title = author.isEmpty() ? a.getString(R.string.lens_in_your_terms)
                : a.getString(R.string.lens_bridge_title, author);
        // titles as the book has them
        final String sub = TextUtils.isEmpty(range.title) ? target.title : target.title + " · " + range.title.trim();
        page.addView(LensUi.topBar(a, title, sub, () -> dialog.dismiss(), change), LensUi.barParams());

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        count = LensUi.text(a, "", 13, 400, CanvasLook.ink2());
        head.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        chip = LensUi.text(a, "", CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(CanvasLook.dp(180));
        chip.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        // a 26 dp chip inside a 44 dp touch target
        chip.setBackground(new InsetDrawable(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(13)),
                0, CanvasLook.dp(9), 0, CanvasLook.dp(9)));
        chip.setOnClickListener(v -> changeLens());
        final LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target());
        chipLp.leftMargin = CanvasLook.dp(8);
        head.addView(chip, chipLp);
        page.addView(head, LensUi.row(20, 0, 16, 0));

        final ScrollView scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final LinearLayout footer = new LinearLayout(a);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(CanvasLook.dp(16), CanvasLook.dp(8), CanvasLook.dp(16), CanvasLook.dp(14));
        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(a);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        status = LensUi.text(a, "", CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        status.setPadding(CanvasLook.dp(6), 0, 0, 0);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        footer.addView(line);
        action = LensUi.button(a, "", true);
        action.setOnClickListener(v -> onAction());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(48));
        lp.topMargin = CanvasLook.dp(8);
        footer.addView(action, lp);
        page.addView(footer);

        dialog.setOnDismissListener(d -> stop());
        render();
        dialog.show();
    }

    private void render() {
        final boolean running = job != null;
        final boolean mapped = bridge != null;
        chip.setText(a.getString(R.string.lens_chip, profile.shortName()));
        count.setText(mapped
                ? a.getResources().getQuantityString(R.plurals.lens_ideas_mapped, bridge.rows.size(), bridge.rows.size())
                : a.getString(R.string.lens_not_mapped));

        body.removeAllViews();
        if (mapped) {
            renderBridge();
        } else {
            final TextView about = LensUi.text(a, a.getString(hasIdeas ? R.string.lens_sends_ideas : R.string.lens_sends_text),
                    13, 400, CanvasLook.muted());
            about.setLineSpacing(CanvasLook.dp(3), 1f);
            body.addView(about, LensUi.row(20, 12, 20, 0));
        }

        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            status.setText(R.string.lens_mapping);
        } else if (error != null) {
            status.setText(error);
        } else if (mapped && bridge.isOfAnotherLens(profile.signature(level))) {
            status.setText(TextUtils.isEmpty(bridge.lensName) ? a.getString(R.string.lens_other_lens)
                    : a.getString(R.string.lens_other_lens_named, bridge.lensName));
        } else {
            status.setText("");
        }
        status.setVisibility(running || status.length() > 0 ? View.VISIBLE : View.GONE);
        action.setText(mapped ? R.string.lens_map_again : R.string.lens_map);
        LensUi.style(action, !mapped);
        LensUi.enable(action, !running);
    }

    private void renderBridge() {
        final LinearLayout columns = new LinearLayout(a);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(0, CanvasLook.dp(12), 0, CanvasLook.dp(6));
        columns.addView(LensUi.mono(a, a.getString(R.string.lens_author_says), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        columns.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.dp(36), 1));
        columns.addView(LensUi.mono(a, a.getString(R.string.lens_in_your_terms), CanvasLook.TEXT_LABEL, CanvasLook.accent(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(columns, LensUi.row(16, 0, 16, 0));
        body.addView(LensUi.line(a, CanvasLook.line()), LensUi.row(16, 0, 16, 0));

        boolean jumps = false;
        for (int i = 0; i < bridge.rows.size(); i++) {
            if (i > 0) {
                body.addView(LensUi.line(a, CanvasLook.surface2()), LensUi.row(16, 0, 16, 0));
            }
            body.addView(row(i, bridge.rows.get(i)));
            jumps |= bridge.rows.get(i).located();
        }

        if (!bridge.breaks.isEmpty()) {
            final LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            final boolean night = CanvasLook.isNight();
            card.setBackground(LensUi.outline(night ? CanvasLook.surface() : BREAKS_FILL,
                    night ? CanvasLook.sensorTint() : BREAKS_LINE, 15, CanvasLook.RADIUS_LARGE_DP, true));
            card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
            card.addView(LensUi.mono(a, a.getString(profile.analogies ? R.string.lens_breaks : R.string.lens_breaks_plain),
                    CanvasLook.TEXT_LABEL, CanvasLook.sensor(), false));
            final TextView note = LensUi.text(a, bridge.breaks, 13, 400, CanvasLook.ink());
            note.setLineSpacing(CanvasLook.dp(3), 1f);
            note.setPadding(0, CanvasLook.dp(4), 0, 0);
            card.addView(note);
            body.addView(card, LensUi.row(16, 14, 16, 0));
        }
        if (jumps) {
            body.addView(LensUi.text(a, a.getString(R.string.lens_tap_hint), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()),
                    LensUi.row(20, 14, 20, 0));
        }
    }

    /** One idea: the author's side with where it is, an arrow, the reader's side with "Ladder". */
    private View row(final int index, final LensBridge.Row r) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.dp(62));
        row.setPadding(CanvasLook.dp(16), CanvasLook.dp(8), CanvasLook.dp(16), 0);
        row.setBackground(CanvasParts.ripple(null));

        final LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(LensUi.serif(a, r.idea, 14, CanvasLook.ink()));
        final String where = r.page >= 0 ? a.getString(R.string.lens_page, r.page + 1)
                : r.located() ? "" : a.getString(R.string.ai_not_located);
        if (!where.isEmpty()) {
            final TextView ref = LensUi.mono(a, where, 10, CanvasLook.muted(), false);
            ref.setPadding(0, CanvasLook.dp(2), 0, 0);
            left.addView(ref);
        }
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final TextView arrow = LensUi.text(a, "→", 16, 400, CanvasLook.muted());
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(CanvasLook.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(LensUi.text(a, r.yours, 14, 600, CanvasLook.ink()));
        final TextView ladder = LensUi.text(a, a.getString(R.string.lens_ladder_link), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        ladder.setGravity(Gravity.CENTER_VERTICAL);
        ladder.setMinHeight(CanvasLook.dp(40));
        ladder.setPadding(0, 0, CanvasLook.dp(12), 0);
        ladder.setOnClickListener(v -> {
            if (job == null) {
                LensLadderScreen.show(a, target, author, range, bridge, index);
            }
        });
        right.addView(ladder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (r.located()) {
            row.setOnClickListener(v -> jump(r));
        }
        return row;
    }

    /** Closes the bridge and goes to the row's passage, looked for inside the chapter. */
    private void jump(LensBridge.Row r) {
        dialog.dismiss();
        final int last = Math.max(range.from, range.to - 1);
        final int hint = r.page < 0 ? range.from : Math.max(range.from, Math.min(last, r.page));
        AiJump.toQuote(dc, r.quote.isEmpty() ? null : r.quote, hint, range.from, last);
    }

    // --- the request ---

    private void onAction() {
        if (job != null) {
            return;
        }
        if (bridge == null) {
            map();
        } else {
            AiMenu.confirmRedo(a, R.string.lens_map_again_confirm, this::map);
        }
    }

    /** One request for the chapter: its stored ideas, else its text. */
    private void map() {
        if (job != null || !Assist.LENS.isOn() || !dialog.isShowing()) {
            return;
        }
        error = null;
        job = new LensJob.Bridge(a, target, author, range, this);
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
    public void onDone(LensBridge made) {
        job = null;
        bridge = made;
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
        if (dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    // --- the lens ---

    private void changeLens() {
        if (job != null) {
            return;
        }
        LensSetupScreen.show(a, dc, this::lensChanged);
    }

    /** The lens was saved: the chip and the "another lens" note follow it. */
    private void lensChanged() {
        new Thread(() -> {
            LensProfile p = null;
            LensProfile.Level l = LensProfile.Level.NEW;
            try {
                p = LensStore.profile();
                l = LensStore.level(target.name);
            } catch (Exception e) {
                LOG.e(e);
            }
            final LensProfile saved = p;
            final LensProfile.Level savedLevel = l;
            if (saved == null || Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                profile = saved;
                level = savedLevel;
                if (dialog.isShowing()) {
                    render();
                }
            });
        }, "@T lens").start();
    }
}
