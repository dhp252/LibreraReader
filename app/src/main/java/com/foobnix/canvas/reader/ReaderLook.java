package com.foobnix.canvas.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.info.wrapper.ReaderChrome;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The reader's bars in the canvas look, past the sheet and ink marks {@link ReaderChrome} gives
 * them: the book's name with the chapter under it, centred, at the head of the menu; 44 dp icon
 * buttons; the bottom strip on the surface colour with a hairline, its page words in JetBrains
 * Mono and a "38%" beside the seek bar. The always-visible status line takes JetBrains Mono.
 * <p>
 * Both readers call it: scroll mode ({@code DocumentWrapperUI}) and book mode
 * ({@code HorizontalViewActivity}); it finds the views by id and passes over the ones a reader
 * lacks. The bars only while {@link ReaderChrome#isOn()}, the status line's font only while
 * {@link CanvasLook#isOn()}; otherwise nothing is touched. Colours are only set while the marks
 * are ink ({@link ReaderChrome#inkMarks()}): a reader who picked an interface text colour keeps it.
 */
public final class ReaderLook {

    /** The glyph inside a 44 dp button, as the canvas draws its icons. */
    private static final int GLYPH_DP = 26;
    /** The room a stretching label (the mode name) keeps when the icons beside it grow. */
    private static final int FLEX_MIN_DP = 72;
    private static final Object TAG_HEADER = "canvas_t08_header";
    private static final Object TAG_PERCENT = "canvas_t08_percent";

    /** Rows whose buttons are already sized on every layout. */
    private static final Map<View, Boolean> FITTED = new WeakHashMap<>();

    // the status line: scroll mode's title bar, book mode's bottom indicators
    private static final int[] STATUS_LINE = {
            R.id.bookName, R.id.currentPageIndex, R.id.currentTime, R.id.currentBattery, R.id.reverseKeysIndicator,
            R.id.pagesTime, R.id.pagesTime1, R.id.pagesCountIndicator, R.id.flippingIntervalView, R.id.pagesPower,
            R.id.pannelBookTitle};

    private ReaderLook() {
    }

    /** After the reader painted and tinted its bars (ReaderChrome), once per set-up. */
    public static void apply(Activity a) {
        if (a == null) {
            return;
        }
        try {
            if (CanvasLook.isOn()) {
                statusLine(a);
            }
            if (!ReaderChrome.isOn()) {
                return;
            }
            topBar(a);
            footer(a);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * On each page: the chapter under the name and the percent in the strip.
     *
     * @param title the book's name for scroll mode's head; null in book mode, whose bar has its
     *              own name and chapter
     */
    public static void onPage(Activity a, String title, DocumentController dc) {
        if (a == null || dc == null || !ReaderChrome.isOn()) {
            return;
        }
        try {
            if (title != null) {
                header(a, title, ReaderText.chapterLine(dc.getCurrentChapter()));
            }
            final TextView percent = percentView(a);
            if (percent != null) {
                percent.setText(ReaderText.percent(dc.getCurentPageFirst1(), dc.getPageCount()));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // colours of the bars: they follow the page under them, as ReaderChrome's do

    static int ink2() {
        return ReaderChrome.isNight() ? CanvasLook.INK_2_N : CanvasLook.INK_2;
    }

    static int muted() {
        return ReaderChrome.isNight() ? CanvasLook.MUTED_N : CanvasLook.MUTED;
    }

    static int surface() {
        if (!ReaderChrome.isNight()) {
            return CanvasLook.SURFACE;
        }
        return AppState.get().appTheme == AppState.THEME_DARK_OLED ? Color.BLACK : CanvasLook.SURFACE_N;
    }

    // the status line

    private static void statusLine(Activity a) {
        for (int id : STATUS_LINE) {
            final View v = a.findViewById(id);
            if (v instanceof TextView) {
                // the font only: size and colour stay the reader's status-bar settings
                ((TextView) v).setTypeface(CanvasLook.mono(a, 400));
            }
        }
    }

    // the top bar

    private static void topBar(Activity a) {
        final boolean ink = ReaderChrome.inkMarks();

        // book mode: the name and chapter already stand in the bar
        title(a.findViewById(R.id.title), ink);
        chapter(a.findViewById(R.id.chapter), ink);

        // scroll mode: the menu gets a head of its own
        final View menu = a.findViewById(R.id.menuLayout);
        if (menu instanceof LinearLayout) {
            ensureHeader((LinearLayout) menu);
        }

        // the corner buttons: library and close
        corner(a.findViewById(R.id.onLibrary));
        corner(a.findViewById(R.id.close));
        corner(a.findViewById(R.id.bookClose));

        // the row of tools (both readers have moveCenter in it)
        final View move = a.findViewById(R.id.moveCenter);
        if (move != null && move.getParent() instanceof ViewGroup) {
            final ViewGroup row = (ViewGroup) move.getParent();
            if (row.getParent() instanceof RelativeLayout && row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                // scroll mode: the row is held clear of the corner buttons, now 44 dp
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
                lp.leftMargin = lp.rightMargin = CanvasLook.dp(CanvasLook.TARGET_DP + 4);
                row.setLayoutParams(lp);
                if (menu != null && menu.findViewWithTag(TAG_HEADER) != null) {
                    row.setPadding(row.getPaddingLeft(), CanvasLook.dp(4), row.getPaddingRight(), row.getPaddingBottom());
                }
            }
            fitButtons(row);
        }
    }

    private static void title(View v, boolean ink) {
        if (!(v instanceof TextView)) {
            return;
        }
        final TextView t = (TextView) v;
        t.setTypeface(CanvasLook.ui(t.getContext(), 600));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_TITLE);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        if (ink) {
            t.setTextColor(ReaderChrome.ink());
        }
    }

    private static void chapter(View v, boolean ink) {
        if (!(v instanceof TextView)) {
            return;
        }
        final TextView t = (TextView) v;
        t.setTypeface(CanvasLook.ui(t.getContext(), 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(t.getPaddingLeft(), CanvasLook.dp(2), t.getPaddingRight(), t.getPaddingBottom());
        if (ink) {
            t.setTextColor(muted());
        }
    }

    /** Scroll mode's menu head: the name over the chapter, centred, above the tools. */
    private static LinearLayout ensureHeader(LinearLayout menu) {
        final View old = menu.findViewWithTag(TAG_HEADER);
        if (old instanceof LinearLayout) {
            final LinearLayout head = (LinearLayout) old;
            final boolean ink = ReaderChrome.inkMarks();
            title(head.getChildAt(0), ink);
            chapter(head.getChildAt(1), ink);
            return head;
        }
        final Context c = menu.getContext();
        final LinearLayout head = new LinearLayout(c);
        head.setTag(TAG_HEADER);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        // the corner buttons stand below it, so it keeps clear of them only by the gutter
        final int side = CanvasLook.dp(CanvasLook.GUTTER_DP + CanvasLook.TARGET_DP / 2);
        head.setPadding(side, CanvasLook.dp(8), side, 0);
        head.setClickable(true);

        final TextView name = new TextView(c);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        final TextView chapter = new TextView(c);
        chapter.setSingleLine(true);
        chapter.setEllipsize(TextUtils.TruncateAt.END);
        chapter.setVisibility(View.GONE);

        final boolean ink = ReaderChrome.inkMarks();
        final int markColor = ink ? ReaderChrome.ink() : AppState.get().uiTextColor;
        name.setTextColor(markColor);
        chapter.setTextColor(ink ? muted() : markColor);
        title(name, ink);
        chapter(chapter, ink);

        head.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(chapter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        menu.addView(head, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return head;
    }

    private static void header(Activity a, String title, String chapter) {
        final View menu = a.findViewById(R.id.menuLayout);
        if (!(menu instanceof LinearLayout)) {
            return;
        }
        final LinearLayout head = ensureHeader((LinearLayout) menu);
        final TextView name = (TextView) head.getChildAt(0);
        final TextView ch = (TextView) head.getChildAt(1);
        final String t = title == null ? "" : title.trim();
        if (!t.contentEquals(name.getText())) {
            name.setText(t);
        }
        name.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        if (!chapter.contentEquals(ch.getText())) {
            ch.setText(chapter);
        }
        ch.setVisibility(chapter.isEmpty() || chapter.equals(t) ? View.GONE : View.VISIBLE);
    }

    private static void corner(View v) {
        if (!(v instanceof ImageView) || AppState.get().isEnableAccessibility) {
            return;
        }
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) {
            return;
        }
        final int size = CanvasLook.target();
        lp.width = lp.height = size;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            // the 50 dp the name keeps either side still clears the larger button
            m.leftMargin = Math.min(m.leftMargin, CanvasLook.dp(4));
            m.rightMargin = Math.min(m.rightMargin, CanvasLook.dp(4));
        }
        v.setLayoutParams(lp);
        final int pad = (size - CanvasLook.dp(22)) / 2; // the ringed corner icons are a little smaller
        v.setPadding(pad, pad, pad, pad);
    }

    // the footer

    private static void footer(Activity a) {
        final boolean ink = ReaderChrome.inkMarks();

        View bar = a.findViewById(R.id.bottomBar1); // scroll mode wraps the footer once more
        if (bar == null) {
            bar = a.findViewById(R.id.bottomBar);
        }
        paintStrip(bar, AppState.get().transparencyUI);

        for (int id : new int[]{R.id.currentSeek, R.id.maxSeek}) {
            final View v = a.findViewById(id);
            if (v instanceof TextView) {
                mono((TextView) v, ink);
            }
        }
        final View mode = a.findViewById(R.id.modeName);
        if (mode instanceof TextView) {
            mono((TextView) mode, ink);
            if (mode.getParent() instanceof ViewGroup) {
                final ViewGroup strip = (ViewGroup) mode.getParent();
                strip.setMinimumHeight(CanvasLook.dp(CanvasLook.STRIP_DP));
                fitButtons(strip);
            }
        }
        final View history = a.findViewById(R.id.linkHistory);
        if (history != null && history.getParent() instanceof ViewGroup) {
            fitButtons((ViewGroup) history.getParent());
        }
        final View name = a.findViewById(R.id.playListName);
        if (name instanceof TextView) {
            ((TextView) name).setTypeface(CanvasLook.ui(a, 600));
        }
        ensurePercent(a);
    }

    private static void mono(TextView t, boolean ink) {
        t.setTypeface(CanvasLook.mono(t.getContext(), 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        t.setLetterSpacing(0.03f);
        if (ink) {
            t.setTextColor(ink2());
        }
    }

    /** The strip: the surface colour, a hairline on the edge that meets the page. */
    private static void paintStrip(View bar, int alpha) {
        if (bar == null) {
            return;
        }
        final ColorDrawable line = new ColorDrawable(ReaderChrome.line());
        final ColorDrawable fill = new ColorDrawable(surface());
        fill.setAlpha(alpha);
        final LayerDrawable layers = new LayerDrawable(new Drawable[]{line, fill});
        layers.setLayerInset(1, 0, Math.max(1, CanvasLook.dp(1)), 0, 0);
        final int l = bar.getPaddingLeft(), t = bar.getPaddingTop(), r = bar.getPaddingRight(), b = bar.getPaddingBottom();
        bar.setBackground(layers);
        bar.setPadding(l, t, r, b);
    }

    /** "38%" at the end of the seek line, in the canvas's 12 sp. */
    private static TextView ensurePercent(Activity a) {
        final View line = a.findViewById(R.id.seekLine);
        if (!(line instanceof LinearLayout)) {
            return null;
        }
        final View old = line.findViewWithTag(TAG_PERCENT);
        if (old instanceof TextView) {
            return (TextView) old;
        }
        final TextView p = new TextView(a);
        p.setTag(TAG_PERCENT);
        p.setSingleLine(true);
        p.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        p.setTypeface(CanvasLook.ui(a, 500));
        p.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
        // the colour picked for the interface text when there is one (inkMarks is off only then)
        p.setTextColor(ReaderChrome.inkMarks() ? ink2() : AppState.get().uiTextColor);
        p.setMinWidth(CanvasLook.dp(40));
        p.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(8), 0);
        p.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = 0;
        ((LinearLayout) line).addView(p, lp);
        return p;
    }

    private static TextView percentView(Activity a) {
        final View line = a.findViewById(R.id.seekLine);
        if (line == null) {
            return null;
        }
        final View p = line.findViewWithTag(TAG_PERCENT);
        return p instanceof TextView ? (TextView) p : null;
    }

    // 44 dp buttons, as many as the row has room for

    /**
     * Grows the icon buttons of a row toward the canvas's 44 dp, as far as the row's width lets
     * them: a phone in portrait cannot hold every tool at 44 dp, and none may be pushed off.
     * Measured again on each layout, since tools come and go with the book and the rotation.
     */
    private static void fitButtons(final ViewGroup row) {
        if (row == null || AppState.get().isEnableAccessibility) {
            return;
        }
        fit(row);
        if (FITTED.containsKey(row)) {
            return;
        }
        FITTED.put(row, Boolean.TRUE);
        // after every layout: a tool shown or hidden changes the room without changing a width;
        // fit() only asks for another layout when a size really changes, so this settles
        row.getViewTreeObserver().addOnGlobalLayoutListener(() -> fit(row));
    }

    private static void fit(ViewGroup row) {
        if (!ReaderChrome.isOn()) {
            return;
        }
        final View parent = row.getParent() instanceof View ? (View) row.getParent() : null;
        if (parent == null || parent.getWidth() <= 0) {
            return;
        }
        int room = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight()
                - row.getPaddingLeft() - row.getPaddingRight();
        if (row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            room -= m.leftMargin + m.rightMargin;
        }
        int taken = 0;
        int icons = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            final View child = row.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                taken += ((ViewGroup.MarginLayoutParams) lp).leftMargin + ((ViewGroup.MarginLayoutParams) lp).rightMargin;
            }
            if (isIcon(child)) {
                icons++;
            } else if (lp instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) lp).weight > 0) {
                taken += Math.max(child.getMinimumWidth(), CanvasLook.dp(FLEX_MIN_DP));
            } else {
                taken += child.getWidth();
            }
        }
        if (icons == 0) {
            return;
        }
        final int base = row.getResources().getDimensionPixelSize(R.dimen.wh_button);
        final int size = Math.max(base, Math.min(CanvasLook.target(), (room - taken) / icons));
        final int pad = Math.max(CanvasLook.dp(3), (size - CanvasLook.dp(GLYPH_DP)) / 2);
        boolean changed = false;
        for (int i = 0; i < row.getChildCount(); i++) {
            final View child = row.getChildAt(i);
            if (!isIcon(child)) {
                continue;
            }
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp.width != size || lp.height != size) {
                lp.width = lp.height = size;
                child.setLayoutParams(lp);
                changed = true;
            }
            if (child.getPaddingLeft() != pad || child.getPaddingTop() != pad) {
                child.setPadding(pad, pad, pad, pad);
            }
        }
        if (changed) {
            row.requestLayout();
        }
    }

    /** A button of fixed size in a row of tools (not one sized by its picture, like the mode). */
    private static boolean isIcon(View v) {
        if (!(v instanceof ImageView)) {
            return false;
        }
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        return lp != null && lp.width > 0 && lp.width == lp.height;
    }
}
