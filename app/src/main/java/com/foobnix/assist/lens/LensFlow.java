package com.foobnix.assist.lens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays its children out in rows, left to right, wrapping at its width: the chips of the lens setup. */
final class LensFlow extends ViewGroup {

    private final int gap;

    LensFlow(Context c, int gapPx) {
        super(c);
        gap = gapPx;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int mode = MeasureSpec.getMode(widthSpec);
        final int limit = mode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec);
        final int inner = Math.max(0, limit - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = 0;
        int row = 0;
        int widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            // a child's own size (a chip's 40 dp height) comes from its layout params
            measureChild(child, widthSpec, heightSpec);
            if (x > 0 && x + child.getMeasuredWidth() > inner) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            x += child.getMeasuredWidth() + gap;
            row = Math.max(row, child.getMeasuredHeight());
            widest = Math.max(widest, x - gap);
        }
        final int width = mode == MeasureSpec.EXACTLY ? limit : widest + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(width, resolveSize(y + row + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int inner = Math.max(0, r - l - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            final int w = child.getMeasuredWidth();
            final int h = child.getMeasuredHeight();
            if (x > 0 && x + w > inner) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            child.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
            x += w + gap;
            row = Math.max(row, h);
        }
    }
}
