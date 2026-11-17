package com.foobnix.assist.recall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

/**
 * The small drawn parts of the recall and teach-back screens, in the canvas's tokens: the round
 * talk button, the timer ring, the level bars and the three marks of an idea.
 */
public final class RecallViews {

    private RecallViews() {
    }

    /** The microphone of the canvas (a 24-unit glyph), stroked in {@code color} inside a box of {@code size} px. */
    static void mic(Canvas c, float cx, float cy, float size, int color, Paint p) {
        final float u = size / 24f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * u);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        final float left = cx - 12 * u;
        final float top = cy - 12 * u;
        c.drawRoundRect(new RectF(left + 9 * u, top + 3 * u, left + 15 * u, top + 14 * u), 3 * u, 3 * u, p);
        c.drawArc(new RectF(left + 5 * u, top + 4 * u, left + 19 * u, top + 18 * u), 0, 180, false, p);
        c.drawLine(left + 12 * u, top + 18 * u, left + 12 * u, top + 21 * u, p);
    }

    /**
     * The round talk button: an accent disc with the microphone; while listening, two soft rings
     * around it. The view is the disc plus {@code room} on every side for the rings.
     */
    public static final class Mic extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int room;
        private int disc;
        private boolean listening;

        /**
         * @param discDp the disc's diameter
         * @param roomDp the space around it for the rings (0: no rings are drawn)
         */
        public Mic(Context c, int discDp, int roomDp) {
            super(c);
            disc = CanvasLook.dp(discDp);
            room = CanvasLook.dp(roomDp);
            setClickable(true);
            setFocusable(true);
        }

        /** The size of the view's side, for its layout params. */
        public int side() {
            return disc + 2 * room;
        }

        public void setListening(boolean on, int discDp) {
            listening = on;
            disc = Math.min(CanvasLook.dp(discDp), getWidth() > 0 ? getWidth() : Integer.MAX_VALUE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float r = disc / 2f;
            final int accent = isPressed() && !listening ? CanvasLook.accentPressed() : CanvasLook.accent();
            paint.setStyle(Paint.Style.FILL);
            if (listening && room > 0) {
                paint.setColor((accent & 0x00FFFFFF) | 0x0F000000);
                c.drawCircle(cx, cy, Math.min(r + room, cx), paint);
                paint.setColor((accent & 0x00FFFFFF) | 0x24000000);
                c.drawCircle(cx, cy, Math.min(r + room * 0.45f, cx), paint);
            }
            paint.setColor(accent);
            c.drawCircle(cx, cy, r, paint);
            mic(c, cx, cy, disc * 0.4f, CanvasLook.onAccent(), paint);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }
    }

    /** The timer ring: a track with an accent arc from the top, {@link #setProgress} 0..1. */
    public static final class Ring extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float progress;

        public Ring(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            final float stroke = CanvasLook.dp(8);
            paint.setStrokeWidth(stroke);
            oval.set(stroke / 2, stroke / 2, getWidth() - stroke / 2, getHeight() - stroke / 2);
            paint.setColor(CanvasLook.line());
            c.drawOval(oval, paint);
            if (progress > 0) {
                paint.setColor(CanvasLook.accent());
                c.drawArc(oval, -90, 360 * progress, false, paint);
            }
        }
    }

    /** The voice as bars: each level heard is one bar, the newest on the right; unused places stay short. */
    public static final class Bars extends View {
        private static final int COUNT = 28;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] levels = new float[COUNT];
        private int used;
        private long lastPush;

        public Bars(Context c) {
            super(c);
            paint.setStyle(Paint.Style.FILL);
        }

        /** One more level, 0..1 (the recognizer sends many a second: one in 90 ms is kept). */
        public void push(float level) {
            final long now = System.currentTimeMillis();
            if (now - lastPush < 90) {
                if (used > 0) {
                    levels[used - 1] = Math.max(levels[used - 1], level);
                }
                return;
            }
            lastPush = now;
            if (used == COUNT) {
                System.arraycopy(levels, 1, levels, 0, COUNT - 1);
                used--;
            }
            levels[used++] = level;
            invalidate();
        }

        public void reset() {
            used = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = CanvasLook.dp(3);
            final float gap = CanvasLook.dp(3);
            final float total = COUNT * w + (COUNT - 1) * gap;
            float x = (getWidth() - total) / 2f;
            final float mid = getHeight() / 2f;
            final float min = CanvasLook.dp(6);
            final float max = Math.max(min, getHeight() - CanvasLook.dp(4));
            for (int i = 0; i < COUNT; i++) {
                final boolean on = i < used;
                final float h = on ? min + (max - min) * levels[i] : min;
                paint.setColor(!on ? CanvasLook.line() : i < used - 18 ? CanvasLook.accentLine() : CanvasLook.accent());
                c.drawRoundRect(new RectF(x, mid - h / 2, x + w, mid + h / 2), w / 2, w / 2, paint);
                x += w + gap;
            }
        }
    }

    /** The confidence check: a track filled to what the reader got, with a tick where they expected to be. */
    public static final class Confidence extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int got;
        private final int expected;

        public Confidence(Context c, int got, int expected) {
            super(c);
            this.got = Recall.percent(got);
            this.expected = Recall.percent(expected);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth();
            final float mid = getHeight() / 2f;
            final float half = CanvasLook.dp(3);
            paint.setColor(CanvasLook.line());
            c.drawRoundRect(new RectF(0, mid - half, w, mid + half), half, half, paint);
            if (got > 0) {
                paint.setColor(CanvasLook.ink());
                c.drawRoundRect(new RectF(0, mid - half, w * got / 100f, mid + half), half, half, paint);
            }
            final float tick = CanvasLook.dp(2);
            final float x = Math.max(0, Math.min(w - tick, w * expected / 100f - tick / 2));
            paint.setColor(CanvasLook.sensor());
            c.drawRoundRect(new RectF(x, 0, x + tick, getHeight()), tick / 2, tick / 2, paint);
        }
    }

    /**
     * The mark before an idea: remembered (a filled disc with a check), unsure (half filled),
     * missed (a dashed ring in the sensor colour). Explained / not yet in teach-back use the first
     * and the last.
     */
    public static final class Mark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final String state;

        public Mark(Context c, String state) {
            super(c);
            this.state = state == null ? "" : state;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float s = Math.min(getWidth(), getHeight());
            final float u = s / 24f;
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (Recall.REMEMBERED.equals(state)) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CanvasLook.accent());
                c.drawCircle(cx, cy, 10 * u, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * u);
                paint.setColor(CanvasLook.onAccent());
                final float l = cx - 12 * u;
                final float t = cy - 12 * u;
                c.drawLine(l + 7.5f * u, t + 12.5f * u, l + 10.5f * u, t + 15.5f * u, paint);
                c.drawLine(l + 10.5f * u, t + 15.5f * u, l + 16.5f * u, t + 9f * u, paint);
            } else if (Recall.UNSURE.equals(state)) {
                oval.set(cx - 9 * u, cy - 9 * u, cx + 9 * u, cy + 9 * u);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CanvasLook.accent());
                c.drawArc(oval, -90, 180, true, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * u);
                c.drawOval(oval, paint);
            } else {
                // dashes drawn as arcs: a path effect can be dropped by hardware drawing
                oval.set(cx - 9 * u, cy - 9 * u, cx + 9 * u, cy + 9 * u);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * u);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setColor(CanvasLook.sensor());
                for (int i = 0; i < 10; i++) {
                    c.drawArc(oval, i * 36, 18, false, paint);
                }
            }
        }
    }
}
