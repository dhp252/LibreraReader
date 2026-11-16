package com.foobnix.canvas;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.pdf.info.R;

/**
 * The canvas's cards, built in code: the tinted "THIS SECTION ANSWERS" card
 * ({@link #answers}: accent tint, accent mono label, the question in 15 sp weight 600) and a plain
 * card ({@link #plain}: surface with a hairline, muted mono label, 15 sp body). Each is a vertical
 * LinearLayout whose child 0 is the label and child 1 the body ({@link #label}, {@link #body});
 * the caller adds more children or sets layout params (see {@link #inGutters()}). Callers check
 * {@link CanvasLook#isOn()} first.
 */
public final class CanvasCard {

    private CanvasCard() {
    }

    /** The tinted card with the label "THIS SECTION ANSWERS". */
    public static LinearLayout answers(Context c, CharSequence question) {
        return answers(c, c.getString(R.string.canvas_t01_section_answers), question);
    }

    /** The tinted accent card: label in accent mono, body 15 sp weight 600 in ink. */
    public static LinearLayout answers(Context c, CharSequence label, CharSequence body) {
        final LinearLayout card = make(c, label, body, true);
        card.setBackground(CanvasParts.accentCard());
        card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        return card;
    }

    /** A plain card: surface with a hairline, label in muted mono, body 15 sp in ink. */
    public static LinearLayout plain(Context c, CharSequence label, CharSequence body) {
        final LinearLayout card = make(c, label, body, false);
        card.setBackground(CanvasParts.card());
        final int pad = CanvasLook.dp(12);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    /** The card's mono label (child 0). */
    public static TextView label(LinearLayout card) {
        return (TextView) card.getChildAt(0);
    }

    /** The card's body (child 1). */
    public static TextView body(LinearLayout card) {
        return (TextView) card.getChildAt(1);
    }

    /** Full width with the 16 dp gutters, for a vertical LinearLayout parent. */
    public static LinearLayout.LayoutParams inGutters() {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CanvasLook.dp(CanvasLook.GUTTER_DP);
        lp.rightMargin = CanvasLook.dp(CanvasLook.GUTTER_DP);
        return lp;
    }

    private static LinearLayout make(Context c, CharSequence labelText, CharSequence bodyText, boolean accent) {
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        final TextView label = new TextView(c);
        CanvasParts.label(label, accent);
        label.setText(labelText);
        card.addView(label);
        final TextView body = new TextView(c);
        CanvasParts.text(body, CanvasLook.TEXT_BODY, accent ? 600 : 400);
        body.setLineSpacing(CanvasLook.dp(accent ? 2 : 4), 1f);
        body.setText(bodyText);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(4);
        card.addView(body, lp);
        return card;
    }
}
