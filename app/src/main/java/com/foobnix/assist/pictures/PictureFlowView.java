package com.foobnix.assist.pictures;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;

import com.foobnix.canvas.CanvasLook;

/**
 * A process as the canvas draws it (Picture): its step boxes left to right in wrapping rows, an
 * arrow after each but the last. The children are the boxes; where they go is
 * {@link PictureLayout#flow}.
 */
final class PictureFlowView extends ViewGroup {

    private final int arrow = CanvasLook.dp(12);
    private final int gap = CanvasLook.dp(4);
    private final int rowGap = CanvasLook.dp(6);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();
    private PictureLayout.Flow flow;

    PictureFlowView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, CanvasLook.dp(12) / 10f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(PictureUi.arrow());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int n = getChildCount();
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED || width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        final int box = MeasureSpec.makeMeasureSpec(PictureLayout.maxBoxWidth(width, arrow, gap), MeasureSpec.AT_MOST);
        final int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int[] widths = new int[n];
        final int[] heights = new int[n];
        for (int i = 0; i < n; i++) {
            final View child = getChildAt(i);
            child.measure(box, free);
            widths[i] = child.getMeasuredWidth();
            heights[i] = child.getMeasuredHeight();
        }
        flow = PictureLayout.flow(widths, heights, arrow, gap, rowGap, width);
        setMeasuredDimension(width, flow.height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (flow == null) {
            return;
        }
        for (int i = 0; i < getChildCount() && i < flow.x.length; i++) {
            final View child = getChildAt(i);
            child.layout(flow.x[i], flow.y[i], flow.x[i] + child.getMeasuredWidth(), flow.y[i] + child.getMeasuredHeight());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (flow == null) {
            return;
        }
        // the canvas's chevron: 3 dp wide, 6 dp tall
        final float w = arrow / 8f;
        final float h = arrow / 4f;
        for (int i = 0; i < flow.arrowX.length; i++) {
            chevron.reset();
            chevron.moveTo(flow.arrowX[i] - w, flow.arrowY[i] - h);
            chevron.lineTo(flow.arrowX[i] + w, flow.arrowY[i]);
            chevron.lineTo(flow.arrowX[i] - w, flow.arrowY[i] + h);
            canvas.drawPath(chevron, paint);
        }
    }
}
