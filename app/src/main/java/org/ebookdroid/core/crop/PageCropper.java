package org.ebookdroid.core.crop;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.crop.CropFinder;
import com.foobnix.crop.CropPlan;
import com.foobnix.model.AppState;

public class PageCropper {
    public static final int MAX_HEIGHT = Dips.DP_300;
    public static final int MAX_WIDTH = Dips.DP_200;
    /** Bitmaps wider than this are measured on a copy this wide. */
    static final int SAMPLE_W = 500;

    /**
     * The matter of a bitmap with no book around it (the reader's pages go through
     * {@link com.foobnix.crop.CropPlans}): {@link CropFinder} on it, mapped into pageSliceBounds.
     */
    public static RectF getCropBounds(Bitmap bitmap1, final Rect bitmapBounds, final RectF pageSliceBounds) {
        Bitmap sample = bitmap1;
        if (bitmap1.getWidth() > SAMPLE_W) {
            final int h = Math.max(1, bitmap1.getHeight() * SAMPLE_W / bitmap1.getWidth());
            sample = Bitmap.createScaledBitmap(bitmap1, SAMPLE_W, h, true);
        }
        final int w = sample.getWidth();
        final int h = sample.getHeight();
        final int[] px = new int[w * h];
        sample.getPixels(px, 0, w, 0, 0, w, h);
        if (sample != bitmap1) {
            sample.recycle();
        }

        final AppState s = AppState.get();
        final float[] b = CropPlan.alone(CropFinder.measure(px, w, h,
                new float[]{s.cropLeft / 100f, s.cropTop / 100f, s.cropRigth / 100f, s.cropBottom / 100f}), s.isCropHeaders);

        LOG.d("getCropBounds-crop", b[0], b[1], b[2], b[3]);
        return new RectF(b[0] * pageSliceBounds.width() + pageSliceBounds.left, b[1] * pageSliceBounds.height() + pageSliceBounds.top,
                b[2] * pageSliceBounds.width() + pageSliceBounds.left, b[3] * pageSliceBounds.height() + pageSliceBounds.top);
    }

}
