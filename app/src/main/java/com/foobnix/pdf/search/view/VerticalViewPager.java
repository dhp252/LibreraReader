package com.foobnix.pdf.search.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Scroller;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pageturn.PageTurn;

import java.lang.reflect.Field;

public class VerticalViewPager extends CustomViewPager {

    /** Takes a horizontal drag in the curl style; see CurlTurn. */
    public interface CurlHost {
        /** The finger went down at downX and is now at (x, y): true if a curl took the drag. */
        boolean beginDrag(float downX, float x, float y);

        void drag(float x, float y);

        /** velocityX in pixels per second. */
        void release(float x, float velocityX);

        /** A curl is on screen (dragged or landing). */
        boolean isTurning();
    }

    private TurnScroller value;
    private CurlHost curlHost;
    private final int touchSlop;
    private final GradientDrawable coverShadow;
    private final int coverShadowWidth = Dips.DP_15;

    private float downX, downY;
    private boolean curlDragging;
    // the curl said no for this gesture (page not drawn yet): the pager slides it
    private boolean curlRefused;
    private VelocityTracker velocityTracker;

    public VerticalViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        coverShadow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x14000000, 0x38000000});
        init();
        setMyScroller();
    }

    public void setCurlHost(CurlHost curlHost) {
        this.curlHost = curlHost;
    }

    private void init() {
        if (AppState.get().rotateViewPager == 90) {
            setPageTransformer(true, new VerticalPageTransformer());
        } else {
            applyTurnStyle();
        }
    }

    /** The page turn style in effect for this pager (the rotated pager always slides). */
    public int turnStyle() {
        if (AppState.get().rotateViewPager == 90) {
            return AppState.get().isScrollAnimation ? PageTurn.STYLE_SLIDE : PageTurn.STYLE_NONE;
        }
        return PageTurn.style(AppState.get().pageTurnStyle, AppState.get().isScrollAnimation);
    }

    /**
     * Installs the cover transformer with the drawing order of the reading direction, or none.
     * Called before each turn, so a style or direction changed in the settings applies at once.
     */
    private void applyTurnStyle() {
        if (AppState.get().rotateViewPager == 90) {
            return;
        }
        if (turnStyle() == PageTurn.STYLE_COVER) {
            // the moving page is drawn last: the right one, or the left one right to left
            setPageTransformer(isRtl(), coverTransformer);
        } else if (coverInstalled) {
            setPageTransformer(false, null);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setTranslationX(0);
            }
        }
        coverInstalled = turnStyle() == PageTurn.STYLE_COVER;
    }

    private boolean coverInstalled;

    private final PageTransformer coverTransformer = new PageTransformer() {
        @Override
        public void transformPage(View page, float position) {
            page.setTranslationX(page.getWidth() * PageTurn.coverTranslation(position, isRtl()));
        }
    };

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (coverInstalled && getWidth() > 0) {
            float position = (float) (child.getLeft() - getScrollX() - getPaddingLeft()) / getWidth();
            if (PageTurn.coverIsMoving(position, isRtl())) {
                int edge;
                if (isRtl()) {
                    edge = (int) (child.getRight() + child.getTranslationX());
                    coverShadow.setOrientation(GradientDrawable.Orientation.RIGHT_LEFT);
                    coverShadow.setBounds(edge, child.getTop(), edge + coverShadowWidth, child.getBottom());
                } else {
                    edge = (int) (child.getLeft() + child.getTranslationX());
                    coverShadow.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                    coverShadow.setBounds(edge - coverShadowWidth, child.getTop(), edge, child.getBottom());
                }
                coverShadow.draw(canvas);
            }
        }
        return more;
    }

    @Override
    public void setCurrentItem(int item, boolean smoothScroll) {
        applyTurnStyle();
        super.setCurrentItem(item, smoothScroll);
    }

    private void setMyScroller() {
        try {
            Class<?> viewpager = ViewPager.class;
            Field scroller = viewpager.getDeclaredField("mScroller");
            scroller.setAccessible(true);
            value = new TurnScroller(getContext());
            scroller.set(this, value);

            Field mFlingDistance = viewpager.getDeclaredField("mFlingDistance");
            mFlingDistance.setAccessible(true);
            mFlingDistance.set(this, Dips.DP_10);

            Field mMinimumVelocity = viewpager.getDeclaredField("mMinimumVelocity");
            mMinimumVelocity.setAccessible(true);
            mMinimumVelocity.set(this, 0);

        } catch (Exception e) {
            LOG.e(e);
        }

    }

    /**
     * Eased slide. A page let go by the finger is already moving, so it only slows down; a page
     * turned by a tap or key starts gently and settles. The duration ViewPager asks for (from
     * the distance left and the flick speed) is kept between 180 and 340 ms; a tap or key turn
     * takes 260 ms.
     */
    public static class TurnScroller extends Scroller {
        private static final Interpolator SETTLE = new PathInterpolator(0.1f, 0.6f, 0.2f, 1f);
        private static final Interpolator TURN = new PathInterpolator(0.35f, 0f, 0.15f, 1f);

        private static class Switch implements Interpolator {
            boolean fromFinger;

            @Override
            public float getInterpolation(float t) {
                return fromFinger ? SETTLE.getInterpolation(t) : TURN.getInterpolation(t);
            }
        }

        private final Switch interpolator;
        boolean nextFromFinger;

        public TurnScroller(Context context) {
            this(context, new Switch());
        }

        private TurnScroller(Context context, Switch interpolator) {
            super(context, interpolator);
            this.interpolator = interpolator;
        }

        @Override
        public void startScroll(int startX, int startY, int dx, int dy, int duration) {
            interpolator.fromFinger = nextFromFinger;
            nextFromFinger = false;
            super.startScroll(startX, startY, dx, dy, PageTurn.slideDuration(duration, interpolator.fromFinger));
        }
    }


    private class VerticalPageTransformer implements ViewPager.PageTransformer {

        @Override
        public void transformPage(View view, float position) {
            if (position < -1) {
                // view.setAlpha(0);
            } else if (position <= 1) {
                // view.setAlpha(1);

                view.setTranslationX(view.getWidth() * -position);

                float yPosition = position * view.getHeight();
                view.setTranslationY(yPosition);
            } else {
                // view.setAlpha(0);
            }
        }
    }

    private MotionEvent swapXY(MotionEvent ev) {
        if (AppState.get().rotateViewPager == 90) {
            float width = getWidth();
            float height = getHeight();

            float newX = (ev.getY() / height) * width;
            float newY = (ev.getX() / width) * height;

            ev.setLocation(newX, newY);
        }

        return ev;
    }

    /** Hands a horizontal drag to the curl before ViewPager's own (larger) paging slop. */
    private boolean interceptForCurl(MotionEvent ev) {
        if (curlHost == null || AppState.get().rotateViewPager == 90 || turnStyle() != PageTurn.STYLE_CURL
                || ev.getPointerCount() != 1) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                curlDragging = false;
                curlRefused = false;
                trackVelocity(ev, true);
                return false;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev, false);
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!curlRefused && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    curlDragging = curlHost.beginDrag(downX, ev.getX(), ev.getY());
                    curlRefused = !curlDragging;
                    return curlDragging;
                }
                return false;
            default:
                return false;
        }
    }

    private void trackVelocity(MotionEvent ev, boolean reset) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else if (reset) {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
    }

    private boolean curlTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev, false);
                curlHost.drag(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackVelocity(ev, false);
                velocityTracker.computeCurrentVelocity(1000);
                curlDragging = false;
                curlHost.release(ev.getX(), velocityTracker.getXVelocity());
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!AppState.get().isEnableHorizontalSwipe) {
            return false;
        }
        if(AppState.get().isSelectTexByTouch){
            return false;
        }

        if (value != null && !value.isFinished()) {
            return false;
        }
        // a finger dragging a text selection (set by the page's long press) is not a page swipe
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN && AppState.get().selectedText != null && !curlDragging) {
            return false;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            applyTurnStyle();
        }
        if (curlDragging || interceptForCurl(ev)) {
            return true;
        }

        boolean intercepted = super.onInterceptTouchEvent(swapXY(ev));
        swapXY(ev);
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (curlDragging) {
            return curlTouch(ev);
        }
        if (!AppState.get().isEnableHorizontalSwipe) {
            return false;
        }
        if(AppState.get().isSelectTexByTouch){
            return false;
        }

        int action = ev.getActionMasked();
        if (value != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            value.nextFromFinger = true;
        }
        boolean handled = super.onTouchEvent(swapXY(ev));
        if (value != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            // no settle started (a tap): the next slide is a tap or key turn
            value.nextFromFinger = false;
        }
        return handled;
    }

    @Override
    public void setAdapter(PagerAdapter adapter) {
        try {
            super.setAdapter(adapter);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

}
