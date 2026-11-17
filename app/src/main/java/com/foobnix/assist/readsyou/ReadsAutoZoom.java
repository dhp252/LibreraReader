package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.zoom.ZoomData;
import com.foobnix.assist.zoom.ZoomText;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Auto-zoom" in the zoom screen (canvas artboard "Auto-zoom", Assist.READS_YOU): one more entry
 * under the zoom bar, shown only for a chapter that has zoom levels and stored page counters.
 * Turned on, it shows each section at the level its measured pace suggests ({@link AutoZoom}):
 * read fast, its one line; at the reader's pace, lean; slowly, re-read or long on a page, the
 * book's full text. Each changed section says why in a caption, with the pages it was measured
 * on; "Show all" opens a squeezed section, "Undo" goes back to the level picked before. It only
 * reads what is stored and the book's own text; nothing is sent to the AI.
 */
public final class ReadsAutoZoom {

    /** Characters of one section's full text shown at most. */
    private static final int FULL_CHARS = 20_000;

    private final Activity a;
    private final AiJobs.Target target;
    private final String book;
    private final Runnable rerender;
    private final TextView link;

    private boolean on;
    private ZoomData data;
    private BookChapters.Range range;
    private ReadsSignals signals;
    private double pace;
    /** The chapter's text in the reader's layout; null until read. */
    private ZoomText text;
    private boolean readingText;
    /** Sections the reader opened with "Show all". */
    private final Set<Integer> opened = new HashSet<>();

    /**
     * @param bar      the box under the zoom bar the "Auto-zoom" entry is added to
     * @param rerender draws the zoom screen again
     */
    public ReadsAutoZoom(Activity a, AiJobs.Target target, LinearLayout bar, Runnable rerender) {
        this.a = a;
        this.target = target;
        this.book = AiHighlights.bookName(target.path);
        this.rerender = rerender;
        link = ReadsUi.text(a, a.getString(R.string.canvas_t33_auto), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setMinHeight(CanvasLook.dp(32));
        link.setVisibility(View.GONE);
        link.setOnClickListener(v -> {
            on = !on;
            opened.clear();
            if (on) {
                readText();
            }
            rerender.run();
        });
        bar.addView(link, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** A level of the zoom bar was picked: auto-zoom steps back. */
    public void off() {
        on = false;
        opened.clear();
    }

    /**
     * Called when the zoom screen draws a chapter's levels: shows or hides the entry, and when
     * auto-zoom is on draws the chapter into {@code body}.
     *
     * @return true when it drew the chapter (the screen then draws no level of its own)
     */
    public boolean render(LinearLayout body, ZoomData data, BookChapters.Range range) {
        this.data = data;
        if (range != null && (this.range == null || !range.key.equals(this.range.key))) {
            this.range = range;
            signals = null;
            text = null;
            on = false;
            load(range);
        }
        final boolean can = Assist.READS_YOU.isOn() && data != null && !data.sections.isEmpty() && this.range != null
                && signals != null && !signals.isEmpty();
        link.setVisibility(can ? View.VISIBLE : View.GONE);
        link.setText(on ? R.string.canvas_t33_auto_on : R.string.canvas_t33_auto);
        if (!can || !on) {
            return false;
        }
        draw(body);
        return true;
    }

    private void load(final BookChapters.Range r) {
        ReadsStore.IO.execute(() -> {
            final ReadsSignals s = ReadsStore.signals(book, r.key);
            s.fit(r.pages());
            final double p = ReadsStore.state(book).pace;
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a) || range != r) {
                    return;
                }
                signals = s;
                pace = p;
                if (!s.isEmpty()) {
                    rerender.run();
                }
            });
        });
    }

    /** The chapter's own text, for the sections opened to full. Off the main thread; nothing is sent. */
    private void readText() {
        if (readingText || text != null || range == null) {
            return;
        }
        readingText = true;
        final BookChapters.Range r = range;
        new Thread(() -> {
            final List<String> pages = new ArrayList<>();
            try (BookText t = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                if (t != null) {
                    for (int p = Math.max(0, r.from); p < Math.min(t.pageCount(), r.to); p++) {
                        pages.add(BookText.plain(t.body(p)));
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final ZoomText read = ZoomText.of(pages);
            a.runOnUiThread(() -> {
                readingText = false;
                if (Apps.isDestroyedActivity(a) || range != r) {
                    return;
                }
                text = read;
                if (on) {
                    rerender.run();
                }
            });
        }, "@T Auto-zoom text").start();
    }

    private void draw(LinearLayout body) {
        final List<AutoZoom.Choice> choices = AutoZoom.choose(data, signals, range.pages(), pace);
        final int changes = AutoZoom.changes(choices);

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ReadsUi.mono(a, a.getString(R.string.canvas_t33_auto_head, changes), CanvasLook.sensor()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView undo = ReadsUi.text(a, a.getString(R.string.canvas_t33_auto_undo), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        undo.setGravity(Gravity.CENTER);
        undo.setMinHeight(CanvasLook.target());
        undo.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(4), 0);
        undo.setOnClickListener(v -> {
            off();
            rerender.run();
        });
        head.addView(undo);
        body.addView(head);
        final int wpm = ReadsRules.wpm(pace);
        final TextView note = ReadsUi.text(a, a.getString(changes > 0 ? R.string.canvas_t33_auto_note : R.string.canvas_t33_auto_note_none)
                + " " + (wpm > 0 ? a.getString(R.string.canvas_t32_legend_pace, wpm) : a.getString(R.string.canvas_t33_auto_no_pace)),
                CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        note.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(note);

        for (int i = 0; i < data.sections.size(); i++) {
            final ZoomData.Section s = data.sections.get(i);
            final AutoZoom.Choice c = choices.get(i);
            final int index = i;
            final boolean shownAll = opened.contains(i);
            if (!s.title.isEmpty()) {
                body.addView(ReadsUi.mono(a, s.title, CanvasLook.accent()), ReadsUi.matchWrap(18));
            }
            int level = shownAll ? AutoZoom.FULL : c.level;
            String full = null;
            if (level == AutoZoom.FULL) {
                full = text == null ? null : fullText(i);
                if (text != null && full == null) {
                    // its place was not found in the book's text as it is laid out now
                    level = AutoZoom.LEAN;
                }
            }
            if (c.changed()) {
                final String pages = a.getString(R.string.ai_pages, range.from + c.from + 1, range.from + c.to);
                final String now = a.getString(shownAll ? R.string.canvas_t33_auto_now_all
                        : c.level == AutoZoom.LINE ? R.string.canvas_t33_auto_now_line
                        : level == AutoZoom.FULL ? R.string.canvas_t33_auto_now_full : R.string.canvas_t33_auto_now_lean);
                body.addView(ReadsUi.mono(a, pages + " · " + a.getString(why(c.why)) + " · " + now, CanvasLook.sensor()),
                        ReadsUi.matchWrap(s.title.isEmpty() ? 18 : 6));
            }
            if (level == AutoZoom.FULL) {
                body.addView(words(full == null ? a.getString(R.string.canvas_t33_auto_reading) : full, full != null),
                        ReadsUi.matchWrap(8));
            } else if (level == AutoZoom.LINE) {
                body.addView(words(s.line, true), ReadsUi.matchWrap(8));
                final TextView all = ReadsUi.text(a, a.getString(R.string.canvas_t33_auto_show_all), CanvasLook.TEXT_SMALL, 600,
                        CanvasLook.accent());
                all.setGravity(Gravity.CENTER_VERTICAL);
                all.setMinHeight(CanvasLook.target());
                all.setOnClickListener(v -> {
                    opened.add(index);
                    readText();
                    rerender.run();
                });
                body.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                lean(body, s);
            }
        }
    }

    private static int why(int why) {
        switch (why) {
            case AutoZoom.FAST:
                return R.string.canvas_t33_auto_why_fast;
            case AutoZoom.REREAD:
                return R.string.canvas_t33_auto_why_reread;
            case AutoZoom.LONG:
                return R.string.canvas_t33_auto_why_long;
            default:
                return R.string.canvas_t33_auto_why_slow;
        }
    }

    private TextView words(String s, boolean book) {
        final TextView t = book ? ReadsUi.book(a, s, 17, CanvasLook.ink())
                : ReadsUi.text(a, s, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        t.setLineSpacing(CanvasLook.dp(4), 1f);
        t.setTextIsSelectable(book);
        return t;
    }

    /** The section's lean version as stored: its condensed sentences, and what was folded away by name. */
    private void lean(LinearLayout body, ZoomData.Section s) {
        boolean any = false;
        for (ZoomData.Block block : s.blocks) {
            if (block.fold) {
                body.addView(ReadsUi.text(a, a.getString(R.string.zoom_folded, block.label), 13, 400, CanvasLook.ink2()),
                        ReadsUi.matchWrap(8));
                any = true;
                continue;
            }
            final StringBuilder p = new StringBuilder();
            for (ZoomData.Sentence x : block.sentences) {
                if (x.text != null && !x.text.trim().isEmpty()) {
                    p.append(p.length() > 0 ? " " : "").append(x.text.trim());
                }
            }
            if (p.length() > 0) {
                body.addView(words(p.toString(), true), ReadsUi.matchWrap(8));
                any = true;
            }
        }
        if (!any && s.line != null && !s.line.trim().isEmpty()) {
            body.addView(words(s.line, true), ReadsUi.matchWrap(8));
        }
    }

    /**
     * The book's own text of a section: from the paragraph its first words are in up to the one
     * before the next section's; null when its first words are not found in the chapter's text.
     */
    private String fullText(int index) {
        try {
            final ZoomText.Place from = text.locate(data.sections.get(index).start);
            if (from == null) {
                return null;
            }
            final String[] paras = text.plain().split("\n\n");
            int to = paras.length;
            for (int j = index + 1; j < data.sections.size(); j++) {
                final ZoomText.Place next = text.locate(data.sections.get(j).start);
                if (next != null && next.para > from.para) {
                    to = next.para - 1;
                    break;
                }
            }
            final StringBuilder out = new StringBuilder();
            for (int p = Math.max(0, from.para - 1); p < Math.min(paras.length, to); p++) {
                if (out.length() + paras[p].length() > FULL_CHARS && out.length() > 0) {
                    out.append("\n\n…");
                    break;
                }
                out.append(out.length() > 0 ? "\n\n" : "").append(paras[p]);
            }
            return out.length() == 0 ? null : out.toString();
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }
}
