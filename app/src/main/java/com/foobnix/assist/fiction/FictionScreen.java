package com.foobnix.assist.fiction;

import android.app.Activity;
import android.app.Dialog;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiMenu;
import com.foobnix.ai.AiProgress;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiTask;
import com.foobnix.ai.AiWork;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * "Who's who" (canvas artboard FictionGraph): the cast of a story as of the reader's page. The
 * main people as cards with initials and one line, by group; how they are tied, as rows; the
 * people met in passing; and a card for the people still ahead, whose names stay hidden.
 * <p>
 * The cast is built on a tap ({@link FictionCastJob}, a long AI task) from the text up to the
 * reader's page only. It always shows under the chapter it was built for: a reader who has read
 * on is told so and offered the update; one who went back sees nobody met later. Only shown
 * while {@link Assist#FICTION} is on.
 */
public final class FictionScreen implements FictionJob.Screen, AiProgress.TaskScreen {

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final String key;

    private Dialog dialog;
    private LinearLayout body;

    private boolean loaded;
    private FictionCast cast = new FictionCast();
    private FictionChapters chapters;
    private FictionWhere where;
    private int readerEnd;
    private String status = "";
    private String error;
    /** Cancel was tapped: a task only queued never says it ended. */
    private boolean stopped;

    private FictionScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.key = FictionBook.castTask(target.name);
    }

    /** Opens the who's who of the book the reader shows (nothing while the feature is off). */
    public static void show(Activity a, DocumentController dc) {
        if (!Assist.FICTION.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new FictionScreen(a, dc).open();
    }

    // ------------------------------------------------------------------ frame

    private void open() {
        final LinearLayout root = FictionUi.column(a);
        final TextView more = FictionUi.text(a, "⋮", 20, 600, CanvasLook.ink());
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(a.getString(R.string.canvas_t29_more));
        more.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        more.setOnClickListener(this::more);
        root.addView(FictionUi.topBar(a, a.getString(R.string.canvas_t29_whos_who), target.title, () -> dialog.dismiss(), more),
                FictionUi.barParams());

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        body = FictionUi.column(a);
        body.setPadding(CanvasLook.dp(16), CanvasLook.dp(4), CanvasLook.dp(16), CanvasLook.dp(24));
        scroll.addView(body, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog = FictionUi.fullScreen(a, root);
        dialog.setOnDismissListener(d -> AiProgress.detach(key, this));
        dialog.show();
        if (AiProgress.isBusy(key)) {
            AiProgress.attach(key, a, this);
        }
        render();
        load();
    }

    private void more(View anchor) {
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        if (!cast.isEmpty()) {
            menu.getMenu(R.drawable.canvas_t29_ic_cast, R.string.canvas_t29_again,
                    () -> AiMenu.confirmRedo(a, R.string.canvas_t29_again_confirm, () -> make(true)));
        }
        menu.getMenu(R.drawable.ic_ai_sparkle, R.string.ai_settings, () -> AiSettingsDialog.show(a, null));
        menu.show();
    }

    // ------------------------------------------------------------------ data

    private void load() {
        final int page = FictionBook.readerPage(dc);
        final int pages = FictionBook.readerPages(dc);
        new Thread(() -> {
            FictionCast c = new FictionCast();
            FictionChapters ch = null;
            try {
                c = FictionStore.loadCast(target.name);
                ch = FictionBook.chapters(target);
            } catch (Exception e) {
                LOG.e(e);
            }
            final FictionCast found = c;
            final FictionChapters book = ch;
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a) || dialog == null || !dialog.isShowing()) {
                    return;
                }
                loaded = true;
                cast = found;
                chapters = book;
                if (book != null) {
                    // the reader's place in the pages the chapters are counted in
                    readerEnd = FictionShield.end(page, pages, book.pages());
                    where = FictionWhere.of(book, readerEnd, found.mark);
                }
                render();
            });
        }, "@T Fiction").start();
    }

    /** Builds the cast, or brings it up to the reader's page; {@code again}: from the start of the book. */
    private void make(boolean again) {
        if (!Assist.FICTION.isOn()) {
            return;
        }
        if (!AiSettings.load(a).isReady()) {
            AiSettingsDialog.show(a, null);
            return;
        }
        if (!AiProgress.isRunning(key)) {
            // queued again when only waiting: the newest request replaces it (AiWork.enqueue)
            AiWork.enqueue(a, AiTask.fictionCast(AiJobs.Target.of(dc), FictionBook.ticket(dc), again));
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
            load();
        } else if (state == AiProgress.FAILED) {
            error = TextUtils.isEmpty(message) ? a.getString(R.string.canvas_t29_unreadable) : message;
            // a long build may have saved its first parts
            load();
        }
        render();
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        if (dialog == null) {
            return;
        }
        body.removeAllViews();
        if (!loaded) {
            body.addView(muted(a.getString(R.string.canvas_t29_loading)), FictionUi.row(0, 12, 0, 0));
            return;
        }
        if (chapters == null || where == null) {
            body.addView(muted(a.getString(R.string.ai_cannot_open)), FictionUi.row(0, 12, 0, 0));
            return;
        }
        final boolean busy = busy();
        if (cast.isEmpty()) {
            renderEmpty(busy);
            return;
        }
        renderHeader(busy);
        final FictionView view = FictionView.build(cast, chapters, where);
        for (FictionView.Group g : view.groups) {
            if (!g.name.isEmpty()) {
                // a group's name is the book's own: shown as it is, not in capitals
                final TextView name = FictionUi.mono(a, g.name, CanvasLook.TEXT_LABEL, CanvasLook.muted(), true);
                name.setPadding(CanvasLook.dp(10), CanvasLook.dp(4), CanvasLook.dp(10), CanvasLook.dp(4));
                name.setBackground(FictionUi.outline(CanvasLook.bg(), CanvasLook.lineStrong(), 8, true));
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = CanvasLook.dp(14);
                body.addView(name, lp);
            }
            for (FictionView.Card card : g.cards) {
                body.addView(personCard(card), FictionUi.row(0, 8, 0, 0));
            }
        }
        if (!view.ties.isEmpty()) {
            body.addView(label(R.string.canvas_t29_ties), FictionUi.row(2, 18, 0, 2));
            for (FictionView.Tie tie : view.ties) {
                body.addView(tieRow(tie), FictionUi.row(0, 6, 0, 0));
            }
        }
        final int ahead = chapters.hiddenAfter(Math.max(0, readerEnd - 1));
        if (view.hidden > 0) {
            body.addView(aheadCard(a.getResources().getQuantityString(R.plurals.canvas_t29_people_hidden, view.hidden,
                    view.hidden), a.getString(R.string.canvas_t29_met_after, chapters.label(where.reader)), ""),
                    FictionUi.row(0, 14, 0, 0));
        } else if (ahead > 0) {
            final int here = Math.max(0, where.reader);
            body.addView(aheadCard(a.getString(R.string.canvas_t29_more_ahead), a.getString(R.string.canvas_t29_names_hidden),
                    chapters.hasToc() && here + 1 < chapters.size() ? chapters.range(here + 1, chapters.size() - 1) : ""),
                    FictionUi.row(0, 14, 0, 0));
        }
        if (!view.alsoMet.isEmpty()) {
            body.addView(label(R.string.canvas_t29_also_met), FictionUi.row(2, 18, 0, 2));
            for (FictionView.Card card : view.alsoMet) {
                body.addView(alsoMetRow(card));
            }
        }
        final TextView foot = muted(a.getString(R.string.canvas_t29_foot));
        body.addView(foot, FictionUi.row(2, 18, 2, 0));
    }

    /** No cast yet: what it is, what one tap sends, and the tap (or its progress). */
    private void renderEmpty(boolean busy) {
        body.addView(label(R.string.canvas_t29_spoiler_safe), FictionUi.row(2, 12, 0, 0));
        final TextView what = FictionUi.text(a, a.getString(R.string.canvas_t29_empty), 20, 600, CanvasLook.ink());
        body.addView(what, FictionUi.row(2, 4, 0, 0));
        final int percent = chapters.pages() <= 0 ? 0 : Math.round(100f * readerEnd / chapters.pages());
        final TextView note = FictionUi.text(a, a.getString(R.string.canvas_t29_empty_note,
                chapters.label(Math.max(0, where.reader)), percent), CanvasLook.TEXT_TITLE, 400, CanvasLook.ink2());
        note.setLineSpacing(CanvasLook.dp(3), 1f);
        body.addView(note, FictionUi.row(2, 8, 0, 0));
        if (busy || error != null) {
            body.addView(progress(busy), FictionUi.row(2, 12, 0, 0));
        }
        if (!busy) {
            final TextView make = FictionUi.button(a, a.getString(error != null ? R.string.study_retry
                    : R.string.canvas_t29_build), true);
            make.setOnClickListener(v -> make(false));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    CanvasLook.dp(52));
            lp.topMargin = CanvasLook.dp(16);
            body.addView(make, lp);
        }
    }

    /** "CAST AS OF / Ch. 14 · your page", and what to do when the reader is somewhere else. */
    private void renderHeader(boolean busy) {
        body.addView(label(R.string.canvas_t29_cast_as_of), FictionUi.row(2, 8, 0, 0));
        final String built = chapters.label(Math.max(0, where.stored));
        final String at;
        if (where.state == FictionWhere.AT) {
            at = a.getString(R.string.canvas_t29_your_page, built);
        } else if (where.state == FictionWhere.BEFORE) {
            at = a.getString(R.string.canvas_t29_back_in, built, chapters.label(where.reader));
        } else {
            at = built;
        }
        body.addView(FictionUi.text(a, at, 20, 600, CanvasLook.ink()), FictionUi.row(2, 2, 0, 0));
        if (where.state == FictionWhere.BEFORE) {
            body.addView(muted(a.getString(R.string.canvas_t29_back_note, chapters.label(where.reader))),
                    FictionUi.row(2, 6, 0, 0));
        } else if (where.state == FictionWhere.PAST && !busy) {
            final LinearLayout card = FictionUi.column(a);
            card.setBackground(CanvasParts.accentCard());
            card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(12));
            final boolean sameChapter = where.reader == where.stored;
            final String now = chapters.label(Math.max(0, where.reader));
            card.addView(FictionUi.text(a, sameChapter ? a.getResources().getQuantityString(R.plurals.canvas_t29_read_on_pages,
                            where.readerEnd - where.storedEnd, where.readerEnd - where.storedEnd) : a.getString(R.string.canvas_t29_read_on, now),
                    CanvasLook.TEXT_TITLE, 400, CanvasLook.ink()));
            final TextView update = FictionUi.button(a, sameChapter ? a.getString(R.string.canvas_t29_update_page)
                    : a.getString(R.string.canvas_t29_update_to, now), true);
            update.setOnClickListener(v -> make(false));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    CanvasLook.dp(48));
            lp.topMargin = CanvasLook.dp(10);
            card.addView(update, lp);
            body.addView(card, FictionUi.row(0, 12, 0, 0));
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

    /** A main person: initials, the name as the book spells it, one line. A tap opens their card. */
    private View personCard(FictionView.Card card) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        row.setMinimumHeight(CanvasLook.dp(60));
        row.setBackground(CanvasParts.ripple(CanvasParts.card()));
        row.addView(FictionUi.avatar(a, card.initials, 40));
        final LinearLayout texts = FictionUi.column(a);
        texts.setPadding(CanvasLook.dp(12), 0, 0, 0);
        texts.addView(FictionUi.text(a, card.person.name, CanvasLook.TEXT_BODY, 600, CanvasLook.ink()));
        if (!card.line.isEmpty()) {
            texts.addView(FictionUi.text(a, card.line, CanvasLook.TEXT_SMALL, 400, CanvasLook.ink2()));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(v -> FictionNameCard.show(a, dc, cast, chapters, where, readerEnd, card.person, true));
        return row;
    }

    /** "Phileas Fogg — the £20,000 bet — Reform Club", the tie in the accent colour. */
    private View tieRow(FictionView.Tie tie) {
        final SpannableStringBuilder s = new SpannableStringBuilder();
        s.append(tie.a).append("  —  ");
        final int from = s.length();
        s.append(tie.label);
        s.setSpan(new ForegroundColorSpan(CanvasLook.accent()), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("  —  ").append(tie.b);
        final TextView t = FictionUi.text(a, s, 13, 500, CanvasLook.ink());
        t.setPadding(CanvasLook.dp(12), CanvasLook.dp(8), CanvasLook.dp(12), CanvasLook.dp(8));
        t.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP)));
        return t;
    }

    /** The dashed card for what the shield hides. */
    private View aheadCard(String title, String line, String range) {
        final LinearLayout card = FictionUi.column(a);
        card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        card.setBackground(FictionUi.outline(CanvasLook.surface(), CanvasLook.lineStrong(), CanvasLook.RADIUS_DP, true));
        if (!range.isEmpty()) {
            card.addView(FictionUi.mono(a, range, 10, CanvasLook.muted(), false));
        }
        card.addView(FictionUi.text(a, title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink2()));
        card.addView(FictionUi.text(a, line, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()));
        return card;
    }

    /** "Sir Francis Cromarty · left at Benares"; a tap opens the card. */
    private View alsoMetRow(FictionView.Card card) {
        final SpannableStringBuilder s = new SpannableStringBuilder(card.person.name);
        if (!card.line.isEmpty()) {
            final int from = s.length();
            s.append("  ·  ").append(card.line);
            s.setSpan(new ForegroundColorSpan(CanvasLook.muted()), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final TextView t = FictionUi.text(a, s, 13, 500, CanvasLook.ink2());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(CanvasLook.target());
        t.setPadding(CanvasLook.dp(2), CanvasLook.dp(4), CanvasLook.dp(2), CanvasLook.dp(4));
        t.setBackground(CanvasParts.ripple(null));
        t.setOnClickListener(v -> FictionNameCard.show(a, dc, cast, chapters, where, readerEnd, card.person, true));
        return t;
    }

    // ------------------------------------------------------------------ small views

    private TextView label(int text) {
        final TextView t = new TextView(a);
        CanvasParts.label(t, true);
        t.setText(text);
        return t;
    }

    private TextView muted(String text) {
        final TextView t = new TextView(a);
        CanvasParts.secondary(t);
        t.setText(text);
        return t;
    }
}
