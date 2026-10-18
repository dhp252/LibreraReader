package com.foobnix.annotation;

import java.util.Locale;

/**
 * Names and emoji of mark colors for the Markdown export: the toolbar's palette (Zotero's), and for
 * any other color (annotations other apps put in a PDF) the nearest one of it.
 */
public final class ColorNames {

    /** Zotero's annotation colors: the toolbar's palette. */
    public static final String[] PALETTE = {"#ffd400", "#ff6666", "#5fb236", "#2ea8e5", "#a28ae5", "#e56eee", "#f19837", "#aaaaaa", "#000000"};
    static final String[] NAMES = {"yellow", "red", "green", "blue", "purple", "magenta", "orange", "gray", "black"};
    static final String[] EMOJI = {
            "🟨", // yellow square
            "🟥", // red square
            "🟩", // green square
            "🟦", // blue square
            "🟪", // purple square
            "🩷", // pink heart
            "🟧", // orange square
            "⬜", // white square
            "⬛", // black square
    };

    private ColorNames() {
    }

    /** The palette color nearest to {@code color} ({@code #rrggbb}, or {@code #aarrggbb}). */
    static int nearest(String color) {
        final int rgb = parse(color);
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            final int p = parse(PALETTE[i]);
            final long dr = ((rgb >> 16) & 0xff) - ((p >> 16) & 0xff);
            final long dg = ((rgb >> 8) & 0xff) - ((p >> 8) & 0xff);
            final long db = (rgb & 0xff) - (p & 0xff);
            // weighted for the eye: green counts most, blue least
            final long distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static String name(String color) {
        return NAMES[nearest(color)];
    }

    public static String emoji(String color) {
        return EMOJI[nearest(color)];
    }

    /** "🟨 yellow". */
    public static String label(String color) {
        final int i = nearest(color);
        return EMOJI[i] + " " + NAMES[i];
    }

    /** {@code #rrggbb} of an RGB int (alpha dropped). */
    public static String hex(int color) {
        return String.format(Locale.US, "#%06x", color & 0xffffff);
    }

    private static int parse(String color) {
        if (color == null) {
            return 0xffd400;
        }
        String c = color.trim();
        if (c.startsWith("#")) {
            c = c.substring(1);
        }
        try {
            final long value = Long.parseLong(c, 16);
            return (int) (value & 0xffffff);
        } catch (NumberFormatException e) {
            return 0xffd400;
        }
    }
}
