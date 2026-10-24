package com.foobnix.crop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CropFinderTest {

    static final int W = 500, H = 700;
    static final int WHITE = 0xffffffff, BLACK = 0xff000000;

    /** A page to draw on, filled with its paper. */
    static int[] page(int paper) {
        final int[] px = new int[W * H];
        java.util.Arrays.fill(px, paper);
        return px;
    }

    static void rect(int[] px, int l, int t, int r, int b, int color) {
        for (int y = t; y < b; y++) {
            for (int x = l; x < r; x++) {
                px[y * W + x] = color;
            }
        }
    }

    /** Lines of words from l to r: 8 px high every 14 px, words 30 px with 8 px gaps. */
    static void text(int[] px, int l, int t, int r, int lines, int ink) {
        for (int i = 0, y = t; i < lines; i++, y += 14) {
            for (int x = l; x < r; x += 38) {
                rect(px, x, y, Math.min(x + 30, r), y + 8, ink);
            }
            rect(px, r - 4, y, r, y + 8, ink);
        }
    }

    /** Where text of this many lines from t ends. */
    static int bottom(int t, int lines) {
        return t + 14 * (lines - 1) + 8;
    }

    /** The body of most tests: 34 lines from 120 to 590. */
    static void body(int[] px, int ink) {
        text(px, 60, 120, 440, 34, ink);
    }

    static void assertBox(float l, float t, float r, float b, float[] box) {
        assertNotNull(box);
        assertEquals("left", l, box[0], 0.01f);
        assertEquals("top", t, box[1], 0.01f);
        assertEquals("right", r, box[2], 0.01f);
        assertEquals("bottom", b, box[3], 0.01f);
    }

    @Test
    public void theBodyIsFoundAndAPageNumberApart() {
        final int[] px = page(WHITE);
        body(px, BLACK);
        rect(px, 240, 650, 260, 658, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(60f / W, 120f / H, 440f / W, 658f / H, m.all);
        assertBox(60f / W, 120f / H, 440f / W, 590f / H, m.body);
        assertNull(m.head);
        assertNotNull(m.foot);
        assertEquals(650f / H, m.foot[1], 0.005f);
    }

    @Test
    public void aRunningHeaderWithItsRuleIsOneLineApart() {
        final int[] px = page(WHITE);
        rect(px, 60, 40, 440, 48, BLACK);
        rect(px, 60, 54, 440, 55, BLACK);
        body(px, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertNotNull(m.head);
        assertEquals(40f / H, m.head[1], 0.005f);
        assertEquals(55f / H, m.head[3], 0.005f);
        assertEquals(120f / H, m.body[1], 0.005f);
        assertEquals(40f / H, m.all[1], 0.005f);
    }

    @Test
    public void aWideFootnoteIsNotAFootLine() {
        final int[] px = page(WHITE);
        body(px, BLACK);
        rect(px, 60, 640, 440, 648, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertNull(m.foot);
        assertEquals(648f / H, m.body[3], 0.005f);
    }

    @Test
    public void specksAwayFromTheTextAreNotContent() {
        final int[] px = page(WHITE);
        body(px, BLACK);
        rect(px, 10, 300, 12, 302, BLACK);
        rect(px, 480, 30, 482, 32, BLACK);
        rect(px, 470, 680, 471, 681, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(60f / W, 120f / H, 440f / W, 590f / H, m.all);
    }

    @Test
    public void aScannerBorderAndTheBindingShadowAreCutOff() {
        final int[] px = page(WHITE);
        rect(px, 0, 0, 20, H, BLACK);
        rect(px, 488, 0, W, H, 0xff333333);
        rect(px, 0, 0, W, 12, BLACK);
        body(px, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(60f / W, 120f / H, 440f / W, 590f / H, m.all);
    }

    @Test
    public void aPictureToTheEdgeIsKeptWhole() {
        final int[] px = page(WHITE);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                final int v = (x * 7 + y * 3) % 256;
                px[y * W + x] = 0xff000000 | (v << 16) | (v << 8) | v;
            }
        }
        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(0, 0, 1, 1, m.all);
    }

    @Test
    public void greyPaperIsPaper() {
        final int grey = 0xffb4b0a8;
        final int[] px = page(grey);
        // scan noise round the paper's tone
        for (int i = 0; i < px.length; i += 7) {
            px[i] = 0xffaaa69e;
        }
        body(px, 0xff202020);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(60f / W, 120f / H, 440f / W, 590f / H, m.all);
    }

    @Test
    public void lightTextOnADarkPage() {
        final int[] px = page(BLACK);
        text(px, 80, 100, 420, 30, 0xffe0e0e0);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, null);
        assertBox(80f / W, 100f / H, 420f / W, (float) bottom(100, 30) / H, m.all);
    }

    @Test
    public void aBlankPageHoldsNothing() {
        assertNull(CropFinder.measure(page(WHITE), W, H, null));
        final int[] speck = page(WHITE);
        rect(speck, 200, 300, 202, 302, BLACK);
        assertNull(CropFinder.measure(speck, W, H, null));
    }

    @Test
    public void theDialogsMarginsAreLeftOut() {
        final int[] px = page(WHITE);
        body(px, BLACK);
        rect(px, 240, 650, 260, 658, BLACK);
        rect(px, 0, 300, 30, 400, BLACK);

        final CropFinder.Measure m = CropFinder.measure(px, W, H, new float[]{0.1f, 0, 0, 0.1f});
        assertBox(60f / W, 120f / H, 440f / W, 590f / H, m.all);
    }
}
