package com.foobnix.assist.pictures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A formula of a small model, read once and worked out for any values of its inputs. It is plain
 * arithmetic and nothing else: numbers, the names of the model's inputs, {@code + - * / ^},
 * parentheses, a leading minus, the functions {@code min max abs sqrt exp ln pow}, and
 * {@code if(a < b, x, y)} with {@code < > <= >= ==}.
 * <p>
 * This is a pure recursive-descent reader over the formula's characters: no script engine, no
 * reflection, nothing is ever run. A formula longer than {@link #MAX_LENGTH}, nested deeper than
 * {@link #MAX_DEPTH}, with more than {@link #MAX_TOKENS} parts, or naming anything that is not an
 * input or one of the functions is refused ({@link Error}). A result that is not a number
 * (division by zero, a root of a negative, an overflow) is {@link Double#NaN}, which the screen
 * shows as "—". Plain Java, no Android.
 */
public final class SimFormula {

    public static final int MAX_LENGTH = 200;
    public static final int MAX_DEPTH = 24;
    public static final int MAX_TOKENS = 120;
    /** Arguments of min and max at most. */
    public static final int MAX_ARGS = 6;

    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList("min", "max", "abs", "sqrt", "exp", "ln", "pow", "if"));

    /** The formula cannot be read; the message says why, for a log. */
    public static final class Error extends Exception {
        Error(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ the tree

    private abstract static class Node {
        abstract double eval(Map<String, Double> values);
    }

    /** A value that is a number, else NaN: an overflow anywhere makes the whole result "no number". */
    private static double finite(double v) {
        return Double.isInfinite(v) ? Double.NaN : v;
    }

    private static final class Num extends Node {
        final double value;

        Num(double value) {
            this.value = value;
        }

        @Override
        double eval(Map<String, Double> values) {
            return value;
        }
    }

    private static final class Var extends Node {
        final String id;

        Var(String id) {
            this.id = id;
        }

        @Override
        double eval(Map<String, Double> values) {
            final Double v = values == null ? null : values.get(id);
            return v == null ? Double.NaN : finite(v);
        }
    }

    private static final class Neg extends Node {
        final Node of;

        Neg(Node of) {
            this.of = of;
        }

        @Override
        double eval(Map<String, Double> values) {
            return -of.eval(values);
        }
    }

    private static final class Bin extends Node {
        final char op;
        final Node left;
        final Node right;

        Bin(char op, Node left, Node right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        double eval(Map<String, Double> values) {
            final double a = left.eval(values);
            final double b = right.eval(values);
            switch (op) {
                case '+':
                    return finite(a + b);
                case '-':
                    return finite(a - b);
                case '*':
                    return finite(a * b);
                case '/':
                    return b == 0 ? Double.NaN : finite(a / b);
                default:
                    return power(a, b);
            }
        }
    }

    /** a to the power b; Math.pow alone would make 1 of "no number" to the power 0. */
    private static double power(double a, double b) {
        return Double.isNaN(a) || Double.isNaN(b) ? Double.NaN : finite(Math.pow(a, b));
    }

    private static final class Call extends Node {
        final String name;
        final Node[] args;

        Call(String name, Node[] args) {
            this.name = name;
            this.args = args;
        }

        @Override
        double eval(Map<String, Double> values) {
            double a = args[0].eval(values);
            switch (name) {
                case "abs":
                    return Math.abs(a);
                case "sqrt":
                    return a < 0 ? Double.NaN : Math.sqrt(a);
                case "exp":
                    return finite(Math.exp(a));
                case "ln":
                    return a <= 0 ? Double.NaN : Math.log(a);
                case "pow":
                    return power(a, args[1].eval(values));
                default:
                    for (int i = 1; i < args.length; i++) {
                        final double b = args[i].eval(values);
                        // Math.min and max pass NaN on
                        a = "min".equals(name) ? Math.min(a, b) : Math.max(a, b);
                    }
                    return a;
            }
        }
    }

    /** if(left cmp right, then, otherwise): only the branch taken is worked out. */
    private static final class If extends Node {
        final String cmp;
        final Node left;
        final Node right;
        final Node then;
        final Node otherwise;

        If(String cmp, Node left, Node right, Node then, Node otherwise) {
            this.cmp = cmp;
            this.left = left;
            this.right = right;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        double eval(Map<String, Double> values) {
            final double a = left.eval(values);
            final double b = right.eval(values);
            if (Double.isNaN(a) || Double.isNaN(b)) {
                return Double.NaN;
            }
            // values a slider steps to are never exactly the number written: equal means equal to nine digits
            final boolean same = Math.abs(a - b) <= 1e-9 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
            final boolean holds;
            switch (cmp) {
                case "<":
                    holds = a < b && !same;
                    break;
                case ">":
                    holds = a > b && !same;
                    break;
                case "<=":
                    holds = a < b || same;
                    break;
                case ">=":
                    holds = a > b || same;
                    break;
                default:
                    holds = same;
                    break;
            }
            return (holds ? then : otherwise).eval(values);
        }
    }

    // ------------------------------------------------------------------ reading

    private static final int NUMBER = 0;
    private static final int NAME = 1;
    private static final int SIGN = 2;
    private static final int END = 3;

    private static final class Token {
        final int type;
        final String text;
        final double value;

        Token(int type, String text, double value) {
            this.type = type;
            this.text = text;
            this.value = value;
        }
    }

    private final String text;
    private final Node root;
    private final Set<String> used;

    private SimFormula(String text, Node root, Set<String> used) {
        this.text = text;
        this.root = root;
        this.used = used;
    }

    /** Whether a word is one of the formula language's own (a function, or "if"): no input may take it. */
    public static boolean isFunction(String name) {
        return FUNCTIONS.contains(name);
    }

    /** Whether a word can name an input: a letter or "_", then letters, digits or "_", 24 at most, not a function. */
    public static boolean isId(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]{0,23}") && !isFunction(name);
    }

    /**
     * Reads a formula.
     *
     * @param ids the names of the model's inputs: the only names a formula may use
     * @throws Error when it is not a formula of this language or breaks a limit
     */
    public static SimFormula parse(String formula, Collection<String> ids) throws Error {
        if (formula == null || formula.trim().isEmpty()) {
            throw new Error("empty");
        }
        if (formula.length() > MAX_LENGTH) {
            throw new Error("longer than " + MAX_LENGTH);
        }
        final Reader r = new Reader(tokens(formula), new HashSet<>(ids));
        final Node root = r.expr(0);
        if (r.peek().type != END) {
            throw new Error("unexpected " + r.peek().text);
        }
        return new SimFormula(formula.trim(), root, r.used);
    }

    /** The formula, or null when it cannot be read. */
    public static SimFormula orNull(String formula, Collection<String> ids) {
        try {
            return parse(formula, ids);
        } catch (Error e) {
            return null;
        }
    }

    private static List<Token> tokens(String formula) throws Error {
        final String s = formula.replace('−', '-').replace('–', '-').replace('×', '*').replace('·', '*').replace('÷', '/');
        final List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (out.size() >= MAX_TOKENS) {
                throw new Error("more than " + MAX_TOKENS + " parts");
            }
            if (isDigit(c) || (c == '.' && i + 1 < s.length() && isDigit(s.charAt(i + 1)))) {
                int end = i;
                while (end < s.length() && isDigit(s.charAt(end))) {
                    end++;
                }
                if (end < s.length() && s.charAt(end) == '.') {
                    end++;
                    while (end < s.length() && isDigit(s.charAt(end))) {
                        end++;
                    }
                }
                // an exponent only when digits follow it: "2e3", "1e-3"
                if (end < s.length() && (s.charAt(end) == 'e' || s.charAt(end) == 'E')) {
                    int digits = end + 1;
                    if (digits < s.length() && (s.charAt(digits) == '+' || s.charAt(digits) == '-')) {
                        digits++;
                    }
                    if (digits < s.length() && isDigit(s.charAt(digits))) {
                        end = digits;
                        while (end < s.length() && isDigit(s.charAt(end))) {
                            end++;
                        }
                    }
                }
                final String number = s.substring(i, end);
                final double value;
                try {
                    value = Double.parseDouble(number);
                } catch (NumberFormatException e) {
                    throw new Error("not a number: " + number);
                }
                if (Double.isInfinite(value) || Double.isNaN(value)) {
                    throw new Error("too large: " + number);
                }
                out.add(new Token(NUMBER, number, value));
                i = end;
            } else if (isLetter(c)) {
                int end = i;
                while (end < s.length() && (isLetter(s.charAt(end)) || isDigit(s.charAt(end)))) {
                    end++;
                }
                out.add(new Token(NAME, s.substring(i, end), 0));
                i = end;
            } else if ((c == '<' || c == '>' || c == '=') && i + 1 < s.length() && s.charAt(i + 1) == '=') {
                out.add(new Token(SIGN, s.substring(i, i + 2), 0));
                i += 2;
            } else if ("+-*/^(),<>".indexOf(c) >= 0) {
                out.add(new Token(SIGN, String.valueOf(c), 0));
                i++;
            } else {
                throw new Error("unexpected " + c);
            }
        }
        out.add(new Token(END, "the end", 0));
        return out;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    /** expr := term (('+' | '-') term)*; term := unary (('*' | '/') unary)*; unary := '-' unary | power; power := primary ('^' unary)? */
    private static final class Reader {
        final List<Token> tokens;
        final Set<String> ids;
        final Set<String> used = new HashSet<>();
        int at;

        Reader(List<Token> tokens, Set<String> ids) {
            this.tokens = tokens;
            this.ids = ids;
        }

        Token peek() {
            return tokens.get(at);
        }

        boolean sign(String s) {
            final Token t = peek();
            if (t.type == SIGN && t.text.equals(s)) {
                at++;
                return true;
            }
            return false;
        }

        void need(String s) throws Error {
            if (!sign(s)) {
                throw new Error("expected " + s + " at " + peek().text);
            }
        }

        Node expr(int depth) throws Error {
            if (depth > MAX_DEPTH) {
                throw new Error("nested deeper than " + MAX_DEPTH);
            }
            Node left = term(depth);
            while (true) {
                if (sign("+")) {
                    left = new Bin('+', left, term(depth));
                } else if (sign("-")) {
                    left = new Bin('-', left, term(depth));
                } else {
                    return left;
                }
            }
        }

        Node term(int depth) throws Error {
            Node left = unary(depth);
            while (true) {
                if (sign("*")) {
                    left = new Bin('*', left, unary(depth));
                } else if (sign("/")) {
                    left = new Bin('/', left, unary(depth));
                } else {
                    return left;
                }
            }
        }

        Node unary(int depth) throws Error {
            if (depth > MAX_DEPTH) {
                throw new Error("nested deeper than " + MAX_DEPTH);
            }
            if (sign("-")) {
                return new Neg(unary(depth + 1));
            }
            if (sign("+")) {
                return unary(depth + 1);
            }
            final Node base = primary(depth);
            if (sign("^")) {
                // to the right first: 2^3^2 is 2^9, and -2^2 is -(2^2)
                return new Bin('^', base, unary(depth + 1));
            }
            return base;
        }

        Node primary(int depth) throws Error {
            final Token t = peek();
            if (t.type == NUMBER) {
                at++;
                return new Num(t.value);
            }
            if (t.type == NAME) {
                at++;
                if (sign("(")) {
                    return call(t.text, depth + 1);
                }
                if (!ids.contains(t.text)) {
                    throw new Error("unknown name " + t.text);
                }
                used.add(t.text);
                return new Var(t.text);
            }
            if (sign("(")) {
                final Node inner = expr(depth + 1);
                need(")");
                return inner;
            }
            throw new Error("unexpected " + t.text);
        }

        /** After "name(": the arguments and ")". */
        Node call(String name, int depth) throws Error {
            if (!FUNCTIONS.contains(name)) {
                throw new Error("unknown function " + name);
            }
            if ("if".equals(name)) {
                final Node left = expr(depth);
                final Token cmp = peek();
                if (cmp.type != SIGN || !Arrays.asList("<", ">", "<=", ">=", "==").contains(cmp.text)) {
                    throw new Error("if needs a comparison at " + cmp.text);
                }
                at++;
                final Node right = expr(depth);
                need(",");
                final Node then = expr(depth);
                need(",");
                final Node otherwise = expr(depth);
                need(")");
                return new If(cmp.text, left, right, then, otherwise);
            }
            final List<Node> args = new ArrayList<>();
            args.add(expr(depth));
            while (sign(",")) {
                if (args.size() >= MAX_ARGS) {
                    throw new Error("too many arguments of " + name);
                }
                args.add(expr(depth));
            }
            need(")");
            final boolean several = "min".equals(name) || "max".equals(name);
            final int wanted = "pow".equals(name) ? 2 : 1;
            if (several ? args.size() < 2 : args.size() != wanted) {
                throw new Error(name + " takes " + (several ? "two or more" : String.valueOf(wanted)));
            }
            return new Call(name, args.toArray(new Node[0]));
        }
    }

    // ------------------------------------------------------------------ working out

    /**
     * The formula's value for these inputs; {@link Double#NaN} when it has none: an input without
     * a value, a division by zero, a root or logarithm outside its range, or a result too large.
     */
    public double eval(Map<String, Double> values) {
        return finite(root.eval(values));
    }

    /** The inputs the formula names. */
    public Set<String> ids() {
        return used;
    }

    public String text() {
        return text;
    }
}
