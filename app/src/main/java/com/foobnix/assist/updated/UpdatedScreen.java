package com.foobnix.assist.updated;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "The book, updated" for the chapter at the reader's page (canvas UpdatedMargin, as a list): the
 * strip "THE BOOK, UPDATED · 4 NOTES SINCE 1776" with the four kinds as filters, one card per note
 * (kind, how settled, page; a tap opens its sheet, "Go to p. N" jumps to its sentence) and the
 * "Strongest objection" row. Opened from the ✦ menu; a chapter without stored notes is asked for
 * once, at that tap ({@link UpdatedJob}), and "Check again" asks anew.
 */
public final class UpdatedScreen implements UpdatedJob.Screen {

    private static boolean opening;

    private final Activity a;
    private final DocumentController dc;
    private final BookChapters.Range range;
    private final String bookPath;
    private final String bookName;
    /** The reader has a lens (the Lens feature is on and has a profile). */
    private final boolean lens;
    /** Null until the chapter has been asked for. */
    private UpdatedChapter chapter;
    /** The kind shown, or null for all. */
    private UpdatedKind filter;
    private UpdatedJob job;
    private String error;
    private boolean relocating;

    private Dialog dialog;
    private LinearLayout body;
    private LinearLayout footer;

    private UpdatedScreen(Activity a, DocumentController dc, BookChapters.Range range, UpdatedChapter stored, boolean lens) {
        this.a = a;
        this.dc = dc;
        this.range = range;
        this.bookPath = dc.getCurrentBook().getPath();
        this.bookName = com.foobnix.ai.AiHighlights.bookName(bookPath);
        this.chapter = stored;
        this.lens = lens;
    }

    /** The notes of the chapter at the reader's page (the ✦ menu's "The book, updated"). */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.UPDATED.isOn() || a == null || dc == null || dc.getCurrentBook() == null || opening) {
            return;
        }
        opening = true;
        final AiJobs.Target t = AiJobs.Target.of(dc);
        new Thread(() -> {
            BookChapters.Range found = null;
            UpdatedChapter stored = null;
            boolean hasLens = false;
            try {
                found = AiJobs.chapterAt(t);
                if (found != null) {
                    stored = UpdatedChapter.fromJson(AssistStore.get().item(t.name, UpdatedChapter.key(found.key)));
                }
                hasLens = !UpdatedSession.lensFields().isEmpty();
            } catch (Exception e) {
                LOG.e(e);
            }
            final BookChapters.Range range = found;
            final UpdatedChapter chapter = stored;
            final boolean lens = hasLens;
            a.runOnUiThread(() -> {
                opening = false;
                if (Apps.isDestroyedActivity(a) || !Assist.UPDATED.isOn() || dc.getCurrentBook() == null) {
                    return;
                }
                if (range == null) {
                    Toast.makeText(a, R.string.ai_cannot_open, Toast.LENGTH_LONG).show();
                    return;
                }
                new UpdatedScreen(a, dc, range, chapter, lens).open();
            });
        }, "@T Updated").start();
    }

    private void open() {
        final boolean dark = CanvasLook.isNight();
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = UpdatedParts.column(a);
        page.setBackgroundColor(CanvasLook.bg());
        page.addView(topBar(a, a.getString(R.string.updated_title), chapterTitle(), () -> dialog.dismiss()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));

        final ScrollView scroll = new ScrollView(a);
        body = UpdatedParts.column(a);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        footer = UpdatedParts.column(a);
        footer.setPadding(gutter, CanvasLook.dp(10), gutter, CanvasLook.dp(14));
        page.addView(footer);

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        if (chapter == null) {
            // the reader's tap on the menu row is the tap that asks
            ask();
        } else {
            render();
            relocate();
        }
    }

    /** The bar of a full screen: back, a title with a line under it. */
    static View topBar(Activity a, String title, String sub, Runnable back) {
        final LinearLayout bar = UpdatedParts.row(a);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView backView = UpdatedParts.text(a, "‹", 26, 400, CanvasLook.ink());
        backView.setGravity(Gravity.CENTER);
        backView.setContentDescription(a.getString(R.string.updated_back));
        backView.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        backView.setOnClickListener(v -> back.run());
        bar.addView(backView, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = UpdatedParts.column(a);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView t = UpdatedParts.text(a, title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        titles.addView(t);
        final TextView s = UpdatedParts.text(a, sub == null ? "" : sub, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        s.setGravity(Gravity.CENTER);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(s);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    /** The chapter's title as the book has it, or its pages when it has none. */
    private String chapterTitle() {
        return range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                : a.getString(R.string.ai_pages, range.from + 1, range.to);
    }

    private String layoutNow() {
        final AiJobs.Target t = AiJobs.Target.of(dc);
        return UpdatedChapter.layoutKey(!dc.isTextFormat(), t.width, t.height, t.fontSize);
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        body.removeAllViews();
        footer.removeAllViews();
        if (job != null) {
            renderBusy();
            return;
        }
        if (error != null) {
            renderError();
        }
        if (chapter == null) {
            return;
        }
        body.addView(strip(), UpdatedParts.wide(error == null ? 0 : 12));
        if (chapter.isEmpty()) {
            final TextView none = UpdatedParts.text(a, a.getString(R.string.updated_none), CanvasLook.TEXT_BODY, 400, CanvasLook.ink2());
            body.addView(none, UpdatedParts.wide(16));
        }
        final List<UpdatedNote> shown = chapter.notes(filter);
        if (shown.isEmpty() && !chapter.notes.isEmpty()) {
            body.addView(UpdatedParts.text(a, a.getString(R.string.updated_none_of_kind), CanvasLook.TEXT_BODY, 400,
                    CanvasLook.ink2()), UpdatedParts.wide(16));
        }
        for (UpdatedNote note : shown) {
            body.addView(noteCard(note), UpdatedParts.wide(10));
        }
        if (chapter.objection != null) {
            body.addView(objectionRow(chapter.objection), UpdatedParts.wide(14));
        }

        footer.addView(UpdatedParts.honesty(a));
        final TextView again = UpdatedParts.button(a, a.getString(R.string.updated_again), false);
        again.setOnClickListener(v -> ask());
        final LinearLayout.LayoutParams lp = UpdatedParts.wide(10);
        lp.height = CanvasLook.dp(48);
        footer.addView(again, lp);
        final TextView note = UpdatedParts.text(a, a.getString(R.string.updated_again_note), CanvasLook.TEXT_SMALL, 400,
                CanvasLook.muted());
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.addView(note, UpdatedParts.wide(6));
    }

    private void renderBusy() {
        final LinearLayout line = UpdatedParts.row(a);
        final ProgressBar progress = new ProgressBar(a);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20)));
        final TextView status = UpdatedParts.text(a, a.getString(R.string.updated_asking), CanvasLook.TEXT_BODY, 500, CanvasLook.ink());
        status.setPadding(CanvasLook.dp(10), 0, 0, 0);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(line, UpdatedParts.wide(20));
        final TextView note = UpdatedParts.text(a, a.getString(R.string.updated_asking_note), CanvasLook.TEXT_SMALL, 400,
                CanvasLook.muted());
        note.setLineSpacing(0, 1.3f);
        body.addView(note, UpdatedParts.wide(10));
    }

    private void renderError() {
        final LinearLayout card = UpdatedParts.column(a);
        card.setBackground(CanvasParts.card());
        final int p = CanvasLook.dp(12);
        card.setPadding(p, p, p, p);
        card.addView(UpdatedParts.text(a, a.getString(R.string.updated_failed), CanvasLook.TEXT_BODY, 600, CanvasLook.ink()));
        final TextView message = UpdatedParts.text(a, error, 13, 400, CanvasLook.ink2());
        message.setPadding(0, CanvasLook.dp(4), 0, 0);
        card.addView(message);
        final TextView retry = UpdatedParts.button(a, a.getString(R.string.updated_try_again), true);
        retry.setOnClickListener(v -> ask());
        final LinearLayout.LayoutParams lp = UpdatedParts.wide(10);
        lp.height = CanvasLook.dp(48);
        card.addView(retry, lp);
        body.addView(card, UpdatedParts.wide(8));
    }

    /** "THE BOOK, UPDATED · 4 NOTES SINCE 1776" and the four kinds, which filter the list. */
    private View strip() {
        final LinearLayout strip = UpdatedParts.column(a);
        strip.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
        final int p = CanvasLook.dp(12);
        strip.setPadding(p, p, p, p);

        final int n = chapter.notes.size();
        String head = a.getString(R.string.updated_label) + " · " + a.getResources().getQuantityString(R.plurals.updated_notes, n, n);
        if (chapter.firstPublished != 0) {
            head += " " + a.getString(R.string.updated_since, String.valueOf(chapter.firstPublished));
        }
        final TextView label = UpdatedParts.mono(a, head, CanvasLook.TEXT_LABEL, CanvasLook.ink());
        label.setCompoundDrawables(UpdatedParts.icon(a, R.drawable.canvas_t22_ic_updated, CanvasLook.ink(), 14), null, null, null);
        label.setCompoundDrawablePadding(CanvasLook.dp(6));
        label.setGravity(Gravity.CENTER_VERTICAL);
        strip.addView(label);

        final UpdatedKind[] kinds = UpdatedKind.values();
        for (int i = 0; i < kinds.length; i += 2) {
            final LinearLayout row = UpdatedParts.row(a);
            row.addView(chip(kinds[i]));
            if (i + 1 < kinds.length) {
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = CanvasLook.dp(6);
                row.addView(chip(kinds[i + 1]), lp);
            }
            strip.addView(row, UpdatedParts.wide(i == 0 ? 8 : 6));
        }
        return strip;
    }

    private View chip(UpdatedKind kind) {
        final boolean on = filter == kind;
        final int count = chapter.count(kind);
        final TextView chip = UpdatedParts.text(a, a.getString(UpdatedParts.name(kind)) + (count > 0 ? "  " + count : ""),
                CanvasLook.TEXT_SMALL, on ? 600 : 400, CanvasLook.ink());
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setMinHeight(CanvasLook.dp(36));
        chip.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        chip.setCompoundDrawables(UpdatedParts.icon(a, UpdatedParts.icon(kind), UpdatedParts.color(kind), 13), null, null, null);
        chip.setCompoundDrawablePadding(CanvasLook.dp(5));
        chip.setBackground(on ? CanvasParts.round(UpdatedParts.tint(kind), UpdatedParts.line(kind), CanvasLook.dp(9))
                : CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(9)));
        if (count == 0) {
            chip.setAlpha(0.45f);
        } else {
            chip.setOnClickListener(v -> {
                filter = chapter.toggle(filter, kind);
                render();
            });
        }
        return chip;
    }

    /** One note: "SOURCE CHECK · WELL ESTABLISHED · P. 12", its title, the start of its text, and its two links. */
    private View noteCard(UpdatedNote note) {
        final LinearLayout card = UpdatedParts.column(a);
        card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), UpdatedParts.line(note.kind), CanvasLook.radius())));
        final int p = CanvasLook.dp(12);
        card.setPadding(p, p, p, CanvasLook.dp(2));

        final StringBuilder label = new StringBuilder(a.getString(UpdatedParts.name(note.kind)));
        if (note.settled != UpdatedSettled.UNKNOWN) {
            label.append(" · ").append(a.getString(UpdatedParts.settledName(note.settled)));
        }
        if (note.page >= 0) {
            label.append(" · ").append(a.getString(R.string.updated_page, note.page + 1));
        }
        card.addView(UpdatedParts.kindLabel(a, note.kind, label, 10));

        final TextView title = UpdatedParts.text(a, note.title, 14, 600, CanvasLook.ink());
        title.setPadding(0, CanvasLook.dp(6), 0, 0);
        card.addView(title);
        if (!note.body.isEmpty()) {
            final TextView text = UpdatedParts.text(a, note.body, 13, 400, CanvasLook.ink2());
            text.setLineSpacing(0, 1.3f);
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setPadding(0, CanvasLook.dp(2), 0, 0);
            card.addView(text);
        }

        final LinearLayout links = UpdatedParts.row(a);
        final TextView details = link(a.getString(R.string.updated_details));
        details.setOnClickListener(v -> openNote(note));
        links.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView go = link(note.page >= 0 ? a.getString(R.string.updated_go_to_page, note.page + 1)
                : a.getString(R.string.updated_go_to_sentence));
        go.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        go.setOnClickListener(v -> jump(note.quote, note.page));
        links.addView(go);
        card.addView(links);

        card.setOnClickListener(v -> openNote(note));
        return card;
    }

    private TextView link(String s) {
        final TextView t = UpdatedParts.text(a, s, 13, 600, CanvasLook.accent());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(CanvasLook.target());
        return t;
    }

    private View objectionRow(UpdatedObjection o) {
        final LinearLayout card = UpdatedParts.column(a);
        final GradientDrawable bg = CanvasParts.round(UpdatedParts.warnCard(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP));
        bg.setStroke(Math.max(1, CanvasLook.dp(3) / 2), UpdatedParts.warnLine());
        card.setBackground(CanvasParts.ripple(bg));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        final LinearLayout head = UpdatedParts.row(a);
        head.addView(UpdatedParts.mono(a, a.getString(R.string.updated_objection), 10, CanvasLook.sensor()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (o.strength.bars > 0) {
            head.addView(UpdatedParts.bars(a, o.strength.bars, UpdatedParts.warnBar()));
            final TextView tag = UpdatedParts.text(a, a.getString(UpdatedObjectionScreen.strengthName(o.strength)),
                    CanvasLook.TEXT_SMALL, 600, CanvasLook.sensor());
            tag.setPadding(CanvasLook.dp(6), 0, 0, 0);
            head.addView(tag);
        }
        final TextView arrow = UpdatedParts.text(a, "›", 18, 400, CanvasLook.sensor());
        arrow.setPadding(CanvasLook.dp(8), 0, 0, 0);
        head.addView(arrow);
        card.addView(head);
        final TextView text = UpdatedParts.text(a, o.objection, 14, 600, CanvasLook.ink());
        text.setLineSpacing(0, 1.3f);
        text.setPadding(0, CanvasLook.dp(4), 0, 0);
        card.addView(text);
        card.setMinimumHeight(CanvasLook.target());
        card.setOnClickListener(v -> UpdatedObjectionScreen.show(a, chapter, chapterTitle(),
                o.twistInChapter ? () -> jump(o.twistQuote, o.twistPage) : null));
        return card;
    }

    private void openNote(UpdatedNote note) {
        UpdatedNoteSheet.show(a, note, lens, () -> jump(note.quote, note.page));
    }

    // ------------------------------------------------------------------ actions

    /** Closes the screen and goes to the page holding the words (looked for in the chapter's pages). */
    private void jump(String quote, int page) {
        if (dialog != null) {
            dialog.dismiss();
        }
        final int lo = Math.max(0, range.from - 2);
        final int hi = range.to + 1;
        // a page of another layout is no hint
        final int hint = page >= lo && page <= hi ? page : Math.max(0, range.from);
        AiJump.toQuote(dc, quote, hint, lo, hi);
    }

    /** The request for this chapter (the reader tapped the menu row, "Check again" or "Try again"). */
    private void ask() {
        if (job != null || !Assist.UPDATED.isOn()) {
            return;
        }
        error = null;
        job = new UpdatedJob(a, AiJobs.Target.of(dc), range, layoutNow(), this);
        render();
        job.start();
    }

    private void stop() {
        final UpdatedJob j = job;
        job = null;
        if (j != null) {
            j.cancel();
        }
    }

    @Override
    public void onDone(UpdatedChapter made) {
        job = null;
        error = null;
        chapter = made;
        filter = null;
        UpdatedSession.changed(bookPath, range.key, made);
        render();
    }

    @Override
    public void onError(String message) {
        job = null;
        error = message == null ? "" : message;
        render();
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (dialog != null) {
            dialog.dismiss();
        }
        AiSettingsDialog.show(a, null);
    }

    /** A reflowing book in a new layout (font, size): the notes' pages are found again and kept. */
    private void relocate() {
        final String layout = layoutNow();
        if (chapter == null || relocating || !chapter.needsPages(layout) || !dc.isTextFormat()) {
            return;
        }
        relocating = true;
        final UpdatedChapter c = chapter;
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final List<String> keys = new ArrayList<>();
        for (UpdatedNote n : c.notes) {
            keys.add(QuoteMatcher.key(n.quote));
        }
        final boolean twist = c.objection != null && c.objection.twistInChapter;
        keys.add(twist ? QuoteMatcher.key(c.objection.twistQuote) : null);
        new Thread(() -> {
            final int[][] pages = new int[keys.size()][];
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text == null) {
                    return;
                }
                final int from = Math.max(0, range.from - 2);
                final int to = Math.min(text.pageCount(), range.to + 2);
                for (int p = from; p < to; p++) {
                    final QuoteMatcher.Words words = new QuoteMatcher.Words(BookText.words(text.text(p)));
                    for (int i = 0; i < keys.size(); i++) {
                        if (keys.get(i) == null
                                || QuoteMatcher.onPage(words, Collections.singletonList(keys.get(i))).isEmpty()) {
                            continue;
                        }
                        if (pages[i] == null) {
                            pages[i] = new int[]{p, p};
                        } else if (pages[i][1] == p - 1) {
                            pages[i][1] = p;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
                return;
            }
            a.runOnUiThread(() -> {
                relocating = false;
                if (chapter != c || Apps.isDestroyedActivity(a)) {
                    return;
                }
                for (int i = 0; i < c.notes.size(); i++) {
                    c.notes.get(i).page = pages[i] == null ? -1 : pages[i][0];
                    c.notes.get(i).lastPage = pages[i] == null ? -1 : pages[i][1];
                }
                if (twist) {
                    final int[] at = pages[pages.length - 1];
                    c.objection.twistPage = at == null ? -1 : at[0];
                }
                c.layout = layout;
                save(c);
                if (job == null) {
                    render();
                }
            });
        }, "@T Updated pages").start();
    }

    private void save(UpdatedChapter c) {
        final String json;
        try {
            json = c.toJson().toString();
        } catch (Exception e) {
            LOG.e(e);
            return;
        }
        new Thread(() -> {
            try {
                AssistStore.get().put(bookName, UpdatedChapter.key(range.key), new JSONObject(json));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T Updated save").start();
    }
}
