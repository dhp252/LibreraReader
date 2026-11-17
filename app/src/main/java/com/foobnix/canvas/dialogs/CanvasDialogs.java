package com.foobnix.canvas.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

/**
 * The app's shared dialogs and popup menus in the canvas look (T9): the sheet in the page colour
 * (night tokens on a dark window), the title 17sp 600 in ink, the message in Instrument Sans, the
 * positive button an accent fill and the others accent text, text fields as rounded wells.
 * <p>
 * Every method does nothing unless {@link CanvasLook#isOn()} (the switch is on and the theme is
 * not Ink), so the call sites keep their old look with the switch off. Call after
 * {@code show()}: the dialog's views exist only then.
 */
public final class CanvasDialogs {

    private CanvasDialogs() {
    }

    /** Restyles a shown dialog; the title is left as written (it may be a book's or a chapter's). */
    public static AlertDialog style(AlertDialog d) {
        return apply(d, false);
    }

    /**
     * Restyles a shown dialog whose title and buttons are the app's own strings: in English they
     * are also set in sentence case ("Contrast and Brightness" → "Contrast and brightness").
     */
    public static AlertDialog styleBuilt(AlertDialog d) {
        return apply(d, true);
    }

    private static AlertDialog apply(AlertDialog d, boolean sentence) {
        if (d == null || !CanvasLook.isOn()) {
            return d;
        }
        try {
            final Context c = d.getContext();
            final Tone t = Tone.of(c);
            final boolean english = sentence && isEnglish(c);
            final Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(t.sheet());
            }

            final int titleId = c.getResources().getIdentifier("alertTitle", "id", "android");
            final View title = titleId == 0 ? null : d.findViewById(titleId);
            if (title instanceof TextView) {
                final TextView tv = (TextView) title;
                t.text(tv, 17, 600, t.ink());
                if (english && tv.getText() != null) {
                    tv.setText(CanvasText.sentenceCase(tv.getText().toString()));
                }
            }
            final View message = d.findViewById(android.R.id.message);
            if (message instanceof TextView) {
                t.text((TextView) message, CanvasLook.TEXT_BODY, 400, t.ink2());
                ((TextView) message).setLineSpacing(0, 1.25f);
            }

            primary(d.getButton(AlertDialog.BUTTON_POSITIVE), t, english);
            quiet(d.getButton(AlertDialog.BUTTON_NEGATIVE), t, english);
            quiet(d.getButton(AlertDialog.BUTTON_NEUTRAL), t, english);

            final View custom = d.findViewById(android.R.id.custom);
            if (custom != null) {
                fields(custom, t);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return d;
    }

    static boolean isEnglish(Context c) {
        try {
            return CanvasText.isEnglish(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
        } catch (Exception e) {
            return false;
        }
    }

    /** The filled accent button. */
    static void primary(Button b, Tone t, boolean english) {
        if (b == null) {
            return;
        }
        button(b, t, english);
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{t.muted(), t.onAccent()}));
        b.setBackground(t.primary());
        b.setPadding(CanvasLook.dp(18), 0, CanvasLook.dp(18), 0);
        if (b.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) b.getLayoutParams();
            lp.leftMargin = CanvasLook.dp(8);
            b.setLayoutParams(lp);
        }
    }

    /** A text button in accent. */
    static void quiet(Button b, Tone t, boolean english) {
        if (b == null) {
            return;
        }
        button(b, t, english);
        b.setTextColor(t.textColors(t.accent()));
        b.setBackground(t.ripple());
        b.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
    }

    private static void button(Button b, Tone t, boolean english) {
        b.setAllCaps(false);
        b.setTypeface(CanvasLook.ui(b.getContext(), 600));
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        b.setLetterSpacing(0f);
        b.setMinHeight(CanvasLook.target());
        b.setMinimumHeight(CanvasLook.target());
        b.setStateListAnimator(null);
        if (english && b.getText() != null) {
            b.setText(CanvasText.sentenceCase(b.getText().toString()));
        }
    }

    /** Text fields under a view as the canvas's rounded wells. */
    static void fields(View v, Tone t) {
        if (v instanceof EditText) {
            field((EditText) v, t);
        } else if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                fields(g.getChildAt(i), t);
            }
        }
    }

    static void field(EditText e, Tone t) {
        e.setBackground(t.field());
        e.setTypeface(CanvasLook.ui(e.getContext(), 400));
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        e.setTextColor(t.ink());
        e.setHintTextColor(t.muted());
        e.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        e.setMinHeight(CanvasLook.target());
        if (e.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) e.getLayoutParams();
            lp.topMargin = Math.max(lp.topMargin, CanvasLook.dp(4));
            lp.bottomMargin = Math.max(lp.bottomMargin, CanvasLook.dp(4));
            e.setLayoutParams(lp);
        }
    }

    // ------------------------------------------------------------------ MyPopupMenu

    /** The popup's sheet: page colour, hairline, the canvas's large round. */
    public static void popup(ListPopupWindow p, Context c) {
        if (p == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(c);
        p.setBackgroundDrawable(com.foobnix.canvas.CanvasParts.round(t.bg(), t.line(),
                CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
    }

    /**
     * A row of MyPopupMenu (item_dict_line), after the menu filled it: 15sp Instrument Sans in ink
     * on a 44 dp row, a head as a mono label, icons in ink-2, a trailing ✓ or › in accent.
     *
     * @param ownFont  the row shows a font by its own face (font menus): its face is kept
     * @param tintIcon the row's icon is a plain mark (not an app icon, not an on/off state)
     */
    public static void menuRow(View row, boolean head, boolean ownFont, boolean tintIcon) {
        if (row == null || !CanvasLook.isOn()) {
            return;
        }
        final Tone t = Tone.of(row.getContext());
        final ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp != null && lp.height > 0 && lp.height < CanvasLook.target()) {
            lp.height = CanvasLook.target();
            row.setLayoutParams(lp);
        }
        row.setMinimumHeight(CanvasLook.target());

        final TextView text = row.findViewById(R.id.text1);
        if (text != null) {
            if (head) {
                t.label(text, false);
                text.setPaintFlags(text.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            } else {
                if (ownFont) {
                    text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
                    text.setTextColor(t.ink());
                } else {
                    t.text(text, CanvasLook.TEXT_BODY, 500, t.ink());
                }
                accentMark(text, t);
            }
        }
        final CheckBox box = row.findViewById(R.id.checkbox1);
        if (box != null && box.getVisibility() == View.VISIBLE) {
            t.text(box, CanvasLook.TEXT_BODY, 500, t.ink());
            box.setButtonTintList(ColorStateList.valueOf(t.accent()));
        }
        if (tintIcon) {
            for (int id : new int[]{R.id.image1, R.id.imageEnd}) {
                final View icon = row.findViewById(id);
                if (icon instanceof ImageView && icon.getVisibility() == View.VISIBLE) {
                    ((ImageView) icon).setColorFilter(t.ink2(), PorterDuff.Mode.SRC_ATOP);
                }
            }
        }
    }

    /** A done mark (✓) or a "more" mark (›) at the end of a row, in accent. */
    private static void accentMark(TextView text, Tone t) {
        final CharSequence s = text.getText();
        if (s == null || s.length() == 0) {
            return;
        }
        final String str = s.toString();
        int at = -1;
        for (String mark : new String[]{"✓", "›"}) {
            final int i = str.lastIndexOf(mark);
            if (i >= 0 && str.substring(i + 1).trim().isEmpty()) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return;
        }
        final SpannableString span = new SpannableString(str);
        span.setSpan(new ForegroundColorSpan(t.accent()), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(span);
    }
}
