package com.foobnix.canvas.prefs;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSwitchRow;
import com.foobnix.model.AppProfile;
import com.foobnix.pdf.info.R;

/**
 * Preferences > Assisted reading: one switch per {@link Assist} feature, with its canvas section
 * name and one line on what it does; the ones that send book text say they use the AI key. A
 * full-screen page in the canvas tokens (day or night); each switch saves the settings at once.
 */
public final class AssistPage {

    private AssistPage() {
    }

    /** The title of a feature, as the canvas names its section. */
    public static int title(Assist f) {
        switch (f) {
            case RECAP:
                return R.string.canvas_t01_assist_recap;
            case PRE_QUESTIONS:
                return R.string.canvas_t01_assist_preq;
            case XRAY:
                return R.string.canvas_t01_assist_xray;
            case SKELETON:
                return R.string.canvas_t01_assist_skeleton;
            case LENS:
                return R.string.canvas_t01_assist_lens;
            case ZOOM:
                return R.string.canvas_t01_assist_zoom;
            case RECALL:
                return R.string.canvas_t01_assist_recall;
            case PATH:
                return R.string.canvas_t01_assist_path;
            case UPDATED:
                return R.string.canvas_t01_assist_updated;
            case USE_IT:
                return R.string.canvas_t01_assist_use;
            case REVIEW:
                return R.string.canvas_t01_assist_review;
            case EDITION:
                return R.string.canvas_t01_assist_edition;
            case PICTURES:
                return R.string.canvas_t01_assist_pictures;
            case FICTION:
                return R.string.canvas_t01_assist_fiction;
            default:
                return R.string.canvas_t01_assist_reads_you;
        }
    }

    /** One line on what a feature does. */
    public static int info(Assist f) {
        switch (f) {
            case RECAP:
                return R.string.canvas_t01_assist_recap_info;
            case PRE_QUESTIONS:
                return R.string.canvas_t01_assist_preq_info;
            case XRAY:
                return R.string.canvas_t01_assist_xray_info;
            case SKELETON:
                return R.string.canvas_t01_assist_skeleton_info;
            case LENS:
                return R.string.canvas_t01_assist_lens_info;
            case ZOOM:
                return R.string.canvas_t01_assist_zoom_info;
            case RECALL:
                return R.string.canvas_t01_assist_recall_info;
            case PATH:
                return R.string.canvas_t01_assist_path_info;
            case UPDATED:
                return R.string.canvas_t01_assist_updated_info;
            case USE_IT:
                return R.string.canvas_t01_assist_use_info;
            case REVIEW:
                return R.string.canvas_t01_assist_review_info;
            case EDITION:
                return R.string.canvas_t01_assist_edition_info;
            case PICTURES:
                return R.string.canvas_t01_assist_pictures_info;
            case FICTION:
                return R.string.canvas_t01_assist_fiction_info;
            default:
                return R.string.canvas_t01_assist_reads_you_info;
        }
    }

    /**
     * Whether a feature sends book text to the AI provider. Only "The book reads you back" works
     * on the phone alone (the camera's readings never leave it).
     */
    public static boolean sendsText(Assist f) {
        return f != Assist.READS_YOU;
    }

    /** How many features are on. */
    public static int countOn() {
        int n = 0;
        for (Assist f : Assist.values()) {
            if (f.isOn()) {
                n++;
            }
        }
        return n;
    }

    /** "All off" or "3 of 15 on", for the preferences row. */
    public static String summary(Activity a) {
        final int n = countOn();
        return n == 0 ? a.getString(R.string.canvas_t01_assist_off)
                : a.getString(R.string.canvas_t01_assist_on, n, Assist.values().length);
    }

    /** Opens the page; {@code onClose} (may be null) runs when it is closed. */
    public static void show(final Activity a, final Runnable onClose) {
        final boolean night = CanvasLook.isNight();
        final Dialog dialog = new Dialog(a, night ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());

        // top bar: the title, and close
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(CanvasLook.dp(CanvasLook.BAR_DP));
        bar.setPadding(gutter, 0, CanvasLook.dp(6), 0);
        final TextView title = new TextView(a);
        CanvasParts.text(title, CanvasLook.TEXT_HEADING, 600);
        title.setText(R.string.canvas_t01_assist);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = new TextView(a);
        CanvasParts.text(close, 20, 400);
        close.setText("✕");
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(a.getString(R.string.close));
        close.setBackground(CanvasParts.ripple(null));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        page.addView(bar);

        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, gutter);

        final TextView header = new TextView(a);
        CanvasParts.secondary(header);
        header.setTextSize(13);
        header.setText(R.string.canvas_t01_assist_header);
        header.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(12));
        list.addView(header);

        final String ai = a.getString(R.string.canvas_t01_assist_ai);
        for (final Assist f : Assist.values()) {
            final View hair = new View(a);
            hair.setBackgroundColor(CanvasLook.line());
            final LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, CanvasLook.dp(1)));
            hp.leftMargin = gutter;
            list.addView(hair, hp);

            final CanvasSwitchRow row = new CanvasSwitchRow(a, a.getString(title(f)), a.getString(info(f)));
            row.setNote(sendsText(f) ? ai : null);
            row.setChecked(f.isOn());
            row.setOnCheckedChangeListener((button, checked) -> {
                f.set(checked);
                AppProfile.save(a);
            });
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog.setContentView(page);
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(CanvasLook.bg()));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.foobnix.android.utils.SafeArea.fit(dialog, page, night);
        dialog.setOnDismissListener(d -> {
            if (onClose != null) {
                onClose.run();
            }
        });
        dialog.show();
    }
}
