package com.foobnix.canvas.dialogs;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * Study mode (F-009) in the canvas look. The before / after cards over the page become the
 * canvas's tinted "This section answers" card (Main.dc.html): accent tint, the heading as an
 * accent JetBrains Mono label, the questions 15sp 600 in ink, the actions as accent text. The
 * Study panel and the answer dialog take the same type and tokens. Nothing happens unless
 * {@link CanvasLook#isOn()}.
 */
public final class CanvasStudy {

    private CanvasStudy() {
    }

    // ------------------------------------------------------------------ StudyCardView

    /** The card itself and its ✕. */
    public static void card(LinearLayout card, TextView close) {
        if (card == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(card.getContext());
        card.setBackground(CanvasParts.round(t.accentTint(), t.accentLine(), CanvasLook.radius()));
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(6), CanvasLook.dp(6));
        if (close != null) {
            t.text(close, 18, 400, t.muted());
            close.setGravity(Gravity.CENTER);
            close.setMinWidth(CanvasLook.target());
            close.setMinHeight(CanvasLook.dp(36));
            close.setPadding(0, 0, 0, 0);
            close.setBackground(t.ripple());
        }
    }

    /** The card's first line (chapter and label mark): ink, 15sp 600. */
    public static void cardTitle(TextView v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(v.getContext());
        t.text(v, CanvasLook.TEXT_BODY, 600, t.ink());
        v.setLineSpacing(0, 1.15f);
    }

    /** A line of the card (time, reason): Instrument Sans, ink-2. */
    public static void cardLine(TextView v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(v.getContext());
        t.text(v, 13, 400, t.ink2());
    }

    /** The heading over the questions ("Read for"): the accent mono label. */
    public static void cardHeading(TextView v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        Tone.of(v.getContext()).label(v, true);
        v.setPadding(0, CanvasLook.dp(10), 0, CanvasLook.dp(2));
    }

    /** A question: the card's body, 15sp 600 in ink. */
    public static void cardItem(TextView v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(v.getContext());
        t.text(v, CanvasLook.TEXT_BODY, 600, t.ink());
        v.setLineSpacing(0, 1.15f);
    }

    /** The rule between two chapters' cards. */
    public static void cardDivider(View v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        v.setBackgroundColor(Tone.of(v.getContext()).accentLine());
    }

    /** An action of the card: accent text on a 44 dp target. */
    public static void cardButton(TextView b) {
        if (b == null || !CanvasLook.isOn()) {
            return;
        }
        action(b, Tone.of(b.getContext()));
    }

    private static void action(TextView b, Tone t) {
        t.text(b, CanvasLook.TEXT_TITLE, 600, t.accent());
        b.setBackground(t.ripple());
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinHeight(CanvasLook.target());
        b.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(14), 0);
    }

    // ------------------------------------------------------------------ StudyPanel

    /** The panel's text: Instrument Sans in ink, 600 where the panel set it bold. */
    public static void panelText(TextView v, int sp, boolean bold) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(v.getContext());
        t.text(v, sp, bold ? 600 : 400, sp <= 13 ? t.muted() : t.ink());
    }

    /** An action of the panel (Map, Book map, More, Go to…): accent text. */
    public static void panelAction(TextView b) {
        if (b == null || !CanvasLook.isOn()) {
            return;
        }
        action(b, Tone.of(b.getContext()));
    }

    /** The book's thesis on the tinted card, under a "Thesis" label. */
    public static View thesis(TextView thesis) {
        if (thesis == null || !CanvasLook.isOn()) {
            return thesis;
        }
        final Tone t = Tone.of(thesis.getContext());
        final LinearLayout card = new LinearLayout(thesis.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(t.accentCard());
        card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        final TextView label = new TextView(thesis.getContext());
        label.setText(R.string.canvas_t09_thesis);
        t.label(label, true);
        card.addView(label);
        t.text(thesis, CanvasLook.TEXT_BODY, 600, t.ink());
        thesis.setLineSpacing(0, 1.15f);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(4);
        card.addView(thesis, lp);
        final LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = CanvasLook.dp(4);
        card.setLayoutParams(cardLp);
        return card;
    }

    /** A chapter's row: the one being read on the accent tint, its time in mono, its state in accent. */
    public static void chapterRow(View row, TextView time, TextView state, boolean here) {
        if (row == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(row.getContext());
        row.setBackground(t.ripple(here ? CanvasParts.round(t.accentTint(), 0, CanvasLook.radius()) : null,
                CanvasLook.radius()));
        row.setPadding(CanvasLook.dp(6), row.getPaddingTop(), CanvasLook.dp(6), row.getPaddingBottom());
        row.setMinimumHeight(CanvasLook.target());
        t.mono(time, CanvasLook.TEXT_SMALL, t.muted());
        if (state != null) {
            t.text(state, CanvasLook.TEXT_TITLE, 600, t.accent());
        }
    }

    /** "Read for" in an open chapter: the accent mono label. */
    public static void panelHeading(TextView v) {
        if (v == null || !CanvasLook.isOn()) {
            return;
        }
        Tone.of(v.getContext()).label(v, true);
        v.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(2));
    }

    // ------------------------------------------------------------------ StudyAnswerDialog

    /** The answer dialog, shown: questions 15sp 600, answers in wells, the feedback in ink. */
    public static void answers(AlertDialog d, LinearLayout box, TextView status, TextView feedback, ProgressBar progress) {
        if (d == null || box == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            CanvasDialogs.style(d);
            final Tone t = Tone.of(d.getContext());
            for (int i = 0; i < box.getChildCount(); i++) {
                final View v = box.getChildAt(i);
                if (v instanceof EditText) {
                    CanvasDialogs.field((EditText) v, t);
                } else if (v instanceof TextView && v != feedback) {
                    final TextView q = (TextView) v;
                    final Typeface face = q.getTypeface();
                    t.text(q, CanvasLook.TEXT_BODY, face != null && face.isBold() ? 600 : 400, t.ink());
                    q.setPadding(0, CanvasLook.dp(12), 0, CanvasLook.dp(6));
                }
            }
            if (feedback != null) {
                t.text(feedback, CanvasLook.TEXT_BODY, 400, t.ink());
                feedback.setLineSpacing(0, 1.25f);
                feedback.setLinkTextColor(t.accent());
            }
            if (status != null) {
                t.text(status, CanvasLook.TEXT_SMALL + 1, 400, t.muted());
            }
            CanvasAi.progress(progress, t);
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
