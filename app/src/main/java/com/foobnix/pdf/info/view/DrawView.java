package com.foobnix.pdf.info.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.foobnix.android.utils.Dips;
import com.foobnix.annotation.StylusRules;

import java.util.ArrayList;
import java.util.List;


public class DrawView extends View {
	private final List<PointF> points = new ArrayList<PointF>();
	/** Width step of each point of a stylus pen stroke (StylusRules.STEP_*); empty for a finger. */
	private final List<Integer> steps = new ArrayList<Integer>();
	Paint paint = new Paint();
	{
        paint.setColor(Color.LTGRAY);
		paint.setStrokeWidth(Dips.dpToPx(4));
		paint.setPathEffect(new CornerPathEffect(10));
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStyle(Style.STROKE);
	}
	private final Paint stylusPaint = new Paint(paint);
	private boolean marker;
	private float stylusWidth;
	private Runnable onFinishDraw;

	public DrawView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

    public void setColor(int color, float width) {
		paint.setColor(color);
        paint.setStrokeWidth(Dips.dpToPx((int) width * 2));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (steps.isEmpty() || marker) {
			canvas.drawPath(pointsPath, marker ? stylusPaint : paint);
			return;
		}
		// a pen stroke with pressure: each piece as wide as its step
		for (int i = 1; i < points.size(); i++) {
			stylusPaint.setStrokeWidth(stylusWidth * StylusRules.stepWidth(steps.get(i)));
			final PointF a = points.get(i - 1);
			final PointF b = points.get(i);
			canvas.drawLine(a.x, a.y, b.x, b.y, stylusPaint);
		}
	}

	public void clear() {
		points.clear();
		steps.clear();
		pointsPath.reset();
		invalidate();

	}

	Path pointsPath = new Path();

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE) {
			pointsPath.lineTo(event.getX(), event.getY());
			points.add(new PointF(event.getX(), event.getY()));
			invalidate();
		}
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			points.clear();
			steps.clear();
			pointsPath.reset();
			pointsPath.moveTo(event.getX(), event.getY());
			points.add(new PointF(event.getX(), event.getY()));
			invalidate();

		}
		if (event.getAction() == MotionEvent.ACTION_UP) {
			if (onFinishDraw != null) {
				onFinishDraw.run();
			}

		}

		return true;
	}

	/**
	 * A stylus stroke starts at (x, y) in this view: a pen line {@code widthPx} wide at normal
	 * pressure, or a see-through marker that wide.
	 */
	public void startStylus(float x, float y, float pressure, int color, float widthPx, boolean marker) {
		this.marker = marker;
		stylusWidth = widthPx;
		stylusPaint.set(paint);
		stylusPaint.setPathEffect(marker ? null : new CornerPathEffect(10));
		stylusPaint.setColor(marker ? (color & 0x00ffffff) | 0x66000000 : color);
		stylusPaint.setStrokeWidth(widthPx);
		stylusPaint.setStrokeCap(marker ? Paint.Cap.SQUARE : Paint.Cap.ROUND);
		points.clear();
		steps.clear();
		pointsPath.reset();
		pointsPath.moveTo(x, y);
		addStylus(x, y, pressure);
	}

	public void moveStylus(float x, float y, float pressure) {
		pointsPath.lineTo(x, y);
		addStylus(x, y, pressure);
	}

	private void addStylus(float x, float y, float pressure) {
		points.add(new PointF(x, y));
		steps.add(marker ? StylusRules.STEP_NORMAL : StylusRules.step(pressure));
		invalidate();
	}

	public List<PointF> getPoints() {
		return points;
	}

	/** The width step of each point of the last stylus stroke (empty for a finger's). */
	public List<Integer> getSteps() {
		return steps;
	}

	public void setOnFinishDraw(Runnable onFinishDraw) {
		this.onFinishDraw = onFinishDraw;
	}

}
