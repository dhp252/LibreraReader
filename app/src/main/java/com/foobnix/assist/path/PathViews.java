package com.foobnix.assist.path;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/** The small views the path screens share, in the canvas tokens. */
final class PathViews {

    private PathViews() {
    }

    static int dp(int dp) {
        return CanvasLook.dp(dp);
    }

    /** A full-screen dialog in the app's day or night. */
    static Dialog fullScreen(Activity a) {
        final Dialog dialog = new Dialog(a, CanvasLook.isNight() ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    /** Shows {@code root} in {@code dialog}, clear of the bars and the keyboard. */
    static void show(Dialog dialog, View root) {
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.foobnix.android.utils.SafeArea.fit(dialog, root, CanvasLook.isNight());
        dialog.show();
    }

    /** The 56 dp bar: an icon button, a title over a subtitle, and room for the same width on the right. */
    static LinearLayout topBar(Context c, int icon, int iconDescription, Runnable onIcon, CharSequence title,
                               CharSequence subtitle) {
        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setMinimumHeight(dp(CanvasLook.BAR_DP));

        final ImageView back = icon(c, icon, CanvasLook.ink());
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription(c.getString(iconDescription));
        back.setBackground(CanvasParts.ripple(null));
        back.setOnClickListener(v -> onIcon.run());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout middle = new LinearLayout(c);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView name = new TextView(c);
        CanvasParts.text(name, CanvasLook.TEXT_TITLE, 600);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(title);
        middle.addView(name);
        if (!TextUtils.isEmpty(subtitle)) {
            final TextView sub = new TextView(c);
            CanvasParts.secondary(sub);
            sub.setGravity(Gravity.CENTER);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setText(subtitle);
            middle.addView(sub);
        }
        bar.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(c), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    static ImageView icon(Context c, int drawable, int tint) {
        final ImageView v = new ImageView(c);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    static TextView text(Context c, CharSequence text, float sp, int weight, int color) {
        final TextView t = new TextView(c);
        CanvasParts.text(t, sp, weight);
        t.setTextColor(color);
        t.setText(text);
        return t;
    }

    static TextView muted(Context c, CharSequence text) {
        final TextView t = new TextView(c);
        CanvasParts.secondary(t);
        t.setText(text);
        return t;
    }

    /** A JetBrains Mono label; its text is shown as given (the caller upper-cases what is not a title). */
    static TextView mono(Context c, CharSequence text, int color) {
        final TextView t = new TextView(c);
        t.setTypeface(CanvasLook.mono(c, 500));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_LABEL);
        t.setLetterSpacing(0.04f);
        t.setTextColor(color);
        t.setText(text);
        return t;
    }

    /** The filled accent button, 52 dp tall on the screens. */
    static TextView primary(Context c, CharSequence text) {
        final TextView b = new TextView(c);
        CanvasParts.text(b, CanvasLook.TEXT_BODY, 600);
        b.setTextColor(CanvasLook.onAccent());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(CanvasParts.primaryButton());
        b.setText(text);
        return b;
    }

    /** The outlined button. */
    static TextView secondary(Context c, CharSequence text) {
        final TextView b = new TextView(c);
        CanvasParts.text(b, 14, 600);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(CanvasParts.secondaryButton());
        b.setText(text);
        return b;
    }

    /** A text button in the accent colour. */
    static TextView link(Context c, CharSequence text) {
        final TextView b = new TextView(c);
        CanvasParts.text(b, 13, 600);
        b.setTextColor(CanvasLook.accent());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinHeight(CanvasLook.target());
        b.setBackground(CanvasParts.ripple(null));
        b.setText(text);
        return b;
    }

    static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.4f);
    }

    /** A one-line text field, 48 dp tall, outlined; {@code serif} sets the reader's own words in italics. */
    static EditText field(Context c, CharSequence hint, boolean serif) {
        final EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (serif) {
            e.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        } else {
            e.setTypeface(CanvasLook.ui(c, 400));
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, CanvasLook.TEXT_BODY);
        }
        e.setTextColor(CanvasLook.ink());
        e.setHintTextColor(CanvasLook.muted());
        e.setHint(hint);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setMinHeight(dp(48));
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setBackground(outline(CanvasLook.bg(), CanvasLook.lineStrong(), 1, dp(12)));
        return e;
    }

    static void hideKeyboard(View v) {
        final InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        v.clearFocus();
    }

    /** A rounded fill with a border {@code strokeDp} wide (1.5 dp borders are given as 2 here: whole pixels). */
    static GradientDrawable outline(int fill, int border, int strokeDp, int radiusPx) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(strokeDp)), border);
        return d;
    }

    /** A dashed border: what was left out. */
    static GradientDrawable dashed(int fill, int border, int radiusPx) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(1)), border, dp(5), dp(4));
        return d;
    }

    /** A spinner with what is going on, and Cancel. */
    static LinearLayout busyRow(Context c, CharSequence status, Runnable onCancel) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final ProgressBar spin = new ProgressBar(c);
        spin.setIndeterminateTintList(ColorStateList.valueOf(CanvasLook.accent()));
        row.addView(spin, new LinearLayout.LayoutParams(dp(20), dp(20)));
        final TextView t = muted(c, status);
        t.setPadding(dp(10), 0, dp(8), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (onCancel != null) {
            final TextView stop = link(c, c.getString(R.string.cancel));
            stop.setOnClickListener(v -> onCancel.run());
            row.addView(stop);
        }
        return row;
    }

    static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams wide(int topDp) {
        final LinearLayout.LayoutParams p = wide();
        p.topMargin = dp(topDp);
        return p;
    }

    static LinearLayout.LayoutParams wide(int heightDp, int topDp) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        p.topMargin = dp(topDp);
        return p;
    }

    /**
     * The mark of a chapter on the path's line: a filled dot (reading, or read), a ring (to come),
     * or the larger ring of the goal; the line runs through it to the rows above and below.
     */
    static final class Dot extends View {
        static final int RING = 0;
        static final int FILLED = 1;
        static final int GOAL = 2;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private final boolean first;
        private final boolean last;
        private final int fill;

        /** @param fill the colour of a filled dot (the accent for the chapter being read, muted once read) */
        Dot(Context c, int kind, boolean first, boolean last, int fill) {
            super(c);
            this.kind = kind;
            this.first = first;
            this.last = last;
            this.fill = fill;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CanvasLook.line());
            final float half = dp(2) / 2f;
            if (!first) {
                canvas.drawRect(cx - half, 0, cx + half, cy, paint);
            }
            if (!last) {
                canvas.drawRect(cx - half, cy, cx + half, getHeight(), paint);
            }
            // the page colour around the mark, so the line stops short of it
            final float r = kind == GOAL ? dp(8) : dp(6);
            paint.setColor(CanvasLook.bg());
            canvas.drawCircle(cx, cy, r + dp(3), paint);
            if (kind == FILLED) {
                paint.setColor(fill);
                canvas.drawCircle(cx, cy, r, paint);
            } else {
                final float stroke = kind == GOAL ? dp(4) : dp(2);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(kind == GOAL ? CanvasLook.accent() : CanvasLook.muted());
                canvas.drawCircle(cx, cy, r - stroke / 2f, paint);
            }
        }
    }
}
