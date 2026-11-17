package com.foobnix.assist.pictures;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.List;

/**
 * The pictures as native views in the canvas's tokens (Picture, PictureTable, PictureLoop): the
 * "FROM THE TEXT" card, a process's rows of step boxes, a table's grid with its best cells
 * tinted, a loop with what holds it back as compared bars, and the "why it works" cards.
 */
final class PictureViews {

    /** Characters of a quote shown; the whole of it is a tap away, in the book. */
    private static final int QUOTE_SHOWN = 260;
    /** A table column is at least this wide, dp; narrower and the table scrolls sideways. */
    private static final int MIN_COLUMN_DP = 84;

    private PictureViews() {
    }

    /** "FROM THE TEXT · p. 12" over the book's own words; a tap goes to the passage. */
    static View fromTheText(Context c, String quote, int page, Runnable jump) {
        final LinearLayout card = PictureUi.column(c);
        card.setPadding(CanvasLook.dp(20), CanvasLook.dp(8), CanvasLook.dp(20), CanvasLook.dp(8));
        card.setBackground(CanvasParts.ripple(null));
        final String label = page >= 0 ? c.getString(R.string.canvas_t27_from_text_page, page + 1)
                : c.getString(R.string.canvas_t27_from_text);
        card.addView(PictureUi.mono(c, label, CanvasLook.muted()));
        final TextView words = PictureUi.quote(c, "“" + PictureText.cut(quote, QUOTE_SHOWN) + "”");
        words.setPadding(0, CanvasLook.dp(4), 0, 0);
        card.addView(words);
        card.setContentDescription(label + ". " + quote + ". " + c.getString(R.string.canvas_t27_go_to_passage));
        card.setOnClickListener(v -> jump.run());
        return card;
    }

    /** A picture's or a block's heading: 16 sp, weight 600. */
    static TextView heading(Context c, CharSequence text) {
        final TextView t = PictureUi.text(c, text, 16, 600, CanvasLook.ink());
        t.setLineSpacing(CanvasLook.dp(2), 1f);
        return t;
    }

    // ------------------------------------------------------------------ a process

    /** The step boxes in wrapping rows with arrows between, and the caption under them. */
    static View process(Context c, Picture p) {
        final LinearLayout block = PictureUi.column(c);
        final PictureFlowView flow = new PictureFlowView(c);
        final StringBuilder says = new StringBuilder();
        for (Picture.Step step : p.steps) {
            final LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setMinimumHeight(CanvasLook.dp(30));
            box.setPadding(CanvasLook.dp(10), CanvasLook.dp(4), CanvasLook.dp(10), CanvasLook.dp(4));
            box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.dp(8)));
            final TextView label = PictureUi.text(c, step.label, 13, 400, CanvasLook.ink());
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (!step.note.isEmpty()) {
                final TextView note = PictureUi.mono(c, step.note, CanvasLook.muted());
                note.setAllCaps(false);
                note.setLetterSpacing(0f);
                note.setSingleLine(true);
                note.setPadding(CanvasLook.dp(6), 0, 0, 0);
                box.addView(note);
            }
            flow.addView(box);
            says.append(says.length() > 0 ? ", " : "").append(step.label);
        }
        flow.setContentDescription(says);
        block.addView(flow);
        if (!p.caption.isEmpty()) {
            final TextView caption = PictureUi.text(c, p.caption, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            caption.setPadding(0, CanvasLook.dp(10), 0, 0);
            block.addView(caption);
        }
        return block;
    }

    // ------------------------------------------------------------------ a table

    /**
     * The grid: a head row, a row per thing compared, the best cells tinted. Columns share the
     * width while each keeps {@link #MIN_COLUMN_DP}; more columns scroll sideways.
     *
     * @param available the width the table has, px
     */
    static View table(Context c, Picture p, int available) {
        final LinearLayout block = PictureUi.column(c);
        final int columns = p.columns.size();
        final int min = CanvasLook.dp(MIN_COLUMN_DP);
        final boolean scrolls = PictureLayout.tableScrolls(columns, available, min);
        final int width = PictureLayout.tableColumn(columns, available, min);

        final LinearLayout grid = PictureUi.column(c);
        final LinearLayout head = row(c);
        final TextView corner = PictureUi.mono(c, p.corner, CanvasLook.muted());
        corner.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        corner.setPadding(0, CanvasLook.dp(8), CanvasLook.dp(8), CanvasLook.dp(8));
        head.addView(corner, cell(width, scrolls));
        for (String name : p.columns) {
            final TextView t = PictureUi.text(c, name, 13, 600, CanvasLook.ink());
            t.setPadding(CanvasLook.dp(8), CanvasLook.dp(8), CanvasLook.dp(8), CanvasLook.dp(8));
            head.addView(t, cell(width, scrolls));
        }
        grid.addView(head);
        grid.addView(rule(c, CanvasLook.lineStrong()));
        boolean tinted = false;
        for (int r = 0; r < p.rows.size(); r++) {
            final Picture.Row data = p.rows.get(r);
            if (r > 0) {
                grid.addView(rule(c, CanvasLook.surface2()));
            }
            final LinearLayout line = row(c);
            final TextView name = PictureUi.text(c, data.label, 13, 600, CanvasLook.ink());
            name.setPadding(0, CanvasLook.dp(10), CanvasLook.dp(8), CanvasLook.dp(10));
            line.addView(name, cell(width, scrolls));
            for (int i = 0; i < columns; i++) {
                final String words = i < data.cells.size() ? data.cells.get(i) : "";
                final boolean best = i < data.best.size() && data.best.get(i) && !words.isEmpty();
                final TextView t = PictureUi.text(c, words.isEmpty() ? "—" : words, 13, best ? 600 : 400,
                        words.isEmpty() ? CanvasLook.muted() : CanvasLook.ink());
                t.setPadding(CanvasLook.dp(8), CanvasLook.dp(10), CanvasLook.dp(8), CanvasLook.dp(10));
                if (words.isEmpty()) {
                    t.setContentDescription(c.getString(R.string.canvas_t27_cell_empty));
                }
                if (best) {
                    t.setBackground(CanvasParts.round(CanvasLook.claimTint(), 0, CanvasLook.dp(8)));
                    t.setContentDescription(words + ". " + (p.bestMeans.isEmpty() ? c.getString(R.string.canvas_t27_best) : p.bestMeans));
                    tinted = true;
                }
                line.addView(t, cell(width, scrolls));
            }
            grid.addView(line);
        }
        if (scrolls) {
            final HorizontalScrollView scroll = new HorizontalScrollView(c);
            scroll.setHorizontalScrollBarEnabled(true);
            scroll.setScrollbarFadingEnabled(false);
            scroll.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            block.addView(scroll);
        } else {
            block.addView(grid);
        }
        if (tinted) {
            final LinearLayout legend = row(c);
            legend.setGravity(Gravity.CENTER_VERTICAL);
            legend.setPadding(0, CanvasLook.dp(10), 0, 0);
            final View swatch = new View(c);
            swatch.setBackground(CanvasParts.round(CanvasLook.claimTint(), CanvasLook.accentLine(), CanvasLook.dp(3)));
            legend.addView(swatch, new LinearLayout.LayoutParams(CanvasLook.dp(12), CanvasLook.dp(12)));
            final TextView means = PictureUi.text(c, p.bestMeans.isEmpty() ? c.getString(R.string.canvas_t27_best) : p.bestMeans,
                    CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            means.setPadding(CanvasLook.dp(6), 0, 0, 0);
            legend.addView(means);
            block.addView(legend);
        }
        return block;
    }

    private static LinearLayout row(Context c) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /** A cell's place in its row: a fixed width when the table scrolls, else an equal share; as tall as the row. */
    private static LinearLayout.LayoutParams cell(int width, boolean scrolls) {
        return scrolls ? new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private static View rule(Context c, int color) {
        final View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));
        return v;
    }

    /** "THE POINT" in the canvas's grey block. */
    static View point(Context c, String point) {
        final LinearLayout card = PictureUi.column(c);
        card.setBackground(CanvasParts.round(PictureUi.block(), 0, CanvasLook.dp(14)));
        final int pad = CanvasLook.dp(14);
        card.setPadding(pad, pad, pad, pad);
        card.addView(PictureUi.mono(c, c.getString(R.string.canvas_t27_the_point), CanvasLook.accent()));
        final TextView text = PictureUi.text(c, point, 14, 400, CanvasLook.ink());
        text.setLineSpacing(CanvasLook.dp(3), 1f);
        text.setPadding(0, CanvasLook.dp(6), 0, 0);
        card.addView(text);
        return card;
    }

    // ------------------------------------------------------------------ a loop

    /** The loop drawn round its ellipse. */
    static View loop(Context c, Picture p) {
        final PictureLoopView view = new PictureLoopView(c);
        final StringBuilder says = new StringBuilder();
        for (String node : p.nodes) {
            says.append(node).append(" → ");
        }
        says.append(p.nodes.get(0));
        final String mark;
        if (p.loopType > 0) {
            mark = c.getString(R.string.canvas_t27_loop_r);
            says.append(". ").append(c.getString(R.string.canvas_t27_loop_reinforcing));
        } else if (p.loopType < 0) {
            mark = c.getString(R.string.canvas_t27_loop_b);
            says.append(". ").append(c.getString(R.string.canvas_t27_loop_balancing));
        } else {
            mark = "";
        }
        if (!p.loopLabel.isEmpty()) {
            says.append(". ").append(p.loopLabel);
        }
        view.setLoop(p.nodes, mark, p.loopLabel, says.toString());
        return view;
    }

    /** "WHAT HOLDS IT BACK: TRANSPORT": what the quantities share, a bar per quantity scaled by its number, what follows. */
    static View holdsBack(Context c, Picture p) {
        final LinearLayout card = PictureUi.column(c);
        card.setBackground(CanvasParts.round(PictureUi.block(), 0, CanvasLook.dp(16)));
        final int pad = CanvasLook.dp(14);
        card.setPadding(pad, pad, pad, pad);
        card.addView(PictureUi.mono(c, p.holds.isEmpty() ? c.getString(R.string.canvas_t27_holds_back)
                : c.getString(R.string.canvas_t27_holds_back_what, p.holds), CanvasLook.sensor()));
        if (!p.holdsBasis.isEmpty()) {
            final TextView basis = PictureUi.text(c, p.holdsBasis, CanvasLook.TEXT_SMALL, 400, CanvasLook.ink2());
            basis.setPadding(0, CanvasLook.dp(2), 0, 0);
            card.addView(basis);
        }
        final float[] shares = PictureLayout.bars(p.quantityNumbers());
        for (int i = 0; i < p.quantities.size(); i++) {
            final Picture.Quantity q = p.quantities.get(i);
            final LinearLayout line = row(c);
            line.setPadding(0, CanvasLook.dp(10), 0, CanvasLook.dp(4));
            line.addView(PictureUi.text(c, q.label, 13, 400, CanvasLook.ink()),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final TextView value = PictureUi.text(c, q.value, 13, 600, CanvasLook.ink());
            value.setPadding(CanvasLook.dp(8), 0, 0, 0);
            line.addView(value);
            card.addView(line);
            card.addView(new Bar(c, shares[i]), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(10)));
        }
        if (!p.holdsNote.isEmpty()) {
            final TextView note = PictureUi.text(c, p.holdsNote, 13, 400, CanvasLook.ink());
            note.setLineSpacing(CanvasLook.dp(2), 1f);
            note.setPadding(0, CanvasLook.dp(10), 0, 0);
            card.addView(note);
        }
        return card;
    }

    /** A 10 dp bar: a track, filled from the left by a share of its width (never less than 5 dp, so a small one shows). */
    static final class Bar extends View {
        private final float share;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Bar(Context c, float share) {
            super(c);
            this.share = share;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float r = getHeight() / 2f;
            rect.set(0, 0, getWidth(), getHeight());
            paint.setColor(CanvasLook.line());
            canvas.drawRoundRect(rect, r, r, paint);
            rect.right = Math.min(getWidth(), Math.max(CanvasLook.dp(5), share * getWidth()));
            paint.setColor(CanvasLook.accent());
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }

    // ------------------------------------------------------------------ why it works

    /** Up to three small cards side by side, numbered in the accent colour. */
    static View whyCards(Context c, List<Picture.Card> cards) {
        final LinearLayout line = row(c);
        for (int i = 0; i < cards.size(); i++) {
            final Picture.Card data = cards.get(i);
            final LinearLayout card = PictureUi.column(c);
            card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.radius()));
            card.setPadding(CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12));
            final TextView number = PictureUi.mono(c, String.valueOf(i + 1), CanvasLook.accent());
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(number);
            final TextView title = PictureUi.text(c, data.title, 13, 600, CanvasLook.ink());
            title.setPadding(0, CanvasLook.dp(6), 0, 0);
            card.addView(title);
            final TextView text = PictureUi.text(c, data.line, CanvasLook.TEXT_SMALL, 400, CanvasLook.ink2());
            text.setPadding(0, CanvasLook.dp(6), 0, 0);
            card.addView(text);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = i == 0 ? 0 : CanvasLook.dp(8);
            line.addView(card, lp);
        }
        return line;
    }

    /** The canvas's dashed footnote: a small label over one line; a tap runs {@code open} when given. */
    static View footnote(Context c, CharSequence label, CharSequence text, Runnable open) {
        final LinearLayout card = PictureUi.column(c);
        card.setBackground(PictureUi.dashed(CanvasLook.bg(), PictureUi.dash(), CanvasLook.RADIUS_DP));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        card.addView(PictureUi.mono(c, label, CanvasLook.muted()));
        final TextView body = PictureUi.text(c, text, 13, 400, CanvasLook.ink());
        body.setLineSpacing(CanvasLook.dp(2), 1f);
        body.setPadding(0, CanvasLook.dp(2), 0, 0);
        card.addView(body);
        if (open != null) {
            card.setOnClickListener(v -> open.run());
        }
        return card;
    }
}
