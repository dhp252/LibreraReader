package com.foobnix.canvas.dialogs;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

/**
 * The AI dialogs (F-004) in the canvas look: the summary, the key passages progress and the
 * ideas of a chapter. Their titles stay as written (a book's or a chapter's name); the rest
 * follows {@link CanvasDialogs}. Nothing happens unless {@link CanvasLook#isOn()}.
 */
public final class CanvasAi {

    private CanvasAi() {
    }

    /** AiSummaryDialog, shown: status as an accent mono label, the summary in ink. */
    public static void summary(AlertDialog d, View view) {
        if (d == null || view == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            CanvasDialogs.style(d);
            final Tone t = Tone.of(d.getContext());
            final View status = view.findViewById(R.id.aiStatus);
            if (status instanceof TextView) {
                t.label((TextView) status, true);
                status.setAlpha(1f);
            }
            final View text = view.findViewById(R.id.aiText);
            if (text instanceof TextView) {
                // the summary is read like the book: its serif face is kept
                ((TextView) text).setTextColor(t.ink());
            }
            progress(view.findViewById(R.id.aiProgress), t);
            for (int id : new int[]{R.id.aiCopy, R.id.aiShare}) {
                final View v = view.findViewById(id);
                if (v instanceof ImageView) {
                    ((ImageView) v).setColorFilter(t.ink2(), PorterDuff.Mode.SRC_ATOP);
                    v.setBackground(t.roundButton(0));
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** AiHighlightDialog, shown: the status in ink, the note quieter, the spinner in accent. */
    public static void progress(AlertDialog d, ProgressBar bar, TextView status, TextView note) {
        if (d == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            CanvasDialogs.styleBuilt(d);
            final Tone t = Tone.of(d.getContext());
            progress(bar, t);
            t.text(status, CanvasLook.TEXT_BODY, 500, t.ink());
            if (note != null) {
                t.text(note, CanvasLook.TEXT_SMALL + 1, 400, t.muted());
                note.setAlpha(1f);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** AiIdeasDialog, shown: the counts as a mono label, each idea in ink, a tappable one with a ripple. */
    public static void ideas(AlertDialog d, LinearLayout list) {
        if (d == null || list == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            CanvasDialogs.style(d);
            final Tone t = Tone.of(d.getContext());
            for (int i = 0; i < list.getChildCount(); i++) {
                final View v = list.getChildAt(i);
                if (!(v instanceof TextView)) {
                    continue;
                }
                final TextView tv = (TextView) v;
                if (i == 0) {
                    t.label(tv, false);
                    tv.setAlpha(1f);
                    tv.setPadding(tv.getPaddingLeft(), tv.getPaddingTop(), tv.getPaddingRight(), CanvasLook.dp(6));
                } else {
                    t.text(tv, CanvasLook.TEXT_BODY, 400, t.ink());
                    tv.setLineSpacing(0, 1.2f);
                    if (tv.hasOnClickListeners()) {
                        tv.setBackground(t.ripple());
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    static void progress(View bar, Tone t) {
        if (bar instanceof ProgressBar) {
            ((ProgressBar) bar).setIndeterminateTintList(ColorStateList.valueOf(t.accent()));
        }
    }
}
