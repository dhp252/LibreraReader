package com.foobnix.annotation;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;

/**
 * Floating annotation toolbar in the manner of Zotero: highlight, underline, strikethrough, note,
 * pen (fixed layouts only) and eraser, the color of the current tool (Zotero's palette, line width
 * for the pen), undo, a menu (export, save into PDF) and close. Tapping the active tool again turns
 * it off, so the page can be scrolled and turned.
 */
public class AnnotationToolbar extends LinearLayout {

    public interface Listener {
        /** {@link AnnotationTools#NONE} when the active tool was tapped again. */
        void onToolChanged(int tool);

        void onUndo();

        /** The menu button: what to show under {@code anchor}. */
        void onMore(View anchor);

        void onClose();
    }

    private static final int[] ALL_TOOLS = {AnnotationTools.HIGHLIGHT, AnnotationTools.UNDERLINE, AnnotationTools.STRIKEOUT,
            AnnotationTools.NOTE, AnnotationTools.PEN, AnnotationTools.ERASER};
    private static final int[] ALL_ICONS = {R.drawable.glyphicons_695_text_background, R.drawable.glyphicons_104_underline,
            R.drawable.glyphicons_105_strikethrough, R.drawable.ic_annot_note, R.drawable.my_glyphicons_pen,
            R.drawable.ic_annot_eraser};
    private static final int[] ALL_TITLES = {R.string.annot_highlight, R.string.annot_underline, R.string.annot_strikeout,
            R.string.annot_note, R.string.annot_pen, R.string.annot_eraser};

    private final int[] tools;
    private final int[] titles;
    private final ImageView[] toolViews;
    private final View colorDot;
    private final int buttonSize;
    private int tool = AnnotationTools.NONE;
    private Listener listener;

    /** @param withPen false for a book whose text reflows: a pen stroke could not follow it */
    public AnnotationToolbar(Context context, boolean withPen) {
        super(context);
        final int count = withPen ? ALL_TOOLS.length : ALL_TOOLS.length - 1;
        tools = new int[count];
        titles = new int[count];
        final int[] icons = new int[count];
        for (int i = 0, k = 0; i < ALL_TOOLS.length; i++) {
            if (!withPen && ALL_TOOLS[i] == AnnotationTools.PEN) {
                continue;
            }
            tools[k] = ALL_TOOLS[i];
            titles[k] = ALL_TITLES[i];
            icons[k] = ALL_ICONS[i];
            k++;
        }
        toolViews = new ImageView[count];
        // the tools, undo, menu and close, plus the color dot (about one button): all on the screen
        final int buttons = count + 3;
        buttonSize = Math.min(Dips.dpToPx(42), (Dips.screenWidth() - Dips.dpToPx(40)) / (buttons + 1));
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        final int pad = Dips.dpToPx(4);
        setPadding(pad * 2, pad, pad * 2, pad);
        final GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE262626);
        background.setCornerRadius(Dips.dpToPx(28));
        setBackground(background);
        setElevation(Dips.dpToPx(6));
        // the bar consumes its touches: they must not reach the page under it
        setClickable(true);

        for (int i = 0; i < tools.length; i++) {
            final int t = tools[i];
            toolViews[i] = addButton(icons[i], titles[i], v -> select(t == tool ? AnnotationTools.NONE : t));
        }

        colorDot = new View(context);
        colorDot.setContentDescription(context.getString(R.string.annot_color));
        colorDot.setOnClickListener(v -> showPalette());
        final int dot = Math.min(Dips.dpToPx(24), buttonSize * 4 / 7);
        final LayoutParams dotParams = new LayoutParams(dot, dot);
        dotParams.leftMargin = (buttonSize - dot) / 2 + Dips.dpToPx(2);
        dotParams.rightMargin = (buttonSize - dot) / 2 + Dips.dpToPx(2);
        addView(colorDot, dotParams);

        addButton(R.drawable.ic_annot_undo, R.string.annot_undo, v -> {
            if (listener != null) {
                listener.onUndo();
            }
        });
        addButton(R.drawable.glyphicons_498_more_vertical, R.string.annot_more, v -> {
            if (listener != null) {
                listener.onMore(v);
            }
        });
        addButton(R.drawable.glyphicons_599_menu_close, R.string.close, v -> {
            if (listener != null) {
                listener.onClose();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getTool() {
        return tool;
    }

    /** Shows {@code tool} as the active one (without telling the listener). */
    public void setTool(int tool) {
        this.tool = tool;
        for (int i = 0; i < tools.length; i++) {
            if (tools[i] == tool) {
                final GradientDrawable active = new GradientDrawable();
                active.setShape(GradientDrawable.OVAL);
                active.setColor(0x55FFFFFF);
                toolViews[i].setBackground(active);
            } else {
                toolViews[i].setBackground(null);
            }
        }
        colorDot.setVisibility(AnnotationTools.hasColor(tool) ? VISIBLE : GONE);
        colorDot.setBackground(circle(AnnotationTools.colorInt(tool), true));
    }

    private void select(int newTool) {
        setTool(newTool);
        if (listener != null) {
            listener.onToolChanged(newTool);
        }
    }

    private ImageView addButton(int icon, int title, OnClickListener click) {
        final ImageView button = new ImageView(getContext());
        final int pad = buttonSize * 4 / 21;
        button.setImageResource(icon);
        button.setColorFilter(Color.WHITE);
        button.setPadding(pad, pad, pad, pad);
        button.setContentDescription(getContext().getString(title));
        button.setOnClickListener(click);
        addView(button, new LayoutParams(buttonSize, buttonSize));
        return button;
    }

    private static GradientDrawable circle(int color, boolean ring) {
        final GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        if (ring) {
            circle.setStroke(Dips.dpToPx(2), Color.WHITE);
        }
        return circle;
    }

    /** Zotero's colors for the current tool, and the line width when it is the pen. */
    private void showPalette() {
        final int forTool = tool;
        if (!AnnotationTools.hasColor(forTool)) {
            return;
        }
        final Context c = getContext();
        final LinearLayout content = new LinearLayout(c);
        content.setOrientation(VERTICAL);
        final int pad = Dips.dpToPx(16);
        content.setPadding(pad, pad, pad, pad);

        final AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout row = null;
        for (int i = 0; i < AnnotationTools.COLORS.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                content.addView(row);
            }
            final String color = AnnotationTools.COLORS[i];
            final View swatch = new View(c);
            final boolean current = color.equalsIgnoreCase(AnnotationTools.color(forTool));
            swatch.setBackground(circle(Color.parseColor(color), current));
            if (current) {
                ((GradientDrawable) swatch.getBackground()).setStroke(Dips.dpToPx(3), Color.GRAY);
            }
            swatch.setContentDescription(color);
            swatch.setOnClickListener(v -> {
                AnnotationTools.setColor(forTool, color);
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                setTool(forTool);
                if (listener != null) {
                    listener.onToolChanged(forTool);
                }
            });
            final int size = Dips.dpToPx(40);
            final LayoutParams params = new LayoutParams(size, size);
            params.setMargins(Dips.dpToPx(6), Dips.dpToPx(6), Dips.dpToPx(6), Dips.dpToPx(6));
            row.addView(swatch, params);
        }

        if (forTool == AnnotationTools.PEN) {
            final TextView label = new TextView(c);
            label.setText(c.getString(R.string.annot_line_width) + ": " + Math.round(AppState.get().editLineWidth));
            label.setPadding(0, pad, 0, 0);
            content.addView(label);
            final SeekBar width = new SeekBar(c);
            width.setMax(15);
            width.setProgress(Math.round(AppState.get().editLineWidth) - 1);
            width.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    AppState.get().editLineWidth = progress + 1;
                    label.setText(c.getString(R.string.annot_line_width) + ": " + (progress + 1));
                    if (listener != null) {
                        listener.onToolChanged(forTool);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            content.addView(width);
        }

        dialog[0] = new AlertDialog.Builder(c)
                .setTitle(titles[indexOf(forTool)])
                .setView(content)
                .create();
        dialog[0].show();
    }

    /** Whether the bar has {@code tool} (no pen for a reflowing book). */
    public boolean has(int tool) {
        for (int t : tools) {
            if (t == tool) {
                return true;
            }
        }
        return false;
    }

    private int indexOf(int tool) {
        for (int i = 0; i < tools.length; i++) {
            if (tools[i] == tool) {
                return i;
            }
        }
        return 0;
    }
}
