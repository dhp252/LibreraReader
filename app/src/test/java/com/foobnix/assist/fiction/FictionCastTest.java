package com.foobnix.assist.fiction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class FictionCastTest {

    static final String ANSWER = "Here is the who's who:\n```json\n{\"story\": true, \"people\": ["
            + "{\"id\": \"fogg\", \"name\": \"Phileas Fogg\", \"aliases\": [\"Mr. Fogg\", \"the gentleman\"], "
            + "\"gender\": \"m\", \"role\": \"Precise English gentleman\", \"first\": 2, "
            + "\"as_of\": \"A member of the Reform Club who has bet that he can go round the world in eighty days.\", "
            + "\"now\": \"On the train to Calcutta.\", \"group\": \"Reform Club\", \"minor\": false, "
            + "\"moments\": [{\"ch\": 4, \"line\": \"Makes the bet at the club.\"}, {\"ch\": 14, \"line\": \"Saves Aouda.\"}]},"
            + "{\"id\": \"Aouda\", \"name\": \"Aouda\", \"aliases\": \"Mrs. Aouda; the young woman\", \"gender\": \"female\", "
            + "\"role\": \"Rescued by Fogg\", \"first\": \"13\", \"as_of\": \"A young Parsee woman.\", "
            + "\"now\": \"Travelling with Fogg.\", \"minor\": false, \"moments\": [\"Rescued at dawn.\"]},"
            + "{\"id\": \"guide\", \"name\": \"The Parsee guide\", \"first\": 12, \"minor\": true},"
            + "{\"id\": \"proctor\", \"name\": \"Colonel Proctor\", \"first\": 26, \"role\": \"A quarrelsome American\"},"
            + "{\"name\": \"\"}], "
            + "\"relations\": [{\"a\": \"fogg\", \"b\": \"Reform Club\", \"label\": \"the £20,000 bet\", \"ch\": 4},"
            + "{\"a\": \"fogg\", \"b\": \"aouda\", \"label\": \"her rescuer\"},"
            + "{\"a\": \"fogg\", \"b\": \"proctor\", \"label\": \"a duel\", \"ch\": 29}]}\n```";

    static FictionCast verneCast() {
        final FictionChapters c = FictionChaptersTest.verne();
        // the text sent ends in the book's chapter 14: its number in the list is 15
        final FictionCast cast = new FictionCast();
        cast.merge(FictionCast.parse(ANSWER, c, 1, 15));
        cast.mark = FictionMark.at(c, 4 + 13 * 10 + 4);
        return cast;
    }

    @Test
    public void anAnswerIsReadTolerantly() {
        final FictionCast cast = FictionCast.parse(ANSWER, FictionChaptersTest.verne(), 1, 15);
        assertNotNull(cast);
        assertTrue(cast.story);
        assertEquals(3, cast.people.size());
        final FictionCast.Person fogg = cast.person("fogg");
        assertEquals("Phileas Fogg", fogg.name);
        assertEquals(2, fogg.aliases.size());
        assertEquals("m", fogg.gender);
        assertEquals(2, fogg.first);
        assertEquals(FictionChaptersTest.verne().key(2), fogg.firstKey);
        assertEquals("Reform Club", fogg.group);
        assertEquals(2, fogg.moments.size());
        final FictionCast.Person aouda = cast.person("aouda");
        assertEquals("f", aouda.gender);
        assertEquals(13, aouda.first);
        assertEquals(2, aouda.aliases.size());
        // a moment without a chapter: the latest it can be
        assertEquals(15, aouda.moments.get(0).ch);
        assertTrue(cast.person("guide").minor);
    }

    @Test
    public void whatTheModelPlacesAfterTheTextIsDropped() {
        final FictionCast cast = FictionCast.parse(ANSWER, FictionChaptersTest.verne(), 1, 15);
        // Colonel Proctor is met in chapter 26: the text ended at 15
        assertNull(cast.person("proctor"));
        assertEquals(2, cast.relations.size());
        for (FictionCast.Relation r : cast.relations) {
            assertFalse(r.label.contains("duel"));
            assertTrue(r.ch <= 15);
        }
        // a tie whose chapter is not given: the latest it can be
        assertEquals(15, cast.relations.get(1).ch);
    }

    @Test
    public void anUnreadableAnswerIsNullAndANonStorySaysSo() {
        assertNull(FictionCast.parse("I cannot help with that.", FictionChaptersTest.verne(), 1, 15));
        assertNull(FictionCast.parse(null, FictionChaptersTest.verne(), 1, 15));
        final FictionCast no = FictionCast.parse("{\"story\": false}", FictionChaptersTest.verne(), 1, 15);
        assertFalse(no.story);
        assertTrue(no.isEmpty());
    }

    @Test
    public void theStoredItemReadsBackTheSame() {
        final FictionCast cast = verneCast();
        cast.model = "m";
        final FictionCast back = FictionCast.fromJson(new JSONObject(cast.toJson().toString()));
        assertEquals(3, back.people.size());
        assertEquals(2, back.relations.size());
        assertEquals(cast.mark.end, back.mark.end);
        assertEquals(cast.mark.pages, back.mark.pages);
        assertEquals(cast.mark.key, back.mark.key);
        assertEquals(15, back.mark.ch);
        assertEquals(38, back.mark.chapters);
        assertEquals("m", back.model);
        final FictionCast.Person fogg = back.person("fogg");
        assertEquals("Phileas Fogg", fogg.name);
        assertEquals("the gentleman", fogg.aliases.get(1));
        assertEquals("On the train to Calcutta.", fogg.now);
        assertEquals(2, fogg.moments.size());
        assertEquals("Saves Aouda.", fogg.moments.get(1).line);
        assertEquals(FictionChaptersTest.verne().key(14), fogg.moments.get(1).key);
        assertTrue(back.person("guide").minor);
        assertTrue(FictionCast.fromJson(null).isEmpty());
        assertTrue(FictionCast.fromJson(new JSONObject("{\"people\": \"x\"}")).isEmpty());
    }

    @Test
    public void anUpdateAddsPeopleAndRewritesWhatChanged() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = verneCast();
        final String update = "{\"people\": ["
                + "{\"id\": \"aouda\", \"as_of\": \"A young Parsee woman, now on the way to Hong Kong.\", "
                + "\"now\": \"Aboard the Rangoon.\", \"moments\": [{\"ch\": 16, \"line\": \"Sails for Hong Kong.\"}, "
                + "{\"ch\": 15, \"line\": \"Rescued at dawn.\"}]},"
                + "{\"id\": \"the-guide\", \"name\": \"the parsee guide\", \"minor\": false, \"aliases\": [\"the Parsee\"]},"
                + "{\"id\": \"oysterpuff\", \"name\": \"Judge Obadiah\", \"first\": 16, \"role\": \"A judge at Calcutta\", \"minor\": true}],"
                + "\"relations\": [{\"a\": \"fogg\", \"b\": \"reform club\", \"label\": \"the wager\", \"ch\": 16},"
                + "{\"a\": \"oysterpuff\", \"b\": \"Fogg\", \"label\": \"sentences him\", \"ch\": 16},"
                + "{\"a\": \"nobody\", \"b\": \"fogg\", \"label\": \"x\", \"ch\": 16}]}";
        cast.merge(FictionCast.parse(update, c, 16, 17));
        assertEquals(4, cast.people.size());
        final FictionCast.Person aouda = cast.person("aouda");
        assertEquals("Aouda", aouda.name);
        assertEquals("Aboard the Rangoon.", aouda.now);
        assertEquals("Rescued by Fogg", aouda.role);
        // she was first met in 13, whatever the update says
        assertEquals(13, aouda.first);
        // the line she had is not added twice
        assertEquals(2, aouda.moments.size());
        // the same person under another id is found by name; the update made them a main person
        assertNull(cast.person("the-guide"));
        assertFalse(cast.person("guide").minor);
        assertEquals("the Parsee", cast.person("guide").aliases.get(0));
        assertEquals(12, cast.person("guide").first);
        assertEquals(16, cast.person("oysterpuff").first);
        // a tie of the same pair is relabelled and keeps where it began; one to a name finds the person
        assertEquals(3, cast.relations.size());
        assertEquals("the wager", cast.relations.get(0).label);
        assertEquals(4, cast.relations.get(0).ch);
        assertEquals("fogg", cast.relations.get(2).b);
    }

    @Test
    public void aRevealedNameKeepsTheOldOneAsAnAlias() {
        final FictionChapters c = FictionChaptersTest.verne();
        final FictionCast cast = new FictionCast();
        cast.merge(FictionCast.parse("{\"people\": [{\"id\": \"stranger\", \"name\": \"The stranger\", \"first\": 3}]}", c, 1, 5));
        cast.merge(FictionCast.parse("{\"people\": [{\"id\": \"stranger\", \"name\": \"Fix\", \"first\": 6}]}", c, 6, 8));
        assertEquals(1, cast.people.size());
        assertEquals("Fix", cast.people.get(0).name);
        assertEquals("The stranger", cast.people.get(0).aliases.get(0));
        assertEquals(3, cast.people.get(0).first);
        assertEquals(cast.people.get(0), cast.byName("the STRANGER"));
    }

    @Test
    public void initialsAndIds() {
        assertEquals("PF", FictionCast.initials("Phileas Fogg"));
        assertEquals("A", FictionCast.initials("Aouda"));
        assertEquals("FC", FictionCast.initials("Sir Francis Cromarty"));
        assertEquals("F", FictionCast.initials("Mr. Fogg"));
        assertEquals("P", FictionCast.initials("The Parsee guide"));
        assertEquals("K", FictionCast.initials("Kiouni, the elephant"));
        assertEquals("", FictionCast.initials(""));
        assertEquals("phileas-fogg", FictionCast.slug("Phileas Fogg"));
        assertEquals("jean-passepartout", FictionCast.slug(" Jean  “Passepartout” "));
        assertEquals("", FictionCast.slug("…"));
    }
}
