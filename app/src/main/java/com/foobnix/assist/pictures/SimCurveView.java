package com.foobnix.assist.pictures;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

/**
 * The curve of a small model (canvas PictureSim): a line through the points {@link SimModel#plot}
 * gives, a base line under it, and a ring where the reader's value stands. It draws what it is
 * given; the block redraws it as the reader drags.
 */
final class SimCurveView extends View {

    private float[] ys = new float[0];
    private float markerX = Float.NaN;
    private float markerY = Float.NaN;

    private final float dp;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    SimCurveView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * dp);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(CanvasLook.muted());
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(Math.max(1f, dp));
        base.setColor(CanvasLook.lineStrong());
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * dp);
        ring.setColor(CanvasLook.accent());
        hole.setStyle(Paint.Style.FILL);
        hole.setColor(CanvasLook.bg());
    }

    /**
     * @param ys      the curve's points left to right, each 0 (bottom) to 1 (top); NaN: no value there
     * @param markerX where the reader's value stands, 0 (left) to 1 (right); NaN: no marker
     * @param markerY how high it stands, 0 to 1; NaN: no marker
     */
    void set(float[] ys, float markerX, float markerY) {
        this.ys = ys == null ? new float[0] : ys;
        this.markerX = markerX;
        this.markerY = markerY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float left = 12 * dp;
        final float right = getWidth() - 12 * dp;
        final float top = 14 * dp;
        final float bottom = getHeight() - 12 * dp;
        if (right <= left || bottom <= top) {
            return;
        }
        canvas.drawLine(left, bottom, right, bottom, base);
        path.reset();
        boolean down = false;
        for (int i = 0; i < ys.length; i++) {
            if (Float.isNaN(ys[i])) {
                // no value here: the line breaks
                down = false;
                continue;
            }
            final float x = ys.length == 1 ? left : left + (right - left) * i / (ys.length - 1);
            final float y = bottom - (bottom - top) * ys[i];
            if (down) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                down = true;
            }
        }
        canvas.drawPath(path, line);
        if (!Float.isNaN(markerX) && !Float.isNaN(markerY)) {
            final float x = left + (right - left) * markerX;
            final float y = bottom - (bottom - top) * markerY;
            canvas.drawCircle(x, y, 7 * dp, hole);
            canvas.drawCircle(x, y, 7 * dp, ring);
        }
    }
}
