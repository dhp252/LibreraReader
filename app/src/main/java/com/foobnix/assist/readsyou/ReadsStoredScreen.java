package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

/**
 * "What is stored" (the canvas's "See exactly what's stored"): everything "The book reads you
 * back" keeps for the open book, item by item as it is in the book's assist file, with the raw
 * data on request and "Delete these signals". Opened from the READING YOU strip and the ✦ menu.
 * It only reads the file; nothing is sent.
 */
final class ReadsStoredScreen {

    private final Activity a;
    private final String book;
    private final String sessionLine;
    private final Runnable onDeleted;
    private Dialog dialog;
    private LinearLayout body;
    private boolean raw;
    private Map<String, JSONObject> items;

    private ReadsStoredScreen(Activity a, String book, String sessionLine, Runnable onDeleted) {
        this.a = a;
        this.book = book;
        this.sessionLine = sessionLine;
        this.onDeleted = onDeleted;
    }

    /**
     * @param sessionLine what the running session measured so far (not stored), or null
     * @param onDeleted   runs after "Delete these signals"
     */
    static void show(Activity a, DocumentController dc, String sessionLine, Runnable onDeleted) {
        if (a == null || Apps.isDestroyedActivity(a) || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final ReadsStoredScreen s = new ReadsStoredScreen(a, AiHighlights.bookName(dc.getCurrentBook().getPath()),
                sessionLine, onDeleted);
        s.open(dc.getTitle());
    }

    private void open(String bookTitle) {
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(ReadsUi.topBar(a, a.getString(R.string.canvas_t31_stored_title), bookTitle, () -> dialog.dismiss()));
        final ScrollView scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(8), gutter, CanvasLook.dp(24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        dialog = ReadsUi.fullScreen(a, page);
        dialog.show();
        load();
    }

    private void load() {
        ReadsStore.IO.execute(() -> {
            final Map<String, JSONObject> found = ReadsStore.all(book);
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    items = found;
                    render();
                }
            });
        });
    }

    private void render() {
        body.removeAllViews();
        final TextView intro = ReadsUi.text(a, a.getString(R.string.canvas_t31_stored_intro), 13, 400, CanvasLook.ink2());
        intro.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(intro);
        // T33: what Eye mode's camera adds, and that it stays here
        final TextView camera = ReadsUi.text(a, a.getString(R.string.canvas_t33_stays), 13, 400, CanvasLook.ink2());
        camera.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(camera, ReadsUi.matchWrap(8));

        if (sessionLine != null) {
            body.addView(card(a.getString(R.string.canvas_t31_stored_session), null, sessionLine), ReadsUi.matchWrap(14));
        }
        if (items == null || items.isEmpty()) {
            final TextView none = ReadsUi.text(a, a.getString(R.string.canvas_t31_stored_none), CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
            body.addView(none, ReadsUi.matchWrap(18));
            return;
        }
        final JSONObject state = items.get(ReadsState.KEY);
        if (state != null) {
            body.addView(card(ReadsState.KEY, null, stateText(ReadsState.fromJson(state))), ReadsUi.matchWrap(14));
        }
        for (Map.Entry<String, JSONObject> e : items.entrySet()) {
            if (!e.getKey().startsWith(ReadsSignals.PREFIX)) {
                continue;
            }
            final ReadsSignals s = ReadsSignals.fromJson(e.getValue());
            body.addView(card(e.getKey(), s.title, signalsText(s)), ReadsUi.matchWrap(10));
        }
        for (Map.Entry<String, JSONObject> e : items.entrySet()) {
            if (!e.getKey().equals(ReadsState.KEY) && !e.getKey().startsWith(ReadsSignals.PREFIX)) {
                // anything else under the feature's prefix is shown as it is
                body.addView(card(e.getKey(), null, e.getValue().toString()), ReadsUi.matchWrap(10));
            }
        }

        final TextView rawLink = ReadsUi.text(a, a.getString(raw ? R.string.canvas_t31_stored_raw_hide
                : R.string.canvas_t31_stored_raw_show), CanvasLook.TEXT_TITLE, 600, CanvasLook.accent());
        rawLink.setGravity(Gravity.CENTER_VERTICAL);
        rawLink.setMinHeight(CanvasLook.target());
        rawLink.setOnClickListener(v -> {
            raw = !raw;
            render();
        });
        body.addView(rawLink, ReadsUi.wrap(10, 0));
        if (raw) {
            final StringBuilder all = new StringBuilder();
            for (Map.Entry<String, JSONObject> e : items.entrySet()) {
                try {
                    all.append(e.getKey()).append('\n').append(e.getValue().toString(2)).append("\n\n");
                } catch (Exception ex) {
                    LOG.e(ex);
                }
            }
            final TextView text = ReadsUi.text(a, all.toString().trim(), CanvasLook.TEXT_LABEL, 400, CanvasLook.ink2());
            text.setTypeface(CanvasLook.mono(a, 400));
            text.setTextIsSelectable(true);
            text.setPadding(CanvasLook.dp(12), CanvasLook.dp(12), CanvasLook.dp(12), CanvasLook.dp(12));
            text.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP)));
            body.addView(text, ReadsUi.matchWrap(4));
        }

        final TextView delete = ReadsUi.button(a, a.getString(R.string.canvas_t31_stored_delete), false);
        delete.setTextColor(CanvasLook.sensor());
        delete.setOnClickListener(v -> new AlertDialog.Builder(a)
                .setMessage(R.string.canvas_t31_stored_delete_ask)
                .setPositiveButton(R.string.canvas_t31_stored_delete, (d, w) -> delete())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        body.addView(delete, ReadsUi.matchWrap(18));
    }

    private void delete() {
        ReadsStore.IO.execute(() -> {
            ReadsStore.deleteAll(book);
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (onDeleted != null) {
                    onDeleted.run();
                }
                Toast.makeText(a, R.string.canvas_t31_stored_deleted, Toast.LENGTH_SHORT).show();
                if (dialog.isShowing()) {
                    load();
                }
            });
        });
    }

    /** One stored item: its key as it is in the file, the chapter's title, and what its numbers say. */
    private View card(String key, String title, String text) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_DP)));
        final TextView k = ReadsUi.text(a, key, CanvasLook.TEXT_LABEL, 500, CanvasLook.muted());
        k.setTypeface(CanvasLook.mono(a, 500));
        card.addView(k);
        if (title != null && !title.isEmpty()) {
            final TextView t = ReadsUi.text(a, title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
            t.setPadding(0, CanvasLook.dp(4), 0, 0);
            card.addView(t);
        }
        final TextView body = ReadsUi.text(a, text, 13, 400, CanvasLook.ink2());
        body.setLineSpacing(CanvasLook.dp(2), 1f);
        body.setPadding(0, CanvasLook.dp(4), 0, 0);
        card.addView(body);
        return card;
    }

    private String stateText(ReadsState s) {
        final StringBuilder out = new StringBuilder();
        final int wpm = ReadsRules.wpm(s.pace);
        out.append(wpm > 0 ? a.getString(R.string.canvas_t31_stored_pace, wpm, s.paceSamples)
                : a.getString(R.string.canvas_t31_stored_pace_none));
        out.append('\n').append(s.snoozeUntil > System.currentTimeMillis()
                ? a.getString(R.string.canvas_t31_stored_snooze,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(s.snoozeUntil)))
                : a.getString(R.string.canvas_t31_stored_snooze_none));
        out.append('\n').append(a.getString(R.string.canvas_t31_stored_breaks, s.breaksOffered, s.breaksTaken, s.listens));
        return out.toString();
    }

    private String signalsText(ReadsSignals s) {
        final ReadsSignals.Page t = s.total();
        final String text = a.getString(R.string.canvas_t31_stored_signals, s.byPage.size(), s.pages,
                ReadsUi.clock(t.seconds * 1000L), t.rereads, t.longs, t.offers, t.helps, t.breaks, t.drifts);
        return t.watched > 0 || t.away > 0 || t.passes > 0 ? text + "\n" + a.getString(R.string.canvas_t33_stored_camera,
                ReadsUi.clock(t.watched * 1000L), ReadsUi.clock(t.away * 1000L), t.passes) : text;
    }
}
