package com.foobnix.eye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;

import java.util.Locale;

/**
 * "Show what the camera sees": a corner overlay with the analysed frame (mirrored, as a mirror
 * shows a face), the landmarks MediaPipe found, the presence state and the numbers behind it.
 * Only on this screen; nothing is kept.
 */
final class EyeDebugView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mirror = new Matrix();
    private final float density;

    private Bitmap frame;
    private FaceFrame face;
    private String[] lines = new String[0];

    EyeDebugView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        text.setColor(Color.WHITE);
        text.setTextSize(10 * density);
        text.setShadowLayer(2 * density, 0, 0, Color.BLACK);
        setClickable(false);
        setFocusable(false);
    }

    /** Main thread. */
    void show(FaceFrame f, Bitmap preview, PresenceDetector.State state, float luma, boolean dark, float fps) {
        if (preview != null) {
            frame = preview;
        }
        face = f;
        lines = new String[]{
                state.name() + (dark ? " · dark" : " · lit"),
                String.format(Locale.US, "luma %.3f (%.3f) · %.1f fps", f.luma, luma, fps),
                f.face ? String.format(Locale.US, "yaw %.0f pitch %.0f roll %.0f", f.yaw, f.pitch, f.roll) : "no face",
                f.face ? String.format(Locale.US, "blink %.2f %.2f · down %.2f %.2f", f.blinkL, f.blinkR, f.downL, f.downR) : "",
                f.face ? String.format(Locale.US, "iris %.2f/%.2f %.2f/%.2f", f.irisLX, f.irisLY, f.irisRX, f.irisRY) : "",
        };
        invalidate();
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
        final float lineH = text.getTextSize() * 1.25f;
        for (String line : lines) {
            y += lineH;
            canvas.drawText(line, 4 * density, y - lineH * 0.25f, text);
        }
    }
}
