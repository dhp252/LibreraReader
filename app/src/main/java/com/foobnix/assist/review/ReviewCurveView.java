package com.foobnix.assist.review;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

/**
 * The forgetting curve of one idea (canvas ReviewHistory): memory falls after each review and is
 * set back to the top, each fall slower than the last. Solid for what happened, dashed for what
 * would happen from the next review on; a dot at each past review (a ring at the foot for a
 * miss), a ring at the next review and at the one after; the dashed level is where the schedule
 * asks again. Everything is an estimate from {@link ReviewCurve}; the screen says so beside it.
 */
final class ReviewCurveView extends View {

    private final ReviewCurve.Model model;
    private final String missed;
    private final String target;
    private final String ahead;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /**
     * @param missed the word under a miss ("missed")
     * @param target the words at the dashed level ("review near 70%")
     * @param ahead  the words at the dashed fall ("if reviewed today")
     */
    ReviewCurveView(Context c, ReviewCurve.Model model, String missed, String target, String ahead, String description) {
        super(c);
        this.model = model;
        this.missed = missed;
        this.target = target;
        this.ahead = ahead;
        text.setTypeface(CanvasLook.ui(c, 400));
        text.setTextSize(CanvasLook.dp(10) * c.getResources().getConfiguration().fontScale);
        setContentDescription(description);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(getDefaultSize(0, widthSpec), CanvasLook.dp(164));
    }

    private float x(double day) {
        final float left = CanvasLook.dp(12);
        final float right = getWidth() - CanvasLook.dp(12);
        return left + (float) (day / Math.max(1, model.end)) * (right - left);
    }

    private float y(double recall) {
        final float top = CanvasLook.dp(20);
        final float bottom = getHeight() - CanvasLook.dp(14);
        return top + (float) (1 - Math.max(0, Math.min(1, recall))) * (bottom - top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (model.segments.isEmpty()) {
            return;
        }
        final float w1 = CanvasLook.dp(1);
        // the top (all of it) and the foot (none of it)
        line.setStyle(Paint.Style.STROKE);
        line.setPathEffect(null);
        line.setAlpha(255);
        line.setStrokeWidth(w1);
        line.setColor(CanvasLook.surface2());
        canvas.drawLine(x(0), y(1), x(model.end), y(1), line);
        canvas.drawLine(x(0), y(0), x(model.end), y(0), line);
        // the level the schedule asks again at
        line.setColor(CanvasLook.lineStrong());
        line.setPathEffect(new DashPathEffect(new float[]{CanvasLook.dp(3), CanvasLook.dp(4)}, 0));
        canvas.drawLine(x(0), y(ReviewCurve.TARGET), x(model.end), y(ReviewCurve.TARGET), line);
        line.setPathEffect(null);
        // today
        canvas.drawLine(x(model.today), y(1) - CanvasLook.dp(8), x(model.today), y(0) + CanvasLook.dp(6), line);

        // the falls: solid for the past, dashed and lighter from today on
        line.setStrokeWidth(CanvasLook.dp(2));
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(CanvasLook.accent());
        for (int pass = 0; pass < 2; pass++) {
            final boolean dashed = pass == 1;
            path.reset();
            ReviewCurve.Segment before = null;
            for (ReviewCurve.Segment s : model.segments) {
                if (s.ahead != dashed) {
                    before = s;
                    continue;
                }
                if (before != null && s.from == s.origin) {
                    // a review: straight up from where the fall had got to
                    path.moveTo(x(s.from), y(before.at(s.from)));
                    path.lineTo(x(s.from), y(1));
                } else {
                    path.moveTo(x(s.from), y(s.at(s.from)));
                }
                final int steps = Math.max(2, Math.min(24, (s.to - s.from) * 2));
                for (int i = 1; i <= steps; i++) {
                    final double day = s.from + (s.to - s.from) * (double) i / steps;
                    path.lineTo(x(day), y(s.at(day)));
                }
                before = s;
            }
            line.setPathEffect(dashed ? new DashPathEffect(new float[]{CanvasLook.dp(4), CanvasLook.dp(4)}, 0) : null);
            line.setAlpha(dashed ? 150 : 255);
            canvas.drawPath(path, line);
        }
        line.setAlpha(255);
        line.setPathEffect(null);

        // the reviews
        for (ReviewCurve.Mark m : model.marks) {
            final float cx = x(m.day);
            if (m.kind == ReviewCurve.Mark.RIGHT) {
                dot(canvas, cx, y(1), CanvasLook.dp(5), CanvasLook.accent(), CanvasLook.accent());
            } else if (m.kind == ReviewCurve.Mark.WRONG) {
                dot(canvas, cx, y(0), CanvasLook.dp(5), CanvasLook.bg(), CanvasLook.sensor());
                text.setColor(CanvasLook.sensor());
                text.setTextAlign(m.day * 2 > model.end ? Paint.Align.RIGHT : Paint.Align.LEFT);
                canvas.drawText(missed, cx + (m.day * 2 > model.end ? -CanvasLook.dp(8) : CanvasLook.dp(8)),
                        y(0) - CanvasLook.dp(6), text);
            } else {
                dot(canvas, cx, y(m.recall), CanvasLook.dp(6), CanvasLook.bg(), CanvasLook.accent());
            }
        }

        // the words, at the right where the curve leaves room
        text.setColor(CanvasLook.ink2());
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(target, x(model.end), y(ReviewCurve.TARGET) + text.getTextSize() + CanvasLook.dp(3), text);
        final boolean right = model.today * 2 > model.end;
        text.setTextAlign(right ? Paint.Align.RIGHT : Paint.Align.LEFT);
        canvas.drawText(ahead, x(model.today + model.nextIn) + (right ? -CanvasLook.dp(8) : CanvasLook.dp(8)),
                y(1) - CanvasLook.dp(8), text);
    }

    private void dot(Canvas canvas, float cx, float cy, float r, int inside, int ring) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(inside);
        canvas.drawCircle(cx, cy, r, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(CanvasLook.dp(2));
        fill.setColor(ring);
        canvas.drawCircle(cx, cy, r, fill);
    }
}
