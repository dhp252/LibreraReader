package com.foobnix.bookmap;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapPromptsTest {

    @Test
    public void chapterPromptAsksForEveryGenreAndExactQuotes() {
        final String s = MapPrompts.chapterSystem("");
        assertTrue(s.contains("do not force a genre"));
        assertTrue(s.contains("introduce") && s.contains("develop") && s.contains("turn") && s.contains("resolve"));
        assertTrue(s.contains("1 to 4 threads"));
        assertTrue(s.contains("one whole sentence copied from the text"));
        assertTrue(s.contains("character for character"));
        assertTrue(s.contains("the language the text is written in"));
        assertTrue(MapPrompts.chapterSystem("German").contains("in German"));
        final String u = MapPrompts.chapter("Gatsby", "Chapter I", 2, 3, "In my younger years.");
        assertTrue(u.contains("Book: Gatsby"));
        assertTrue(u.contains("Chapter I (part 2 of 3)"));
        assertTrue(u.contains("<text>\nIn my younger years.\n</text>"));
        assertFalse(MapPrompts.chapter("G", "One", 1, 1, "x").contains("part 1"));
    }

    @Test
    public void ideasPromptSendsTheIdeasNotTheText() {
        assertTrue(MapPrompts.ideasSystem("").contains("not the chapter's text"));
        final List<AiStore.Idea> ideas = Arrays.asList(
                new AiStore.Idea(1, "Fortune rules half our actions", AiCategory.MAIN, -1, true, 3),
                new AiStore.Idea(2, "Rivers in flood", AiCategory.EXAMPLE, 1, true, 4));
        final String u = MapPrompts.ideas("The Prince", "XXV", ideas);
        assertTrue(u.contains("1. [" + AiCategory.MAIN.code + "] Fortune rules half our actions\n"));
        assertTrue(u.contains("2. [" + AiCategory.EXAMPLE.code + "] Rivers in flood (supports 1)\n"));
    }

    @Test
    public void bookPromptAsksForSevenLanesWithTheNamesTheyMerge() {
        final String s = MapPrompts.bookSystem("");
        assertTrue(s.contains("keep the 7 threads"));
        assertTrue(s.contains("\"merged\""));
        assertTrue(s.contains("at most 1 per 3 moments"));
        assertTrue(s.contains("pays_off") && s.contains("contradicts") && s.contains("needs"));
        assertTrue(s.contains("No text of the book"));
    }

    private static MapBook.Chapter chapter(int n, String... kinds) {
        final MapBook.Chapter c = new MapBook.Chapter("c" + n);
        c.n = n;
        c.title = "Chapter \"" + n + "\"";
        c.threads.add(new MapBook.Thread("Gatsby", Collections.singletonList("Jay"), "the host nobody knows"));
        for (int i = 0; i < kinds.length; i++) {
            c.moments.add(new MapBook.Moment(i % 2 == 0 ? Arrays.asList("Gatsby") : Arrays.asList("Gatsby", "Daisy"),
                    kinds[i], "Label " + n + "." + (i + 1), "q", 0, 0));
        }
        return c;
    }

    @Test
    public void bookTextListsMomentsByIdAndTrimsDevelopFirst() {
        final List<MapBook.Chapter> chapters = Arrays.asList(chapter(1, "introduce", "develop", "develop"),
                chapter(2), chapter(3, "turn", "develop", "resolve", "develop"));
        final String all = MapPrompts.book("Gatsby", chapters, 100000);
        assertTrue(all.contains("<chapter n=\"1\" title=\"Chapter '1'\">"));
        assertTrue(all.contains("Threads: Gatsby (Jay): the host nobody knows"));
        assertTrue(all.contains("1.1 introduce [Gatsby] Label 1.1"));
        assertTrue(all.contains("1.2 develop [Gatsby, Daisy] Label 1.2"));
        assertFalse("a chapter without moments is left out", all.contains("n=\"2\""));
        assertTrue(all.contains("3.4 develop"));

        // a little less room: develop moments of a single thread go first, the ids stay
        final String trimmed = MapPrompts.book("Gatsby", chapters, all.length() - 1);
        assertFalse(trimmed.contains("1.3 develop"));
        assertTrue(trimmed.contains("1.2 develop"));
        assertTrue(trimmed.contains("3.4 develop"));
        assertTrue(trimmed.contains("3.3 resolve"));
        assertTrue(trimmed.length() < all.length());

        final String tiny = MapPrompts.book("Gatsby", chapters, 40);
        assertEquals(40, tiny.length());
    }

    @Test
    public void readsAWholeChapterAnswerAlsoInAFence() {
        final String answer = "Here it is:\n```json\n{\"threads\":[{\"name\":\"Gatsby\",\"aliases\":[\"Jay\"],\"what\":\"host\"},"
                + "{\"name\":\"\"}],\"moments\":[{\"threads\":[\"Gatsby\",\"Daisy\"],\"kind\":\"Turn\",\"label\":\"They meet\","
                + "\"quote\":\"He smiled.\"},{\"threads\":[\"Gatsby\"],\"kind\":\"twist\",\"label\":\"x\",\"quote\":\"y\"},"
                + "{\"threads\":[],\"kind\":\"develop\",\"label\":\"no threads\",\"quote\":\"z\"},"
                + "{\"threads\":[\"Gatsby\"],\"kind\":\"develop\",\"label\":\"no quote\"},"
                + "{\"threads\":[\"a\",\"b\",\"c\",\"d\",\"e\"],\"kind\":\"resolve\",\"label\":\"many\",\"quote\":\"q\",\"extra\":1}]}\n```";
        final MapPrompts.Extract e = MapPrompts.parseChapter(answer);
        assertFalse(e.cutOff);
        assertEquals(1, e.threads.size());
        assertEquals(Collections.singletonList("Jay"), e.threads.get(0).aliases);
        assertEquals(2, e.moments.size());
        assertEquals(MapBook.TURN, e.moments.get(0).kind);
        assertEquals("He smiled.", e.moments.get(0).quote);
        assertEquals(4, e.moments.get(1).threads.size());
    }

    @Test
    public void keepsTheWholeMomentsOfACutOffAnswer() {
        final String answer = "{\"threads\":[{\"name\":\"Gatsby\",\"what\":\"host\"}],\"moments\":["
                + "{\"threads\":[\"Gatsby\"],\"kind\":\"introduce\",\"label\":\"Arrives\",\"quote\":\"He came {here}.\"},"
                + "{\"threads\":[\"Gatsby\"],\"kind\":\"develop\",\"label\":\"Par";
        final MapPrompts.Extract e = MapPrompts.parseChapter(answer);
        assertTrue(e.cutOff);
        assertEquals(1, e.threads.size());
        assertEquals(1, e.moments.size());
        assertEquals("He came {here}.", e.moments.get(0).quote);
        assertTrue(MapPrompts.parseChapter("I cannot help with that.").moments.isEmpty());
        assertTrue(MapPrompts.parseChapter(null).moments.isEmpty());
    }

    @Test
    public void readsIdeasAnswersByIdeaId() {
        final MapPrompts.Extract e = MapPrompts.parseIdeas("{\"threads\":[{\"name\":\"Fortune\"}],\"moments\":["
                + "{\"idea\":3,\"threads\":[\"Fortune\"],\"kind\":\"develop\",\"label\":\"Half our actions\"},"
                + "{\"threads\":[\"Fortune\"],\"kind\":\"develop\",\"label\":\"no idea\"}]}");
        assertEquals(1, e.moments.size());
        assertEquals(3, e.moments.get(0).idea);
        assertEquals("", e.moments.get(0).quote);
    }

    @Test
    public void partsOfALongChapterJoinTheirThreadsByName() {
        final MapPrompts.Extract a = MapPrompts.parseChapter("{\"threads\":[{\"name\":\"Gatsby\"}],\"moments\":["
                + "{\"threads\":[\"Gatsby\"],\"kind\":\"introduce\",\"label\":\"A\",\"quote\":\"a\"}]}");
        final MapPrompts.Extract b = MapPrompts.parseChapter("{\"threads\":[{\"name\":\"gatsby\"},{\"name\":\"Daisy\"}],"
                + "\"moments\":[{\"threads\":[\"Daisy\"],\"kind\":\"develop\",\"label\":\"B\",\"quote\":\"b\"}");
        a.add(b);
        assertEquals(2, a.threads.size());
        assertEquals("Daisy", a.threads.get(1).name);
        assertEquals(2, a.moments.size());
        assertTrue(a.cutOff);
    }

    @Test
    public void readsTheBookAnswerAndCapsItsLanes() {
        final StringBuilder lanes = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            lanes.append(i > 1 ? "," : "").append("{\"id\":\"").append(i == 2 ? "l1" : "l" + i).append("\",\"name\":\"Lane ")
                    .append(i).append("\",\"what\":\"w\",\"merged\":[\"T").append(i).append("\"]}");
        }
        final MapPrompts.Plan p = MapPrompts.parseBook("{\"premise\":\"A man.\",\"threads\":[" + lanes
                + ",{\"id\":\"x\",\"name\":\"\"}],\"arcs\":[{\"from\":\"1.1\",\"to\":\"4.2\",\"type\":\"pays_off\",\"why\":\"w\"},"
                + "{\"from\":\"1.1\",\"to\":\"1.1\",\"type\":\"pays_off\"},{\"from\":\"1.1\",\"to\":\"2.2\",\"type\":\"rhymes\"}],"
                + "\"key\":[\"1.1\",\"4.2\"]}");
        assertNotNull(p);
        assertEquals("A man.", p.premise);
        assertEquals(MapPrompts.LANES, p.lanes.size());
        assertEquals("l1", p.lanes.get(0).id);
        assertEquals("a repeated id gets its own", "t2", p.lanes.get(1).id);
        assertEquals(Collections.singletonList("T1"), p.lanes.get(0).merged);
        assertEquals(1, p.arcs.size());
        assertEquals(Arrays.asList("1.1", "4.2"), p.key);
        assertNull(MapPrompts.parseBook("{\"premise\":\"x\",\"threads\":[]}"));
        assertNull(MapPrompts.parseBook("sorry"));

        // cut off in the arcs: the lanes and whole arcs stay, no key
        final MapPrompts.Plan cut = MapPrompts.parseBook("{\"premise\":\"A.\",\"threads\":[{\"id\":\"a\",\"name\":\"A\"}],"
                + "\"arcs\":[{\"from\":\"1.1\",\"to\":\"2.1\",\"type\":\"leads_to\"},{\"from\":\"2.");
        assertNotNull(cut);
        assertEquals(Collections.singletonList("A"), cut.lanes.get(0).merged);
        assertEquals(1, cut.arcs.size());
        assertTrue(cut.key.isEmpty());
        assertEquals(new ArrayList<String>(), cut.key);
    }
}
