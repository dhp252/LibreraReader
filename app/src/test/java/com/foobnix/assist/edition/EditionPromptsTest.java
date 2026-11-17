package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EditionPromptsTest {

    private static final String RAW = "so um splitting the job into narrow steps makes more pins, ten pin makers made "
            + "like 48000 pins a day. people get faster at one step and stop loosing time sauntering between jobs";

    private static EditionModel.Raw raw(String key, String words, String... ideas) {
        return new EditionModel.Raw(key, "Chapter " + key, words, Arrays.asList(ideas));
    }

    @Test
    public void theRequestCarriesTheReadersWordsAndIdeasOnly() {
        final String user = EditionPrompts.user("The  Wealth\nof Nations", Arrays.asList(
                raw("a", RAW, "Narrow steps multiply output", "Switching wastes time"), raw("b", "second notes")));
        assertTrue(user, user.startsWith("Book: The Wealth of Nations\n"));
        assertTrue(user.contains("Chapter 1: Chapter a\nKey ideas (not the reader's words):\n- Narrow steps multiply output\n"
                + "- Switching wastes time\n<notes>\n" + RAW + "\n</notes>"));
        assertTrue(user.contains("Chapter 2: Chapter b\n<notes>\nsecond notes\n</notes>"));
        final String system = EditionPrompts.system("");
        assertTrue(system.contains("Add nothing"));
        assertTrue(system.contains("You do not get the book"));
        assertTrue(system.contains("the language the reader wrote in"));
        assertTrue(EditionPrompts.system("German").contains("Write it in German"));
    }

    @Test
    public void aLongRecallIsCappedAtAWord() {
        final StringBuilder words = new StringBuilder();
        while (words.length() < EditionPrompts.MAX_WORDS + 500) {
            words.append("word ");
        }
        final String user = EditionPrompts.user("B", Arrays.asList(raw("a", words.toString())));
        final String notes = user.substring(user.indexOf("<notes>\n") + 8, user.indexOf("\n</notes>"));
        assertTrue(notes.length() <= EditionPrompts.MAX_WORDS);
        assertTrue(notes.endsWith("word"));
    }

    @Test
    public void onlyWholeChaptersThatFitGo() {
        final List<EditionModel.Raw> raws = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            raws.add(raw("c" + i, RAW));
        }
        assertEquals(EditionPrompts.MAX_CHAPTERS, EditionPrompts.fit(raws, 1_000_000).size());
        assertEquals(2, EditionPrompts.fit(raws, 2 * (RAW.length() + 60)).size());
        // the first always goes
        assertEquals(1, EditionPrompts.fit(raws, 10).size());
        assertEquals(0, EditionPrompts.fit(new ArrayList<EditionModel.Raw>(), 10).size());
    }

    @Test
    public void theAnswerIsReadTolerantly() {
        Map<Integer, String> p = EditionPrompts.parse("```json\n{\"chapters\": [{\"n\": 2, \"text\": \" B. \"}, "
                + "{\"n\": \"1\", \"text\": \"A.\"}, {\"n\": 9, \"text\": \"none\"}, {\"n\": 1, \"text\": \"again\"}]}\n```", 2);
        assertEquals(2, p.size());
        assertEquals("A.", p.get(0));
        assertEquals("B.", p.get(1));
        // a bare list, numbered by place
        p = EditionPrompts.parse("Here: [{\"passage\": \"A.\"}, {\"text\": \"\"}, {\"text\": \"C.\"}]", 3);
        assertEquals("A.", p.get(0));
        assertNull(p.get(1));
        assertEquals("C.", p.get(2));
        assertTrue(EditionPrompts.parse("no json", 3).isEmpty());
        assertTrue(EditionPrompts.parse(null, 3).isEmpty());
        assertTrue(EditionPrompts.parse("{\"chapters\": 5}", 3).isEmpty());
    }

    @Test
    public void aTidiedPassageMustStayTheReadersWording() {
        final List<String> ideas = Arrays.asList("Switching between tasks wastes time");
        final String tidy = "Splitting the job into narrow steps makes more pins: ten pin-makers made about 48,000 pins "
                + "a day. People get faster at one step and stop losing time sauntering between jobs.";
        assertTrue(EditionPrompts.keepsWording(RAW, ideas, tidy));
        // a fixed recognition mistake may come from the ideas
        assertTrue(EditionPrompts.keepsWording("swishing between tasks wastes time", ideas,
                "Switching between tasks wastes time."));
        // new content is not the reader's
        assertFalse(EditionPrompts.keepsWording(RAW, ideas, "Adam Smith argues that specialisation, markets and "
                + "capital accumulation together explain national prosperity."));
        // nor a passage much longer than the notes
        assertFalse(EditionPrompts.keepsWording("pins", ideas, tidy));
        assertFalse(EditionPrompts.keepsWording(RAW, ideas, "  "));
        assertFalse(EditionPrompts.keepsWording(RAW, null, null));
    }

    @Test
    public void aTidiedPassageBelongsToTheWordsItWasMadeFrom() throws Exception {
        final JSONObject item = EditionPrompts.item("Tidy.", RAW, "model-x", 7L);
        assertEquals("Tidy.", EditionPrompts.tidied(item, RAW));
        assertEquals("Tidy.", EditionPrompts.tidied(item, "  " + RAW + "\n"));
        assertNull(EditionPrompts.tidied(item, RAW + " and more"));
        assertNull(EditionPrompts.tidied(null, RAW));
        assertNull(EditionPrompts.tidied(new JSONObject().put("from", EditionPrompts.mark(RAW)), RAW));
        assertEquals("edition:words:ch 1#0", EditionPrompts.key("ch 1#0"));
        assertEquals("model-x", item.getString("model"));
    }
}
