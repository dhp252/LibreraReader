package com.foobnix.annotation;

import android.app.Activity;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.DrawView;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects the annotation toolbar to an open book: the pen draws on a {@link DrawView} over the
 * page, the eraser deletes the annotation tapped (AppState.EDIT_DELETE, which both views
 * handle), and with a markup tool on a finished text selection becomes a highlight, underline or
 * strikethrough at once ({@link #markSelection}) instead of opening the selection menu.
 */
public class AnnotationSession implements AnnotationToolbar.Listener {

    private static WeakReference<AnnotationSession> current = new WeakReference<>(null);

    private final Activity activity;
    private final DocumentController dc;
    private final DrawView drawView;
    private final AnnotationToolbar toolbar;
    private int activeTool = AnnotationTools.NONE;

    /** One per open book; the toolbar floats at the bottom of the reader. */
    public static AnnotationSession attach(Activity activity, DocumentController dc, DrawView drawView) {
        final AnnotationSession session = new AnnotationSession(activity, dc, drawView);
        current = new WeakReference<>(session);
        return session;
    }

    private AnnotationSession(Activity activity, DocumentController dc, DrawView drawView) {
        this.activity = activity;
        this.dc = dc;
        this.drawView = drawView;
        // a tool left on when an earlier book was closed must not act in this one
        AppState.get().editWith = AppState.EDIT_NONE;

        toolbar = new AnnotationToolbar(activity);
        toolbar.setListener(this);
        toolbar.setVisibility(View.GONE);
        final FrameLayout content = activity.findViewById(android.R.id.content);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = Dips.dpToPx(80);
        content.addView(toolbar, params);
    }

    public boolean isShown() {
        return toolbar.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (isShown()) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        toolbar.setVisibility(View.VISIBLE);
        toolbar.bringToFront();
        final int tool = AppState.get().annotTool;
        toolbar.setTool(tool);
        apply(tool);
        hint(tool);
    }

    public void hide() {
        toolbar.setVisibility(View.GONE);
        apply(AnnotationTools.NONE);
    }

    @Override
    public void onToolChanged(int tool) {
        if (tool != AnnotationTools.NONE) {
            AppState.get().annotTool = tool;
        }
        final boolean changed = tool != activeTool;
        apply(tool);
        if (changed) {
            hint(tool);
        }
    }

    @Override
    public void onUndo() {
        if (!dc.undoLastAnnotation()) {
            Toast.makeText(activity, R.string.annot_nothing_to_undo, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClose() {
        hide();
    }

    private void apply(int tool) {
        activeTool = tool;
        if (tool == AnnotationTools.ERASER) {
            AppState.get().editWith = AppState.EDIT_DELETE;
        } else if (tool == AnnotationTools.PEN) {
            AppState.get().editWith = AppState.EDIT_PEN;
        } else {
            AppState.get().editWith = AppState.EDIT_NONE;
        }
        drawView.clear();
        if (tool == AnnotationTools.PEN) {
            // set here, not once: the old edit panel may have replaced it
            drawView.setOnFinishDraw(this::onStroke);
            drawView.setColor(AnnotationTools.colorInt(AnnotationTools.PEN), AppState.get().editLineWidth);
            drawView.setVisibility(View.VISIBLE);
            toolbar.bringToFront();
        } else {
            drawView.setVisibility(View.GONE);
        }
    }

    private void hint(int tool) {
        final int text;
        if (AnnotationTools.isMarkup(tool)) {
            text = R.string.annot_hint_markup;
        } else if (tool == AnnotationTools.PEN) {
            text = R.string.annot_hint_pen;
        } else if (tool == AnnotationTools.ERASER) {
            text = R.string.annot_hint_eraser;
        } else {
            return;
        }
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private void onStroke() {
        final List<PointF> points = new ArrayList<>();
        for (PointF p : drawView.getPoints()) {
            points.add(new PointF(p.x, p.y));
        }
        drawView.clear();
        if (points.size() < 2) {
            return;
        }
        dc.saveChanges(points, AnnotationTools.colorInt(AnnotationTools.PEN));
    }

    /** True while the toolbar of {@code dc} is shown: the old edit panel stays closed then. */
    public static boolean isShownFor(DocumentController dc) {
        final AnnotationSession session = current.get();
        return session != null && session.dc == dc && session.isShown();
    }

    /**
     * A text selection is finished: with a markup tool on it is marked at once.
     *
     * @return true when it was, so the selection menu is not shown
     */
    public static boolean markSelection(DocumentController dc) {
        final AnnotationSession session = current.get();
        if (session == null || session.dc != dc || !session.isShown() || !AnnotationTools.isMarkup(session.activeTool)) {
            return false;
        }
        dc.underlineText(AnnotationTools.colorInt(session.activeTool), 2.0f, AnnotationTools.markupType(session.activeTool));
        dc.clearSelectedText();
        return true;
    }
}
