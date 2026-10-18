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
    /**
     * A dark room is still "away" when a face was found at nearly this brightness: down to this
     * share of the luminance the face was last seen at, the camera can still tell (D-98).
     */
    public static final float SEEN_LUMA_RATIO = 0.6f;
    /** Below this the frame is black (camera covered or blocked): always "can't see". */
    public static final float BLACK_LUMA = 0.015f;
    /**
     * Degrees of gaze per unit of eye direction (eyeLook blendshapes), across and up/down
     * ({@link ReadingPose}, D-102): fitted on the owner's trace, where the eyes turned back as
     * the head turned while reading.
     */
    public static final float EYE_DEG_H = 27f;
    public static final float EYE_DEG_V = 40f;
    /**
     * Looking away = the gaze this many degrees off the reading one, across and up/down. In the
     * owner's trace reading stayed within 3.4° / 6.4° and look-aways were 8–31° off.
     */
    public static final float DEV_GAZE_H = 8f;
    public static final float DEV_GAZE_V = 12f;
    /** Face frames taken as reading when Eye mode starts or comes back. */
    public static final long POSE_LEARN_MS = 3_000L;
    /** How fast the pose follows the reader while they look (per frame). */
    public static final float POSE_FOLLOW = 0.03f;
    /** Weight of a new frame in the smoothed pose numbers. */
    public static final float POSE_SMOOTH = 0.5f;
    /** A touch re-learns the pose from a frame at most this old. */
    public static final long TOUCH_FRAME_MS = 600L;

    /** A face turned further than this from the camera is not looking at the screen. */
    public static final float LOOK_YAW = 40f;
    public static final float LOOK_PITCH = 40f;
    /** Blendshape score (eyeBlinkLeft/Right) from which an eye counts as closed. */
    public static final float CLOSED = 0.6f;

    /** Frames analysed per second while only the presence features are on. */
    public static final int PRESENCE_FPS = 5;
    /** ...and while long blink is on: a 0.6 s closure is 6 frames, a natural blink 1–3. */
    public static final int LONG_BLINK_FPS = 10;

    // long blink (M2, design 5.1 feature 3, D-103)
    /** eyeBlink of both eyes from which a frame may be closed. */
    public static final float BLINK_CLOSED = 0.5f;
    /** ...and before the open-eye ratio is known, from which it is. */
    public static final float BLINK_CLOSED_ALONE = 0.75f;
    /**
     * A closure starts when both eyes are at most this share of their open aspect ratio, and goes
     * on while they stay under {@link #EAR_CLOSED_KEEP} (D-105). In the owner's second trace
     * (tablet) reading the bottom lines narrowed the eyes to 0.38–0.43 of open, a real closure to
     * 0.17–0.27.
     */
    public static final float EAR_CLOSED = 0.30f;
    public static final float EAR_CLOSED_KEEP = 0.42f;
    /** Frames with eyeBlink under this, looking, teach the open aspect ratio. */
    public static final float BLINK_OPEN = 0.4f;
    /** Open frames needed before the open aspect ratio is trusted. */
    public static final int EAR_LEARN_FRAMES = 10;
    /** A closure this long, ended by opening the eyes, turns the page; longer is resting the eyes. */
    public static final long LONG_BLINK_MIN_MS = 600L;
    public static final long LONG_BLINK_MAX_MS = 2_000L;
    /** One open frame inside a closure up to this long does not split it. */
    public static final long BLINK_GAP_MS = 150L;
    /** No second long blink this soon after one. */
    public static final long BLINK_COOLDOWN_MS = 1_000L;

    // calibrated gaze (M3, D-90)
    /** Calibration dots: time to settle on a dot, then time its frames count. */
    public static final long CAL_SETTLE_MS = 700L;
    /** The first dot shows this much longer: the eyes take up to 0.8 s to find it. */
    public static final long CAL_LEAD_MS = 600L;
    /** The closing step (D-106): read "close your eyes", close them, frames, then a tick. */
    public static final long CAL_CLOSE_PROMPT_MS = 1_800L;
    public static final long CAL_CLOSE_SETTLE_MS = 600L;
    public static final long CAL_CLOSE_COLLECT_MS = 1_500L;
    /** Closed eyes and the bottom gaze must differ this much (aspect ratio, and share of the bottom's). */
    public static final float EYE_SEPARATION_MIN = 0.012f;
    public static final float EYE_SEPARATION_SHARE = 0.25f;
    /** With the reader's profile: a closure starts this far from closed toward the bottom gaze, goes on to this. */
    public static final float EYE_START_AT = 0.4f;
    public static final float EYE_KEEP_AT = 0.7f;
    public static final long CAL_COLLECT_MS = 900L;
    public static final long REZERO_SETTLE_MS = 400L;
    public static final long REZERO_COLLECT_MS = 800L;
    /** Frames a dot needs to count, and dots a calibration needs. */
    public static final int CAL_MIN_FRAMES = 4;
    public static final int CAL_MIN_DOTS = 5;
    /** A good calibration: the dots fit the line to this share of the screen ... */
    public static final float CAL_RMS = 0.12f;
    /** ... and the gaze moved at least this many degrees between the top and bottom dots. */
    public static final float CAL_MIN_SPREAD = 4f;
    /** Book mode: screen y of the bottom zone (the last lines) and of the top zone. */
    public static final float BOTTOM_ZONE = 0.72f;
    public static final float TOP_ZONE = 0.35f;
    /** Resting on the last lines this long arms the turn; the jump up must come this soon after. */
    public static final long BOTTOM_DWELL_MS = 1_000L;
    public static final long JUMP_MS = 800L;
    /** The page's expected time is unknown (no WPM, no words): the guard is this. */
    public static final long TURN_GUARD_FALLBACK_MS = 5_000L;
    /** Re-zero from page turns: gaze y after a page is shown, the first lines' y, step, bound. */
    public static final long NUDGE_FROM_MS = 300L;
    public static final long NUDGE_TO_MS = 1_500L;
    public static final float NUDGE_TARGET_Y = 0.12f;
    public static final float NUDGE_STEP = 0.15f;
    public static final float NUDGE_MAX = 0.3f;
    /** Scroll mode zones (enter / leave) and the speed factor's steps. */
    public static final float SCROLL_TOP_ENTER = 0.25f;
    public static final float SCROLL_TOP_LEAVE = 0.30f;
    public static final float SCROLL_BOTTOM_ENTER = 0.75f;
    public static final float SCROLL_BOTTOM_LEAVE = 0.70f;
    public static final float SCROLL_RAMP_PER_S = 0.1f;
    public static final float SCROLL_EASE_PER_S = 0.5f;
    public static final float SCROLL_MAX_FACTOR = 2f;
    /** The gaze must stay in the top zone this long before the scroll waits (a glance up re-reads). */
    public static final long SCROLL_TOP_DWELL_MS = 600L;

    /**
     * A smoothing weight given per frame at {@link #PRESENCE_FPS}, for a frame {@code dtMs} after
     * the last: the same smoothing in time at any frame rate.
     */
    public static float alpha(float perFrameAt5fps, long dtMs) {
        if (dtMs <= 0) {
            return perFrameAt5fps;
        }
        return (float) (1 - Math.pow(1 - perFrameAt5fps, dtMs / (1000.0 / PRESENCE_FPS)));
    }
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
