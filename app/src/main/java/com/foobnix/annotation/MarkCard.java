package com.foobnix.annotation;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.R;

import org.ebookdroid.core.codec.Annotation;

/**
 * The card a tap on a mark opens (its quote, page, color and kind, its note to add or edit, Color
 * and Delete), the note box of the Note tool, and the card of an annotation that is inside the PDF.
 */
public final class MarkCard {

    public interface Listener {
        void onNote(String note);

        void onColor(String color);

        void onDelete();
    }

    public interface NoteListener {
        void onNote(String note);
    }

    public interface ColorListener {
        void onColor(String color);
    }

    private MarkCard() {
    }

    /** "Highlight", "Underline"... of a mark's kind. */
    static String kindName(Activity a, String kind) {
        if (Mark.UNDERLINE.equals(kind)) {
            return a.getString(R.string.annot_underline);
        }
        if (Mark.STRIKE.equals(kind)) {
            return a.getString(R.string.annot_strikeout);
        }
        if (Mark.INK.equals(kind)) {
            return a.getString(R.string.annot_pen);
        }
        return a.getString(R.string.annot_highlight);
    }

    /**
     * @param page 0-based document page the mark was tapped on
     */
    public static void show(final Activity a, final Mark mark, int page, final Listener listener) {
        final LinearLayout content = column(a);
        if (!TextUtils.isEmpty(mark.quote)) {
            content.addView(quoteView(a, mark.quote, 5));
        }
        final TextView info = new TextView(a);
        info.setText(a.getString(R.string.marks_page_kind, page + 1, ColorNames.label(mark.color), kindName(a, mark.kind)));
        info.setAlpha(0.7f);
        info.setTextSize(13);
        info.setPadding(0, Dips.dpToPx(6), 0, Dips.dpToPx(6));
        content.addView(info);

        final EditText note = noteField(a, mark.note);
        if (!mark.isInk()) {
            content.addView(note);
        }

        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setView(content)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    final String text = note.getText().toString().trim();
                    if (!mark.isInk() && !text.equals(mark.note == null ? "" : mark.note.trim())) {
                        listener.onNote(text);
                    }
                })
                .setNeutralButton(R.string.annot_color, (d, w) -> pickColor(a, mark.color, listener::onColor))
                .setNegativeButton(R.string.delete, (d, w) -> listener.onDelete())
                .create();
        dialog.show();
    }

    /** The Note tool: the selected words and a box for the note; an empty note or Cancel makes nothing. */
    public static void askNote(final Activity a, String quote, final NoteListener listener) {
        final LinearLayout content = column(a);
        if (!TextUtils.isEmpty(quote)) {
            content.addView(quoteView(a, quote, 3));
        }
        final EditText note = noteField(a, "");
        content.addView(note);
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(R.string.annot_note)
                .setView(content)
                .setPositiveButton(R.string.save, (d, w) -> {
                    final String text = note.getText().toString().trim();
                    if (!text.isEmpty()) {
                        listener.onNote(text);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        note.requestFocus();
    }

    /** An annotation inside the PDF (older Librera, other apps): its comment, read only, and Delete. */
    public static void showEmbedded(Activity a, Annotation annotation, final Runnable onDelete) {
        final String text = annotation == null ? null : annotation.text;
        new AlertDialog.Builder(a)
                .setTitle(R.string.marks_in_pdf)
                .setMessage(TxtUtils.isNotEmpty(text) ? text : a.getString(R.string.marks_no_comment))
                .setPositiveButton(R.string.close, null)
                .setNegativeButton(R.string.delete, (d, w) -> onDelete.run())
                .show();
    }

    /** The toolbar's colors (Zotero's). */
    public static void pickColor(Activity a, String current, final ColorListener listener) {
        final LinearLayout content = column(a);
        final AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout row = null;
        for (int i = 0; i < ColorNames.PALETTE.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(row);
            }
            final String color = ColorNames.PALETTE[i];
            final View swatch = new View(a);
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(color));
            if (color.equalsIgnoreCase(current)) {
                circle.setStroke(Dips.dpToPx(3), Color.GRAY);
            }
            swatch.setBackground(circle);
            swatch.setContentDescription(ColorNames.name(color));
            swatch.setOnClickListener(v -> {
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                listener.onColor(color);
            });
            final int size = Dips.dpToPx(40);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(Dips.dpToPx(6), Dips.dpToPx(6), Dips.dpToPx(6), Dips.dpToPx(6));
            row.addView(swatch, params);
        }
        dialog[0] = new AlertDialog.Builder(a).setTitle(R.string.annot_color).setView(content).create();
        dialog[0].show();
    }

    private static LinearLayout column(Activity a) {
        final LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(20);
        content.setPadding(pad, Dips.dpToPx(16), pad, Dips.dpToPx(4));
        return content;
    }

    private static TextView quoteView(Activity a, String quote, int lines) {
        final TextView view = new TextView(a);
        view.setText("“" + quote.trim() + "”");
        view.setTypeface(view.getTypeface(), Typeface.ITALIC);
        view.setTextSize(15);
        view.setMaxLines(lines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private static EditText noteField(Activity a, String text) {
        final EditText note = new EditText(a);
        note.setHint(R.string.annot_add_note);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setMaxLines(6);
        note.setText(text == null ? "" : text);
        note.setSelection(note.getText().length());
        return note;
    }
}
