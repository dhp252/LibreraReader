package com.foobnix.eye;

import android.view.View;
import android.widget.ImageView;

import com.foobnix.eye.PresenceDetector.State;
import com.foobnix.pdf.info.R;

/** The eye button (footer, both readers) and the status-bar icon, set up the same way in each reader. */
public final class EyeUi {

    private EyeUi() {
    }

    /** Tap turns Eye mode on / off, long press opens its settings; hidden without a front camera. */
    public static void setUpButton(ImageView button, EyeMode.Host host) {
        if (button == null) {
            return;
        }
        if (!EyeMode.isAvailable(button.getContext())) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> EyeMode.toggle(host));
        button.setOnLongClickListener(v -> {
            EyeMode.showSettings(host.activity());
            return true;
        });
        showButton(button, EyeMode.isOn(host));
    }

    public static void showButton(ImageView button, boolean on) {
        if (button != null) {
            button.setImageResource(on ? R.drawable.glyphicons_25_eye : R.drawable.ic_eye_off);
        }
    }

    /** @param state null hides the icon (Eye mode off) */
    public static void showState(ImageView icon, State state, EyeMode.Host host) {
        if (icon == null) {
            return;
        }
        if (state == null) {
            icon.setVisibility(View.GONE);
            return;
        }
        final int image;
        final int text;
        switch (state) {
            case SEES:
                image = R.drawable.glyphicons_25_eye;
                text = R.string.eye_sees_you;
                break;
            case AWAY:
                image = R.drawable.ic_eye_closed;
                text = R.string.eye_away;
                break;
            default:
                image = R.drawable.ic_eye_off;
                text = R.string.eye_cant_see;
                break;
        }
        icon.setImageResource(image);
        icon.setContentDescription(icon.getContext().getString(text));
        icon.setVisibility(View.VISIBLE);
        icon.setOnClickListener(v -> EyeMode.iconTapped(host.activity()));
    }
}
