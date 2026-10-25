package com.foobnix.android.utils;

import android.app.Dialog;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Full-screen dialogs are drawn edge to edge (targetSdk 35+): under the status bar, the
 * navigation bar and the camera cutout. Their content keeps clear of those, and of the keyboard.
 */
public final class SafeArea {

    private SafeArea() {
    }

    /**
     * @param root the dialog's content view; its own padding is kept and the insets added
     * @param dark whether the content is dark (the bars' icons turn light)
     */
    public static void fit(Dialog dialog, View root, boolean dark) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final Insets clear = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(left + clear.left, top + clear.top, right + clear.right, bottom + clear.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        final Window window = dialog.getWindow();
        if (window != null) {
            final WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
            bars.setAppearanceLightStatusBars(!dark);
            bars.setAppearanceLightNavigationBars(!dark);
        }
        ViewCompat.requestApplyInsets(root);
    }
}
