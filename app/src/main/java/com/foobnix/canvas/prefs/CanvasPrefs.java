package com.foobnix.canvas.prefs;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;

import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;

/**
 * The canvas rows of the library preferences ({@code layout/canvas_t01_prefs}, included once in
 * {@code fragment_preferences}): the Canvas look switch ({@link AppState#isCanvasLook}).
 * {@code PrefFragment2} calls {@link #bind} once; nothing else of the preferences changes.
 */
public final class CanvasPrefs {

    private CanvasPrefs() {
    }

    /**
     * @param recreate saves the settings and redraws the whole app ({@code PrefFragment2.onTheme})
     */
    public static void bind(final Activity a, final View root, final Runnable recreate) {
        if (a == null || root == null) {
            return;
        }
        final CheckBox look = root.findViewById(R.id.canvas_t01_look);
        if (look == null) {
            return;
        }
        look.setChecked(AppState.get().isCanvasLook);
        // Ink keeps the classic look whatever the switch says (CanvasLook.isOn)
        look.setEnabled(AppState.get().appTheme != AppState.THEME_INK);
        look.setOnCheckedChangeListener((button, checked) -> {
            if (AppState.get().isCanvasLook == checked) {
                return;
            }
            AppState.get().isCanvasLook = checked;
            recreate.run();
        });
    }
}
