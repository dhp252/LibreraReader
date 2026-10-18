package com.foobnix.eye;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The gaze side of an Eye mode session (M3): the calibration of the current display rotation
 * ({@link GazeModel}, kept in {@link AppSP#eyeCalibration}, D-91), the smoothed screen point,
 * book mode's {@link BottomTopTurn} with the re-zero from page turns, and scroll mode's
 * {@link ScrollZones}. Eye mode turns its answers into actions.
 */
final class EyeGaze {

    private int rotation = -1;
    private GazeModel model;
    private final BottomTopTurn turn = new BottomTopTurn();
    private final ScrollZones zones = new ScrollZones();
    private float sx = Float.NaN;
    private float sy = Float.NaN;
    private long lastT = -1;
    // re-zero from a page turn: the gaze y in the first moments of the page
    private long pageAt = -1;
    private final List<float[]> pageStart = new ArrayList<>();
    private boolean nudged = true;

    /** The display rotation now (0..3); a change loads that rotation's calibration. @return whether it changed */
    boolean rotation(int r) {
        if (r == rotation) {
            return false;
        }
        rotation = r;
        model = load(r);
        sx = Float.NaN;
        sy = Float.NaN;
        turn.pageShown(-1, -1);
        zones.update(Float.NaN, 0);
        return true;
    }

    /** The reader's eyes from this rotation's calibration, null without. */
    EyeProfile profile() {
        return model == null ? null : model.eyes;
    }

    int rotation() {
        return rotation;
    }

    boolean hasModel() {
        return model != null && model.isGood();
    }

    GazeModel model() {
        return model;
    }

    void setModel(GazeModel m) {
        model = m;
        save(rotation, m);
    }

    /** Saves the model after a re-zero or a nudge. */
    void saveModel() {
        save(rotation, model);
    }

    /**
     * The smoothed screen point of the frame's gaze: {x, y}, NaN when there is no good model or
     * the frame cannot say ({@code usable} false: no face, eyes closed, the reader not looking).
     */
    float[] predict(FaceFrame f, boolean usable) {
        final long dt = lastT < 0 ? 0 : f.t - lastT;
        lastT = f.t;
        if (!hasModel() || !usable) {
            sx = Float.NaN;
            sy = Float.NaN;
            return new float[]{sx, sy};
        }
        final float x = model.x(ReadingPose.gazeH(f));
        final float y = model.y(ReadingPose.gazeV(f));
        if (Float.isNaN(sy)) {
            sx = x;
            sy = y;
        } else {
            final float a = EyeRules.alpha(EyeRules.POSE_SMOOTH, dt);
            sx += a * (x - sx);
            sy += a * (y - sy);
        }
        return new float[]{sx, sy};
    }

    float x() {
        return sx;
    }

    float y() {
        return sy;
    }

    /** Book mode: a page came at {@code t}. */
    void pageShown(long t, long expectedMs) {
        turn.pageShown(t, expectedMs);
        pageAt = t;
        pageStart.clear();
        nudged = false;
    }

    /** Book mode: whether the last lines then the top say "next page" now. */
    boolean bookTurn(long t, Boolean secondPage) {
        return turn.update(t, sy, sx, secondPage);
    }

    boolean isArmed() {
        return turn.isArmed();
    }

    /**
     * Book mode re-zero from page turns (D-90): 0.3–1.5 s after a page came the reader is on its
     * first lines; their gaze nudges the offset a step toward them.
     * @return whether the offset moved (to save it)
     */
    boolean nudge(long t, boolean looking) {
        if (nudged || pageAt < 0 || !hasModel()) {
            return false;
        }
        final long in = t - pageAt;
        if (in >= EyeRules.NUDGE_FROM_MS && in <= EyeRules.NUDGE_TO_MS && looking && !Float.isNaN(sy)) {
            pageStart.add(new float[]{sy});
        }
        if (in <= EyeRules.NUDGE_TO_MS) {
            return false;
        }
        nudged = true;
        if (pageStart.size() < EyeRules.CAL_MIN_FRAMES) {
            return false;
        }
        final float y = Calibration.median(pageStart, 0);
        if (y > 0.5f) {
            // not on the first lines (looked elsewhere, a picture page): nothing to learn
            return false;
        }
        model.nudgeY(EyeRules.NUDGE_STEP * (EyeRules.NUDGE_TARGET_Y - y));
        return true;
    }

    /** Scroll mode with auto-scroll running: the speed factor for the gaze now. */
    float scrollFactor(long dtMs) {
        return zones.update(sy, dtMs);
    }

    ScrollZones.Zone zone() {
        return zones.zone();
    }

    // ---------------------------------------------------------------- store (per rotation)

    static GazeModel load(int rotation) {
        try {
            final String s = AppSP.get().eyeCalibration;
            if (s == null || s.isEmpty()) {
                return null;
            }
            return GazeModel.fromJson(new JSONObject(s).optJSONObject("r" + rotation));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    static void save(int rotation, GazeModel m) {
        try {
            final String s = AppSP.get().eyeCalibration;
            final JSONObject all = s == null || s.isEmpty() ? new JSONObject() : new JSONObject(s);
            if (m == null) {
                all.remove("r" + rotation);
            } else {
                all.put("r" + rotation, m.toJson());
            }
            AppSP.get().eyeCalibration = all.toString();
            AppSP.get().save();
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
