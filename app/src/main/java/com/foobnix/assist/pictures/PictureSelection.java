package com.foobnix.assist.pictures;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The entry of the text-selection box: an "As a picture" chip in its row of dictionaries, after
 * the lens's chip, for a selection long enough to draw ({@link PicturePrompts#MIN_PASSAGE}). A
 * tap draws just that passage: one request, shown, not stored.
 */
public final class PictureSelection {

    private PictureSelection() {
    }

    /**
     * Nothing is added with Assist.PICTURES off or for a short selection.
     *
     * @param line     the box's row of dictionary apps
     * @param selected holds the selected text (the reader may have edited it)
     * @param close    closes the selection box
     */
    public static void addTo(LinearLayout line, final DocumentController dc, final TextView selected, final Runnable close) {
        if (!Assist.PICTURES.isOn() || line == null || dc == null || selected == null
                || PicturePrompts.passage(selected.getText().toString()).isEmpty()) {
            return;
        }
        final TextView chip = PictureUi.text(line.getContext(), line.getContext().getString(R.string.canvas_t27_as_picture), 13, 600,
                CanvasLook.accent());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        chip.setBackground(CanvasParts.ripple(CanvasParts.chip(true)));
        chip.setOnClickListener(v -> {
            final String text = selected.getText().toString();
            if (PicturePrompts.passage(text).isEmpty()) {
                return;
            }
            if (close != null) {
                close.run();
            }
            dc.clearSelectedText();
            PictureScreen.showPassage(dc.getActivity(), dc, text);
        });
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target());
        lp.rightMargin = CanvasLook.dp(8);
        // after the lens's chip when it is there
        line.addView(chip, Math.min(line.getChildCount(), Assist.LENS.isOn() ? 1 : 0), lp);
    }
}
