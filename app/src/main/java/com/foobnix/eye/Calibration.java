package com.foobnix.eye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A calibration run: the reader follows a dot through {@link #SPOTS} (or one centre dot for a
 * re-zero); for each the eye signals of the face frames after it settled, which
 * {@link GazeModel#fit} turns into the gaze (medians: a blink is a few frames they do not see). A
 * full run then asks the reader to close the eyes until a tick (D-106): with the bottom row that
 * gives their own {@link EyeProfile}. Pure: the view asks what to show, Eye mode feeds the frames.
 */
public final class Calibration {

    public enum Result {RUNNING, OK, POOR, NO_FACE, TOO_DARK, CANCELLED}

    /** What the view shows at a time: the dots, the head step (D-134), the closing step. */
    public enum Phase {DOT, HEAD, PROMPT, CLOSE, DONE}

    /**
     * Screen fractions (x, y): a 3 × 3 grid, row by row in a snake (each dot next to the last), so
     * both axes see the eyes at three places along the other (D-131).
     */
    static final float[][] SPOTS = {
            {0.12f, 0.08f}, {0.5f, 0.08f}, {0.88f, 0.08f},
            {0.88f, 0.5f}, {0.5f, 0.5f}, {0.12f, 0.5f},
            {0.12f, 0.92f}, {0.5f, 0.92f}, {0.88f, 0.92f}};
    static final float[][] CENTRE = {{0.5f, 0.5f}};
    /** Dots from this screen y on are the bottom gaze (the eyes' own profile). */
    static final float LOW_Y = 0.9f;

    private final float[][] spots;
    private final long settleMs;
    private final long collectMs;
    private final long start;
    private final boolean closeStep;
    private final GazeGeometry geometry;
    /**
     * Frames of the current dot: {@link GazeModel#features}, {@link GazeGeometry#head}, and eye
     * aspect ratio left, right.
     */
    private final List<float[]> current = new ArrayList<>();
    private final List<float[]> currentHeads = new ArrayList<>();
    private final List<float[]> currentEars = new ArrayList<>();
    /** Good dots, and per good dot its screen y and median eye aspect ratio left, right. */
    private final List<GazeModel.Dot> dots = new ArrayList<>();
    private final List<float[]> ears = new ArrayList<>();
    private final List<float[]> closedFrames = new ArrayList<>();
    /** The head step's frames: {@link GazeModel#features}, {@link GazeGeometry#head}. */
    private final List<float[]> headFrames = new ArrayList<>();
    private final List<float[]> headHeads = new ArrayList<>();
    private int index;
    private Result result = Result.RUNNING;
    private GazeModel model;
    private EyeProfile profile;
    private int darkFrames;
    private int frames;

    private Calibration(float[][] spots, long settleMs, long collectMs, long start, boolean closeStep, GazeGeometry geometry) {
        this.spots = spots;
        this.settleMs = settleMs;
        this.collectMs = collectMs;
        this.start = start;
        this.closeStep = closeStep;
        this.geometry = geometry;
    }

    /** The first dot shows {@link EyeRules#CAL_LEAD_MS} longer (its time starts that much later). */
    public static Calibration full(long start, GazeGeometry geometry) {
        return new Calibration(SPOTS, EyeRules.CAL_SETTLE_MS, EyeRules.CAL_COLLECT_MS, start + EyeRules.CAL_LEAD_MS, true, geometry);
    }

    public static Calibration rezero(long start, GazeGeometry geometry) {
        return new Calibration(CENTRE, EyeRules.REZERO_SETTLE_MS, EyeRules.REZERO_COLLECT_MS, start, false, geometry);
    }

    public boolean isRezero() {
        return spots == CENTRE;
    }

    public int dotCount() {
        return spots.length;
    }

    public long settleMs() {
        return settleMs;
    }

    public long collectMs() {
        return collectMs;
    }

    /** When the first dot's own time starts (after the lead-in). */
    public long start() {
        return start;
    }

    private long dotsEnd() {
        return start + spots.length * (settleMs + collectMs);
    }

    private long headEnd() {
        return dotsEnd() + EyeRules.CAL_HEAD_SETTLE_MS + EyeRules.CAL_HEAD_COLLECT_MS;
    }

    private long promptEnd() {
        return headEnd() + EyeRules.CAL_CLOSE_PROMPT_MS;
    }

    private long closeEnd() {
        return promptEnd() + EyeRules.CAL_CLOSE_SETTLE_MS + EyeRules.CAL_CLOSE_COLLECT_MS;
    }

    public Phase phase(long t) {
        if (result != Result.RUNNING) {
            return Phase.DONE;
        }
        if (t < dotsEnd()) {
            return Phase.DOT;
        }
        if (!closeStep) {
            return Phase.DONE;
        }
        if (t < headEnd()) {
            return Phase.HEAD;
        }
        if (t < promptEnd()) {
            return Phase.PROMPT;
        }
        return t < closeEnd() ? Phase.CLOSE : Phase.DONE;
    }

    /** The dot to show at {@code t} (the head step's in the middle), null outside the dots. */
    public float[] spotAt(long t) {
        final Phase p = phase(t);
        if (p == Phase.HEAD) {
            return CENTRE[0];
        }
        if (p != Phase.DOT) {
            return null;
        }
        final int i = t < start ? 0 : (int) ((t - start) / (settleMs + collectMs));
        return i < spots.length ? spots[i] : null;
    }

    /** How far the current dot is in its time, 0..1 (the view shrinks it). */
    public float progress(long t) {
        if (t < start) {
            return 0;
        }
        if (phase(t) == Phase.HEAD) {
            return Math.min(1, (t - dotsEnd()) / (float) (headEnd() - dotsEnd()));
        }
        final long per = settleMs + collectMs;
        return Math.max(0, Math.min(1, ((t - start) % per) / (float) per));
    }

    /** A frame; {@code dark} = the room is too dark to tell (presence). @return the result so far */
    public Result frame(FaceFrame f, boolean dark) {
        if (tick(f.t) != Result.RUNNING || f.t < start) {
            return result;
        }
        frames++;
        if (dark) {
            darkFrames++;
        }
        final float[] head = GazeGeometry.head(f);
        if (head == null) {
            return result;
        }
        if (f.t < dotsEnd()) {
            final long in = (f.t - start) % (settleMs + collectMs);
            if (in >= settleMs) {
                current.add(GazeModel.features(f));
                currentHeads.add(head);
                currentEars.add(new float[]{f.earL, f.earR});
            }
        } else if (f.t >= dotsEnd() + EyeRules.CAL_HEAD_SETTLE_MS && f.t < headEnd()) {
            headFrames.add(GazeModel.features(f));
            headHeads.add(head);
        } else if (f.t >= promptEnd() + EyeRules.CAL_CLOSE_SETTLE_MS && f.t < closeEnd()) {
            closedFrames.add(new float[]{f.earL, f.earR});
        }
        return result;
    }

    /** Moves on with the time alone (frames may stop coming): closes the dots and steps whose time is over. */
    public Result tick(long t) {
        if (result != Result.RUNNING || t < start) {
            return result;
        }
        final int i = (int) ((t - start) / (settleMs + collectMs));
        while (index < i && index < spots.length) {
            closeDot();
        }
        if (index >= spots.length && (!closeStep || t >= closeEnd())) {
            finish();
        }
        return result;
    }

    /** Ends the run early (the reader cancelled). */
    public void cancel() {
        result = Result.CANCELLED;
    }

    private void closeDot() {
        if (current.size() >= EyeRules.CAL_MIN_FRAMES) {
            dots.add(new GazeModel.Dot(spots[index][0], spots[index][1], current, currentHeads, geometry));
            ears.add(new float[]{spots[index][1], median(currentEars, 0), median(currentEars, 1)});
        }
        current.clear();
        currentHeads.clear();
        currentEars.clear();
        index++;
    }

    private void finish() {
        if (isRezero()) {
            result = dots.isEmpty() ? (darkFrames * 2 > frames ? Result.TOO_DARK : Result.NO_FACE) : Result.OK;
            return;
        }
        if (dots.size() < EyeRules.CAL_MIN_DOTS) {
            result = darkFrames * 2 > frames ? Result.TOO_DARK : Result.NO_FACE;
            return;
        }
        model = GazeModel.fit(dots, headFrames.size() >= EyeRules.CAL_MIN_FRAMES
                ? new GazeModel.Dot(CENTRE[0][0], CENTRE[0][1], headFrames, headHeads, geometry) : null);
        if (model != null) {
            profile = eyes();
            model.eyes = profile;
        }
        result = model == null ? Result.POOR : model.isGood() ? Result.OK : Result.POOR;
    }

    /** The reader's eyes from the dots and the closing step, null when a part is missing. */
    private EyeProfile eyes() {
        if (closedFrames.size() < EyeRules.CAL_MIN_FRAMES) {
            return null;
        }
        final List<float[]> low = new ArrayList<>();
        final List<float[]> open = new ArrayList<>();
        for (float[] e : ears) {
            if (e[0] >= LOW_Y) {
                low.add(e);
            } else if (e[0] <= 0.5f) {
                open.add(e);
            }
        }
        if (low.isEmpty() || open.isEmpty()) {
            return null;
        }
        return new EyeProfile(median(open, 1), median(open, 2), median(low, 1), median(low, 2),
                median(closedFrames, 0), median(closedFrames, 1));
    }

    public Result result() {
        return result;
    }

    /** The fitted model (full run), null when none could be fitted. */
    public GazeModel model() {
        return model;
    }

    /** The reader's eyes (full run), null when the closing step or the bottom dot failed. */
    public EyeProfile profile() {
        return profile;
    }

    /** The re-zero's dot (its eye signals and head), null when it failed. */
    public GazeModel.Dot rezeroDot() {
        return isRezero() && !dots.isEmpty() ? dots.get(0) : null;
    }

    public GazeGeometry geometry() {
        return geometry;
    }

    public int goodDots() {
        return dots.size();
    }

    static float median(List<float[]> values, int column) {
        final float[] v = new float[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(i)[column];
        }
        Arrays.sort(v);
        return v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2;
    }
}
