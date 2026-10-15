package com.foobnix.stats;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * The charts of the reading stats, drawn on a Canvas (no chart library: the F-Droid build keeps
 * its dependencies). Every chart shows one series in the app's accent color; text is in the
 * theme's text colors; grids are hairlines. Tapping a mark reports it to {@link OnPick}, which
 * shows its value in a caption (the touch form of a hover tooltip).
 */
public final class StatsCharts {

    private StatsCharts() {
    }

    public interface OnPick {
        void picked(int index);
    }

    /** Shared paints and sizes of a chart. */
    abstract static class Chart extends View {
        final float dp;
        final int accent;
        final int textPrimary;
        final int textSecondary;
        final int grid;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        int picked = -1;
        OnPick onPick;

        Chart(Context c, int accent) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            this.accent = accent;
            final TypedArray a = c.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary, android.R.attr.textColorSecondary});
            textPrimary = a.getColor(0, Color.GRAY);
            textSecondary = a.getColor(1, Color.GRAY);
            a.recycle();
            grid = withAlpha(textSecondary, 0x40);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            text.setTextSize(11 * dp);
        }

        void setOnPick(OnPick onPick) {
            this.onPick = onPick;
        }

        /** The mark at this touch, -1 for none. */
        abstract int markAt(float x, float y);

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                final int i = markAt(e.getX(), e.getY());
                if (i >= 0) {
                    picked = i;
                    invalidate();
                    if (onPick != null) {
                        onPick.picked(i);
                    }
                }
                return true;
            }
            return super.onTouchEvent(e);
        }

        void hairline(Canvas c, float x1, float y1, float x2, float y2) {
            line.setColor(grid);
            line.setStrokeWidth(Math.max(1, dp / 2));
            c.drawLine(x1, y1, x2, y2, line);
        }

        void label(Canvas c, String s, float x, float y, Paint.Align align, int color) {
            text.setColor(color);
            text.setTextAlign(align);
            c.drawText(s, x, y, text);
        }

        int height(float dps) {
            return (int) (dps * dp);
        }
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Vertical bars with labels under them (one label in k when they would collide). */
    public static class Bars extends Chart {
        private float[] values = new float[0];
        private String[] labels = new String[0];
        private int highlight = -1;

        public Bars(Context c, int accent) {
            super(c, accent);
            setMinimumHeight(height(150));
        }

        /** @param highlight the bar of today, drawn at full strength while others are lighter; -1 none */
        public void set(float[] values, String[] labels, int highlight, OnPick onPick) {
            this.values = values;
            this.labels = labels;
            this.highlight = highlight;
            this.picked = -1;
            setOnPick(onPick);
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), height(150));
        }

        private float barLeft(int i, float slot) {
            return getPaddingLeft() + i * slot;
        }

        @Override
        int markAt(float x, float y) {
            if (values.length == 0) {
                return -1;
            }
            final float slot = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) values.length;
            return Math.max(0, Math.min(values.length - 1, (int) ((x - getPaddingLeft()) / slot)));
        }

        @Override
        protected void onDraw(Canvas c) {
            final float bottom = getHeight() - 18 * dp;
            final float top = 8 * dp;
            hairline(c, 0, bottom, getWidth(), bottom);
            if (values.length == 0) {
                return;
            }
            float max = 0;
            for (float v : values) {
                max = Math.max(max, v);
            }
            final float slot = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) values.length;
            final float gap = Math.max(2 * dp, slot * 0.25f);
            final float width = Math.min(24 * dp, slot - gap);
            final float radius = Math.min(4 * dp, width / 2);
            final int every = (int) Math.ceil(values.length / 10.0);
            for (int i = 0; i < values.length; i++) {
                final float left = barLeft(i, slot) + (slot - width) / 2;
                if (max > 0 && values[i] > 0) {
                    final float h = Math.max(2 * dp, (bottom - top) * values[i] / max);
                    final boolean strong = highlight < 0 || i == highlight || i == picked;
                    fill.setColor(strong ? accent : withAlpha(accent, 0x99));
                    final RectF r = new RectF(left, bottom - h, left + width, bottom);
                    c.drawRoundRect(r, radius, radius, fill);
                    // square the end on the baseline: only the data end is rounded
                    c.drawRect(left, Math.max(r.top + radius, bottom - radius), left + width, bottom, fill);
                }
                if (i < labels.length && (i % every == 0 || i == picked || i == highlight)) {
                    label(c, labels[i], left + width / 2, getHeight() - 4 * dp, Paint.Align.CENTER,
                            i == picked || i == highlight ? textPrimary : textSecondary);
                }
            }
        }
    }

    /** A line through the values; a value of 0 is a gap (no data that week). */
    public static class Line extends Chart {
        private int[] values = new int[0];
        private String firstLabel = "";
        private String lastLabel = "";

        public Line(Context c, int accent) {
            super(c, accent);
        }

        public void set(int[] values, String firstLabel, String lastLabel, OnPick onPick) {
            this.values = values;
            this.firstLabel = firstLabel;
            this.lastLabel = lastLabel;
            this.picked = -1;
            setOnPick(onPick);
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), height(120));
        }

        private float x(int i) {
            final float left = 8 * dp;
            final float right = getWidth() - 8 * dp;
            return values.length <= 1 ? (left + right) / 2 : left + (right - left) * i / (values.length - 1);
        }

        @Override
        int markAt(float px, float py) {
            int best = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) {
                    continue;
                }
                final float d = Math.abs(x(i) - px);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = i;
                }
            }
            return best;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float bottom = getHeight() - 18 * dp;
            final float top = 10 * dp;
            hairline(c, 0, bottom, getWidth(), bottom);
            label(c, firstLabel, 0, getHeight() - 4 * dp, Paint.Align.LEFT, textSecondary);
            label(c, lastLabel, getWidth(), getHeight() - 4 * dp, Paint.Align.RIGHT, textSecondary);
            int min = Integer.MAX_VALUE;
            int max = 0;
            int last = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] > 0) {
                    min = Math.min(min, values[i]);
                    max = Math.max(max, values[i]);
                    last = i;
                }
            }
            if (last < 0) {
                return;
            }
            final float span = Math.max(1, (max - min) * 1.2f);
            final float base = min - (span - (max - min)) / 2;
            line.setColor(accent);
            line.setStrokeWidth(2 * dp);
            final Path path = new Path();
            boolean pen = false;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) {
                    pen = false;
                    continue;
                }
                final float y = bottom - (bottom - top) * (values[i] - base) / span;
                if (pen) {
                    path.lineTo(x(i), y);
                } else {
                    path.moveTo(x(i), y);
                    pen = true;
                }
            }
            c.drawPath(path, line);
            // the last value and the picked one get a marker and their number
            for (int i : new int[]{last, picked}) {
                if (i < 0 || values[i] <= 0) {
                    continue;
                }
                final float y = bottom - (bottom - top) * (values[i] - base) / span;
                fill.setColor(accent);
                c.drawCircle(x(i), y, 4 * dp, fill);
                final Paint.Align align = x(i) > getWidth() * 0.8f ? Paint.Align.RIGHT : x(i) < getWidth() * 0.2f ? Paint.Align.LEFT : Paint.Align.CENTER;
                label(c, String.valueOf(values[i]), x(i), Math.max(text.getTextSize(), y - 8 * dp), align, textPrimary);
            }
        }
    }

    /** Progress toward a goal: a ring with the number in it. */
    public static class Ring extends Chart {
        private float progress;
        private String center = "";
        private String below = "";

        public Ring(Context c, int accent) {
            super(c, accent);
        }

        public void set(float progress, String center, String below) {
            this.progress = progress;
            this.center = center;
            this.below = below;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(height(96), height(96));
        }

        @Override
        int markAt(float x, float y) {
            return -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            // taps go to the click listener (set the goal)
            return performClickIfUp(e);
        }

        private boolean performClickIfUp(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onDraw(Canvas c) {
            final float stroke = 7 * dp;
            final RectF r = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            line.setStrokeWidth(stroke);
            line.setColor(grid);
            c.drawArc(r, 0, 360, false, line);
            line.setColor(accent);
            c.drawArc(r, -90, 360 * Math.max(0, Math.min(1, progress)), false, line);
            text.setTextSize(16 * dp);
            text.setFakeBoldText(true);
            label(c, center, getWidth() / 2f, getHeight() / 2f + 2 * dp, Paint.Align.CENTER, textPrimary);
            text.setFakeBoldText(false);
            text.setTextSize(10 * dp);
            label(c, below, getWidth() / 2f, getHeight() / 2f + 16 * dp, Paint.Align.CENTER, textSecondary);
            text.setTextSize(11 * dp);
        }
    }

    /** Weekday x hour cells, one hue from light (little) to strong (most). */
    public static class Heatmap extends Chart {
        private long[][] values = new long[7][24];
        private String[] weekdays = {"M", "T", "W", "T", "F", "S", "S"};
        private String less = "";
        private String more = "";

        public Heatmap(Context c, int accent) {
            super(c, accent);
        }

        public void set(long[][] values, String[] weekdays, String less, String more, OnPick onPick) {
            this.values = values;
            this.weekdays = weekdays;
            this.less = less;
            this.more = more;
            this.picked = -1;
            setOnPick(onPick);
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), height(7 * 16 + 40));
        }

        private float left() {
            return 16 * dp;
        }

        private float cell() {
            return Math.min(16 * dp, (getWidth() - left()) / 24f);
        }

        @Override
        int markAt(float x, float y) {
            final int hour = (int) ((x - left()) / cell());
            final int day = (int) (y / cell());
            if (hour < 0 || hour > 23 || day < 0 || day > 6) {
                return -1;
            }
            return day * 24 + hour;
        }

        @Override
        protected void onDraw(Canvas c) {
            long max = 0;
            for (long[] row : values) {
                for (long v : row) {
                    max = Math.max(max, v);
                }
            }
            final float cell = cell();
            final float gap = Math.max(1, dp);
            for (int d = 0; d < 7; d++) {
                label(c, weekdays[d], 0, d * cell + cell * 0.75f, Paint.Align.LEFT, textSecondary);
                for (int h = 0; h < 24; h++) {
                    final long v = values[d][h];
                    fill.setColor(v <= 0 || max == 0 ? withAlpha(textSecondary, 0x14) : withAlpha(accent, 0x30 + (int) (0xCF * v / (double) max)));
                    final float x = left() + h * cell;
                    final float y = d * cell;
                    c.drawRoundRect(new RectF(x + gap, y + gap, x + cell - gap, y + cell - gap), 2 * dp, 2 * dp, fill);
                    if (picked == d * 24 + h) {
                        line.setColor(textPrimary);
                        line.setStrokeWidth(1.5f * dp);
                        c.drawRoundRect(new RectF(x + gap, y + gap, x + cell - gap, y + cell - gap), 2 * dp, 2 * dp, line);
                    }
                }
            }
            final float hoursY = 7 * cell + 14 * dp;
            for (int h = 0; h < 24; h += 6) {
                label(c, String.valueOf(h), left() + h * cell, hoursY, Paint.Align.LEFT, textSecondary);
            }
            // scale legend: less -> more
            final float legendY = hoursY + 16 * dp;
            label(c, less, left(), legendY, Paint.Align.LEFT, textSecondary);
            float x = left() + text.measureText(less) + 6 * dp;
            for (int i = 0; i < 5; i++) {
                fill.setColor(withAlpha(accent, 0x30 + 0xCF * i / 4));
                c.drawRoundRect(new RectF(x, legendY - cell * 0.7f, x + cell * 0.7f, legendY), 2 * dp, 2 * dp, fill);
                x += cell * 0.7f + 3 * dp;
            }
            label(c, more, x + 3 * dp, legendY, Paint.Align.LEFT, textSecondary);
        }
    }

    /** The days of a month, those with reading filled. */
    public static class Calendar extends Chart {
        private boolean[] read = new boolean[32];
        private int firstWeekday;
        private int days = 30;
        private int today;
        private String[] weekdays = {"M", "T", "W", "T", "F", "S", "S"};

        public Calendar(Context c, int accent) {
            super(c, accent);
        }

        /** @param firstWeekday 0 = Monday; @param today day of month, 0 when not this month */
        public void set(boolean[] read, int firstWeekday, int days, int today, String[] weekdays) {
            this.read = read;
            this.firstWeekday = firstWeekday;
            this.days = days;
            this.today = today;
            this.weekdays = weekdays;
            invalidate();
        }

        private float cell() {
            return Math.min(36 * dp, getWidth() / 7f);
        }

        @Override
        protected void onMeasure(int w, int h) {
            final float cell = Math.min(36 * dp, MeasureSpec.getSize(w) / 7f);
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (cell * 7));
        }

        @Override
        int markAt(float x, float y) {
            return -1;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float cell = cell();
            for (int i = 0; i < 7; i++) {
                label(c, weekdays[i], i * cell + cell / 2, cell * 0.6f, Paint.Align.CENTER, textSecondary);
            }
            for (int d = 1; d <= days; d++) {
                final int pos = firstWeekday + d - 1;
                final float cx = (pos % 7) * cell + cell / 2;
                final float cy = (pos / 7 + 1) * cell + cell / 2;
                final float r = cell * 0.38f;
                if (read[d]) {
                    fill.setColor(accent);
                    c.drawCircle(cx, cy, r, fill);
                }
                if (d == today) {
                    line.setColor(textPrimary);
                    line.setStrokeWidth(1.5f * dp);
                    c.drawCircle(cx, cy, r + 2 * dp, line);
                }
                label(c, String.valueOf(d), cx, cy + text.getTextSize() / 3, Paint.Align.CENTER, read[d] ? Color.WHITE : textSecondary);
            }
        }
    }

    /** Rows of label, bar and value: shares of reading by genre, author... */
    public static class HBars extends Chart {
        private List<String> labels;
        private long[] values = new long[0];
        private List<String> valueTexts;

        public HBars(Context c, int accent) {
            super(c, accent);
        }

        public void set(List<String> labels, long[] values, List<String> valueTexts) {
            this.labels = labels;
            this.values = values;
            this.valueTexts = valueTexts;
            invalidate();
            requestLayout();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (values.length * 26 * dp));
        }

        @Override
        int markAt(float x, float y) {
            return -1;
        }

        @Override
        protected void onDraw(Canvas c) {
            long max = 0;
            for (long v : values) {
                max = Math.max(max, v);
            }
            final float labelWidth = getWidth() * 0.38f;
            final float valueWidth = 56 * dp;
            final float barLeft = labelWidth + 6 * dp;
            final float barRight = getWidth() - valueWidth - 6 * dp;
            for (int i = 0; i < values.length; i++) {
                final float y = i * 26 * dp;
                final String l = TextUtils.ellipsize(labels.get(i), new android.text.TextPaint(text), labelWidth, TextUtils.TruncateAt.END).toString();
                label(c, l, 0, y + 17 * dp, Paint.Align.LEFT, textPrimary);
                final float w = max == 0 ? 0 : (barRight - barLeft) * values[i] / max;
                fill.setColor(accent);
                final RectF r = new RectF(barLeft, y + 8 * dp, barLeft + Math.max(2 * dp, w), y + 18 * dp);
                c.drawRoundRect(r, 4 * dp, 4 * dp, fill);
                c.drawRect(barLeft, r.top, Math.min(r.right, barLeft + 4 * dp), r.bottom, fill);
                label(c, valueTexts.get(i), getWidth(), y + 17 * dp, Paint.Align.RIGHT, textSecondary);
            }
        }
    }
}
