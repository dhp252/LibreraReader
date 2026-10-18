package com.foobnix.eye;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.model.AppSP;
import com.foobnix.pdf.info.R;

/**
 * Eye mode's settings (long press on the eye button, or a tap on its status icon). Kept on this
 * device ({@link AppSP}, D-91). Long blink, gaze and calibration rows come with M2 and M3.
 */
final class EyeSettingsDialog {

    interface Flag {
        boolean get();

        void set(boolean on);
    }

    private EyeSettingsDialog() {
    }

    static void show(Activity a) {
        final AppSP sp = AppSP.get();
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(20);
        box.setPadding(pad, Dips.dpToPx(8), pad, 0);

        final TextView about = new TextView(a);
        about.setText(R.string.eye_settings_about);
        about.setPadding(0, 0, 0, Dips.dpToPx(8));
        box.addView(about);

        add(box, a, R.string.eye_pause_away, new Flag() {
            public boolean get() {
                return sp.eyePauseAway;
            }

            public void set(boolean on) {
                sp.eyePauseAway = on;
            }
        });
        add(box, a, R.string.eye_pause_tts, new Flag() {
            public boolean get() {
                return sp.eyePauseTts;
            }

            public void set(boolean on) {
                sp.eyePauseTts = on;
            }
        });
        add(box, a, R.string.eye_screen, new Flag() {
            public boolean get() {
                return sp.eyeScreen;
            }

            public void set(boolean on) {
                sp.eyeScreen = on;
            }
        });
        add(box, a, R.string.eye_preview, new Flag() {
            public boolean get() {
                return sp.eyePreview;
            }

            public void set(boolean on) {
                sp.eyePreview = on;
            }
        });
        add(box, a, R.string.eye_trace, new Flag() {
            public boolean get() {
                return sp.eyeTrace;
            }

            public void set(boolean on) {
                sp.eyeTrace = on;
            }
        });

        final Button share = new Button(a);
        share.setText(R.string.eye_share_trace);
        share.setAllCaps(false);
        share.setOnClickListener(v -> TraceRecorder.share(a));
        box.addView(share, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        new AlertDialog.Builder(a)
                .setTitle(R.string.eye_mode)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void add(LinearLayout box, Activity a, int text, Flag flag) {
        final CheckBox check = new CheckBox(a);
        check.setText(text);
        check.setChecked(flag.get());
        check.setOnCheckedChangeListener((b, on) -> {
            flag.set(on);
            AppSP.get().save();
            EyeMode.settingsChanged();
        });
        box.addView(check);
    }
}
