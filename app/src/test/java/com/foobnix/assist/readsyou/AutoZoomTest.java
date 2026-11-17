package com.foobnix.assist.readsyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.zoom.ZoomData;

import org.junit.Test;

import java.util.List;

public class AutoZoomTest {

    /** 300 ms a word: a page of 200 words is expected to take 60 s. */
    private static final double PACE = 300;

    /** A chapter of 10 pages in 4 sections, starting on its pages 0, 2, 5 and 8. */
    private static ZoomData zoom() {
        final ZoomData z = new ZoomData();
        z.pages = 10;
        final int[] starts = {0, 2, 5, 8};
        for (int i = 0; i < starts.length; i++) {
            final ZoomData.Section s = new ZoomData.Section();
            s.title = "S" + i;
            s.line = "Line " + i + ".";
            s.page = starts[i];
            z.sections.add(s);
        }
        return z;
    }

    private static ReadsSignals signals(int[] seconds) {
        final ReadsSignals s = new ReadsSignals();
        s.pages = seconds.length;
        for (int p = 0; p < seconds.length; p++) {
            if (seconds[p] > 0) {
                s.at(p).seconds = seconds[p];
                s.at(p).words = 200;
            }
        }
        return s;
    }

    @Test
    public void fastIsOneLineNormalLeanSlowFull() {
        // section 0 (pages 0-1) at the pace, 1 (2-4) fast, 2 (5-7) slow, 3 (8-9) not read
        final ReadsSignals s = signals(new int[]{60, 55, 15, 20, 10, 120, 110, 100, 0, 0});
        final List<AutoZoom.Choice> c = AutoZoom.choose(zoom(), s, 10, PACE);
        assertEquals(4, c.size());
        assertEquals(AutoZoom.LEAN, c.get(0).level);
        assertFalse(c.get(0).changed());
        assertEquals(AutoZoom.LINE, c.get(1).level);
        assertEquals(AutoZoom.FAST, c.get(1).why);
        assertEquals(2, c.get(1).from);
        assertEquals(5, c.get(1).to);
        assertEquals(AutoZoom.FULL, c.get(2).level);
        assertEquals(AutoZoom.SLOW, c.get(2).why);
        // pages that were never timed say nothing: lean
        assertEquals(AutoZoom.LEAN, c.get(3).level);
        assertEquals(AutoZoom.NONE, c.get(3).why);
        assertEquals(2, AutoZoom.changes(c));
    }

    @Test
    public void aReReadOrALongPageOpensTheSection() {
        final ReadsSignals s = signals(new int[]{60, 60, 15, 15, 15, 60, 60, 60, 60, 60});
        s.at(3).rereads = 1;
        s.at(6).longs = 1;
        s.at(9).passes = 1;
        final List<AutoZoom.Choice> c = AutoZoom.choose(zoom(), s, 10, PACE);
        // read fast, but read again: opened, not squeezed
        assertEquals(AutoZoom.FULL, c.get(1).level);
        assertEquals(AutoZoom.REREAD, c.get(1).why);
        assertEquals(AutoZoom.FULL, c.get(2).level);
        assertEquals(AutoZoom.LONG, c.get(2).why);
        assertEquals(AutoZoom.REREAD, c.get(3).why);
    }

    @Test
    public void withoutAPaceOnlyReReadsAndLongPagesChangeASection() {
        final ReadsSignals s = signals(new int[]{5, 5, 5, 5, 5, 500, 500, 500, 5, 5});
        s.at(0).rereads = 1;
        final List<AutoZoom.Choice> c = AutoZoom.choose(zoom(), s, 10, 0);
        assertEquals(AutoZoom.FULL, c.get(0).level);
        assertEquals(AutoZoom.LEAN, c.get(1).level);
        assertEquals(AutoZoom.LEAN, c.get(2).level);
        assertEquals(1, AutoZoom.changes(c));
    }

    @Test
    public void theSectionsPagesFollowTheReadersLayout() {
        // the chapter has 20 pages now: twice as many as when the levels were made
        final int[] seconds = new int[20];
        for (int p = 0; p < 20; p++) {
            seconds[p] = p >= 4 && p < 10 ? 10 : 60;
        }
        final List<AutoZoom.Choice> c = AutoZoom.choose(zoom(), signals(seconds), 20, PACE);
        assertEquals(4, c.get(1).from);
        assertEquals(10, c.get(1).to);
        assertEquals(AutoZoom.LINE, c.get(1).level);
        assertEquals(AutoZoom.LEAN, c.get(0).level);
        assertEquals(AutoZoom.LEAN, c.get(2).level);
        assertEquals(20, c.get(3).to);
    }

    @Test
    public void aSectionWithoutAPlaceOrALineIsLeftLean() {
        final ZoomData z = zoom();
        z.sections.get(1).page = -1;
        z.sections.get(2).line = "";
        final ReadsSignals s = signals(new int[]{60, 60, 10, 10, 10, 10, 10, 10, 60, 60});
        final List<AutoZoom.Choice> c = AutoZoom.choose(z, s, 10, PACE);
        // unplaced: its pages are not known
        assertEquals(AutoZoom.LEAN, c.get(1).level);
        assertEquals(-1, c.get(1).from);
        // read fast, but it has no line to squeeze to
        assertEquals(AutoZoom.LEAN, c.get(2).level);
        // section 0 now runs up to section 2's start
        assertEquals(5, c.get(0).to);
        assertTrue(AutoZoom.choose(null, s, 10, PACE).isEmpty());
        assertEquals(AutoZoom.LEAN, AutoZoom.choose(z, null, 10, PACE).get(0).level);
    }

    @Test
    public void slowAndSkimRules() {
        assertTrue(ReadsRules.isSlow(100, 200, PACE));
        assertFalse(ReadsRules.isSlow(85, 200, PACE));
        assertFalse(ReadsRules.isSlow(100, 200, 0));
        assertFalse(ReadsRules.isSlow(100, 10, PACE));
    }
}
