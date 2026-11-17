package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Apps;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

/**
 * "Take 5 minutes" (canvas "Fatigue: take a break"): a quiet page over the book that counts five
 * minutes down. The reader can end it at any moment. It makes no sound, shows no notification
 * and sets no alarm: it only counts while it is on screen, from the clock.
 */
final class ReadsBreak {

    private final Activity a;
    private final Runnable onEnd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long until;
    private Dialog dialog;
    private TextView clock;
    private TextView line;
    private TextView button;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (dialog == null || !dialog.isShowing() || Apps.isDestroyedActivity(a)) {
                return;
            }
            final long left = until - SystemClock.elapsedRealtime();
            if (left <= 0) {
                clock.setText(ReadsUi.clock(0));
                line.setText(R.string.canvas_t31_break_over);
                button.setText(R.string.canvas_t31_break_back);
                return;
            }
            // rounded up, so the count starts at 5:00 and ends at 0:00
            clock.setText(ReadsUi.clock(left + 999));
            handler.postDelayed(this, 500);
        }
    };

    private ReadsBreak(Activity a, Runnable onEnd) {
        this.a = a;
        this.onEnd = onEnd;
        this.until = SystemClock.elapsedRealtime() + ReadsRules.BREAK_LENGTH_MS;
    }

    /** @param onEnd runs when the reader comes back, early or after the five minutes */
    static void show(Activity a, Runnable onEnd) {
        if (a == null || Apps.isDestroyedActivity(a)) {
            return;
        }
        new ReadsBreak(a, onEnd).open();
    }

    private void open() {
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        final int gutter = CanvasLook.dp(32);
        page.setPadding(gutter, gutter, gutter, gutter);

        final TextView label = ReadsUi.mono(a, a.getString(R.string.canvas_t31_break_label), CanvasLook.sensor());
        label.setGravity(Gravity.CENTER);
        page.addView(label, ReadsUi.matchWrap(0));
        clock = ReadsUi.text(a, ReadsUi.clock(ReadsRules.BREAK_LENGTH_MS), 56, 400, CanvasLook.ink());
        clock.setTypeface(CanvasLook.mono(a, 400));
        clock.setGravity(Gravity.CENTER);
        page.addView(clock, ReadsUi.matchWrap(16));
        line = ReadsUi.text(a, a.getString(R.string.canvas_t31_break_line), CanvasLook.TEXT_BODY, 400, CanvasLook.ink2());
        line.setGravity(Gravity.CENTER);
        line.setLineSpacing(CanvasLook.dp(3), 1f);
        page.addView(line, ReadsUi.matchWrap(16));
        button = ReadsUi.button(a, a.getString(R.string.canvas_t31_break_end), false);
        final LinearLayout.LayoutParams lp = ReadsUi.wrap(32, 0);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        button.setMinWidth(CanvasLook.dp(160));
        page.addView(button, lp);

        dialog = ReadsUi.fullScreen(a, page);
        button.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(tick);
            if (onEnd != null) {
                onEnd.run();
            }
        });
        dialog.show();
        handler.post(tick);
    }
}
