package com.foobnix.assist.useit;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;

/**
 * The lists of experiments, full screen: a book's own (due, running, and the done ones under
 * "Tried it", with "New experiment" at the foot; from the ✦ menu's "Use it" row once the book has
 * one), and the library's "to check in" list of every book's due experiments (from the main
 * screen's banner). A tap opens the check-in ({@link UseCheckinScreen}).
 */
final class UseListScreen {

    private final Activity a;
    /** The open book, or null for the library's list. */
    private final DocumentController dc;
    private final String bookName;
    /** Runs when the library's list changed (main thread). */
    private final Runnable onChanged;
    private UsePage page;

    private UseListScreen(Activity a, DocumentController dc, Runnable onChanged) {
        this.a = a;
        this.dc = dc;
        this.bookName = dc == null ? null : AiHighlights.bookName(dc.getCurrentBook().getPath());
        this.onChanged = onChanged;
    }

    /** The experiments of the open book. */
    static void show(Activity a, DocumentController dc, List<UseExperiment> list) {
        if (!Assist.USE_IT.isOn() || dc == null || dc.getCurrentBook() == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        final UseListScreen s = new UseListScreen(a, dc, null);
        s.page = new UsePage(a, a.getString(R.string.canvas_t23_title), dc.getTitle());
        s.book(list);
        s.page.dialog.show();
    }

    /** Every book's experiments that are due. */
    static void showDue(Activity a, List<UseIndex.Entry> due, Runnable onChanged) {
        if (!Assist.USE_IT.isOn() || Apps.isDestroyedActivity(a)) {
            return;
        }
        final UseListScreen s = new UseListScreen(a, null, onChanged);
        s.page = new UsePage(a, a.getString(R.string.canvas_t23_due_list), "");
        s.library(due);
        s.page.dialog.show();
    }

    // ------------------------------------------------------------------ a book's experiments

    private void book(List<UseExperiment> list) {
        page.content.removeAllViews();
        page.actions.removeAllViews();
        final String today = UseStore.today();
        String section = null;
        for (final UseExperiment e : list) {
            final String state = e.stateOn(today);
            if (!state.equals(section)) {
                section = state;
                section(UseExperiment.DUE.equals(state) ? R.string.canvas_t23_section_due
                        : UseExperiment.RUNNING.equals(state) ? R.string.canvas_t23_section_running
                        : R.string.canvas_t23_section_done);
            }
            final String line;
            if (UseExperiment.DONE.equals(state)) {
                line = join(verdict(e.verdict), UsePage.day(e.doneDay, "MMM d"));
            } else if (UseExperiment.DUE.equals(state)) {
                line = a.getString(R.string.canvas_t23_was_due, UsePage.dayLong(e.checkDay));
            } else {
                final int left = e.daysLeft(today);
                line = join(a.getString(R.string.canvas_t23_checkin_on, UsePage.dayLong(e.checkDay)),
                        left == 1 ? a.getString(R.string.canvas_t23_day_left)
                                : a.getString(R.string.canvas_t23_days_left, left));
            }
            row(e.tryText, line, UseExperiment.DUE.equals(state),
                    () -> UseCheckinScreen.show(a, bookName, e, this::reloadBook));
        }
        if (list.isEmpty()) {
            empty(R.string.canvas_t23_none);
        }
        final TextView add = UsePage.primary(a, a.getString(R.string.canvas_t23_new));
        add.setOnClickListener(v -> UseItScreen.show(a, dc, this::reloadBook));
        page.actions.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
    }

    private void reloadBook() {
        UseStore.IO.execute(() -> {
            final List<UseExperiment> list = UseStore.list(bookName, UseStore.today());
            ui(() -> book(list));
        });
    }

    private String verdict(String v) {
        if (UseExperiment.HELD.equals(v)) {
            return a.getString(R.string.canvas_t23_held);
        }
        if (UseExperiment.DIDNT.equals(v)) {
            return a.getString(R.string.canvas_t23_didnt);
        }
        return UseExperiment.MOSTLY.equals(v) ? a.getString(R.string.canvas_t23_mostly) : "";
    }

    // ------------------------------------------------------------------ the library's due list

    private void library(List<UseIndex.Entry> due) {
        page.content.removeAllViews();
        page.actions.removeAllViews();
        for (final UseIndex.Entry entry : due) {
            row(entry.title, join(entry.bookTitle, a.getString(R.string.canvas_t23_was_due, UsePage.dayLong(entry.day))),
                    true, () -> checkIn(entry));
        }
        if (due.isEmpty()) {
            empty(R.string.canvas_t23_none_due);
        }
    }

    /** Reads the experiment from its book's file and opens its check-in; a stale entry is dropped. */
    private void checkIn(UseIndex.Entry entry) {
        UseStore.IO.execute(() -> {
            final UseExperiment e = UseStore.get(entry.book, entry.id);
            final boolean gone = e == null || UseExperiment.DONE.equals(e.state);
            if (gone) {
                try {
                    UseStore.unindex(entry.id);
                } catch (Exception ex) {
                    LOG.e(ex);
                }
            }
            ui(() -> {
                if (gone) {
                    Toast.makeText(a, R.string.canvas_t23_gone, Toast.LENGTH_LONG).show();
                    reloadLibrary();
                } else {
                    UseCheckinScreen.show(a, entry.book, e, this::reloadLibrary);
                }
            });
        });
    }

    private void reloadLibrary() {
        UseStore.IO.execute(() -> {
            final List<UseIndex.Entry> due = UseStore.due(UseStore.today());
            ui(() -> {
                library(due);
                if (onChanged != null) {
                    onChanged.run();
                }
                if (due.isEmpty()) {
                    page.dialog.dismiss();
                }
            });
        });
    }

    // ------------------------------------------------------------------ parts

    private void section(int name) {
        final TextView t = new TextView(a);
        t.setText(name);
        CanvasParts.label(t, false);
        t.setPadding(CanvasLook.dp(4), 0, 0, 0);
        page.add(t, 16);
    }

    private void empty(int text) {
        final TextView t = UsePage.text(a, a.getString(text), 14, 400, CanvasLook.muted());
        t.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(4), 0);
        page.add(t, 16);
    }

    /** A card: what is tried, and a line under it (in the accent when it waits for a check-in). */
    private void row(String title, String line, boolean due, Runnable onTap) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(14);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(),
                due ? CanvasLook.accentLine() : CanvasLook.line(), CanvasLook.dp(14))));
        final TextView t = UsePage.text(a, title, 15, 600, CanvasLook.ink());
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        card.addView(t);
        if (!line.isEmpty()) {
            final TextView l = UsePage.text(a, line, 12, 400, due ? CanvasLook.accent() : CanvasLook.muted());
            l.setPadding(0, CanvasLook.dp(4), 0, 0);
            card.addView(l);
        }
        card.setOnClickListener(v -> onTap.run());
        page.add(card, 8);
    }

    private static String join(String first, String second) {
        final boolean a = first != null && !first.trim().isEmpty();
        final boolean b = second != null && !second.trim().isEmpty();
        return a && b ? first.trim() + " · " + second.trim() : a ? first.trim() : b ? second.trim() : "";
    }

    private void ui(Runnable r) {
        a.runOnUiThread(() -> {
            if (!Apps.isDestroyedActivity(a) && page.dialog.isShowing()) {
                r.run();
            }
        });
    }
}
