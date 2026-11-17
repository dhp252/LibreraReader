package com.foobnix.assist.fiction;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import com.foobnix.canvas.CanvasLook;

import java.util.ArrayList;
import java.util.List;

/**
 * The places map (canvas artboard FictionMap), drawn on the device with nothing loaded: no
 * tiles, no images, no network. With coordinates ({@link FictionMapModel#geographic}) the places
 * sit on a plain grid of latitude and longitude lines fitted to them, the route so far as a
 * solid line, the plan's legs dashed, and a marker where the story is. Without, the places stand
 * in order along a winding line.
 */
final class FictionMapView extends View {

    private static final int MAP_HEIGHT_DP = 260;
    private static final int PAD_DP = 30;
    private static final int ROW_DP = 64;
    private static final int SCHEME_PAD_DP = 44;

    private FictionMapModel model;
    private boolean showRoute = true;
    private boolean showPlan = true;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF area = new RectF();

    FictionMapView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, c.getResources().getDisplayMetrics()));
    }

    void set(FictionMapModel model) {
        this.model = model;
        requestLayout();
        invalidate();
    }

    /** Which lines show: the route so far, the plan. */
    void layers(boolean route, boolean plan) {
        showRoute = route;
        showPlan = plan;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int perRow(int width) {
        return FictionMapMath.perRow(width / getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = getDefaultSize(CanvasLook.dp(320), widthMeasureSpec);
        final int height;
        if (model == null || model.geographic) {
            height = CanvasLook.dp(MAP_HEIGHT_DP);
        } else {
            final int rows = Math.max(1, FictionMapMath.rows(model.dots.size(), perRow(width)));
            height = CanvasLook.dp(SCHEME_PAD_DP) + (rows - 1) * CanvasLook.dp(ROW_DP) + CanvasLook.dp(48);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        area.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(area, dp(16), dp(16), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(CanvasLook.surface());
        canvas.drawRect(area, fill);
        if (model != null && !model.dots.isEmpty()) {
            if (model.geographic) {
                drawMap(canvas, w, h);
            } else {
                drawScheme(canvas, w);
            }
        }
        canvas.restore();
    }

    // ------------------------------------------------------------------ with coordinates

    private void drawMap(Canvas canvas, int w, int h) {
        final int n = model.dots.size();
        final double[] lats = new double[n];
        final double[] lons = new double[n];
        for (int i = 0; i < n; i++) {
            lats[i] = model.dots.get(i).lat;
            lons[i] = model.dots.get(i).lon;
        }
        final FictionMapMath.Fit f = FictionMapMath.fit(lats, lons, w, h, dp(PAD_DP));
        grid(canvas, f, w, h);

        if (showPlan) {
            stroke.setColor(CanvasLook.muted());
            stroke.setStrokeWidth(dp(2));
            for (int[] leg : model.plan) {
                final FictionMapModel.Dot a = model.dots.get(leg[0]);
                final FictionMapModel.Dot b = model.dots.get(leg[1]);
                for (float[] piece : FictionMapMath.leg(f, a.lat, a.lon, b.lat, b.lon)) {
                    final float[] dashes = FictionMapMath.dashes(piece[0], piece[1], piece[2], piece[3], dp(4), dp(5));
                    if (dashes.length > 0) {
                        canvas.drawLines(dashes, stroke);
                    }
                }
            }
        }
        if (showRoute) {
            stroke.setColor(CanvasLook.accent());
            stroke.setStrokeWidth(dp(2.5f));
            for (int i = 1; i < model.route.size(); i++) {
                final FictionMapModel.Dot a = model.dots.get(model.route.get(i - 1));
                final FictionMapModel.Dot b = model.dots.get(model.route.get(i));
                for (float[] piece : FictionMapMath.leg(f, a.lat, a.lon, b.lat, b.lon)) {
                    canvas.drawLine(piece[0], piece[1], piece[2], piece[3], stroke);
                }
            }
        }
        final float[] xs = new float[n];
        final float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            final FictionMapModel.Dot d = model.dots.get(i);
            xs[i] = f.x(d.lon);
            ys[i] = f.y(d.lat);
            if (!d.visited && !showPlan) {
                continue;
            }
            dot(canvas, xs[i], ys[i], d.visited);
        }
        if (model.here >= 0) {
            here(canvas, xs[model.here], ys[model.here]);
        }
        // names: where the story is first, then the route backwards, then the plan's stops
        final List<RectF> taken = new ArrayList<>();
        final List<Integer> order = new ArrayList<>();
        if (model.here >= 0) {
            order.add(model.here);
        }
        for (int i = model.route.size() - 1; i >= 0; i--) {
            if (!order.contains(model.route.get(i))) {
                order.add(model.route.get(i));
            }
        }
        for (int i = 0; i < n; i++) {
            if (!order.contains(i) && showPlan) {
                order.add(i);
            }
        }
        for (int i : order) {
            final FictionMapModel.Dot d = model.dots.get(i);
            name(canvas, d.name, xs[i], ys[i], d.visited, taken, w, h);
        }
    }

    /** Latitude and longitude lines, a few across the area. */
    private void grid(Canvas canvas, FictionMapMath.Fit f, int w, int h) {
        stroke.setColor(CanvasLook.line());
        stroke.setStrokeWidth(Math.max(1, dp(1)));
        // the area in degrees: east of the cut, and north
        final double west = f.west - f.left / f.perLon;
        final double east = f.west + (w - f.left) / f.perLon;
        final double north = f.south + f.spanLat + f.top / f.perLat;
        final double south = north - h / f.perLat;
        final double step = FictionMapMath.gridStep(Math.max(east - west, north - south));
        // lines on whole multiples of the step in real degrees
        final long firstLon = (long) Math.ceil((west + f.cut) / step);
        for (long k = firstLon; k < firstLon + 40; k++) {
            final double e = k * step - f.cut;
            if (e > east) {
                break;
            }
            final float x = (float) (f.left + (e - f.west) * f.perLon);
            canvas.drawLine(x, 0, x, h, stroke);
        }
        final long firstLat = (long) Math.ceil(south / step);
        for (long k = firstLat; k < firstLat + 40; k++) {
            final double lat = k * step;
            if (lat > north) {
                break;
            }
            final float y = f.y(lat);
            canvas.drawLine(0, y, w, y, stroke);
        }
    }

    // ------------------------------------------------------------------ without: places in order

    private void drawScheme(Canvas canvas, int w) {
        final int n = model.dots.size();
        final int per = perRow(w);
        final float pad = dp(SCHEME_PAD_DP);
        final float[][] p = FictionMapMath.schematic(n, per, w, pad, dp(ROW_DP));
        if (showRoute && n > 1) {
            stroke.setColor(CanvasLook.accent());
            stroke.setStrokeWidth(dp(2.5f));
            final Path line = new Path();
            line.moveTo(p[0][0], p[0][1]);
            for (int i = 1; i < n; i++) {
                line.lineTo(p[i][0], p[i][1]);
            }
            canvas.drawPath(line, stroke);
        }
        final float cell = per <= 1 ? w - dp(16) : (w - 2 * pad) / (per - 1) - dp(8);
        for (int i = 0; i < n; i++) {
            dot(canvas, p[i][0], p[i][1], true);
        }
        if (model.here >= 0 && model.here < n) {
            here(canvas, p[model.here][0], p[model.here][1]);
        }
        text.setTypeface(CanvasLook.ui(getContext(), 600));
        text.setColor(CanvasLook.ink());
        for (int i = 0; i < n; i++) {
            final CharSequence s = TextUtils.ellipsize(model.dots.get(i).name, text, Math.max(dp(40), cell),
                    TextUtils.TruncateAt.END);
            final float tw = text.measureText(s, 0, s.length());
            final float x = Math.max(dp(6), Math.min(w - dp(6) - tw, p[i][0] - tw / 2));
            canvas.drawText(s, 0, s.length(), x, p[i][1] + dp(12) - text.ascent(), text);
        }
    }

    // ------------------------------------------------------------------ marks

    private void dot(Canvas canvas, float x, float y, boolean visited) {
        fill.setColor(visited ? CanvasLook.accent() : CanvasLook.bg());
        canvas.drawCircle(x, y, dp(3.5f), fill);
        stroke.setColor(visited ? CanvasLook.bg() : CanvasLook.muted());
        stroke.setStrokeWidth(dp(1.5f));
        canvas.drawCircle(x, y, dp(3.5f), stroke);
    }

    /** "You are here": the accent dot in a soft ring. */
    private void here(Canvas canvas, float x, float y) {
        fill.setColor((CanvasLook.accent() & 0x00FFFFFF) | 0x29000000);
        canvas.drawCircle(x, y, dp(10), fill);
        fill.setColor(CanvasLook.accent());
        canvas.drawCircle(x, y, dp(5), fill);
        stroke.setColor(CanvasLook.bg());
        stroke.setStrokeWidth(dp(2));
        canvas.drawCircle(x, y, dp(5), stroke);
    }

    /** A place's name beside its dot, where it covers no other name; left out when there is no room. */
    private void name(Canvas canvas, String name, float x, float y, boolean visited, List<RectF> taken, int w, int h) {
        text.setTypeface(CanvasLook.ui(getContext(), visited ? 600 : 400));
        final CharSequence s = TextUtils.ellipsize(name, text, dp(120), TextUtils.TruncateAt.END);
        final float tw = text.measureText(s, 0, s.length()) + dp(8);
        final float th = text.descent() - text.ascent() + dp(2);
        final float off = dp(7);
        final float[][] spots = {
                {x + off, y + off}, {x + off, y - off - th}, {x - off - tw, y + off}, {x - off - tw, y - off - th},
                {x - tw / 2, y + off + dp(2)}, {x - tw / 2, y - off - dp(2) - th}};
        for (float[] spot : spots) {
            final RectF r = new RectF(spot[0], spot[1], spot[0] + tw, spot[1] + th);
            if (r.left < dp(2) || r.top < dp(2) || r.right > w - dp(2) || r.bottom > h - dp(2)) {
                continue;
            }
            boolean free = true;
            for (RectF t : taken) {
                if (RectF.intersects(t, r)) {
                    free = false;
                    break;
                }
            }
            if (!free) {
                continue;
            }
            taken.add(r);
            fill.setColor((CanvasLook.surface() & 0x00FFFFFF) | 0xEB000000);
            canvas.drawRoundRect(r, dp(4), dp(4), fill);
            text.setColor(visited ? CanvasLook.ink() : CanvasLook.ink2());
            canvas.drawText(s, 0, s.length(), r.left + dp(4), r.top + dp(1) - text.ascent(), text);
            return;
        }
    }
}
