package com.foobnix.assist.pictures;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "TRY IT" block (canvas Picture, PictureSim): a small model's statement, its outputs as big
 * numbers, its curve with a ring at the reader's value, a slider or a switch per input, and
 * preset chips. Every move works the formulas out again on the device ({@link SimModel},
 * {@link SimFormula}); nothing is run and nothing is sent. A model whose numbers are not the
 * text's says so: "Illustration, not the book's numbers".
 */
final class SimBlock {

    private final Context c;
    private final SimModel model;
    private final Map<String, Double> values;

    private final List<TextView> outputs = new ArrayList<>();
    /** By input id: the slider and the text of its value, or the switch and the name of its state. */
    private final Map<String, SeekBar> sliders = new HashMap<>();
    private final Map<String, Switch> switches = new HashMap<>();
    private final Map<String, TextView> shown = new HashMap<>();
    private final List<TextView> presets = new ArrayList<>();
    private SimCurveView curve;
    private TextView range;
    /** The block itself moves a slider or a switch (a preset): their listeners keep still. */
    private boolean setting;

    private SimBlock(Context c, SimModel model) {
        this.c = c;
        this.model = model;
        this.values = model.start();
    }

    /** The block for a model, every input at its start value. */
    static View build(Context c, SimModel model) {
        return new SimBlock(c, model).view();
    }

    private View view() {
        final LinearLayout all = PictureUi.column(c);

        final LinearLayout block = PictureUi.column(c);
        block.setBackground(CanvasParts.round(PictureUi.block(), 0, CanvasLook.dp(16)));
        final int pad = CanvasLook.dp(16);
        block.setPadding(pad, pad, pad, pad);
        block.addView(PictureUi.text(c, model.title.isEmpty() ? c.getString(R.string.canvas_t28_title) : model.title, 15, 600,
                CanvasLook.ink()));
        final TextView tryIt = PictureUi.mono(c, c.getString(model.inputs.get(0).isSwitch ? R.string.canvas_t28_try_switch
                : R.string.canvas_t28_try_slider), CanvasLook.muted());
        tryIt.setPadding(0, CanvasLook.dp(2), 0, 0);
        block.addView(tryIt);
        if (!model.statement.isEmpty()) {
            final TextView statement = PictureUi.text(c, model.statement, 13, 400, CanvasLook.ink2());
            statement.setLineSpacing(CanvasLook.dp(2), 1f);
            statement.setPadding(0, CanvasLook.dp(8), 0, 0);
            block.addView(statement);
        }
        for (SimModel.Output output : model.outputs) {
            if (!output.label.isEmpty()) {
                final TextView label = PictureUi.mono(c, output.label, CanvasLook.accent());
                label.setPadding(0, CanvasLook.dp(12), 0, 0);
                block.addView(label);
            }
            final LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(true);
            line.setPadding(0, CanvasLook.dp(output.label.isEmpty() ? 12 : 2), 0, 0);
            final TextView big = PictureUi.text(c, "", 34, 700, CanvasLook.ink());
            big.setLetterSpacing(-0.02f);
            big.setSingleLine(true);
            line.addView(big);
            outputs.add(big);
            if (!output.unit.isEmpty()) {
                final TextView unit = PictureUi.text(c, output.unit, 14, 400, CanvasLook.ink2());
                unit.setPadding(CanvasLook.dp(8), 0, 0, 0);
                line.addView(unit);
            }
            block.addView(line);
        }
        if (model.curve != null && model.curveX != null) {
            addCurve(block);
        }
        if (!model.note.isEmpty()) {
            final TextView note = PictureUi.text(c, model.note, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            note.setPadding(0, CanvasLook.dp(10), 0, 0);
            block.addView(note);
        }
        if (model.illustration) {
            final TextView flag = PictureUi.text(c, c.getString(R.string.canvas_t28_illustration), CanvasLook.TEXT_SMALL, 600,
                    CanvasLook.sensor());
            flag.setPadding(0, CanvasLook.dp(10), 0, 0);
            flag.setContentDescription(c.getString(R.string.canvas_t28_illustration) + ". "
                    + c.getString(R.string.canvas_t28_illustration_info));
            block.addView(flag);
        }
        all.addView(block, PictureUi.row(16, 8, 16, 0));

        for (SimModel.Input input : model.inputs) {
            all.addView(input.isSwitch ? switchRow(input) : sliderRow(input), PictureUi.row(20, 14, 20, 0));
        }
        if (!model.presets.isEmpty()) {
            all.addView(presetRow(), PictureUi.row(16, 12, 16, 0));
        }
        final TextView local = PictureUi.text(c, c.getString(model.illustration ? R.string.canvas_t28_local_illustration
                : R.string.canvas_t28_local), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        local.setLineSpacing(CanvasLook.dp(2), 1f);
        all.addView(local, PictureUi.row(20, 14, 20, 0));
        update();
        return all;
    }

    /** The curve under its name and the lowest and highest value it reaches, over the axis's ends. */
    private void addCurve(LinearLayout block) {
        final LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, CanvasLook.dp(12), 0, 0);
        final String name = model.curveLabel.isEmpty() ? model.outputs.get(0).label : model.curveLabel;
        head.addView(PictureUi.text(c, name, 13, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        range = small(CanvasLook.muted());
        head.addView(range);
        block.addView(head);

        curve = new SimCurveView(c);
        curve.setContentDescription(c.getString(R.string.canvas_t28_curve, name, axisName(model.curveX)));
        block.addView(curve, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(160)));

        final LinearLayout axis = new LinearLayout(c);
        axis.setOrientation(LinearLayout.HORIZONTAL);
        axis.setPadding(CanvasLook.dp(6), 0, CanvasLook.dp(4), 0);
        final TextView from = small(CanvasLook.muted());
        from.setText(SimModel.show(model.curveX.min));
        axis.addView(from, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView middle = small(CanvasLook.muted());
        middle.setText(axisName(model.curveX));
        middle.setGravity(Gravity.CENTER);
        middle.setSingleLine(true);
        middle.setEllipsize(TextUtils.TruncateAt.END);
        axis.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        final TextView to = small(CanvasLook.muted());
        to.setText(SimModel.show(model.curveX.max));
        to.setGravity(Gravity.END);
        axis.addView(to, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        block.addView(axis);
    }

    private static String axisName(SimModel.Input input) {
        return input.label.isEmpty() ? input.id : input.label;
    }

    /** A 10 sp mono text as the reader wrote or the model named it: no capitals forced. */
    private TextView small(int color) {
        final TextView t = PictureUi.mono(c, "", color);
        t.setAllCaps(false);
        t.setLetterSpacing(0f);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        return t;
    }

    /** A slider: its name and its value over a seek bar from min to max in steps. */
    private View sliderRow(final SimModel.Input input) {
        final LinearLayout row = PictureUi.column(c);
        final LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(true);
        head.addView(PictureUi.text(c, axisName(input), 14, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView value = PictureUi.mono(c, "", CanvasLook.accent());
        value.setAllCaps(false);
        value.setLetterSpacing(0f);
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        value.setPadding(CanvasLook.dp(8), 0, 0, 0);
        head.addView(value);
        row.addView(head);
        shown.put(input.id, value);

        final SeekBar bar = new SeekBar(c);
        bar.setMax(input.steps());
        bar.setProgress(input.position(values.get(input.id)));
        bar.setProgressTintList(ColorStateList.valueOf(CanvasLook.accent()));
        bar.setThumbTintList(ColorStateList.valueOf(CanvasLook.accent()));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(CanvasLook.lineStrong()));
        bar.setContentDescription(axisName(input));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!setting) {
                    values.put(input.id, input.value(progress));
                    update();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.target());
        // the bar's own side padding would indent it from its name
        lp.leftMargin = -CanvasLook.dp(8);
        lp.rightMargin = -CanvasLook.dp(8);
        row.addView(bar, lp);
        sliders.put(input.id, bar);
        return row;
    }

    /** A switch: its name over the name of the state it is in, and the switch. */
    private View switchRow(final SimModel.Input input) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.target());
        final LinearLayout names = PictureUi.column(c);
        names.addView(PictureUi.text(c, axisName(input), 14, 600, CanvasLook.ink()));
        final TextView state = PictureUi.mono(c, "", CanvasLook.muted());
        state.setPadding(0, CanvasLook.dp(2), 0, 0);
        names.addView(state);
        shown.put(input.id, state);
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final Switch toggle = new Switch(c);
        toggle.setChecked(values.get(input.id) >= 0.5);
        toggle.setMinHeight(CanvasLook.target());
        toggle.setMinWidth(CanvasLook.target());
        toggle.setContentDescription(axisName(input));
        final int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{CanvasLook.accent(), CanvasLook.muted()}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{CanvasLook.accentLine(), CanvasLook.lineStrong()}));
        toggle.setOnCheckedChangeListener((button, on) -> {
            if (!setting) {
                values.put(input.id, on ? 1.0 : 0.0);
                update();
            }
        });
        row.addView(toggle);
        switches.put(input.id, toggle);
        return row;
    }

    /** The presets as chips of equal width; the one the values are on is marked. */
    private View presetRow() {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setContentDescription(c.getString(R.string.canvas_t28_presets));
        for (int i = 0; i < model.presets.size(); i++) {
            final SimModel.Preset preset = model.presets.get(i);
            final TextView chip = PictureUi.text(c, preset.label, 13, 600, CanvasLook.ink());
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(CanvasLook.dp(6), 0, CanvasLook.dp(6), 0);
            chip.setOnClickListener(v -> apply(preset));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, CanvasLook.target(), 1);
            lp.leftMargin = i == 0 ? 0 : CanvasLook.dp(8);
            row.addView(chip, lp);
            presets.add(chip);
        }
        return row;
    }

    /** A preset's values onto the sliders and switches, then everything worked out once. */
    private void apply(SimModel.Preset preset) {
        values.putAll(preset.values);
        setting = true;
        for (SimModel.Input input : model.inputs) {
            final Double v = values.get(input.id);
            if (v == null) {
                continue;
            }
            if (input.isSwitch) {
                switches.get(input.id).setChecked(v >= 0.5);
            } else {
                sliders.get(input.id).setProgress(input.position(v));
            }
        }
        setting = false;
        update();
    }

    /** Works the model out for the values as they are and shows it. */
    private void update() {
        for (int i = 0; i < outputs.size(); i++) {
            outputs.get(i).setText(SimModel.show(model.output(i, values)));
        }
        for (SimModel.Input input : model.inputs) {
            final TextView text = shown.get(input.id);
            final double v = values.get(input.id);
            if (input.isSwitch) {
                final boolean on = v >= 0.5;
                final String name = on ? input.on : input.off;
                text.setText(name.isEmpty() ? c.getString(on ? R.string.canvas_t28_on : R.string.canvas_t28_off) : name);
                text.setTextColor(on ? CanvasLook.accent() : CanvasLook.muted());
            } else {
                text.setText(input.unit.isEmpty() ? SimModel.show(v) : SimModel.show(v) + " " + input.unit);
            }
        }
        if (curve != null) {
            final SimModel.Plot plot = SimModel.plot(model.curve(values, SimModel.CURVE_POINTS));
            curve.set(plot.y, model.curveAt(values), plot.at(model.curve.eval(values)));
            range.setText(Double.isNaN(plot.min) ? "—" : c.getString(R.string.canvas_t28_range, SimModel.show(plot.min),
                    SimModel.show(plot.max)));
        }
        final int on = model.presetOf(values);
        for (int i = 0; i < presets.size(); i++) {
            final TextView chip = presets.get(i);
            final boolean chosen = i == on;
            chip.setSelected(chosen);
            chip.setBackground(CanvasParts.ripple(CanvasParts.round(chosen ? CanvasLook.accentTint() : CanvasLook.bg(),
                    chosen ? CanvasLook.accent() : CanvasLook.lineStrong(), CanvasLook.radius())));
            chip.setTextColor(chosen ? CanvasLook.accent() : CanvasLook.ink());
        }
    }
}
