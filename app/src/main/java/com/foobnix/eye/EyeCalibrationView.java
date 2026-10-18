package com.foobnix.eye;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.foobnix.pdf.info.R;

/**
 * The calibration screen (design 5.1): a dark full-screen layer over the reader with one dot at a
 * time, shrinking while its frames count, and "Follow the dot". A re-zero is the same with one
 * centre dot. Cancel at the bottom. Eye mode feeds the frames to the {@link Calibration}; this
 * view only draws it and moves it on with the clock.
 */
@SuppressLint("ViewConstructor")
final class EyeCalibrationView extends View {

    interface Done {
        void done(Calibration c);
    }

    private final Calibration calibration;
    private final Done done;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final String hint;
    private final String closePrompt;
    private final String cancel;
    private boolean finished;
    private Calibration.Phase shown = Calibration.Phase.DOT;

    EyeCalibrationView(Context c, Calibration calibration, Done done) {
        super(c);
        this.calibration = calibration;
        this.done = done;
        density = c.getResources().getDisplayMetrics().density;
        dot.setColor(0xFFFFC107);
        text.setColor(0xCCFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(16 * density);
        hint = c.getString(calibration.isRezero() ? R.string.eye_look_dot : R.string.eye_follow_dot);
        closePrompt = c.getString(R.string.eye_close_prompt);
        cancel = c.getString(android.R.string.cancel);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.argb(240, 0, 0, 0));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // the keys come here first: back cancels (instead of closing the book), volume does not turn pages
        requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            calibration.cancel();
            finish();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final long now = SystemClock.uptimeMillis();
        final Calibration.Phase before = shown;
        if (calibration.tick(now) != Calibration.Result.RUNNING) {
            if (before == Calibration.Phase.CLOSE) {
                // the eyes are shut: the tick tells them to open
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            finish();
            return;
        }
        final int w = getWidth();
        final int h = getHeight();
        shown = calibration.phase(now);
        if (shown == Calibration.Phase.PROMPT || shown == Calibration.Phase.CLOSE) {
            canvas.drawText(closePrompt, w / 2f, h * 0.5f, text);
            canvas.drawText(cancel, w / 2f, h - 32 * density, text);
            postInvalidateOnAnimation();
            return;
        }
        final float[] spot = calibration.spotAt(now);
        if (spot != null) {
            final float r = density * (14 - 8 * calibration.progress(now));
            canvas.drawCircle(spot[0] * w, spot[1] * h, r, dot);
        }
        final boolean spotLow = spot != null && spot[1] > 0.4f && spot[1] < 0.6f && spot[0] == 0.5f;
        canvas.drawText(hint, w / 2f, h * (spotLow ? 0.4f : 0.5f), text);
        canvas.drawText(cancel, w / 2f, h - 32 * density, text);
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && e.getY() > getHeight() - 80 * density) {
            calibration.cancel();
            finish();
        }
        return true;
    }

    void finish() {
        if (finished) {
            return;
        }
        finished = true;
        post(() -> done.done(calibration));
    }
}
