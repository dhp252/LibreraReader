package com.foobnix.assist.path;

import com.foobnix.assist.skeleton.SkeletonData;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopicTest {

    private static List<TopicData.Book> books() {
        return Arrays.asList(new TopicData.Book("wealth.epub", "The Wealth of Nations", "Adam Smith", 1776),
                new TopicData.Book("capital.epub", "Capital, Vol. I", "Marx, Karl", 1867),
                new TopicData.Book("taylor.pdf", "Scientific Management", "", 0));
    }

    private static List<List<PathChapters.Entry>> lists() {
        final List<PathChapters.Entry> marx = Arrays.asList(
                new PathChapters.Entry("ch. 14#0", "Ch. 14: Division of Labour and Manufacture", "", "Manufacture cripples the worker", 9000, 0, 9));
        final List<PathChapters.Entry> taylor = Arrays.asList(
                new PathChapters.Entry("k0", "Fundamentals", "", "Both sides can prosper", 3000, 0, 5),
                new PathChapters.Entry("k1", "Principles", "", "Timing every motion", 3000, 5, 9));
        final List<List<PathChapters.Entry>> out = new ArrayList<>();
        out.add(PathFixture.list());
        out.add(marx);
        out.add(taylor);
        return out;
    }

    private static final String ANSWER = "{\"path\": [{\"book\": 0, \"chapter\": 1, \"why\": \"Where the idea starts\"},"
            + " {\"book\": 1, \"chapter\": 0, \"why\": \"The cost to the worker\"}, {\"book\": 2, \"chapter\": 1, \"why\": \"The method\"},"
            + " {\"book\": 1, \"chapter\": 5, \"why\": \"no such chapter\"}, {\"book\": 7, \"chapter\": 0, \"why\": \"no such book\"},"
            + " {\"book\": 0, \"chapter\": 1, \"why\": \"twice\"}],"
            + " \"agree\": [{\"point\": \"Splitting work multiplies output.\", \"books\": [0, 1, 2, 9], \"note\": \"Pins, watches, pig iron.\"},"
            + " {\"point\": \"Only one book\", \"books\": [0]}],"
            + " \"disagree\": [{\"question\": \"Is it good for the worker?\", \"positions\": [{\"book\": 0, \"says\": \"It dulls him.\"},"
            + " {\"book\": 1, \"says\": \"It cripples him.\"}, {\"book\": 1, \"says\": \"again\"}, {\"book\": 2, \"says\": \"\"}]},"
            + " {\"question\": \"One voice\", \"positions\": [{\"book\": 0, \"says\": \"x\"}]}],"
            + " \"only\": [{\"book\": 2, \"point\": \"Timing every motion\"}, {\"book\": 5, \"point\": \"gone\"}]}";

    private static TopicData data() {
        return TopicData.fromAnswer(" Splitting work:  good for whom? ", TopicPrompts.parse(ANSWER), books(), lists(), "m", 9L);
    }

    @Test
    public void slugs() {
        assertEquals("splitting-work-good-for-whom", TopicData.slug("Splitting work: good for whom?"));
        assertEquals("topic:splitting-work-good-for-whom", TopicData.key(" Splitting  work: good for whom? "));
        assertEquals("phân-công-lao-động", TopicData.slug("Phân công lao động"));
        assertEquals("topic", TopicData.slug("?!"));
        assertEquals("topic", TopicData.slug(null));
        final StringBuilder longTopic = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longTopic.append("word ");
        }
        final String slug = TopicData.slug(longTopic.toString());
        assertTrue(slug.length() <= 60);
        assertFalse(slug.endsWith("-"));
    }

    @Test
    public void bookNamesForShort() {
        final List<TopicData.Book> b = books();
        assertEquals("Smith", b.get(0).shortName());
        assertEquals("S", b.get(0).letter());
        assertEquals("Smith · 1776", b.get(0).byline());
        assertEquals("Marx", b.get(1).shortName());
        assertEquals("M", b.get(1).letter());
        assertEquals("the title when the author is not known", "Scientific Management", b.get(2).shortName());
        assertEquals("S", b.get(2).letter());
        assertEquals("", b.get(2).byline());
    }

    @Test
    public void promptListsEveryBookAndFits() {
        final String u = TopicPrompts.user("Splitting work", books(), lists(), 600_000);
        assertTrue(u.startsWith("Topic: Splitting work\n\n<book n=\"0\" title=\"The Wealth of Nations\" author=\"Adam Smith\" year=\"1776\">"));
        assertTrue(u.contains("<chapter n=\"1\" part=\"Book I\" title=\"Chapter I: Of the Division of Labour\">Splitting work multiplies output</chapter>"));
        assertTrue(u.contains("<book n=\"2\" title=\"Scientific Management\">"));
        assertTrue(u.contains("<chapter n=\"1\" title=\"Principles\">Timing every motion</chapter>"));
        assertTrue(TopicPrompts.system("").contains("\"disagree\""));

        final List<PathChapters.Entry> many = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            many.add(new PathChapters.Entry("k" + i, "T" + i, "", "a long line of words that goes on and on and on and on and on and on", 1, 0, 1));
        }
        final List<List<PathChapters.Entry>> two = new ArrayList<>();
        two.add(many);
        two.add(many);
        final String cut = TopicPrompts.user("t", books().subList(0, 2), two, 30_000);
        assertTrue("fits: " + cut.length(), cut.length() <= 30_000);
        assertTrue(cut.contains("…</chapter>"));
    }

    @Test
    public void theAnswerBecomesTheMergedPath() {
        final TopicData d = data();
        assertEquals("Splitting work: good for whom?", d.topic);
        assertEquals(3, d.books.size());
        assertEquals("unknown books and chapters are dropped, a chapter counts once", 3, d.steps.size());
        assertEquals(0, d.steps.get(0).book);
        assertEquals(PathFixture.LABOUR, d.steps.get(0).key);
        assertEquals("Chapter I: Of the Division of Labour", d.steps.get(0).title);
        assertEquals("I.1", d.steps.get(0).code);
        assertEquals("Where the idea starts", d.steps.get(0).why);
        assertEquals("ch. 14#0", d.steps.get(1).key);
        assertEquals("14", d.steps.get(1).code);
        assertEquals("k1", d.steps.get(2).key);
        assertEquals(1, d.stepsOf(0));
        assertEquals("I.1", d.codesOf(0));
        assertEquals("no code for a chapter without a number", "", d.codesOf(2));

        assertEquals("a point needs two books", 1, d.agree.size());
        assertEquals(Arrays.asList(0, 1, 2), d.agree.get(0).books);
        assertEquals("Pins, watches, pig iron.", d.agree.get(0).note);
        assertEquals("a disagreement needs two positions", 1, d.disagree.size());
        assertEquals(2, d.disagree.get(0).positions.size());
        assertEquals("It cripples him.", d.disagree.get(0).positions.get(1).says);
        assertEquals(1, d.only.size());
        assertEquals(2, d.only.get(0).book);
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final TopicData back = TopicData.fromJson(new JSONObject(data().toJson().toString()));
        assertEquals("Splitting work: good for whom?", back.topic);
        assertEquals("capital.epub", back.books.get(1).name);
        assertEquals(1867, back.books.get(1).year);
        assertEquals(3, back.steps.size());
        assertEquals("The method", back.steps.get(2).why);
        assertEquals(3000, back.steps.get(2).chars);
        assertEquals(Arrays.asList(0, 1, 2), back.agree.get(0).books);
        assertEquals("Is it good for the worker?", back.disagree.get(0).question);
        assertEquals(1, back.disagree.get(0).positions.get(1).book);
        assertEquals("Timing every motion", back.only.get(0).point);
        assertEquals("m", back.model);
        assertEquals(9L, back.made);
        assertTrue(TopicData.fromJson(null).isEmpty());
    }

    @Test
    public void tolerantParsing() {
        assertTrue(TopicPrompts.parse(ANSWER).read);
        final TopicPrompts.Answer empty = TopicPrompts.parse("```json\n{\"path\": []}\n```");
        assertTrue(empty.read);
        assertTrue(empty.steps.isEmpty());
        // cut off inside the agreements: the whole chapters of the path are kept
        final TopicPrompts.Answer cut = TopicPrompts.parse("{\"path\": [{\"book\": 0, \"chapter\": 1, \"why\": \"a\"}, "
                + "{\"book\": 1, \"chapter\": 0, \"why\": \"b\"}], \"agree\": [{\"point\": \"Splitting wo");
        assertFalse(cut.read);
        assertEquals(2, cut.steps.size());
        assertEquals(1, cut.steps.get(1).book);
        assertTrue(TopicPrompts.parse("no").steps.isEmpty());
        assertTrue(TopicPrompts.parse(null).steps.isEmpty());
    }

    @Test
    public void aBookThatCannotBeOpenedGivesItsLinesAlone() {
        final SkeletonData d = new SkeletonData();
        d.chapterLines.put("a#0", "First line");
        d.chapterLines.put("b#0", " ");
        d.chars.put("a#0", 600L);
        final List<PathChapters.Entry> list = TopicPrompts.fromLines(d);
        assertEquals(1, list.size());
        assertEquals("a#0", list.get(0).key);
        assertEquals("", list.get(0).title);
        assertEquals("First line", list.get(0).line);
        assertEquals(600, list.get(0).chars);
    }
}
