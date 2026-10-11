package com.foobnix.pageturn;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewTreeObserver;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.pdf.search.activity.PageImaveView;
import com.foobnix.pdf.search.view.VerticalViewPager;

import fi.harism.curl.CurlView;

/**
 * The page curl of book mode. When a turn starts it takes pictures of the shown page and of the
 * page it turns to, lays them in the curl view over the pager and curls the top one; when the
 * curl lands it moves the pager underneath and lets the curl view go transparent once the pager
 * has drawn. Whenever that is not possible (page not drawn yet, GL error) it says no and the
 * pager slides as before. Design: .agents/planning/page-turn-design.md.
 */
public class CurlTurn implements VerticalViewPager.CurlHost, CurlView.Listener {


    private final VerticalViewPager pager;
    private final CurlView curl;
    private final Runnable onUserTurn;

    private boolean glFailed;
    private int target = -1;
    private boolean pendingTurn;
    // the running turn was dragged (a play's caller marks its own turns)
    private boolean dragTurn;
    private float downX;
    private float edge;

    /** {@code onUserTurn} runs right before the pager moves for a turn dragged by the user. */
    public CurlTurn(VerticalViewPager pager, CurlView curl, Runnable onUserTurn) {
        this.pager = pager;
        this.curl = curl;
        this.onUserTurn = onUserTurn;
        curl.setListener(this);
        curl.setOnGlErrorListener(() -> {
            LOG.d("CurlTurn", "GL failed, the pages slide for this session");
            glFailed = true;
            finishNow();
            updateVisibility();
        });
        pager.setCurlHost(this);
        updateVisibility();
    }

    /** The curl view exists only in the curl style, so the other styles pay nothing for it. */
    public void updateVisibility() {
        int visibility = isCurlStyle() ? View.VISIBLE : View.GONE;
        if (curl.getVisibility() != visibility) {
            curl.setVisibility(visibility);
        }
    }

    private boolean isCurlStyle() {
        return !glFailed && pager.turnStyle() == PageTurn.STYLE_CURL;
    }

    private boolean available() {
        updateVisibility();
        return isCurlStyle() && curl.isReady() && pager.getWidth() > 0 && pager.getAdapter() != null;
    }

    private static boolean rtl() {
        return AppSP.get().isRTL;
    }

    @Override
    public boolean isTurning() {
        return curl.isTurning();
    }

    @Override
    public boolean beginDrag(float downX, float x, float y) {
        if (!available()) {
            return false;
        }
        finishNow();
        boolean forward = (x < downX) != rtl();
        Bitmap[] pictures = pictures(forward);
        if (pictures == null) {
            return false;
        }
        this.downX = downX;
        // the page edge being lifted, as seen on screen
        edge = (forward != rtl()) ? pager.getWidth() : 0;
        pendingTurn = false;
        dragTurn = true;
        curl.begin(forward, pictures[0], pictures[1], edgeX(x), y, backColor(), rtl());
        return true;
    }

    @Override
    public void drag(float x, float y) {
        curl.drag(edgeX(x), y, rtl());
    }

    @Override
    public void release(float x, float velocityX) {
        float width = pager.getWidth();
        float progress = Math.abs(edgeX(x) - edge) / width;
        // the finger moves away from the lifted edge: leftwards from the right edge
        float velocity = (edge > 0 ? -velocityX : velocityX) / width;
        pendingTurn = PageTurn.completes(progress, velocity);
        curl.release(pendingTurn);
    }

    /** A turn by tap or key; false when it cannot curl (the caller turns the page itself). */
    public boolean play(boolean forward) {
        if (!available()) {
            return false;
        }
        finishNow();
        Bitmap[] pictures = pictures(forward);
        if (pictures == null) {
            return false;
        }
        pendingTurn = true;
        dragTurn = false;
        curl.play(forward, pictures[0], pictures[1], backColor(), rtl(), PageTurn.CURL_TAP_MS);
        return true;
    }

    /** Lands a running turn at once (a new turn, the reader paused). */
    public void finishNow() {
        if (!curl.isTurning()) {
            return;
        }
        curl.clear();
        if (pendingTurn) {
            pendingTurn = false;
            moveTo(target);
        }
    }

    @Override
    public void onLanded(boolean turned) {
        pendingTurn = false;
        if (!turned) {
            curl.clear();
            return;
        }
        moveTo(target);
        // the curl view shows the landed page until the pager has drawn it too
        final ViewTreeObserver observer = pager.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                } else {
                    pager.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                curl.clear();
                return true;
            }
        });
        pager.invalidate();
    }

    private void moveTo(int page) {
        if (page < 0 || pager.getAdapter() == null || page >= pager.getAdapter().getCount()) {
            return;
        }
        if (dragTurn) {
            onUserTurn.run();
        }
        pager.setCurrentItem(page, false);
    }

    private float edgeX(float x) {
        return Math.max(0, Math.min(pager.getWidth(), edge + x - downX));
    }

    private static int backColor() {
        int bg = MagicHelper.getBgColor();
        return Color.argb(110, Color.red(bg), Color.green(bg), Color.blue(bg));
    }

    /** Pictures of the shown page and of the page a turn goes to, or null; sets the target. */
    private Bitmap[] pictures(boolean forward) {
        int shown = pager.getCurrentItem();
        int to = shown + (forward ? 1 : -1);
        if (to < 0 || to >= pager.getAdapter().getCount()) {
            return null;
        }
        PageImaveView shownView = findPage(shown);
        PageImaveView toView = findPage(to);
        if (shownView == null || toView == null || !shownView.hasPageImage() || !toView.hasPageImage()) {
            return null;
        }
        Bitmap a = null;
        try {
            a = picture(shownView);
            Bitmap b = picture(toView);
            target = to;
            return new Bitmap[]{a, b};
        } catch (OutOfMemoryError e) {
            LOG.e(e);
            if (a != null) {
                a.recycle();
            }
            return null;
        }
    }

    private PageImaveView findPage(int page) {
        for (int i = 0; i < pager.getChildCount(); i++) {
            View image = pager.getChildAt(i).findViewById(R.id.myImage1);
            if (image instanceof PageImaveView && ((PageImaveView) image).getPageNumber() == page) {
                return (PageImaveView) image;
            }
        }
        return null;
    }

    /**
     * The page as drawn on screen (zoom, TTS, AI and annotation marks included), stretched to a
     * power-of-two size in RGB_565 (design D8), mirrored for right-to-left books (D10).
     */
    private Bitmap picture(PageImaveView view) {
        int w = view.getWidth();
        int h = view.getHeight();
        int tw = PageTurn.textureSide(w);
        int th = PageTurn.textureSide(h);
        Bitmap bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        if (rtl()) {
            canvas.translate(tw, 0);
            canvas.scale(-1, 1);
        }
        canvas.scale((float) tw / w, (float) th / h);
        view.draw(canvas);
        return bitmap;
    }
}
