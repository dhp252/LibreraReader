package com.foobnix.canvas.dialogs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.CustomSeek;
import com.foobnix.pdf.info.view.MyPopupMenu;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The read-aloud sheet (TTS dialog, {@code dialog_tts.xml}) in the canvas look, after the
 * "Listening" artboard (Audio.dc.html): a big round accent play/pause (68 dp), back and forward
 * as 48 dp marks in ink, the speed as an outlined 44 dp chip on the left, stop as an outlined
 * button on the right, times in JetBrains Mono, labels in Instrument Sans, the two parts of the
 * sheet as a segmented control. Every control keeps its action. The controls standing in the
 * reader's bar and the TTS notification keep their look. Nothing happens unless
 * {@link CanvasLook#isOn()}.
 */
public final class CanvasTts {

    /** Speeds the chip offers: the ones the speed's own menu offers. */
    private static final List<Float> SPEEDS = Arrays.asList(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f, 5f, 6f);

    private static final Set<View> STYLED = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> CHIPS = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private CanvasTts() {
    }

    /** Whether these controls wear the canvas look (then they stand on no sheet of their own). */
    public static boolean isStyled(View controls) {
        return CanvasLook.isOn() && controls != null && STYLED.contains(controls);
    }

    /**
     * Restyles the TTS sheet once it is built. {@code controls} is its TTSControlsView,
     * {@code speed} the speed slider whose value the chip shows and sets.
     */
    public static void dialog(View root, ViewGroup controls, CustomSeek speed) {
        if (root == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            final Tone t = Tone.of(root.getContext());
            tabs(root, t);
            walk(root, t, controls);
            if (controls != null) {
                controls(controls, t, speed);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The speed changed (slider or menu): the chip follows. */
    public static void speedChanged(View root) {
        if (root == null || !CanvasLook.isOn()) {
            return;
        }
        for (View chip : CHIPS) {
            if (chip instanceof TextView && isInside(chip, root)) {
                ((TextView) chip).setText(CanvasText.speed(AppState.get().ttsSpeed));
                chip.setContentDescription(chip.getContext().getString(R.string.canvas_t09_speed,
                        CanvasText.speed(AppState.get().ttsSpeed)));
            }
        }
    }

    private static boolean isInside(View v, View root) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == root) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the two parts

    private static void tabs(View root, Tone t) {
        final TextView main = root.findViewById(R.id.ttsMainTab);
        final TextView advanced = root.findViewById(R.id.ttsAdvancedTab);
        final View mainSection = root.findViewById(R.id.ttsMainSection);
        final View advancedSection = root.findViewById(R.id.ttsAdvancedSection);
        if (main == null || advanced == null || mainSection == null || advancedSection == null
                || !(main.getParent() instanceof ViewGroup)) {
            return;
        }
        final ViewGroup track = (ViewGroup) main.getParent();
        track.setBackground(com.foobnix.canvas.CanvasParts.round(t.surface2(), 0, CanvasLook.dp(13)));
        final int p = CanvasLook.dp(4);
        track.setPadding(p, p, p, p);
        if (track.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) track.getLayoutParams();
            lp.setMargins(CanvasLook.dp(8), CanvasLook.dp(8), CanvasLook.dp(8), CanvasLook.dp(8));
            track.setLayoutParams(lp);
        }
        final boolean english = CanvasDialogs.isEnglish(root.getContext());
        for (TextView tab : new TextView[]{main, advanced}) {
            if (tab.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tab.getLayoutParams();
                lp.setMargins(0, 0, 0, 0);
                lp.width = 0;
                lp.weight = 1;
                tab.setLayoutParams(lp);
            }
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setMinLines(1);
            tab.setMaxLines(2);
            tab.setMinHeight(CanvasLook.dp(36));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
            if (english) {
                tab.setText(CanvasText.sentenceCase(tab.getText().toString()));
            }
        }
        final Runnable paint = () -> {
            final boolean adv = advancedSection.getVisibility() == View.VISIBLE;
            segment(main, !adv, t);
            segment(advanced, adv, t);
        };
        // the same two parts the old tabs showed; set again so the segments are painted after
        main.setOnClickListener(v -> {
            mainSection.setVisibility(View.VISIBLE);
            advancedSection.setVisibility(View.GONE);
            paint.run();
        });
        advanced.setOnClickListener(v -> {
            mainSection.setVisibility(View.GONE);
            advancedSection.setVisibility(View.VISIBLE);
            paint.run();
        });
        paint.run();
    }

    private static void segment(TextView tab, boolean on, Tone t) {
        t.text(tab, CanvasLook.TEXT_TITLE, on ? 600 : 500, on ? t.ink() : t.muted());
        tab.setBackground(on ? com.foobnix.canvas.CanvasParts.round(t.bg(), 0, CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP)) : null);
    }

    // ------------------------------------------------------------------ the settings

    private static void walk(View v, Tone t, View skip) {
        if (v == null || v == skip) {
            return;
        }
        final int id = v.getId();
        if (id == R.id.ttsMainTab || id == R.id.ttsAdvancedTab) {
            return;
        }
        if (v instanceof CustomSeek) {
            seek((CustomSeek) v, t);
            return;
        }
        if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), t, skip);
            }
            return;
        }
        if (v instanceof EditText) {
            CanvasDialogs.field((EditText) v, t);
        } else if (v instanceof CompoundButton) {
            t.text((TextView) v, CanvasLook.TEXT_BODY, 400, t.ink());
            ((CompoundButton) v).setButtonTintList(ColorStateList.valueOf(t.accent()));
            ((CompoundButton) v).setMinHeight(CanvasLook.target());
        } else if (v instanceof TextView) {
            final TextView tv = (TextView) v;
            if ("textLink".equals(tv.getTag())) {
                link(tv, t);
            } else if (id == R.id.textBGwarning) {
                // a warning keeps its red
                tv.setTypeface(CanvasLook.ui(tv.getContext(), 500));
            } else {
                final boolean bold = tv.getTypeface() != null && tv.getTypeface().isBold();
                t.text(tv, CanvasLook.TEXT_TITLE, bold ? 600 : 400, bold ? t.ink() : t.ink2());
            }
        } else if (v instanceof ImageView && id == R.id.onHelp) {
            ((ImageView) v).setImageTintList(ColorStateList.valueOf(t.muted()));
        }
    }

    /** A value that opens a menu or does something (Voice, 350 ms, Start…): outlined, accent text. */
    static void link(TextView tv, Tone t) {
        t.text(tv, CanvasLook.TEXT_TITLE, 600, t.accent());
        tv.setText(tv.getText().toString());
        tv.setBackground(t.outlined());
        tv.setMinHeight(CanvasLook.dp(40));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(CanvasLook.dp(12), CanvasLook.dp(6), CanvasLook.dp(12), CanvasLook.dp(6));
    }

    private static void seek(CustomSeek s, Tone t) {
        final TextView name = s.findViewById(R.id.name);
        if (name != null) {
            if ("textLink".equals(name.getTag())) {
                link(name, t);
                name.setMinWidth(0);
            } else {
                t.text(name, CanvasLook.TEXT_TITLE, 500, t.ink());
            }
        }
        t.mono(s.findViewById(R.id.value), CanvasLook.TEXT_SMALL, t.ink2());
        final View bar = s.findViewById(R.id.seek);
        if (bar instanceof SeekBar) {
            tintSeek((SeekBar) bar, t);
        }
        for (int id : new int[]{R.id.plus, R.id.minus}) {
            final View mark = s.findViewById(id);
            if (mark instanceof ImageView) {
                ((ImageView) mark).setImageTintList(ColorStateList.valueOf(t.ink2()));
            }
        }
    }

    private static void tintSeek(SeekBar bar, Tone t) {
        if (bar.getProgressDrawable() != null) {
            bar.getProgressDrawable().clearColorFilter();
            bar.getProgressDrawable().setAlpha(255);
        }
        if (bar.getThumb() != null) {
            bar.getThumb().clearColorFilter();
            bar.getThumb().setAlpha(255);
        }
        bar.setProgressTintList(ColorStateList.valueOf(t.accent()));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(t.lineStrong()));
        bar.setThumbTintList(ColorStateList.valueOf(t.accent()));
    }

    // ------------------------------------------------------------------ the controls

    private static void controls(ViewGroup controls, Tone t, CustomSeek speed) {
        STYLED.add(controls);
        final View sheet = controls.getChildCount() > 0 ? controls.getChildAt(0) : null;
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
            sheet.setPadding(0, CanvasLook.dp(12), 0, CanvasLook.dp(4));
        }

        final ImageView play = controls.findViewById(R.id.ttsPlay);
        if (play == null || !(play.getParent() instanceof LinearLayout)) {
            return;
        }
        final LinearLayout row = (LinearLayout) play.getParent();
        row.setGravity(Gravity.CENTER);

        mark(play, 68, 21, t.onAccent());
        play.setBackground(t.roundButton(t.accent()));
        play.setElevation(CanvasLook.dp(6));

        for (int id : new int[]{R.id.ttsPrev, R.id.ttsNext}) {
            final ImageView v = controls.findViewById(id);
            if (v != null) {
                mark(v, 48, 12, t.ink());
                v.setBackground(t.roundButton(0));
            }
        }
        final ImageView stop = controls.findViewById(R.id.ttsStop);
        if (stop != null) {
            mark(stop, 44, 13, t.ink());
            stop.setBackground(t.outlined());
        }
        final ImageView settings = controls.findViewById(R.id.ttsDialog);
        if (settings != null) {
            settings.setColorFilter(t.ink(), PorterDuff.Mode.SRC_ATOP);
        }

        tracks(controls, t);

        if (speed != null) {
            final TextView chip = new TextView(controls.getContext());
            t.text(chip, 13, 700, t.ink());
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(t.outlined());
            chip.setText(CanvasText.speed(AppState.get().ttsSpeed));
            chip.setContentDescription(chip.getContext().getString(R.string.canvas_t09_speed,
                    CanvasText.speed(AppState.get().ttsSpeed)));
            chip.setOnClickListener(v -> speedMenu(v, speed));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(CanvasLook.dp(52), CanvasLook.target());
            final View prev = controls.findViewById(R.id.ttsPrev);
            final int at = prev == null ? 0 : Math.max(0, row.indexOfChild(prev));
            row.addView(chip, at, lp);
            CHIPS.add(chip);
        }

        for (int i = 0; i < row.getChildCount(); i++) {
            final View child = row.getChildAt(i);
            if (child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                lp.leftMargin = lp.rightMargin = CanvasLook.dp(3);
                lp.gravity = Gravity.CENTER_VERTICAL;
                child.setLayoutParams(lp);
            }
        }
        // room for the play button's shadow
        row.setPadding(0, CanvasLook.dp(6), 0, CanvasLook.dp(10));
        row.setClipToPadding(false);
        if (row.getParent() instanceof ViewGroup) {
            ((ViewGroup) row.getParent()).setClipToPadding(false);
            ((ViewGroup) row.getParent()).setClipChildren(false);
        }

        final TextView track = controls.findViewById(R.id.trackName);
        t.text(track, CanvasLook.TEXT_TITLE, 600, t.ink());
        t.mono(controls.findViewById(R.id.seekCurrent), CanvasLook.TEXT_SMALL, t.ink2());
        t.mono(controls.findViewById(R.id.seekMax), CanvasLook.TEXT_SMALL, t.ink2());
        final View mp3 = controls.findViewById(R.id.seekMp3);
        if (mp3 instanceof SeekBar) {
            tintSeek((SeekBar) mp3, t);
        }
    }

    /**
     * The previous / next track marks (an MP3 folder) move beside the track's name, as a player
     * shows them, so the row of controls stays within the sheet's width.
     */
    private static void tracks(ViewGroup controls, Tone t) {
        final TextView name = controls.findViewById(R.id.trackName);
        final ImageView prev = controls.findViewById(R.id.ttsPrevTrack);
        final ImageView next = controls.findViewById(R.id.ttsNextTrack);
        if (name == null || prev == null || next == null || !(name.getParent() instanceof LinearLayout)) {
            return;
        }
        final LinearLayout column = (LinearLayout) name.getParent();
        final int at = column.indexOfChild(name);
        final LinearLayout line = new LinearLayout(controls.getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        column.removeView(name);
        for (ImageView v : new ImageView[]{prev, next}) {
            if (v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
            mark(v, 44, 12, t.ink());
            v.setBackground(t.roundButton(0));
        }
        line.addView(prev, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.addView(next, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        column.addView(line, at, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void mark(ImageView v, int sizeDp, int padDp, int color) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            lp.width = CanvasLook.dp(sizeDp);
            lp.height = CanvasLook.dp(sizeDp);
            v.setLayoutParams(lp);
        }
        final int p = CanvasLook.dp(padDp);
        v.setPadding(p, p, p, p);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        v.setImageAlpha(255);
    }

    private static void speedMenu(View chip, CustomSeek speed) {
        final Context c = chip.getContext();
        final MyPopupMenu menu = new MyPopupMenu(c, chip);
        for (final float s : SPEEDS) {
            menu.getMenu().add(CanvasText.speed(s)).setOnMenuItemClickListener(item -> {
                // as the speed's own menu does: the slider is set, its listener stores it
                speed.reset((int) (s * 100));
                speed.sendProgressChanged();
                return false;
            });
        }
        menu.show();
    }
}
