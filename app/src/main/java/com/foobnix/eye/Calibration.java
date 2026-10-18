package com.foobnix.eye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A calibration run: the reader follows a dot through {@link #SPOTS} (or one centre dot for a
 * re-zero); for each the median gaze of the face frames after it settled (a blink is a few frames
 * the median does not see). A full run then asks the reader to close the eyes until a tick
 * (D-106): with the bottom dots that gives their own {@link EyeProfile}. Pure: the view asks what
 * to show, Eye mode feeds the frames.
 */
public final class Calibration {

    public enum Result {RUNNING, OK, POOR, NO_FACE, TOO_DARK, CANCELLED}

    /** What the view shows at a time. */
    public enum Phase {DOT, PROMPT, CLOSE, DONE}

    /** Screen fractions (x, y): the centre column top to bottom, then left and right of the middle. */
    static final float[][] SPOTS = {
            {0.5f, 0.08f}, {0.5f, 0.29f}, {0.5f, 0.5f}, {0.5f, 0.71f}, {0.5f, 0.92f},
            {0.12f, 0.5f}, {0.88f, 0.5f}};
    static final float[][] CENTRE = {{0.5f, 0.5f}};
    /** The dot whose eyes are the bottom gaze. */
    static final int LOW_DOT = 4;

    private final float[][] spots;
    private final long settleMs;
    private final long collectMs;
    private final long start;
    private final boolean closeStep;
    /** Frames of the current dot: gaze across, up/down, eye aspect ratio left, right. */
    private final List<float[]> current = new ArrayList<>();
    /** Per good dot: screen x, y, then the medians of {@link #current}'s columns, then the spot index. */
    private final List<float[]> dots = new ArrayList<>();
    private final List<float[]> closedFrames = new ArrayList<>();
    private int index;
    private Result result = Result.RUNNING;
    private GazeModel model;
    private EyeProfile profile;
    private int darkFrames;
    private int frames;

    private Calibration(float[][] spots, long settleMs, long collectMs, long start, boolean closeStep) {
        this.spots = spots;
        this.settleMs = settleMs;
        this.collectMs = collectMs;
        this.start = start;
        this.closeStep = closeStep;
    }

    /** The first dot shows {@link EyeRules#CAL_LEAD_MS} longer (its time starts that much later). */
    public static Calibration full(long start) {
        return new Calibration(SPOTS, EyeRules.CAL_SETTLE_MS, EyeRules.CAL_COLLECT_MS, start + EyeRules.CAL_LEAD_MS, true);
    }

    public static Calibration rezero(long start) {
        return new Calibration(CENTRE, EyeRules.REZERO_SETTLE_MS, EyeRules.REZERO_COLLECT_MS, start, false);
    }

    public boolean isRezero() {
        return spots == CENTRE;
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

    private long promptEnd() {
        return dotsEnd() + EyeRules.CAL_CLOSE_PROMPT_MS;
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
        if (t < promptEnd()) {
            return Phase.PROMPT;
        }
        return t < closeEnd() ? Phase.CLOSE : Phase.DONE;
    }

    /** The dot to show at {@code t}, null outside the dots. */
    public float[] spotAt(long t) {
        if (phase(t) != Phase.DOT) {
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
        if (!f.face) {
            return result;
        }
        if (f.t < dotsEnd()) {
            final long in = (f.t - start) % (settleMs + collectMs);
            if (in >= settleMs) {
                current.add(new float[]{ReadingPose.gazeH(f), ReadingPose.gazeV(f), f.earL, f.earR});
            }
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
            dots.add(new float[]{spots[index][0], spots[index][1], median(current, 0), median(current, 1),
                    median(current, 2), median(current, 3), index});
        }
        current.clear();
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
        model = GazeModel.fit(dots);
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
        float[] low = null;
        final List<float[]> open = new ArrayList<>();
        for (float[] d : dots) {
            if ((int) d[6] == LOW_DOT) {
                low = d;
            } else if (d[1] <= 0.5f) {
                open.add(new float[]{d[4], d[5]});
            }
        }
        if (low == null || open.isEmpty()) {
            return null;
        }
        return new EyeProfile(median(open, 0), median(open, 1), low[4], low[5],
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

    /** The re-zero's gaze {across, up/down}, null when it failed. */
    public float[] rezeroGaze() {
        return isRezero() && !dots.isEmpty() ? new float[]{dots.get(0)[2], dots.get(0)[3]} : null;
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
