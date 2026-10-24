package com.foobnix.pdf.info.wrapper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.foobnix.model.AppState;

/**
 * The reader's paper: the background colour and, when one is chosen, the texture over it,
 * painted across the whole screen rather than behind the page alone.
 * <p>
 * The texture is one picture for the whole window, scaled to cover it, and every surface paints
 * the part of it that lies at its own place in the window. The page, the room round it, the strips
 * under the system bars and the status line then read as one sheet. The blend is the one the page
 * always had: the texture over the colour (or over the page) at {@code 255 - transparency}.
 */
public final class ReadingSurface {

    private static Matrix matrix;
    private static Paint paint;
    private static final int[] at = new int[2];

    private ReadingSurface() {
    }

    /** The paper fills the screen; off, the room round the page keeps its own colour. */
    public static boolean isOn() {
        return AppState.get().isBgFullScreen;
    }

    /** The paper alone, at the place of {@code view} in its window. */
    public static void fill(Canvas canvas, View view) {
        canvas.drawColor(MagicHelper.getBgColor());
        overlay(canvas, view);
    }

    /** The texture over what is drawn already (nothing without a texture). UI thread only. */
    public static void overlay(Canvas canvas, View view) {
        overlay(canvas, view, view);
    }

    /**
     * The texture over what {@code view} has drawn, placed as if {@code view} lay where
     * {@code atRest} is. A pager's page is drawn ahead of time, off screen, and its drawing is
     * kept while it slides in: its own place at that moment would leave the texture a screen off.
     */
    public static void overlay(Canvas canvas, View view, View atRest) {
        if (view == null || atRest == null || !MagicHelper.isNeedBookBackgroundImage()) {
            return;
        }
        final Bitmap texture = MagicHelper.getBackgroundImage();
        final View window = view.getRootView();
        if (texture == null || texture.isRecycled() || window.getWidth() <= 0 || window.getHeight() <= 0) {
            return;
        }
        if (matrix == null) {
            matrix = new Matrix();
            paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }
        atRest.getLocationInWindow(at);
        final float[] cover = coverMatrix(texture.getWidth(), texture.getHeight(), window.getWidth(), window.getHeight());
        matrix.setScale(cover[0], cover[0]);
        matrix.postTranslate(cover[1] - at[0], cover[2] - at[1]);
        paint.setAlpha(255 - MagicHelper.getTransparencyInt());
        canvas.drawBitmap(texture, matrix, paint);
    }

    /** Whether the paper reads as light, so what stands on it (the clock, the battery) goes dark. */
    public static boolean isLight() {
        int color = MagicHelper.getBgColor();
        if (MagicHelper.isNeedBookBackgroundImage()) {
            MagicHelper.getBackgroundImage();
            color = ColorUtils.blendARGB(color, MagicHelper.mainColor, 1f - MagicHelper.getTransparencyInt() / 255f);
        }
        return ColorUtils.calculateLuminance(color) > 0.5;
    }

    /**
     * How a {@code w} x {@code h} picture covers a {@code screenW} x {@code screenH} window,
     * centred, its shape kept: {scale, dx, dy}, the picture's top left at (dx, dy) once scaled.
     */
    public static float[] coverMatrix(int w, int h, int screenW, int screenH) {
        final float scale = Math.max((float) screenW / w, (float) screenH / h);
        return new float[]{scale, (screenW - w * scale) / 2f, (screenH - h * scale) / 2f};
    }

    /** The paper as a background, for the window and the status line. */
    public static class Paper extends Drawable {
        private final View host;

        /** @param host the view the drawable is the background of: it says where the paper lies. */
        public Paper(View host) {
            this.host = host;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.clipRect(getBounds());
            fill(canvas, host);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
