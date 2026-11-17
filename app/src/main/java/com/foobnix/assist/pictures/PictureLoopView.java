package com.foobnix.assist.pictures;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

import java.util.ArrayList;
import java.util.List;

/**
 * A causal loop as the canvas draws it (PictureLoop): the nodes as boxes round an ellipse, an
 * arrowed arc from each to the next, and in the middle the mark R (reinforcing) or B (balancing)
 * over two or three words. Everything is drawn here; where it goes is {@link PictureLayout#fitLoop}.
 */
final class PictureLoopView extends View {

    private final List<String> nodes = new ArrayList<>();
    private String mark = "";
    private String label = "";

    private final float dp;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nodeText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint markText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();

    private PictureLayout.Loop loop;
    private StaticLayout[] texts = new StaticLayout[0];
    private StaticLayout labelLayout;

    PictureLoopView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * dp);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(CanvasLook.accent());
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(CanvasLook.accent());
        boxFill.setStyle(Paint.Style.FILL);
        boxFill.setColor(CanvasLook.bg());
        boxLine.setStyle(Paint.Style.STROKE);
        boxLine.setStrokeWidth(Math.max(1f, dp));
        boxLine.setColor(CanvasLook.lineStrong());
        nodeText.setTypeface(CanvasLook.ui(c, 600));
        nodeText.setColor(CanvasLook.ink());
        markText.setTypeface(CanvasLook.ui(c, 700));
        markText.setColor(CanvasLook.onAccent());
        markText.setTextSize(sp(20));
        markText.setTextAlign(Paint.Align.CENTER);
        labelText.setTypeface(CanvasLook.ui(c, 400));
        labelText.setColor(CanvasLook.ink2());
        labelText.setTextSize(sp(12));
    }

    /**
     * @param nodes in order round the loop
     * @param mark  the letter in the middle ("R", "B"), or empty for none
     * @param label the words under it, or empty
     * @param says  the loop in words, for a screen reader
     */
    void setLoop(List<String> nodes, String mark, String label, String says) {
        this.nodes.clear();
        this.nodes.addAll(nodes);
        this.mark = mark == null ? "" : mark;
        this.label = label == null ? "" : label;
        setContentDescription(says);
        loop = null;
        requestLayout();
        invalidate();
    }

    private float sp(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED || width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        // the canvas draws the loop 358 dp wide: wider screens keep it in the middle
        setMeasuredDimension(width, Math.round(PictureLayout.loopHeight(nodes.size(), dp)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        loop = null;
    }

    private void lay() {
        final float width = Math.min(getWidth(), 420 * dp);
        loop = PictureLayout.fitLoop(nodes.size(), width, dp);
        texts = new StaticLayout[nodes.size()];
        final int inner = Math.max(1, Math.round(loop.boxWidth - 16 * dp));
        for (int i = 0; i < texts.length; i++) {
            nodeText.setTextSize(sp(14));
            StaticLayout s = text(nodes.get(i), nodeText, inner, 3);
            if (s.getLineCount() > 2 || s.getHeight() > loop.boxHeight - 8 * dp) {
                // a long name: smaller, three lines
                final TextPaint small = new TextPaint(nodeText);
                small.setTextSize(sp(12));
                s = text(nodes.get(i), small, inner, 3);
            }
            texts[i] = s;
        }
        labelLayout = label.isEmpty() ? null : text(label, labelText, Math.round(96 * dp), 2);
    }

    private static StaticLayout text(String s, TextPaint paint, int width, int lines) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(lines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (nodes.isEmpty() || getWidth() <= 0) {
            return;
        }
        if (loop == null) {
            lay();
        }
        canvas.save();
        canvas.translate((getWidth() - loop.width) / 2f, 0);
        for (int i = 0; i < loop.n; i++) {
            final float[] arc = loop.arcs[i];
            if (arc.length >= 4) {
                path.reset();
                path.moveTo(arc[0], arc[1]);
                for (int p = 2; p < arc.length; p += 2) {
                    path.lineTo(arc[p], arc[p + 1]);
                }
                canvas.drawPath(path, line);
            }
            final float[] head = loop.heads[i];
            if (head.length == 6) {
                path.reset();
                path.moveTo(head[0], head[1]);
                path.lineTo(head[2], head[3]);
                path.lineTo(head[4], head[5]);
                path.close();
                canvas.drawPath(path, fill);
            }
        }
        final float radius = 12 * dp;
        for (int i = 0; i < loop.n; i++) {
            box.set(loop.cx[i] - loop.boxWidth / 2, loop.cy[i] - loop.boxHeight / 2,
                    loop.cx[i] + loop.boxWidth / 2, loop.cy[i] + loop.boxHeight / 2);
            canvas.drawRoundRect(box, radius, radius, boxFill);
            canvas.drawRoundRect(box, radius, radius, boxLine);
            final StaticLayout s = texts[i];
            canvas.save();
            canvas.clipRect(box);
            canvas.translate(loop.cx[i] - s.getWidth() / 2f, loop.cy[i] - s.getHeight() / 2f);
            s.draw(canvas);
            canvas.restore();
        }
        final float cx = loop.width / 2;
        final float cy = loop.height / 2;
        if (!mark.isEmpty()) {
            canvas.drawCircle(cx, cy, 28 * dp, fill);
            canvas.drawText(mark, cx, cy - (markText.ascent() + markText.descent()) / 2, markText);
        }
        if (labelLayout != null) {
            canvas.save();
            canvas.translate(cx - labelLayout.getWidth() / 2f, mark.isEmpty() ? cy - labelLayout.getHeight() / 2f : cy + 36 * dp);
            labelLayout.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }
}
