package com.foobnix.assist.pictures;

/**
 * The part of the pictures' one request that asks for a small model to play with (canvas Picture
 * "TRY IT", PictureSim): what a model is, and the only formula language the app reads
 * ({@link SimFormula}). It rides in the same request as the pictures, so a chapter still costs
 * one request. Plain Java.
 */
final class SimPrompts {

    private SimPrompts() {
    }

    /** One more rule of the system prompt. */
    static final String RULES = "- \"sim\" (beside \"pictures\", at most one): only when the text gives numbers that depend on "
            + "one another, a small model the reader can play with; else leave it out. \"statement\": the rule in one or "
            + "two sentences, as the text states it. \"quote\": as for a picture. \"inputs\" (1 to 4), each with an \"id\" "
            + "(a short name of letters), a \"label\" and a \"unit\": either a slider with \"min\", \"max\", \"step\" and "
            + "\"start\", where start is the value the text gives; or \"kind\":\"switch\" with the names \"off\" and \"on\" "
            + "of its two states (its value is 0 or 1) and \"start\". \"outputs\" (1 to 3): a \"label\", a \"unit\" and a "
            + "\"formula\" over the input ids. \"presets\" (up to 4): a \"label\" of 1 to 3 words and \"values\" by input id. "
            + "\"curve\" (optional): \"x\", the id of the slider along its axis, a \"formula\" to plot and a \"label\". "
            + "A formula is plain arithmetic and nothing else: numbers, the input ids, + - * / ^, parentheses, "
            + "min(a, b), max(a, b), abs(a), sqrt(a), exp(a), ln(a), pow(a, b), and if(a < b, x, y) with < > <= >= ==. "
            + "No other names, no loops, no text, no code: the app works the formula out itself and runs nothing; a "
            + "model with a formula it cannot read is thrown away. Use the text's own numbers as start values and in "
            + "the formulas; a model with other numbers is shown as an illustration, not the book's numbers.\n";

    /** The model of the answer's example, to follow the list of pictures inside the same object. */
    static final String EXAMPLE = "\"sim\":{\"title\":\"Same 10 people, two ways\",\"statement\":\"...\",\"quote\":\"...\","
            + "\"inputs\":[{\"id\":\"split\",\"kind\":\"switch\",\"label\":\"Split the work\",\"off\":\"Everyone makes whole "
            + "pins\",\"on\":\"Split into steps\",\"start\":1},{\"id\":\"people\",\"label\":\"People\",\"unit\":\"\","
            + "\"min\":1,\"max\":20,\"step\":1,\"start\":10}],\"outputs\":[{\"label\":\"Pins a day\",\"unit\":\"pins\","
            + "\"formula\":\"if(split == 1, 4800 * people, 20 * people)\"}],\"presets\":[{\"label\":\"One alone\","
            + "\"values\":{\"split\":0,\"people\":1}}],\"curve\":{\"x\":\"people\",\"formula\":\"if(split == 1, 4800 * people, "
            + "20 * people)\",\"label\":\"Pins a day by people\"},\"note\":\"...\"}";
}
