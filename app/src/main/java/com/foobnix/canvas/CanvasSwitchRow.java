package com.foobnix.canvas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * A settings row in the canvas look, built in code: a title (15 sp, ink), an optional subtitle
 * (12 sp, muted) and an optional small mono note under it, and on the right a switch tinted in
 * the accent. At least 56 dp tall, with the canvas gutters and an ink ripple; a tap anywhere on
 * the row flips the switch. Callers check {@link CanvasLook#isOn()} first.
 */
public class CanvasSwitchRow extends LinearLayout {

    private final TextView title;
    private final TextView subtitle;
    private final TextView note;
    private final Switch toggle;

    public CanvasSwitchRow(Context c) {
        this(c, null, null);
    }

    public CanvasSwitchRow(Context c, CharSequence titleText, CharSequence subtitleText) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(CanvasLook.dp(CanvasLook.BAR_DP));
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        setPadding(gutter, CanvasLook.dp(10), gutter, CanvasLook.dp(10));
        final int ripple = (CanvasLook.ink() & 0x00FFFFFF) | 0x1F000000;
        setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), null, new ColorDrawable(0xFFFFFFFF)));
        setClickable(true);
        setFocusable(true);

        final LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(VERTICAL);
        title = new TextView(c);
        CanvasParts.text(title, CanvasLook.TEXT_BODY, 500);
        texts.addView(title);
        subtitle = new TextView(c);
        CanvasParts.secondary(subtitle);
        subtitle.setPadding(0, CanvasLook.dp(2), 0, 0);
        texts.addView(subtitle);
        note = new TextView(c);
        CanvasParts.label(note, false);
        note.setPadding(0, CanvasLook.dp(4), 0, 0);
        texts.addView(note);
        addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        toggle = new Switch(c);
        toggle.setText(null);
        toggle.setThumbTintList(new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled},
                {android.R.attr.state_checked},
                {}},
                new int[]{CanvasLook.lineStrong(), CanvasLook.accent(), CanvasLook.surface()}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled},
                {android.R.attr.state_checked},
                {}},
                new int[]{CanvasLook.line(), CanvasLook.accentLine(), CanvasLook.lineStrong()}));
        // the row is the one control for touch and talkback
        toggle.setClickable(false);
        toggle.setFocusable(false);
        toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CanvasLook.dp(12);
        addView(toggle, lp);

        setOnClickListener(v -> toggle.toggle());
        setTitle(titleText);
        setSubtitle(subtitleText);
        setNote(null);
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
        updateDescription();
    }

    /** The muted line under the title; null or empty hides it. */
    public void setSubtitle(CharSequence text) {
        subtitle.setText(text);
        subtitle.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        updateDescription();
    }

    /** A small uppercase mono note under the subtitle ("Uses your AI key"); null hides it. */
    public void setNote(CharSequence text) {
        note.setText(text);
        note.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        updateDescription();
    }

    public boolean isChecked() {
        return toggle.isChecked();
    }

    /** Sets the switch; the listener hears it like a tap. Set before adding the listener. */
    public void setChecked(boolean checked) {
        toggle.setChecked(checked);
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
        toggle.setOnCheckedChangeListener(l);
    }

    public Switch getSwitch() {
        return toggle;
    }

    public TextView getTitleView() {
        return title;
    }

    public TextView getSubtitleView() {
        return subtitle;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        toggle.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(toggle.isChecked());
    }

    private void updateDescription() {
        if (title == null || subtitle == null || note == null) {
            return;
        }
        final StringBuilder b = new StringBuilder();
        for (TextView t : new TextView[]{title, subtitle, note}) {
            if (!TextUtils.isEmpty(t.getText())) {
                if (b.length() > 0) {
                    b.append(". ");
                }
                b.append(t.getText());
            }
        }
        setContentDescription(b.toString());
    }
}
