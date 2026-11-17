package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class SimFormulaTest {

    private static final List<String> IDS = Arrays.asList("x", "y", "lr", "rate_2", "e");

    private static Map<String, Double> values(Object... pairs) {
        final Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return m;
    }

    private static double eval(String formula, Object... pairs) throws Exception {
        return SimFormula.parse(formula, IDS).eval(values(pairs));
    }

    private static void refused(String formula) {
        try {
            SimFormula.parse(formula, IDS);
            fail("read: " + formula);
        } catch (SimFormula.Error e) {
            assertNotNull(e.getMessage());
        }
        assertNull(SimFormula.orNull(formula, IDS));
    }

    @Test
    public void arithmeticWithItsUsualOrder() throws Exception {
        assertEquals(7, eval("1 + 2 * 3"), 0);
        assertEquals(9, eval("(1 + 2) * 3"), 0);
        assertEquals(2, eval("10 - 5 - 3"), 0);
        assertEquals(2, eval("12 / 3 / 2"), 0);
        assertEquals(2.5, eval("5 / 2"), 0);
        assertEquals(14, eval("2 + 3 * 4"), 0);
        assertEquals(0.75, eval(".5 + 0.25"), 0);
        assertEquals(1500, eval("1.5e3"), 0);
        assertEquals(0.002, eval("2E-3"), 1e-15);
        assertEquals(42, eval(" ( ( 42 ) ) "), 0);
    }

    @Test
    public void powersGoToTheRightAndBindTighterThanMinus() throws Exception {
        assertEquals(8, eval("2 ^ 3"), 0);
        assertEquals(512, eval("2 ^ 3 ^ 2"), 0);
        assertEquals(-4, eval("-2 ^ 2"), 0);
        assertEquals(4, eval("(-2) ^ 2"), 0);
        assertEquals(0.5, eval("2 ^ -1"), 0);
        assertEquals(18, eval("2 * 3 ^ 2"), 0);
        assertEquals(1, eval("x ^ 0", "x", 7), 0);
    }

    @Test
    public void unaryMinusAndPlus() throws Exception {
        assertEquals(-3, eval("-3"), 0);
        assertEquals(3, eval("--3"), 0);
        assertEquals(3, eval("+3"), 0);
        assertEquals(-1, eval("2 - 3"), 0);
        assertEquals(5, eval("2 - -3"), 0);
        assertEquals(-6, eval("2 * -3"), 0);
        assertEquals(-5, eval("-x", "x", 5), 0);
    }

    @Test
    public void inputsByTheirIds() throws Exception {
        assertEquals(12, eval("x * y", "x", 3, "y", 4), 0);
        assertEquals(0.6, eval("lr * 2", "lr", 0.3), 1e-12);
        assertEquals(10, eval("rate_2 + e", "rate_2", 4, "e", 6), 0);
        // gradient descent on w squared, ten steps from 4: closed form
        assertEquals(4 * Math.pow(1 - 2 * 0.3, 10), eval("4 * (1 - 2 * lr) ^ 10", "lr", 0.3), 1e-12);
        final SimFormula f = SimFormula.parse("x + x * lr", IDS);
        assertEquals(new HashSet<>(Arrays.asList("x", "lr")), f.ids());
        assertEquals("x + x * lr", f.text());
        // an input without a value: no number
        assertTrue(Double.isNaN(f.eval(values("x", 1))));
        assertTrue(Double.isNaN(f.eval(null)));
        assertTrue(Double.isNaN(f.eval(Collections.<String, Double>emptyMap())));
    }

    @Test
    public void theFunctions() throws Exception {
        assertEquals(2, eval("min(2, 3)"), 0);
        assertEquals(1, eval("min(2, 3, 1, 5)"), 0);
        assertEquals(3, eval("max(2, 3)"), 0);
        assertEquals(4, eval("abs(-4)"), 0);
        assertEquals(3, eval("sqrt(9)"), 0);
        assertEquals(Math.E, eval("exp(1)"), 1e-12);
        assertEquals(1, eval("ln(exp(1))"), 1e-12);
        assertEquals(8, eval("pow(2, 3)"), 0);
        assertEquals(10, eval("max(x, 0) + min(y, 4)", "x", 6, "y", 9), 0);
        assertEquals(7, eval("abs(min(-7, x))", "x", 2), 0);
    }

    @Test
    public void ifPicksABranchByAComparison() throws Exception {
        assertEquals(1, eval("if(x < 3, 1, 2)", "x", 2), 0);
        assertEquals(2, eval("if(x < 3, 1, 2)", "x", 3), 0);
        assertEquals(1, eval("if(x <= 3, 1, 2)", "x", 3), 0);
        assertEquals(1, eval("if(x > 3, 1, 2)", "x", 4), 0);
        assertEquals(2, eval("if(x > 3, 1, 2)", "x", 3), 0);
        assertEquals(1, eval("if(x >= 3, 1, 2)", "x", 3), 0);
        assertEquals(1, eval("if(x == 3, 1, 2)", "x", 3), 0);
        assertEquals(2, eval("if(x == 3, 1, 2)", "x", 3.1), 0);
        assertEquals(48000, eval("if(x == 1, 4800 * y, 20 * y)", "x", 1, "y", 10), 0);
        assertEquals(200, eval("if(x == 1, 4800 * y, 20 * y)", "x", 0, "y", 10), 0);
        // nested, and sides that are sums
        assertEquals(3, eval("if(x + 1 > y * 2, 1, if(y == 2, 3, 4))", "x", 1, "y", 2), 0);
        // a slider's 0.1 + 0.2 equals 0.3
        assertEquals(1, eval("if(x == 0.3, 1, 2)", "x", 0.1 + 0.2), 0);
        assertEquals(2, eval("if(x < 0.3, 1, 2)", "x", 0.1 + 0.2 - 1e-17), 0);
    }

    @Test
    public void onlyTheBranchTakenIsWorkedOut() throws Exception {
        assertEquals(0, eval("if(x == 0, 0, 1 / x)", "x", 0), 0);
        assertEquals(0.5, eval("if(x == 0, 0, 1 / x)", "x", 2), 0);
        // a comparison with no number has no answer
        assertTrue(Double.isNaN(eval("if(1 / x > 0, 1, 2)", "x", 0)));
    }

    @Test
    public void divisionByZeroIsNoNumber() throws Exception {
        assertTrue(Double.isNaN(eval("1 / 0")));
        assertTrue(Double.isNaN(eval("x / (y - y)", "x", 5, "y", 2)));
        assertTrue(Double.isNaN(eval("0 / 0")));
        assertTrue(Double.isNaN(eval("3 + 1 / 0")));
        // and it does not turn back into a number further on
        assertTrue(Double.isNaN(eval("1 / (1 / 0)")));
        assertTrue(Double.isNaN(eval("(1 / 0) ^ 0")));
        assertTrue(Double.isNaN(eval("pow(1 / 0, 0)")));
        assertTrue(Double.isNaN(eval("min(1, 1 / 0)")));
        assertTrue(Double.isNaN(eval("max(1 / 0, 1)")));
        assertTrue(Double.isNaN(eval("abs(1 / 0)")));
        assertTrue(Double.isNaN(eval("-(1 / 0)")));
    }

    @Test
    public void overflowIsNoNumber() throws Exception {
        assertTrue(Double.isNaN(eval("exp(1000)")));
        assertTrue(Double.isNaN(eval("10 ^ 400")));
        assertTrue(Double.isNaN(eval("pow(10, 400)")));
        assertTrue(Double.isNaN(eval("1e308 * 10")));
        assertTrue(Double.isNaN(eval("1e308 + 1e308")));
        assertTrue(Double.isNaN(eval("-1e308 - 1e308")));
        assertTrue(Double.isNaN(eval("x * x", "x", 1e200)));
        // an overflow in the middle does not come back as 0
        assertTrue(Double.isNaN(eval("1 / exp(1000)")));
        assertTrue(Double.isNaN(eval("x + 1", "x", Double.POSITIVE_INFINITY)));
        assertTrue(Double.isNaN(eval("x + 1", "x", Double.NaN)));
        // large but a number
        assertEquals(1e300, eval("1e150 * 1e150"), 1e286);
    }

    @Test
    public void rootsAndLogarithmsOutsideTheirRange() throws Exception {
        assertTrue(Double.isNaN(eval("sqrt(-1)")));
        assertTrue(Double.isNaN(eval("ln(0)")));
        assertTrue(Double.isNaN(eval("ln(-2)")));
        assertTrue(Double.isNaN(eval("(-8) ^ 0.5")));
        assertEquals(0, eval("sqrt(0)"), 0);
    }

    @Test
    public void otherSignsForTheSameOperators() throws Exception {
        assertEquals(6, eval("2 × 3"), 0);
        assertEquals(6, eval("2 · 3"), 0);
        assertEquals(2, eval("6 ÷ 3"), 0);
        assertEquals(-1, eval("2 − 3"), 0);
    }

    @Test
    public void onlyKnownNamesAreRead() {
        refused("z + 1");
        refused("X + 1");
        refused("sin(x)");
        refused("Math.pow(x, 2)");
        refused("x.y");
        refused("java(x)");
        refused("System");
        refused("pi * x");
        // a function's name is not an input, an input is not a function
        refused("min + 1");
        refused("x(2)");
    }

    @Test
    public void whatIsNotAFormulaIsRefused() {
        refused(null);
        refused("");
        refused("   ");
        refused("1 +");
        refused("* 2");
        refused("(1 + 2");
        refused("1 + 2)");
        refused("1 2");
        refused("2x");
        refused("x y");
        refused("1,2");
        refused("x = 2");
        refused("x == 2");
        refused("x < 2");
        refused("x != 2");
        refused("x ? 1 : 2");
        refused("x; y");
        refused("\"text\"");
        refused("x + 'a'");
        refused("x[0]");
        refused("{x}");
        refused("x % 2");
        refused("1e999");
        refused("1..2");
        refused("x && y");
    }

    @Test
    public void functionsTakeTheirOwnNumberOfArguments() {
        refused("abs()");
        refused("abs(1, 2)");
        refused("sqrt(1, 2)");
        refused("pow(2)");
        refused("pow(2, 3, 4)");
        refused("min(1)");
        refused("max()");
        refused("min(1, 2, 3, 4, 5, 6, 7)");
        refused("if(x, 1, 2)");
        refused("if(x < 1, 2)");
        refused("if(x < 1, 2, 3, 4)");
        refused("if(x < 1 < 2, 2, 3)");
        refused("if(x <> 1, 2, 3)");
        refused("min(1, 2");
        refused("min 1, 2)");
    }

    @Test
    public void theLimitsAreKept() throws Exception {
        // length
        final StringBuilder longOne = new StringBuilder("x");
        while (longOne.length() <= SimFormula.MAX_LENGTH) {
            longOne.append(" + 1");
        }
        refused(longOne.toString());
        // parts: short enough, too many
        final StringBuilder many = new StringBuilder("1");
        for (int i = 0; i < SimFormula.MAX_TOKENS / 2 + 1; i++) {
            many.append("+1");
        }
        assertTrue(many.length() <= SimFormula.MAX_LENGTH);
        refused(many.toString());
        // depth: brackets, minus signs, powers, calls
        assertEquals(1, eval(nested("(", ")", SimFormula.MAX_DEPTH)), 0);
        refused(nested("(", ")", SimFormula.MAX_DEPTH + 1));
        refused(nested("-", "", SimFormula.MAX_DEPTH + 2));
        refused(nested("1^", "", SimFormula.MAX_DEPTH + 2));
        refused(nested("abs(", ")", SimFormula.MAX_DEPTH + 1));
        assertEquals(1, eval(nested("abs(", ")", 10)), 0);
    }

    private static String nested(String open, String close, int times) {
        final StringBuilder s = new StringBuilder();
        for (int i = 0; i < times; i++) {
            s.append(open);
        }
        s.append("1");
        for (int i = 0; i < times; i++) {
            s.append(close);
        }
        return s.toString();
    }

    @Test
    public void namesOfInputs() {
        assertTrue(SimFormula.isId("lr"));
        assertTrue(SimFormula.isId("rate_2"));
        assertTrue(SimFormula.isId("_x"));
        assertFalse(SimFormula.isId(null));
        assertFalse(SimFormula.isId(""));
        assertFalse(SimFormula.isId("2x"));
        assertFalse(SimFormula.isId("learning rate"));
        assertFalse(SimFormula.isId("η"));
        assertFalse(SimFormula.isId("a-b"));
        assertFalse(SimFormula.isId("abcdefghijklmnopqrstuvwxyz"));
        // the language's own words
        assertFalse(SimFormula.isId("min"));
        assertFalse(SimFormula.isId("if"));
        assertTrue(SimFormula.isFunction("pow"));
        assertFalse(SimFormula.isFunction("sin"));
    }

    @Test
    public void aFormulaIsReadOnceAndWorkedOutManyTimes() throws Exception {
        final SimFormula f = SimFormula.parse("4 * (1 - 2 * lr) ^ 10", IDS);
        double last = Double.MAX_VALUE;
        for (int i = 1; i <= 9; i++) {
            final double v = Math.abs(f.eval(values("lr", i * 0.05)));
            assertTrue(v < last);
            last = v;
        }
        // "break it": past 1 the steps grow
        assertTrue(Math.abs(f.eval(values("lr", 1.1))) > 4);
    }
}
