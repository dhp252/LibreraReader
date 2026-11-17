package com.foobnix.canvas;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Bottom sheets in the canvas look: the page colour with rounded top corners
 * ({@link CanvasParts#sheet()}), a 36 x 4 dp grab handle and 16 dp gutters.
 * <p>
 * {@link #style} restyles an existing dialog or popup root as a sheet; {@link #show} puts a view in
 * a plain {@link Dialog} along the bottom of the screen (full width, dimmed behind, slides up),
 * since Material's BottomSheetDialog is not a dependency of the app. Callers check
 * {@link CanvasLook#isOn()} first.
 */
public final class CanvasSheet {

    /** The tag of the handle view, so styling the same root twice adds one handle. */
    public static final String HANDLE_TAG = "canvasSheetHandle";

    private CanvasSheet() {
    }

    /**
     * Gives {@code root} the sheet background and gutters; a vertical LinearLayout also gets the
     * grab handle as its first child.
     */
    public static void style(View root) {
        if (root == null) {
            return;
        }
        root.setBackground(CanvasParts.sheet());
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        root.setPadding(gutter, root instanceof LinearLayout ? CanvasLook.dp(8) : gutter, gutter, gutter);
        if (root instanceof LinearLayout && ((LinearLayout) root).getOrientation() == LinearLayout.VERTICAL
                && root.findViewWithTag(HANDLE_TAG) == null) {
            ((LinearLayout) root).addView(handle(root.getContext()), 0);
        }
    }

    /** The grab handle: a 36 x 4 dp rounded bar in the strong line colour, centred, 8 dp below it. */
    public static View handle(Context c) {
        final View h = new View(c);
        h.setTag(HANDLE_TAG);
        h.setBackground(CanvasParts.round(CanvasLook.lineStrong(), 0, CanvasLook.dp(2)));
        h.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CanvasLook.dp(36), CanvasLook.dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = CanvasLook.dp(8);
        h.setLayoutParams(lp);
        return h;
    }

    /**
     * Shows {@code content} in a bottom sheet: wrapped in a vertical sheet with the handle, in a
     * Dialog at the bottom, full width, cancelled by back or a tap outside.
     *
     * @return the dialog (shown), for the caller's dismiss listener or {@code dismiss()}
     */
    public static Dialog show(Context c, View content) {
        final boolean night = CanvasLook.isNight();
        final Dialog dialog = new Dialog(c, night ? android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        style(sheet);
        if (content.getParent() instanceof ViewGroup) {
            ((ViewGroup) content.getParent()).removeView(content);
        }
        sheet.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.32f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        // the navigation bar and the keyboard stay below the sheet's content
        com.foobnix.android.utils.SafeArea.fit(dialog, sheet, night);
        dialog.show();
        return dialog;
    }
}
