package com.foobnix.canvas;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The canvas's segmented control (the Full / Lean / Claims / 1 line bar of the reader): a
 * {@link CanvasParts#segmentTrack() track} with equal 44 dp segments, labels at 13 sp weight 600,
 * the chosen one on a raised {@link CanvasParts#segmentOn() page-coloured} segment in ink, the
 * others in ink 2.
 * <p>
 * Build one from labels with {@link #create}, or restyle a horizontal {@link LinearLayout} of
 * {@link TextView}s from a layout with {@link #style}; then {@link #setOnSelectListener} and add
 * {@link #getView()} where it goes. Callers check {@link CanvasLook#isOn()} first.
 */
public final class CanvasSegmented {

    /** Called when the reader picks a segment (not for {@link #select} from code). */
    public interface OnSelectListener {
        void onSelect(int index);
    }

    private final LinearLayout track;
    private final List<TextView> segments = new ArrayList<>();
    private int selected = -1;
    private OnSelectListener listener;

    private CanvasSegmented(LinearLayout track) {
        this.track = track;
    }

    /** A new control with these labels, the first chosen. */
    public static CanvasSegmented create(Context c, List<? extends CharSequence> labels) {
        final LinearLayout track = new LinearLayout(c);
        for (CharSequence label : labels) {
            final TextView t = new TextView(c);
            t.setText(label);
            track.addView(t);
        }
        return style(track);
    }

    /** A new control with these labels, the first chosen. */
    public static CanvasSegmented create(Context c, CharSequence... labels) {
        final List<CharSequence> list = new ArrayList<>();
        for (CharSequence l : labels) {
            list.add(l);
        }
        return create(c, list);
    }

    /**
     * Styles an existing row of TextViews (other children are left as they are) as segments; the
     * one that is {@link View#isSelected()} is chosen, else the first.
     */
    public static CanvasSegmented style(LinearLayout row) {
        final CanvasSegmented s = new CanvasSegmented(row);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(CanvasParts.segmentTrack());
        final int pad = CanvasLook.dp(3);
        row.setPadding(pad, pad, pad, pad);
        int chosen = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            final View v = row.getChildAt(i);
            if (!(v instanceof TextView)) {
                continue;
            }
            final TextView t = (TextView) v;
            final int index = s.segments.size();
            if (t.isSelected()) {
                chosen = index;
            }
            s.segments.add(t);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, CanvasLook.target(), 1);
            lp.leftMargin = index == 0 ? 0 : CanvasLook.dp(2);
            t.setLayoutParams(lp);
            t.setMinWidth(0);
            t.setMinHeight(0);
            t.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(4), 0);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            CanvasParts.text(t, 13f, 600);
            t.setClickable(true);
            t.setFocusable(true);
            t.setOnClickListener(x -> {
                if (s.selected != index) {
                    s.select(index);
                    if (s.listener != null) {
                        s.listener.onSelect(index);
                    }
                }
            });
        }
        s.select(chosen);
        return s;
    }

    /** The track, to add to a layout (its width is the caller's; segments share it equally). */
    public LinearLayout getView() {
        return track;
    }

    public int getSelected() {
        return selected;
    }

    public int size() {
        return segments.size();
    }

    /** The TextView of a segment (to change its label or content description). */
    public TextView segment(int index) {
        return segments.get(index);
    }

    public void setOnSelectListener(OnSelectListener l) {
        listener = l;
    }

    /** Chooses a segment without telling the listener. */
    public void select(int index) {
        if (index < 0 || index >= segments.size()) {
            return;
        }
        selected = index;
        for (int i = 0; i < segments.size(); i++) {
            final TextView t = segments.get(i);
            final boolean on = i == index;
            t.setSelected(on);
            t.setBackground(on ? CanvasParts.segmentOn() : null);
            t.setElevation(on ? CanvasLook.dp(1) : 0);
            t.setTextColor(on ? CanvasLook.ink() : CanvasLook.ink2());
        }
    }

    /** Lays the track out full width with the canvas gutters; for code that builds a screen. */
    public static ViewGroup.MarginLayoutParams fullWidth() {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CanvasLook.dp(CanvasLook.GUTTER_DP);
        lp.rightMargin = CanvasLook.dp(CanvasLook.GUTTER_DP);
        return lp;
    }
}
