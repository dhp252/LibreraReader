package com.foobnix.crop;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.foobnix.LibreraApp;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;

import org.ebookdroid.common.bitmaps.BitmapManager;
import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crop of each page of the open book, for both reading modes.
 * <p>
 * Pages are measured on a small plain render (no colours, no night inversion), so every mode
 * and format crops alike. With "same margins" on, the book's {@link CropPlan} is measured once
 * and kept in the app's cache folder (this device only); each page's own box is kept for the
 * session. The renders run under this object's lock, which only waits on MuPDF's own lock.
 */
public final class CropPlans {

    private static final String TAG = "CropPlans";
    /** The sample is this wide, its height following the page (at most {@link #SAMPLE_MAX_H}). */
    static final int SAMPLE_W = 500;
    static final int SAMPLE_MAX_H = 1400;

    static final RectF FULL = new RectF(0, 0, 1, 1);
    static final RectF LEFT = new RectF(0, 0, 0.5f, 1);
    static final RectF RIGHT = new RectF(0.5f, 0, 1, 1);

    private static final CropPlans INSTANCE = new CropPlans();

    /** The book and settings the caches below belong to. */
    private String key;
    private CropPlan plan;
    private boolean planned;
    private final Map<String, float[]> boxes = new HashMap<>();

    private CropPlans() {
    }

    public static CropPlans get() {
        return INSTANCE;
    }

    /**
     * The part of the page to show, in fractions of the whole page.
     *
     * @param slice   how the page is shown: whole, or one half of it (split pages)
     * @param current the page when the caller has it open already, else null
     */
    public synchronized RectF box(CodecDocument doc, String path, int page, RectF slice, CodecPage current) {
        reset(path, slice);
        final int slot = slot(page, slice);
        final String k = page + ":" + slot;
        float[] b = boxes.get(k);
        if (b == null) {
            final boolean headers = AppState.get().isCropHeaders;
            final CropPlan p = AppState.get().isCropSameMargins ? plan(doc, path, slice, page, current) : null;
            final CropFinder.Measure own = measure(doc, page, slice, page, current);
            b = p != null ? p.box(slot, own, headers) : CropPlan.alone(own, headers);
            boxes.put(k, b);
        }
        return toPage(b, slice);
    }

    /** The book's margins for a page not measured yet: null when there is no plan (yet). */
    public synchronized RectF planned(String path, int page, RectF slice) {
        reset(path, slice);
        if (plan == null) {
            return null;
        }
        final float[] b = plan.planned(slot(page, slice));
        return b == null ? null : toPage(b, slice);
    }

    /** Forget everything when the book or a crop setting changed. */
    private void reset(String path, RectF slice) {
        final AppState s = AppState.get();
        final String k = path + "|" + s.cropLeft + "," + s.cropTop + "," + s.cropRigth + "," + s.cropBottom
                + "|" + s.isCropSameMargins + "|" + s.isCropHeaders + "|" + isHalf(slice);
        if (!k.equals(key)) {
            key = k;
            plan = null;
            planned = false;
            boxes.clear();
        }
    }

    private CropPlan plan(CodecDocument doc, String path, RectF slice, int page, CodecPage current) {
        if (planned) {
            return plan;
        }
        planned = true;
        final int count = doc.getPageCount();
        final File book = new File(path == null ? "" : path);
        final String diskKey = CropPlan.VERSION + "|" + key + "|" + book.length() + "|" + book.lastModified() + "|" + count;
        final File file = new File(new File(LibreraApp.context.getCacheDir(), "crop"),
                Integer.toHexString(diskKey.hashCode()) + "-" + diskKey.length() + ".json");

        plan = load(file, diskKey);
        if (plan != null) {
            return plan;
        }

        final long start = System.currentTimeMillis();
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int p : CropPlan.samplePages(count)) {
            if (isHalf(slice)) {
                samples.add(new CropPlan.Sample(CropPlan.LEFT_HALF, measure(doc, p, LEFT, page, current)));
                samples.add(new CropPlan.Sample(CropPlan.RIGHT_HALF, measure(doc, p, RIGHT, page, current)));
            } else {
                samples.add(new CropPlan.Sample(slot(p, FULL), measure(doc, p, FULL, page, current)));
            }
        }
        plan = CropPlan.build(samples, AppState.get().isCropHeaders);
        if (plan == null) {
            // too few pages hold anything: every page alone, and that is remembered too
            plan = new CropPlan();
        }
        LOG.d(TAG, "plan", samples.size(), "samples", System.currentTimeMillis() - start, "ms");
        save(file, diskKey, plan);
        return plan;
    }

    /** Page {@code p} measured as {@code slice}; null when blank or not measurable. */
    private static CropFinder.Measure measure(CodecDocument doc, int p, RectF slice, int currentPage, CodecPage current) {
        final boolean own = p == currentPage && current != null && !current.isRecycled();
        CodecPage page = null;
        BitmapRef ref = null;
        try {
            page = own ? current : doc.getPageInner(p);
            if (page == null || page.getWidth() <= 0 || page.getHeight() <= 0) {
                return null;
            }
            final float aspect = page.getWidth() * slice.width() / (page.getHeight() * slice.height());
            final int w = SAMPLE_W;
            final int h = Math.max(16, Math.min(SAMPLE_MAX_H, Math.round(w / aspect)));
            ref = page.renderBitmapSimple(w, h, slice);
            final Bitmap bitmap = ref.getBitmap();
            final int bw = bitmap.getWidth(), bh = bitmap.getHeight();
            final int[] px = new int[bw * bh];
            bitmap.getPixels(px, 0, bw, 0, 0, bw, bh);
            final AppState s = AppState.get();
            return CropFinder.measure(px, bw, bh,
                    new float[]{s.cropLeft / 100f, s.cropTop / 100f, s.cropRigth / 100f, s.cropBottom / 100f});
        } catch (Throwable e) {
            LOG.e(e);
            return null;
        } finally {
            BitmapManager.release(ref);
            if (!own && page != null) {
                page.recycle();
            }
        }
    }

    static int slot(int page, RectF slice) {
        if (isHalf(slice)) {
            return slice.left < 0.25f ? CropPlan.LEFT_HALF : CropPlan.RIGHT_HALF;
        }
        return page % 2 == 0 ? CropPlan.EVEN : CropPlan.ODD;
    }

    static boolean isHalf(RectF slice) {
        return slice.width() < 0.75f;
    }

    static RectF toPage(float[] b, RectF slice) {
        return new RectF(slice.left + b[0] * slice.width(), slice.top + b[1] * slice.height(),
                slice.left + b[2] * slice.width(), slice.top + b[3] * slice.height());
    }

    private static CropPlan load(File file, String diskKey) {
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] bytes = new byte[(int) file.length()];
            int n = 0;
            while (n < bytes.length) {
                final int r = in.read(bytes, n, bytes.length - n);
                if (r < 0) {
                    break;
                }
                n += r;
            }
            final JSONObject o = new JSONObject(new String(bytes, 0, n, StandardCharsets.UTF_8));
            return diskKey.equals(o.optString("key")) ? CropPlan.fromJson(o) : null;
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    private static void save(File file, String diskKey, CropPlan plan) {
        try {
            file.getParentFile().mkdirs();
            final JSONObject o = plan.toJson();
            o.put("key", diskKey);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
