package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SimModelTest {

    private static final String QUOTE = "Each step moves the weight against the gradient, scaled by the learning rate.";
    private static final String TEXT = QUOTE + " Start at w = 4 and take 10 steps with a learning rate of 0.3; too small"
            + " and you crawl, too big and you overshoot. Past a rate of 1 every step lands further away.";

    /** Gradient descent on w squared: ten steps from 4 have a closed form. */
    private static final String DESCENT = "{\"title\":\"Gradient descent\",\"statement\":\"Each step moves the weight against"
            + " the gradient.\",\"quote\":\"" + QUOTE + "\",\"inputs\":[{\"id\":\"lr\",\"label\":\"Learning rate η\","
            + "\"unit\":\"\",\"min\":0.05,\"max\":1.1,\"step\":0.05,\"start\":0.3}],\"outputs\":[{\"label\":\"w after 10"
            + " steps\",\"unit\":\"\",\"formula\":\"4 * (1 - 2 * lr) ^ 10\"}],\"presets\":[{\"label\":\"Too small\","
            + "\"values\":{\"lr\":0.05}},{\"label\":\"Just right\",\"values\":{\"lr\":0.3}},{\"label\":\"Break it\","
            + "\"values\":{\"lr\":1.05}},{\"label\":\"Nothing\",\"values\":{\"other\":1}},{\"values\":{\"lr\":0.5}}],"
            + "\"curve\":{\"x\":\"lr\",\"formula\":\"4 * (1 - 2 * lr) ^ 10\",\"label\":\"w after 10 steps\"},"
            + "\"note\":\"Past 1 every step lands further away.\"}";

    private static final String PINS = "{\"title\":\"Same 10 people, two ways\",\"statement\":\"Ten people make far more"
            + " pins when the work is split.\",\"quote\":\"" + PictureSetTest.PINS + "\",\"inputs\":[{\"id\":\"split\","
            + "\"kind\":\"switch\",\"label\":\"Split the work\",\"off\":\"Everyone makes whole pins\",\"on\":\"Split into"
            + " steps\",\"start\":\"on\"}],\"outputs\":[{\"label\":\"Pins a day\",\"unit\":\"pins a day\",\"formula\":"
            + "\"if(split == 1, 48000, 10)\"}]}";

    private static PictureSource text() {
        return PictureSource.ofPassage(TEXT, 7);
    }

    private static SimModel read(String json) throws Exception {
        return SimModel.read(new JSONObject(json));
    }

    @Test
    public void readsAModelWithASliderPresetsAndACurve() throws Exception {
        final SimModel m = read(DESCENT);
        assertNotNull(m);
        assertEquals("Gradient descent", m.title);
        assertEquals(1, m.inputs.size());
        final SimModel.Input lr = m.inputs.get(0);
        assertEquals("lr", lr.id);
        assertFalse(lr.isSwitch);
        assertEquals(21, lr.steps());
        assertEquals(0.05, lr.value(0), 0);
        assertEquals(0.3, lr.value(5), 0);
        assertEquals(1.1, lr.value(21), 0);
        assertEquals(1.1, lr.value(99), 0);
        assertEquals(5, lr.position(0.3));
        assertEquals(0, lr.position(-4));
        assertEquals(21, lr.position(50));
        assertEquals(0, lr.position(Double.NaN));
        assertEquals(0.3, lr.snap(0.31), 0);
        assertEquals(1, m.outputs.size());
        assertEquals("w after 10 steps", m.outputs.get(0).label);
        // a preset needs a name and a value of a known input
        assertEquals(3, m.presets.size());
        assertEquals("Break it", m.presets.get(2).label);
        assertEquals(1.05, m.presets.get(2).values.get("lr"), 0);
        assertNotNull(m.curve);
        assertEquals("lr", m.curveX.id);

        final Map<String, Double> values = m.start();
        assertEquals(0.3, values.get("lr"), 0);
        assertEquals(4 * Math.pow(0.4, 10), m.output(0, values), 1e-12);
        assertEquals(1, m.presetOf(values));
        final Map<String, Double> broken = m.with(m.presets.get(2), values);
        assertEquals(2, m.presetOf(broken));
        assertTrue(Math.abs(m.output(0, broken)) > 4);
        // the start values are untouched
        assertEquals(0.3, values.get("lr"), 0);
        values.put("lr", 0.35);
        assertEquals(-1, m.presetOf(values));
    }

    @Test
    public void theTextsNumbersMakeItTheBooksElseAnIllustration() throws Exception {
        final Picture.Left left = new Picture.Left();
        final SimModel book = read(DESCENT);
        assertTrue(book.check(text(), left));
        assertFalse(book.illustration);
        assertEquals(7, book.page);
        assertEquals(QUOTE, book.quote);
        assertEquals(0, left.numbers);

        // a start value the text does not have
        final SimModel other = read(DESCENT.replace("\"start\":0.3", "\"start\":0.25"));
        assertTrue(other.check(text(), new Picture.Left()));
        assertTrue(other.illustration);
        // a start value that lands on another step than the text's number
        final SimModel off = read(DESCENT.replace("\"start\":0.3", "\"start\":0.33").replace("\"step\":0.05", "\"step\":0.1"));
        assertTrue(off.check(PictureSource.ofPassage(TEXT + " Or 0.33.", 0), new Picture.Left()));
        assertTrue(off.illustration);
        // a label with a number the text does not have
        final SimModel label = read(DESCENT.replace("w after 10 steps\",\"unit\"", "w after 25 steps\",\"unit\""));
        assertTrue(label.check(text(), new Picture.Left()));
        assertTrue(label.illustration);
        // the title, statement and note lose such a line
        final Picture.Left lines = new Picture.Left();
        final SimModel note = read(DESCENT.replace("Past 1 every step", "Past 1.5 every step").replace("Gradient descent\"", "99 descents\""));
        assertTrue(note.check(text(), lines));
        assertEquals("", note.note);
        assertEquals("", note.title);
        assertEquals(2, lines.numbers);
        assertFalse(note.illustration);
    }

    @Test
    public void aModelWhoseWordsAreNotInTheTextIsLeftOut() throws Exception {
        final Picture.Left left = new Picture.Left();
        final SimModel m = read(DESCENT.replace(QUOTE, "A sentence about gradients that the book never wrote at all."));
        assertFalse(m.check(text(), left));
        assertEquals(1, left.pictures);
        assertFalse(read(DESCENT.replace(QUOTE, "")).check(text(), new Picture.Left()));
    }

    @Test
    public void aSwitchIsZeroOrOneAndItsStatesMustShowTheTextsNumbers() throws Exception {
        final SimModel m = read(PINS);
        assertNotNull(m);
        final SimModel.Input split = m.inputs.get(0);
        assertTrue(split.isSwitch);
        assertEquals(1, split.steps());
        assertEquals("Split into steps", split.on);
        assertEquals(1, split.start, 0);
        assertNull(m.curve);
        final Map<String, Double> values = m.start();
        assertEquals(48000, m.output(0, values), 0);
        values.put("split", 0.0);
        assertEquals(10, m.output(0, values), 0);
        // forty-eight thousand and ten are in the chapter
        assertTrue(m.check(PictureSetTest.chapter(), new Picture.Left()));
        assertFalse(m.illustration);
        // "under 200" is not
        final SimModel made = read(PINS.replace("48000, 10)", "48000, 200 - 1)"));
        assertTrue(made.check(PictureSetTest.chapter(), new Picture.Left()));
        assertTrue(made.illustration);
        // other ways to say where a switch starts
        assertEquals(0, read(PINS.replace("\"start\":\"on\"", "\"start\":false")).inputs.get(0).start, 0);
        assertEquals(1, read(PINS.replace("\"start\":\"on\"", "\"start\":\"Split into steps\"")).inputs.get(0).start, 0);
        assertEquals(0, read(PINS.replace("\"start\":\"on\"", "\"start\":0")).inputs.get(0).start, 0);
        assertEquals(0, read(PINS.replace(",\"start\":\"on\"", "")).inputs.get(0).start, 0);
    }

    @Test
    public void aModelWithAFormulaThatCannotBeReadIsNoModel() throws Exception {
        assertNull(SimModel.read(null));
        assertNull(read("{}"));
        assertNull(read(DESCENT.replace("4 * (1 - 2 * lr) ^ 10\"}],", "4 * (1 - 2 * rate) ^ 10\"}],")));
        assertNull(read(DESCENT.replace("4 * (1 - 2 * lr) ^ 10\"}],", "for step in range(10): w -= lr * 2 * w\"}],")));
        assertNull(read(DESCENT.replace("4 * (1 - 2 * lr) ^ 10\"}],", "Math.pow(lr, 2)\"}],")));
        assertNull(read(DESCENT.replace("4 * (1 - 2 * lr) ^ 10\"}],", "\"}],")));
        // the curve's formula too
        assertNull(read(DESCENT.replace("\"formula\":\"4 * (1 - 2 * lr) ^ 10\",\"label\"", "\"formula\":\"plot(lr)\",\"label\"")));
        // no outputs, no inputs
        assertNull(read(DESCENT.replace("\"outputs\":[", "\"results\":[")));
        assertNull(read(DESCENT.replace("\"inputs\":[", "\"knobs\":[")));
        // an input that cannot be one: a bad name, a function's name, no range, a second of the same name
        assertNull(read(DESCENT.replace("\"id\":\"lr\"", "\"id\":\"learning rate\"")));
        assertNull(read(DESCENT.replace("\"id\":\"lr\"", "\"id\":\"min\"")));
        assertNull(read(DESCENT.replace("\"max\":1.1", "\"max\":0.05")));
        assertNull(read(DESCENT.replace("\"min\":0.05,", "")));
        assertNull(read(DESCENT.replace("\"inputs\":[", "\"inputs\":[{\"id\":\"lr\",\"min\":0,\"max\":1},")));
        // a curve along nothing is no curve, the model stays
        final SimModel noAxis = read(DESCENT.replace("\"x\":\"lr\"", "\"x\":\"w\""));
        assertNotNull(noAxis);
        assertNull(noAxis.curve);
        assertEquals(0, noAxis.curve(noAxis.start(), 8).length);
    }

    @Test
    public void aSlidersStepsAreKeptToAThousand() throws Exception {
        final SimModel fine = read(DESCENT.replace("\"step\":0.05", "\"step\":0.000001"));
        assertEquals(SimModel.MAX_STEPS, fine.inputs.get(0).steps());
        // no step given: a hundred
        final SimModel none = read(DESCENT.replace("\"step\":0.05,", ""));
        assertEquals(100, none.inputs.get(0).steps());
        final SimModel negative = read(DESCENT.replace("\"step\":0.05", "\"step\":-1"));
        assertEquals(100, negative.inputs.get(0).steps());
        // a start outside the range is brought inside
        final SimModel outside = read(DESCENT.replace("\"start\":0.3", "\"start\":9"));
        assertEquals(1.1, outside.start().get("lr"), 0);
        final SimModel unset = read(DESCENT.replace(",\"start\":0.3", ""));
        assertEquals(0.05, unset.start().get("lr"), 0);
        // steps add up without noise
        assertEquals(0.15, fineStep().value(2), 0);
    }

    private static SimModel.Input fineStep() throws Exception {
        return read(DESCENT).inputs.get(0);
    }

    @Test
    public void theCurveRunsAlongItsInputAndScalesToItsOwnHeight() throws Exception {
        final SimModel m = read(DESCENT);
        final double[] ys = m.curve(m.start(), SimModel.CURVE_POINTS);
        assertEquals(SimModel.CURVE_POINTS, ys.length);
        assertEquals(4 * Math.pow(0.9, 10), ys[0], 1e-9);
        assertEquals(4 * Math.pow(-1.2, 10), ys[SimModel.CURVE_POINTS - 1], 1e-9);
        final SimModel.Plot plot = SimModel.plot(ys);
        float lowest = 1;
        float highest = 0;
        for (float y : plot.y) {
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }
        assertEquals(0, lowest, 0);
        assertEquals(1, highest, 0);
        assertEquals(1, plot.y[SimModel.CURVE_POINTS - 1], 0);
        // the marker: where the slider stands, and how high its value is
        final Map<String, Double> values = m.start();
        assertEquals((0.3 - 0.05) / 1.05, m.curveAt(values), 1e-6);
        final float at = plot.at(m.curve.eval(values));
        assertTrue(at >= 0 && at < 0.01);
        assertTrue(Float.isNaN(plot.at(Double.NaN)));
        assertEquals(1, plot.at(1e9), 0);
    }

    @Test
    public void aCurveWithGapsOrFlatOrEmpty() {
        final SimModel.Plot gaps = SimModel.plot(new double[]{1, Double.NaN, 3, Double.POSITIVE_INFINITY, 2});
        assertEquals(0, gaps.y[0], 0);
        assertTrue(Float.isNaN(gaps.y[1]));
        assertEquals(1, gaps.y[2], 0);
        assertTrue(Float.isNaN(gaps.y[3]));
        assertEquals(0.5f, gaps.y[4], 0);
        assertEquals(1, gaps.min, 0);
        assertEquals(3, gaps.max, 0);
        final SimModel.Plot flat = SimModel.plot(new double[]{5, 5, 5});
        assertEquals(0.5f, flat.y[1], 0);
        final SimModel.Plot none = SimModel.plot(new double[]{Double.NaN, Double.NaN});
        assertTrue(Float.isNaN(none.y[0]));
        assertTrue(Float.isNaN(none.at(1)));
        assertEquals(0, SimModel.plot(new double[0]).y.length);
    }

    @Test
    public void aDivisionByZeroOrAnOverflowShowsADash() throws Exception {
        final SimModel m = read("{\"quote\":\"q\",\"inputs\":[{\"id\":\"x\",\"min\":0,\"max\":10,\"step\":1,\"start\":0}],"
                + "\"outputs\":[{\"label\":\"share\",\"formula\":\"100 / x\"},{\"label\":\"growth\",\"formula\":\"exp(x * 100)\"},"
                + "{\"label\":\"twice\",\"formula\":\"2 * x\"}],\"curve\":{\"x\":\"x\",\"formula\":\"100 / x\"}}");
        assertNotNull(m);
        final Map<String, Double> values = m.start();
        assertEquals("—", SimModel.show(m.output(0, values)));
        assertEquals("1", SimModel.show(m.output(1, values)));
        assertEquals("0", SimModel.show(m.output(2, values)));
        values.put("x", 10.0);
        assertEquals("10", SimModel.show(m.output(0, values)));
        assertEquals("—", SimModel.show(m.output(1, values)));
        assertEquals("20", SimModel.show(m.output(2, values)));
        // the curve has a gap where it has no value, and is drawn elsewhere
        final double[] ys = m.curve(values, 11);
        assertTrue(Double.isNaN(ys[0]));
        assertEquals(100, ys[1], 0);
        assertTrue(Float.isNaN(SimModel.plot(ys).y[0]));
        // an input without a value
        assertEquals("—", SimModel.show(m.output(2, new HashMap<String, Double>())));
    }

    @Test
    public void numbersAsTheBlockShowsThem() {
        assertEquals("—", SimModel.show(Double.NaN));
        assertEquals("—", SimModel.show(Double.POSITIVE_INFINITY));
        assertEquals("—", SimModel.show(Double.NEGATIVE_INFINITY));
        assertEquals("0", SimModel.show(0));
        assertEquals("0", SimModel.show(-0.0));
        assertEquals("≈ 0", SimModel.show(0.00004));
        assertEquals("≈ 0", SimModel.show(-0.00004));
        assertEquals("0.0004", SimModel.show(0.00042));
        assertEquals("0.3", SimModel.show(0.1 + 0.2));
        assertEquals("0.25", SimModel.show(0.25));
        assertEquals("3.142", SimModel.show(Math.PI));
        assertEquals("12.35", SimModel.show(12.345));
        assertEquals("123.5", SimModel.show(123.45));
        assertEquals("4,800", SimModel.show(4800));
        assertEquals("48,000", SimModel.show(48000.4));
        assertEquals("-1,235", SimModel.show(-1234.5));
        assertEquals("1,000,000", SimModel.show(1e6));
        assertEquals("1e12", SimModel.show(1e12));
        assertEquals("1.2e15", SimModel.show(1.2e15));
        assertEquals("-3.5e200", SimModel.show(-3.46e200));
    }

    @Test
    public void survivesTheStoreInsideItsSet() throws Exception {
        final PictureSet set = PictureSet.parse("{\"pictures\":[],\"sim\":" + DESCENT + "}", text());
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertNotNull(set.sim);
        assertTrue(set.pictures.isEmpty());
        final PictureSet back = PictureSet.fromJson(new JSONObject(set.toJson().toString()));
        final SimModel m = back.sim;
        assertNotNull(m);
        assertEquals("Gradient descent", m.title);
        assertEquals(QUOTE, m.quote);
        assertEquals(7, m.page);
        assertFalse(m.illustration);
        assertEquals(21, m.inputs.get(0).steps());
        assertEquals("Learning rate η", m.inputs.get(0).label);
        assertEquals(3, m.presets.size());
        assertEquals(0.05, m.presets.get(0).values.get("lr"), 0);
        assertEquals("w after 10 steps", m.curveLabel);
        assertEquals(4 * Math.pow(0.4, 10), m.output(0, m.start()), 1e-12);
        assertEquals("Past 1 every step lands further away.", m.note);

        final PictureSet pins = PictureSet.parse("{\"sim\":" + PINS.replace("48000, 10)", "48000, 199)") + "}", PictureSetTest.chapter());
        final SimModel sw = PictureSet.fromJson(new JSONObject(pins.toJson().toString())).sim;
        assertTrue(sw.inputs.get(0).isSwitch);
        assertEquals("Everyone makes whole pins", sw.inputs.get(0).off);
        assertEquals(1, sw.start().get("split"), 0);
        assertTrue(sw.illustration);
    }

    @Test
    public void theSetKeepsAModelOnlyWhenItHolds() {
        // listed among the pictures by its kind
        final PictureSet listed = PictureSet.parse("{\"pictures\":[" + DESCENT.replace("{\"title\"", "{\"kind\":\"sim\",\"title\"")
                + "]}", text());
        assertNotNull(listed.sim);
        assertEquals(0, listed.leftOut);
        // a formula that cannot be read: left out, and counted
        final PictureSet bad = PictureSet.parse("{\"pictures\":[],\"sim\":" + DESCENT.replace("(1 - 2 * lr)", "f(lr)") + "}", text());
        assertNull(bad.sim);
        assertTrue(bad.isEmpty());
        assertEquals(1, bad.leftOut);
        // words that are not the book's
        final PictureSet made = PictureSet.parse("{\"sim\":" + DESCENT.replace(QUOTE, "Never written in this book anywhere.") + "}", text());
        assertNull(made.sim);
        assertEquals(1, made.leftOut);
        // no model asked for: nothing counted
        assertEquals(0, PictureSet.parse("{\"pictures\":[],\"sim\":null}", text()).leftOut);
    }
}
