package com.foobnix.eye;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What one analysed camera frame says about the reader's face: the numbers the detectors use and
 * a face trace records (one JSON line per frame, short keys). No image data: the landmarks kept
 * for the camera preview are never written.
 */
public final class FaceFrame {

    /** Uptime of the frame, ms. */
    public long t;
    public boolean face;
    /** Mean luminance of the frame, 0 (black) .. 1 (white). */
    public float luma;
    /** Size of the analysed frame once upright, and the rotation that made it upright. */
    public int w;
    public int h;
    public int rot;

    // the rest only when a face was found

    /** Blendshape scores 0..1: eyeBlinkLeft/Right, eyeLookDownLeft/Right. */
    public float blinkL;
    public float blinkR;
    public float downL;
    public float downR;
    /** Head pose in degrees ({@link HeadPose}). */
    public float yaw;
    public float pitch;
    public float roll;
    /** Iris position in each eye ({@link FaceSignals#iris}): along the eye 0..1, across it (+ = down). */
    public float irisLX;
    public float irisLY;
    public float irisRX;
    public float irisRY;
    /** Centre between the eyes (0..1 of the frame) and the distance between them (0..1 of its width). */
    public float faceX;
    public float faceY;
    public float faceSize;

    /** Landmarks 0..1 of the upright frame, only for the camera preview; never written to a trace. */
    public transient float[] lx;
    public transient float[] ly;

    public boolean eyesClosed() {
        return face && blinkL >= EyeRules.CLOSED && blinkR >= EyeRules.CLOSED;
    }

    /** @param state the presence state to record with the frame, or null */
    public String toJson(String state) {
        try {
            final JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("f", face ? 1 : 0);
            o.put("l", round(luma));
            o.put("w", w);
            o.put("h", h);
            o.put("r", rot);
            if (face) {
                o.put("bl", round(blinkL));
                o.put("br", round(blinkR));
                o.put("dl", round(downL));
                o.put("dr", round(downR));
                o.put("y", round(yaw));
                o.put("p", round(pitch));
                o.put("ro", round(roll));
                o.put("ilx", round(irisLX));
                o.put("ily", round(irisLY));
                o.put("irx", round(irisRX));
                o.put("iry", round(irisRY));
                o.put("fx", round(faceX));
                o.put("fy", round(faceY));
                o.put("fs", round(faceSize));
            }
            if (state != null) {
                o.put("st", state);
            }
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null for a line that is not a frame (a header, a reader event, damaged) */
    public static FaceFrame fromJson(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        try {
            final JSONObject o = new JSONObject(line);
            if (!o.has("t") || !o.has("f")) {
                return null;
            }
            final FaceFrame f = new FaceFrame();
            f.t = o.getLong("t");
            f.face = o.getInt("f") == 1;
            f.luma = (float) o.optDouble("l", 0);
            f.w = o.optInt("w", 0);
            f.h = o.optInt("h", 0);
            f.rot = o.optInt("r", 0);
            if (f.face) {
                f.blinkL = (float) o.optDouble("bl", 0);
                f.blinkR = (float) o.optDouble("br", 0);
                f.downL = (float) o.optDouble("dl", 0);
                f.downR = (float) o.optDouble("dr", 0);
                f.yaw = (float) o.optDouble("y", 0);
                f.pitch = (float) o.optDouble("p", 0);
                f.roll = (float) o.optDouble("ro", 0);
                f.irisLX = (float) o.optDouble("ilx", 0);
                f.irisLY = (float) o.optDouble("ily", 0);
                f.irisRX = (float) o.optDouble("irx", 0);
                f.irisRY = (float) o.optDouble("iry", 0);
                f.faceX = (float) o.optDouble("fx", 0);
                f.faceY = (float) o.optDouble("fy", 0);
                f.faceSize = (float) o.optDouble("fs", 0);
            }
            return f;
        } catch (JSONException e) {
            return null;
        }
    }

    private static double round(float v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
