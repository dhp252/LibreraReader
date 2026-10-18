package com.foobnix.eye;

/**
 * Every threshold of Eye mode (F-010) in one place. The values are first guesses from the design;
 * the owner's traces tune them (design "Assumptions").
 */
public final class EyeRules {

    /** No one looking at the screen this long is "away" (or "can't see" in the dark). */
    public static final long AWAY_MS = 2_000L;
    /** Looking again this long is "sees you" again. */
    public static final long BACK_MS = 500L;
    /** Away this long dims the screen. */
    public static final long DIM_AFTER_MS = 30_000L;
    /** Window brightness while dimmed (0 turns some screens off). */
    public static final float DIM_BRIGHTNESS = 0.01f;

    /** Mean frame luminance (0..1) below which the room is too dark to tell "away" from "can't see". */
    public static final float DARK_LUMA = 0.08f;
    /** ...and above which it is bright again (hysteresis, so the state does not flicker). */
    public static final float BRIGHT_LUMA = 0.11f;
    /** Weight of a new frame in the smoothed luminance. */
    public static final float LUMA_SMOOTH = 0.3f;

    /** A face turned further than this from the camera is not looking at the screen. */
    public static final float LOOK_YAW = 40f;
    public static final float LOOK_PITCH = 40f;
    /** Blendshape score (eyeBlinkLeft/Right) from which an eye counts as closed. */
    public static final float CLOSED = 0.6f;

    /** Frames analysed per second while only the presence features are on. */
    public static final int PRESENCE_FPS = 5;
    /** Requested analysis frame size (the camera picks the closest it has). */
    public static final int FRAME_W = 640;
    public static final int FRAME_H = 480;
    /** Face detector and tracker confidences: a little under MediaPipe's 0.5 for dim rooms. */
    public static final float CONFIDENCE = 0.4f;

    /** Width of the camera preview of "Show what the camera sees", in pixels. */
    public static final int PREVIEW_W = 160;
    /** Face traces kept on the device; older ones are deleted. */
    public static final int TRACES_KEPT = 5;

    private EyeRules() {
    }
}
