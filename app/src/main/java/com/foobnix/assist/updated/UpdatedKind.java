package com.foobnix.assist.updated;

import java.util.Locale;

/** The four kinds of note "The book, updated" writes (canvas UpdatedMargin). */
public enum UpdatedKind {
    /** Where a fact, figure or story of the author really comes from, or whether it checks out. */
    SOURCE("source"),
    /** A cause or explanation the author did not name. */
    MISSED("missed"),
    /** A story or fact that is doubted or known to be wrong. */
    DOUBTFUL("doubtful"),
    /** How later research or events changed the picture. */
    NEWER("newer");

    /** How the kind is written in a request and in stored data. */
    public final String code;

    UpdatedKind(String code) {
        this.code = code;
    }

    /** The kind a model named ("missed", "Missed cause", "source_check"); null when it is none of the four. */
    public static UpdatedKind of(String s) {
        if (s == null) {
            return null;
        }
        final String k = s.trim().toLowerCase(Locale.ROOT);
        if (k.startsWith("source") || k.startsWith("fact")) {
            return SOURCE;
        }
        if (k.startsWith("miss")) {
            return MISSED;
        }
        if (k.startsWith("doubt") || k.startsWith("disputed") || k.startsWith("myth")) {
            return DOUBTFUL;
        }
        if (k.startsWith("new") || k.startsWith("later") || k.startsWith("update")) {
            return NEWER;
        }
        return null;
    }
}
