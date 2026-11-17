package com.foobnix.canvas.library;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.ui2.MainTabs2;
import com.foobnix.ui2.adapter.BookmarksAdapter2;
import com.foobnix.ui2.adapter.EntryAdapter;

/**
 * The library's other tabs in the canvas look: Bookmarks, Statistics, WebDAV, OPDS (and Clouds).
 * Rows are flat on the page with 16 dp gutters and a faint rule, covers cut to 8 dp, titles in
 * ink, details muted, marks in ink2; section heads are JetBrains Mono uppercase labels; grouped
 * numbers stand in cards; a choice of one among a few (the stats' sections and units) is a
 * segmented control; download formats are chips; fields are the canvas's rounded field.
 * <p>
 * Callers ask {@link #isOn(Context)} first and keep their old code otherwise. The theme colour
 * is not used for fills here: the accent token marks what is chosen or what is a link.
 */
public final class LibraryTabs {

    private static final int GUTTER_DP = CanvasLook.GUTTER_DP;
    private static final int COVER_RADIUS_DP = 8;

    private LibraryTabs() {
    }

    /** The canvas look applies: the switch is on and this is the main screen (library). */
    public static boolean isOn(Context c) {
        return c instanceof MainTabs2 && CanvasLook.isOn();
    }

    /** A mark on a tab's top bar: white on the theme colour, ink on the canvas's white bar. */
    public static int barMark(Context c) {
        return isOn(c) ? CanvasLook.ink() : Color.WHITE;
    }

    // ---- Bookmarks ----------------------------------------------------------------------------------

    public static void bookmark(BookmarksAdapter2.BookmarksViewHolder h, boolean withPageNumber) {
        try {
            flatRow(h.parent);
            final View cover = (View) h.image.getParent();
            TintUtil.roundCorners(cover, CanvasLook.dp(COVER_RADIUS_DP));
            if (cover.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) cover.getLayoutParams();
                if (lp.leftMargin != 0 || lp.rightMargin != CanvasLook.dp(4)) {
                    lp.leftMargin = 0;
                    lp.rightMargin = CanvasLook.dp(4);
                    cover.setLayoutParams(lp);
                }
            }
            // the bookmark's own words lead, the book it is in follows, small and muted
            CanvasParts.text(h.text, CanvasLook.TEXT_BODY, 500);
            CanvasParts.secondary(h.title);
            if (withPageNumber) {
                // the page, a small accent tag on the foot of the cover
                h.page.setBackgroundColor(CanvasLook.accent());
                h.page.setTextColor(CanvasLook.onAccent());
                h.page.setTypeface(CanvasLook.mono(h.page.getContext(), 500));
            }
            mark(h.remove, !withPageNumber);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // ---- OPDS ---------------------------------------------------------------------------------------

    public static void entry(EntryAdapter.EntryViewHolder h) {
        try {
            flatRow(h.parent);
            if (h.image != null) {
                TintUtil.roundCorners(h.image, CanvasLook.dp(COVER_RADIUS_DP));
            }
            CanvasParts.text(h.title, CanvasLook.TEXT_BODY, 600);
            if (h.author != null) {
                final boolean link = h.author.hasOnClickListeners();
                CanvasParts.secondary(h.author);
                h.author.setTypeface(CanvasLook.ui(h.author.getContext(), 500));
                if (link) {
                    h.author.setTextColor(CanvasLook.accent());
                }
            }
            if (h.category != null) {
                CanvasParts.label(h.category, false);
            }
            if (h.content != null) {
                h.content.setTypeface(CanvasLook.ui(h.content.getContext(), 400));
                h.content.setTextColor(CanvasLook.ink2());
                h.content.setLinkTextColor(CanvasLook.accent());
            }
            if (h.expand != null) {
                h.expand.setTextColor(CanvasLook.accent());
                h.expand.setTypeface(CanvasLook.mono(h.expand.getContext(), 500));
            }
            formats(h.downloadLinks);
            links(h.links);
            mark(h.remove, false);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The download formats of a book (EPUB, FB2, PDF): chips, the one on the device marked. */
    private static void formats(LinearLayout row) {
        if (row == null) {
            return;
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            if (!(row.getChildAt(i) instanceof TextView)) {
                continue;
            }
            final TextView t = (TextView) row.getChildAt(i);
            final boolean have = t.isActivated();
            final int l = t.getPaddingLeft(), top = t.getPaddingTop(), r = t.getPaddingRight(), b = t.getPaddingBottom();
            t.setBackground(CanvasParts.chip(have));
            t.setPadding(Math.max(l, CanvasLook.dp(12)), top, Math.max(r, CanvasLook.dp(12)), b);
            t.setTextColor(have ? CanvasLook.accent() : CanvasLook.ink2());
            t.setTypeface(CanvasLook.ui(t.getContext(), 600));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
            final Drawable[] d = t.getCompoundDrawables();
            if (d[0] != null) {
                d[0].setColorFilter(t.getCurrentTextColor(), PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    /** The links of an entry (catalogs, pages, search): accent words; a search line is a field. */
    private static void links(LinearLayout links) {
        if (links == null) {
            return;
        }
        for (int i = 0; i < links.getChildCount(); i++) {
            final View v = links.getChildAt(i);
            if (v instanceof TextView) {
                final TextView t = (TextView) v;
                t.setTextColor(CanvasLook.accent());
                t.setTypeface(CanvasLook.ui(t.getContext(), 500));
            } else if (v instanceof LinearLayout) {
                final LinearLayout line = (LinearLayout) v;
                for (int j = 0; j < line.getChildCount(); j++) {
                    final View part = line.getChildAt(j);
                    if (part instanceof EditText) {
                        field((EditText) part);
                    } else if (part instanceof ImageView) {
                        mark((ImageView) part, false);
                    }
                }
            }
        }
    }

    // ---- WebDAV ---------------------------------------------------------------------------------------

    /**
     * The WebDAV tab's own parts: the filter field, the folder row above the list (the way up and
     * the path) and the status line.
     */
    public static void webdav(EditText filter, TextView count, ImageView clear, View pathRow, ImageView back,
                              TextView path, ProgressBar progress, TextView status) {
        try {
            field(filter);
            if (count != null) {
                count.setTextColor(CanvasLook.muted());
                count.setTypeface(CanvasLook.mono(count.getContext(), 500));
            }
            if (clear != null) {
                clear.setImageTintList(ColorStateList.valueOf(CanvasLook.ink2()));
            }
            if (pathRow != null) {
                pathRow.setPadding(CanvasLook.dp(GUTTER_DP - 6), pathRow.getPaddingTop(),
                        CanvasLook.dp(GUTTER_DP), pathRow.getPaddingBottom());
                pathRow.setMinimumHeight(CanvasLook.target());
            }
            mark(back, false);
            if (path != null) {
                path.setTypeface(CanvasLook.ui(path.getContext(), 600));
                path.setTextColor(CanvasLook.ink2());
                path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            }
            if (progress != null && progress.getIndeterminateDrawable() != null) {
                progress.getIndeterminateDrawable().getCurrent().setColorFilter(CanvasLook.accent(), PorterDuff.Mode.SRC_ATOP);
            }
            if (status != null) {
                CanvasParts.secondary(status);
                status.setPadding(CanvasLook.dp(GUTTER_DP), status.getPaddingTop(), CanvasLook.dp(GUTTER_DP), status.getPaddingBottom());
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // ---- Statistics -----------------------------------------------------------------------------------

    /**
     * Turns a row of choices into a segmented control: the soft track, the chosen segment raised on
     * the page colour. The segments share the width; {@code scroller}, when the row stands in one,
     * is stretched to the screen's width so they can.
     */
    public static void segments(LinearLayout row, HorizontalScrollView scroller) {
        final int pad = CanvasLook.dp(3);
        row.setBackground(CanvasParts.segmentTrack());
        row.setPadding(pad, pad, pad, pad);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < row.getChildCount(); i++) {
            final View child = row.getChildAt(i);
            child.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (scroller != null) {
            scroller.setFillViewport(true);
            scroller.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(8));
            scroller.setClipToPadding(false);
        } else {
            lp.topMargin = CanvasLook.dp(12);
            lp.bottomMargin = CanvasLook.dp(8);
        }
        row.setLayoutParams(lp);
    }

    /** One segment: ink and raised when chosen, muted on the track otherwise. */
    public static void segment(TextView t, boolean selected) {
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTypeface(CanvasLook.ui(t.getContext(), selected ? 600 : 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_TITLE);
        t.setTextColor(selected ? CanvasLook.ink() : CanvasLook.muted());
        t.setPadding(CanvasLook.dp(8), CanvasLook.dp(7), CanvasLook.dp(8), CanvasLook.dp(7));
        t.setBackground(selected ? CanvasParts.segmentOn() : null);
        t.setMinHeight(CanvasLook.dp(36));
    }

    /** A number with its name, in a card: the number large in ink, the name a mono label. */
    public static void tile(LinearLayout tile, TextView value, TextView label, LinearLayout.LayoutParams lp) {
        final int pad = CanvasLook.dp(12);
        tile.setBackground(CanvasParts.card());
        tile.setPadding(pad, pad, pad, pad);
        final int gap = CanvasLook.dp(4);
        lp.setMargins(gap, gap, gap, gap);
        value.setTypeface(CanvasLook.ui(value.getContext(), 700));
        value.setTextColor(CanvasLook.ink());
        CanvasParts.label(label, false);
        label.setPadding(0, CanvasLook.dp(2), 0, 0);
    }

    /** A section's head: a muted JetBrains Mono uppercase label with room above it. */
    public static void heading(TextView t) {
        CanvasParts.label(t, false);
        t.setPadding(0, CanvasLook.dp(20), 0, CanvasLook.dp(6));
    }

    /** Body text: ink when it is the point, muted when it explains. */
    public static void text(TextView t, boolean primary) {
        t.setTypeface(CanvasLook.ui(t.getContext(), primary ? 500 : 400));
        t.setTextColor(primary ? CanvasLook.ink() : CanvasLook.muted());
    }

    /** A book in the stats: its title 600 in ink over its numbers, a faint rule under it. */
    public static void statsBook(LinearLayout row, TextView title) {
        CanvasParts.text(title, CanvasLook.TEXT_BODY, 600);
        row.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(8));
        row.setMinimumHeight(CanvasLook.dp(48));
        row.setBackground(new Rule(0));
    }

    // ---- Clouds -----------------------------------------------------------------------------------------

    /** The Clouds tab (not shown by default): the services as cards, the head as a label. */
    public static void clouds(View root, View band) {
        try {
            if (band != null) {
                band.setBackground(null);
                labels(band);
            }
            final View services = root.findViewById(com.foobnix.pdf.info.R.id.cloudsLayout);
            if (services instanceof ViewGroup) {
                final ViewGroup g = (ViewGroup) services;
                for (int i = 0; i < g.getChildCount(); i++) {
                    final View card = g.getChildAt(i);
                    keepPadding(card, CanvasParts.ripple(CanvasParts.card()));
                    if (card instanceof ViewGroup) {
                        final ViewGroup cg = (ViewGroup) card;
                        for (int j = 0; j < cg.getChildCount(); j++) {
                            if (cg.getChildAt(j) instanceof TextView) {
                                CanvasParts.text((TextView) cg.getChildAt(j), CanvasLook.TEXT_SMALL, 600);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void labels(View v) {
        if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                labels(g.getChildAt(i));
            }
        } else if (v instanceof ImageView) {
            mark((ImageView) v, false);
        } else if (v instanceof TextView) {
            CanvasParts.label((TextView) v, false);
        }
    }

    // ---- shared pieces ---------------------------------------------------------------------------------

    /** A search or filter field: the canvas's rounded field, ink words, a muted prompt. */
    public static void field(EditText field) {
        if (field == null) {
            return;
        }
        keepPadding(field, CanvasParts.field());
        field.setTextColor(CanvasLook.ink());
        field.setHintTextColor(CanvasLook.muted());
        field.setTypeface(CanvasLook.ui(field.getContext(), 500));
    }

    /**
     * A list row: flat on the page (the card's fill is the page colour, no shadow, no round, no
     * margin), 16 dp gutters, a faint rule under it and an ink press.
     */
    private static void flatRow(View row) {
        if (row instanceof CardView) {
            final CardView card = (CardView) row;
            card.setCardBackgroundColor(CanvasLook.bg());
            card.setCardElevation(0);
            card.setRadius(0);
        }
        if (row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            if (lp.leftMargin != 0 || lp.topMargin != 0 || lp.rightMargin != 0 || lp.bottomMargin != 0) {
                lp.setMargins(0, 0, 0, 0);
                row.setLayoutParams(lp);
            }
        }
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0) {
            final View content = ((ViewGroup) row).getChildAt(0);
            final int gutter = CanvasLook.dp(GUTTER_DP), air = CanvasLook.dp(8);
            content.setPadding(gutter, air, gutter, air);
            content.setMinimumHeight(CanvasLook.dp(64));
        }
        if (row instanceof FrameLayout && !(((FrameLayout) row).getForeground() instanceof RowFace)) {
            ((FrameLayout) row).setForeground(new RowFace());
        }
    }

    /** An icon on a row: ink2 with a round ink press; {@code ring} keeps a hairline ring round it. */
    private static void mark(ImageView image, boolean ring) {
        if (image == null) {
            return;
        }
        image.setColorFilter(null);
        image.setImageTintList(ColorStateList.valueOf(CanvasLook.ink2()));
        if (ring) {
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.TRANSPARENT);
            circle.setStroke(Math.max(1, CanvasLook.dp(1)), CanvasLook.lineStrong());
            keepPadding(image, new InkRipple(circle, oval()));
        } else if (!(image.getBackground() instanceof InkRipple)) {
            keepPadding(image, new InkRipple(null, oval()));
        }
    }

    private static Drawable oval() {
        final GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFF000000);
        return mask;
    }

    private static void keepPadding(View v, Drawable d) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackground(d);
        v.setPadding(l, t, r, b);
    }

    private static int rippleColor() {
        return (CanvasLook.ink() & 0x00FFFFFF) | 0x1F000000;
    }

    static final class InkRipple extends RippleDrawable {
        InkRipple(Drawable content, Drawable mask) {
            super(ColorStateList.valueOf(rippleColor()), content, mask);
        }
    }

    /** A row's foreground: the faint rule at its foot and the ink press over the whole row. */
    static final class RowFace extends LayerDrawable {
        RowFace() {
            super(new Drawable[]{new Rule(CanvasLook.dp(GUTTER_DP)), new InkRipple(null, rect())});
        }

        private static Drawable rect() {
            final GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFF000000);
            return mask;
        }
    }

    /** A 1 px rule along the foot, held in from both sides by {@code inset}. */
    static final class Rule extends Drawable {
        private final Paint paint = new Paint();
        private final int inset;

        Rule(int inset) {
            this.inset = inset;
            paint.setColor(CanvasLook.surface2());
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            final int px = Math.max(1, CanvasLook.dp(1));
            canvas.drawRect(b.left + inset, b.bottom - px, b.right - inset, b.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
