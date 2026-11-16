package com.foobnix.canvas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.pdf.info.R;

/**
 * The canvas's bottom strip under the page (52 dp, the surface colour, a hairline on top): an
 * optional icon (the eye, in the sensor colour while "reading you" is on), a JetBrains Mono 11 sp
 * status ("READING YOU · steady · 228 wpm") and on the right a 64 x 4 dp progress bar with the
 * percent ("38%"). Built in code; add it with {@link #layoutParams()} or any height of 52 dp.
 * Callers check {@link CanvasLook#isOn()} first.
 */
public class CanvasStatusStrip extends LinearLayout {

    private final ImageView icon;
    private final TextView status;
    private final Bar bar;
    private final TextView percent;
    private boolean sensor;
    private boolean ownIcon;

    public CanvasStatusStrip(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        setPadding(gutter, 0, gutter, 0);
        final LayerDrawable bg = new LayerDrawable(new Drawable[]{
                new ColorDrawable(CanvasLook.line()), new ColorDrawable(CanvasLook.surface())});
        bg.setLayerInset(1, 0, Math.max(1, CanvasLook.dp(1)), 0, 0);
        setBackground(bg);
        setMinimumHeight(CanvasLook.dp(CanvasLook.STRIP_DP));

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LayoutParams ilp = new LayoutParams(CanvasLook.dp(22), CanvasLook.dp(22));
        ilp.rightMargin = CanvasLook.dp(10);
        addView(icon, ilp);

        status = new TextView(c);
        status.setTypeface(CanvasLook.mono(c, 400));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        status.setLetterSpacing(0.03f);
        status.setTextColor(CanvasLook.ink2());
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        addView(status, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bar = new Bar(c);
        final LayoutParams blp = new LayoutParams(CanvasLook.dp(64), CanvasLook.dp(4));
        blp.leftMargin = CanvasLook.dp(10);
        addView(bar, blp);

        percent = new TextView(c);
        CanvasParts.text(percent, CanvasLook.TEXT_SMALL, 400);
        percent.setTextColor(CanvasLook.ink2());
        percent.setSingleLine(true);
        final LayoutParams plp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = CanvasLook.dp(8);
        addView(percent, plp);

        setSensor(false);
        setProgress(0);
    }

    /** Full width, 52 dp tall, for a LinearLayout or FrameLayout parent. */
    public static ViewGroup.LayoutParams layoutParams() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.STRIP_DP));
    }

    /** The status text (shown as given; pass it uppercase where the canvas does). */
    public void setStatus(String text) {
        status.setText(text);
    }

    /** 0..1; the bar and the rounded percent. */
    public void setProgress(float progress) {
        final float p = Float.isNaN(progress) ? 0 : Math.max(0, Math.min(1, progress));
        bar.setProgress(p);
        percent.setText(Math.round(p * 100) + "%");
        setContentDescription(status.getText() + " " + percent.getText());
    }

    /** Hides or shows the progress bar and percent (a strip with only a status). */
    public void setProgressVisible(boolean visible) {
        bar.setVisibility(visible ? View.VISIBLE : View.GONE);
        percent.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * "Reading you" on: the icon (the eye unless {@link #setIcon} gave another) in the sensor
     * colour. Off: no icon, or the caller's icon in the muted colour.
     */
    public void setSensor(boolean on) {
        sensor = on;
        if (on && !ownIcon) {
            icon.setImageResource(R.drawable.glyphicons_25_eye);
        }
        icon.setImageTintList(ColorStateList.valueOf(on ? CanvasLook.sensor() : CanvasLook.muted()));
        icon.setVisibility(on || ownIcon ? View.VISIBLE : View.GONE);
    }

    public boolean isSensor() {
        return sensor;
    }

    /** The icon at the start; null goes back to the eye shown only while the sensor is on. */
    public void setIcon(Drawable d) {
        ownIcon = d != null;
        icon.setImageDrawable(d);
        setSensor(sensor);
    }

    public TextView getStatusView() {
        return status;
    }

    /** The 64 x 4 dp bar: the line colour, filled in ink up to the progress. */
    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        Bar(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = getHeight();
            final float radius = h / 2f;
            r.set(0, 0, getWidth(), h);
            paint.setColor(CanvasLook.line());
            canvas.drawRoundRect(r, radius, radius, paint);
            if (progress > 0) {
                r.set(0, 0, Math.max(h, getWidth() * progress), h);
                paint.setColor(CanvasLook.ink());
                canvas.drawRoundRect(r, radius, radius, paint);
            }
        }
    }
}
