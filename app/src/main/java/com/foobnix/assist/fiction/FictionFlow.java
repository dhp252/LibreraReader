package com.foobnix.assist.fiction;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.foobnix.canvas.CanvasLook;

/** Lays its children out in rows, left to right, wrapping at its width: the "places so far" chips. */
final class FictionFlow extends ViewGroup {

    private final int gap;

    FictionFlow(Context c, int gapDp) {
        super(c);
        gap = CanvasLook.dp(gapDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : MeasureSpec.getSize(widthMeasureSpec);
        final int room = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        final int childSpec = MeasureSpec.makeMeasureSpec(width == Integer.MAX_VALUE ? 0 : room,
                width == Integer.MAX_VALUE ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST);
        int x = 0;
        int y = 0;
        int row = 0;
        int widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View v = getChildAt(i);
            if (v.getVisibility() == GONE) {
                continue;
            }
            v.measure(childSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + v.getMeasuredWidth() > room) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            x += v.getMeasuredWidth() + gap;
            widest = Math.max(widest, x - gap);
            row = Math.max(row, v.getMeasuredHeight());
        }
        final int w = width == Integer.MAX_VALUE ? widest + getPaddingLeft() + getPaddingRight() : width;
        setMeasuredDimension(w, resolveSize(y + row + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int room = Math.max(0, r - l - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View v = getChildAt(i);
            if (v.getVisibility() == GONE) {
                continue;
            }
            if (x > 0 && x + v.getMeasuredWidth() > room) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + v.getMeasuredWidth(),
                    getPaddingTop() + y + v.getMeasuredHeight());
            x += v.getMeasuredWidth() + gap;
            row = Math.max(row, v.getMeasuredHeight());
        }
    }
}
