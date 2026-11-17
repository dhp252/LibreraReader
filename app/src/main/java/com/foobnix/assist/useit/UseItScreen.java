package com.foobnix.assist.useit;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSegmented;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * "Use it" for the chapter at the reader's page, full screen (canvas "Use it this month"): the
 * question "Where would this change something you're doing this month?", the reader's answer, and
 * after one request ({@link UseJob}, on a tap) the experiment card: try, what the book predicts,
 * up to three measures, the check-in day, the book's own warning. Every field can be edited, the
 * length chosen (1, 2 or 4 weeks) and each measure's value before the start written down; then
 * "Start experiment" stores it with the book. Nothing is scheduled: a due check-in shows when the
 * app or the book is opened ({@link UseDue}).
 */
final class UseItScreen implements UseJob.Screen {

    private final Activity a;
    private final AiJobs.Target target;
    private final String author;
    /** Runs after an experiment was started and stored (main thread). */
    private final Runnable onStarted;

    private UsePage page;
    private UseJob job;
    private String answer = "";
    private UseExperiment draft;
    private boolean editing;
    private int weeks = UseExperiment.DEFAULT_WEEKS;
    private String error;
    /** "Start experiment" was tapped and the experiment is being stored. */
    private boolean starting;

    private UseItScreen(Activity a, DocumentController dc, Runnable onStarted) {
        this.a = a;
        this.target = AiJobs.Target.of(dc);
        this.author = author(dc);
        this.onStarted = onStarted;
    }

    static void show(Activity a, DocumentController dc, Runnable onStarted) {
        if (!Assist.USE_IT.isOn() || dc == null || dc.getCurrentBook() == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        new UseItScreen(a, dc, onStarted).open();
    }

    private static String author(DocumentController dc) {
        try {
            final com.foobnix.dao2.FileMeta meta = dc.getBookFileMeta();
            return meta == null || meta.getAuthor() == null ? "" : meta.getAuthor().trim();
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    private void open() {
        // the chapter's title when it is known without reading the book; the request finds it otherwise
        final BookChapters.Range known = AiJobs.knownChapter(target);
        page = new UsePage(a, a.getString(R.string.canvas_t23_title),
                known == null || known.title == null ? "" : known.title);
        page.dialog.setOnDismissListener(d -> {
            final UseJob j = job;
            job = null;
            if (j != null) {
                j.cancel();
            }
        });
        render();
        page.dialog.show();
    }

    // ------------------------------------------------------------------ the request

    private void design() {
        if (job != null || answer.trim().isEmpty()) {
            return;
        }
        error = null;
        job = new UseJob(a, target, author, answer.trim(), this);
        job.start();
        render();
    }

    @Override
    public void onChapter(String key, String title) {
        if (page.dialog.isShowing()) {
            page.setSubtitle(title);
        }
    }

    @Override
    public void onDraft(UseExperiment d) {
        job = null;
        draft = d;
        editing = false;
        if (page.dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        error = message;
        if (page.dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (page.dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    // ------------------------------------------------------------------ the screen

    private void render() {
        final LinearLayout content = page.content;
        content.removeAllViews();
        page.actions.removeAllViews();

        final TextView label = UsePage.label(a, a.getString(R.string.canvas_t23_one_idea));
        label.setPadding(dp(4), 0, dp(4), 0);
        page.add(label, 4);
        final TextView question = UsePage.text(a, a.getString(R.string.canvas_t23_question), 22, 500, CanvasLook.ink());
        // the canvas sets the question in the book's serif
        question.setTypeface(Typeface.SERIF);
        question.setLineSpacing(dp(4), 1f);
        question.setPadding(dp(4), 0, dp(4), 0);
        page.add(question, 6);

        final TextView yours = UsePage.text(a, a.getString(R.string.canvas_t23_your_answer), 13, 600, CanvasLook.ink());
        yours.setPadding(dp(4), 0, 0, 0);
        page.add(yours, 14);
        final EditText field = UsePage.field(a, answer, a.getString(R.string.canvas_t23_answer_hint), true);
        page.add(field, 6);

        final TextView design = UsePage.primary(a, a.getString(job != null ? R.string.canvas_t23_designing
                : R.string.canvas_t23_design));
        final TextView again = UsePage.text(a, a.getString(job != null ? R.string.canvas_t23_designing
                : R.string.canvas_t23_design_again), 13, 600, CanvasLook.accent());
        UsePage.enable(design, job == null && !answer.trim().isEmpty());
        UsePage.onText(field, s -> {
            answer = s;
            UsePage.enable(design, job == null && !answer.trim().isEmpty());
            UsePage.enable(again, job == null && !answer.trim().isEmpty());
        });
        design.setOnClickListener(v -> design());

        if (error != null) {
            final TextView e = UsePage.text(a, error, 13, 400, CanvasLook.sensor());
            e.setPadding(dp(4), 0, dp(4), 0);
            page.add(e, 8);
        }

        if (draft == null) {
            final TextView note = UsePage.text(a, a.getString(R.string.canvas_t23_sends), 12, 400, CanvasLook.muted());
            note.setPadding(dp(4), 0, dp(4), 0);
            page.add(note, 8);
            page.actions.addView(design, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            return;
        }

        // another answer makes another experiment, again only on a tap
        again.setGravity(Gravity.CENTER_VERTICAL);
        again.setPadding(dp(4), 0, dp(4), 0);
        again.setMinHeight(CanvasLook.target());
        UsePage.enable(again, job == null && !answer.trim().isEmpty());
        again.setOnClickListener(v -> design());
        final LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        content.addView(again, ap);

        page.add(editing ? editCard() : card(), 6);
        length();
        before();

        final TextView edit = UsePage.secondary(a, a.getString(editing ? R.string.canvas_t23_done_editing : R.string.edit));
        edit.setOnClickListener(v -> {
            if (editing) {
                dropEmptyMeasures();
            }
            editing = !editing;
            render();
        });
        final TextView start = UsePage.primary(a, a.getString(R.string.canvas_t23_start));
        start.setOnClickListener(v -> start());
        page.actionRow(new View[]{edit, start}, new float[]{1, 2});
    }

    /** The experiment as the canvas shows it. */
    private View card() {
        final LinearLayout card = cardBox();
        card.addView(cardHead());
        if (!draft.idea.isEmpty()) {
            card.addView(pair(a.getString(R.string.canvas_t23_idea), draft.idea, 13, 400, CanvasLook.ink2()), gap(12));
        }
        card.addView(pair(a.getString(R.string.canvas_t23_try), draft.tryText, 15, 600, CanvasLook.ink()), gap(12));
        if (!draft.predicts.isEmpty()) {
            card.addView(pair(predictsLabel(), draft.predicts, 14, 400, CanvasLook.ink()), gap(12));
        }
        if (!draft.measures.isEmpty()) {
            final LinearLayout block = new LinearLayout(a);
            block.setOrientation(LinearLayout.VERTICAL);
            block.addView(UsePage.text(a, a.getString(R.string.canvas_t23_measure), 12, 400, CanvasLook.muted()));
            final UsePage.Flow chips = new UsePage.Flow(a, 6);
            for (UseExperiment.Measure m : draft.measures) {
                chips.addView(UsePage.chip(a, m.label()), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            }
            final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = dp(6);
            block.addView(chips, cp);
            card.addView(block, gap(12));
        }
        card.addView(checkinRow(), gap(12));
        final String warning = UsePrompts.warningLine(draft);
        if (!warning.isEmpty()) {
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            final LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(14), dp(14));
            ip.topMargin = dp(2);
            ip.rightMargin = dp(8);
            row.addView(UsePage.icon(a, R.drawable.canvas_t23_ic_warning, CanvasLook.sensor()), ip);
            final TextView w = UsePage.text(a, warning, 12, 400, CanvasLook.sensor());
            w.setLineSpacing(dp(1), 1f);
            row.addView(w, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(row, gap(12));
        }
        return card;
    }

    /** The same card with every text as a field. */
    private View editCard() {
        final LinearLayout card = cardBox();
        card.addView(cardHead());
        card.addView(editPair(a.getString(R.string.canvas_t23_idea), draft.idea, s -> draft.idea = s.trim()), gap(12));
        card.addView(editPair(a.getString(R.string.canvas_t23_try), draft.tryText, s -> draft.tryText = s.trim()), gap(12));
        card.addView(editPair(predictsLabel(), draft.predicts, s -> draft.predicts = s.trim()), gap(12));

        final LinearLayout block = new LinearLayout(a);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(UsePage.text(a, a.getString(R.string.canvas_t23_measure), 12, 400, CanvasLook.muted()));
        for (final UseExperiment.Measure m : draft.measures) {
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            final EditText name = UsePage.field(a, m.name, a.getString(R.string.canvas_t23_measure_name), false);
            UsePage.onText(name, s -> m.name = s.trim());
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final EditText scale = UsePage.field(a, m.scale, a.getString(R.string.canvas_t23_measure_scale), false);
            UsePage.onText(scale, s -> m.scale = s.trim());
            final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.leftMargin = dp(6);
            row.addView(scale, sp);
            final TextView remove = UsePage.text(a, "✕", 16, 400, CanvasLook.muted());
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(CanvasParts.ripple(null));
            remove.setContentDescription(a.getString(R.string.delete));
            remove.setOnClickListener(v -> {
                draft.measures.remove(m);
                render();
            });
            row.addView(remove, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
            final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(6);
            block.addView(row, rp);
        }
        if (draft.measures.size() < UseExperiment.MAX_MEASURES) {
            final TextView add = UsePage.text(a, a.getString(R.string.canvas_t23_add_measure), 13, 600, CanvasLook.accent());
            add.setGravity(Gravity.CENTER_VERTICAL);
            add.setMinHeight(CanvasLook.target());
            add.setOnClickListener(v -> {
                draft.measures.add(new UseExperiment.Measure());
                render();
            });
            block.addView(add);
        }
        card.addView(block, gap(12));
        card.addView(editPair(a.getString(R.string.canvas_t23_warning), draft.warning, s -> draft.warning = s.trim()), gap(12));
        return card;
    }

    private LinearLayout cardBox() {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(UsePage.accentBorder(a, 18));
        return card;
    }

    /** "YOUR 2-WEEK EXPERIMENT" and the flask. */
    private View cardHead() {
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UsePage.label(a, a.getString(R.string.canvas_t23_your_experiment, weeks)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(UsePage.icon(a, R.drawable.canvas_t23_ic_useit, CanvasLook.accent()),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        return head;
    }

    /** "Smith predicts", or "The book predicts" for a book without an author. */
    private String predictsLabel() {
        final String name = UseExperiment.shortAuthor(author);
        return name.isEmpty() ? a.getString(R.string.canvas_t23_book_predicts)
                : a.getString(R.string.canvas_t23_predicts, name);
    }

    private View pair(String name, String value, float sp, int weight, int color) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UsePage.text(a, name, 12, 400, CanvasLook.muted()));
        final TextView v = UsePage.text(a, value, sp, weight, color);
        v.setLineSpacing(dp(2), 1f);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        return box;
    }

    private View editPair(String name, String value, UsePage.Text onChange) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UsePage.text(a, name, 12, 400, CanvasLook.muted()));
        final EditText field = UsePage.field(a, value, "", true);
        field.setMinLines(1);
        UsePage.onText(field, onChange);
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(4);
        box.addView(field, p);
        return box;
    }

    /** "Check-in on Mon, Nov 9": today plus the chosen length. */
    private View checkinRow() {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(12)));
        final LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(18), dp(18));
        ip.rightMargin = dp(10);
        row.addView(UsePage.icon(a, R.drawable.canvas_t23_ic_calendar, CanvasLook.ink()), ip);
        final String day = UsePage.dayLong(UseExperiment.checkDay(UseStore.today(), weeks));
        final String line = a.getString(R.string.canvas_t23_checkin_on, day);
        final SpannableStringBuilder s = new SpannableStringBuilder(line);
        final int at = line.indexOf(day);
        if (at >= 0) {
            s.setSpan(new StyleSpan(Typeface.BOLD), at, at + day.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final TextView t = UsePage.text(a, s, 13, 400, CanvasLook.ink());
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /** 1 week / 2 weeks / 4 weeks. */
    private void length() {
        final TextView h = UsePage.text(a, a.getString(R.string.canvas_t23_how_long), 13, 600, CanvasLook.ink());
        h.setPadding(dp(4), 0, 0, 0);
        page.add(h, 16);
        final CanvasSegmented lengths = CanvasSegmented.create(a, a.getString(R.string.canvas_t23_week_1),
                a.getString(R.string.canvas_t23_weeks_2), a.getString(R.string.canvas_t23_weeks_4));
        for (int i = 0; i < UseExperiment.WEEKS.length; i++) {
            if (UseExperiment.WEEKS[i] == weeks) {
                lengths.select(i);
            }
        }
        lengths.setOnSelectListener(index -> {
            weeks = UseExperiment.WEEKS[index];
            render();
        });
        page.add(lengths.getView(), 6);
    }

    /** Each measure's value now, and whether higher is better. */
    private void before() {
        if (draft.measures.isEmpty()) {
            return;
        }
        final TextView h = UsePage.text(a, a.getString(R.string.canvas_t23_now), 13, 600, CanvasLook.ink());
        h.setPadding(dp(4), 0, 0, 0);
        page.add(h, 16);
        final TextView note = UsePage.text(a, a.getString(R.string.canvas_t23_now_note), 12, 400, CanvasLook.muted());
        note.setPadding(dp(4), 0, dp(4), 0);
        page.add(note, 2);
        for (final UseExperiment.Measure m : draft.measures) {
            page.add(measureRow(a, m, true, null), 8);
        }
    }

    /**
     * One measure's row: its name, the "Higher is better" / "Lower is better" chip (a tap turns
     * it), and a field for its value before ({@code before}) or during.
     */
    static View measureRow(Activity a, UseExperiment.Measure m, boolean before, Runnable onChange) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView name = UsePage.text(a, m.label().isEmpty() ? a.getString(R.string.canvas_t23_measure) : m.label(),
                13, 400, CanvasLook.ink());
        name.setPadding(dp(4), 0, 0, dp(4));
        texts.addView(name);
        final TextView direction = UsePage.chip(a, "");
        final Runnable show = () -> direction.setText(m.higherBetter ? R.string.canvas_t23_higher_better
                : R.string.canvas_t23_lower_better);
        show.run();
        direction.setOnClickListener(v -> {
            m.higherBetter = !m.higherBetter;
            show.run();
            if (onChange != null) {
                onChange.run();
            }
        });
        texts.addView(direction, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final EditText value = UsePage.number(a, before ? m.before : m.during,
                a.getString(before ? R.string.canvas_t23_before : R.string.canvas_t23_during));
        UsePage.onText(value, s -> {
            if (before) {
                m.before = UseExperiment.parseNumber(s);
            } else {
                m.during = UseExperiment.parseNumber(s);
            }
            if (onChange != null) {
                onChange.run();
            }
        });
        final LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(84), CanvasLook.target());
        vp.leftMargin = dp(8);
        row.addView(value, vp);
        return row;
    }

    private void dropEmptyMeasures() {
        for (int i = draft.measures.size() - 1; i >= 0; i--) {
            if (draft.measures.get(i).name.trim().isEmpty()) {
                draft.measures.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ starting

    private void start() {
        if (starting) {
            return;
        }
        dropEmptyMeasures();
        if (draft.tryText.trim().isEmpty()) {
            Toast.makeText(a, R.string.canvas_t23_need_try, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Assist.USE_IT.isOn()) {
            page.dialog.dismiss();
            return;
        }
        starting = true;
        final UseExperiment e = draft;
        e.answer = answer.trim();
        e.id = UseExperiment.newId(System.currentTimeMillis(), new java.util.Random().nextInt());
        e.start(UseStore.today(), weeks);
        final String bookName = target.name;
        UseStore.IO.execute(() -> {
            String failure = null;
            try {
                UseStore.save(bookName, e);
            } catch (Exception ex) {
                LOG.e(ex);
                failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
            final String failed = failure;
            a.runOnUiThread(() -> {
                starting = false;
                if (Apps.isDestroyedActivity(a)) {
                    return;
                }
                if (failed != null) {
                    e.id = "";
                    Toast.makeText(a, a.getString(R.string.canvas_t23_not_saved, failed), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(a, a.getString(R.string.canvas_t23_started, UsePage.dayLong(e.checkDay)),
                        Toast.LENGTH_LONG).show();
                page.dialog.dismiss();
                if (onStarted != null) {
                    onStarted.run();
                }
            });
        });
    }

    private static LinearLayout.LayoutParams gap(int topDp) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
