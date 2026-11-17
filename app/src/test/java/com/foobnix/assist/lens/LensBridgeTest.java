package com.foobnix.assist.lens;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LensBridgeTest {

    @Test
    public void readsPairsAndTheNote() {
        final LensBridge b = LensBridge.parse("{\"pairs\":[{\"id\":3,\"author\":\"Division of labour\","
                + "\"yours\":\"Split a monolith into pipeline stages\"},{\"id\":\"5\",\"author\":\"Dexterity from practice\","
                + "\"quote\":\"the improvement of the dexterity of the workman\",\"yours\":\"A stage tuned for one job\"}],"
                + "\"breaks\":\"Services do not get bored.\"}");
        assertNotNull(b);
        assertEquals(2, b.rows.size());
        assertEquals("Division of labour", b.rows.get(0).idea);
        assertEquals("Split a monolith into pipeline stages", b.rows.get(0).yours);
        assertEquals(3, b.rows.get(0).id);
        assertEquals("", b.rows.get(0).quote);
        assertEquals(5, b.rows.get(1).id);
        assertEquals("the improvement of the dexterity of the workman", b.rows.get(1).quote);
        assertEquals("Services do not get bored.", b.breaks);
    }

    @Test
    public void toleratesFencesTextAroundAndOtherNames() {
        final LensBridge b = LensBridge.parse("Here is the bridge:\n```json\n{\"rows\": [{\"idea\": \" \\\"Exchange joins\\n the "
                + "specialists\\\" \", \"in_your_terms\": \"Contracts and APIs between services\"}], "
                + "\"where_it_breaks\": {\"text\": \"Markets have prices.\"}}\n```\nHope it helps.");
        assertNotNull(b);
        assertEquals("Exchange joins the specialists", b.rows.get(0).idea);
        assertEquals("Contracts and APIs between services", b.rows.get(0).yours);
        assertEquals(-1, b.rows.get(0).id);
        assertEquals("Markets have prices.", b.breaks);
    }

    @Test
    public void readsABareListOfPairs() {
        final LensBridge b = LensBridge.parse("[{\"author\":\"A\",\"yours\":\"B\"},{\"author\":\"C\",\"yours\":\"D\"}]");
        assertNotNull(b);
        assertEquals(2, b.rows.size());
        assertEquals("", b.breaks);
    }

    @Test
    public void leavesOutHalfPairsAndKeepsSix() {
        final StringBuilder json = new StringBuilder("{\"pairs\":[{\"author\":\"only one side\"},{\"yours\":\"only mine\"},7");
        for (int i = 0; i < 9; i++) {
            json.append(",{\"author\":\"a").append(i).append("\",\"yours\":\"y").append(i).append("\"}");
        }
        json.append("]}");
        final LensBridge b = LensBridge.parse(json.toString());
        assertNotNull(b);
        assertEquals(LensBridge.MAX_ROWS, b.rows.size());
        assertEquals("a0", b.rows.get(0).idea);
        assertEquals("y5", b.rows.get(5).yours);
    }

    @Test
    public void nothingReadableIsNull() {
        assertNull(LensBridge.parse(null));
        assertNull(LensBridge.parse(""));
        assertNull(LensBridge.parse("I cannot help with that."));
        assertNull(LensBridge.parse("{\"pairs\":[]}"));
        assertNull(LensBridge.parse("{\"pairs\":[{\"author\":\"cut off"));
        assertNull(LensBridge.parse("{\"breaks\":\"only a note\"}"));
    }

    @Test
    public void rowsFromIdeasTakeTheIdeasMarks() {
        final LensBridge b = LensBridge.parse("{\"pairs\":[{\"id\":2,\"author\":\"A\",\"quote\":\"made up\",\"yours\":\"B\"},"
                + "{\"id\":9,\"author\":\"C\",\"yours\":\"D\"},{\"id\":4,\"author\":\"E\",\"yours\":\"F\"}]}");
        assertNotNull(b);
        b.attach(Arrays.asList(new LensIdea(2, "idea two", "main", "the book's own words", 12),
                new LensIdea(4, "not located", "caveat", null, -1)));
        assertEquals("the book's own words", b.rows.get(0).quote);
        assertEquals(12, b.rows.get(0).page);
        assertTrue(b.rows.get(0).located());
        // an id the chapter does not have: no mark, and no made-up quote
        assertEquals(-1, b.rows.get(1).id);
        assertEquals("", b.rows.get(1).quote);
        assertFalse(b.rows.get(1).located());
        // an idea without a mark
        assertEquals(4, b.rows.get(2).id);
        assertFalse(b.rows.get(2).located());
    }

    @Test
    public void survivesTheStoreWithItsLadders() throws Exception {
        final LensBridge b = LensBridge.parse("{\"pairs\":[{\"author\":\"A\",\"yours\":\"B\"},{\"author\":\"C\",\"yours\":\"D\"}],"
                + "\"breaks\":\"note\"}");
        assertNotNull(b);
        b.title = "Of the Division of Labour";
        b.lens = "machine learning|ap-|new";
        b.lensName = "ML";
        b.fromIdeas = true;
        b.ideasTime = 42L;
        b.model = "m";
        b.rows.get(0).quote = "words";
        b.rows.get(0).page = 7;
        b.rows.get(1).ladder = LensLadder.parse("{\"principle\":\"p\",\"mechanism\":\"m\",\"example\":\"e\",\"world\":\"w\"}");
        final LensBridge back = LensBridge.fromJson(new JSONObject(b.toJson().toString()));
        assertNotNull(back);
        assertEquals("Of the Division of Labour", back.title);
        assertEquals(2, back.rows.size());
        assertEquals("words", back.rows.get(0).quote);
        assertEquals(7, back.rows.get(0).page);
        assertNull(back.rows.get(0).ladder);
        assertNotNull(back.rows.get(1).ladder);
        assertEquals("w", back.rows.get(1).ladder.world);
        assertEquals("note", back.breaks);
        assertTrue(back.fromIdeas);
        assertEquals(42L, back.ideasTime);
        assertEquals("ML", back.lensName);
        assertFalse(back.isOfAnotherLens("machine learning|ap-|new"));
        assertTrue(back.isOfAnotherLens("finance|ap-|new"));
    }

    @Test
    public void anItemWithoutRowsIsNoBridge() throws Exception {
        assertNull(LensBridge.fromJson(null));
        assertNull(LensBridge.fromJson(new JSONObject("{\"title\":\"x\",\"rows\":[]}")));
        assertEquals("lens:bridge:of the division of labour#0", LensBridge.key("of the division of labour#0"));
    }

    @Test
    public void cleanKeepsMarksThatBelongToWordsInside() {
        assertEquals("one line", LensBridge.clean("  \"one\n line\"  "));
        assertEquals("\"stock\" becomes \"savings\"", LensBridge.clean("\"stock\" becomes \"savings\""));
        assertEquals("bold", LensBridge.clean("**bold**"));
        assertEquals("", LensBridge.clean(null));
    }

    @Test
    public void readsALadder() {
        final LensLadder l = LensLadder.parse("```json\n{\"principle\": \"Splitting work multiplies output.\", "
                + "\"mechanism\": \"Practice, no switching, and machines.\", \"example\": \"Ten pin-makers, about 48,000 "
                + "pins a day.\", \"world\": \"One person owns the labeling pipeline.\"}\n```");
        assertNotNull(l);
        assertTrue(l.isComplete());
        assertEquals("Splitting work multiplies output.", l.rung(LensLadder.PRINCIPLE));
        assertEquals("Practice, no switching, and machines.", l.rung(LensLadder.MECHANISM));
        assertEquals("Ten pin-makers, about 48,000 pins a day.", l.rung(LensLadder.EXAMPLE));
        assertEquals("One person owns the labeling pipeline.", l.rung(LensLadder.WORLD));
        assertEquals("", l.rung(0));
    }

    @Test
    public void readsALadderByOtherNamesOrAsAList() {
        final LensLadder named = LensLadder.parse("{\"principle\":\"p\",\"mechanism\":\"m\",\"authors_example\":\"e\","
                + "\"your_world\":\"w\"}");
        assertNotNull(named);
        assertEquals("e", named.example);
        assertEquals("w", named.world);

        final LensLadder listed = LensLadder.parse("{\"rungs\":[\"p\",{\"text\":\"m\"},\"e\",\"w\"]}");
        assertNotNull(listed);
        assertEquals("p", listed.principle);
        assertEquals("m", listed.mechanism);
        assertEquals("e", listed.example);
        assertEquals("w", listed.world);
    }

    @Test
    public void aLadderWithAMissingRungIsNone() throws Exception {
        assertNull(LensLadder.parse("{\"principle\":\"p\",\"mechanism\":\"m\",\"example\":\"e\"}"));
        assertNull(LensLadder.parse("no json"));
        assertNull(LensLadder.parse(null));
        assertNull(LensLadder.fromJson(null));
        assertNull(LensLadder.fromJson(new JSONObject("{\"principle\":\"p\"}")));
        final LensLadder l = LensLadder.parse("{\"principle\":\"p\",\"mechanism\":\"m\",\"example\":\"e\",\"world\":\"w\"}");
        assertNotNull(l);
        final LensLadder back = LensLadder.fromJson(new JSONObject(l.toJson().toString()));
        assertNotNull(back);
        assertEquals("m", back.mechanism);
    }
}
