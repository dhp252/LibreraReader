package com.foobnix.crop;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The margins of a whole book, so every page is shown at the same size.
 * <p>
 * A dozen pages are measured ({@link #samplePages}); each kind of page ("slot": even and odd
 * pages, or the left and right halves of split pages) gets the widest box of its samples, the
 * most extreme one left out so a single full-page picture does not undo the crop. Even and odd
 * boxes get the same size, each keeping its own place on the page. A page then shows its slot's
 * box, widened by whatever of its own falls outside it (a wide table).
 * <p>
 * Head and foot lines (see {@link CropFinder.Measure}) are left out only where such lines sit
 * on the sampled pages too: running headers and page numbers repeat at one height, a chapter
 * title does not, so it is kept.
 * <p>
 * Pure; boxes are fractions of the measured slice, {left, top, right, bottom}.
 */
public final class CropPlan {

    /** Bump when the measuring changes, so plans saved by an older build are measured again. */
    public static final int VERSION = 1;

    public static final int EVEN = 0, ODD = 1, LEFT_HALF = 2, RIGHT_HALF = 3;
    static final int SLOTS = 4;

    static final int SAMPLES = 12;
    /** Fewer usable samples than this: no plan, each page alone. */
    static final int MIN_SAMPLES = 4;
    /** Room left round the content, per side. */
    static final float PAD = 0.015f;
    /** A kept box smaller than this either way is no crop at all. */
    static final float MIN_KEPT = 0.3f;
    /** Even and odd boxes closer than this on every side are one box. */
    static final float SAME = 0.02f;
    /** Head or foot lines of the samples within this of each other are the same line. */
    static final float BAND = 0.02f;
    /**
     * A page's padded content up to this much bigger than its slot's box still only moves the
     * box: it gives up a little of its {@link #PAD}, not the book's size.
     */
    static final float FIT = 0.005f;

    final float[][] slots = new float[SLOTS][];
    float[] headBand, footBand;

    /** A measured page: its slot and what was found (null when blank). */
    public static final class Sample {
        final int slot;
        final CropFinder.Measure measure;

        public Sample(int slot, CropFinder.Measure measure) {
            this.slot = slot;
            this.measure = measure;
        }
    }

    /** The pages to measure: pairs (an even and an odd page) spread over 10..90% of the book. */
    public static int[] samplePages(int pageCount) {
        if (pageCount <= SAMPLES) {
            final int[] all = new int[pageCount];
            for (int i = 0; i < pageCount; i++) {
                all[i] = i;
            }
            return all;
        }
        final int pairs = SAMPLES / 2;
        // the last pair's odd page still within the 90%
        final int lo = (int) (pageCount * 0.1f), hi = (int) (pageCount * 0.9f) - 2;
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            final int p = lo + Math.round((float) i * (hi - lo) / (pairs - 1));
            if (!pages.contains(p)) {
                pages.add(p);
            }
            if (p + 1 < pageCount && !pages.contains(p + 1)) {
                pages.add(p + 1);
            }
        }
        final int[] res = new int[pages.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = pages.get(i);
        }
        return res;
    }

    /** The plan of these samples, or null when too few pages hold anything. */
    public static CropPlan build(List<Sample> samples, boolean dropHeaders) {
        int usable = 0;
        for (Sample s : samples) {
            if (s.measure != null) {
                usable++;
            }
        }
        if (usable < MIN_SAMPLES) {
            return null;
        }
        final CropPlan plan = new CropPlan();
        if (dropHeaders) {
            plan.headBand = band(samples, true);
            plan.footBand = band(samples, false);
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            final List<float[]> boxes = new ArrayList<>();
            for (Sample s : samples) {
                if (s.slot == slot && s.measure != null) {
                    boxes.add(plan.content(s.measure, dropHeaders));
                }
            }
            if (!boxes.isEmpty()) {
                plan.slots[slot] = kept(pad(widest(boxes)));
            }
        }
        pair(plan.slots, EVEN, ODD);
        pair(plan.slots, LEFT_HALF, RIGHT_HALF);
        return plan;
    }

    /**
     * What a page shows: its slot's box, moved over its own content where that sticks out but
     * fits in the box's size (a scan shifted on the glass, a figure off to one side), so the page
     * keeps the book's size and zoom; widened only by content bigger than the box (a wide table).
     */
    public float[] box(int slot, CropFinder.Measure own, boolean dropHeaders) {
        final float[] planned = slots[slot];
        if (planned == null) {
            return alone(own, dropHeaders);
        }
        if (own == null) {
            return planned.clone();
        }
        final float[] c = pad(content(own, dropHeaders));
        final float[] box = planned.clone();
        cover(box, c, 0);
        cover(box, c, 1);
        return kept(box);
    }

    /** One axis ({@code a} 0 = across, 1 = down) of {@code box} made to hold {@code content}. */
    static void cover(float[] box, float[] content, int a) {
        final int lo = a, hi = a + 2;
        final float size = box[hi] - box[lo];
        if (content[lo] >= box[lo] && content[hi] <= box[hi]) {
            return;
        }
        if (content[hi] - content[lo] > size + FIT) {
            box[lo] = Math.min(box[lo], content[lo]);
            box[hi] = Math.max(box[hi], content[hi]);
        } else if (content[lo] < box[lo]) {
            box[lo] = content[lo];
            box[hi] = content[lo] + size;
        } else {
            box[hi] = content[hi];
            box[lo] = content[hi] - size;
        }
    }

    /** What a page shows with no plan: its own content, head and foot lines judged alone. */
    public static float[] alone(CropFinder.Measure own, boolean dropHeaders) {
        if (own == null) {
            return full();
        }
        return kept(pad(dropHeaders ? own.body : own.all));
    }

    /** The slot's box with no page measured (null when the plan has none for it). */
    public float[] planned(int slot) {
        return slots[slot] == null ? null : slots[slot].clone();
    }

    /** The content of a page: the body, plus a head or foot line that is not a running one. */
    float[] content(CropFinder.Measure m, boolean dropHeaders) {
        if (!dropHeaders) {
            return m.all;
        }
        float[] box = m.body.clone();
        if (m.head != null && !inBand(m.head, headBand)) {
            box = union(box, m.head);
        }
        if (m.foot != null && !inBand(m.foot, footBand)) {
            box = union(box, m.foot);
        }
        return box;
    }

    /**
     * The height where the samples' head (or foot) lines sit, when at least 2 of them, and 40%
     * of the pages holding anything, have one there: {top, bottom}, else null.
     */
    static float[] band(List<Sample> samples, boolean head) {
        final List<float[]> lines = new ArrayList<>();
        int pages = 0;
        for (Sample s : samples) {
            if (s.measure == null) {
                continue;
            }
            pages++;
            final float[] line = head ? s.measure.head : s.measure.foot;
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.size() < 2 || lines.size() < 0.4f * pages) {
            return null;
        }
        final float[] centres = new float[lines.size()];
        for (int i = 0; i < centres.length; i++) {
            centres[i] = (lines.get(i)[1] + lines.get(i)[3]) / 2;
        }
        final float[] sorted = centres.clone();
        Arrays.sort(sorted);
        final float median = sorted[sorted.length / 2];
        float top = 1, bottom = 0;
        int near = 0;
        for (int i = 0; i < centres.length; i++) {
            if (Math.abs(centres[i] - median) <= BAND) {
                near++;
                top = Math.min(top, lines.get(i)[1]);
                bottom = Math.max(bottom, lines.get(i)[3]);
            }
        }
        if (near < 2 || near < 0.4f * pages) {
            return null;
        }
        return new float[]{top - BAND / 2, bottom + BAND / 2};
    }

    static boolean inBand(float[] line, float[] band) {
        if (band == null) {
            return false;
        }
        final float centre = (line[1] + line[3]) / 2;
        return centre >= band[0] && centre <= band[1];
    }

    /** Per side the widest box but the most extreme one (from 3 boxes on). */
    static float[] widest(List<float[]> boxes) {
        final int n = boxes.size();
        final int skip = n >= 3 ? Math.max(1, n / 5) : 0;
        final float[] res = new float[4];
        for (int side = 0; side < 4; side++) {
            final float[] v = new float[n];
            for (int i = 0; i < n; i++) {
                v[i] = boxes.get(i)[side];
            }
            Arrays.sort(v);
            // left and top: the smallest is the widest; right and bottom: the largest
            res[side] = side < 2 ? v[skip] : v[n - 1 - skip];
        }
        return res;
    }

    /** Two slots of a pair: one box when close, else the same size, each in its own place. */
    static void pair(float[][] slots, int a, int b) {
        if (slots[a] == null && slots[b] == null) {
            return;
        }
        if (slots[a] == null) {
            slots[a] = slots[b].clone();
            return;
        }
        if (slots[b] == null) {
            slots[b] = slots[a].clone();
            return;
        }
        boolean close = true;
        for (int i = 0; i < 4; i++) {
            close &= Math.abs(slots[a][i] - slots[b][i]) <= SAME;
        }
        if (close) {
            final float[] u = union(slots[a], slots[b]);
            slots[a] = u;
            slots[b] = u.clone();
            return;
        }
        final float w = Math.max(slots[a][2] - slots[a][0], slots[b][2] - slots[b][0]);
        final float h = Math.max(slots[a][3] - slots[a][1], slots[b][3] - slots[b][1]);
        slots[a] = resize(slots[a], w, h);
        slots[b] = resize(slots[b], w, h);
    }

    /** The box at this size round its own centre, moved back inside the page if need be. */
    static float[] resize(float[] box, float w, float h) {
        final float cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2;
        float l = cx - w / 2, t = cy - h / 2;
        l = Math.max(0, Math.min(1 - w, l));
        t = Math.max(0, Math.min(1 - h, t));
        return new float[]{l, t, l + w, t + h};
    }

    static float[] union(float[] a, float[] b) {
        return new float[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    static float[] pad(float[] box) {
        return new float[]{Math.max(0, box[0] - PAD), Math.max(0, box[1] - PAD),
                Math.min(1, box[2] + PAD), Math.min(1, box[3] + PAD)};
    }

    /** No crop rather than a box too small to be the page's matter. */
    static float[] kept(float[] box) {
        if (box[2] - box[0] < MIN_KEPT || box[3] - box[1] < MIN_KEPT) {
            return full();
        }
        return box;
    }

    static float[] full() {
        return new float[]{0, 0, 1, 1};
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("v", VERSION);
        final JSONArray s = new JSONArray();
        for (float[] slot : slots) {
            s.put(slot == null ? JSONObject.NULL : array(slot));
        }
        o.put("slots", s);
        if (headBand != null) {
            o.put("head", array(headBand));
        }
        if (footBand != null) {
            o.put("foot", array(footBand));
        }
        return o;
    }

    /** A saved plan, or null when it is from another version or unreadable. */
    public static CropPlan fromJson(JSONObject o) {
        try {
            if (o.optInt("v") != VERSION) {
                return null;
            }
            final CropPlan plan = new CropPlan();
            final JSONArray s = o.getJSONArray("slots");
            for (int i = 0; i < SLOTS && i < s.length(); i++) {
                plan.slots[i] = s.isNull(i) ? null : floats(s.getJSONArray(i));
            }
            plan.headBand = o.has("head") ? floats(o.getJSONArray("head")) : null;
            plan.footBand = o.has("foot") ? floats(o.getJSONArray("foot")) : null;
            return plan;
        } catch (JSONException e) {
            return null;
        }
    }

    static JSONArray array(float[] v) throws JSONException {
        final JSONArray a = new JSONArray();
        for (float f : v) {
            a.put((double) f);
        }
        return a;
    }

    static float[] floats(JSONArray a) throws JSONException {
        final float[] v = new float[a.length()];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) a.getDouble(i);
        }
        return v;
    }
}
