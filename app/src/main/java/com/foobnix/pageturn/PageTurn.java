package com.foobnix.pageturn;

/**
 * Book mode page turn styles and the arithmetic behind them, kept free of Android classes so it
 * runs in JVM tests. Design: .agents/planning/page-turn-design.md.
 */
public final class PageTurn {

    public static final int STYLE_NONE = 0;
    public static final int STYLE_SLIDE = 1;
    public static final int STYLE_COVER = 2;
    public static final int STYLE_CURL = 3;

    /** Slide duration bounds (ms): ViewPager asks 100-600 ms depending on distance and flick speed. */
    public static final int SLIDE_MIN_MS = 180;
    public static final int SLIDE_MAX_MS = 340;
    /** A slide started by a tap or key: long enough to see the page go, short enough to read on. */
    public static final int SLIDE_TAP_MS = 260;

    /** Largest curl texture side: 2048 fits every GLES device and is close to a phone screen. */
    public static final int MAX_TEXTURE = 2048;

    private PageTurn() {
    }

    /**
     * The style in use. {@code chosen} is AppState.pageTurnStyle: -1 (never chosen) or an unknown
     * value means the old "Scroll animation" checkbox decides.
     */
    public static int style(int chosen, boolean isScrollAnimation) {
        if (chosen >= STYLE_NONE && chosen <= STYLE_CURL) {
            return chosen;
        }
        return isScrollAnimation ? STYLE_SLIDE : STYLE_NONE;
    }

    /** The old checkbox value that matches a style, so older builds reading the synced state agree. */
    public static boolean isAnimated(int style) {
        return style != STYLE_NONE;
    }

    /**
     * Duration of a slide ViewPager wants to run for {@code requestedMs}. After a finger let go
     * it follows ViewPager (distance left, flick speed) within a calm range; a tap or key turn
     * always takes the same time.
     */
    public static int slideDuration(int requestedMs, boolean fromFinger) {
        if (!fromFinger) {
            return SLIDE_TAP_MS;
        }
        return Math.max(SLIDE_MIN_MS, Math.min(SLIDE_MAX_MS, requestedMs));
    }

    /**
     * Cover style: translation (in page widths) that keeps the page that stays still in place.
     * {@code position} is ViewPager's page position (0 = shown, -1 = one page to the left,
     * 1 = one to the right). Left to right, the right-hand page moves over the left one; right to
     * left, the mirror. Returns 0 for the page that moves normally.
     */
    public static float coverTranslation(float position, boolean rtl) {
        if (position <= -1f || position >= 1f) {
            return 0f;
        }
        boolean staysStill = rtl ? position > 0f : position < 0f;
        return staysStill ? -position : 0f;
    }

    /** True for the page drawn on top in cover style (the one that moves), which gets the shadow. */
    public static boolean coverIsMoving(float position, boolean rtl) {
        return rtl ? position < 0f && position > -1f : position > 0f && position < 1f;
    }

    /**
     * Power-of-two texture side for a page side of {@code px} pixels: the nearest power of two,
     * at most {@link #MAX_TEXTURE}. The page is drawn stretched into it, so nothing is wasted on
     * padding and the stretch is undone when the texture is laid on the page rectangle.
     */
    public static int textureSide(int px) {
        if (px <= 1) {
            return 1;
        }
        int lower = Integer.highestOneBit(px);
        int upper = lower << 1;
        int nearest = (px - lower) <= (upper - px) ? lower : upper;
        return Math.min(nearest, MAX_TEXTURE);
    }

    /**
     * Whether a released drag completes the turn. {@code progress} is how far the finger travelled
     * across the page in the turn's direction (0..1), {@code velocity} its speed in page widths
     * per second, positive in the turn's direction. A clear flick wins over the distance.
     */
    public static boolean completes(float progress, float velocity) {
        if (velocity > 0.8f) {
            return true;
        }
        if (velocity < -0.8f) {
            return false;
        }
        return progress > 0.3f;
    }

    /** A curl played by a tap or key: about as long as an unhurried swipe across the page. */
    public static final int CURL_TAP_MS = 680;
    /** How high (in page heights) the corner of a tapped curl is lifted at mid-turn. */
    public static final float CURL_TAP_LIFT = 0.16f;

    /**
     * How far across a tapped curl is (0..1) at {@code t} of its time (0..1), like a hand turning
     * a page: the corner is peeled off slowly, the page swings over, and settles gently.
     */
    public static float curlTapProgress(float t) {
        t = Math.max(0f, Math.min(1f, t));
        // ease in-out, a cubic: a calm start and landing, the speed in the middle
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /**
     * How much of {@link #CURL_TAP_LIFT} the corner is raised at {@code progress} across: none at
     * either end, most in the middle, so the corner travels an arc as a finger would.
     */
    public static float curlTapLift(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        return (float) Math.sin(Math.PI * progress);
    }
}
