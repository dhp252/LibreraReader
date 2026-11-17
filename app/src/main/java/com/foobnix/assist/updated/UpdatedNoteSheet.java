package com.foobnix.assist.updated;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSheet;
import com.foobnix.pdf.info.R;

/**
 * One note of "The book, updated" as a bottom sheet (canvas UpdatedNote): its kind and title, the
 * note, "In your terms" when the reader has a lens and the note has it, how settled it is, its
 * source or "No source given", and always the line that the AI wrote it from its own knowledge.
 * "Quiz me later" is Spaced review's and shows only while that switch is on; the canvas's "Add
 * to my edition" belongs to Your edition and is not here yet; "Go to the sentence" stands in.
 */
final class UpdatedNoteSheet {

    private UpdatedNoteSheet() {
    }

    /**
     * @param lens whether the reader has a lens: without one "In your terms" never shows
     * @param go   goes to the note's sentence, or null for no button
     */
    static Dialog show(Activity a, UpdatedNote note, boolean lens, Runnable go) {
        return show(a, note, lens, go, null);
    }

    /** @param quiz "Quiz me later" (Spaced review, only while its switch is on), or null for no such button */
    static Dialog show(Activity a, UpdatedNote note, boolean lens, Runnable go, Runnable quiz) {
        final LinearLayout box = UpdatedParts.column(a);
        final Dialog[] dialog = new Dialog[1];

        final LinearLayout head = UpdatedParts.row(a);
        final LinearLayout titles = UpdatedParts.column(a);
        titles.addView(UpdatedParts.kindLabel(a, note.kind,
                a.getString(R.string.updated_label) + " · " + a.getString(UpdatedParts.name(note.kind)), CanvasLook.TEXT_LABEL));
        final TextView title = UpdatedParts.text(a, note.title, 19, 600, CanvasLook.ink());
        title.setPadding(0, CanvasLook.dp(2), 0, 0);
        titles.addView(title);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = UpdatedParts.text(a, "✕", 16, 400, CanvasLook.ink2());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(a.getString(R.string.updated_close));
        close.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.radius()));
        close.setOnClickListener(v -> {
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
        });
        final LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target());
        closeLp.leftMargin = CanvasLook.dp(8);
        head.addView(close, closeLp);
        box.addView(head);

        if (!note.body.isEmpty()) {
            final TextView body = UpdatedParts.text(a, note.body, CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
            body.setLineSpacing(0, 1.3f);
            body.setTextIsSelectable(true);
            box.addView(body, UpdatedParts.wide(14));
        }

        if (lens && !note.terms.isEmpty()) {
            final LinearLayout terms = UpdatedParts.column(a);
            terms.setBackground(CanvasParts.accentCard());
            final int p = CanvasLook.dp(12);
            terms.setPadding(p, p, p, p);
            terms.addView(UpdatedParts.mono(a, a.getString(R.string.updated_in_your_terms), 10, CanvasLook.accent()));
            final TextView t = UpdatedParts.text(a, note.terms, 14, 400, CanvasLook.ink());
            t.setLineSpacing(0, 1.3f);
            t.setPadding(0, CanvasLook.dp(4), 0, 0);
            terms.addView(t);
            box.addView(terms, UpdatedParts.wide(14));
        }

        // How settled
        final LinearLayout settled = fact(a, a.getString(R.string.updated_how_settled));
        final LinearLayout value = UpdatedParts.row(a);
        if (note.settled.bars > 0) {
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CanvasLook.dp(6);
            value.addView(UpdatedParts.bars(a, note.settled.bars, CanvasLook.accent()), lp);
        }
        value.addView(UpdatedParts.text(a, UpdatedParts.settledLine(a, note), 13, 400,
                note.settled == UpdatedSettled.UNKNOWN ? CanvasLook.muted() : CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        settled.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(settled, UpdatedParts.wide(8));

        final View rule = new View(a);
        rule.setBackgroundColor(CanvasLook.surface2());
        box.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));

        // Source, or that there is none
        final LinearLayout source = fact(a, a.getString(R.string.updated_source));
        final TextView sourceText;
        if (note.hasSource()) {
            final String line = UpdatedText.sourceLine(note.source);
            final SpannableString s = new SpannableString(line);
            // the work's title in italics, as the canvas has it
            s.setSpan(new StyleSpan(Typeface.ITALIC), 0, Math.min(line.length(), note.source.work.length()),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sourceText = UpdatedParts.text(a, s, 13, 400, CanvasLook.ink());
            sourceText.setTextIsSelectable(true);
        } else {
            sourceText = UpdatedParts.text(a, a.getString(R.string.updated_no_source), 13, 400, CanvasLook.muted());
        }
        source.addView(sourceText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(source);

        box.addView(UpdatedParts.honesty(a), UpdatedParts.wide(6));

        if (go != null) {
            final TextView button = UpdatedParts.button(a, a.getString(R.string.updated_go_to_sentence), false);
            button.setOnClickListener(v -> {
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                go.run();
            });
            final LinearLayout.LayoutParams lp = UpdatedParts.wide(14);
            lp.height = CanvasLook.dp(48);
            box.addView(button, lp);
        }

        if (quiz != null) {
            final TextView button = UpdatedParts.button(a, a.getString(R.string.canvas_t24_quiz_later), false);
            button.setOnClickListener(v -> {
                button.setEnabled(false);
                button.setAlpha(0.4f);
                quiz.run();
            });
            final LinearLayout.LayoutParams lp = UpdatedParts.wide(go != null ? 8 : 14);
            lp.height = CanvasLook.dp(48);
            box.addView(button, lp);
        }

        final ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        dialog[0] = CanvasSheet.show(a, scroll);
        return dialog[0];
    }

    /** A row "name   value": the name in a fixed column, as on the canvas. */
    private static LinearLayout fact(Activity a, String name) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, CanvasLook.dp(10), 0, CanvasLook.dp(10));
        final TextView n = UpdatedParts.text(a, name, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CanvasLook.dp(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = CanvasLook.dp(12);
        row.addView(n, lp);
        return row;
    }
}
