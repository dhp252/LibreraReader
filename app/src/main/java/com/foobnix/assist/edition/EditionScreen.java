package com.foobnix.assist.edition;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.SafeArea;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The cover and contents of "Your edition", full screen (canvas "Your edition"): the cover (the
 * book's title, "in n pages, in your own words", "FROM n CHAPTERS · YOUR RECALLS, MISSES, LINKS",
 * all counted from what is there), the contents with each section's pages, a sample of the
 * reader's own words, "Tidy my words" (the one optional AI request, on a tap, and what it sends),
 * "Read it" and "Export PDF", and when the next refresher is. A book without anything of the
 * reader's shows what feeds an edition instead. Opening it stores the day of the first open and
 * of the next refresher in {@code edition:book}; nothing is scheduled.
 */
final class EditionScreen implements EditionJob.Screen {

    private final Activity a;
    private final AiJobs.Target target;
    private EditionData data;
    private EditionJob job;
    private String tidyNote = "";

    private Dialog dialog;
    private TextView subtitle;
    private View export;
    private LinearLayout body;
    private LinearLayout foot;

    private EditionScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.target = AiJobs.Target.of(dc);
    }

    static void show(Activity a, DocumentController dc) {
        if (!Assist.EDITION.isOn() || a == null || dc == null || dc.getCurrentBook() == null
                || Apps.isDestroyedActivity(a)) {
            return;
        }
        new EditionScreen(a, dc).open();
    }

    private void open() {
        final boolean dark = CanvasLook.isNight();
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = column(a);
        page.setBackgroundColor(CanvasLook.bg());

        final TextView[] sub = new TextView[1];
        final View[] exportButton = new View[1];
        page.addView(bar(a, a.getString(R.string.canvas_t26_title), sub, exportButton, dialog::dismiss, this::export),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));
        subtitle = sub[0];
        export = exportButton[0];
        export.setVisibility(View.INVISIBLE);
        setSubtitle(target.title);

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        body = column(a);
        body.setPadding(0, 0, 0, CanvasLook.dp(12));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        foot = column(a);
        foot.setPadding(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(16));
        page.addView(foot);

        dialog.setContentView(page);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> {
            if (job != null) {
                job.cancel();
                job = null;
            }
        });
        message(a.getString(R.string.canvas_t26_loading));
        dialog.show();
        load(true);
    }

    /** Reads the edition; {@code opening} also stores that it was opened (the first open, a refresher). */
    private void load(boolean opening) {
        EditionData.IO.execute(() -> {
            EditionData found = null;
            try {
                EditionData d = EditionData.load(a, target, true);
                if (opening && !d.model.isEmpty()) {
                    final String today = d.today;
                    // the state as it is now, with the day of the next refresher
                    d = d.with(EditionData.update(target.name, s -> s.open(today)));
                }
                found = d;
            } catch (Exception e) {
                LOG.e(e);
            }
            final EditionData loaded = found;
            ui(() -> {
                data = loaded;
                render();
            });
        });
    }

    // ------------------------------------------------------------------ the screen

    private void render() {
        body.removeAllViews();
        foot.removeAllViews();
        if (data == null) {
            message(a.getString(R.string.canvas_t26_cannot_read));
            return;
        }
        final EditionModel m = data.model;
        if (m.isEmpty()) {
            export.setVisibility(View.INVISIBLE);
            empty();
            return;
        }
        export.setVisibility(View.VISIBLE);
        setSubtitle(data.state.finished.isEmpty() ? target.title
                : a.getString(R.string.canvas_t26_finished_subtitle, target.title));
        add(cover(m), 16, 12, 16);
        contents(m);
        if (!m.sampleText.isEmpty()) {
            add(sample(m), 16, 14, 16);
        }
        if (!m.raws.isEmpty()) {
            add(tidy(m), 16, 14, 16);
        }

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView read = EditionLook.button(a, a.getString(R.string.canvas_t26_read_it_minutes, m.minutes()), true);
        read.setBackground(CanvasParts.round(CanvasLook.accent(), 0, CanvasLook.dp(14)));
        read.setForeground(CanvasParts.ripple(null));
        read.setOnClickListener(v -> EditionPageScreen.show(a, data, 1, this::export));
        buttons.addView(read, new LinearLayout.LayoutParams(0, CanvasLook.dp(52), 1));
        final TextView pdf = EditionLook.button(a, a.getString(R.string.canvas_t26_export_pdf), false);
        pdf.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.dp(14))));
        pdf.setOnClickListener(v -> export());
        final LinearLayout.LayoutParams pdfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                CanvasLook.dp(52));
        pdfLp.leftMargin = CanvasLook.dp(8);
        buttons.addView(pdf, pdfLp);
        foot.addView(buttons);
        final String next = refresherLine();
        if (!next.isEmpty()) {
            final TextView t = EditionLook.text(a, next, 12, 400, CanvasLook.muted());
            t.setGravity(Gravity.CENTER);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = CanvasLook.dp(10);
            foot.addView(t, lp);
        }
    }

    /** "Next refresher in 6 weeks · Dec 29." The day is stored; the app looks at it when the book is opened. */
    private String refresherLine() {
        final String day = data.state.next;
        final String date = EditionDates.shortDay(day, data.today, data.words.months);
        if (date.isEmpty()) {
            return "";
        }
        final EditionDates.Until until = EditionDates.until(data.today, day);
        final String when;
        switch (until.unit) {
            case WEEKS:
                when = a.getString(R.string.canvas_t26_next_weeks, until.n);
                break;
            case DAYS:
                when = a.getString(R.string.canvas_t26_next_days, until.n);
                break;
            case TOMORROW:
                when = a.getString(R.string.canvas_t26_next_tomorrow);
                break;
            default:
                return a.getString(R.string.canvas_t26_next_now);
        }
        return a.getString(R.string.canvas_t26_next_on, when, date);
    }

    /** The cover: the canvas's dark card on a stack of two sheets. */
    private View cover(EditionModel m) {
        final FrameLayout stack = new FrameLayout(a);
        final boolean night = CanvasLook.isNight();
        final View back = new View(a);
        back.setBackground(CanvasParts.round(night ? CanvasLook.LINE_N : 0xFFE9EBEF, 0, CanvasLook.dp(14)));
        final FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        backLp.setMargins(CanvasLook.dp(16), CanvasLook.dp(12), 0, 0);
        stack.addView(back, backLp);
        final View middle = new View(a);
        middle.setBackground(CanvasParts.round(night ? CanvasLook.LINE_STRONG_N : 0xFFC9CDD5, 0, CanvasLook.dp(14)));
        final FrameLayout.LayoutParams middleLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        middleLp.setMargins(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        stack.addView(middle, middleLp);

        final LinearLayout front = column(a);
        front.setBackground(CanvasParts.round(EditionLook.coverFill(), 0, CanvasLook.dp(14)));
        front.setPadding(CanvasLook.dp(20), CanvasLook.dp(18), CanvasLook.dp(20), CanvasLook.dp(18));
        front.setMinimumHeight(CanvasLook.dp(160));
        front.addView(EditionLook.mono(a, a.getString(R.string.canvas_t26_title), 11, CanvasLook.ACCENT_N));
        final TextView title = EditionLook.book(a, m.bookTitle, 26, 0, EditionLook.coverText());
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, CanvasLook.dp(6), 0, CanvasLook.dp(6));
        front.addView(title);
        final int pages = data.pages.size();
        front.addView(EditionLook.text(a, a.getResources().getQuantityString(R.plurals.canvas_t26_in_pages, pages, pages),
                15, 400, EditionLook.coverSoft()));
        final View gap = new View(a);
        front.addView(gap, new LinearLayout.LayoutParams(1, 0, 1));
        final String from = a.getResources().getQuantityString(R.plurals.canvas_t26_from_chapters, m.chapters, m.chapters);
        final TextView made = EditionLook.mono(a, m.kinds.isEmpty() ? from
                : a.getString(R.string.canvas_t26_from_your, from, TextUtils.join(", ", m.kinds)), 11, EditionLook.coverMuted());
        made.setLetterSpacing(0.04f);
        made.setPadding(0, CanvasLook.dp(14), 0, 0);
        front.addView(made);
        final FrameLayout.LayoutParams frontLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        frontLp.setMargins(0, 0, CanvasLook.dp(16), CanvasLook.dp(12));
        stack.addView(front, frontLp);
        return stack;
    }

    /** "CONTENTS": each section with its number and its pages; a tap opens it. */
    private void contents(EditionModel m) {
        final TextView label = EditionLook.mono(a, a.getString(R.string.canvas_t26_contents), 11, CanvasLook.muted());
        add(label, 20, 16, 20);
        for (int i = 0; i < m.sections.size(); i++) {
            final int section = i;
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(CanvasLook.target());
            row.setPadding(CanvasLook.dp(20), 0, CanvasLook.dp(20), 0);
            row.setBackground(CanvasParts.ripple(null));
            final TextView number = EditionLook.mono(a, String.valueOf(i + 1), 12, CanvasLook.muted());
            row.addView(number, new LinearLayout.LayoutParams(CanvasLook.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(EditionLook.text(a, m.sections.get(i).title, 14, 400, CanvasLook.ink()),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final int count = data.pages.pageCount(i);
            final TextView pages = EditionLook.mono(a,
                    a.getResources().getQuantityString(R.plurals.canvas_t26_pages_short, count, count), 12, CanvasLook.muted());
            pages.setAllCaps(false);
            row.addView(pages);
            row.setOnClickListener(v -> EditionPageScreen.show(a, data, data.pages.firstPage(section), this::export));
            body.addView(row);
            if (i + 1 < m.sections.size()) {
                final View rule = new View(a);
                rule.setBackgroundColor(CanvasLook.surface2());
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, CanvasLook.dp(1)));
                lp.leftMargin = CanvasLook.dp(20);
                lp.rightMargin = CanvasLook.dp(20);
                body.addView(rule, lp);
            }
        }
    }

    /** A page's corner: the first chapter's own words, and how its first miss went since. */
    private View sample(EditionModel m) {
        final LinearLayout card = column(a);
        card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(6))));
        card.setElevation(CanvasLook.dp(3));
        final int p = CanvasLook.dp(16);
        card.setPadding(p, p, p, p);
        final int page = Math.max(1, data.pages.firstPageOf(EditionModel.Kind.WORDS));
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        final TextView what = EditionLook.mono(a, m.sampleChapter.isEmpty() ? data.words.labelWords
                : data.words.labelWords + " · " + m.sampleChapter, 11, CanvasLook.muted());
        what.setSingleLine(true);
        what.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView number = EditionLook.mono(a, a.getString(R.string.canvas_t26_page_short, page), 11, CanvasLook.muted());
        number.setPadding(CanvasLook.dp(8), 0, 0, 0);
        head.addView(number);
        card.addView(head);
        final TextView text = EditionLook.book(a, m.sampleText, 15, 0, CanvasLook.ink());
        text.setPadding(0, CanvasLook.dp(10), 0, 0);
        card.addView(text);
        if (!m.sampleNote.isEmpty()) {
            final LinearLayout note = new LinearLayout(a);
            note.setOrientation(LinearLayout.HORIZONTAL);
            note.setGravity(Gravity.CENTER_VERTICAL);
            note.setPadding(0, CanvasLook.dp(10), 0, 0);
            final View dot = new View(a);
            dot.setBackground(CanvasParts.round(EditionLook.WARN_DOT, 0, CanvasLook.dp(3)));
            final LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(CanvasLook.dp(6), CanvasLook.dp(6));
            dotLp.rightMargin = CanvasLook.dp(6);
            note.addView(dot, dotLp);
            final SpannableString s = new SpannableString(m.sampleNote);
            final int lead = Math.min(m.sampleNoteLead.length(), s.length());
            if (lead > 0) {
                s.setSpan(new StyleSpan(Typeface.BOLD), 0, lead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new ForegroundColorSpan(CanvasLook.sensor()), 0, lead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            note.addView(EditionLook.text(a, s, 12, 400, CanvasLook.ink2()),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(note);
        }
        card.setOnClickListener(v -> EditionPageScreen.show(a, data, page, this::export));
        return card;
    }

    // ------------------------------------------------------------------ tidy my words

    /** The one optional AI request: what it sends, the button, which chapters are tidied, and the way back. */
    private View tidy(EditionModel m) {
        final LinearLayout box = column(a);
        box.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.radius()));
        final int p = CanvasLook.dp(14);
        box.setPadding(p, p, p, p);
        box.addView(EditionLook.mono(a, a.getString(R.string.canvas_t26_tidy), 11, CanvasLook.accent()));
        final int chapters = m.raws.size();
        final TextView sends = EditionLook.text(a,
                a.getResources().getQuantityString(R.plurals.canvas_t26_tidy_sends, chapters, chapters), 13, 400,
                CanvasLook.ink2());
        sends.setLineSpacing(CanvasLook.dp(2), 1f);
        sends.setPadding(0, CanvasLook.dp(6), 0, 0);
        box.addView(sends);
        if (!m.tidied.isEmpty()) {
            final TextView done = EditionLook.text(a, a.getString(m.showsRaw ? R.string.canvas_t26_tidied_hidden
                    : R.string.canvas_t26_tidied_shown, m.tidied.size(), chapters, TextUtils.join(" · ", m.tidied)),
                    13, 600, CanvasLook.ink());
            done.setLineSpacing(CanvasLook.dp(2), 1f);
            done.setPadding(0, CanvasLook.dp(8), 0, 0);
            box.addView(done);
        }
        if (!tidyNote.isEmpty()) {
            final TextView note = EditionLook.text(a, tidyNote, 12, 400, CanvasLook.sensor());
            note.setPadding(0, CanvasLook.dp(6), 0, 0);
            box.addView(note);
        }
        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        final boolean busy = job != null;
        final TextView go = EditionLook.text(a, a.getString(busy ? R.string.canvas_t26_tidying
                : m.tidied.size() >= chapters ? R.string.canvas_t26_tidy_again : R.string.canvas_t26_tidy), 14, 600,
                CanvasLook.ink());
        go.setGravity(Gravity.CENTER);
        go.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        go.setBackground(CanvasParts.secondaryButton());
        go.setEnabled(!busy);
        go.setAlpha(busy ? 0.4f : 1f);
        go.setOnClickListener(v -> startTidy());
        buttons.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        if (!m.tidied.isEmpty() && !busy) {
            final TextView back = EditionLook.text(a, a.getString(m.showsRaw ? R.string.canvas_t26_show_tidied
                    : R.string.canvas_t26_show_raw), 14, 600, CanvasLook.accent());
            back.setGravity(Gravity.CENTER);
            back.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
            back.setBackground(CanvasParts.ripple(null));
            final boolean raw = !m.showsRaw;
            back.setOnClickListener(v -> showRaw(raw));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.target());
            lp.leftMargin = CanvasLook.dp(4);
            buttons.addView(back, lp);
        }
        final LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = CanvasLook.dp(10);
        box.addView(buttons, buttonsLp);
        return box;
    }

    private void startTidy() {
        if (job != null || data == null || data.model.raws.isEmpty() || !Assist.EDITION.isOn()) {
            return;
        }
        tidyNote = "";
        job = new EditionJob(a, target, data.model.raws, this);
        job.start();
        render();
    }

    /** The reader's raw words, or the tidied passages again; the passages stay stored either way. */
    private void showRaw(boolean raw) {
        EditionData.IO.execute(() -> EditionData.update(target.name, s -> {
            final boolean change = s.raw != raw;
            s.raw = raw;
            return change;
        }));
        load(false);
    }

    @Override
    public void onTidied(int kept, int changedTooMuch, int asked) {
        job = null;
        tidyNote = changedTooMuch > 0 ? a.getResources().getQuantityString(R.plurals.canvas_t26_tidy_rejected,
                changedTooMuch, changedTooMuch) : asked < data.model.raws.size()
                ? a.getString(R.string.canvas_t26_tidy_part, asked, data.model.raws.size()) : "";
        // tidied words are what the reader asked to see
        EditionData.IO.execute(() -> EditionData.update(target.name, s -> {
            final boolean change = s.raw;
            s.raw = false;
            return change;
        }));
        load(false);
    }

    @Override
    public void onError(String message) {
        job = null;
        tidyNote = message == null ? "" : message;
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

    // ------------------------------------------------------------------ nothing yet

    /** No material: what feeds an edition, instead of empty pages. */
    private void empty() {
        final TextView title = EditionLook.book(a, a.getString(R.string.canvas_t26_empty_title), 22, 2, CanvasLook.ink());
        add(title, 20, 20, 20);
        final TextView text = EditionLook.text(a, a.getString(R.string.canvas_t26_empty_text), 14, 400, CanvasLook.ink2());
        text.setLineSpacing(CanvasLook.dp(3), 1f);
        add(text, 20, 8, 20);
        final int[][] feeds = {
                {R.string.canvas_t26_feed_recall, R.string.canvas_t26_feed_recall_text},
                {R.string.canvas_t26_feed_teach, R.string.canvas_t26_feed_teach_text},
                {R.string.canvas_t26_feed_use, R.string.canvas_t26_feed_use_text},
                {R.string.canvas_t26_feed_debate, R.string.canvas_t26_feed_debate_text},
                {R.string.canvas_t26_feed_review, R.string.canvas_t26_feed_review_text}};
        for (int[] feed : feeds) {
            final LinearLayout row = column(a);
            row.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.radius()));
            final int p = CanvasLook.dp(14);
            row.setPadding(p, CanvasLook.dp(12), p, CanvasLook.dp(12));
            row.addView(EditionLook.text(a, a.getString(feed[0]), 14, 600, CanvasLook.ink()));
            final TextView line = EditionLook.text(a, a.getString(feed[1]), 13, 400, CanvasLook.ink2());
            line.setLineSpacing(CanvasLook.dp(2), 1f);
            line.setPadding(0, CanvasLook.dp(2), 0, 0);
            row.addView(line);
            add(row, 16, 10, 16);
        }
        final TextView note = EditionLook.text(a, a.getString(R.string.canvas_t26_empty_note), 12, 400, CanvasLook.muted());
        note.setLineSpacing(CanvasLook.dp(2), 1f);
        add(note, 20, 14, 20);
    }

    // ------------------------------------------------------------------ parts

    private void export() {
        if (data != null && !data.model.isEmpty()) {
            EditionPdf.export(a, data);
        } else {
            Toast.makeText(a, R.string.canvas_t26_empty_title, Toast.LENGTH_SHORT).show();
        }
    }

    private void message(String text) {
        body.removeAllViews();
        final TextView t = EditionLook.text(a, text, 14, 400, CanvasLook.muted());
        add(t, 20, 24, 20);
    }

    private void setSubtitle(CharSequence s) {
        subtitle.setText(s == null ? "" : s);
        subtitle.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void add(View view, int leftDp, int topDp, int rightDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(CanvasLook.dp(leftDp), CanvasLook.dp(topDp), CanvasLook.dp(rightDp), 0);
        body.addView(view, lp);
    }

    private void ui(Runnable r) {
        a.runOnUiThread(() -> {
            if (!Apps.isDestroyedActivity(a) && dialog.isShowing()) {
                r.run();
            }
        });
    }

    static LinearLayout column(Activity a) {
        final LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /**
     * The 56 dp bar of both edition screens: back, "Your edition" over a line, and the export button.
     *
     * @param subtitle gets the line under the title
     * @param export   gets the export button
     */
    static View bar(Activity a, String title, TextView[] subtitle, View[] export, Runnable onBack, Runnable onExport) {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = EditionLook.text(a, "‹", 28, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setBackground(CanvasParts.ripple(null));
        back.setContentDescription(a.getString(R.string.canvas_t26_back));
        back.setOnClickListener(v -> onBack.run());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout titles = column(a);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView t = EditionLook.text(a, title, 14, 600, CanvasLook.ink());
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        titles.addView(t);
        final TextView sub = EditionLook.text(a, "", 12, 400, CanvasLook.muted());
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setGravity(Gravity.CENTER);
        titles.addView(sub);
        subtitle[0] = sub;
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.canvas_t26_ic_export);
        icon.setColorFilter(CanvasLook.ink());
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(CanvasParts.ripple(null));
        icon.setContentDescription(a.getString(R.string.canvas_t26_export_pdf));
        icon.setOnClickListener(v -> onExport.run());
        bar.addView(icon, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        export[0] = icon;
        return bar;
    }
}
