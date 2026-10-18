package com.foobnix.eye;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Locale;

/**
 * With "Show what the camera sees": where Eye mode thinks the reader looks (a ring over the page)
 * and the zone lines it works with (book mode: last lines and top; scroll mode: the quarters), so
 * the owner can see the calibration hold while reading. Touches go through.
 */
final class EyeGazeView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float x = Float.NaN;
    private float y = Float.NaN;
    private boolean scrollMode;
    private String label = "";

    EyeGazeView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * density);
        ring.setColor(0xAAFF5722);
        line.setColor(0x66FF5722);
        line.setStrokeWidth(density);
        text.setColor(0xCCFF5722);
        text.setTextSize(11 * density);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    /** @param x screen fraction, NaN when not known */
    void show(float x, float y, boolean scrollMode, String label) {
        this.x = x;
        this.y = y;
        this.scrollMode = scrollMode;
        this.label = label == null ? "" : label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        final float[] lines = scrollMode
                ? new float[]{EyeRules.SCROLL_TOP_ENTER, EyeRules.SCROLL_BOTTOM_ENTER}
                : new float[]{EyeRules.TOP_ZONE, EyeRules.BOTTOM_ZONE};
        for (float l : lines) {
            canvas.drawLine(0, l * h, w, l * h, line);
        }
        canvas.drawText(label, 8 * density, lines[1] * h - 4 * density, text);
        if (!Float.isNaN(y)) {
            final float cx = Float.isNaN(x) ? w / 2f : Math.max(0, Math.min(1, x)) * w;
            final float cy = Math.max(0, Math.min(1, y)) * h;
            canvas.drawCircle(cx, cy, 18 * density, ring);
            canvas.drawText(String.format(Locale.US, "%.2f", y), cx + 22 * density, cy, text);
        }
    }
}
