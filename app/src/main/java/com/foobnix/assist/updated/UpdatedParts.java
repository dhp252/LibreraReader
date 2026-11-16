package com.foobnix.assist.updated;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/** Views and colours the screens of "The book, updated" share (canvas tokens, day and night). */
final class UpdatedParts {

    /** The canvas's orange of a doubtful story and of the objection (day). */
    static final int WARN_BAR = 0xFFF27420;
    static final int WARN_LINE = 0xFFF3C6A5;
    static final int WARN_TINT = 0xFFFFF1E6;
    static final int WARN_CARD = 0xFFFFF8F3;
    static final int WARN_LINE_N = 0xFF7A4A24;
    static final int WARN_CARD_N = 0xFF2B2018;

    private UpdatedParts() {
    }

    // ------------------------------------------------------------------ kinds

    static int name(UpdatedKind kind) {
        switch (kind) {
            case SOURCE:
                return R.string.updated_kind_source;
            case MISSED:
                return R.string.updated_kind_missed;
            case DOUBTFUL:
                return R.string.updated_kind_doubtful;
            default:
                return R.string.updated_kind_newer;
        }
    }

    static int icon(UpdatedKind kind) {
        switch (kind) {
            case SOURCE:
                return R.drawable.canvas_t22_ic_source;
            case MISSED:
                return R.drawable.canvas_t22_ic_missed;
            case DOUBTFUL:
                return R.drawable.canvas_t22_ic_doubtful;
            default:
                return R.drawable.canvas_t22_ic_newer;
        }
    }

    /** The kind's ink: the accent, orange for a doubtful story. */
    static int color(UpdatedKind kind) {
        return kind == UpdatedKind.DOUBTFUL ? CanvasLook.sensor() : CanvasLook.accent();
    }

    static int tint(UpdatedKind kind) {
        return kind == UpdatedKind.DOUBTFUL ? warnTint() : CanvasLook.accentTint();
    }

    static int line(UpdatedKind kind) {
        return kind == UpdatedKind.DOUBTFUL ? warnLine() : CanvasLook.accentLine();
    }

    static int warnTint() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_TINT_N : WARN_TINT;
    }

    static int warnLine() {
        return CanvasLook.isNight() ? WARN_LINE_N : WARN_LINE;
    }

    static int warnCard() {
        return CanvasLook.isNight() ? WARN_CARD_N : WARN_CARD;
    }

    static int warnBar() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_N : WARN_BAR;
    }

    static int settledName(UpdatedSettled s) {
        switch (s) {
            case ESTABLISHED:
                return R.string.updated_settled_established;
            case DEBATED:
                return R.string.updated_settled_debated;
            case SPECULATIVE:
                return R.string.updated_settled_speculative;
            default:
                return R.string.updated_settled_unknown;
        }
    }

    /** "Well established, now called the Babbage principle". */
    static String settledLine(Context c, UpdatedNote n) {
        final String name = c.getString(settledName(n.settled));
        return n.settledNote.isEmpty() || n.settled == UpdatedSettled.UNKNOWN ? name : name + ", " + n.settledNote;
    }

    /** The kind's icon in a colour, {@code dp} wide. */
    static Drawable icon(Context c, int res, int color, int dp) {
        final Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setTint(color);
        d.setBounds(0, 0, CanvasLook.dp(dp), CanvasLook.dp(dp));
        return d;
    }

    // ------------------------------------------------------------------ views

    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(c, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** A JetBrains Mono label in capitals. */
    static TextView mono(Context c, CharSequence s, float sp, int color) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(c, 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLetterSpacing(0.05f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    /** A mono label with a kind's icon before it. */
    static TextView kindLabel(Context c, UpdatedKind kind, CharSequence s, float sp) {
        final TextView t = mono(c, s, sp, color(kind));
        t.setCompoundDrawables(icon(c, icon(kind), color(kind), 14), null, null, null);
        t.setCompoundDrawablePadding(CanvasLook.dp(6));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    static TextView button(Context c, CharSequence s, boolean primary) {
        final TextView t = text(c, s, CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        t.setMinHeight(CanvasLook.dp(48));
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }

    /** Three small bars, {@code filled} of them in {@code color}: how settled, how strong. */
    static View bars(Context c, int filled, int color) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < 3; i++) {
            final View bar = new View(c);
            bar.setBackground(CanvasParts.round(i < filled ? color : CanvasLook.line(), 0, CanvasLook.dp(2)));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CanvasLook.dp(10), CanvasLook.dp(6));
            lp.leftMargin = i == 0 ? 0 : CanvasLook.dp(2);
            row.addView(bar, lp);
        }
        return row;
    }

    /** The honesty rule: the same line on every note sheet and on the objection screen. */
    static TextView honesty(Context c) {
        final TextView t = text(c, c.getString(R.string.updated_honesty), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    static LinearLayout column(Context c) {
        final LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        final LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams wide(int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }
}
