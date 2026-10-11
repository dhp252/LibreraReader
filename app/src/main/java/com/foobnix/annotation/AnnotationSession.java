package com.foobnix.annotation;

import android.app.Activity;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.DrawView;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.search.activity.HorizontalModeController;

import org.ebookdroid.BookType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    // ---- the stylus: draws at once, fingers keep reading (.agents/planning/stylus-annotation-design.md) ----

    /** How wide the highlighter marks, on screen. */
    private static final int MARKER_DP = 20;
    /** Pressure flicker shorter than this many points keeps the width before it. */
    private static final int STEADY_POINTS = 4;

    private boolean stylusDown;
    private boolean stylusHovering;
    private long stylusLastSeen;
    private boolean palmGesture;
    /** What the stylus touching the screen does (StylusRules.DRAW_*). */
    private int stylusDraw = StylusRules.DRAW_NONE;
    private int editWithBeforeEraser;
    private int drawViewVisibilityBefore = View.GONE;
    private final int[] drawViewOrigin = new int[2];

    /**
     * Called first by the reader's dispatchTouchEvent.
     *
     * @return true when the touch was the stylus drawing, or a palm, and is used up
     */
    public static boolean onTouch(Activity a, MotionEvent ev) {
        final AnnotationSession session = current.get();
        if (session == null || session.activity != a) {
            return false;
        }
        try {
            return session.route(ev);
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }
    }

    /** Called by the reader's dispatchGenericMotionEvent: a stylus hovering starts the palm rule. */
    public static void onHover(Activity a, MotionEvent ev) {
        final AnnotationSession session = current.get();
        if (session == null || session.activity != a || ev.getPointerCount() == 0
                || !StylusRules.isStylus(ev.getToolType(0))) {
            return;
        }
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            session.stylusHovering = true;
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            session.stylusHovering = false;
        }
        session.stylusLastSeen = SystemClock.uptimeMillis();
    }

    /** PDFs only, and one page per screen in book mode, as the toolbar. */
    private boolean stylusWorks() {
        if (dc.getCurrentBook() == null || !BookType.PDF.is(dc.getCurrentBook().getPath()) || dc.isTextFormat()
                || dc.isPasswordProtected()) {
            return false;
        }
        return !(dc instanceof HorizontalModeController) || !(AppSP.get().isDouble || AppSP.get().isCut);
    }

    private int toolbarTool() {
        return isShown() ? activeTool : AnnotationTools.NONE;
    }

    private boolean route(MotionEvent ev) {
        final long now = SystemClock.uptimeMillis();
        final int action = ev.getActionMasked();
        final int stylus = stylusIndex(ev);
        if (stylus < 0) {
            // a finger: dropped while it is a palm under a writing hand
            if (action == MotionEvent.ACTION_DOWN) {
                palmGesture = StylusRules.draw(StylusRules.TOOL_TYPE_STYLUS, toolbarTool(), AppState.get().stylusTool, false) != StylusRules.DRAW_NONE
                        && StylusRules.isPalm(stylusDown || stylusHovering, stylusLastSeen, now) && stylusWorks();
            }
            final boolean drop = palmGesture || stylusDown;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                palmGesture = false;
            }
            return drop;
        }

        stylusLastSeen = now;
        final boolean down = action == MotionEvent.ACTION_DOWN
                || (action == MotionEvent.ACTION_POINTER_DOWN && ev.getActionIndex() == stylus);
        final boolean up = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || (action == MotionEvent.ACTION_POINTER_UP && ev.getActionIndex() == stylus);
        if (down) {
            final boolean sideButton = (ev.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
            stylusDraw = stylusWorks()
                    ? StylusRules.draw(ev.getToolType(stylus), toolbarTool(), AppState.get().stylusTool, sideButton)
                    : StylusRules.DRAW_NONE;
            if (stylusDraw == StylusRules.DRAW_PEN || stylusDraw == StylusRules.DRAW_HIGHLIGHTER) {
                if (ev.getPointerCount() > 1) {
                    // a finger already on the page lets go of what it was doing
                    final MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    activity.getWindow().superDispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                stylusDown = true;
                startStroke(ev, stylus);
                return true;
            }
            if (stylusDraw == StylusRules.DRAW_ERASER) {
                startEraser();
            }
            return false;
        }
        if (stylusDraw == StylusRules.DRAW_NONE) {
            return false;
        }
        if (stylusDraw == StylusRules.DRAW_ERASER) {
            // the page sees the touch as a tap and deletes the annotation under it
            if (up) {
                endEraserLater();
                stylusDraw = StylusRules.DRAW_NONE;
            }
            return false;
        }
        moveStroke(ev, stylus);
        if (up) {
            stylusDown = false;
            final int draw = stylusDraw;
            stylusDraw = StylusRules.DRAW_NONE;
            finishStroke(draw, action != MotionEvent.ACTION_CANCEL);
        }
        return true;
    }

    private static int stylusIndex(MotionEvent ev) {
        for (int i = 0; i < ev.getPointerCount(); i++) {
            if (StylusRules.isStylus(ev.getToolType(i))) {
                return i;
            }
        }
        return -1;
    }

    private void startEraser() {
        editWithBeforeEraser = AppState.get().editWith;
        AppState.get().editWith = AppState.EDIT_DELETE;
        // the pen tool's draw view would take the tap
        drawViewVisibilityBefore = drawView.getVisibility();
        drawView.setVisibility(View.GONE);
    }

    /** After the page has taken the tap (a single tap is confirmed only after the double-tap time). */
    private void endEraserLater() {
        final int before = editWithBeforeEraser;
        final int visibility = drawViewVisibilityBefore;
        drawView.postDelayed(() -> {
            if (AppState.get().editWith == AppState.EDIT_DELETE) {
                AppState.get().editWith = before;
            }
            drawView.setVisibility(visibility);
        }, ViewConfiguration.getDoubleTapTimeout() + 150);
    }

    private void startStroke(MotionEvent ev, int stylus) {
        drawViewVisibilityBefore = drawView.getVisibility();
        drawView.setVisibility(View.VISIBLE);
        drawView.getLocationInWindow(drawViewOrigin);
        final boolean marker = stylusDraw == StylusRules.DRAW_HIGHLIGHTER;
        final int color = AnnotationTools.colorInt(marker ? AnnotationTools.HIGHLIGHT : AnnotationTools.PEN);
        // the same width the pen draws with under a finger (DrawView.setColor)
        final float width = marker ? Dips.dpToPx(MARKER_DP) : Dips.dpToPx((int) AppState.get().editLineWidth * 2);
        drawView.startStylus(ev.getX(stylus) - drawViewOrigin[0], ev.getY(stylus) - drawViewOrigin[1],
                ev.getPressure(stylus), color, width, marker);
        toolbar.bringToFront();
    }

    private void moveStroke(MotionEvent ev, int stylus) {
        if (ev.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return;
        }
        // the points the screen sampled between two events too: a quick line stays smooth
        for (int h = 0; h < ev.getHistorySize(); h++) {
            drawView.moveStylus(ev.getHistoricalX(stylus, h) - drawViewOrigin[0], ev.getHistoricalY(stylus, h) - drawViewOrigin[1],
                    ev.getHistoricalPressure(stylus, h));
        }
        drawView.moveStylus(ev.getX(stylus) - drawViewOrigin[0], ev.getY(stylus) - drawViewOrigin[1], ev.getPressure(stylus));
    }

    private void finishStroke(int draw, boolean save) {
        final List<PointF> points = new ArrayList<>();
        for (PointF p : drawView.getPoints()) {
            points.add(new PointF(p.x, p.y));
        }
        final int[] steps = new int[drawView.getSteps().size()];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = drawView.getSteps().get(i);
        }
        drawView.clear();
        drawView.setVisibility(drawViewVisibilityBefore);
        // a tap with the stylus is not a stroke
        if (!save || points.size() < 2 || steps.length != points.size()) {
            return;
        }
        if (draw == StylusRules.DRAW_PEN) {
            savePen(points, steps);
        } else {
            saveHighlighter(points);
        }
    }

    /** One ink annotation per pressure width, each holding the pieces of the stroke drawn at it, page by page. */
    private void savePen(List<PointF> points, int[] steps) {
        final List<DocumentController.PagePoint> onPages = dc.toPageUnits(points);
        if (onPages == null) {
            Toast.makeText(activity, R.string.fail, Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] steady = StylusRules.steady(steps, STEADY_POINTS);
        final List<int[]> runs = StylusRules.runs(steady);
        final int color = AnnotationTools.colorInt(AnnotationTools.PEN);
        // page units, as the pen under a finger (saveChanges)
        final float width = AppState.get().editLineWidth * 3;
        dc.beginAnnotationGroup();
        try {
            for (int step = StylusRules.STEP_THIN; step <= StylusRules.STEP_BOLD; step++) {
                final Map<Integer, List<PointF[]>> arcs = new TreeMap<>();
                for (int[] run : runs) {
                    if (steady[run[0]] != step) {
                        continue;
                    }
                    List<PointF> piece = new ArrayList<>();
                    int piecePage = -1;
                    for (int i = run[0]; i <= run[1]; i++) {
                        final DocumentController.PagePoint p = onPages.get(i);
                        final int page = p == null ? -1 : p.page;
                        if (page != piecePage) {
                            addArc(arcs, piecePage, piece);
                            piece = new ArrayList<>();
                            piecePage = page;
                        }
                        if (p != null) {
                            piece.add(p.point);
                        }
                    }
                    addArc(arcs, piecePage, piece);
                }
                for (Map.Entry<Integer, List<PointF[]>> page : arcs.entrySet()) {
                    dc.addInk(page.getKey(), page.getValue(), color, width * StylusRules.stepWidth(step));
                }
            }
        } finally {
            dc.endAnnotationGroup();
        }
    }

    private static void addArc(Map<Integer, List<PointF[]>> arcs, int page, List<PointF> piece) {
        if (page < 0 || piece.size() < 2) {
            return;
        }
        List<PointF[]> list = arcs.get(page);
        if (list == null) {
            list = new ArrayList<>();
            arcs.put(page, list);
        }
        list.add(piece.toArray(new PointF[0]));
    }

    /**
     * Over text: the text from the stroke's start to its end gets a highlight, as a selection
     * would. Elsewhere (a picture, a scan): a highlight laid along the stroke, which PDF readers
     * draw see-through like a marker.
     */
    private void saveHighlighter(List<PointF> points) {
        final int color = AnnotationTools.colorInt(AnnotationTools.HIGHLIGHT);
        final PointF start = points.get(0);
        final PointF end = points.get(points.size() - 1);
        if (dc.selectTextBetween(start.x, start.y, end.x, end.y)) {
            dc.underlineText(color, 2.0f, AnnotationType.HIGHLIGHT);
            dc.clearSelectedText();
            return;
        }
        dc.clearSelectedText();
        final List<DocumentController.PagePoint> onPages = dc.toPageUnits(points);
        if (onPages == null) {
            Toast.makeText(activity, R.string.fail, Toast.LENGTH_SHORT).show();
            return;
        }
        final Map<Integer, List<RectF>> rects = new TreeMap<>();
        DocumentController.PagePoint from = null;
        for (int i = 0; i < onPages.size(); i++) {
            final DocumentController.PagePoint p = onPages.get(i);
            if (p == null) {
                from = null;
                continue;
            }
            final float half = Dips.dpToPx(MARKER_DP) / 2f * p.unitsPerPx;
            final boolean last = i == onPages.size() - 1;
            if (from == null || from.page != p.page) {
                from = p;
                if (last) {
                    addRect(rects, p.page, new RectF(p.point.x - half, p.point.y - half, p.point.x + half, p.point.y + half));
                }
                continue;
            }
            // one box per step of at least half the marker's width: boxes meet, they do not pile up
            if (!last && Math.hypot(p.point.x - from.point.x, p.point.y - from.point.y) < half) {
                continue;
            }
            addRect(rects, p.page, new RectF(Math.min(from.point.x, p.point.x), Math.min(from.point.y, p.point.y) - half,
                    Math.max(from.point.x, p.point.x), Math.max(from.point.y, p.point.y) + half));
            from = p;
        }
        dc.beginAnnotationGroup();
        try {
            for (Map.Entry<Integer, List<RectF>> page : rects.entrySet()) {
                dc.addHighlightLines(page.getKey(), page.getValue(), color);
            }
        } finally {
            dc.endAnnotationGroup();
        }
    }

    private static void addRect(Map<Integer, List<RectF>> rects, int page, RectF rect) {
        List<RectF> list = rects.get(page);
        if (list == null) {
            list = new ArrayList<>();
            rects.put(page, list);
        }
        list.add(rect);
    }
}
