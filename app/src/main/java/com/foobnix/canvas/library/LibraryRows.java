package com.foobnix.canvas.library;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.ui2.MainTabs2;
import com.foobnix.ui2.adapter.FileMetaAdapter;

/**
 * Book rows and covers of the library (Recent, Library/Folders, Favorites, Search) in the canvas
 * look: flat rows on the page with 16 dp gutters and a faint rule between them, covers cut to
 * 8 dp, the title in ink (15 sp, 600), author and details small and muted, a thin accent progress
 * line on a hairline track, marks (heart, menu, remove) in ink2. Section heads (Starred, Books,
 * Folders) become JetBrains Mono labels instead of bands filled with the theme colour.
 * <p>
 * Called from the end of each bind, after the old styling, only when {@link #isOn(View)}; with
 * the switch off nothing here runs and the rows are as they were.
 */
public final class LibraryRows {

    private static final int GUTTER_DP = CanvasLook.GUTTER_DP;
    private static final int COVER_RADIUS_DP = 8;
    private static final int ROW_MIN_DP = 64;

    private LibraryRows() {
    }

    /** The canvas look applies to rows of the main screen (not to the same lists in dialogs). */
    public static boolean isOn(View row) {
        return row != null && row.getContext() instanceof MainTabs2 && CanvasLook.isOn();
    }

    /** The colour of a mark that stood white on a band of the theme colour. */
    public static int panelMark() {
        return CanvasLook.ink2();
    }

    // ---- a book --------------------------------------------------------------------------------

    public static void book(FileMetaAdapter.FileMetaViewHolder h, boolean grid, boolean coversOnly, double progress) {
        try {
            if (grid) {
                if (h.parent instanceof CardView) {
                    final CardView card = (CardView) h.parent;
                    card.setCardBackgroundColor(coversOnly ? Color.TRANSPARENT : CanvasLook.bg());
                    card.setCardElevation(0);
                    card.setRadius(CanvasLook.dp(COVER_RADIUS_DP));
                }
            } else {
                flatRow(h.parent);
            }
            cover(h.imageParent, !grid);

            CanvasParts.text(h.title, grid ? CanvasLook.TEXT_TITLE : CanvasLook.TEXT_BODY, 600);
            meta(h.author, 500);
            meta(h.series, 400);
            meta(h.path, 400);
            meta(h.size, 400);
            meta(h.date, 400);
            meta(h.idPercentText, 500);
            if (h.browserExt != null) {
                CanvasParts.label(h.browserExt, false);
            }
            if (h.tags != null) {
                for (int i = 0; i < h.tags.getChildCount(); i++) {
                    final View tag = h.tags.getChildAt(i);
                    if (tag instanceof TextView) {
                        final TextView t = (TextView) tag;
                        t.setTypeface(CanvasLook.ui(t.getContext(), 500));
                        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
                        t.setTextColor(CanvasLook.accent());
                    }
                }
            }
            progress(h.idProgressBg, h.progresLayout, progress);

            mark(h.star);
            mark(h.menu);
            if (h.remove instanceof ImageView) {
                mark((ImageView) h.remove);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void meta(TextView v, int weight) {
        if (v == null) {
            return;
        }
        CanvasParts.secondary(v);
        if (weight != 400) {
            v.setTypeface(CanvasLook.ui(v.getContext(), weight));
        }
    }

    /** The cover, cut to 8 dp; in a list held off the text by 12 dp. */
    private static void cover(View cover, boolean list) {
        if (cover == null) {
            return;
        }
        if (cover instanceof CardView) {
            ((CardView) cover).setRadius(CanvasLook.dp(COVER_RADIUS_DP));
        } else {
            TintUtil.roundCorners(cover, CanvasLook.dp(COVER_RADIUS_DP));
        }
        if (list && cover.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) cover.getLayoutParams();
            if (lp.rightMargin != CanvasLook.dp(12)) {
                lp.rightMargin = CanvasLook.dp(12);
                cover.setLayoutParams(lp);
            }
        }
    }

    /** A thin (3 dp) accent line on a hairline-coloured track, as wide as the text column. */
    private static void progress(View line, View holder, double progress) {
        if (line == null || progress <= 0) {
            return;
        }
        final int thick = CanvasLook.dp(3);
        final ViewGroup.LayoutParams lp = line.getLayoutParams();
        if (lp != null && lp.height != thick) {
            lp.height = thick;
            line.setLayoutParams(lp);
        }
        TintUtil.setProgressLine(line, CanvasLook.line(), CanvasLook.accent(), progress);
        if (holder != null && holder.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams hl = (ViewGroup.MarginLayoutParams) holder.getLayoutParams();
            final int left = CanvasLook.dp(3), top = CanvasLook.dp(6);
            if (hl.leftMargin != left || hl.rightMargin != 0 || hl.topMargin != top) {
                hl.leftMargin = left;
                hl.rightMargin = 0;
                hl.topMargin = top;
                holder.setLayoutParams(hl);
            }
        }
    }

    // ---- a folder, a playlist, a tag ------------------------------------------------------------

    public static void folder(FileMetaAdapter.DirectoryViewHolder h, boolean grid) {
        try {
            plainRow(h.parent, grid);
            CanvasParts.text(h.title, CanvasLook.TEXT_BODY, 600);
            meta(h.path, 400);
            if (h.image != null && h.image.getColorFilter() != null) {
                // the folder or playlist glyph (a folder's own cover is left as it is)
                h.image.setColorFilter(CanvasLook.ink2(), PorterDuff.Mode.SRC_ATOP);
            }
            mark(h.starIcon);
            badge(h.count);
            if (h.play != null) {
                TintUtil.setRingColor(h.play, CanvasLook.accent());
                h.play.setTextColor(CanvasLook.accent());
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public static void tag(FileMetaAdapter.TagViewHolder h, boolean grid) {
        try {
            plainRow(h.parent, grid);
            CanvasParts.text(h.title, CanvasLook.TEXT_BODY, 600);
            if (h.image != null) {
                h.image.setColorFilter(CanvasLook.ink2(), PorterDuff.Mode.SRC_ATOP);
            }
            badge(h.count);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void plainRow(View row, boolean grid) {
        if (!grid) {
            flatRow(row);
        } else if (row instanceof CardView) {
            // in a grid a folder is a tile of its own: the soft surface, no shadow
            final CardView card = (CardView) row;
            card.setCardBackgroundColor(CanvasLook.surface2());
            card.setCardElevation(0);
            card.setRadius(CanvasLook.radius());
        }
    }

    /** A count: accent text on the accent's tint, a small pill. */
    private static void badge(TextView count) {
        if (count == null) {
            return;
        }
        count.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(10)));
        count.setTextColor(CanvasLook.accent());
        count.setTypeface(CanvasLook.ui(count.getContext(), 600));
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_SMALL);
    }

    // ---- section heads ----------------------------------------------------------------------------

    /**
     * A band that headed a section (Starred, Recent, Books, Folders, a divider): no fill, its
     * name as a muted JetBrains Mono label, a link in it (Clear all, a picker) in the accent.
     */
    public static void section(View band, View... links) {
        if (band == null) {
            return;
        }
        try {
            final int l = band.getPaddingLeft(), t = band.getPaddingTop(), r = band.getPaddingRight(), b = band.getPaddingBottom();
            band.setBackground(null);
            band.setPadding(Math.max(l, CanvasLook.dp(GUTTER_DP - 4)), t, Math.max(r, CanvasLook.dp(GUTTER_DP - 4)), b);
            if (band.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) band.getLayoutParams();
                lp.topMargin = Math.max(lp.topMargin, CanvasLook.dp(8));
                band.setLayoutParams(lp);
            }
            heads(band, links);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void heads(View v, View[] links) {
        if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                heads(g.getChildAt(i), links);
            }
        } else if (v instanceof ImageView) {
            final ImageView image = (ImageView) v;
            image.setColorFilter(null);
            image.setImageTintList(ColorStateList.valueOf(panelMark()));
            if (image.getBackground() != null) {
                keepPadding(image, inkRipple(true));
            }
        } else if (v instanceof TextView) {
            final TextView text = (TextView) v;
            boolean link = text.isClickable();
            for (View l : links) {
                link |= l == v;
            }
            CanvasParts.label(text, link);
        }
    }

    /** The divider line in a list ("Discarded books"): a label with room above it. */
    public static void divider(TextView title, ImageView image) {
        if (title == null) {
            return;
        }
        CanvasParts.label(title, false);
        final View row = (View) title.getParent();
        if (row != null) {
            row.setPadding(CanvasLook.dp(GUTTER_DP), CanvasLook.dp(14), CanvasLook.dp(GUTTER_DP), CanvasLook.dp(4));
        }
        if (image != null) {
            image.setColorFilter(CanvasLook.muted(), PorterDuff.Mode.SRC_ATOP);
        }
    }

    // ---- the search field -------------------------------------------------------------------------

    /** The library's search field: the canvas's rounded field, ink words, muted prompt and count. */
    public static void searchField(EditText field, TextView count, ImageView... icons) {
        if (field == null) {
            return;
        }
        keepPadding(field, CanvasParts.field());
        field.setTextColor(CanvasLook.ink());
        field.setHintTextColor(CanvasLook.muted());
        field.setTypeface(CanvasLook.ui(field.getContext(), 500));
        if (count != null) {
            count.setTextColor(CanvasLook.muted());
            count.setTypeface(CanvasLook.mono(count.getContext(), 500));
        }
        for (ImageView icon : icons) {
            if (icon != null) {
                icon.setColorFilter(null);
                icon.setImageTintList(ColorStateList.valueOf(CanvasLook.ink2()));
            }
        }
    }

    // ---- shared pieces ------------------------------------------------------------------------------

    /**
     * A list row: flat on the page (the card's fill is the page colour, no shadow, no round, no
     * margin), 16 dp gutters, at least 64 dp tall, a faint rule under it and an ink press.
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
            content.setMinimumHeight(CanvasLook.dp(ROW_MIN_DP));
        }
        if (row instanceof FrameLayout && !(((FrameLayout) row).getForeground() instanceof RowFace)) {
            ((FrameLayout) row).setForeground(new RowFace());
        }
    }

    /** An icon on a row (heart, menu, remove): ink2, a round ink press. */
    private static void mark(ImageView image) {
        if (image == null) {
            return;
        }
        image.setColorFilter(CanvasLook.ink2(), PorterDuff.Mode.SRC_ATOP);
        image.setImageAlpha(255);
        if (!(image.getBackground() instanceof InkRipple)) {
            keepPadding(image, inkRipple(true));
        }
    }

    private static void keepPadding(View v, Drawable d) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackground(d);
        v.setPadding(l, t, r, b);
    }

    private static int rippleColor() {
        return (CanvasLook.ink() & 0x00FFFFFF) | 0x1F000000;
    }

    private static InkRipple inkRipple(boolean oval) {
        final GradientDrawable mask = new GradientDrawable();
        if (oval) {
            mask.setShape(GradientDrawable.OVAL);
        }
        mask.setColor(0xFF000000);
        return new InkRipple(mask);
    }

    static final class InkRipple extends RippleDrawable {
        InkRipple(Drawable mask) {
            super(ColorStateList.valueOf(rippleColor()), null, mask);
        }
    }

    /** A row's foreground: the ink press over the whole row, and the faint rule at its foot. */
    static final class RowFace extends LayerDrawable {
        RowFace() {
            super(new Drawable[]{new Rule(), inkRipple(false)});
        }
    }

    /** A 1 px rule along the foot, held in from both sides by the gutter. */
    static final class Rule extends Drawable {
        private final Paint paint = new Paint();

        Rule() {
            paint.setColor(CanvasLook.surface2());
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect b = getBounds();
            final int gutter = CanvasLook.dp(GUTTER_DP);
            final int px = Math.max(1, CanvasLook.dp(1));
            canvas.drawRect(b.left + gutter, b.bottom - px, b.right - gutter, b.bottom, paint);
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
