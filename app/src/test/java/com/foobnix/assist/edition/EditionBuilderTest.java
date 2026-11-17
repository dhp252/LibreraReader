package com.foobnix.assist.edition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class EditionBuilderTest {

    private static final String BOOK = "wealth.epub";
    private static final String TODAY = "2026-11-17";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static JSONObject idea(int id, String text, String state, String quote) throws Exception {
        return new JSONObject().put("id", id).put("idea", text).put("state", state).put("quote", quote).put("page", 3);
    }

    private static JSONObject recall(String title, String words, boolean fromPassages, JSONObject... ideas) throws Exception {
        final JSONArray list = new JSONArray();
        for (JSONObject i : ideas) {
            list.put(i);
        }
        return new JSONObject().put("state", "done").put("title", title).put("date", 1L).put("words", words)
                .put("ideas", list).put("fromPassages", fromPassages).put("history", new JSONArray());
    }

    private static EditionOutline outline() {
        return new EditionOutline().add("book1", "Book I", true).add("ch1", "Of the Division of Labour", false)
                .add("ch2", "Of the Principle", false).add("ch3", "That the Division", false);
    }

    private static Map<String, JSONObject> sample() throws Exception {
        final Map<String, JSONObject> book = new HashMap<>();
        book.put("recall:ch1", recall("Of the Division of Labour",
                "Splitting a job into narrow steps multiplies output: ten pin-makers, about 48,000 pins a day.", true,
                idea(0, "Narrow steps make people faster", "remembered", ""),
                idea(1, "Switching between tasks wastes time", "missed", "saunters a little")));
        // a recall not finished: nothing of it
        book.put("recall:ch2", new JSONObject().put("state", "ideas").put("title", "Of the Principle").put("words", "x"));
        book.put("review:ch1:1", new JSONObject().put("idea", "Switching between tasks wastes time")
                .put("chapterTitle", "Of the Division of Labour").put("reason", "missed").put("strength", 3)
                .put("due", "2026-12-01").put("history", new JSONArray()
                        .put(new JSONObject().put("day", "2026-10-20").put("kind", "added").put("right", false))
                        .put(new JSONObject().put("day", "2026-10-21").put("kind", "recall").put("right", false))
                        .put(new JSONObject().put("day", "2026-10-27").put("kind", "review").put("right", true))
                        .put(new JSONObject().put("day", "2026-11-10").put("kind", "review").put("right", true))));
        book.put("use:x1", new JSONObject().put("id", "x1").put("try", "Week-long roles instead of daily rotation")
                .put("chapter", "ch1").put("chapterTitle", "Of the Division of Labour").put("state", "done")
                .put("start", "2026-10-26").put("checkin", "2026-11-09").put("doneDay", "2026-11-09")
                .put("verdict", "held").put("surprise", "Boredom went up too")
                .put("measures", new JSONArray().put(new JSONObject().put("name", "Task switches a day")
                        .put("before", 9).put("during", 4).put("higherBetter", false))));
        book.put("debate:ch3:2", new JSONObject().put("claim", "Specialisation dulls the mind")
                .put("crux", "Is one school enough to make up for a lifetime of one task?"));
        book.put("skeleton:book", new JSONObject().put("line", "Wealth is what labour makes, and markets organise it.")
                .put("parts", new JSONObject().put("book1", "How labour is divided"))
                .put("chapters", new JSONObject().put("ch1", "Splitting work multiplies output")));
        return book;
    }

    private static Map<String, JSONObject> library() throws Exception {
        final Map<String, JSONObject> lib = new HashMap<>();
        lib.put("link:a", new JSONObject().put("state", "added").put("relation", "disagrees")
                .put("sentence", "Marx reads the same workshop as alienation.")
                .put("a", new JSONObject().put("book", BOOK).put("chapterKey", "ch1").put("idea", "Division of labour")
                        .put("chapterTitle", "Of the Division of Labour"))
                .put("b", new JSONObject().put("book", "capital.epub").put("bookTitle", "Capital").put("idea", "Alienation")));
        // not added, or of other books: never
        lib.put("link:b", new JSONObject().put("state", "new").put("relation", "same")
                .put("a", new JSONObject().put("book", BOOK).put("chapterKey", "ch1").put("idea", "x"))
                .put("b", new JSONObject().put("book", "other.epub").put("idea", "y")));
        lib.put("link:c", new JSONObject().put("state", "added").put("relation", "same")
                .put("a", new JSONObject().put("book", "one.epub").put("idea", "x"))
                .put("b", new JSONObject().put("book", "two.epub").put("idea", "y")));
        return lib;
    }

    private static EditionModel build(Map<String, JSONObject> book, Map<String, JSONObject> lib) {
        return EditionBuilder.build(BOOK, "The Wealth of Nations", book, lib, outline(), new EditionWords(), TODAY, UTC);
    }

    private static String all(EditionModel m, EditionModel.Part part) {
        final EditionModel.Section s = m.section(part);
        if (s == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (EditionModel.Group g : s.groups) {
            out.append('[').append(g.title).append(']');
            for (EditionModel.Block b : g.blocks) {
                out.append(b.label).append('|').append(b.title).append('|').append(b.text).append('|').append(b.caption).append('\n');
            }
        }
        return out.toString();
    }

    @Test
    public void nothingOfTheReaderGivesAnEmptyEdition() throws Exception {
        final Map<String, JSONObject> book = new HashMap<>();
        // the skeleton alone is the AI's, not the reader's
        book.put("skeleton:book", new JSONObject().put("line", "One line"));
        final EditionModel m = build(book, null);
        assertTrue(m.isEmpty());
        assertTrue(m.sections.isEmpty());
        assertTrue(EditionBuilder.build(BOOK, "", null, null, null, new EditionWords(), TODAY, UTC).isEmpty());
    }

    @Test
    public void sectionsInContentsOrderAndOnlyChaptersWithMaterial() throws Exception {
        final EditionModel m = build(sample(), library());
        assertEquals(2, m.chapters);
        assertEquals(5, m.sections.size());
        assertEquals(EditionModel.Part.SKELETON, m.sections.get(0).part);
        assertEquals(EditionModel.Part.WORDS, m.sections.get(1).part);
        assertEquals(EditionModel.Part.WRONG, m.sections.get(2).part);
        assertEquals(EditionModel.Part.CONNECTIONS, m.sections.get(3).part);
        assertEquals(EditionModel.Part.OPEN, m.sections.get(4).part);
        // ch2 has only an unfinished recall: not listed anywhere
        for (EditionModel.Section s : m.sections) {
            for (EditionModel.Group g : s.groups) {
                assertFalse(g.key.equals("ch2"));
            }
        }
        assertEquals("[recalls, misses, experiments, links, questions]", m.kinds.toString());
    }

    @Test
    public void wordsAndTriedIt() throws Exception {
        final String words = all(build(sample(), library()), EditionModel.Part.WORDS);
        assertTrue(words, words.startsWith("[Of the Division of Labour]In your words||Splitting a job"));
        assertTrue(words, words.contains("Tried it · Oct 26 – Nov 9||Week-long roles instead of daily rotation. "
                + "Task switches a day: 9 → 4. It held. Boredom went up too."));
    }

    @Test
    public void theFixWithTheBooksWordsAndTheReviews() throws Exception {
        final EditionModel m = build(sample(), library());
        final String wrong = all(m, EditionModel.Part.WRONG);
        assertTrue(wrong, wrong.contains("You missed: Switching between tasks wastes time. The book: “saunters a little” "
                + "Right on every review since Oct 27."));
        assertEquals("Of the Division of Labour", m.sampleChapter);
        assertTrue(m.sampleNote, m.sampleNote.startsWith("Missed “Switching between tasks wastes time” at first"));
        assertTrue(m.sampleNote.endsWith("right on every review since"));
    }

    @Test
    public void noQuoteWithoutKeyPassages() throws Exception {
        final Map<String, JSONObject> book = sample();
        book.put("recall:ch1", recall("Of the Division of Labour", "words", false,
                idea(1, "Switching wastes time", "missed", "the check's own words")));
        final String wrong = all(build(book, null), EditionModel.Part.WRONG);
        assertFalse(wrong, wrong.contains("the check's own words"));
        assertTrue(wrong, wrong.contains("You missed: Switching wastes time."));
    }

    @Test
    public void aWrongReviewEndsTheRun() throws Exception {
        final Map<String, JSONObject> book = sample();
        book.get("review:ch1:1").put("history", new JSONArray()
                .put(new JSONObject().put("day", "2026-10-27").put("kind", "review").put("right", true))
                .put(new JSONObject().put("day", "2026-11-03").put("kind", "review").put("right", false))
                .put(new JSONObject().put("day", "2026-11-12").put("kind", "review").put("right", true)));
        assertTrue(all(build(book, null), EditionModel.Part.WRONG).contains("Right on every review since Nov 12."));
        book.get("review:ch1:1").put("history", new JSONArray()
                .put(new JSONObject().put("day", "2026-11-12").put("kind", "review").put("right", false)));
        final String wrong = all(build(book, null), EditionModel.Part.WRONG);
        assertTrue(wrong, wrong.contains("Not right yet at the last review, Nov 12."));
    }

    @Test
    public void connectionsOnlyAddedAndOfThisBook() throws Exception {
        final String c = all(build(sample(), library()), EditionModel.Part.CONNECTIONS);
        assertTrue(c, c.contains("Connections|Disagrees · Alienation · Capital|Marx reads the same workshop as alienation.|"));
        assertFalse(c, c.contains("Same idea"));
    }

    @Test
    public void questionsStillOpen() throws Exception {
        final Map<String, JSONObject> book = sample();
        book.put("preq:ch3", new JSONObject().put("title", "That the Division").put("questions", new JSONArray()
                .put(new JSONObject().put("q", "How many pins a day?").put("options", new JSONArray().put("200").put("48,000"))
                        .put("answer", 1).put("guess", 0))
                .put(new JSONObject().put("q", "Right one").put("options", new JSONArray().put("a").put("b"))
                        .put("answer", 1).put("guess", 1))));
        final String open = all(build(book, null), EditionModel.Part.OPEN);
        assertTrue(open, open.contains("Still open||Is one school enough to make up for a lifetime of one task?|"
                + "From your debate on: Specialisation dulls the mind"));
        assertTrue(open, open.contains("You guessed wrong||How many pins a day?|You guessed 200. The book: 48,000."));
        assertFalse(open, open.contains("Right one"));
        assertTrue(open, open.startsWith("[That the Division]"));
    }

    @Test
    public void teachBackExplainedAndGaps() throws Exception {
        final Map<String, JSONObject> book = new HashMap<>();
        book.put("teach:ch2", new JSONObject().put("title", "Of the Principle").put("finished", true)
                .put("explained", new JSONArray().put("Exchange comes first"))
                .put("missing", new JSONArray().put("Self-love, not benevolence"))
                .put("gaps", new JSONArray()));
        final EditionModel m = build(book, null);
        assertEquals(1, m.chapters);
        assertTrue(all(m, EditionModel.Part.WORDS).contains("• Exchange comes first"));
        assertTrue(all(m, EditionModel.Part.WRONG).contains("You did not get to explain: Self-love, not benevolence."));
        assertNull(m.section(EditionModel.Part.CONNECTIONS));
        // the skeleton comes along once the reader has material
        assertNull(m.section(EditionModel.Part.SKELETON));
    }

    @Test
    public void notesTheReaderChose() throws Exception {
        final Map<String, JSONObject> book = new HashMap<>();
        book.put(EditionNote.key("ch1", 0), new EditionNote("Attention residue", "Switching leaves part of the mind behind.",
                "well established", "", "Of the Division of Labour", 5L).toJson());
        final EditionModel m = build(book, null);
        final String c = all(m, EditionModel.Part.CONNECTIONS);
        assertTrue(c, c.contains("The book, updated|Attention residue|Switching leaves part of the mind behind.|"
                + "How settled: well established"));
    }

    @Test
    public void tidiedWordsShowUnlessTheReaderWentBack() throws Exception {
        final Map<String, JSONObject> book = sample();
        final String raw = book.get("recall:ch1").getString("words");
        book.put(EditionPrompts.key("ch1"), EditionPrompts.item("Tidied passage.", raw, "m", 1L));
        EditionModel m = build(book, null);
        assertEquals("[Of the Division of Labour]", m.tidied.toString());
        assertTrue(all(m, EditionModel.Part.WORDS).contains("In your words · tidied||Tidied passage."));
        book.put(EditionState.KEY, new JSONObject().put("raw", true));
        m = build(book, null);
        assertTrue(m.showsRaw);
        assertTrue(all(m, EditionModel.Part.WORDS).contains("In your words||Splitting"));
        // a new recall of the chapter: the old tidied passage no longer counts
        book.remove(EditionState.KEY);
        book.put("recall:ch1", recall("Of the Division of Labour", "Other words now", true,
                idea(0, "a", "remembered", "")));
        m = build(book, null);
        assertTrue(m.tidied.isEmpty());
        assertEquals(1, m.raws.size());
        assertEquals("Of the Division of Labour", m.raws.get(0).title);
    }

    @Test
    public void removedItemsAndBrokenOnesAreLeftOut() throws Exception {
        final Map<String, JSONObject> book = new HashMap<>();
        book.put("recall:ch1", new JSONObject().put("del", true).put("state", "done"));
        book.put("use:y", new JSONObject().put("id", "y"));
        book.put("debate:nocolon", new JSONObject().put("crux", "x"));
        book.put("review:ch1:1", new JSONObject().put("history", "not a list"));
        assertTrue(build(book, null).isEmpty());
    }

    @Test
    public void chaptersWithoutAnOutlineAreNamedByTheirItems() throws Exception {
        final EditionModel m = EditionBuilder.build(BOOK, "T", sample(), null, null, new EditionWords(), TODAY, UTC);
        assertNotNull(m.section(EditionModel.Part.SKELETON));
        assertEquals("Of the Division of Labour", m.section(EditionModel.Part.WORDS).groups.get(0).title);
        assertEquals("From page 5", EditionBuilder.titleOf("@pages:4", new EditionWords()));
    }

    @Test
    public void cutAndSentence() {
        assertEquals("one two…", EditionBuilder.cut("one two three four", 10));
        assertEquals("Done.", EditionBuilder.sentence("Done"));
        assertEquals("Done?", EditionBuilder.sentence("Done?"));
    }
}
