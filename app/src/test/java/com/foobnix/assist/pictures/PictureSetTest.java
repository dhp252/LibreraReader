package com.foobnix.assist.pictures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PictureSetTest {

    static final String WIRE = "One man draws out the wire, another straights it, a third cuts it, a fourth points it,"
            + " a fifth grinds it at the top for receiving the head.";
    static final String HEAD = "To make the head requires two or three distinct operations; the whole work is divided"
            + " into about eighteen distinct operations.";
    static final String PINS = "Those ten persons could make among them upwards of forty-eight thousand pins in a day.";
    static final String CORN = "The corn of Poland, in the same degree of goodness, is as cheap as that of France.";
    static final String MARKET = "The division of labour is limited by the extent of the market.";
    static final String WAGON = "A broad-wheeled waggon, attended by two men and drawn by eight horses, carries about"
            + " four ton weight of goods; a ship navigated by six or eight men carries 200 ton weight.";

    /** The chapter: two pages. */
    static PictureSource chapter() {
        final List<String> words = new ArrayList<>();
        final List<Integer> pages = new ArrayList<>();
        final StringBuilder plain = new StringBuilder();
        for (String s : new String[]{WIRE, HEAD, PINS}) {
            plain.append(s).append(' ');
            for (String w : s.split(" ")) {
                words.add(w);
                pages.add(4);
            }
        }
        for (String s : new String[]{CORN, MARKET, WAGON}) {
            plain.append(s).append(' ');
            for (String w : s.split(" ")) {
                words.add(w);
                pages.add(5);
            }
        }
        return PictureSource.ofText(words, pages, plain.toString());
    }

    private static final String PROCESS = "{\"kind\":\"process\",\"quote\":\"“One man draws out the wire, another straights"
            + " it, a third cuts it, a fourth points it…”\",\"title\":\"One pin, about eighteen operations\",\"steps\":["
            + "{\"label\":\"Draw wire\",\"note\":\"\"},{\"label\":\"Straighten\"},\"Cut\",{\"label\":\"Point\"},"
            + "{\"label\":\"Make head\",\"note\":\"×2–3\"},{\"label\":\"Whiten\",\"note\":\"×7\"}],"
            + "\"caption\":\"Smith names these; with sub-steps there are about 18.\","
            + "\"why_title\":\"Why it works: three causes\",\"why\":[{\"title\":\"Dexterity\",\"line\":\"One task, done"
            + " thousands of times.\"},{\"title\":\"Machines\",\"line\":\"Invented in 1755.\"},{\"title\":\"Half\"}]}";

    private static final String TABLE = "{\"kind\":\"table\",\"quote\":\"" + CORN + "\",\"title\":\"Who wins, product by"
            + " product\",\"corner\":\"Product\",\"columns\":[\"Poland\",\"France\",\"England\"],\"rows\":["
            + "{\"label\":\"Corn\",\"cells\":[\"as cheap as France's\",\"about England's price\",\"about France's price\"],"
            + "\"best\":[]},{\"label\":\"Silks\",\"cells\":[\"scarcely made\",\"better and cheaper\",\"dearer, by 30%\"],"
            + "\"best\":[1,2]},{\"label\":\"Hardware\",\"cells\":[\"\",\"inferior\",\"far better\"],\"best\":2}],"
            + "\"best_means\":\"clear winner, in Smith's words\",\"point\":\"Rich countries pull ahead in manufactures.\"}";

    private static final String LOOP = "{\"kind\":\"loop\",\"quote\":\"" + MARKET + "\",\"title\":\"Why bigger markets mean"
            + " more splitting\",\"nodes\":[\"A bigger market\",\"Work split more finely\",\"More output per worker\","
            + "\"Cheaper goods\"],\"type\":\"reinforcing\",\"label\":\"feeds itself\",\"holds_back\":{\"what\":\"Transport\","
            + "\"basis\":\"The same road, London to Edinburgh\",\"compare\":[{\"label\":\"Wagon · 2 men, 8 horses\","
            + "\"value\":\"≈ 4 tons\",\"number\":4},{\"label\":\"Ship · 6 to 8 sailors\",\"value\":\"≈ 200 tons\","
            + "\"number\":200},{\"label\":\"Train\",\"value\":\"900 tons\",\"number\":900}],\"note\":\"So splitting work"
            + " starts on coasts and rivers.\"}}";

    private static final String ANSWER = "Here are the pictures:\n```json\n{\"pictures\":[" + PROCESS + "," + TABLE + ","
            + LOOP + "]}\n```";

    @Test
    public void readsTheThreeKindsAndKeepsOnlyWhatTheTextCarries() {
        final PictureSet set = PictureSet.parse(ANSWER, chapter());
        assertNotNull(set);
        assertEquals(3, set.pictures.size());
        assertEquals(0, set.leftOut);
        assertFalse(set.fromIdeas);

        final Picture process = set.of(Picture.Kind.PROCESS);
        // the quote is the book's own words, found by the piece before the "…"
        assertEquals("One man draws out the wire, another straights it, a third cuts it, a fourth points it,", process.quote);
        assertEquals(4, process.page);
        assertEquals("One pin, about eighteen operations", process.title);
        assertEquals(6, process.steps.size());
        assertEquals("Cut", process.steps.get(2).label);
        assertEquals("×2–3", process.steps.get(4).note);
        // "×7" is not in the text: the note goes, the step stays
        assertEquals("Whiten", process.steps.get(5).label);
        assertEquals("", process.steps.get(5).note);
        // "18" stands in the text as "eighteen"
        assertEquals("Smith names these; with sub-steps there are about 18.", process.caption);
        // 1755 is not in the text, and half a card is no card
        assertEquals(1, process.cards.size());
        assertEquals("Dexterity", process.cards.get(0).title);
        assertEquals("Why it works: three causes", process.cardsTitle);

        final Picture table = set.of(Picture.Kind.TABLE);
        assertEquals(5, table.page);
        assertEquals("Product", table.corner);
        assertEquals(Arrays.asList("Poland", "France", "England"), table.columns);
        assertEquals(3, table.rows.size());
        assertEquals(Arrays.asList(false, false, false), table.rows.get(0).best);
        // "30%" is not in the text: the cell is emptied and cannot be the best
        assertEquals(Arrays.asList("scarcely made", "better and cheaper", ""), table.rows.get(1).cells);
        assertEquals(Arrays.asList(false, true, false), table.rows.get(1).best);
        assertEquals(Arrays.asList(false, false, true), table.rows.get(2).best);
        assertEquals("", table.rows.get(2).cells.get(0));
        assertEquals("clear winner, in Smith's words", table.bestMeans);
        assertEquals("Rich countries pull ahead in manufactures.", table.point);

        final Picture loop = set.of(Picture.Kind.LOOP);
        assertEquals(4, loop.nodes.size());
        assertEquals(1, loop.loopType);
        assertEquals("feeds itself", loop.loopLabel);
        assertEquals("Transport", loop.holds);
        // the train's 900 tons are not in the text
        assertEquals(2, loop.quantities.size());
        assertEquals("≈ 200 tons", loop.quantities.get(1).value);
        assertEquals(200, loop.quantities.get(1).number, 0);
        assertTrue(loop.hasHolds());
        assertEquals(4, set.numbersOut);
    }

    @Test
    public void aPictureWhoseWordsAreNotInTheTextIsLeftOut() {
        final String made = PROCESS.replace("One man draws out the wire, another straights it, a third cuts it, a fourth"
                + " points it…", "Pins are made in eighteen steps by ten men working together in one small room.");
        final PictureSet set = PictureSet.parse("{\"pictures\":[" + made + "," + LOOP + "]}", chapter());
        assertNotNull(set);
        assertEquals(1, set.pictures.size());
        assertEquals(Picture.Kind.LOOP, set.pictures.get(0).kind);
        assertEquals(1, set.leftOut);
        // no quote at all
        final PictureSet none = PictureSet.parse("{\"pictures\":[" + LOOP.replace(MARKET, "") + "]}", chapter());
        assertTrue(none.isEmpty());
        assertEquals(1, none.leftOut);
    }

    @Test
    public void picturesNamedByTheirKind() {
        final String loop = LOOP.replace("\"kind\":\"loop\",", "\"kind\":\"balancing\",").replace("\"type\":\"reinforcing\",", "");
        final PictureSet set = PictureSet.parse("{\"process\":" + PROCESS + ",\"loop\":" + loop + "}", chapter());
        assertNotNull(set);
        assertEquals(2, set.pictures.size());
        assertEquals(Picture.Kind.PROCESS, set.pictures.get(0).kind);
        assertEquals(-1, set.of(Picture.Kind.LOOP).loopType);
        // a bare list
        final PictureSet bare = PictureSet.parse("[" + TABLE + "]", chapter());
        assertEquals(1, bare.pictures.size());
        // the model did not say which way the loop goes
        final PictureSet unsaid = PictureSet.parse("[" + LOOP.replace("\"type\":\"reinforcing\",", "") + "]", chapter());
        assertEquals(0, unsaid.of(Picture.Kind.LOOP).loopType);
    }

    @Test
    public void oneOfAKindTheFirstThatHolds() {
        final String other = PROCESS.replace("One pin, about eighteen operations", "Second").replace("…", "");
        final String bad = PROCESS.replace("One man draws out the wire", "One woman pulls the thread out long");
        final PictureSet set = PictureSet.parse("{\"pictures\":[" + bad + "," + other + "," + PROCESS + "]}", chapter());
        assertEquals(1, set.pictures.size());
        assertEquals("Second", set.pictures.get(0).title);
    }

    @Test
    public void listsAreCutToTheirLimits() throws Exception {
        final JSONArray steps = new JSONArray();
        for (int i = 0; i < 15; i++) {
            steps.put(new JSONObject().put("label", "Step " + (char) ('a' + i)));
        }
        final JSONArray columns = new JSONArray();
        final JSONArray cells = new JSONArray();
        for (int i = 0; i < 8; i++) {
            columns.put("Col " + (char) ('a' + i));
            cells.put("cell");
        }
        final JSONArray rows = new JSONArray();
        for (int i = 0; i < 10; i++) {
            rows.put(new JSONObject().put("label", "Row " + (char) ('a' + i)).put("cells", cells));
        }
        final JSONArray nodes = new JSONArray();
        for (int i = 0; i < 9; i++) {
            nodes.put("Node " + (char) ('a' + i));
        }
        final JSONArray why = new JSONArray();
        for (int i = 0; i < 5; i++) {
            why.put(new JSONObject().put("title", "T").put("line", "L"));
        }
        final JSONArray pictures = new JSONArray()
                .put(new JSONObject().put("kind", "process").put("quote", WIRE).put("steps", steps).put("why", why))
                .put(new JSONObject().put("kind", "table").put("quote", CORN).put("columns", columns).put("rows", rows))
                .put(new JSONObject().put("kind", "loop").put("quote", MARKET).put("nodes", nodes));
        final PictureSet set = PictureSet.parse(new JSONObject().put("pictures", pictures).toString(), chapter());
        assertEquals(Picture.MAX_STEPS, set.of(Picture.Kind.PROCESS).steps.size());
        assertEquals(Picture.MAX_CARDS, set.of(Picture.Kind.PROCESS).cards.size());
        assertEquals(Picture.MAX_COLUMNS, set.of(Picture.Kind.TABLE).columns.size());
        assertEquals(Picture.MAX_ROWS, set.of(Picture.Kind.TABLE).rows.size());
        assertEquals(Picture.MAX_COLUMNS, set.of(Picture.Kind.TABLE).rows.get(0).cells.size());
        assertEquals(Picture.MAX_NODES, set.of(Picture.Kind.LOOP).nodes.size());
        assertFalse(set.of(Picture.Kind.LOOP).hasHolds());
    }

    @Test
    public void aPictureThatLacksWhatItsKindNeedsIsLeftOut() {
        final PictureSource text = chapter();
        // one step, one column, one node, an unknown kind, not an object
        final PictureSet set = PictureSet.parse("{\"pictures\":["
                + "{\"kind\":\"process\",\"quote\":\"" + WIRE + "\",\"steps\":[\"Only\"]},"
                + "{\"kind\":\"table\",\"quote\":\"" + CORN + "\",\"columns\":[\"One\"],\"rows\":[{\"label\":\"a\"},{\"label\":\"b\"}]},"
                + "{\"kind\":\"loop\",\"quote\":\"" + MARKET + "\",\"nodes\":[\"Alone\"]},"
                + "{\"kind\":\"pie chart\",\"quote\":\"" + MARKET + "\"}, 7]}", text);
        assertNotNull(set);
        assertTrue(set.isEmpty());
        assertEquals(4, set.leftOut);
    }

    @Test
    public void aColumnOrRowWithAnUnknownNumberGoes() {
        final String table = "{\"kind\":\"table\",\"quote\":\"" + CORN + "\",\"columns\":[\"Poland\",\"France\",\"In 1900\"],"
                + "\"rows\":[{\"label\":\"Corn\",\"cells\":[\"a\",\"b\",\"c\"],\"best\":[2]},"
                + "{\"label\":\"Silks\",\"cells\":[\"d\",\"e\",\"f\"]},{\"label\":\"After 1850\",\"cells\":[\"g\",\"h\",\"i\"]}]}";
        final PictureSet set = PictureSet.parse("[" + table + "]", chapter());
        final Picture t = set.of(Picture.Kind.TABLE);
        assertEquals(Arrays.asList("Poland", "France"), t.columns);
        assertEquals(2, t.rows.size());
        assertEquals(Arrays.asList("a", "b"), t.rows.get(0).cells);
        assertEquals(Arrays.asList(false, false), t.rows.get(0).best);
        assertEquals(2, set.numbersOut);
        // a loop does not lose a link: it goes whole
        final PictureSet loop = PictureSet.parse("[" + LOOP.replace("A bigger market", "A market of 5000") + "]", chapter());
        assertTrue(loop.isEmpty());
        assertEquals(1, loop.leftOut);
    }

    @Test
    public void nothingReadableIsNullAndNothingDrawnIsEmpty() {
        final PictureSource text = chapter();
        assertNull(PictureSet.parse(null, text));
        assertNull(PictureSet.parse("", text));
        assertNull(PictureSet.parse("I cannot draw this chapter.", text));
        assertNull(PictureSet.parse("{\"pictures\":[{\"kind\":\"process\",\"quote\":\"cut off", text));
        final PictureSet empty = PictureSet.parse("{\"pictures\": []}", text);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.leftOut);
    }

    @Test
    public void survivesTheStore() throws Exception {
        final PictureSet set = PictureSet.parse(ANSWER, chapter());
        set.title = "Of the Division of Labour";
        set.at = 42L;
        set.model = "m";
        final PictureSet back = PictureSet.fromJson(new JSONObject(set.toJson().toString()));
        assertNotNull(back);
        assertEquals("Of the Division of Labour", back.title);
        assertEquals(42L, back.at);
        assertEquals("m", back.model);
        assertEquals(3, back.pictures.size());
        assertEquals(4, back.numbersOut);
        final Picture process = back.of(Picture.Kind.PROCESS);
        assertEquals(4, process.page);
        assertEquals(6, process.steps.size());
        assertEquals("×2–3", process.steps.get(4).note);
        assertEquals(1, process.cards.size());
        assertEquals("Why it works: three causes", process.cardsTitle);
        final Picture table = back.of(Picture.Kind.TABLE);
        assertEquals(Arrays.asList("Poland", "France", "England"), table.columns);
        assertEquals(Arrays.asList(false, true, false), table.rows.get(1).best);
        assertEquals("", table.rows.get(1).cells.get(2));
        assertEquals("Rich countries pull ahead in manufactures.", table.point);
        final Picture loop = back.of(Picture.Kind.LOOP);
        assertEquals(1, loop.loopType);
        assertEquals(MARKET, loop.quote);
        assertEquals(2, loop.quantities.size());
        assertEquals(4, loop.quantities.get(0).number, 0);
        assertEquals("The same road, London to Edinburgh", loop.holdsBasis);
        assertNull(PictureSet.fromJson(null));
        assertEquals("picture:of the division of labour#0", PictureSet.key("of the division of labour#0"));
    }
}
