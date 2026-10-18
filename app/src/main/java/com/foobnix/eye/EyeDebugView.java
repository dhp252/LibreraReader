package com.foobnix.eye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * "Show what the camera sees": a corner overlay with the analysed frame (mirrored, as a mirror
 * shows a face), the landmarks MediaPipe found, the presence state and the numbers behind it, a
 * strip of the last {@link #HISTORY_MS} of states and what Eye mode last did, so that after
 * looking away and back the reader can see what it made of it. Only on this screen; nothing is
 * kept.
 */
final class EyeDebugView extends View {

    static final long HISTORY_MS = 30_000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mirror = new Matrix();
    private final float density;
    /** {time, state ordinal} of recent frames, oldest first. */
    private final ArrayDeque<long[]> history = new ArrayDeque<>();

    private Bitmap frame;
    private FaceFrame face;
    private String[] lines = new String[0];

    EyeDebugView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        text.setColor(Color.WHITE);
        text.setTextSize(9.5f * density);
        text.setShadowLayer(2 * density, 0, 0, Color.BLACK);
        setClickable(false);
        setFocusable(false);
    }

    /** Main thread. */
    void show(FaceFrame f, Bitmap preview, PresenceDetector.State state, float luma, boolean dark, boolean canTell,
              float fps, String lastAction, long actionAgeMs) {
        if (preview != null) {
            frame = preview;
        }
        face = f;
        history.addLast(new long[]{f.t, state.ordinal()});
        while (!history.isEmpty() && f.t - history.peekFirst()[0] > HISTORY_MS) {
            history.removeFirst();
        }
        lines = new String[]{
                state.name() + (dark ? (canTell ? " · dim, can tell" : " · too dark") : " · lit"),
                String.format(Locale.US, "luma %.3f (%.3f) · %.1f fps", f.luma, luma, fps),
                f.face ? String.format(Locale.US, "yaw %.0f pitch %.0f roll %.0f", f.yaw, f.pitch, f.roll) : "no face",
                f.face ? String.format(Locale.US, "blink %.2f %.2f · side %.2f up %.2f", f.blinkL, f.blinkR, f.eyesSide, f.eyesUp) : "",
                lastAction == null ? "no action yet" : String.format(Locale.US, "%s · %ds ago", lastAction, actionAgeMs / 1000),
        };
        invalidate();
    }

    private static int color(int state) {
        switch (PresenceDetector.State.values()[state]) {
            case SEES:
                return 0xFF00E676;
            case AWAY:
                return 0xFFFFA000;
            default:
                return 0xFF9E9E9E;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        float y = 0;
        paint.setColor(0x99000000);
        canvas.drawRect(0, 0, w, getHeight(), paint);
        if (frame != null && !frame.isRecycled()) {
            final float scale = w / (float) frame.getWidth();
            final float h = frame.getHeight() * scale;
            mirror.reset();
            mirror.postScale(-scale, scale);
            mirror.postTranslate(w, 0);
            canvas.drawBitmap(frame, mirror, null);
            if (face != null && face.face && face.lx != null) {
                paint.setColor(0xFF00E676);
                final float r = Math.max(1, density * 0.8f);
                for (int i = 0; i < face.lx.length; i++) {
                    canvas.drawCircle(w - face.lx[i] * w, face.ly[i] * h, r, paint);
                }
            }
            y = h;
        }

        // the last 30 s of states, newest on the right: green sees you, orange away, grey can't see
        final float barH = 6 * density;
        if (face != null && !history.isEmpty()) {
            final long now = face.t;
            long[] prev = null;
            for (long[] e : history) {
                if (prev != null) {
                    final float x0 = w - (now - prev[0]) * w / (float) HISTORY_MS;
                    final float x1 = w - (now - e[0]) * w / (float) HISTORY_MS;
                    paint.setColor(color((int) prev[1]));
                    canvas.drawRect(Math.max(0, x0), y + 2 * density, x1, y + 2 * density + barH, paint);
                }
                prev = e;
            }
        }
        y += barH + 2 * density;

        final float lineH = text.getTextSize() * 1.25f;
        for (String line : lines) {
            y += lineH;
            canvas.drawText(line, 4 * density, y - lineH * 0.25f, text);
        }
    }
}
