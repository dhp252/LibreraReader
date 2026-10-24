package com.foobnix.eye;

/**
 * The phone and the reader's head as the camera sees them (D-133): where the eyes are, how the
 * head is turned, and where a gaze lands on the screen. The calibration learns only where the eyes
 * point inside the head ({@link GazeModel}); this turns that into a screen point for the head of
 * the moment, so a nod, a tilt or a phone moved a little is not read as the eyes moving.
 * <p>
 * Camera space: x to the right of the upright analysed frame, y down, z from the camera to the
 * reader, in mm. The frame is not mirrored, so its right is the screen's left as the reader sees
 * it. Angles in degrees: yaw turns toward the frame's right, pitch down (as {@link HeadPose} and
 * the eye scores agree in the owner's trace, D-102); tilt is the line between the eyes in the
 * frame, + when the eye on the frame's right is lower. Pure.
 */
public final class GazeGeometry {

    /** Distance between the eyes' centres, mm (an adult's average): the frame's scale. */
    static final float IPD_MM = 63f;
    /** A camera whose focal length is not known: this share of the frame's long side, in pixels. */
    static final float FOCAL_SHARE = 0.78f;
    /** The camera above the screen's edge in the device's natural top bezel, mm. */
    static final float CAMERA_GAP_MM = 5f;

    /** Indexes of {@link #head}. */
    static final int EX = 0, EY = 1, Z = 2, YAW = 3, PITCH = 4, TILT = 5, HEAD = 6;

    /** The screen as shown now, mm. */
    final float wMm;
    final float hMm;
    /** The camera on the screen's plane, mm from the screen's top left, x right, y down as the reader sees it. */
    final float camX;
    final float camY;

    GazeGeometry(float wMm, float hMm, float camX, float camY) {
        this.wMm = wMm;
        this.hMm = hMm;
        this.camX = camX;
        this.camY = camY;
    }

    /**
     * The screen of this size shown at this display rotation (0..3): the camera sits beyond the
     * edge that is the device's natural top (the middle of it; a few mm off only shifts the whole
     * map, which the calibration takes up).
     */
    public static GazeGeometry of(float wMm, float hMm, int rotation) {
        switch (rotation) {
            case 1:
                // turned a quarter to the left: the natural top is on the left
                return new GazeGeometry(wMm, hMm, -CAMERA_GAP_MM, hMm / 2);
            case 2:
                return new GazeGeometry(wMm, hMm, wMm / 2, hMm + CAMERA_GAP_MM);
            case 3:
                return new GazeGeometry(wMm, hMm, wMm + CAMERA_GAP_MM, hMm / 2);
            default:
                return new GazeGeometry(wMm, hMm, wMm / 2, -CAMERA_GAP_MM);
        }
    }

    /**
     * The screen's size in mm from its pixels and dpi. Some devices report a made-up dpi (160):
     * one far from the density's (its bucket is within a third of the real one), or a screen that
     * would be under 3 or over 20 inches across, uses the density's dpi instead.
     */
    public static float[] screenMm(int wPx, int hPx, float xdpi, float ydpi, int densityDpi) {
        float dpi = (xdpi + ydpi) / 2;
        final double inches = dpi > 0 ? Math.hypot(wPx, hPx) / dpi : 0;
        if (inches < 3 || inches > 20 || (densityDpi > 0 && Math.abs(dpi - densityDpi) > densityDpi / 3f)) {
            dpi = densityDpi > 0 ? densityDpi : 160;
        }
        return new float[]{wPx / dpi * 25.4f, hPx / dpi * 25.4f};
    }

    /**
     * The head in a frame: {eye x, eye y, distance} (mm, the point between the eyes) and {yaw,
     * pitch, tilt}; null without the face's place and size.
     */
    public static float[] head(FaceFrame f) {
        if (!f.face || f.w <= 0 || f.h <= 0 || f.faceSize <= 0) {
            return null;
        }
        final float focal = f.focal > 0 ? f.focal : FOCAL_SHARE * Math.max(f.w, f.h);
        // a turned head shows its eyes closer together than they are
        final double eyesPx = f.faceSize * f.w / Math.max(0.5, Math.cos(Math.toRadians(f.yaw)));
        if (eyesPx < 1) {
            return null;
        }
        final double mmPerPx = IPD_MM / eyesPx;
        return new float[]{(float) ((f.faceX - 0.5) * f.w * mmPerPx), (float) ((f.faceY - 0.5) * f.h * mmPerPx),
                (float) (focal * mmPerPx), f.yaw, f.pitch, f.tilt};
    }

    /** The direction {across, down} (degrees, camera space) from the eyes of this head to screen fraction (x, y). */
    public float[] cameraAngles(float x, float y, float[] head) {
        final double px = camX - x * wMm;
        final double py = y * hMm - camY;
        return new float[]{(float) Math.toDegrees(Math.atan2(px - head[EX], head[Z])),
                (float) Math.toDegrees(Math.atan2(py - head[EY], head[Z]))};
    }

    /** Angles {across, down} inside a head tilted by {@code tilt} turned into camera space. */
    public static float[] untilt(float across, float down, float tilt) {
        final double t = Math.toRadians(tilt);
        final double c = Math.cos(t);
        final double s = Math.sin(t);
        return new float[]{(float) (across * c - down * s), (float) (across * s + down * c)};
    }

    /** The angles {across, down} the eyes need inside this head to look at screen fraction (x, y). */
    public float[] eyeAngles(float x, float y, float[] head) {
        final float[] cam = cameraAngles(x, y, head);
        final double tx = cam[0] - head[YAW];
        final double ty = cam[1] - head[PITCH];
        // into the head's own axes, turned by its tilt in the frame
        final double t = Math.toRadians(head[TILT]);
        final double c = Math.cos(t);
        final double s = Math.sin(t);
        return new float[]{(float) (tx * c + ty * s), (float) (-tx * s + ty * c)};
    }

    /** The screen fraction {x, y} the eyes look at with these angles inside this head. */
    public float[] point(float across, float down, float[] head) {
        final float[] eyes = untilt(across, down, head[TILT]);
        final double tx = head[YAW] + eyes[0];
        final double ty = head[PITCH] + eyes[1];
        final double px = head[EX] + head[Z] * Math.tan(Math.toRadians(clamp(tx)));
        final double py = head[EY] + head[Z] * Math.tan(Math.toRadians(clamp(ty)));
        return new float[]{(float) ((camX - px) / wMm), (float) ((py + camY) / hMm)};
    }

    /** Screen fraction per degree of gaze at this distance: {across, down}. */
    public float[] perDegree(float[] head) {
        final double mm = Math.toRadians(1) * head[Z];
        return new float[]{(float) (mm / wMm), (float) (mm / hMm)};
    }

    private static double clamp(double deg) {
        return Math.max(-80, Math.min(80, deg));
    }
}
