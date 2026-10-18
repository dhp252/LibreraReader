package com.foobnix.eye;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The reader's own eyes (D-106), learned in the calibration: the eye aspect ratio per eye looking
 * at the top half (open), at the bottom of the screen (low: the lids follow the eyes down and,
 * with small eyes, nearly close), and with the eyes shut (closed). A closure is then a ratio
 * well under the reader's own bottom gaze, not under one limit for everybody. Pure.
 */
public final class EyeProfile {

    final float openL;
    final float openR;
    final float lowL;
    final float lowR;
    final float closedL;
    final float closedR;

    public EyeProfile(float openL, float openR, float lowL, float lowR, float closedL, float closedR) {
        this.openL = openL;
        this.openR = openR;
        this.lowL = lowL;
        this.lowR = lowR;
        this.closedL = closedL;
        this.closedR = closedR;
    }

    /**
     * Whether closed eyes and the bottom gaze differ enough to tell apart: by at least
     * {@link EyeRules#EYE_SEPARATION_MIN} and {@link EyeRules#EYE_SEPARATION_SHARE} of the bottom
     * gaze's ratio, on average over both eyes.
     */
    public boolean separable() {
        final float low = (lowL + lowR) / 2;
        final float closed = (closedL + closedR) / 2;
        return closed < low && low - closed >= Math.max(EyeRules.EYE_SEPARATION_MIN, EyeRules.EYE_SEPARATION_SHARE * low);
    }

    /**
     * A closure starts when the mean aspect ratio of both eyes is under this, {@code going} = it
     * goes on under the higher one. The mean, not each eye: in the owner's tablet trace one eye's
     * bottom gaze and closure were only 0.035 apart, and a real long blink would have been cut
     * into short ones.
     */
    public float limit(boolean going) {
        final float low = (lowL + lowR) / 2;
        final float closed = (closedL + closedR) / 2;
        return closed + (going ? EyeRules.EYE_KEEP_AT : EyeRules.EYE_START_AT) * (low - closed);
    }

    public JSONObject toJson() {
        try {
            final JSONObject o = new JSONObject();
            o.put("openL", openL);
            o.put("openR", openR);
            o.put("lowL", lowL);
            o.put("lowR", lowR);
            o.put("closedL", closedL);
            o.put("closedR", closedR);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null when missing or damaged */
    public static EyeProfile fromJson(JSONObject o) {
        if (o == null || !o.has("closedL")) {
            return null;
        }
        return new EyeProfile((float) o.optDouble("openL", 0), (float) o.optDouble("openR", 0),
                (float) o.optDouble("lowL", 0), (float) o.optDouble("lowR", 0),
                (float) o.optDouble("closedL", 0), (float) o.optDouble("closedR", 0));
    }
}
