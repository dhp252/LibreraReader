package com.foobnix.assist.path;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * "One topic, three books · Merged reading path" (canvas artboard PathBooks): the reader names a
 * topic and picks 2 to 4 books that have a skeleton or a study preview; one AI request merges
 * their chapters on the topic into one path, says once what they agree on and flags where they
 * disagree, with each author's position. A row opens that book at that chapter. A full-screen
 * dialog over the book, only while {@link Assist#PATH} is on.
 */
public final class TopicScreen implements TopicJob.Screen {

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;

    private Dialog dialog;
    private TextView barTitle;
    private LinearLayout body;
    private LinearLayout bottom;
    private EditText topicField;

    private boolean loaded;
    private List<TopicBooks.Item> items = new ArrayList<>();
    private final List<TopicBooks.Item> picked = new ArrayList<>();
    private List<TopicData> saved = new ArrayList<>();
    private String topic = "";
    /** The merged path shown, or null while picking. */
    private TopicData data;
    private TopicJob job;
    private String status = "";
    private String error;
    private boolean opening;

    private TopicScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
    }

    /** Opens the topic screen over the book the reader shows (nothing while the feature is off). */
    public static void show(Activity a, DocumentController dc) {
        if (!Assist.PATH.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new TopicScreen(a, dc).open();
    }

    private void open() {
        dialog = PathViews.fullScreen(a);
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CanvasLook.bg());
        final LinearLayout bar = PathViews.topBar(a, R.drawable.glyphicons_223_chevron_left, R.string.canvas_t20_back,
                this::back, a.getString(R.string.canvas_t21_title), a.getString(R.string.canvas_t21_subtitle));
        barTitle = (TextView) ((LinearLayout) bar.getChildAt(1)).getChildAt(0);
        root.addView(bar);

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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

    /** From a merged path back to the books; from the books back to the book. */
    private void back() {
        if (data != null && job == null) {
            data = null;
            error = null;
            render();
        } else {
            dialog.dismiss();
        }
    }

    private void load() {
        new Thread(() -> {
            final List<TopicBooks.Item> found = TopicBooks.list();
            final List<TopicData> topics = new ArrayList<>();
            for (JSONObject o : AssistStore.get().items(AssistStore.LIBRARY, TopicData.PREFIX).values()) {
                final TopicData t = TopicData.fromJson(o);
                if (!t.isEmpty()) {
                    topics.add(t);
                }
            }
            Collections.sort(topics, (x, y) -> Long.compare(y.made, x.made));
            a.runOnUiThread(() -> {
                if (!alive()) {
                    return;
                }
                loaded = true;
                items = found;
                saved = topics;
                // the open book first among the picks, when it can be one
                for (TopicBooks.Item item : found) {
                    if (item.name.equals(target.name)) {
                        picked.add(item);
                    }
                }
                render();
            });
        }, "@T Topic").start();
    }

    // ------------------------------------------------------------------ the job

    private void merge() {
        if (topicField != null) {
            topic = topicField.getText().toString().trim().replaceAll("\\s+", " ");
            PathViews.hideKeyboard(topicField);
        }
        if (topic.isEmpty() || picked.size() < TopicData.MIN_BOOKS || job != null) {
            return;
        }
        error = null;
        status = "";
        job = new TopicJob(a, target, topic, picked, this);
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
    public void onTopic(TopicData made) {
        job = null;
        if (!alive()) {
            return;
        }
        for (int i = saved.size() - 1; i >= 0; i--) {
            if (TopicData.slug(saved.get(i).topic).equals(TopicData.slug(made.topic))) {
                saved.remove(i);
            }
        }
        saved.add(0, made);
        data = made;
        render();
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

    private void render() {
        if (dialog == null) {
            return;
        }
        if (topicField != null && data == null) {
            // what was typed survives a redraw
            topic = topicField.getText().toString();
        }
        body.removeAllViews();
        bottom.removeAllViews();
        bottom.setVisibility(View.GONE);
        topicField = null;
        final int n = data == null ? 0 : data.books.size();
        barTitle.setText(n == 2 ? R.string.canvas_t21_title_two : n == 3 ? R.string.canvas_t21_title_three
                : n == 4 ? R.string.canvas_t21_title_four : R.string.canvas_t21_title);
        if (data != null) {
            renderResult();
        } else {
            renderPicker();
        }
    }

    private void renderPicker() {
        body.addView(PathViews.text(a, a.getString(R.string.canvas_t21_topic), 13, 600, CanvasLook.ink()));
        topicField = PathViews.field(a, a.getString(R.string.canvas_t21_topic_hint), false);
        topicField.setText(topic);
        body.addView(topicField, PathViews.wide(6));

        if (job != null) {
            body.addView(PathViews.busyRow(a, TextUtils.isEmpty(status) ? a.getString(R.string.canvas_t21_merging) : status,
                    () -> {
                        if (job != null) {
                            job.cancel();
                            job = null;
                        }
                        render();
                    }), PathViews.wide(12));
        }
        if (error != null) {
            body.addView(PathViews.text(a, error, 13, 400, CanvasLook.sensor()), PathViews.wide(12));
        }

        final TextView label = PathViews.mono(a, a.getString(R.string.canvas_t21_pick).toUpperCase(Locale.getDefault()),
                CanvasLook.muted());
        body.addView(label, PathViews.wide(16));
        if (!loaded) {
            body.addView(PathViews.muted(a, a.getString(R.string.canvas_t20_loading)), PathViews.wide(8));
            return;
        }
        if (items.size() < TopicData.MIN_BOOKS) {
            body.addView(PathViews.text(a, a.getString(R.string.canvas_t21_no_books), 14, 400, CanvasLook.ink2()),
                    PathViews.wide(8));
        }
        for (TopicBooks.Item item : items) {
            final boolean on = picked.contains(item);
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setMinimumHeight(dp(52));
            row.setBackground(CanvasParts.ripple(PathViews.outline(on ? CanvasLook.accentTint() : CanvasLook.bg(),
                    on ? CanvasLook.accent() : CanvasLook.line(), on ? 2 : 1, dp(12))));
            final int place = picked.indexOf(item);
            final TextView mark = PathViews.text(a, on ? item.book().letter() : "", 12, 700, CanvasLook.onAccent());
            mark.setGravity(Gravity.CENTER);
            mark.setBackground(on ? CanvasParts.round(badge(place), 0, dp(6))
                    : PathViews.outline(CanvasLook.bg(), CanvasLook.lineStrong(), 1, dp(6)));
            row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));
            final LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView title = PathViews.text(a, item.title, 14, 600, CanvasLook.ink());
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            final String by = item.book().byline();
            if (!by.isEmpty()) {
                texts.addView(PathViews.muted(a, by));
            }
            final LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textsParams.leftMargin = dp(12);
            row.addView(texts, textsParams);
            row.setSelected(on);
            row.setOnClickListener(v -> {
                if (job != null) {
                    return;
                }
                if (!picked.remove(item) && picked.size() < TopicData.MAX_BOOKS) {
                    picked.add(item);
                }
                render();
            });
            body.addView(row, PathViews.wide(8));
        }

        if (!saved.isEmpty()) {
            body.addView(PathViews.mono(a, a.getString(R.string.canvas_t21_earlier).toUpperCase(Locale.getDefault()),
                    CanvasLook.muted()), PathViews.wide(20));
            for (int i = 0; i < saved.size() && i < 10; i++) {
                final TopicData t = saved.get(i);
                final LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(4), dp(8), dp(4), dp(8));
                row.setMinimumHeight(CanvasLook.target());
                row.setBackground(CanvasParts.ripple(null));
                final TextView name = PathViews.text(a, t.topic, 14, 600, CanvasLook.ink());
                name.setMaxLines(2);
                name.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(name);
                row.addView(PathViews.muted(a, a.getString(R.string.canvas_t21_books_chapters, t.books.size(), t.steps.size())));
                row.setOnClickListener(v -> {
                    if (job == null) {
                        data = t;
                        error = null;
                        render();
                    }
                });
                body.addView(row, PathViews.wide(2));
            }
        }

        bottom.setVisibility(View.VISIBLE);
        bottom.addView(PathViews.muted(a, a.getString(R.string.canvas_t21_note)));
        final TextView go = PathViews.primary(a, a.getString(R.string.canvas_t21_merge));
        PathViews.enable(go, job == null && picked.size() >= TopicData.MIN_BOOKS);
        go.setOnClickListener(v -> merge());
        bottom.addView(go, PathViews.wide(52, 10));
    }

    private void renderResult() {
        // the topic; a tap goes back to change it or the books
        body.addView(PathViews.text(a, a.getString(R.string.canvas_t21_topic), 13, 600, CanvasLook.ink()));
        final TextView shown = PathViews.text(a, data.topic, CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
        shown.setGravity(Gravity.CENTER_VERTICAL);
        shown.setPadding(dp(14), dp(8), dp(14), dp(8));
        shown.setMinHeight(dp(48));
        shown.setBackground(CanvasParts.ripple(PathViews.outline(CanvasLook.bg(), CanvasLook.lineStrong(), 1, dp(12))));
        shown.setOnClickListener(v -> {
            topic = data.topic;
            back();
        });
        body.addView(shown, PathViews.wide(6));
        if (error != null) {
            body.addView(PathViews.text(a, error, 13, 400, CanvasLook.sensor()), PathViews.wide(12));
        }

        // the books, side by side
        final LinearLayout books = new LinearLayout(a);
        books.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < data.books.size(); i++) {
            final TopicData.Book b = data.books.get(i);
            final LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(12)));
            card.addView(badgeView(i, 24, 12), new LinearLayout.LayoutParams(dp(24), dp(24)));
            final TextView title = PathViews.text(a, b.title.isEmpty() ? b.name : b.title, 12, 600, CanvasLook.ink());
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(title, PathViews.wide(6));
            final String codes = data.codesOf(i);
            final int count = data.stepsOf(i);
            final String chapters = !codes.isEmpty() ? codes : count == 1 ? a.getString(R.string.canvas_t20_chapters_one)
                    : a.getString(R.string.canvas_t20_chapters, count);
            final String by = b.byline();
            final TextView under = PathViews.text(a, by.isEmpty() ? chapters : by + "\n" + chapters, 11, 400, CanvasLook.muted());
            under.setMaxLines(4);
            under.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(under, PathViews.wide(6));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) {
                p.leftMargin = dp(8);
            }
            books.addView(card, p);
        }
        body.addView(books, PathViews.wide(14));

        final int steps = data.steps.size();
        final TextView become = PathViews.text(a, steps == 1 ? a.getString(R.string.canvas_t21_become_one)
                : a.getString(R.string.canvas_t21_become, steps), 13, 400, CanvasLook.ink2());
        become.setPadding(dp(4), 0, dp(4), 0);
        body.addView(become, PathViews.wide(12));

        // THEY AGREE · SAID ONCE
        for (TopicData.Agree g : data.agree) {
            final LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.setBackground(CanvasParts.round(CanvasLook.surface(), 0, dp(14)));
            final LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(PathViews.mono(a, a.getString(R.string.canvas_t21_agree).toUpperCase(Locale.getDefault()),
                    CanvasLook.accent()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            for (Integer b : g.books) {
                final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(18), dp(18));
                p.leftMargin = dp(3);
                head.addView(badgeView(b, 18, 10), p);
            }
            box.addView(head);
            box.addView(PathViews.text(a, g.point, CanvasLook.TEXT_BODY, 600, CanvasLook.ink()), PathViews.wide(8));
            if (!g.note.isEmpty()) {
                box.addView(PathViews.text(a, g.note, 12, 400, CanvasLook.ink2()), PathViews.wide(4));
            }
            body.addView(box, PathViews.wide(12));
        }

        // THEY DISAGREE · FLAGGED
        for (TopicData.Disagree g : data.disagree) {
            final LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.setBackground(PathViews.outline(CanvasLook.bg(), CanvasLook.sensorTint(), 2, dp(14)));
            box.addView(PathViews.mono(a, a.getString(R.string.canvas_t21_disagree).toUpperCase(Locale.getDefault()),
                    CanvasLook.sensor()));
            box.addView(PathViews.text(a, g.question, CanvasLook.TEXT_BODY, 600, CanvasLook.ink()), PathViews.wide(8));
            for (TopicData.Position p : g.positions) {
                final LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.addView(badgeView(p.book, 18, 10), new LinearLayout.LayoutParams(dp(18), dp(18)));
                final String who = data.books.get(p.book).shortName();
                final SpannableStringBuilder says = new SpannableStringBuilder(who + ": " + p.says);
                says.setSpan(new StyleSpan(Typeface.BOLD), 0, who.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                final TextView text = PathViews.text(a, says, 13, 400, CanvasLook.ink());
                final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                textParams.leftMargin = dp(10);
                row.addView(text, textParams);
                box.addView(row, PathViews.wide(10));
            }
            body.addView(box, PathViews.wide(10));
        }

        // MOSTLY IN ONE BOOK
        if (!data.only.isEmpty()) {
            body.addView(PathViews.mono(a, a.getString(R.string.canvas_t21_only).toUpperCase(Locale.getDefault()),
                    CanvasLook.muted()), PathViews.wide(12));
            for (TopicData.Only o : data.only) {
                final LinearLayout chip = new LinearLayout(a);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(dp(10), 0, dp(10), 0);
                chip.setMinimumHeight(dp(30));
                chip.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(8)));
                final View square = new View(a);
                square.setBackground(CanvasParts.round(badge(o.book), 0, dp(2)));
                chip.addView(square, new LinearLayout.LayoutParams(dp(8), dp(8)));
                final TextView text = PathViews.text(a, o.point, 13, 400, CanvasLook.ink());
                text.setPadding(dp(6), 0, 0, 0);
                chip.addView(text);
                final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                p.topMargin = dp(6);
                body.addView(chip, p);
            }
        }

        // the merged path, chapter by chapter
        body.addView(PathViews.mono(a, a.getString(R.string.canvas_t21_path).toUpperCase(Locale.getDefault()),
                CanvasLook.muted()), PathViews.wide(16));
        final int wpm = ReadingSpeed.wpm(target.name);
        for (int i = 0; i < data.steps.size(); i++) {
            final TopicData.Step s = data.steps.get(i);
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setPadding(0, dp(6), 0, dp(6));
            row.setBackground(CanvasParts.ripple(null));
            row.addView(badgeView(s.book, 20, 11), new LinearLayout.LayoutParams(dp(20), dp(20)));
            final TextView code = PathViews.mono(a, s.code.isEmpty() ? String.valueOf(i + 1) : s.code, CanvasLook.muted());
            code.setTypeface(CanvasLook.mono(a, 400));
            code.setMinWidth(dp(34));
            code.setMaxWidth(dp(60));
            final LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            codeParams.leftMargin = dp(10);
            row.addView(code, codeParams);
            final LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView title = PathViews.text(a, !s.title.isEmpty() ? s.title : !s.line.isEmpty() ? s.line
                    : a.getString(R.string.canvas_t20_chapter), 14, 600, CanvasLook.ink());
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            final String why = !s.why.isEmpty() ? s.why : s.title.isEmpty() ? "" : s.line;
            if (!why.isEmpty()) {
                final TextView w = PathViews.muted(a, why);
                w.setMaxLines(2);
                w.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(w);
            }
            final LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textsParams.leftMargin = dp(10);
            row.addView(texts, textsParams);
            final String time = PathText.duration(PathData.minutes(s.chars, wpm));
            if (!time.isEmpty()) {
                final TextView t = PathViews.text(a, time, 12, 400, CanvasLook.ink2());
                t.setPadding(dp(8), 0, 0, 0);
                row.addView(t);
            }
            row.setOnClickListener(v -> openStep(s));
            body.addView(row, PathViews.wide());
        }

        bottom.setVisibility(View.VISIBLE);
        final TextView read = PathViews.primary(a, opening ? a.getString(R.string.canvas_t20_loading)
                : a.getString(R.string.canvas_t21_read) + "  →");
        PathViews.enable(read, !opening);
        read.setOnClickListener(v -> openStep(data.steps.get(0)));
        bottom.addView(read, PathViews.wide(52, 0));
    }

    /** The colour of a book's badge, by its place among the books. */
    private static int badge(int book) {
        switch (book) {
            case 0:
                return CanvasLook.accent();
            case 1:
                return CanvasLook.sensor();
            case 2:
                return CanvasLook.ink();
            default:
                return CanvasLook.muted();
        }
    }

    private TextView badgeView(int book, int sizeDp, float sp) {
        final String letter = data != null && book >= 0 && book < data.books.size() ? data.books.get(book).letter() : "";
        // on ink the letter takes the page colour, on the other fills the colour set on the accent
        final TextView t = PathViews.text(a, letter, sp, 700, book == 2 ? CanvasLook.bg() : CanvasLook.onAccent());
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(CanvasParts.round(badge(book), 0, dp(sizeDp >= 24 ? 6 : 5)));
        return t;
    }

    // ------------------------------------------------------------------ opening a chapter

    /** Opens the step's book at its chapter: in place for the open book, else that book in a reader of its own. */
    private void openStep(TopicData.Step step) {
        if (opening || data == null) {
            return;
        }
        final String book = data.books.get(step.book).name;
        opening = true;
        error = null;
        render();
        new Thread(() -> {
            final boolean here = book.equals(target.name);
            final PathSession.Ranges ranges = here ? PathSession.read(target) : null;
            final String path = here ? target.path : TopicBooks.pathOf(book);
            a.runOnUiThread(() -> {
                opening = false;
                if (!alive()) {
                    return;
                }
                if (here) {
                    final int[] place = ranges == null ? null : ranges.byKey.get(step.key);
                    if (place == null) {
                        error = a.getString(R.string.canvas_t20_no_place);
                        render();
                        return;
                    }
                    PathSession.goTo(dc, place[0]);
                    dialog.dismiss();
                    return;
                }
                if (path == null) {
                    error = a.getString(R.string.canvas_t21_not_here);
                    render();
                    return;
                }
                // the reader of that book goes to the chapter once its contents are read
                PathSession.openAt(path, step.key);
                dialog.dismiss();
                dc.onCloseActivityFinal(() -> ExtUtils.showDocumentWithoutDialog(a, new File(path), null));
            });
        }, "@T Topic").start();
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
