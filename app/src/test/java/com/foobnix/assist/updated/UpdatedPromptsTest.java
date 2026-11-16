package com.foobnix.assist.updated;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UpdatedPromptsTest {

    @Test
    public void systemCarriesTheHonestyRules() {
        final String s = UpdatedPrompts.system("", Collections.<String>emptyList());
        assertTrue(s.contains("only when you are sure the work exists"));
        assertTrue(s.contains("\"source\" is null"));
        assertTrue(s.contains("Never invent"));
        assertTrue(s.contains("\"debated\""));
        assertTrue(s.contains("\"speculative\""));
        assertTrue(s.contains("choose the weaker one"));
        assertTrue(s.contains("at most " + UpdatedChapter.MAX_NOTES + " notes"));
        assertTrue(s.contains("the language the text is written in"));
        // no lens: nothing about the reader's terms
        assertFalse(s.contains("\"terms\""));
        for (UpdatedKind k : UpdatedKind.values()) {
            assertTrue(k.code, s.contains("- " + k.code + ":"));
        }
    }

    @Test
    public void lensAndLanguageGoIntoTheSystemPrompt() {
        final String s = UpdatedPrompts.system("Vietnamese", Arrays.asList("machine learning", "nursing"));
        assertTrue(s.contains("The reader works in: machine learning; nursing."));
        assertTrue(s.contains("\"terms\""));
        assertTrue(s.contains("never force it"));
        assertTrue(s.contains("in Vietnamese"));
    }

    @Test
    public void userNamesTheBookAsFarAsItIsKnown() {
        final String all = UpdatedPrompts.user("The Wealth of Nations", "Adam Smith", 2007, "Of the Division of Labour", "TEXT");
        assertTrue(all.startsWith("Book: The Wealth of Nations\nAuthor: Adam Smith\n"));
        assertTrue(all.contains("may be this edition's"));
        assertTrue(all.contains("2007"));
        assertTrue(all.contains("Chapter: Of the Division of Labour\n"));
        assertTrue(all.endsWith("\n\nTEXT"));
        final String bare = UpdatedPrompts.user("Book", null, 0, "", "TEXT");
        assertEquals("Book: Book\n\nTEXT", bare);
    }

    @Test
    public void kindsAndRatingsAreReadTolerantly() {
        assertEquals(UpdatedKind.SOURCE, UpdatedKind.of(" Source check "));
        assertEquals(UpdatedKind.SOURCE, UpdatedKind.of("source_check"));
        assertEquals(UpdatedKind.MISSED, UpdatedKind.of("Missed cause"));
        assertEquals(UpdatedKind.DOUBTFUL, UpdatedKind.of("doubtful story"));
        assertEquals(UpdatedKind.NEWER, UpdatedKind.of("NEWER VIEW"));
        assertNull(UpdatedKind.of("praise"));
        assertNull(UpdatedKind.of(null));

        assertEquals(UpdatedSettled.ESTABLISHED, UpdatedSettled.of("Well established"));
        assertEquals(UpdatedSettled.ESTABLISHED, UpdatedSettled.of("well-documented"));
        assertEquals(UpdatedSettled.DEBATED, UpdatedSettled.of("debated"));
        assertEquals(UpdatedSettled.DEBATED, UpdatedSettled.of("still disputed"));
        assertEquals(UpdatedSettled.SPECULATIVE, UpdatedSettled.of("speculative"));
        // never rounded up
        assertEquals(UpdatedSettled.SPECULATIVE, UpdatedSettled.of("not well established"));
        assertEquals(UpdatedSettled.UNKNOWN, UpdatedSettled.of("high"));
        assertEquals(UpdatedSettled.UNKNOWN, UpdatedSettled.of(null));
        assertEquals(3, UpdatedSettled.ESTABLISHED.bars);
        assertEquals(0, UpdatedSettled.UNKNOWN.bars);

        assertEquals(UpdatedObjection.Strength.MODERATE, UpdatedObjection.Strength.of("Moderate"));
        assertEquals(UpdatedObjection.Strength.WEAK, UpdatedObjection.Strength.of("weak"));
        assertEquals(UpdatedObjection.Strength.UNKNOWN, UpdatedObjection.Strength.of(null));
    }

    @Test
    public void lensFieldsComeFromTheProfileItem() throws Exception {
        assertTrue(UpdatedLens.fields(null).isEmpty());
        assertTrue(UpdatedLens.fields(new JSONObject()).isEmpty());
        assertTrue(UpdatedLens.fields(new JSONObject().put("fields", "ML")).isEmpty());
        final JSONObject profile = new JSONObject().put("fields",
                new JSONArray().put(" machine  learning ").put("").put(7).put("nursing").put("nursing"));
        assertEquals(Arrays.asList("machine learning", "nursing"), UpdatedLens.fields(profile));
        final JSONArray many = new JSONArray();
        for (int i = 0; i < 9; i++) {
            many.put("field " + i);
        }
        assertEquals(UpdatedLens.MAX_FIELDS, UpdatedLens.fields(new JSONObject().put("fields", many)).size());
    }

    @Test
    public void smallTextRules() {
        assertEquals("", UpdatedText.given(" N/A "));
        assertEquals("", UpdatedText.given("null"));
        assertEquals("Capital", UpdatedText.given(" Capital "));
        assertEquals("1867", UpdatedText.year(1867));
        assertEquals("c. 1755", UpdatedText.year("c. 1755"));
        assertEquals("", UpdatedText.year("unknown"));
        assertEquals("", UpdatedText.year("recently"));
        assertEquals("", UpdatedText.year(null));
        assertEquals("The man whose whole life", UpdatedText.unquote("“The man whose whole life”"));
        assertEquals("Adam Smith", UpdatedText.labelAuthor(" Adam Smith "));
        assertEquals("", UpdatedText.labelAuthor("Smith, Adam"));
        assertEquals("", UpdatedText.labelAuthor("Daniel Kahneman and Amos Tversky"));
        assertEquals("", UpdatedText.labelAuthor(null));
        assertEquals("one two…", UpdatedText.cut("one two three four", 10));
    }
}
