package com.foobnix.crop;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CropPlanTest {

    static final float E = 1e-4f;
    static final float P = CropPlan.PAD;

    /** A page's measure: its body, and a head / foot line at these heights (or none). */
    static CropFinder.Measure page(float l, float t, float r, float b, float[] head, float[] foot) {
        float[] all = {l, t, r, b};
        if (head != null) {
            all = CropPlan.union(all, head);
        }
        if (foot != null) {
            all = CropPlan.union(all, foot);
        }
        return new CropFinder.Measure(all, new float[]{l, t, r, b}, head, foot);
    }

    static float[] line(float top, float bottom) {
        return new float[]{0.45f, top, 0.55f, bottom};
    }

    static void assertBox(float[] expected, float[] box) {
        assertNotNull(box);
        assertArrayEquals(expected, box, 1e-3f);
    }

    /** Six pairs of pages: even ones on the left, odd ones on the right (a binding offset). */
    static List<CropPlan.Sample> book(boolean pageNumbers) {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final float[] foot = pageNumbers ? line(0.93f, 0.945f) : null;
            samples.add(new CropPlan.Sample(CropPlan.EVEN, page(0.10f, 0.12f, 0.80f, 0.86f, null, foot)));
            samples.add(new CropPlan.Sample(CropPlan.ODD, page(0.20f, 0.12f, 0.90f, 0.86f, null, foot)));
        }
        return samples;
    }

    @Test
    public void samplesArePairsSpreadOverTheBook() {
        final int[] pages = CropPlan.samplePages(300);
        assertEquals(12, pages.length);
        int odd = 0;
        for (int p : pages) {
            assertTrue(p >= 30 && p < 270);
            odd += p % 2;
        }
        assertEquals(6, odd);
        assertEquals(7, CropPlan.samplePages(7).length);
        assertEquals(0, CropPlan.samplePages(0).length);
    }

    @Test
    public void evenAndOddPagesKeepTheirPlaceAndShareASize() {
        final CropPlan plan = CropPlan.build(book(false), true);
        assertBox(new float[]{0.10f - P, 0.12f - P, 0.80f + P, 0.86f + P}, plan.planned(CropPlan.EVEN));
        assertBox(new float[]{0.20f - P, 0.12f - P, 0.90f + P, 0.86f + P}, plan.planned(CropPlan.ODD));
    }

    @Test
    public void pageNumbersAtOneHeightAreLeftOut() {
        final CropPlan plan = CropPlan.build(book(true), true);
        assertNotNull(plan.footBand);
        assertEquals(0.86f + P, plan.planned(CropPlan.EVEN)[3], E);
        // a page measured alone keeps its number out the same way
        final CropFinder.Measure own = page(0.10f, 0.12f, 0.80f, 0.86f, null, line(0.93f, 0.945f));
        assertEquals(0.86f + P, plan.box(CropPlan.EVEN, own, true)[3], E);
        // kept when asked to
        final CropPlan keep = CropPlan.build(book(true), false);
        assertEquals(0.945f + P, keep.planned(CropPlan.EVEN)[3], E);
    }

    @Test
    public void aChapterTitleIsNotARunningHeader() {
        final CropPlan plan = CropPlan.build(book(true), true);
        assertNull(plan.headBand);
        // a short line set apart at the head of a chapter's first page stays on screen
        final CropFinder.Measure chapter = page(0.10f, 0.20f, 0.80f, 0.86f, line(0.06f, 0.08f), null);
        assertEquals(0.06f - P, plan.box(CropPlan.EVEN, chapter, true)[1], E);
    }

    @Test
    public void runningHeadersRepeatingOnThePagesAreLeftOut() {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final float top = 0.05f + (i % 3) * 0.002f;
            samples.add(new CropPlan.Sample(i % 2, page(0.1f, 0.12f, 0.9f, 0.86f, line(top, top + 0.015f), null)));
        }
        final CropPlan plan = CropPlan.build(samples, true);
        assertNotNull(plan.headBand);
        assertEquals(0.12f - P, plan.planned(CropPlan.EVEN)[1], E);
        final CropFinder.Measure own = page(0.1f, 0.12f, 0.9f, 0.86f, line(0.051f, 0.066f), null);
        assertEquals(0.12f - P, plan.box(CropPlan.ODD, own, true)[1], E);
    }

    @Test
    public void oneFullPagePictureDoesNotUndoTheCrop() {
        final List<CropPlan.Sample> samples = book(false);
        samples.set(0, new CropPlan.Sample(CropPlan.EVEN, page(0, 0, 1, 1, null, null)));
        final CropPlan plan = CropPlan.build(samples, true);
        assertBox(new float[]{0.10f - P, 0.12f - P, 0.80f + P, 0.86f + P}, plan.planned(CropPlan.EVEN));
    }

    @Test
    public void aWideTableWidensItsOwnPageOnly() {
        final CropPlan plan = CropPlan.build(book(false), true);
        final CropFinder.Measure table = page(0.02f, 0.12f, 0.95f, 0.60f, null, null);
        assertBox(new float[]{0.02f - P, 0.12f - P, 0.95f + P, 0.86f + P}, plan.box(CropPlan.EVEN, table, true));
        // a short last page keeps the book's size
        final CropFinder.Measure shortPage = page(0.10f, 0.12f, 0.80f, 0.30f, null, null);
        assertBox(plan.planned(CropPlan.EVEN), plan.box(CropPlan.EVEN, shortPage, true));
        // and a blank one too
        assertBox(plan.planned(CropPlan.ODD), plan.box(CropPlan.ODD, null, true));
    }

    @Test
    public void closeEvenAndOddBoxesAreOne() {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            samples.add(new CropPlan.Sample(i % 2, page(0.10f + (i % 2) * 0.01f, 0.12f, 0.80f, 0.86f, null, null)));
        }
        final CropPlan plan = CropPlan.build(samples, true);
        assertBox(plan.planned(CropPlan.EVEN), plan.planned(CropPlan.ODD));
        assertEquals(0.10f - P, plan.planned(CropPlan.ODD)[0], E);
    }

    @Test
    public void unevenSizesAreMadeEqual() {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            samples.add(new CropPlan.Sample(CropPlan.EVEN, page(0.10f, 0.10f, 0.70f, 0.90f, null, null)));
            samples.add(new CropPlan.Sample(CropPlan.ODD, page(0.30f, 0.20f, 0.80f, 0.80f, null, null)));
        }
        final CropPlan plan = CropPlan.build(samples, true);
        final float[] even = plan.planned(CropPlan.EVEN), odd = plan.planned(CropPlan.ODD);
        assertEquals(even[2] - even[0], odd[2] - odd[0], E);
        assertEquals(even[3] - even[1], odd[3] - odd[1], E);
        // the odd box grew round its own centre
        assertEquals(0.55f, (odd[0] + odd[2]) / 2, E);
        assertEquals(0.50f, (odd[1] + odd[3]) / 2, E);
    }

    @Test
    public void halvesOfSplitPagesArePlannedApart() {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            samples.add(new CropPlan.Sample(CropPlan.LEFT_HALF, page(0.15f, 0.1f, 0.95f, 0.9f, null, null)));
            samples.add(new CropPlan.Sample(CropPlan.RIGHT_HALF, page(0.05f, 0.1f, 0.85f, 0.9f, null, null)));
        }
        final CropPlan plan = CropPlan.build(samples, true);
        assertEquals(0.15f - P, plan.planned(CropPlan.LEFT_HALF)[0], E);
        assertEquals(0.05f - P, plan.planned(CropPlan.RIGHT_HALF)[0], E);
        assertNull(plan.planned(CropPlan.EVEN));
    }

    @Test
    public void tooFewPagesMeansEachPageAlone() {
        final List<CropPlan.Sample> samples = new ArrayList<>();
        samples.add(new CropPlan.Sample(CropPlan.EVEN, page(0.1f, 0.1f, 0.9f, 0.9f, null, null)));
        samples.add(new CropPlan.Sample(CropPlan.ODD, null));
        samples.add(new CropPlan.Sample(CropPlan.EVEN, null));
        assertNull(CropPlan.build(samples, true));

        final CropFinder.Measure numbered = page(0.1f, 0.1f, 0.9f, 0.8f, null, line(0.93f, 0.945f));
        assertBox(new float[]{0.1f - P, 0.1f - P, 0.9f + P, 0.8f + P}, CropPlan.alone(numbered, true));
        assertEquals(0.945f + P, CropPlan.alone(numbered, false)[3], E);
        assertBox(new float[]{0, 0, 1, 1}, CropPlan.alone(null, true));
        // a few words are no reason to zoom in on them
        assertBox(new float[]{0, 0, 1, 1}, CropPlan.alone(page(0.4f, 0.4f, 0.5f, 0.45f, null, null), true));
    }

    @Test
    public void aPlanIsSavedAndReadBack() throws Exception {
        final CropPlan plan = CropPlan.build(book(true), true);
        final CropPlan back = CropPlan.fromJson(new JSONObject(plan.toJson().toString()));
        assertNotNull(back);
        assertBox(plan.planned(CropPlan.EVEN), back.planned(CropPlan.EVEN));
        assertBox(plan.planned(CropPlan.ODD), back.planned(CropPlan.ODD));
        assertNull(back.planned(CropPlan.LEFT_HALF));
        assertBox(plan.footBand, back.footBand);
        assertNull(back.headBand);

        final JSONObject old = plan.toJson();
        old.put("v", CropPlan.VERSION - 1);
        assertNull(CropPlan.fromJson(old));
    }
}
