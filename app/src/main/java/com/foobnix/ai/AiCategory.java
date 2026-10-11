package com.foobnix.ai;

/**
 * Why the AI marked a passage, and the color it is drawn in. Yellow is the reader's own
 * highlighter and never an AI color. Design: .agents/planning/ai-highlight-categories-design.md.
 */
public enum AiCategory {

    MAIN("main", 0xffff6666, "Main idea", "The author's core claim or thesis for a section.",
            "If I kept only one sentence here, is it this?"),
    EVIDENCE("evidence", 0xff2ea8e5, "Evidence", "Data, studies or reasoning that support a claim.",
            "Does this show why I should believe the claim?"),
    CONCEPT("concept", 0xffa28ae5, "Concept", "A new term, definition, framework or model.",
            "Will the author use this word or idea again later?"),
    CAVEAT("caveat", 0xfff19837, "Caveat", "Limitations, exceptions, counterarguments, a \"but\".",
            "Does this narrow or challenge a claim?"),
    APPLY("apply", 0xff5fb236, "Apply", "Something the reader could act on, judged against their stated goal.",
            "Could I do this or use this for my goal?"),
    EXAMPLE("example", 0xffaaaaaa, "Example", "Stories, anecdotes, cases.",
            "Is this illustrating a point made elsewhere?");

    /** The reader's own highlighter color: the AI never uses it. */
    public static final int READER_YELLOW = 0xffffd400;

    /** How it is written in the model's answers and in storage. */
    public final String code;
    public final int color;
    /** For the prompt (English, as the rest of the prompt). */
    public final String promptName;
    public final String promptMeaning;
    public final String promptTest;

    AiCategory(String code, int color, String promptName, String promptMeaning, String promptTest) {
        this.code = code;
        this.color = color;
        this.promptName = promptName;
        this.promptMeaning = promptMeaning;
        this.promptTest = promptTest;
    }

    /** The category written as {@code code} (any case, surrounding spaces), or null when unknown. */
    public static AiCategory of(String code) {
        if (code == null) {
            return null;
        }
        final String c = code.trim().toLowerCase(java.util.Locale.US);
        for (AiCategory category : values()) {
            if (category.code.equals(c)) {
                return category;
            }
        }
        return null;
    }
}
