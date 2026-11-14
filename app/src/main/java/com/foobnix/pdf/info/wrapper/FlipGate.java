package com.foobnix.pdf.info.wrapper;

/**
 * When "Portrait (auto)" turns the screen over (no Android types, tested on the JVM). The display
 * turns a few hundred ms after the request, and the sensor goes on reporting meanwhile: a reading
 * taken against the old rotation would ask to turn back at once. So after a request the readings
 * wait until the display has turned (or {@link #SETTLE_MS} passed), and the device must be held
 * upside down for {@link #HOLD_MS} before a turn is asked: a quick tilt through 180° does nothing.
 */
final class FlipGate {

    /** How far (degrees) from exactly upside down the device may be held and still flip. */
    static final int FLIP_TOLERANCE = 40;
    /** The longest wait for the display to turn after a request. */
    static final long SETTLE_MS = 1500;
    /** How long the device must stay upside down before the screen turns. */
    static final long HOLD_MS = 300;

    private static final int ORIENTATION_UNKNOWN = -1;

    /** The display rotation when the last turn was asked, -1 when none is pending. */
    private int pendingFrom = -1;
    private long pendingAt;
    private long upsideDownSince = -1;

    /**
     * Whether a device held at {@code degrees} (clockwise from its natural orientation, as
     * OrientationEventListener reports) is upside down to a screen at {@code displayRotation}
     * (a {@code Surface.ROTATION_*} value).
     */
    static boolean isUpsideDown(int degrees, int displayRotation) {
        if (degrees == ORIENTATION_UNKNOWN) {
            return false;
        }
        int upright = (360 - displayRotation * 90) % 360;
        int distance = Math.abs(degrees - (upright + 180) % 360);
        return Math.min(distance, 360 - distance) <= FLIP_TOLERANCE;
    }

    /** @return true when the screen should be turned over now */
    boolean onSensor(long now, int degrees, int displayRotation) {
        if (pendingFrom >= 0) {
            if (displayRotation == pendingFrom && now - pendingAt < SETTLE_MS) {
                // the display has not turned yet: this reading is against the old rotation
                return false;
            }
            pendingFrom = -1;
            upsideDownSince = -1;
        }
        if (!isUpsideDown(degrees, displayRotation)) {
            upsideDownSince = -1;
            return false;
        }
        if (upsideDownSince < 0) {
            upsideDownSince = now;
        }
        if (now - upsideDownSince < HOLD_MS) {
            return false;
        }
        pendingFrom = displayRotation;
        pendingAt = now;
        upsideDownSince = -1;
        return true;
    }

    /** Forgets what it was waiting for (the screen left the foreground). */
    void reset() {
        pendingFrom = -1;
        upsideDownSince = -1;
    }
}
