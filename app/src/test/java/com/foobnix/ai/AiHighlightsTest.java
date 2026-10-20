package com.foobnix.ai;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class AiHighlightsTest {

    private static final float E = 1e-6f;

    @Test
    public void aParagraphGetsOneBarLeftOfItsLeftmostLine() {
        // an indented first line, then two full lines
        final List<float[]> bars = AiHighlights.bars(new float[][]{
                {0.15f, 0.10f, 0.90f, 0.12f}, {0.10f, 0.12f, 0.90f, 0.14f}, {0.10f, 0.14f, 0.50f, 0.16f}});
        assertEquals(1, bars.size());
        final float[] bar = bars.get(0);
        assertEquals(0.10f - AiHighlights.BAR_GAP, bar[2], E);
        assertEquals(bar[2] - AiHighlights.BAR_WIDTH, bar[0], E);
        assertEquals(0.10f, bar[1], E);
        assertEquals(0.16f, bar[3], E);
    }

    @Test
    public void aParagraphRunningIntoTheNextColumnGetsABarThere() {
        // two lines at the foot of the left column, one at the top of the right
        final List<float[]> bars = AiHighlights.bars(new float[][]{
                {0.08f, 0.90f, 0.45f, 0.92f}, {0.08f, 0.92f, 0.45f, 0.94f}, {0.55f, 0.05f, 0.92f, 0.07f}});
        assertEquals(2, bars.size());
        assertEquals(0.08f - AiHighlights.BAR_GAP, bars.get(0)[2], E);
        assertEquals(0.94f, bars.get(0)[3], E);
        assertEquals(0.55f - AiHighlights.BAR_GAP, bars.get(1)[2], E);
        assertEquals(0.05f, bars.get(1)[1], E);
    }

    @Test
    public void aBarStaysOnThePageWhenTheTextStartsAtItsEdge() {
        final float[] bar = AiHighlights.bars(new float[][]{{0.0f, 0.1f, 0.9f, 0.2f}}).get(0);
        assertEquals(0f, bar[0], E);
        assertEquals(AiHighlights.BAR_WIDTH, bar[2], E);
    }
}
