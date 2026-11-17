package com.foobnix.assist.review;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

/** The small drawn parts of the review screens: the memory bars, a session's progress, the "Comes back" row. */
final class ReviewViews {

    private ReviewViews() {
    }

    /** "memory n of 5": five small bars, the first {@code strength} filled (orange for an idea missed in recall). */
    static final class Bars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int strength;
        private final boolean missed;
        private final int count;
        private final int barW;
        private final int barH;

        /** A row's bars: five, 6 x 14 dp. */
        Bars(Context c, int strength, boolean missed) {
            this(c, ReviewItem.strength(strength), missed, ReviewItem.MAX_STRENGTH, 6, 14);
        }

        private Bars(Context c, int strength, boolean missed, int count, int wDp, int hDp) {
            super(c);
            this.strength = strength;
            this.missed = missed;
            this.count = count;
            barW = CanvasLook.dp(wDp);
            barH = CanvasLook.dp(hDp);
        }

        /** The legend's two small bars, one filled. */
        static Bars legend(Context c) {
            return new Bars(c, 1, false, 2, 4, 10);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(count * barW + (count - 1) * CanvasLook.dp(2), barH);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int gap = CanvasLook.dp(2);
            final float r = CanvasLook.dp(2);
            for (int i = 0; i < count; i++) {
                paint.setColor(i < strength ? (missed ? ReviewPage.missedBar() : CanvasLook.accent()) : CanvasLook.line());
                rect.set(i * (barW + gap), 0, i * (barW + gap) + barW, barH);
                canvas.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    /** A session's progress: one 4 dp segment per card; done ones in ink, the current in the accent. */
    static final class Progress extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int count;
        private int current;

        Progress(Context c) {
            super(c);
        }

        void set(int count, int current) {
            this.count = count;
            this.current = current;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(getDefaultSize(0, widthSpec), CanvasLook.dp(4));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (count <= 0) {
                return;
            }
            final float gap = CanvasLook.dp(4);
            final float w = (getWidth() - gap * (count - 1)) / count;
            final float r = CanvasLook.dp(2);
            for (int i = 0; i < count; i++) {
                paint.setColor(i < current ? CanvasLook.ink() : i == current ? CanvasLook.accent() : CanvasLook.line());
                rect.set(i * (w + gap), 0, i * (w + gap) + w, getHeight());
                canvas.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    /**
     * The "Comes back" row: four dots on a line, each with its words under it. The first is
     * today (filled), the second the next review (ringed in the accent, its words in ink), the
     * rest grey; the last is dashed: it is the furthest guess.
     */
    static final class Ladder extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] words;

        Ladder(Context c, String[] words) {
            super(c);
            this.words = words;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(CanvasLook.dp(12) * c.getResources().getConfiguration().fontScale);
            setContentDescription(android.text.TextUtils.join(", ", words));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(getDefaultSize(0, widthSpec), CanvasLook.dp(14 + 6) + Math.round(text.getTextSize() * 1.4f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int n = words.length;
            if (n == 0) {
                return;
            }
            final float cell = getWidth() / (float) n;
            final float cy = CanvasLook.dp(7);
            final float radius = CanvasLook.dp(7);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(CanvasLook.dp(2));
            paint.setColor(CanvasLook.lineStrong());
            canvas.drawLine(cell / 2, cy, getWidth() - cell / 2, cy, paint);
            for (int i = 0; i < n; i++) {
                final float cx = cell * i + cell / 2;
                // the line does not show through a ring
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CanvasLook.bg());
                canvas.drawCircle(cx, cy, radius + CanvasLook.dp(3), paint);
                if (i == 0) {
                    paint.setColor(CanvasLook.accent());
                    canvas.drawCircle(cx, cy, radius, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(i == 1 ? CanvasLook.accent() : CanvasLook.muted());
                    paint.setPathEffect(i == n - 1 && n > 2
                            ? new DashPathEffect(new float[]{CanvasLook.dp(3), CanvasLook.dp(3)}, 0) : null);
                    canvas.drawCircle(cx, cy, radius - CanvasLook.dp(1), paint);
                    paint.setPathEffect(null);
                }
                text.setTypeface(CanvasLook.ui(getContext(), i == 0 ? 600 : 400));
                text.setColor(i <= 1 ? CanvasLook.ink() : CanvasLook.ink2());
                canvas.drawText(words[i], cx, getHeight() - text.getTextSize() * 0.3f, text);
            }
        }
    }

    /** What joins the two ideas of a connection card: a dashed line between them and the word beside it. */
    static final class Connector extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String word;

        Connector(Context c, String word) {
            super(c);
            this.word = word;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(CanvasLook.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setPathEffect(new DashPathEffect(new float[]{CanvasLook.dp(5), CanvasLook.dp(5)}, 0));
            text.setTypeface(CanvasLook.ui(c, 600));
            text.setTextSize(CanvasLook.dp(13) * c.getResources().getConfiguration().fontScale);
            setContentDescription(word);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(getDefaultSize(0, widthSpec), CanvasLook.dp(48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float cx = getWidth() / 2f;
            paint.setColor(CanvasLook.accent());
            canvas.drawLine(cx, CanvasLook.dp(2), cx, getHeight() - CanvasLook.dp(2), paint);
            text.setColor(CanvasLook.accent());
            canvas.drawText(word, cx + CanvasLook.dp(12), getHeight() / 2f + text.getTextSize() * 0.35f, text);
        }
    }
}
