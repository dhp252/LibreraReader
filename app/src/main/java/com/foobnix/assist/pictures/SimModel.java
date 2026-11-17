package com.foobnix.assist.pictures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A small model of a passage with numbers, for the "TRY IT" block (canvas Picture, PictureSim):
 * a statement, inputs the reader moves (sliders, switches), outputs worked out from them by
 * {@link SimFormula}, presets, and a curve of one output along one input. Read from the model's
 * answer and from the store by {@link #read}; a model whose formula cannot be read is no model.
 * {@link #check} marks it an illustration unless its numbers are the text's. Nothing is run and
 * nothing is sent while the reader plays. Plain Java, no Android.
 */
public final class SimModel {

    public static final int MAX_INPUTS = 4;
    public static final int MAX_OUTPUTS = 3;
    public static final int MAX_PRESETS = 4;
    /** Positions of a slider at most; a finer step is widened. */
    public static final int MAX_STEPS = 1000;
    /** Points a curve is drawn through. */
    public static final int CURVE_POINTS = 64;

    /** What the reader moves: a slider from min to max in steps, or a switch (0 off, 1 on). */
    public static final class Input {
        public final String id;
        public String label = "";
        public String unit = "";
        public boolean isSwitch;
        public double min;
        public double max = 1;
        public double step = 1;
        public double start;
        /** The names of a switch's two states; empty: the screen's own "Off" and "On". */
        public String off = "";
        public String on = "";

        Input(String id) {
            this.id = id;
        }

        /** How many steps the slider has (its positions are 0 to this). */
        public int steps() {
            return isSwitch ? 1 : (int) Math.max(1, Math.round((max - min) / step));
        }

        /** The value at a slider position. */
        public double value(int position) {
            final int p = Math.max(0, Math.min(steps(), position));
            return p == steps() ? max : tidy(min + p * step);
        }

        /** The slider position nearest to a value. */
        public int position(double value) {
            if (Double.isNaN(value)) {
                return 0;
            }
            return (int) Math.max(0, Math.min(steps(), Math.round((value - min) / step)));
        }

        /** A value brought onto the slider: inside its range, on a step. */
        public double snap(double value) {
            return value(position(value));
        }
    }

    /** What the model shows: a number worked out from the inputs. */
    public static final class Output {
        public String label = "";
        public String unit = "";
        public final SimFormula formula;

        Output(SimFormula formula) {
            this.formula = formula;
        }
    }

    /** Values to set at one tap ("Too small", "Just right", "Break it"). */
    public static final class Preset {
        public final String label;
        public final Map<String, Double> values = new LinkedHashMap<>();

        Preset(String label) {
            this.label = label;
        }
    }

    public String title = "";
    /** The rule the model plays with, as the book states it; may be empty. */
    public String statement = "";
    /** The book's own words it comes from: the model's until {@link #check}, the book's after. */
    public String quote = "";
    public int page = -1;
    public String note = "";
    public final List<Input> inputs = new ArrayList<>();
    public final List<Output> outputs = new ArrayList<>();
    public final List<Preset> presets = new ArrayList<>();
    /** The input along the curve's axis, or null for no curve. */
    public Input curveX;
    public SimFormula curve;
    public String curveLabel = "";
    /** Not the book's numbers: a start value, a label or a state shows a number the text sent does not have. */
    public boolean illustration;

    // ------------------------------------------------------------------ reading

    /**
     * A model from the answer or from the store. Null when it is not one: no input, no output, an
     * input without a usable name or range, or any formula that cannot be read.
     */
    static SimModel read(JSONObject o) {
        if (o == null) {
            return null;
        }
        final SimModel m = new SimModel();
        m.title = PictureText.text(o, 80, "title", "name");
        m.statement = PictureText.text(o, 300, "statement", "rule");
        m.quote = PictureText.oneLine(o.optString("quote", ""));
        if (m.quote.length() > 1200) {
            m.quote = m.quote.substring(0, 1200);
        }
        m.page = o.optInt("page", -1);
        m.note = PictureText.text(o, 200, "note", "caption");
        m.illustration = o.optBoolean("illustration", false);

        final Set<String> ids = new HashSet<>();
        final JSONArray inputs = PictureText.list(o, "inputs");
        for (int i = 0; inputs != null && i < inputs.length(); i++) {
            final Input in = input(inputs.optJSONObject(i));
            if (in == null || !ids.add(in.id) || m.inputs.size() >= MAX_INPUTS) {
                // the formulas may name it: without it they cannot be trusted
                return null;
            }
            m.inputs.add(in);
        }
        if (m.inputs.isEmpty()) {
            return null;
        }
        final JSONArray outputs = PictureText.list(o, "outputs");
        for (int i = 0; outputs != null && i < outputs.length() && m.outputs.size() < MAX_OUTPUTS; i++) {
            final JSONObject out = outputs.optJSONObject(i);
            final SimFormula formula = out == null ? null : SimFormula.orNull(out.optString("formula", ""), ids);
            if (formula == null) {
                return null;
            }
            final Output output = new Output(formula);
            output.label = PictureText.text(out, 60, "label", "name");
            output.unit = PictureText.text(out, 24, "unit");
            m.outputs.add(output);
        }
        if (m.outputs.isEmpty()) {
            return null;
        }
        final JSONArray presets = PictureText.list(o, "presets");
        for (int i = 0; presets != null && i < presets.length() && m.presets.size() < MAX_PRESETS; i++) {
            final JSONObject p = presets.optJSONObject(i);
            final JSONObject values = p == null ? null : p.optJSONObject("values");
            final Preset preset = new Preset(PictureText.text(p, 24, "label", "name"));
            for (Input in : m.inputs) {
                final double v = values == null ? Double.NaN : number(values.opt(in.id), in);
                if (!Double.isNaN(v)) {
                    preset.values.put(in.id, in.snap(v));
                }
            }
            if (!preset.label.isEmpty() && !preset.values.isEmpty()) {
                m.presets.add(preset);
            }
        }
        final JSONObject curve = o.optJSONObject("curve");
        if (curve != null) {
            m.curve = SimFormula.orNull(curve.optString("formula", ""), ids);
            if (m.curve == null) {
                return null;
            }
            final Input x = m.input(curve.optString("x", ""));
            if (x == null || x.isSwitch) {
                // nothing to draw it along
                m.curve = null;
            } else {
                m.curveX = x;
                m.curveLabel = PictureText.text(curve, 60, "label", "name");
            }
        }
        return m;
    }

    private static Input input(JSONObject o) {
        final String id = o == null ? "" : o.optString("id", "").trim();
        if (!SimFormula.isId(id)) {
            return null;
        }
        final Input in = new Input(id);
        in.label = PictureText.text(o, 60, "label", "name");
        in.unit = PictureText.text(o, 24, "unit");
        final String kind = o.optString("kind", o.optString("type", "")).toLowerCase(Locale.US);
        in.isSwitch = kind.contains("switch") || kind.contains("toggle");
        if (in.isSwitch) {
            in.off = PictureText.text(o, 40, "off");
            in.on = PictureText.text(o, 40, "on");
            in.min = 0;
            in.max = 1;
            in.step = 1;
            final double start = number(o.opt("start"), in);
            in.start = Double.isNaN(start) ? 0 : start;
            return in;
        }
        in.min = PictureText.number(o.opt("min"));
        in.max = PictureText.number(o.opt("max"));
        if (!real(in.min) || !real(in.max) || !(in.min < in.max)) {
            return null;
        }
        final double step = PictureText.number(o.opt("step"));
        in.step = real(step) && step > 0 ? step : (in.max - in.min) / 100;
        if ((in.max - in.min) / in.step > MAX_STEPS) {
            in.step = (in.max - in.min) / MAX_STEPS;
        }
        final double start = PictureText.number(o.opt("start"));
        in.start = real(start) ? Math.max(in.min, Math.min(in.max, tidy(start))) : in.min;
        return in;
    }

    /** A value for an input: a number, or for a switch also true / false and its state's name. */
    private static double number(Object v, Input in) {
        if (in.isSwitch) {
            if (v instanceof Boolean) {
                return (Boolean) v ? 1 : 0;
            }
            if (v instanceof String) {
                final String s = ((String) v).trim();
                if (s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || (!in.on.isEmpty() && s.equalsIgnoreCase(in.on))) {
                    return 1;
                }
                if (s.equalsIgnoreCase("off") || s.equalsIgnoreCase("false") || (!in.off.isEmpty() && s.equalsIgnoreCase(in.off))) {
                    return 0;
                }
            }
            final double n = PictureText.number(v);
            return Double.isNaN(n) ? Double.NaN : n >= 0.5 ? 1 : 0;
        }
        final double n = PictureText.number(v);
        return real(n) ? n : Double.NaN;
    }

    private static boolean real(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** Without the noise of adding steps (0.30000000000000004 is 0.3). */
    static double tidy(double v) {
        return real(v) && v != 0 ? new BigDecimal(v).round(new MathContext(12)).doubleValue() : v;
    }

    public Input input(String id) {
        for (Input in : inputs) {
            if (in.id.equals(id)) {
                return in;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the checks

    /**
     * Keeps the model only when its quote is among the book's words sent. Its title, statement and
     * note lose a line that shows a number the text sent does not have. It becomes an
     * {@link #illustration} when a slider's start value is not a number of the text, when a label
     * shows such a number, or (a model of switches only) when a state shows one.
     *
     * @return false when the model is to be left out
     */
    boolean check(PictureSource source, Picture.Left left) {
        final PictureSource.Found found = source.locate(quote);
        if (found == null) {
            left.pictures++;
            return false;
        }
        quote = found.text;
        page = found.page;
        final Set<String> known = source.numbers();
        title = keep(title, known, left);
        statement = keep(statement, known, left);
        note = keep(note, known, left);
        boolean sliders = false;
        for (Input in : inputs) {
            illustration |= !PictureNumbers.allIn(in.label + " " + in.unit + " " + in.off + " " + in.on, known);
            if (!in.isSwitch) {
                sliders = true;
                // the value the slider starts on (the given one brought onto a step)
                illustration |= !PictureNumbers.has(in.snap(in.start), known);
            }
        }
        for (Output out : outputs) {
            illustration |= !PictureNumbers.allIn(out.label + " " + out.unit, known);
        }
        for (Preset p : presets) {
            illustration |= !PictureNumbers.allIn(p.label, known);
        }
        illustration |= !PictureNumbers.allIn(curveLabel, known);
        if (!sliders) {
            // only switches: every number a state can show is the model's own writing
            for (int state = 0; state < (1 << inputs.size()); state++) {
                final Map<String, Double> values = new HashMap<>();
                for (int i = 0; i < inputs.size(); i++) {
                    values.put(inputs.get(i).id, (state >> i & 1) == 1 ? 1.0 : 0.0);
                }
                for (Output out : outputs) {
                    final double v = out.formula.eval(values);
                    illustration |= !Double.isNaN(v) && !PictureNumbers.has(tidy(v), known);
                }
            }
        }
        return true;
    }

    private static String keep(String line, Set<String> known, Picture.Left left) {
        if (line.isEmpty() || PictureNumbers.allIn(line, known)) {
            return line;
        }
        left.numbers++;
        return "";
    }

    // ------------------------------------------------------------------ playing

    /** Every input at its start value. */
    public Map<String, Double> start() {
        final Map<String, Double> values = new HashMap<>();
        for (Input in : inputs) {
            values.put(in.id, in.isSwitch ? in.start : in.snap(in.start));
        }
        return values;
    }

    /** An output for these values; NaN when it has none. */
    public double output(int index, Map<String, Double> values) {
        return outputs.get(index).formula.eval(values);
    }

    /** These values with a preset's put over them. */
    public Map<String, Double> with(Preset preset, Map<String, Double> values) {
        final Map<String, Double> out = new HashMap<>(values);
        out.putAll(preset.values);
        return out;
    }

    /** The preset these values are, or -1. */
    public int presetOf(Map<String, Double> values) {
        for (int i = 0; i < presets.size(); i++) {
            boolean same = true;
            for (Map.Entry<String, Double> e : presets.get(i).values.entrySet()) {
                final Double v = values.get(e.getKey());
                same &= v != null && Math.abs(v - e.getValue()) <= 1e-9 * Math.max(1, Math.abs(v));
            }
            if (same) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The curve for these values: its formula at {@code points} places along the axis input, from
     * its min to its max, the other inputs as they are. NaN where it has no value. Empty without a
     * curve.
     */
    public double[] curve(Map<String, Double> values, int points) {
        if (curve == null || curveX == null || points < 2) {
            return new double[0];
        }
        final Map<String, Double> at = new HashMap<>(values);
        final double[] ys = new double[points];
        for (int i = 0; i < points; i++) {
            at.put(curveX.id, curveX.min + (curveX.max - curveX.min) * i / (points - 1));
            ys[i] = curve.eval(at);
        }
        return ys;
    }

    /** A curve scaled to its own height. */
    public static final class Plot {
        /** Each point from 0 (the lowest value) to 1 (the highest); NaN where the curve has no value. */
        public final float[] y;
        public final double min;
        public final double max;

        Plot(float[] y, double min, double max) {
            this.y = y;
            this.min = min;
            this.max = max;
        }

        /** Where a value stands on the plot's height, 0 to 1; NaN for no value. */
        public float at(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value) || Double.isNaN(min)) {
                return Float.NaN;
            }
            if (max <= min) {
                return 0.5f;
            }
            return (float) Math.max(0, Math.min(1, (value - min) / (max - min)));
        }
    }

    /** The values of a curve scaled between their lowest and highest; a flat curve runs through the middle. */
    public static Plot plot(double[] ys) {
        double min = Double.NaN;
        double max = Double.NaN;
        for (double v : ys) {
            if (real(v)) {
                min = Double.isNaN(min) ? v : Math.min(min, v);
                max = Double.isNaN(max) ? v : Math.max(max, v);
            }
        }
        final Plot plot = new Plot(new float[ys.length], min, max);
        for (int i = 0; i < ys.length; i++) {
            plot.y[i] = real(ys[i]) ? plot.at(ys[i]) : Float.NaN;
        }
        return plot;
    }

    /** Where the axis input's value stands along the curve, 0 to 1. */
    public float curveAt(Map<String, Double> values) {
        final Double v = curveX == null ? null : values.get(curveX.id);
        if (v == null || Double.isNaN(v)) {
            return Float.NaN;
        }
        return (float) Math.max(0, Math.min(1, (v - curveX.min) / (curveX.max - curveX.min)));
    }

    /**
     * A number as the block shows it: "—" when there is none (a division by zero, an overflow),
     * "≈ 0" for one too small to matter, thousands grouped, four digits that count ("48,000",
     * "0.3", "12.35"), and powers of ten for one too long to read ("1.2e15").
     */
    public static String show(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "—";
        }
        final double abs = Math.abs(v);
        if (abs == 0) {
            return "0";
        }
        if (abs < 1e-4) {
            return "≈ 0";
        }
        if (abs >= 1e12) {
            return String.format(Locale.US, "%.1e", v).replace("e+", "e").replace(".0e", "e");
        }
        final DecimalFormat format = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setMaximumFractionDigits(abs >= 1000 ? 0 : abs >= 100 ? 1 : abs >= 10 ? 2 : abs >= 1 ? 3 : 4);
        final String s = format.format(v);
        return "-0".equals(s) ? "0" : s;
    }

    // ------------------------------------------------------------------ the store

    JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("statement", statement);
        o.put("quote", quote);
        o.put("page", page);
        o.put("note", note);
        o.put("illustration", illustration);
        final JSONArray ins = new JSONArray();
        for (Input in : inputs) {
            final JSONObject i = new JSONObject().put("id", in.id).put("label", in.label).put("unit", in.unit).put("start", in.start);
            if (in.isSwitch) {
                i.put("kind", "switch").put("off", in.off).put("on", in.on);
            } else {
                i.put("min", in.min).put("max", in.max).put("step", in.step);
            }
            ins.put(i);
        }
        o.put("inputs", ins);
        final JSONArray outs = new JSONArray();
        for (Output out : outputs) {
            outs.put(new JSONObject().put("label", out.label).put("unit", out.unit).put("formula", out.formula.text()));
        }
        o.put("outputs", outs);
        final JSONArray pres = new JSONArray();
        for (Preset p : presets) {
            final JSONObject values = new JSONObject();
            final Iterator<Map.Entry<String, Double>> it = p.values.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<String, Double> e = it.next();
                values.put(e.getKey(), e.getValue());
            }
            pres.put(new JSONObject().put("label", p.label).put("values", values));
        }
        o.put("presets", pres);
        if (curve != null && curveX != null) {
            o.put("curve", new JSONObject().put("x", curveX.id).put("formula", curve.text()).put("label", curveLabel));
        }
        return o;
    }
}
