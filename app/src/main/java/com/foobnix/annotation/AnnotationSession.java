package com.foobnix.annotation;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsSeekBar;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.DrawView;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.search.activity.HorizontalModeController;

import org.ebookdroid.BookType;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Connects the annotation toolbar to an open book. Every mark made here (markup and note tools on
 * selected text, the pen, the stylus) is a separate mark kept outside the book ({@link MarksStore},
 * .agents/features/F-008-separate-marks/design-2026-10-18.md): a PDF only changes when the reader
 * asks ("Save marks into PDF"). The eraser deletes the mark tapped; annotations already inside a
 * PDF are erased as before (AppState.EDIT_DELETE, which both views handle). Undo takes back the last
 * mark made or erased.
 */
public class AnnotationSession implements AnnotationToolbar.Listener {

    private static WeakReference<AnnotationSession> current = new WeakReference<>(null);

    private final Activity activity;
    private final DocumentController dc;
    private final DrawView drawView;
    /** Built when first shown: the book (and whether its text reflows) is known by then. */
    private AnnotationToolbar toolbar;
    /** Short messages of the session, shown above the toolbar: a system toast would cover it. */
    private final TextView note;
    private final Runnable hideNote;
    /** Where the toolbar stands above the bottom of the screen. */
    private static final int TOOLBAR_MARGIN_DP = 80;
    private int activeTool = AnnotationTools.NONE;

    /** A mark made (undo removes it) or erased (undo puts it back). */
    private static final class UndoStep {
        final boolean made;
        final Mark mark;

        UndoStep(boolean made, Mark mark) {
            this.made = made;
            this.mark = mark;
        }
    }

    private final ArrayDeque<UndoStep> undo = new ArrayDeque<>();

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

        final FrameLayout content = activity.findViewById(android.R.id.content);
        note = new TextView(activity);
        note.setTextColor(Color.WHITE);
        note.setTextSize(14);
        final int pad = Dips.dpToPx(10);
        note.setPadding(pad + pad / 2, pad, pad + pad / 2, pad);
        final GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xe0323232);
        bubble.setCornerRadius(Dips.dpToPx(18));
        note.setBackground(bubble);
        note.setVisibility(View.GONE);
        content.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        hideNote = () -> note.animate().alpha(0).setDuration(200).withEndAction(() -> note.setVisibility(View.GONE));
    }

    /** Whether marks can be made in a book: PDF, XPS, DjVu and every book whose text reflows. */
    public static boolean supports(String path) {
        return path != null && (BookType.PDF.is(path) || BookType.DJVU.is(path) || ExtUtils.isTextFomat(path));
    }

    /** A fixed layout: marks are pinned to page positions, and the pen can draw. */
    private boolean fixedLayout() {
        return !dc.isTextFormat();
    }

    private String book() {
        return dc.getCurrentBook() == null ? null : MarksLayer.bookName(dc.getCurrentBook().getPath());
    }

    private AnnotationToolbar toolbar() {
        if (toolbar == null) {
            toolbar = new AnnotationToolbar(activity, fixedLayout());
            toolbar.setListener(this);
            toolbar.setVisibility(View.GONE);
            final FrameLayout content = activity.findViewById(android.R.id.content);
            final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.bottomMargin = Dips.dpToPx(TOOLBAR_MARGIN_DP);
            content.addView(toolbar, params);
        }
        return toolbar;
    }

    /** A short message above the toolbar (or where it would stand), gone after a moment. */
    private void note(CharSequence text) {
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) note.getLayoutParams();
        params.bottomMargin = Dips.dpToPx(TOOLBAR_MARGIN_DP) + (isShown() ? toolbar.getHeight() + Dips.dpToPx(10) : 0);
        note.setLayoutParams(params);
        note.setText(text);
        note.removeCallbacks(hideNote);
        note.animate().cancel();
        note.setAlpha(1);
        note.setVisibility(View.VISIBLE);
        note.bringToFront();
        note.postDelayed(hideNote, 2200);
    }

    private void note(int text) {
        note(activity.getString(text));
    }

    public boolean isShown() {
        return toolbar != null && toolbar.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (isShown()) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        final AnnotationToolbar bar = toolbar();
        bar.setVisibility(View.VISIBLE);
        bar.bringToFront();
        int tool = AppState.get().annotTool;
        if (!bar.has(tool)) {
            // the pen of a PDF in a book whose text reflows
            tool = AnnotationTools.HIGHLIGHT;
        }
        bar.setTool(tool);
        apply(tool);
        hint(tool);
    }

    public void hide() {
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
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
        final UndoStep step = undo.poll();
        if (step == null) {
            // annotations made in the PDF itself this time (none come from the toolbar any more)
            if (!dc.undoLastAnnotation()) {
                note(R.string.annot_nothing_to_undo);
            }
            return;
        }
        if (step.made) {
            MarkEdits.remove(book(), step.mark.id, this::failed);
        } else {
            step.mark.touch();
            MarkEdits.add(book(), step.mark, this::failed);
        }
    }

    @Override
    public void onMore(View anchor) {
        final PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(R.string.marks_export).setOnMenuItemClickListener((MenuItem item) -> {
            MarksExport.start(activity, dc.getCurrentBook().getPath(), dc);
            return true;
        });
        final int count = SaveIntoPdf.countFor(dc);
        if (count > 0) {
            menu.getMenu().add(activity.getString(R.string.marks_save_into_pdf, count)).setOnMenuItemClickListener((MenuItem item) -> {
                SaveIntoPdf.confirm(activity, dc, this::note);
                return true;
            });
        }
        menu.show();
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
            toolbar().bringToFront();
        } else {
            drawView.setVisibility(View.GONE);
        }
    }

    private void hint(int tool) {
        final int text;
        if (AnnotationTools.isText(tool)) {
            text = R.string.annot_hint_markup;
        } else if (tool == AnnotationTools.PEN) {
            text = R.string.annot_hint_pen;
        } else if (tool == AnnotationTools.ERASER) {
            text = R.string.annot_hint_eraser;
        } else {
            return;
        }
        note(text);
    }

    /** A finger stroke of the pen: one mark at the pen's width. */
    private void onStroke() {
        final List<PointF> points = new ArrayList<>();
        for (PointF p : drawView.getPoints()) {
            points.add(new PointF(p.x, p.y));
        }
        drawView.clear();
        if (points.size() < 2) {
            return;
        }
        final List<DocumentController.PagePoint> onPages = dc.toPageUnits(points);
        if (onPages == null) {
            note(R.string.fail);
            return;
        }
        // page units, as the pen drew into the PDF before
        final float width = AppState.get().editLineWidth * 3;
        final Mark mark = MarkMaker.fromStroke(dc, onPages, null, step -> width, AnnotationTools.color(AnnotationTools.PEN));
        if (mark != null) {
            made(mark);
        }
    }

    /** A new mark is kept, and Undo can take it back. */
    private void made(Mark mark) {
        undo.push(new UndoStep(true, mark));
        if (!mark.isInk()) {
            com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.HIGHLIGHT);
        }
        if (mark.hasNote()) {
            com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.NOTE);
        }
        MarkEdits.add(book(), mark, this::failed);
    }

    private void erase(Mark mark) {
        undo.push(new UndoStep(false, mark));
        MarkEdits.remove(book(), mark.id, this::failed);
    }

    private void failed(Exception error) {
        if (error != null) {
            note(activity.getString(R.string.marks_write_failed, String.valueOf(error.getMessage())));
        }
    }

    /** True while the toolbar of {@code dc} is shown: the old edit panel stays closed then. */
    public static boolean isShownFor(DocumentController dc) {
        final AnnotationSession session = current.get();
        return session != null && session.dc == dc && session.isShown();
    }

    /**
     * A text selection is finished: with a markup or the note tool on it is marked at once.
     *
     * @return true when it was, so the selection menu is not shown
     */
    public static boolean markSelection(DocumentController dc) {
        final AnnotationSession session = current.get();
        if (session == null || session.dc != dc || !session.isShown() || !AnnotationTools.isText(session.activeTool)) {
            return false;
        }
        session.markSelected(session.activeTool, AnnotationTools.color(session.activeTool));
        return true;
    }

    /**
     * The selection menu's highlight, underline and strikethrough buttons: a separate mark in that
     * color, as the toolbar makes.
     */
    public static void markSelectionAs(DocumentController dc, int tool, int color) {
        final AnnotationSession session = current.get();
        final String hex = ColorNames.hex(color);
        if (session != null && session.dc == dc) {
            session.markSelected(tool, hex);
            return;
        }
        final Mark mark = MarkMaker.fromSelection(dc, AnnotationTools.kind(tool), hex);
        dc.clearSelectedText();
        if (mark != null && dc.getCurrentBook() != null) {
            com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.HIGHLIGHT);
            MarkEdits.add(MarksLayer.bookName(dc.getCurrentBook().getPath()), mark, null);
        }
    }

    private void markSelected(int tool, String color) {
        final Mark mark = MarkMaker.fromSelection(dc, AnnotationTools.kind(tool), color);
        if (mark == null) {
            dc.clearSelectedText();
            note(R.string.marks_no_text);
            return;
        }
        if (tool != AnnotationTools.NOTE) {
            dc.clearSelectedText();
            made(mark);
            return;
        }
        // the note box shows the words; the mark is made when the note is saved
        MarkCard.askNote(activity, mark.quote, text -> {
            mark.note = text;
            made(mark);
        });
        dc.clearSelectedText();
    }

    /**
     * A tap landed on a separate mark: the eraser deletes it, anything else opens its card.
     *
     * @return true when the tap was used
     */
    public static boolean onMarkTap(MarkTap.Hit hit) {
        final AnnotationSession session = current.get();
        if (hit == null || session == null || session.activity.isFinishing()) {
            return false;
        }
        if (AppState.get().editWith == AppState.EDIT_DELETE) {
            session.erase(hit.mark);
            return true;
        }
        session.showCard(hit);
        return true;
    }

    private void showCard(final MarkTap.Hit hit) {
        final Mark mark = hit.mark;
        MarkCard.show(activity, mark, hit.page, new MarkCard.Listener() {
            @Override
            public void onNote(final String text) {
                if (!mark.hasNote() && !text.isEmpty()) {
                    com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.NOTE);
                }
                MarkEdits.apply(book(), marks -> {
                    final Mark stored = marks.get(mark.id);
                    if (stored != null) {
                        stored.note = text;
                        stored.touch();
                    }
                }, AnnotationSession.this::failed);
            }

            @Override
            public void onColor(final String color) {
                MarkEdits.apply(book(), marks -> {
                    final Mark stored = marks.get(mark.id);
                    if (stored != null) {
                        stored.color = color;
                        stored.touch();
                    }
                }, AnnotationSession.this::failed);
            }

            @Override
            public void onDelete() {
                erase(mark);
            }
        });
    }

    // ---- the stylus: draws at once, fingers keep reading (.agents/planning/stylus-annotation-design.md) ----

    /** How often the text under a moving highlighter is selected again (ms). */
    private static final long SELECT_EVERY_MS = 50;
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
    /** Where the highlighter came down, and when the text under it was last selected. */
    private final PointF highlightStart = new PointF();
    private final PointF highlightEnd = new PointF();
    private long lastSelect;

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

    /** Books marks can be made in, and one page per screen in book mode, as the toolbar. */
    private boolean stylusWorks() {
        if (dc.getCurrentBook() == null || !supports(dc.getCurrentBook().getPath())) {
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
            if (stylusDraw != StylusRules.DRAW_NONE && !onPage(ev.getX(stylus), ev.getY(stylus))) {
                // a button, a bar or a panel over the page: the stylus presses it as a finger does
                stylusDraw = StylusRules.DRAW_NONE;
            }
            if (stylusDraw == StylusRules.DRAW_PEN && !fixedLayout()) {
                // a pen stroke cannot follow text that reflows: the stylus highlights it instead
                stylusDraw = StylusRules.DRAW_HIGHLIGHTER;
            }
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
            // the page sees the touch as a tap and deletes the mark under it
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

    /** The views that show the book: the scroll-mode document, the book-mode pages and their curl. */
    private static final int[] PAGE_IDS = {R.id.documentView, R.id.pager2, R.id.pageCurl};

    /** What a touch meets at a view, for {@link #partAt}. */
    private static final int PART_NONE = 0;
    private static final int PART_PAGE = 1;
    private static final int PART_CONTROL = 2;

    /**
     * Whether a stylus coming down at (x, y) (window pixels) lands on the book rather than on the
     * reader's controls over it. The topmost view under the point that a finger tap would stop at
     * decides, as Android gives touches: the page (or the pen's draw view) draws; a button, a bar, a
     * list, a slider or a floating panel gets the stylus as it gets a finger.
     */
    private boolean onPage(float x, float y) {
        final List<View> pages = new ArrayList<>();
        for (int id : PAGE_IDS) {
            final View page = activity.findViewById(id);
            if (page != null) {
                pages.add(page);
            }
        }
        if (pages.isEmpty()) {
            // a reader screen this does not know: draw as before
            return true;
        }
        pages.add(drawView);
        return partAt(activity.getWindow().getDecorView(), x, y, pages, new int[2]) == PART_PAGE;
    }

    private static int partAt(View view, float x, float y, List<View> pages, int[] origin) {
        if (view.getVisibility() != View.VISIBLE) {
            return PART_NONE;
        }
        view.getLocationInWindow(origin);
        if (x < origin[0] || y < origin[1] || x >= origin[0] + view.getWidth() || y >= origin[1] + view.getHeight()) {
            return PART_NONE;
        }
        if (pages.contains(view)) {
            return PART_PAGE;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final List<View> children = new ArrayList<>();
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                children.add(group.getChildAt(i));
            }
            // topmost first, as touches are given: raised children, then the last drawn
            Collections.sort(children, (a, b) -> Float.compare(b.getZ(), a.getZ()));
            for (View child : children) {
                final int part = partAt(child, x, y, pages, origin);
                if (part != PART_NONE) {
                    return part;
                }
            }
        }
        return takesTouch(view) ? PART_CONTROL : PART_NONE;
    }

    /**
     * A view a finger tap stops at, or a panel raised over the page (the top bar, a floating
     * dialog). Not a background: the blue light filter covers the whole page and takes nothing.
     */
    private static boolean takesTouch(View view) {
        return view.isClickable() || view.isLongClickable() || view.isContextClickable()
                || view instanceof AdapterView || view instanceof AbsSeekBar || view instanceof WebView
                || view instanceof ScrollView || view instanceof HorizontalScrollView
                || view.canScrollVertically(1) || view.canScrollVertically(-1)
                || view.canScrollHorizontally(1) || view.canScrollHorizontally(-1)
                || view.getZ() > 0;
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
        drawView.getLocationInWindow(drawViewOrigin);
        final float x = ev.getX(stylus) - drawViewOrigin[0];
        final float y = ev.getY(stylus) - drawViewOrigin[1];
        if (stylusDraw == StylusRules.DRAW_HIGHLIGHTER) {
            // nothing is drawn: the text from here to the tip is selected as it moves
            highlightStart.set(x, y);
            highlightEnd.set(x, y);
            lastSelect = 0;
            return;
        }
        drawViewVisibilityBefore = drawView.getVisibility();
        drawView.setVisibility(View.VISIBLE);
        // the same width the pen draws with under a finger (DrawView.setColor)
        drawView.startStylus(x, y, ev.getPressure(stylus), AnnotationTools.colorInt(AnnotationTools.PEN),
                Dips.dpToPx((int) AppState.get().editLineWidth * 2));
        if (toolbar != null) {
            toolbar.bringToFront();
        }
    }

    private void moveStroke(MotionEvent ev, int stylus) {
        if (stylusDraw == StylusRules.DRAW_HIGHLIGHTER) {
            highlightEnd.set(ev.getX(stylus) - drawViewOrigin[0], ev.getY(stylus) - drawViewOrigin[1]);
            final long now = SystemClock.uptimeMillis();
            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && now - lastSelect >= SELECT_EVERY_MS) {
                lastSelect = now;
                dc.selectTextBetween(highlightStart.x, highlightStart.y, highlightEnd.x, highlightEnd.y);
            }
            return;
        }
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
        if (draw == StylusRules.DRAW_HIGHLIGHTER) {
            finishHighlight(save);
            return;
        }
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
        savePen(points, steps);
    }

    /** One mark per stroke: runs of points drawn at one pressure width, page by page. */
    private void savePen(List<PointF> points, int[] steps) {
        final List<DocumentController.PagePoint> onPages = dc.toPageUnits(points);
        if (onPages == null) {
            note(R.string.fail);
            return;
        }
        // page units, as the pen under a finger
        final float width = AppState.get().editLineWidth * 3;
        final Mark mark = MarkMaker.fromStroke(dc, onPages, StylusRules.steady(steps, STEADY_POINTS),
                step -> width * StylusRules.stepWidth(step), AnnotationTools.color(AnnotationTools.PEN));
        if (mark != null) {
            made(mark);
        }
    }

    /**
     * The text from where the highlighter came down to where it left gets a highlight, as a
     * selection would. With no text there (a picture, a scan) nothing is marked.
     */
    private void finishHighlight(boolean save) {
        if (!save) {
            dc.clearSelectedText();
            return;
        }
        if (dc.selectTextBetween(highlightStart.x, highlightStart.y, highlightEnd.x, highlightEnd.y)) {
            final Mark mark = MarkMaker.fromSelection(dc, Mark.HIGHLIGHT, AnnotationTools.color(AnnotationTools.HIGHLIGHT));
            if (mark != null) {
                made(mark);
            } else {
                note(R.string.stylus_no_text);
            }
        } else {
            note(R.string.stylus_no_text);
        }
        dc.clearSelectedText();
    }
}
