package com.foobnix.assist.xray;

import com.foobnix.ai.AiCategory;

/**
 * The job a marked passage does in the argument, as the canvas's X-ray shows it: a claim (tinted),
 * evidence (accent underline), an example (dotted grey underline), a caveat (muted). Concepts and
 * "apply" marks have no X-ray kind of their own and keep a light fill ({@link #OTHER}).
 */
public enum XrayKind {
    CLAIM, EVIDENCE, EXAMPLE, CAVEAT, OTHER;

    /** The X-ray kind of a key passage's category (null: a main idea). */
    public static XrayKind of(AiCategory category) {
        if (category == null) {
            return CLAIM;
        }
        switch (category) {
            case MAIN:
                return CLAIM;
            case EVIDENCE:
                return EVIDENCE;
            case EXAMPLE:
                return EXAMPLE;
            case CAVEAT:
                return CAVEAT;
            default:
                return OTHER;
        }
    }
}
