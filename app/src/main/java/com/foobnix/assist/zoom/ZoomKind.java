package com.foobnix.assist.zoom;

import java.util.Locale;

/** The job a row of the Claims level does (canvas ZoomClaims: CLAIM, EVIDENCE, WHY · 1, CAVEAT, SO WHAT). */
public enum ZoomKind {
    CLAIM("claim"), EVIDENCE("evidence"), WHY("why"), CAVEAT("caveat"), SO_WHAT("so_what");

    /** Its name in the request, the answer and the stored data. */
    public final String id;

    ZoomKind(String id) {
        this.id = id;
    }

    /** The kind a model named, also by a near word ("reason", "implication"); a claim when unknown. */
    public static ZoomKind of(String name) {
        final String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (ZoomKind k : values()) {
            if (k.id.equals(n)) {
                return k;
            }
        }
        switch (n) {
            case "reason":
            case "because":
            case "cause":
            case "mechanism":
                return WHY;
            case "example":
            case "data":
            case "fact":
            case "support":
                return EVIDENCE;
            case "limit":
            case "limitation":
            case "objection":
            case "exception":
            case "qualification":
                return CAVEAT;
            case "sowhat":
            case "implication":
            case "consequence":
            case "conclusion":
            case "upshot":
                return SO_WHAT;
            default:
                return CLAIM;
        }
    }
}
