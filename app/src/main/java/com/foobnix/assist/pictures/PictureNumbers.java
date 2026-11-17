package com.foobnix.assist.pictures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers of a text, for the rule "a number shown on a picture must be in the text sent":
 * numbers written in digits ("48,000", "0.3", "20%", "18th") and English number words
 * ("eighteen", "forty-eight thousand", "two hundred and forty"). Each is kept in one plain form
 * ("48000", "0.3"), without its sign. Other languages are matched by their digits only.
 * Plain Java, no Android.
 */
public final class PictureNumbers {

    /** Number words up to this value are everyday words ("one loop, two brakes"): not checked on a picture. */
    static final int SMALL_WORD = 12;

    private static final Pattern DIGITS = Pattern.compile("\\d{1,3}(?:[,\\u00A0\\u202F\\u2009]\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    private static final Map<String, Integer> UNITS = new HashMap<>();
    private static final Map<String, Integer> TEENS = new HashMap<>();
    private static final Map<String, Integer> TENS = new HashMap<>();
    private static final Map<String, Long> SCALES = new HashMap<>();

    static {
        final String[] units = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        for (int i = 0; i < units.length; i++) {
            UNITS.put(units[i], i + 1);
        }
        final String[] teens = {"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
                "eighteen", "nineteen"};
        for (int i = 0; i < teens.length; i++) {
            TEENS.put(teens[i], i + 10);
        }
        final String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < tens.length; i++) {
            TENS.put(tens[i], (i + 2) * 10);
        }
        SCALES.put("thousand", 1_000L);
        SCALES.put("million", 1_000_000L);
        SCALES.put("billion", 1_000_000_000L);
    }

    private PictureNumbers() {
    }

    /** Every number of a text, in digits or in English words. */
    public static Set<String> of(String text) {
        return of(text, 0);
    }

    /** The numbers a line of a picture shows: its digits, and its number words above {@link #SMALL_WORD}. */
    public static Set<String> shown(String line) {
        return of(line, SMALL_WORD + 1);
    }

    /** Whether every number a line of a picture shows is among {@code known}. An empty line holds none. */
    public static boolean allIn(String line, Set<String> known) {
        for (String n : shown(line)) {
            if (!known.contains(n)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a value is among {@code known} (by its size, without its sign). */
    public static boolean has(double value, Set<String> known) {
        final String key = canon(value);
        return key != null && known.contains(key);
    }

    /** The plain form of a value ("48000", "0.3"), rounded to 9 digits; null when it is not a number. */
    public static String canon(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return plain(new BigDecimal(Math.abs(value)).round(new MathContext(9)));
    }

    private static String plain(BigDecimal n) {
        if (n.signum() == 0) {
            return "0";
        }
        return n.stripTrailingZeros().toPlainString();
    }

    private static Set<String> of(String text, int minWord) {
        final Set<String> out = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        final Matcher m = DIGITS.matcher(text);
        while (m.find()) {
            try {
                out.add(plain(new BigDecimal(m.group().replaceAll("[^0-9.]", ""))));
            } catch (NumberFormatException e) {
                // not a number after all ("1.2.3" is read as 1.2 and 3)
            }
        }
        words(text, minWord, out);
        return out;
    }

    /** Number words: "forty-eight thousand" is one number, "two or three" are two. */
    private static void words(String text, int min, Set<String> out) {
        final Matcher m = WORD.matcher(text);
        long total = 0;
        long current = 0;
        boolean open = false;
        // what the next word may still add: 2 = tens, teens or units, 1 = units only, 0 = nothing
        int room = 2;
        boolean pendingAnd = false;
        while (m.find()) {
            final String w = m.group().toLowerCase(Locale.US);
            final Integer unit = UNITS.get(w);
            final Integer teen = TEENS.get(w);
            final Integer ten = TENS.get(w);
            final Long scale = SCALES.get(w);
            if (open && "and".equals(w) && !pendingAnd) {
                // "two hundred and forty": joins only when a number word follows
                pendingAnd = true;
                continue;
            }
            final boolean number = unit != null || teen != null || ten != null || scale != null
                    || "hundred".equals(w) || "dozen".equals(w);
            if (!number) {
                if (open) {
                    add(total + current, min, out);
                }
                total = 0;
                current = 0;
                open = false;
                room = 2;
                pendingAnd = false;
                continue;
            }
            pendingAnd = false;
            if (unit != null || teen != null || ten != null) {
                final int need = unit != null ? 1 : 2;
                if (open && room < need) {
                    // "one two three": each its own number
                    add(total + current, min, out);
                    total = 0;
                    current = 0;
                }
                current += unit != null ? unit : teen != null ? teen : ten;
                room = ten != null ? 1 : 0;
            } else if ("hundred".equals(w)) {
                current = Math.max(1, current) * 100;
                room = 2;
            } else if ("dozen".equals(w)) {
                current = Math.max(1, current) * 12;
                room = 0;
            } else {
                total += Math.max(1, current) * scale;
                current = 0;
                room = 2;
            }
            open = true;
            if (total > 1_000_000_000_000_000L) {
                // not a number anyone wrote: start over
                total = 0;
                current = 0;
                open = false;
            }
        }
        if (open) {
            add(total + current, min, out);
        }
    }

    private static void add(long value, int min, Set<String> out) {
        if (value >= min && value > 0) {
            out.add(Long.toString(value));
        }
    }
}
