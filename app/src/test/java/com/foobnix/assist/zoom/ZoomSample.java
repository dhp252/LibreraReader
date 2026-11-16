package com.foobnix.assist.zoom;

import java.util.Arrays;
import java.util.List;

/** A small chapter in three pages (after Adam Smith), and an answer a model could give for it. */
final class ZoomSample {

    private ZoomSample() {
    }

    static final String P1 = "The greatest improvement in the productive powers of labour seems to have been the effect of "
            + "the division of labour.";
    static final String P2 = "To take an example, therefore, from a very trifling manufacture, the trade of the pin-maker; a "
            + "workman not educated to this business could scarce, perhaps, with his utmost industry, make one pin in a "
            + "day, and certainly could not make twenty.";
    static final String P3A = "I have seen a small manufactory of this kind where ten men only were employed. Those ten "
            + "persons, therefore, could make among them";
    static final String P3B = "upwards of forty-eight thousand pins in a day.";
    static final String P4 = "In every other art and manufacture, the effects of the division of labour are similar to what "
            + "they are in this very trifling one. The nature of agriculture, indeed, does not admit of so many "
            + "subdivisions of labour.";
    static final String P5 = "This great increase of the quantity of work is owing to three different circumstances; first, "
            + "to the increase of dexterity in every particular workman.";
    static final String P6 = "It is the great multiplication of the productions of all the different arts which occasions "
            + "that universal opulence which extends itself to the lowest ranks of the people.";

    /** Paragraph 3 runs over the edge of pages 1 and 2. */
    static List<String> pages() {
        return Arrays.asList(P1 + "\n\n" + P2 + "\n\n" + P3A, P3B + "\n\n" + P4, P5 + "\n\n" + P6);
    }

    static ZoomText text() {
        return ZoomText.of(pages());
    }

    static final String ANSWER = "Here is the chapter:\n```json\n{\"line\": \"Splitting work multiplies what people make.\","
            + "\"sections\": ["
            + "{\"title\": \"The pin shop\", \"line\": \"Ten specialists make 48,000 pins a day.\", "
            + "\"question\": \"Why can ten specialists make so many pins?\", "
            + "\"start\": \"The greatest improvement in the productive powers of labour\", \"lean\": ["
            + "{\"kept\": \"best example\", \"sentences\": ["
            + "{\"text\": \"Take pin-making: an untrained worker makes one pin a day.\", "
            + "\"quote\": \"To take an example, therefore, from a very trifling manufacture\", \"end\": \"could not make twenty.\"},"
            + "{\"text\": \"Ten workers made 48,000 pins a day.\", "
            + "\"quote\": \"I have seen a small manufactory of this kind\", \"end\": \"thousand pins in a day.\"}]},"
            + "{\"folded\": \"other trades, and why farming lags\", "
            + "\"quote\": \"In every other art and manufacture, the effects of the division\"}]},"
            + "{\"title\": \"Why it works\", \"line\": \"Practice makes each worker faster.\", \"question\": null, "
            + "\"start\": \"This great increase of the quantity of work is owing\", \"lean\": ["
            + "{\"kept\": \"\", \"sentences\": ["
            + "{\"text\": \"It works because practice builds dexterity.\", "
            + "\"quote\": \"This great increase of the quantity of work\", \"end\": \"every particular workman.\"},"
            + "{\"text\": \"A sentence the model made up.\", \"quote\": \"These words are nowhere in the chapter at all\", "
            + "\"end\": \"nowhere\"}]}]}],"
            + "\"claims\": ["
            + "{\"kind\": \"claim\", \"text\": \"Splitting work multiplies output.\", \"example\": \"\", "
            + "\"quote\": \"The greatest improvement in the productive powers of labour seems to have been the effect of the division of labour.\"},"
            + "{\"kind\": \"evidence\", \"text\": \"Ten pin workers made 48,000 pins a day.\", \"example\": \"the pin shop\", "
            + "\"quote\": \"Those ten persons, therefore, could make among them upwards of forty-eight thousand pins in a day.\"},"
            + "{\"kind\": \"why\", \"text\": \"Practice builds dexterity.\", "
            + "\"quote\": \"first, to the increase of dexterity in every particular workman.\"},"
            + "{\"kind\": \"reason\", \"text\": \"No time is lost switching.\", \"quote\": \"not in the chapter, not at all, never\"},"
            + "{\"kind\": \"caveat\", \"text\": \"Farming cannot be split as finely.\", "
            + "\"quote\": \"The nature of agriculture, indeed, does not admit of so many subdivisions of labour.\"},"
            + "{\"kind\": \"so what\", \"text\": \"Plenty reaches the lowest ranks.\", "
            + "\"quote\": \"that universal opulence which extends itself to the lowest ranks of the people.\"}]}\n```";
}
