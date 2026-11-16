package com.foobnix.assist.useit;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSegmented;
import com.foobnix.pdf.info.R;

import org.json.JSONException;

/**
 * The check-in of an experiment, full screen (canvas "Experiment check-in"): "TWO WEEKS LATER:
 * Did Smith's idea hold up?", each measure's value before and during as two bars with the
 * difference ("+7", "−5", "+1, worse"), the verdict (Held up / Mostly / Didn't) and "What
 * surprised you?". The reader types the values here; nothing is sent to the AI. It needs no open
 * book, so it also opens from the library's list. Saving marks the experiment done.
 */
final class UseCheckinScreen {

    private static final String[] VERDICTS = {UseExperiment.HELD, UseExperiment.MOSTLY, UseExperiment.DIDNT};
    /** The grey of the "before" bars on the canvas. */
    private static final int BEFORE = 0xFF8A909C;

    private final Activity a;
    private final String bookName;
    private final UseExperiment e;
    /** Runs after the experiment was saved or deleted (main thread). */
    private final Runnable onChanged;
    private UsePage page;
    private boolean saving;

    private UseCheckinScreen(Activity a, String bookName, UseExperiment e, Runnable onChanged) {
        this.a = a;
        this.bookName = bookName;
        this.e = e;
        this.onChanged = onChanged;
    }

    static void show(Activity a, String bookName, UseExperiment experiment, Runnable onChanged) {
        if (!Assist.USE_IT.isOn() || experiment == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        UseExperiment copy;
        try {
            // what is typed here is the reader's only once it is saved
            copy = UseExperiment.fromJson(experiment.toJson());
        } catch (JSONException ex) {
            copy = null;
        }
        if (copy != null) {
            new UseCheckinScreen(a, bookName, copy, onChanged).open();
        }
    }

    private void open() {
        final String today = UseStore.today();
        final String state = e.stateOn(today);
        page = new UsePage(a, a.getString(R.string.canvas_t23_checkin_title), UsePage.span(e.startDay,
                UseExperiment.DONE.equals(state) && UseDates.isDay(e.doneDay) ? e.doneDay : e.checkDay));

        final TextView delete = UsePage.text(a, a.getString(R.string.delete), 13, 600, CanvasLook.muted());
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(8), 0, dp(8), 0);
        delete.setBackground(CanvasParts.ripple(null));
        delete.setOnClickListener(v -> confirmDelete());
        page.right.addView(delete, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));

        // "TWO WEEKS LATER", or the day still ahead for a check-in made early
        final TextView label = UsePage.label(a, UseExperiment.RUNNING.equals(state)
                ? a.getString(R.string.canvas_t23_checkin_on, UsePage.dayLong(e.checkDay))
                : a.getString(e.weeks == 1 ? R.string.canvas_t23_later_1 : e.weeks == 4 ? R.string.canvas_t23_later_4
                : R.string.canvas_t23_later_2));
        label.setPadding(dp(4), 0, dp(4), 0);
        page.add(label, 4);
        final String name = UseExperiment.shortAuthor(e.author);
        final TextView h = UsePage.text(a, name.isEmpty() ? a.getString(R.string.canvas_t23_hold_up_book)
                : a.getString(R.string.canvas_t23_hold_up, name), 22, 600, CanvasLook.ink());
        h.setPadding(dp(4), 0, dp(4), 0);
        page.add(h, 4);

        // what was tried and what was predicted, to judge against
        final TextView tried = UsePage.text(a, e.tryText, 14, 600, CanvasLook.ink());
        tried.setLineSpacing(dp(2), 1f);
        tried.setPadding(dp(4), 0, dp(4), 0);
        page.add(tried, 10);
        if (!e.predicts.isEmpty()) {
            final TextView predicted = UsePage.text(a, (name.isEmpty() ? a.getString(R.string.canvas_t23_book_predicts)
                    : a.getString(R.string.canvas_t23_predicts, name)) + ": " + e.predicts, 13, 400, CanvasLook.ink2());
            predicted.setLineSpacing(dp(2), 1f);
            predicted.setPadding(dp(4), 0, dp(4), 0);
            page.add(predicted, 4);
        }

        if (!e.measures.isEmpty()) {
            page.add(logged(), 14);
        }

        final TextView verdictTitle = UsePage.text(a, a.getString(R.string.canvas_t23_verdict), 14, 600, CanvasLook.ink());
        verdictTitle.setPadding(dp(4), 0, 0, 0);
        page.add(verdictTitle, 16);
        final CanvasSegmented verdict = CanvasSegmented.create(a, a.getString(R.string.canvas_t23_held),
                a.getString(R.string.canvas_t23_mostly), a.getString(R.string.canvas_t23_didnt));
        int chosen = 1;
        for (int i = 0; i < VERDICTS.length; i++) {
            if (VERDICTS[i].equals(e.verdict)) {
                chosen = i;
            }
        }
        verdict.select(chosen);
        page.add(verdict.getView(), 6);

        final TextView surpriseTitle = UsePage.text(a, a.getString(R.string.canvas_t23_surprised), 14, 600, CanvasLook.ink());
        surpriseTitle.setPadding(dp(4), 0, 0, 0);
        page.add(surpriseTitle, 14);
        final EditText surprise = UsePage.field(a, e.surprise, "", true);
        page.add(surprise, 6);

        final TextView note = UsePage.text(a, a.getString(Assist.EDITION.isOn() ? R.string.canvas_t23_saved_edition
                : R.string.canvas_t23_saved_book), 12, 400, CanvasLook.muted());
        note.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.bottomMargin = dp(8);
        page.actions.addView(note, np);
        final TextView save = UsePage.primary(a, a.getString(R.string.save));
        save.setOnClickListener(v -> save(VERDICTS[Math.max(0, Math.min(VERDICTS.length - 1, verdict.getSelected()))],
                surprise.getText().toString()));
        page.actions.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        page.dialog.show();
    }

    /** "What you logged": per measure the difference, a bar and a field for before and for during. */
    private View logged() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(16)));

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UsePage.text(a, a.getString(R.string.canvas_t23_logged), 13, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        legend(head, BEFORE, R.string.canvas_t23_before);
        legend(head, CanvasLook.accent(), R.string.canvas_t23_during);
        box.addView(head);

        for (final UseExperiment.Measure m : e.measures) {
            final LinearLayout block = new LinearLayout(a);
            block.setOrientation(LinearLayout.VERTICAL);

            final LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            final TextView name = UsePage.text(a, m.label(), 13, 400, CanvasLook.ink());
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final TextView diff = UsePage.text(a, "", 12, 600, CanvasLook.accent());
            diff.setPadding(dp(8), 0, 0, 0);
            top.addView(diff);
            block.addView(top);

            final View[] beforeBar = new View[2];
            final View[] duringBar = new View[2];
            final Runnable refresh = () -> {
                final UseExperiment.Diff d = m.diff();
                diff.setText(d == null ? "" : d.worse ? a.getString(R.string.canvas_t23_worse, d.text) : d.text);
                diff.setTextColor(d != null && d.worse ? CanvasLook.sensor() : d != null && d.same ? CanvasLook.muted()
                        : CanvasLook.accent());
                share(beforeBar, m.share(m.before));
                share(duringBar, m.share(m.during));
            };
            block.addView(barRow(m, true, beforeBar, refresh), top(6));
            block.addView(barRow(m, false, duringBar, refresh), top(6));

            final TextView direction = UsePage.chip(a, "");
            direction.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(8)));
            final Runnable showDirection = () -> direction.setText(m.higherBetter ? R.string.canvas_t23_higher_better
                    : R.string.canvas_t23_lower_better);
            showDirection.run();
            direction.setOnClickListener(v -> {
                m.higherBetter = !m.higherBetter;
                showDirection.run();
                refresh.run();
            });
            final LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            chipParams.topMargin = dp(6);
            block.addView(direction, chipParams);
            refresh.run();
            box.addView(block, top(12));
        }
        return box;
    }

    private void legend(LinearLayout head, int color, int name) {
        final View square = new View(a);
        square.setBackground(CanvasParts.round(color, 0, dp(3)));
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(10), dp(10));
        sp.leftMargin = dp(10);
        sp.rightMargin = dp(4);
        head.addView(square, sp);
        head.addView(UsePage.text(a, a.getString(name), 11, 400, CanvasLook.ink2()));
    }

    /** A bar as long as the value's share, and the field the value is typed in. */
    private View barRow(UseExperiment.Measure m, boolean before, View[] bar, Runnable refresh) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        bar[0] = new View(a);
        bar[0].setBackground(CanvasParts.round(before ? BEFORE : CanvasLook.accent(), 0, dp(4)));
        bar[1] = new View(a);
        track.addView(bar[0], new LinearLayout.LayoutParams(0, dp(8), 0f));
        track.addView(bar[1], new LinearLayout.LayoutParams(0, dp(8), 1f));
        row.addView(track, new LinearLayout.LayoutParams(0, dp(8), 1));
        final EditText value = UsePage.number(a, before ? m.before : m.during,
                a.getString(before ? R.string.canvas_t23_before : R.string.canvas_t23_during));
        value.setTextColor(before ? CanvasLook.ink2() : CanvasLook.ink());
        UsePage.onText(value, s -> {
            if (before) {
                m.before = UseExperiment.parseNumber(s);
            } else {
                m.during = UseExperiment.parseNumber(s);
            }
            refresh.run();
        });
        final LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(76), dp(40));
        vp.leftMargin = dp(8);
        row.addView(value, vp);
        return row;
    }

    private static void share(View[] bar, float share) {
        if (bar[0] == null) {
            return;
        }
        final float s = Math.max(0f, Math.min(1f, share));
        ((LinearLayout.LayoutParams) bar[0].getLayoutParams()).weight = s;
        ((LinearLayout.LayoutParams) bar[1].getLayoutParams()).weight = 1f - s;
        bar[0].setVisibility(s > 0 ? View.VISIBLE : View.INVISIBLE);
        bar[0].requestLayout();
    }

    // ------------------------------------------------------------------ saving

    private void save(String verdict, String surprise) {
        if (saving) {
            return;
        }
        if (!Assist.USE_IT.isOn()) {
            page.dialog.dismiss();
            return;
        }
        saving = true;
        e.finish(UseStore.today(), verdict, surprise);
        UseStore.IO.execute(() -> {
            String failure = null;
            try {
                UseStore.save(bookName, e);
            } catch (Exception ex) {
                LOG.e(ex);
                failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
            done(failure, R.string.canvas_t23_checked_in);
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(a)
                .setMessage(R.string.canvas_t23_delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (saving) {
                        return;
                    }
                    saving = true;
                    UseStore.IO.execute(() -> {
                        String failure = null;
                        try {
                            UseStore.delete(bookName, e.id);
                        } catch (Exception ex) {
                            LOG.e(ex);
                            failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                        }
                        done(failure, 0);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** After a write: says how it went, closes the screen, tells the caller. */
    private void done(String failure, int message) {
        a.runOnUiThread(() -> {
            saving = false;
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            if (failure != null) {
                Toast.makeText(a, a.getString(R.string.canvas_t23_not_saved, failure), Toast.LENGTH_LONG).show();
                return;
            }
            if (message != 0) {
                Toast.makeText(a, message, Toast.LENGTH_SHORT).show();
            }
            page.dialog.dismiss();
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }

    private static LinearLayout.LayoutParams top(int topDp) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
