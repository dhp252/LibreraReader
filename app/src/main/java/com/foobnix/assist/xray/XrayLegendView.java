package com.foobnix.assist.xray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The X-ray legend strip of the canvas: "X-RAY  Claim  Evidence  Example  Caveat", each word
 * marked as the page marks it ({@link XrayStyle#drawMark}); optionally with how many ideas of the
 * chapter do each job. Words wrap to a second row on a narrow screen.
 */
public class XrayLegendView extends View {

    private static final XrayKind[] KINDS = {XrayKind.CLAIM, XrayKind.EVIDENCE, XrayKind.EXAMPLE, XrayKind.CAVEAT};

    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caveat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String title;
    private final String[] names;
    private Map<XrayKind, Integer> counts;

    /** Where each piece goes, as laid out by the last measure: x, baseline. */
    private final List<float[]> places = new ArrayList<>();
    private float rowHeight;

    public XrayLegendView(Context c) {
        super(c);
        title = c.getString(R.string.xray_legend_title);
        names = new String[]{c.getString(R.string.xray_kind_claim), c.getString(R.string.xray_kind_evidence),
                c.getString(R.string.xray_kind_example), c.getString(R.string.xray_kind_caveat)};
        label.setTypeface(CanvasLook.mono(c, 400));
        label.setTextSize(sp(CanvasLook.TEXT_LABEL));
        label.setLetterSpacing(0.04f);
        label.setColor(CanvasLook.muted());
        word.setTypeface(Typeface.SERIF);
        word.setTextSize(sp(14));
        word.setColor(CanvasLook.ink());
        caveat.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        caveat.setTextSize(sp(14));
        caveat.setColor(CanvasLook.muted());
        number.setTypeface(CanvasLook.ui(c, 400));
        number.setTextSize(sp(CanvasLook.TEXT_SMALL));
        number.setColor(CanvasLook.muted());
        final int pad = CanvasLook.dp(10);
        setPadding(CanvasLook.dp(CanvasLook.GUTTER_DP), pad, CanvasLook.dp(CanvasLook.GUTTER_DP), pad);
    }

    /** Adds the legend to the AI colors list, while the X-ray is on. */
    public static void addTo(LinearLayout list) {
        if (Assist.XRAY.isOn()) {
            final XrayLegendView legend = new XrayLegendView(list.getContext());
            legend.setPadding(0, legend.getPaddingTop(), 0, legend.getPaddingBottom());
            list.addView(legend, 0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** How many ideas do each job, shown after each word (null: none shown). */
    public void setCounts(Map<XrayKind, Integer> counts) {
        this.counts = counts;
        requestLayout();
        invalidate();
    }

    private float sp(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    private String countOf(int i) {
        if (counts == null) {
            return null;
        }
        final Integer n = counts.get(KINDS[i]);
        return " " + (n == null ? 0 : n);
    }

    private float widthOf(int i) {
        final Paint p = KINDS[i] == XrayKind.CAVEAT ? caveat : word;
        float w = p.measureText(names[i]) + (KINDS[i] == XrayKind.CLAIM ? CanvasLook.dp(8) : 0);
        final String n = countOf(i);
        if (n != null) {
            w += number.measureText(n);
        }
        return w;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        final float right = (width > 0 ? width : Float.MAX_VALUE) - getPaddingRight();
        final Paint.FontMetrics fm = word.getFontMetrics();
        rowHeight = (fm.descent - fm.ascent) + CanvasLook.dp(6);
        final float gap = CanvasLook.dp(16);
        places.clear();
        float x = getPaddingLeft();
        float baseline = getPaddingTop() - fm.ascent;
        places.add(new float[]{x, baseline});
        x += label.measureText(title) + gap;
        float widest = x;
        for (int i = 0; i < KINDS.length; i++) {
            final float w = widthOf(i);
            if (x + w > right && x > getPaddingLeft()) {
                x = getPaddingLeft();
                baseline += rowHeight;
            }
            places.add(new float[]{x, baseline});
            x += w;
            widest = Math.max(widest, x);
            x += gap;
        }
        final int height = (int) Math.ceil(baseline + fm.descent + CanvasLook.dp(4) + getPaddingBottom());
        setMeasuredDimension(width > 0 ? width : (int) Math.ceil(widest + getPaddingRight()), resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (places.size() != KINDS.length + 1) {
            return;
        }
        final boolean dark = CanvasLook.isNight();
        final float[] first = places.get(0);
        canvas.drawText(title, first[0], first[1], label);
        final Paint.FontMetrics fm = word.getFontMetrics();
        for (int i = 0; i < KINDS.length; i++) {
            final float[] at = places.get(i + 1);
            final XrayKind kind = KINDS[i];
            final Paint p = kind == XrayKind.CAVEAT ? caveat : word;
            final float pad = kind == XrayKind.CLAIM ? CanvasLook.dp(4) : 0;
            final float w = p.measureText(names[i]);
            final RectF mark = kind == XrayKind.CLAIM
                    ? new RectF(at[0], at[1] + fm.ascent, at[0] + w + 2 * pad, at[1] + fm.descent)
                    : new RectF(at[0], at[1] + fm.ascent, at[0] + w, at[1] + CanvasLook.dp(3));
            if (kind == XrayKind.CAVEAT) {
                // the italic, muted word is its own mark here; only the thin line is drawn
                mark.top = mark.bottom - 1;
            }
            XrayStyle.drawMark(canvas, mark, kind, null, dark, CanvasLook.bg());
            canvas.drawText(names[i], at[0] + pad, at[1], p);
            final String n = countOf(i);
            if (n != null) {
                canvas.drawText(n, at[0] + w + 2 * pad, at[1], number);
            }
        }
    }
}
