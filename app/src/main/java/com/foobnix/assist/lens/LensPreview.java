package com.foobnix.assist.lens;

/**
 * The PREVIEW card of the lens setup (canvas LensSetup): one fixed phrase of Adam Smith, "the
 * division of labour", and how the lens being set up would put it. Written here for each field
 * offered, so setting up the lens asks the AI nothing. No Android here.
 */
public final class LensPreview {

    /** Whose phrase the preview shows. */
    public static final String AUTHOR = "Smith";
    /** The phrase, as the author wrote it. */
    public static final String PHRASE = "the division of labour";

    /** The phrase in plain words, for a lens without analogies. */
    static final String PLAIN = "splitting one job into small steps, each done by one person";

    private static final String[] YOURS = {
            "splitting a monolith into pipeline stages, each owned by one person",
            "splitting a monolith into services, each owned by one team",
            "splitting an analysis into cleaning, modelling and checking, each done by a specialist",
            "splitting a launch into research, design and build, each owned by one person",
            "splitting a deal into origination, pricing and settlement, each with its own desk",
            "a surgical team: surgeon, anaesthetist and scrub nurse each do one part",
            "a kitchen brigade: one cook per station instead of everyone cooking whole dishes",
            "fixed positions: keeper, defenders and strikers instead of eleven players chasing the ball"};

    private LensPreview() {
    }

    /**
     * The "You" line: the phrase through the first field of the lens; plain words when analogies
     * are off; the author's term kept in front when the lens keeps key terms. Empty without a field.
     */
    public static String yours(LensProfile profile) {
        if (profile == null || !profile.isSet()) {
            return "";
        }
        final String text = profile.analogies ? analogy(profile.allFields().get(0)) : PLAIN;
        return profile.keepTerms ? PHRASE + ": " + text : text;
    }

    static String analogy(String field) {
        for (int i = 0; i < LensProfile.PRESETS.length; i++) {
            if (LensProfile.PRESETS[i].equalsIgnoreCase(field)) {
                return YOURS[i];
            }
        }
        return "how work in " + field + " is split into steps, each done by whoever knows it best";
    }
}
