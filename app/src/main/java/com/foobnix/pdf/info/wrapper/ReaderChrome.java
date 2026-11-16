package com.foobnix.pdf.info.wrapper;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

/**
 * The reader's bars as a sheet: a white (over a dark page a near-black) surface with ink marks,
 * a hairline against the page and one accent, in place of bars filled with the theme colour and
 * white marks. The look of the "Book That Reads You Back" canvas.
 * <p>
 * Off ({@link AppState#isPaperChrome}), on Ink, and for a reader who picked a colour for the
 * interface text, every method falls back to what the bars did before.
 */
public final class ReaderChrome {

    public static final int INK_DAY = 0xFF14161B;
    public static final int INK_NIGHT = 0xFFE9EBEF;
    public static final int BG_DAY = 0xFFFFFFFF;
    public static final int BG_NIGHT = 0xFF14161B;
    public static final int LINE_DAY = 0xFFE3E5EA;
    public static final int LINE_NIGHT = 0xFF2A2D35;
    public static final int ACCENT_DAY = 0xFF2F45E0;
    public static final int ACCENT_NIGHT = 0xFFAEB9FF;

    private ReaderChrome() {
    }

    public static boolean isOn() {
        return AppState.get().isPaperChrome && com.foobnix.canvas.CanvasLook.isOn();
    }

    /** The marks are ink only while the reader has not picked a colour for them. */
    public static boolean inkMarks() {
        return isOn() && !AppState.get().isUiTextColor;
    }

    /** The bars follow the page under them: a dark page gets the dark sheet. */
    public static boolean isNight() {
        return ColorUtils.calculateLuminance(MagicHelper.getBgColor()) < 0.4;
    }

    public static int bg() {
        if (!isOn()) {
            return TintUtil.color;
        }
        if (!isNight()) {
            return BG_DAY;
        }
        return AppState.get().appTheme == AppState.THEME_DARK_OLED ? Color.BLACK : BG_NIGHT;
    }

    public static int ink() {
        return isNight() ? INK_NIGHT : INK_DAY;
    }

    public static int line() {
        return isNight() ? LINE_NIGHT : LINE_DAY;
    }

    /** The one colour that is not ink: progress, the seek bar. Off, the theme colour. */
    public static int accent() {
        if (!isOn()) {
            return TintUtil.color;
        }
        return isNight() ? ACCENT_NIGHT : ACCENT_DAY;
    }

    /** The seek bar: the accent on the sheet, else the colour of the marks. */
    public static int seek() {
        return inkMarks() ? accent() : MagicHelper.getTextOrIconColor();
    }

    /**
     * Fills a bar. On, the bar also gets a hairline on the edge that meets the page, since a
     * white bar on a white page has no edge of its own.
     */
    public static void paintBar(View bar, int alpha, boolean lineBelow) {
        if (bar == null) {
            return;
        }
        if (!isOn()) {
            TintUtil.setTintBgSimple(bar, alpha);
            return;
        }
        ColorDrawable line = new ColorDrawable(line());
        ColorDrawable fill = new ColorDrawable(bg());
        fill.setAlpha(alpha);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{line, fill});
        int px = Math.max(1, Dips.dpToPx(1));
        layers.setLayerInset(1, 0, lineBelow ? 0 : px, 0, lineBelow ? px : 0);
        // a LayerDrawable would hand its own padding to the bar
        int l = bar.getPaddingLeft(), t = bar.getPaddingTop(), r = bar.getPaddingRight(), b = bar.getPaddingBottom();
        bar.setBackground(layers);
        bar.setPadding(l, t, r, b);
    }

    /**
     * Draws the marks of the reader's screen: every icon, link and tick under {@code root} in
     * ink, and every label on the {@code bars} too. Off, the old tinting.
     */
    public static void tint(View root, View... bars) {
        if (!inkMarks() || !(root instanceof ViewGroup)) {
            TxtUtils.updateAllLinks(root);
            return;
        }
        try {
            int ink = ink();
            marks((ViewGroup) root, ink, ColorStateList.valueOf(ink), ColorStateList.valueOf(accent()));
            for (View bar : bars) {
                labels(bar, ink);
            }
        } catch (Exception e) {
            com.foobnix.android.utils.LOG.e(e);
        }
    }

    // The walk of TxtUtils.updateAllLinks, with ink where that one puts white.
    private static void marks(ViewGroup parent, int ink, ColorStateList inkTint, ColorStateList accentTint) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if ("no_tint".equals(child.getTag())) {
                continue;
            }
            if ("with_tint".equals(child.getTag())) {
                child.setBackgroundColor(line());
                continue;
            }
            if (child instanceof ViewGroup) {
                marks((ViewGroup) child, ink, inkTint, accentTint);
            }
            if (child instanceof TextView && "textLink".equals(child.getTag())) {
                ((TextView) child).setTextColor(ink);
                TintUtil.setRingColor(child, ink);
            }
            if (Build.VERSION.SDK_INT < 21) {
                continue;
            }
            if (child instanceof CheckBox) {
                ((CheckBox) child).setButtonTintList(inkTint);
            } else if (child instanceof ImageView) {
                ImageView image = (ImageView) child;
                if (image.getId() == R.id.closePopup || image.getId() == R.id.onIconAction) {
                    // these stand on a dialog's header: the theme colour, or the canvas's light head
                    image.setImageTintList(ColorStateList.valueOf(com.foobnix.canvas.reader.PopupLook.headMark(image, Color.WHITE)));
                } else {
                    image.setImageTintList(inkTint);
                    TintUtil.setRingColor(image, ink);
                }
            } else if (child instanceof SeekBar) {
                SeekBar seekBar = (SeekBar) child;
                seekBar.setSaveEnabled(false);
                seekBar.setSaveFromParentEnabled(false);
                seekBar.setProgressTintList(accentTint);
                seekBar.setThumbTintList(accentTint);
                seekBar.setIndeterminateTintList(accentTint);
            }
        }
    }

    private static void labels(View view, int ink) {
        if (view == null || "no_tint".equals(view.getTag())) {
            return;
        }
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(ink);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                labels(group.getChildAt(i), ink);
            }
        }
    }
}
