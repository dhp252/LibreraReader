package com.foobnix.annotation;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.PointF;
import android.graphics.RectF;

import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyProgressDialog;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * "Save marks into PDF" (D-59): the open PDF's separate marks become PDF annotations (notes as
 * comments, pen strokes as ink), the file is written at once, and only then the marks leave the
 * separate store, so they are never drawn twice nor lost. A save that fails takes the added
 * annotations back.
 */
public final class SaveIntoPdf {

    public interface Notice {
        void show(CharSequence text);
    }

    private SaveIntoPdf() {
    }

    /** A real PDF file Librera may write (not XPS, not password-protected). */
    static boolean canWrite(DocumentController dc) {
        return dc.getCurrentBook() != null && dc.getCurrentBook().getName().toLowerCase(Locale.US).endsWith(".pdf")
                && !dc.isPasswordProtected() && !dc.isTextFormat();
    }

    private static List<Mark> marks(DocumentController dc) {
        final List<Mark> marks = new ArrayList<>();
        if (!canWrite(dc)) {
            return marks;
        }
        for (Mark mark : MarksLayer.get().marks()) {
            if (mark.isOnPositions() && (!mark.parts.isEmpty() || !mark.ink.isEmpty()) && fits(dc, mark)) {
                marks.add(mark);
            }
        }
        return marks;
    }

    /** Every page of the mark is a page of this file (another edition with the same name may be shorter). */
    private static boolean fits(DocumentController dc, Mark mark) {
        for (Mark.Part part : mark.parts) {
            if (dc.pageSizeUnits(part.page) == null) {
                return false;
            }
        }
        for (Mark.Ink run : mark.ink) {
            if (dc.pageSizeUnits(run.page) == null) {
                return false;
            }
        }
        return true;
    }

    /** How many marks the book's PDF could take now; 0 when it is not a PDF that can be written. */
    public static int countFor(DocumentController dc) {
        return marks(dc).size();
    }

    public static void confirm(final Activity a, final DocumentController dc, final Notice notice) {
        final List<Mark> marks = marks(dc);
        if (marks.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(a)
                .setMessage(a.getString(R.string.marks_save_confirm, marks.size()))
                .setPositiveButton(R.string.save, (d, w) -> save(a, dc, marks, notice))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void save(final Activity a, final DocumentController dc, final List<Mark> marks, final Notice notice) {
        final String book = MarksLayer.bookName(dc.getCurrentBook().getPath());
        final MyProgressDialog progress = MyProgressDialog.show(a, a.getString(R.string.saving_));
        progress.setCancelable(false);
        progress.show();
        // one undo group: a failed save takes all of them back
        dc.beginAnnotationGroup();
        try {
            for (Mark mark : marks) {
                final int color = MarksLayer.color(mark);
                if (mark.isInk()) {
                    for (Mark.Ink run : mark.ink) {
                        final float[] size = dc.pageSizeUnits(run.page);
                        if (size == null) {
                            continue;
                        }
                        final PointF[] arc = new PointF[run.points.length / 2];
                        for (int i = 0; i < arc.length; i++) {
                            arc[i] = new PointF(run.points[i * 2] * size[0], run.points[i * 2 + 1] * size[1]);
                        }
                        dc.addInk(run.page, Collections.singletonList(arc), color, run.width * size[0]);
                    }
                    continue;
                }
                boolean first = true;
                for (Mark.Part part : mark.parts) {
                    final float[] size = dc.pageSizeUnits(part.page);
                    if (size == null) {
                        continue;
                    }
                    final List<RectF> lines = new ArrayList<>();
                    for (int i = 0; i + 3 < part.rects.length; i += 4) {
                        lines.add(new RectF(part.rects[i] * size[0], part.rects[i + 1] * size[1],
                                part.rects[i + 2] * size[0], part.rects[i + 3] * size[1]));
                    }
                    // the note goes with the mark's first lines only
                    dc.addMarkupForSave(part.page, lines, AnnotationTools.annotationType(mark.kind), color,
                            first && mark.hasNote() ? mark.note.trim() : null);
                    first = false;
                }
            }
        } finally {
            dc.endAnnotationGroup();
        }
        dc.saveAnnotationsNow(saved -> {
            progress.dismiss();
            if (saved) {
                MarkEdits.apply(book, all -> {
                    for (Mark mark : marks) {
                        all.remove(mark.id);
                    }
                }, null);
                notice.show(a.getString(R.string.marks_saved_into_pdf, marks.size()));
            } else {
                dc.undoLastAnnotation();
                notice.show(a.getString(R.string.marks_save_failed));
            }
            return false;
        });
    }
}
