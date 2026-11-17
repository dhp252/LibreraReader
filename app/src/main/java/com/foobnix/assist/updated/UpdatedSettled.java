package com.foobnix.assist.updated;

import java.util.Locale;

/** How settled what a note says is, as the model itself rates it ("How settled" on the note sheet). */
public enum UpdatedSettled {
    ESTABLISHED("well established", 3),
    DEBATED("debated", 2),
    SPECULATIVE("speculative", 1),
    /** The model did not say: shown as not stated, never as established. */
    UNKNOWN("", 0);

    public final String code;
    /** Bars filled out of three. */
    public final int bars;

    UpdatedSettled(String code, int bars) {
        this.code = code;
        this.bars = bars;
    }

    /** Tolerant of the model's wording; anything unclear is {@link #UNKNOWN}. */
    public static UpdatedSettled of(String s) {
        if (s == null) {
            return UNKNOWN;
        }
        final String k = s.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        if (k.isEmpty()) {
            return UNKNOWN;
        }
        // "not well established", "not settled": the weaker word wins
        if (k.contains("specul") || k.contains("guess") || k.contains("uncertain") || k.contains("unsettled")
                || k.startsWith("not ") || k.contains("minority")) {
            return SPECULATIVE;
        }
        if (k.contains("debat") || k.contains("disput") || k.contains("contested") || k.contains("mixed")) {
            return DEBATED;
        }
        if (k.contains("establish") || k.contains("settled") || k.contains("documented") || k.contains("accepted")
                || k.contains("consensus")) {
            return ESTABLISHED;
        }
        return UNKNOWN;
    }
}
