package com.foobnix.assist.pictures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Text and JSON helpers of the pictures: one-line labels with a length limit, tolerant field reading. Plain Java. */
final class PictureText {

    private PictureText() {
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** One line, {@code max} characters at most, cut at a space with "…". */
    static String cut(String s, int max) {
        final String line = oneLine(s);
        if (line.length() <= max) {
            return line;
        }
        int end = line.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return line.substring(0, end).trim() + "…";
    }

    /** Whole words of {@code s}, {@code max} characters at most, nothing added (the book's words stay its own). */
    static String cutWords(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        final int end = s.lastIndexOf(' ', max);
        return end <= 0 ? s.substring(0, max) : s.substring(0, end).trim();
    }

    /** The JSON object of a model's answer, whatever stands around it (code fences, a sentence); null when none. */
    static JSONObject object(String answer) {
        if (answer == null) {
            return null;
        }
        final int brace = answer.indexOf('{');
        final int end = answer.lastIndexOf('}');
        final int open = answer.indexOf('[');
        final int close = answer.lastIndexOf(']');
        if (open >= 0 && close > open && (brace < 0 || open < brace)) {
            // a bare list of pictures
            try {
                return new JSONObject().put("pictures", new JSONArray(answer.substring(open, close + 1)));
            } catch (JSONException e) {
                // an object after all
            }
        }
        if (brace >= 0 && end > brace) {
            try {
                return new JSONObject(answer.substring(brace, end + 1));
            } catch (JSONException e) {
                return null;
            }
        }
        return null;
    }

    /** The first of {@code names} that holds text or a number, as a label of {@code max} characters; else empty. */
    static String text(JSONObject o, int max, String... names) {
        if (o == null) {
            return "";
        }
        for (String name : names) {
            final String s = plain(o.opt(name));
            if (!s.isEmpty()) {
                return cut(s, max);
            }
        }
        return "";
    }

    /** A value as text: a string, or a number without a needless ".0"; empty for anything else and for "null". */
    static String plain(Object v) {
        if (v instanceof String) {
            final String s = oneLine((String) v);
            return "null".equalsIgnoreCase(s) ? "" : s;
        }
        if (v instanceof Number) {
            final double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "";
            }
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : String.valueOf(v);
        }
        return "";
    }

    /** The first of {@code names} that holds a list; null when none does. */
    static JSONArray list(JSONObject o, String... names) {
        if (o == null) {
            return null;
        }
        for (String name : names) {
            final JSONArray a = o.optJSONArray(name);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    /** A number given as a number or as text ("≈ 4", "48,000"); NaN when there is none. */
    static double number(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            final String s = ((String) v).replace(",", "").replace("−", "-").replaceAll("[^0-9.eE+-]", "");
            try {
                return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }
}
