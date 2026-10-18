package com.foobnix.eye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A calibration run: the reader follows a dot through {@link #SPOTS} (or one centre dot for a
 * re-zero); for each the median gaze of the open-eyed face frames after it settled. Pure: the
 * view asks where the dot is, Eye mode feeds the frames.
 */
public final class Calibration {

    public enum Result {RUNNING, OK, POOR, NO_FACE, TOO_DARK, CANCELLED}

    /** Screen fractions (x, y): the centre column top to bottom, then left and right of the middle. */
    static final float[][] SPOTS = {
            {0.5f, 0.08f}, {0.5f, 0.29f}, {0.5f, 0.5f}, {0.5f, 0.71f}, {0.5f, 0.92f},
            {0.12f, 0.5f}, {0.88f, 0.5f}};
    static final float[][] CENTRE = {{0.5f, 0.5f}};

    private final float[][] spots;
    private final long settleMs;
    private final long collectMs;
    private final long start;
    private final List<float[]> current = new ArrayList<>();
    private final List<float[]> dots = new ArrayList<>();
    private int index;
    private Result result = Result.RUNNING;
    private GazeModel model;
    private int darkFrames;
    private int frames;

    private Calibration(float[][] spots, long settleMs, long collectMs, long start) {
        this.spots = spots;
        this.settleMs = settleMs;
        this.collectMs = collectMs;
        this.start = start;
    }

    public static Calibration full(long start) {
        return new Calibration(SPOTS, EyeRules.CAL_SETTLE_MS, EyeRules.CAL_COLLECT_MS, start);
    }

    public static Calibration rezero(long start) {
        return new Calibration(CENTRE, EyeRules.REZERO_SETTLE_MS, EyeRules.REZERO_COLLECT_MS, start);
    }

    public boolean isRezero() {
        return spots == CENTRE;
    }

    /** The dot to show at {@code t}, null once done. */
    public float[] spotAt(long t) {
        final int i = (int) ((t - start) / (settleMs + collectMs));
        return i >= 0 && i < spots.length && result == Result.RUNNING ? spots[i] : null;
    }

    /** How far the current dot is in its time, 0..1 (the view shrinks it). */
    public float progress(long t) {
        final long per = settleMs + collectMs;
        return Math.max(0, Math.min(1, ((t - start) % per) / (float) per));
    }

    /** A frame; {@code dark} = the room is too dark to tell (presence). @return the result so far */
    public Result frame(FaceFrame f, boolean closed, boolean dark) {
        if (tick(f.t) != Result.RUNNING) {
            return result;
        }
        final long per = settleMs + collectMs;
        frames++;
        if (dark) {
            darkFrames++;
        }
        final long in = (f.t - start) % per;
        if (in >= settleMs && f.face && !closed) {
            current.add(new float[]{ReadingPose.gazeH(f), ReadingPose.gazeV(f)});
        }
        return result;
    }

    /** Moves on with the time alone (frames may stop coming): closes the dots whose time is over. */
    public Result tick(long t) {
        if (result != Result.RUNNING) {
            return result;
        }
        final int i = (int) ((t - start) / (settleMs + collectMs));
        while (index < i && index < spots.length) {
            closeDot();
        }
        if (index >= spots.length) {
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
            dots.add(new float[]{spots[index][0], spots[index][1], median(current, 0), median(current, 1)});
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
        result = model == null ? Result.POOR : model.isGood() ? Result.OK : Result.POOR;
    }

    public Result result() {
        return result;
    }

    /** The fitted model (full run), null when none could be fitted. */
    public GazeModel model() {
        return model;
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
