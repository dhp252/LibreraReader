package com.foobnix.canvas.reader;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * The reader's floating panels ({@code DragingPopup}: contents, bookmarks, search, settings...)
 * in the canvas look: a sheet cut to 14 dp on the page colour, a head with the panel's name in
 * ink over a hairline (not a bar filled with the theme colour), ink close button, 16 dp gutters;
 * the rows of the contents and the bookmarks list on hairlines, the current chapter in the
 * accent; the search field rounded.
 * <p>
 * Only the chrome and these rows: what a panel holds is its own. Everything here returns at once
 * unless {@link CanvasLook#isOn()}, so with the look off the panels are as they were.
 */
public final class PopupLook {

    private static final int RADIUS_DP = CanvasLook.RADIUS_LARGE_DP;

    private PopupLook() {
    }

    public static boolean isOn() {
        return CanvasLook.isOn();
    }

    /** The head's background, by which a later tint walk knows the head is light. */
    static final class Head extends LayerDrawable {
        Head(int fill, int line) {
            super(new Drawable[]{new ColorDrawable(line), new ColorDrawable(fill)});
            setLayerInset(1, 0, 0, 0, Math.max(1, CanvasLook.dp(1)));
        }
    }

    /**
     * The colour of a mark on a panel's head (close, the head's menu): ink on the canvas head,
     * else {@code old} (the white the theme-coloured head took).
     */
    public static int headMark(View mark, int old) {
        if (isOn() && mark != null && mark.getParent() instanceof View
                && ((View) mark.getParent()).getBackground() instanceof Head) {
            return CanvasLook.ink();
        }
        return old;
    }

    /** After the panel is filled and tinted (end of {@code DragingPopup.show}). */
    public static void chrome(View popupView) {
        if (!isOn() || popupView == null) {
            return;
        }
        try {
            if (popupView instanceof CardView) {
                final CardView card = (CardView) popupView;
                card.setRadius(CanvasLook.dp(RADIUS_DP));
                card.setCardBackgroundColor(CanvasLook.bg());
            }

            final View head = popupView.findViewById(R.id.topLayout);
            if (head != null) {
                head.setBackground(new Head(CanvasLook.bg(), CanvasLook.line()));
                head.setPadding(CanvasLook.dp(CanvasLook.GUTTER_DP), CanvasLook.dp(4), CanvasLook.dp(4), CanvasLook.dp(5));
                head.setMinimumHeight(CanvasLook.dp(CanvasLook.STRIP_DP));
            }

            final TextView back = popupView.findViewById(R.id.onTitleAction);
            final boolean trail = back != null && back.getVisibility() == View.VISIBLE;
            if (back != null) {
                back.setAllCaps(false);
                back.setTypeface(CanvasLook.ui(back.getContext(), 500));
                back.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
                back.setTextColor(CanvasLook.accent());
                back.setPadding(0, back.getPaddingTop(), 0, back.getPaddingBottom());
                back.setMinEms(0);
            }
            final TextView sep = popupView.findViewById(R.id.onTitleAction1);
            if (sep != null) {
                sep.setTypeface(CanvasLook.ui(sep.getContext(), 500));
                sep.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
                sep.setTextColor(CanvasLook.muted());
                sep.setPadding(CanvasLook.dp(6), 0, 0, 0);
            }
            final TextView title = popupView.findViewById(R.id.dialogTitle);
            if (title != null) {
                title.setAllCaps(false);
                title.setTypeface(CanvasLook.ui(title.getContext(), 600));
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
                title.setTextColor(CanvasLook.ink());
                title.setPadding(trail ? CanvasLook.dp(6) : 0, 0, CanvasLook.dp(4), 0);
            }

            headButton(popupView.findViewById(R.id.onIconAction));
            headButton(popupView.findViewById(R.id.closePopup));

            // the corners that resize the panel: quiet marks, not the theme colour
            for (int id : new int[]{R.id.left, R.id.rigth}) {
                final View corner = popupView.findViewById(id);
                if (corner != null && corner.getBackground() != null) {
                    corner.getBackground().mutate().setColorFilter(CanvasLook.lineStrong(), PorterDuff.Mode.SRC_ATOP);
                }
            }

            final View content = popupView.findViewById(R.id.popupContent);
            if (content instanceof ViewGroup) {
                gutters((ViewGroup) content);
                searchField(content.findViewById(R.id.edit1), content.findViewById(R.id.imageClear));
                searchField(content.findViewById(R.id.searchBookmark), content.findViewById(R.id.cleanBookmarkSearch));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void headButton(View v) {
        if (!(v instanceof ImageView)) {
            return;
        }
        final ImageView b = (ImageView) v;
        final ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp != null) {
            lp.width = lp.height = CanvasLook.target();
            b.setLayoutParams(lp);
        }
        final int pad = (CanvasLook.target() - CanvasLook.dp(22)) / 2;
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(CanvasParts.ripple(new ColorDrawable(Color.TRANSPARENT)));
        b.setImageTintList(ColorStateList.valueOf(CanvasLook.ink()));
    }

    /** 16 dp either side, counting the room the panel's own content already keeps. */
    private static void gutters(ViewGroup frame) {
        int own = 0;
        if (frame.getChildCount() > 0) {
            final View child = frame.getChildAt(0);
            own = Math.min(child.getPaddingLeft(), child.getPaddingRight());
        }
        final int pad = Math.max(0, CanvasLook.dp(CanvasLook.GUTTER_DP) - own);
        frame.setPadding(pad, frame.getPaddingTop(), pad, frame.getPaddingBottom());
    }

    /** The search fields of the search, bookmarks and go-to-page panels: a rounded well, no underline. */
    private static void searchField(View v, View clear) {
        if (!(v instanceof EditText)) {
            return;
        }
        final EditText edit = (EditText) v;
        edit.setBackground(CanvasParts.field());
        edit.setTypeface(CanvasLook.ui(edit.getContext(), 400));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        edit.setTextColor(CanvasLook.ink());
        edit.setHintTextColor(CanvasLook.muted());
        edit.setMinHeight(CanvasLook.target());
        final int side = CanvasLook.dp(12);
        // the clear button stands inside the field's right end
        final int right = clear != null ? side + CanvasLook.dp(28) : side;
        edit.setPadding(side, CanvasLook.dp(8), right, CanvasLook.dp(8));
        if (edit.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) edit.getLayoutParams();
            lp.topMargin = lp.bottomMargin = CanvasLook.dp(8);
            edit.setLayoutParams(lp);
        }
    }

    // rows

    /** A row on a hairline, its fill (or none) and an ink ripple. */
    private static Drawable row(int fill) {
        final LayerDrawable d = new LayerDrawable(new Drawable[]{
                new ColorDrawable(CanvasLook.line()),
                new ColorDrawable(fill),
                CanvasParts.ripple(new ColorDrawable(Color.TRANSPARENT))});
        final int px = Math.max(1, CanvasLook.dp(1));
        d.setLayerInset(1, 0, 0, 0, px);
        d.setLayerInset(2, 0, 0, 0, px);
        return d;
    }

    /** The list carries the rows' own hairlines, so it draws no dividers of its own. */
    private static void noDividers(ViewGroup parent) {
        if (parent instanceof ListView && ((ListView) parent).getDivider() != null) {
            ((ListView) parent).setDivider(null);
        }
    }

    /**
     * A row of the book's contents (OutlineAdapter): 44 dp and more, ink, the chapter being read
     * in the accent, a hairline under it.
     */
    public static void outlineRow(View row, TextView title, TextView page, ImageView fold, boolean current, ViewGroup parent) {
        if (!isOn() || row == null || title == null) {
            return;
        }
        try {
            noDividers(parent);
            row.setMinimumHeight(CanvasLook.target());
            row.setBackground(row(current ? CanvasLook.accentTint() : Color.TRANSPARENT));
            title.setTypeface(CanvasLook.ui(title.getContext(), current ? 600 : 400));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
            title.setTextColor(current ? CanvasLook.accent() : CanvasLook.ink());
            if (page != null) {
                page.setTypeface(CanvasLook.mono(page.getContext(), current ? 500 : 400));
                page.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
                page.setTextColor(current ? CanvasLook.accent() : CanvasLook.muted());
            }
            if (fold != null && fold.getDrawable() != null) {
                // the adapter tints it with a colour filter, which a tint list would not override
                fold.setColorFilter(current ? CanvasLook.accent() : CanvasLook.ink2(), PorterDuff.Mode.SRC_ATOP);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** A row of the reader's bookmarks (BookmarksAdapter): flat, on a hairline, ink words. */
    public static void bookmarkRow(View row, TextView text, TextView page, ImageView remove, ViewGroup parent) {
        if (!isOn() || row == null) {
            return;
        }
        try {
            noDividers(parent);
            if (row instanceof CardView) {
                final CardView card = (CardView) row;
                card.setCardElevation(0);
                card.setRadius(0);
                card.setCardBackgroundColor(Color.TRANSPARENT);
                if (card.getChildCount() > 0) {
                    final View inner = card.getChildAt(0);
                    inner.setBackground(row(Color.TRANSPARENT));
                    inner.setMinimumHeight(CanvasLook.target());
                }
            }
            if (row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
                lp.leftMargin = lp.rightMargin = lp.topMargin = lp.bottomMargin = 0;
                row.setLayoutParams(lp);
            }
            if (text != null) {
                text.setTypeface(CanvasLook.ui(text.getContext(), 400));
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_TITLE);
                text.setTextColor(CanvasLook.ink());
                text.setPadding(0, CanvasLook.dp(10), 0, CanvasLook.dp(10));
            }
            if (page != null) {
                page.setTypeface(CanvasLook.mono(page.getContext(), 400));
                page.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
                page.setTextColor(CanvasLook.muted());
            }
            if (remove != null) {
                remove.setImageTintList(ColorStateList.valueOf(CanvasLook.muted()));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
