package com.foobnix.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.foobnix.assist.prequestions.PreQ;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.updated.UpdatedChapter;
import com.foobnix.assist.updated.UpdatedKind;
import com.foobnix.assist.xray.XrayRatings;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The skill on the server (F-016, assets/ai-skill) tells an outside AI agent how to write the
 * app's AI data. Items written as the reference files say are read back by the app's own readers
 * here, and every field these samples use is named in the reference file: a format change that
 * breaks this test needs the skill updated and its version (skill.json) raised.
 */
public class AiSkillFormatTest {

    @Test
    public void aiFile() throws Exception {
        final String json = "{\"version\":2,\"updated\":5,\"summaries\":[{\"key\":\"@book\",\"title\":\"T\","
                + "\"text\":\"Sum.\",\"model\":\"M\",\"time\":5},{\"key\":\"one#0\",\"title\":\"One\",\"text\":\"C.\","
                + "\"model\":\"M\",\"time\":5}],\"highlights\":[{\"key\":\"one#0\",\"title\":\"One\",\"model\":\"M\","
                + "\"time\":7,\"ideas\":[{\"id\":1,\"text\":\"Idea\",\"category\":\"main\",\"parent\":-1,\"marked\":true,"
                + "\"page\":-1},{\"id\":2,\"text\":\"Proof\",\"category\":\"evidence\",\"parent\":1,\"marked\":true,"
                + "\"page\":-1}],\"quotes\":[{\"text\":\"A sentence.\",\"page\":-1,\"category\":\"main\",\"idea\":1}],"
                + "\"paragraphs\":[{\"text\":\"A paragraph.\",\"page\":-1,\"category\":\"evidence\",\"idea\":2}]}],"
                + "\"emptyKeys\":[\"two#0\"]}";
        final AiStore.Book b = AiStore.Book.fromJson(json);
        assertEquals("Sum.", b.summary(AiStore.BOOK).text);
        assertEquals("C.", b.summary("one#0").text);
        final AiStore.Chapter c = b.chapter("one#0");
        assertEquals(2, c.ideas.size());
        assertEquals(AiCategory.EVIDENCE, c.idea(2).category);
        assertEquals(1, c.idea(2).parent);
        assertEquals(1, c.quotes.get(0).idea);
        assertEquals(-1, c.quotes.get(0).page);
        assertEquals(1, c.paragraphs.size());
        assertTrue(b.isDone("one#0"));
        assertTrue(b.isDone("two#0"));
        named("ai-file.md", "version", "updated", "summaries", "highlights", "key", "title", "text", "model", "time",
                "ideas", "id", "category", "parent", "marked", "page", "quotes", "idea", "paragraphs", "emptyKeys");
        for (AiCategory category : AiCategory.values()) {
            named("ai-file.md", category.code);
        }
    }

    @Test
    public void skeleton() throws Exception {
        final SkeletonData d = SkeletonData.fromJson(new JSONObject("{\"line\":\"L\",\"paragraph\":\"P\",\"note\":\"N\","
                + "\"conclusion\":\"book iv#0\",\"parts\":{\"book iv#0\":\"Ends\"},\"optional\":[\"book ii#0\"],"
                + "\"chapters\":{\"book iv#0/chapter 1#0\":\"C\"},\"chars\":{\"book iv#0/chapter 1#0\":100},"
                + "\"model\":\"M\",\"made\":5,\"t\":5}"));
        assertEquals("L", d.line);
        assertEquals("book iv#0", d.conclusion);
        assertEquals("Ends", d.partLines.get("book iv#0"));
        assertTrue(d.optional.contains("book ii#0"));
        assertEquals("C", d.chapterLines.get("book iv#0/chapter 1#0"));
        assertEquals(Long.valueOf(100), d.chars.get("book iv#0/chapter 1#0"));
        assertEquals("skeleton:book", SkeletonData.KEY);
        named("assist-file.md", SkeletonData.KEY, "line", "paragraph", "note", "conclusion", "parts", "optional",
                "chapters", "chars", "model", "made", "t");
    }

    @Test
    public void preQuestions() throws Exception {
        final PreQ.Chapter c = PreQ.Chapter.fromJson(new JSONObject("{\"title\":\"One\",\"state\":\"ready\",\"layout\":\"\","
                + "\"locked\":0,\"questions\":[{\"q\":\"How many?\",\"short\":\"Pins\",\"options\":[\"a\",\"b\",\"c\",\"d\"],"
                + "\"answer\":2,\"number\":48000,\"label\":\"pins\",\"why\":\"W\",\"quote\":\"Q.\",\"page\":-1,"
                + "\"lastPage\":-1,\"guess\":-1,\"own\":\"\",\"confidence\":-1,\"skipped\":false,\"found\":0}],\"t\":5}"));
        assertEquals(PreQ.READY, c.state);
        assertEquals(0, c.nextOpen());
        final PreQ.Question q = c.questions.get(0);
        assertEquals("c", q.answerText());
        assertEquals(48000, q.number, 0.1);
        assertEquals("Q.", q.quote);
        assertFalse(q.answered());
        assertTrue(PreQ.offerable("one#0", "One", false, c));
        assertEquals("preq:", PreQ.PREFIX);
        named("assist-file.md", "preq:<chapter key>", "title", "state", "layout", "locked", "questions", "q", "short",
                "options", "answer", "number", "label", "why", "quote", "page", "lastPage", "guess", "own", "confidence",
                "skipped", "found", PreQ.READY, PreQ.LOCKED, PreQ.DECLINED);
    }

    @Test
    public void xray() throws Exception {
        final XrayRatings r = XrayRatings.fromJson(new JSONObject("{\"ideas\":7,\"model\":\"M\",\"ratings\":[{\"id\":2,"
                + "\"rating\":\"weak\",\"label\":\"Proof\",\"why\":\"1 story\"}],\"weakest\":{\"id\":2,\"title\":\"T\","
                + "\"explanation\":\"E. F.\"},\"t\":5}"));
        assertNotNull(r);
        assertEquals(7, r.ideasTime);
        assertEquals(XrayRatings.Rating.WEAK, r.entries.get(2).rating);
        assertEquals(2, r.weakest);
        assertEquals("T", r.weakTitle);
        for (XrayRatings.Rating rating : XrayRatings.Rating.values()) {
            named("assist-file.md", rating.code);
        }
        named("assist-file.md", "xray:<chapter key>", "ideas", "ratings", "rating", "weakest", "explanation");
    }

    @Test
    public void updated() throws Exception {
        final UpdatedChapter c = UpdatedChapter.fromJson(new JSONObject("{\"title\":\"One\",\"notes\":[{\"kind\":\"newer\","
                + "\"quote\":\"Q.\",\"title\":\"T\",\"body\":\"B.\",\"settled\":\"debated\",\"settled_note\":\"\","
                + "\"source\":{\"work\":\"W\",\"author\":\"A\",\"year\":\"1832\"},\"page\":-1,\"last\":-1}],"
                + "\"objection\":{\"claim\":\"C\",\"objection\":\"O\",\"strength\":\"moderate\"},\"layout\":\"\",\"at\":5,"
                + "\"model\":\"M\",\"first_published\":1776,\"author\":\"Smith\",\"t\":5}"));
        assertNotNull(c);
        assertEquals(1, c.notes.size());
        assertEquals(UpdatedKind.NEWER, c.notes.get(0).kind);
        assertTrue(c.notes.get(0).hasSource());
        assertNotNull(c.objection);
        assertEquals(1776, c.firstPublished);
        assertTrue(c.needsPages("1080x1920@20"));
        for (UpdatedKind kind : UpdatedKind.values()) {
            named("assist-file.md", kind.code);
        }
        named("assist-file.md", "updated:<chapter key>", "notes", "kind", "body", "settled", "settled_note", "source",
                "work", "author", "year", "last", "objection", "claim", "strength", "twist", "pushed", "disagree",
                "at", "first_published", "well established", "debated", "speculative");
    }

    @Test
    public void appOnlyItemsAreNotDescribed() throws Exception {
        final String assist = doc("assist-file.md");
        assertNull(find(assist, "\"zoom:"));
        assertNull(find(assist, "\"lens:bridge:"));
        assertNull(find(assist, "\"picture:"));
    }

    private static String find(String text, String what) {
        return text.contains(what) ? what : null;
    }

    /** Each name appears in the reference file (as a JSON field, a key or a value). */
    private static void named(String file, String... names) throws Exception {
        final String doc = doc(file);
        for (String n : names) {
            assertTrue(file + " does not name " + n, doc.contains("\"" + n + "\"") || doc.contains("`" + n + "`")
                    || doc.contains("\"" + n + ":"));
        }
    }

    private static String doc(String file) throws Exception {
        for (String p : new String[]{"src/main/assets/ai-skill/reference/", "app/src/main/assets/ai-skill/reference/"}) {
            final File f = new File(p + file);
            if (f.isFile()) {
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError(file + " not found");
    }
}
