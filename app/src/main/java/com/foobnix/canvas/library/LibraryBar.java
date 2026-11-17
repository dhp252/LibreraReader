package com.foobnix.canvas.library;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.util.SparseArray;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.ViewPager;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.SlidingTabLayout;
import com.foobnix.ui2.MainTabs2;

import java.util.List;

/**
 * The library's chrome in the canvas look: each tab's own top bar (white, night: dark, with ink
 * icons and a hairline under it instead of a bar filled with the theme colour), the tab strip
 * (ink label for the chosen tab, muted for the rest, a 2 dp accent underline) and the status
 * bar (dark icons in the day).
 * <p>
 * Callers ask {@link CanvasLook#isOn()} first; with it off the old tinting runs untouched. The
 * user's theme colour is not used for any fill here: the accent token marks the chosen tab.
 */
public final class LibraryBar {

    /** A press in ink, with no fill of its own: the icon buttons of the bar. */
    private static final int RIPPLE_ALPHA = 0x1F;

    private LibraryBar() {
    }

    /** The canvas look applies to this screen: the main screen (library) with the switch on. */
    public static boolean isOn(Activity a) {
        return a instanceof MainTabs2 && CanvasLook.isOn();
    }

    // ---- a tab's top bar --------------------------------------------------------------------

    /**
     * Paints a tab's top bar: page colour with a hairline under it, at least the canvas's bar
     * height, and every mark on it in ink. Call before the bar's height is taken as its own.
     */
    public static void header(View header) {
        if (header == null) {
            return;
        }
        try {
            final int l = header.getPaddingLeft(), t = header.getPaddingTop(),
                    r = header.getPaddingRight(), b = header.getPaddingBottom();
            final ColorDrawable line = new ColorDrawable(CanvasLook.line());
            final ColorDrawable fill = new ColorDrawable(CanvasLook.bg());
            final LayerDrawable layers = new LayerDrawable(new Drawable[]{line, fill});
            layers.setLayerInset(1, 0, 0, 0, Math.max(1, CanvasLook.dp(1)));
            header.setBackground(layers);
            final int side = CanvasLook.dp(8);
            header.setPadding(Math.max(l, side), t, Math.max(r, side), b);

            final int bar = CanvasLook.dp(CanvasLook.BAR_DP);
            header.setMinimumHeight(bar);
            final ViewGroup.LayoutParams lp = header.getLayoutParams();
            if (lp != null && lp.height > 0 && lp.height < bar) {
                lp.height = bar;
                header.setLayoutParams(lp);
            }
            keep(header);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * Draws every mark on the bar in ink again. It only writes what differs, so it is cheap to
     * call on each layout pass: a tab adds and recolours views in its bar as it goes (the folder
     * path, a sort arrow), in white, meant for a bar filled with the theme colour.
     */
    public static void keep(View header) {
        if (header instanceof ViewGroup) {
            try {
                marks((ViewGroup) header);
            } catch (Exception e) {
                LOG.e(e);
            }
        }
    }

    /** The notices a tab raises in its bar (no permission, an error): the accent's tint, ink text. */
    public static void notices(List<View> notices) {
        for (View notice : notices) {
            notice.setBackgroundColor(CanvasLook.accentTint());
            if (notice instanceof ViewGroup) {
                marks((ViewGroup) notice);
            }
        }
    }

    private static void marks(ViewGroup parent) {
        final int ink = CanvasLook.ink();
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (child instanceof EditText) {
                field((EditText) child);
            } else if (child instanceof ImageView) {
                icon((ImageView) child, ink);
            } else if (child instanceof ProgressBar) {
                spinner((ProgressBar) child);
            } else if (child instanceof TextView) {
                label((TextView) child, ink);
            } else if (child instanceof ViewGroup) {
                // a panel inside the bar (the sort buttons' pill) was filled with the theme colour
                final Drawable bg = child.getBackground();
                if (bg instanceof StateListDrawable || bg instanceof GradientDrawable
                        || bg instanceof ColorDrawable) {
                    setBackgroundKeepPadding(child, child.isClickable() ? inkRipple(false) : null);
                }
                marks((ViewGroup) child);
            }
        }
    }

    private static void icon(ImageView image, int ink) {
        if (image.getColorFilter() != null) {
            image.setColorFilter(null);
        }
        final ColorStateList tint = ImageViewCompat.getImageTintList(image);
        if (tint == null || tint.getDefaultColor() != ink) {
            ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(ink));
        }
        final Drawable bg = image.getBackground();
        if (!(bg instanceof InkRipple) && (bg != null || image.isClickable())) {
            // a white ripple, or a white or tinted ring: a round ink press instead
            setBackgroundKeepPadding(image, inkRipple(true));
        }
    }

    private static void label(TextView text, int ink) {
        if (text.getCurrentTextColor() != ink) {
            text.setTextColor(ink);
        }
        final ColorStateList drawableTint = text.getCompoundDrawableTintList();
        if (drawableTint == null || drawableTint.getDefaultColor() != ink) {
            text.setCompoundDrawableTintList(ColorStateList.valueOf(ink));
        }
        // weighed once: the canvas's faces are not "bold" to Android, so a second pass leaves them
        final Typeface now = text.getTypeface();
        final Typeface semi = CanvasLook.ui(text.getContext(), 600);
        final Typeface medium = CanvasLook.ui(text.getContext(), 500);
        if (now != semi && now != medium) {
            text.setTypeface(now != null && now.isBold() ? semi : medium);
        }
        final Drawable bg = text.getBackground();
        if (bg instanceof StateListDrawable || bg instanceof GradientDrawable) {
            setBackgroundKeepPadding(text, inkRipple(false));
        }
    }

    private static void field(EditText edit) {
        if (edit.getBackground() instanceof StateListDrawable) {
            // the outlined box of the old bar: the canvas's rounded field
            setBackgroundKeepPadding(edit, CanvasParts.field());
        }
        if (edit.getCurrentTextColor() != CanvasLook.ink()) {
            edit.setTextColor(CanvasLook.ink());
        }
        if (edit.getCurrentHintTextColor() != CanvasLook.muted()) {
            edit.setHintTextColor(CanvasLook.muted());
        }
    }

    private static void spinner(ProgressBar bar) {
        final Drawable d = bar.getIndeterminateDrawable();
        if (d == null) {
            return;
        }
        final ColorFilter accent = filter(CanvasLook.accent());
        if (d.getCurrent().getColorFilter() != accent) {
            d.getCurrent().setColorFilter(accent);
        }
    }

    private static final SparseArray<PorterDuffColorFilter> FILTERS = new SparseArray<>();

    /** One filter per colour, so a mark already in that colour is told apart and left alone. */
    static ColorFilter filter(int color) {
        PorterDuffColorFilter f = FILTERS.get(color);
        if (f == null) {
            f = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            FILTERS.put(color, f);
        }
        return f;
    }

    private static void setBackgroundKeepPadding(View v, Drawable d) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackground(d);
        v.setPadding(l, t, r, b);
    }

    /** A ripple in ink with no fill: round for an icon, rounded for a label or a panel. */
    static InkRipple inkRipple(boolean oval) {
        final GradientDrawable mask = new GradientDrawable();
        if (oval) {
            mask.setShape(GradientDrawable.OVAL);
        } else {
            mask.setCornerRadius(CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP));
        }
        mask.setColor(0xFF000000);
        return new InkRipple(null, mask);
    }

    static int rippleColor() {
        return (CanvasLook.ink() & 0x00FFFFFF) | (RIPPLE_ALPHA << 24);
    }

    /** Our own press, told apart from the old bar's so it is set once. */
    static class InkRipple extends RippleDrawable {
        InkRipple(Drawable content, Drawable mask) {
            super(ColorStateList.valueOf(rippleColor()), content, mask);
        }
    }

    // ---- the main screen ---------------------------------------------------------------------

    /**
     * The tab strip and the strip that holds it at the top: page colour, ink and muted labels, the
     * chosen tab underlined in the accent. At the foot the floating bar keeps its shape, as a
     * page-coloured pill with a hairline.
     */
    public static void tabs(SlidingTabLayout strip, View topStrip, ImageView menu, ViewPager pager) {
        try {
            if (topStrip != null) {
                final ColorDrawable line = new ColorDrawable(CanvasLook.line());
                final ColorDrawable fill = new ColorDrawable(CanvasLook.bg());
                final LayerDrawable layers = new LayerDrawable(new Drawable[]{line, fill});
                layers.setLayerInset(1, 0, 0, 0, Math.max(1, CanvasLook.dp(1)));
                setBackgroundKeepPadding(topStrip, layers);
            }
            if (menu != null) {
                icon(menu, CanvasLook.ink());
            }
            if (strip == null) {
                return;
            }
            strip.setDividerColors(0);
            strip.setSelectedIndicatorColors(0);
            strip.setPainter(new TabPainter());
            for (int i = 0; i < strip.getmTabStrip().getChildCount(); i++) {
                final View tab = strip.getmTabStrip().getChildAt(i);
                if (tab instanceof TextView) {
                    ((TextView) tab).setAllCaps(false);
                }
            }
            strip.setTabsBackground(CanvasLook.bg());
            strip.updateIcons(pager == null ? 0 : pager.getCurrentItem());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** Dark clock and icons over the white bar in the day, light ones at night. */
    public static void statusBar(Activity a) {
        try {
            final Window w = a.getWindow();
            WindowCompat.getInsetsController(w, w.getDecorView())
                        .setAppearanceLightStatusBars(!CanvasLook.isNight());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The pull-to-refresh spinner: the accent on the page colour. */
    public static void refresh(SwipeRefreshLayout refresh) {
        if (refresh == null) {
            return;
        }
        refresh.setColorSchemeColors(CanvasLook.accent());
        refresh.setProgressBackgroundColorSchemeColor(CanvasLook.bg());
    }

    /** Paints the bar and its tabs for {@link SlidingTabLayout} on each change of the chosen tab. */
    static final class TabPainter implements SlidingTabLayout.Painter {

        @Override
        public void paintPill(SlidingTabLayout bar, GradientDrawable pill) {
            pill.setColor(CanvasLook.bg());
            pill.setStroke(Math.max(1, CanvasLook.dp(1)), CanvasLook.line());
            bar.setElevation(CanvasLook.dp(3));
        }

        @Override
        public void paintTab(TextView tab, Drawable icon, boolean selected) {
            final int color = selected ? CanvasLook.ink() : CanvasLook.muted();
            if (tab.getCurrentTextColor() != color) {
                tab.setTextColor(color);
            }
            if (icon != null) {
                icon.setColorFilter(filter(color));
            }
            final Typeface face = CanvasLook.ui(tab.getContext(), 600);
            if (face != null && tab.getTypeface() != face) {
                tab.setTypeface(face);
            }
            Drawable bg = tab.getBackground();
            if (!(bg instanceof TabBackground)) {
                bg = new TabBackground(new Underline(SlidingTabLayout.isFloating()));
                setBackgroundKeepPadding(tab, bg);
            }
            ((TabBackground) bg).line.setOn(selected);
        }
    }

    /** A tab's background: an ink press over the accent underline of the chosen tab. */
    static final class TabBackground extends RippleDrawable {
        final Underline line;

        TabBackground(Underline line) {
            super(ColorStateList.valueOf(rippleColor()), line, roundMask());
            this.line = line;
        }

        private static Drawable roundMask() {
            final GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(CanvasLook.dp(CanvasLook.RADIUS_DP));
            mask.setColor(0xFF000000);
            return mask;
        }
    }

    /** The 2 dp accent underline under the chosen tab's name; nothing under the others. */
    static final class Underline extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final boolean floating;
        private boolean on;

        Underline(boolean floating) {
            this.floating = floating;
            paint.setColor(CanvasLook.accent());
        }

        void setOn(boolean value) {
            if (on != value) {
                on = value;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (!on) {
                return;
            }
            final Rect b = getBounds();
            final int thick = CanvasLook.dp(2);
            final int width = floating
                    ? Math.min(b.width() - CanvasLook.dp(16), CanvasLook.dp(28))
                    : b.width() - CanvasLook.dp(20);
            if (width <= 0) {
                return;
            }
            final float left = b.left + (b.width() - width) / 2f;
            final float bottom = b.bottom - (floating ? CanvasLook.dp(1) : 0);
            rect.set(left, bottom - thick, left + width, bottom);
            canvas.drawRoundRect(rect, thick / 2f, thick / 2f, paint);
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
