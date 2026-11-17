package com.foobnix.assist.pictures;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PictureLayoutTest {

    @Test
    public void boxesThatFitStandInOneRowWithArrowsBetween() {
        final PictureLayout.Flow f = PictureLayout.flow(new int[]{60, 80, 40}, new int[]{30, 30, 30}, 12, 4, 6, 358);
        assertEquals(1, f.rows);
        assertEquals(30, f.height);
        assertArrayEquals(new int[]{0, 80, 180}, f.x);
        assertArrayEquals(new int[]{0, 0, 0}, f.y);
        // gap, then the arrow's middle
        assertArrayEquals(new int[]{70, 170}, f.arrowX);
        assertArrayEquals(new int[]{15, 15}, f.arrowY);
    }

    @Test
    public void aBoxThatDoesNotFitWithItsArrowStartsARow() {
        // 100 + 4 + 12 + 4 = 120, + 100 + 16 = 236 > 230: the second box wraps; the third needs no arrow and fits
        final PictureLayout.Flow f = PictureLayout.flow(new int[]{100, 100, 100}, new int[]{30, 30, 30}, 12, 4, 6, 230);
        assertEquals(2, f.rows);
        assertArrayEquals(new int[]{0, 0, 120}, f.x);
        assertArrayEquals(new int[]{0, 36, 36}, f.y);
        assertArrayEquals(new int[]{0, 1, 1}, f.row);
        // the arrow stays on the row of the box it follows
        assertArrayEquals(new int[]{110, 110}, f.arrowX);
        assertArrayEquals(new int[]{15, 51}, f.arrowY);
        assertEquals(66, f.height);
        final PictureLayout.Flow narrow = PictureLayout.flow(new int[]{100, 100, 100}, new int[]{30, 30, 30}, 12, 4, 6, 130);
        assertEquals(3, narrow.rows);
        assertArrayEquals(new int[]{0, 36, 72}, narrow.y);
        assertEquals(102, narrow.height);
        // the last box needs no arrow after it: it fits where one with an arrow would not
        final PictureLayout.Flow last = PictureLayout.flow(new int[]{100, 100}, new int[]{30, 30}, 12, 4, 6, 220);
        assertEquals(1, last.rows);
        assertArrayEquals(new int[]{0, 120}, last.x);
    }

    @Test
    public void boxesOfARowAreCentredOnItsTallest() {
        final PictureLayout.Flow f = PictureLayout.flow(new int[]{50, 50, 50}, new int[]{30, 50, 30}, 12, 4, 6, 140);
        assertArrayEquals(new int[]{0, 0, 1}, f.row);
        assertArrayEquals(new int[]{10, 0, 56}, f.y);
        assertArrayEquals(new int[]{25, 25}, f.arrowY);
        assertEquals(86, f.height);
    }

    @Test
    public void noBoxesOrOneBox() {
        final PictureLayout.Flow none = PictureLayout.flow(new int[0], new int[0], 12, 4, 6, 300);
        assertEquals(0, none.rows);
        assertEquals(0, none.height);
        final PictureLayout.Flow one = PictureLayout.flow(new int[]{500}, new int[]{30}, 12, 4, 6, 300);
        assertEquals(1, one.rows);
        assertEquals(0, one.arrowX.length);
        assertEquals(284, PictureLayout.maxBoxWidth(300, 12, 4));
    }

    @Test
    public void fourNodesStandWhereTheCanvasDrawsThem() {
        final PictureLayout.Loop loop = PictureLayout.loop(4, 358, 344, 136, 60, 4, 9);
        assertEquals(179, loop.cx[0], 0.01);
        assertEquals(30, loop.cy[0], 0.01);
        assertEquals(290, loop.cx[1], 0.01);
        assertEquals(172, loop.cy[1], 0.01);
        assertEquals(179, loop.cx[2], 0.01);
        assertEquals(314, loop.cy[2], 0.01);
        assertEquals(68, loop.cx[3], 0.01);
        assertEquals(172, loop.cy[3], 0.01);
        assertFalse(loop.crowded(8));
    }

    @Test
    public void arcsRunFromEachBoxToTheNextAndEndInAnArrowhead() {
        for (int n = 2; n <= Picture.MAX_NODES; n++) {
            final PictureLayout.Loop loop = PictureLayout.fitLoop(n, 358, 1);
            assertFalse("n=" + n, loop.crowded(8));
            for (int i = 0; i < n; i++) {
                final int next = (i + 1) % n;
                final float[] arc = loop.arcs[i];
                final float[] head = loop.heads[i];
                assertTrue("n=" + n, arc.length >= 4);
                assertEquals(6, head.length);
                // starts beside its own box, the tip stops beside the next
                assertTrue(outside(loop, i, arc[0], arc[1], 3.9f));
                assertFalse(outside(loop, i, arc[0], arc[1], 12));
                assertTrue(outside(loop, next, head[0], head[1], 3.9f));
                assertFalse(outside(loop, next, head[0], head[1], 12));
                // the head is as long as asked and its base is centred on the line's end
                final float endX = arc[arc.length - 2];
                final float endY = arc[arc.length - 1];
                assertEquals(9, Math.hypot(head[0] - endX, head[1] - endY), 1.5);
                assertEquals(endX, (head[2] + head[4]) / 2, 0.01);
                assertEquals(endY, (head[3] + head[5]) / 2, 0.01);
                for (int p = 0; p < arc.length; p += 2) {
                    assertTrue(arc[p] >= 0 && arc[p] <= loop.width);
                    assertTrue(arc[p + 1] >= 0 && arc[p + 1] <= loop.height);
                    // no arc crosses a box
                    for (int b = 0; b < n; b++) {
                        assertTrue("n=" + n + " arc " + i + " box " + b, outside(loop, b, arc[p], arc[p + 1], 0));
                    }
                }
            }
        }
    }

    private static boolean outside(PictureLayout.Loop loop, int node, float x, float y, float by) {
        return Math.abs(x - loop.cx[node]) >= loop.boxWidth / 2 + by || Math.abs(y - loop.cy[node]) >= loop.boxHeight / 2 + by;
    }

    @Test
    public void aLoopFitsNarrowAndWideScreens() {
        for (float dp : new float[]{1f, 2.75f}) {
            for (int width : new int[]{288, 328, 358, 411, 600, 900}) {
                for (int n = 2; n <= Picture.MAX_NODES; n++) {
                    final PictureLayout.Loop loop = PictureLayout.fitLoop(n, width * dp, dp);
                    final String at = "n=" + n + " width=" + width;
                    assertFalse(at, loop.crowded(8 * dp));
                    assertTrue(at, loop.boxWidth <= 136 * dp + 0.01 && loop.boxWidth >= 88 * dp - 0.01);
                    assertEquals(at, PictureLayout.loopHeight(n, dp), loop.height, 0.01);
                    for (int i = 0; i < n; i++) {
                        assertTrue(at, loop.cx[i] - loop.boxWidth / 2 >= -0.01 && loop.cx[i] + loop.boxWidth / 2 <= loop.width + 0.01);
                        assertTrue(at, loop.cy[i] - loop.boxHeight / 2 >= -0.01 && loop.cy[i] + loop.boxHeight / 2 <= loop.height + 0.01);
                    }
                    // the mark in the middle (a 56 dp circle) is clear of every box
                    assertFalse(at, loop.coversCentre(28 * dp, 28 * dp, 28 * dp));
                    if (width >= 320) {
                        // and so are the words under it
                        assertFalse(at, loop.coversCentre(48 * dp, -36 * dp, 68 * dp));
                    }
                }
            }
        }
    }

    @Test
    public void aBoxInTheMiddleCoversTheMark() {
        // two boxes side by side in a short loop reach under the mark
        final PictureLayout.Loop wide = PictureLayout.loop(4, 300, 300, 140, 60, 4, 9);
        assertTrue(wide.coversCentre(28, 28, 28));
        final PictureLayout.Loop narrow = PictureLayout.loop(4, 300, 300, 80, 60, 4, 9);
        assertFalse(narrow.coversCentre(28, 28, 28));
        // the bottom box is below the words, the side boxes beside them
        assertFalse(narrow.coversCentre(48, -36, 68));
        assertTrue(narrow.coversCentre(48, -36, 100));
    }

    @Test
    public void aLoopOfNoneOrOneHasNoArcs() {
        assertEquals(0, PictureLayout.loop(0, 300, 300, 100, 60, 4, 9).n);
        final PictureLayout.Loop one = PictureLayout.loop(1, 300, 300, 100, 60, 4, 9);
        assertEquals(0, one.arcs[0].length);
        assertEquals(0, one.heads[0].length);
    }

    @Test
    public void barsAreSharesOfTheLongest() {
        assertArrayEquals(new float[]{0.02f, 1f}, PictureLayout.bars(new double[]{4, 200}), 0.0001f);
        assertArrayEquals(new float[]{1f, 0.5f, 0f}, PictureLayout.bars(new double[]{10, 5, 0}), 0.0001f);
        assertArrayEquals(new float[]{0f, 0f}, PictureLayout.bars(new double[]{0, 0}), 0f);
        assertArrayEquals(new float[]{0f, 1f}, PictureLayout.bars(new double[]{Double.NaN, 3}), 0f);
        assertEquals(0, PictureLayout.bars(new double[0]).length);
    }

    @Test
    public void aWideTableScrollsSideways() {
        // three columns and the row names share 358
        assertEquals(89, PictureLayout.tableColumn(3, 358, 84));
        assertFalse(PictureLayout.tableScrolls(3, 358, 84));
        // six do not fit at 84 each
        assertEquals(84, PictureLayout.tableColumn(6, 358, 84));
        assertTrue(PictureLayout.tableScrolls(6, 358, 84));
    }
}
