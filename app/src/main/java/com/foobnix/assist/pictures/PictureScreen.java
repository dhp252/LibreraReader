package com.foobnix.assist.pictures;

import android.app.Activity;
import android.app.Dialog;
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
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.updated.UpdatedChapter;
import com.foobnix.assist.updated.UpdatedNote;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSegmented;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.List;

/**
 * "Prose to pictures" (canvas Picture, PictureTable, PictureLoop): the chapter at the reader's
 * page as up to three pictures, a tab for each kind it has. A stored set shows at once; else the
 * screen says what will be sent and waits for "Draw this chapter", which asks the AI once. Every
 * picture stands under the book's words it is drawn from ("FROM THE TEXT", a tap goes there).
 * "Draw again" asks first. The same screen draws one selected passage, which is not stored.
 * When the same answer brought a small model (canvas PictureSim), it gets a last tab, "Try it".
 */
public final class PictureScreen implements PictureJob.Screen {

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    /** The chapter, or null for a selected passage. */
    private final BookChapters.Range range;
    private final String passage;
    /** The stored key ideas a chapter's request sends; 0 when it sends the chapter's text. */
    private final int ideas;
    /** The first note of "The book, updated" stored for the chapter, or null. */
    private final String updated;
    private final int updatedSince;
    private PictureSet set;

    private Dialog dialog;
    private LinearLayout tabs;
    private LinearLayout body;
    private ScrollView scroll;
    private ProgressBar progress;
    private TextView status;
    private TextView action;
    private String error;
    private PictureJob job;
    private int tab;

    private PictureScreen(Activity a, DocumentController dc, AiJobs.Target target, BookChapters.Range range, String passage,
                          int ideas, PictureSet set, String updated, int updatedSince) {
        this.a = a;
        this.dc = dc;
        this.target = target;
        this.range = range;
        this.passage = passage;
        this.ideas = ideas;
        this.set = set;
        this.updated = updated;
        this.updatedSince = updatedSince;
    }

    /** The ✦ menu's "As a picture": the pictures of the chapter at the reader's page. */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.PICTURES.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        new Thread(() -> {
            BookChapters.Range range = null;
            PictureSet stored = null;
            int ideas = 0;
            String note = null;
            int since = 0;
            try {
                range = AiJobs.chapterAt(t);
                if (range != null) {
                    stored = PictureChapter.load(t.name, range.key);
                    ideas = PictureChapter.ideas(AiStore.get().load(t.name).chapter(range.key)).size();
                    if (Assist.UPDATED.isOn()) {
                        // the canvas's footnote: a note already stored for this chapter, nothing is asked
                        final UpdatedChapter u = UpdatedChapter.fromJson(AssistStore.get().item(t.name, UpdatedChapter.key(range.key)));
                        if (u != null && !u.notes.isEmpty()) {
                            final UpdatedNote n = u.notes.get(0);
                            note = TextUtils.isEmpty(n.title) ? n.body : n.title;
                            since = u.firstPublished;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final BookChapters.Range r = range;
            final PictureSet s = stored;
            final int count = ideas;
            final String updated = TextUtils.isEmpty(note) ? null : note;
            final int year = since;
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (r == null) {
                    Toast.makeText(a, R.string.ai_cannot_open, Toast.LENGTH_LONG).show();
                } else {
                    new PictureScreen(a, dc, t, r, null, count, s, updated, year).open();
                }
            });
        }, "@T pictures").start();
    }

    /** The pictures of one selected passage: asked at once (the tap on its chip is the request), not stored. */
    static void showPassage(Activity a, DocumentController dc, String selected) {
        final String passage = PicturePrompts.passage(selected);
        if (!Assist.PICTURES.isOn() || a == null || dc == null || dc.getCurrentBook() == null || passage.isEmpty()) {
            return;
        }
        final PictureScreen screen = new PictureScreen(a, dc, AiJobs.Target.of(dc), null, passage, 0, null, null, 0);
        screen.open();
        screen.draw();
    }

    private void open() {
        final LinearLayout page = PictureUi.column(a);
        dialog = PictureUi.fullScreen(a, page);

        final ImageView text = new ImageView(a);
        text.setImageResource(R.drawable.canvas_t27_ic_text);
        text.setColorFilter(CanvasLook.ink());
        text.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final int inset = CanvasLook.dp(11);
        text.setPadding(inset, inset, inset, inset);
        text.setContentDescription(a.getString(R.string.canvas_t27_show_text));
        text.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        text.setOnClickListener(v -> showText());
        // titles as the book has them
        final String sub = range == null ? a.getString(R.string.canvas_t27_sub_passage)
                : TextUtils.isEmpty(range.title) ? a.getString(R.string.canvas_t27_as_picture)
                : a.getString(R.string.canvas_t27_sub, range.title.trim());
        page.addView(PictureUi.topBar(a, target.title, sub, a.getString(R.string.canvas_t27_back), () -> dialog.dismiss(), text),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));

        tabs = PictureUi.column(a);
        page.addView(tabs);

        scroll = new ScrollView(a);
        body = PictureUi.column(a);
        body.setPadding(0, 0, 0, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final LinearLayout footer = PictureUi.column(a);
        footer.setPadding(CanvasLook.dp(16), CanvasLook.dp(8), CanvasLook.dp(16), CanvasLook.dp(14));
        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(a);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        status = PictureUi.text(a, "", CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        footer.addView(line);
        action = PictureUi.button(a, "", true);
        action.setOnClickListener(v -> onAction());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(48));
        lp.topMargin = CanvasLook.dp(8);
        footer.addView(action, lp);
        page.addView(footer);

        dialog.setOnDismissListener(d -> stop());
        render();
        dialog.show();
    }

    // ------------------------------------------------------------------ drawing the screen

    /** The names of the tabs: one for each kind the set has. */
    private List<String> tabNames() {
        final List<String> names = new ArrayList<>();
        if (set != null) {
            for (Picture p : set.pictures) {
                names.add(a.getString(kindName(p.kind)));
            }
            if (set.sim != null) {
                names.add(a.getString(R.string.canvas_t28_tab));
            }
        }
        return names;
    }

    static int kindName(Picture.Kind kind) {
        switch (kind) {
            case PROCESS:
                return R.string.canvas_t27_kind_process;
            case TABLE:
                return R.string.canvas_t27_kind_table;
            default:
                return R.string.canvas_t27_kind_loop;
        }
    }

    private void render() {
        final boolean running = job != null;
        final List<String> names = tabNames();
        tab = Math.max(0, Math.min(tab, names.size() - 1));
        tabs.removeAllViews();
        if (names.size() > 1) {
            final CanvasSegmented segmented = CanvasSegmented.create(a, names);
            segmented.select(tab);
            segmented.setOnSelectListener(index -> {
                tab = index;
                renderBody();
                scroll.scrollTo(0, 0);
            });
            final ViewGroup.MarginLayoutParams lp = CanvasSegmented.fullWidth();
            lp.topMargin = CanvasLook.dp(4);
            lp.bottomMargin = CanvasLook.dp(4);
            tabs.addView(segmented.getView(), lp);
        }
        renderBody();

        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        status.setPadding(CanvasLook.dp(running ? 6 : 0), 0, 0, 0);
        if (running) {
            status.setText(R.string.canvas_t27_drawing);
        } else if (error != null) {
            status.setText(error);
        } else if (set != null && !set.isEmpty()) {
            status.setText(drawnFrom());
        } else {
            status.setText("");
        }
        status.setVisibility(status.length() > 0 ? View.VISIBLE : View.GONE);
        action.setText(set != null ? R.string.canvas_t27_draw_again
                : range == null ? R.string.canvas_t27_draw_passage : R.string.canvas_t27_draw);
        PictureUi.style(action, set == null);
        PictureUi.enable(action, !running);
    }

    /** What the pictures were drawn from, and what was left out of the answer. */
    private String drawnFrom() {
        final StringBuilder s = new StringBuilder(a.getString(range == null ? R.string.canvas_t27_from_passage
                : set.fromIdeas ? R.string.canvas_t27_from_ideas : R.string.canvas_t27_from_chapter));
        if (set.leftOut > 0) {
            s.append(' ').append(a.getResources().getQuantityString(R.plurals.canvas_t27_left_out, set.leftOut, set.leftOut));
        }
        if (set.numbersOut > 0) {
            s.append(' ').append(a.getResources().getQuantityString(R.plurals.canvas_t27_numbers_out, set.numbersOut, set.numbersOut));
        }
        return s.toString();
    }

    private void renderBody() {
        body.removeAllViews();
        if (set == null) {
            renderIntro();
        } else if (set.isEmpty()) {
            renderNone();
        } else {
            renderPage(tab);
        }
    }

    /** Before the request: what will be sent, and what comes back. */
    private void renderIntro() {
        body.addView(PictureViews.heading(a, a.getString(range == null ? R.string.canvas_t27_intro_title_passage
                : R.string.canvas_t27_intro_title)), PictureUi.row(20, 12, 20, 0));
        final String sends = range == null ? a.getString(R.string.canvas_t27_sends_passage, passage.length())
                : ideas > 0 ? a.getResources().getQuantityString(R.plurals.canvas_t27_sends_ideas, ideas, ideas)
                : a.getString(R.string.canvas_t27_sends_text);
        final TextView what = PictureUi.text(a, sends, 14, 400, CanvasLook.ink());
        what.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(what, PictureUi.row(20, 10, 20, 0));
        final TextView rules = PictureUi.text(a, a.getString(R.string.canvas_t27_intro_rules), 13, 400, CanvasLook.muted());
        rules.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(rules, PictureUi.row(20, 10, 20, 0));
        final TextView model = PictureUi.text(a, a.getString(R.string.canvas_t28_intro), 13, 400, CanvasLook.muted());
        model.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(model, PictureUi.row(20, 10, 20, 0));
    }

    /** The answer held no picture that the text carries. */
    private void renderNone() {
        body.addView(PictureViews.heading(a, a.getString(range == null ? R.string.canvas_t27_none_title_passage
                : R.string.canvas_t27_none_title)), PictureUi.row(20, 12, 20, 0));
        final StringBuilder why = new StringBuilder(a.getString(R.string.canvas_t27_none));
        if (set.leftOut > 0) {
            why.append(' ').append(a.getResources().getQuantityString(R.plurals.canvas_t27_left_out, set.leftOut, set.leftOut));
        }
        final TextView text = PictureUi.text(a, why, 14, 400, CanvasLook.ink2());
        text.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(text, PictureUi.row(20, 10, 20, 0));
    }

    /** One tab: the book's words, the title, the picture, what it adds up to, why it works. */
    private void renderPage(int index) {
        if (index >= set.pictures.size()) {
            // the last tab: the small model, worked out on the device as the reader plays
            final SimModel sim = set.sim;
            body.addView(PictureViews.fromTheText(a, sim.quote, sim.page, () -> jump(sim.quote, sim.page)));
            body.addView(SimBlock.build(a, sim));
            return;
        }
        final Picture p = set.pictures.get(index);
        body.addView(PictureViews.fromTheText(a, p.quote, p.page, () -> jump(p.quote, p.page)));
        body.addView(PictureViews.heading(a, p.title.isEmpty() ? a.getString(kindName(p.kind)) : p.title),
                PictureUi.row(16, 8, 16, 0));
        switch (p.kind) {
            case PROCESS:
                body.addView(PictureViews.process(a, p), PictureUi.row(16, 10, 16, 0));
                break;
            case TABLE:
                final int width = a.getResources().getDisplayMetrics().widthPixels - 2 * CanvasLook.dp(CanvasLook.GUTTER_DP);
                body.addView(PictureViews.table(a, p, width), PictureUi.row(16, 10, 16, 0));
                if (!p.point.isEmpty()) {
                    body.addView(PictureViews.point(a, p.point), PictureUi.row(16, 16, 16, 0));
                }
                break;
            default:
                body.addView(PictureViews.loop(a, p), PictureUi.row(16, 16, 16, 0));
                if (p.hasHolds()) {
                    body.addView(PictureViews.holdsBack(a, p), PictureUi.row(16, 18, 16, 0));
                }
                break;
        }
        if (!p.cards.isEmpty()) {
            body.addView(PictureViews.heading(a, p.cardsTitle.isEmpty() ? a.getString(R.string.canvas_t27_why) : p.cardsTitle),
                    PictureUi.row(16, 16, 16, 0));
            body.addView(PictureViews.whyCards(a, p.cards), PictureUi.row(16, 10, 16, 0));
        }
        if (updated != null) {
            final String label = updatedSince != 0 ? a.getString(R.string.canvas_t27_updated_since, String.valueOf(updatedSince))
                    : a.getString(R.string.canvas_t27_updated);
            body.addView(PictureViews.footnote(a, label, updated, this::openUpdated), PictureUi.row(16, 14, 16, 0));
        }
    }

    // ------------------------------------------------------------------ taps

    /** Closes the screen and goes to the passage, looked for inside the chapter. */
    private void jump(String quote, int page) {
        dialog.dismiss();
        if (range == null) {
            AiJump.toQuote(dc, quote, page, -1, -1);
            return;
        }
        final int last = Math.max(range.from, range.to - 1);
        final int hint = page < 0 ? range.from : Math.max(range.from, Math.min(last, page));
        AiJump.toQuote(dc, quote, hint, range.from, last);
    }

    /** The top bar's "show the text": to the passage of the picture shown, else back to the page. */
    private void showText() {
        if (set != null && !set.isEmpty() && tab < set.pictures.size()) {
            final Picture p = set.pictures.get(tab);
            jump(p.quote, p.page);
        } else if (set != null && set.sim != null) {
            jump(set.sim.quote, set.sim.page);
        } else {
            dialog.dismiss();
        }
    }

    /** The footnote's tap: the chapter's notes of "The book, updated", over this screen. */
    private void openUpdated() {
        if (job == null && Assist.UPDATED.isOn()) {
            com.foobnix.assist.updated.UpdatedScreen.showHere(a, dc);
        }
    }

    private void onAction() {
        if (job != null) {
            return;
        }
        if (set == null) {
            draw();
        } else {
            AiMenu.confirmRedo(a, range == null ? R.string.canvas_t27_again_confirm_passage : R.string.canvas_t27_again_confirm,
                    this::draw);
        }
    }

    /** One request: the chapter's stored ideas, else its text; or the passage. */
    private void draw() {
        if (job != null || !Assist.PICTURES.isOn() || !dialog.isShowing()) {
            return;
        }
        error = null;
        job = range == null ? new PictureJob(a, target, passage, target.page, this) : new PictureJob(a, target, range, this);
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
    public void onDone(PictureSet drawn) {
        job = null;
        set = drawn;
        tab = 0;
        if (dialog.isShowing()) {
            render();
            scroll.scrollTo(0, 0);
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
}
