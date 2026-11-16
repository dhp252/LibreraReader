package com.foobnix.assist.updated;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UpdatedChapterTest {

    private static final String PINS = "Ten workers, each on a few of about eighteen steps, made about 48,000 pins a day.";
    private static final String WHY = "Why it works: practice, no time lost switching, and machines.";
    private static final String BOY = "Workers invent machines: one boy rigged a fire-engine valve so he could go and play.";

    private static final String ANSWER = "Here is the JSON:\n```json\n{\"first_published\": 1776, \"notes\": ["
            + "{\"kind\": \"missed\", \"quote\": \"" + WHY + "\", \"title\": \"A fourth reason Smith didn't name\","
            + " \"body\": \"In 1832 Charles Babbage pointed out more.\", \"settled\": \"well established\","
            + " \"settled_note\": \"now called the Babbage principle\", \"source\": {\"work\": \"On the Economy of"
            + " Machinery and Manufactures\", \"author\": \"Charles Babbage\", \"year\": 1832},"
            + " \"terms\": \"Don't pay senior rates for labeling.\"},"
            + "{\"kind\": \"Source check\", \"quote\": \"" + PINS + "\", \"title\": \"Where the 18 steps come from\","
            + " \"body\": \"Historians trace them to the Encyclopédie.\", \"settled\": \"debated\", \"source\": null},"
            + "{\"kind\": \"doubtful\", \"quote\": \"A sentence that is nowhere in the chapter at all, really.\","
            + " \"title\": \"Made up\", \"body\": \"x\", \"settled\": \"speculative\", \"source\": null},"
            + "{\"kind\": \"gossip\", \"quote\": \"" + BOY + "\", \"title\": \"t\", \"body\": \"b\"},"
            + "{\"kind\": \"newer\", \"title\": \"No quote\", \"body\": \"b\"}],"
            + " \"objection\": {\"claim\": \"Splitting work makes everyone richer.\", \"objection\": \"It makes the"
            + " worker poorer in mind.\", \"strength\": \"strong\", \"twist\": {\"head\": \"Smith agreed, in Book V\","
            + " \"quote\": \"“" + BOY + "”\", \"note\": \"His fix: a little school.\"}, \"pushed\": {\"who\":"
            + " \"Marx\", \"work\": \"Capital\", \"year\": \"1867\", \"text\": \"It cripples the worker.\"},"
            + " \"disagree\": {\"author\": \"The gains are worth it.\", \"other\": \"The damage is built in.\"}}}\n```";

    /** The chapter's words, two pages. */
    private static List<String> words(List<Integer> pages) {
        final List<String> words = new ArrayList<>();
        for (String w : (PINS + " Some filler words between the sentences of the page.").split(" ")) {
            words.add(w);
            pages.add(4);
        }
        for (String w : (WHY + " " + BOY).split(" ")) {
            words.add(w);
            pages.add(5);
        }
        return words;
    }

    @Test
    public void parsesNotesAndLeavesOutBadOnes() {
        final UpdatedChapter c = UpdatedChapter.parse(ANSWER);
        assertNotNull(c);
        // no kind and no quote are left out; the one not in the chapter stays until place()
        assertEquals(3, c.notes.size());
        assertEquals(1776, c.firstPublished);
        final UpdatedNote first = c.notes.get(0);
        assertEquals(UpdatedKind.MISSED, first.kind);
        assertEquals(UpdatedSettled.ESTABLISHED, first.settled);
        assertEquals("now called the Babbage principle", first.settledNote);
        assertTrue(first.hasSource());
        assertEquals("On the Economy of Machinery and Manufactures, Charles Babbage, 1832",
                UpdatedText.sourceLine(first.source));
        assertEquals("Don't pay senior rates for labeling.", first.terms);
        final UpdatedNote second = c.notes.get(1);
        assertEquals(UpdatedKind.SOURCE, second.kind);
        assertEquals(UpdatedSettled.DEBATED, second.settled);
        assertFalse(second.hasSource());
        assertEquals("", UpdatedText.sourceLine(second.source));
    }

    @Test
    public void keepsOnlyNotesFoundInTheChapterInItsOrder() {
        final UpdatedChapter c = UpdatedChapter.parse(ANSWER);
        final List<Integer> pages = new ArrayList<>();
        final List<String> words = words(pages);
        c.place(words, pages);
        assertEquals(2, c.notes.size());
        // the chapter's order, not the answer's
        assertEquals(UpdatedKind.SOURCE, c.notes.get(0).kind);
        assertEquals(4, c.notes.get(0).page);
        assertEquals(UpdatedKind.MISSED, c.notes.get(1).kind);
        assertEquals(5, c.notes.get(1).page);
        assertEquals(WHY, c.notes.get(1).quote);
        // the twist's words are in the chapter: flagged, with their page, without the marks around them
        assertTrue(c.objection.twistInChapter);
        assertEquals(5, c.objection.twistPage);
        assertEquals(BOY, c.objection.twistQuote);
    }

    @Test
    public void atMostSixNotesAndOnePerSentence() {
        final StringBuilder text = new StringBuilder();
        final StringBuilder json = new StringBuilder("{\"notes\":[");
        for (int i = 0; i < 9; i++) {
            final String sentence = "Sentence number " + i + " of the chapter says something quite particular here.";
            text.append(sentence).append(' ');
            json.append(i > 0 ? "," : "").append("{\"kind\":\"newer\",\"quote\":\"").append(sentence)
                    .append("\",\"title\":\"T").append(i).append("\",\"body\":\"b\"}");
        }
        // the same sentence twice
        json.append(",{\"kind\":\"source\",\"quote\":\"Sentence number 0 of the chapter says something quite "
                + "particular here.\",\"title\":\"again\",\"body\":\"b\"}]}");
        final UpdatedChapter c = UpdatedChapter.parse(json.toString());
        assertEquals(10, c.notes.size());
        final List<String> words = Arrays.asList(text.toString().trim().split(" "));
        final List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            pages.add(0);
        }
        c.place(words, pages);
        assertEquals(UpdatedChapter.MAX_NOTES, c.notes.size());
        assertEquals("T0", c.notes.get(0).title);
        assertEquals("T5", c.notes.get(5).title);
    }

    @Test
    public void objectionIsRead() {
        final UpdatedObjection o = UpdatedChapter.parse(ANSWER).objection;
        assertNotNull(o);
        assertEquals(UpdatedObjection.Strength.STRONG, o.strength);
        assertEquals("Smith agreed, in Book V", o.twistHead);
        assertEquals("His fix: a little school.", o.twistNote);
        assertFalse(o.twistInChapter);
        assertEquals("Marx, Capital, 1867", UpdatedText.pushedLine(o));
        assertTrue(o.hasDisagree());
        assertEquals("The damage is built in.", o.otherSide);
    }

    @Test
    public void nullPartsOfTheObjectionStayEmpty() {
        final UpdatedChapter c = UpdatedChapter.parse("{\"notes\": [], \"objection\": {\"claim\": \"A.\", \"objection\":"
                + " \"B.\", \"strength\": \"who knows\", \"twist\": null, \"pushed\": {\"who\": \"Someone\", \"text\":"
                + " null}, \"disagree\": \"They differ on who pays.\"}, \"first_published\": null}");
        assertTrue(c.notes.isEmpty());
        assertFalse(c.isEmpty());
        assertEquals(0, c.firstPublished);
        assertEquals(UpdatedObjection.Strength.UNKNOWN, c.objection.strength);
        assertFalse(c.objection.hasTwist());
        assertFalse(c.objection.hasPushed());
        assertEquals("", UpdatedText.pushedLine(c.objection));
        assertEquals("They differ on who pays.", c.objection.disagree);
        // no claim, no objection
        assertNull(UpdatedChapter.parse("{\"notes\": [], \"objection\": {\"claim\": \"\", \"objection\": \"B\"}}").objection);
        assertNull(UpdatedChapter.parse("{\"notes\": [], \"objection\": null}").objection);
    }

    @Test
    public void bareListAndUnreadableAnswers() {
        final UpdatedChapter list = UpdatedChapter.parse("[{\"kind\":\"newer\",\"quote\":\"" + PINS
                + "\",\"title\":\"t\",\"body\":\"b\"}]");
        assertEquals(1, list.notes.size());
        assertNull(list.objection);
        assertNull(UpdatedChapter.parse("I cannot help with that."));
        assertNull(UpdatedChapter.parse("{\"notes\": [ {broken"));
        assertNull(UpdatedChapter.parse(null));
        assertTrue(UpdatedChapter.parse("{}").isEmpty());
    }

    @Test
    public void aSourceWithoutAWorkIsNoSource() {
        final UpdatedChapter c = UpdatedChapter.parse("{\"notes\":[{\"kind\":\"source\",\"quote\":\"" + PINS
                + "\",\"title\":\"t\",\"body\":\"b\",\"settled\":\"\",\"source\":{\"work\":\"unknown\",\"author\":"
                + "\"Someone\",\"year\":1900}},{\"kind\":\"source\",\"quote\":\"" + WHY + "\",\"title\":\"t\","
                + "\"body\":\"b\",\"source\":{\"work\":\"Encyclopédie\",\"author\":\"N/A\",\"year\":\"c. 1755\"}}]}");
        assertFalse(c.notes.get(0).hasSource());
        assertNull(c.notes.get(0).source);
        assertEquals(UpdatedSettled.UNKNOWN, c.notes.get(0).settled);
        assertEquals("Encyclopédie, c. 1755", UpdatedText.sourceLine(c.notes.get(1).source));
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final UpdatedChapter c = UpdatedChapter.parse(ANSWER);
        final List<Integer> pages = new ArrayList<>();
        c.place(words(pages), pages);
        c.title = "Of the Division of Labour";
        c.layout = UpdatedChapter.layoutKey(false, 1080, 1920, 22);
        c.at = 1234L;
        c.model = "m";
        c.author = "Adam Smith";
        final UpdatedChapter back = UpdatedChapter.fromJson(new JSONObject(c.toJson().toString()));
        assertEquals("Of the Division of Labour", back.title);
        assertEquals("1080x1920@22", back.layout);
        assertEquals(1234L, back.at);
        assertEquals(1776, back.firstPublished);
        assertEquals("Adam Smith", back.author);
        assertEquals(2, back.notes.size());
        final UpdatedNote missed = back.notes.get(1);
        assertEquals(UpdatedKind.MISSED, missed.kind);
        assertEquals(5, missed.page);
        assertEquals(5, missed.lastPage);
        assertEquals(UpdatedSettled.ESTABLISHED, missed.settled);
        assertEquals("Charles Babbage", missed.source.author);
        assertEquals("1832", missed.source.year);
        assertEquals("Don't pay senior rates for labeling.", missed.terms);
        assertFalse(back.notes.get(0).hasSource());
        assertTrue(back.objection.twistInChapter);
        assertEquals(5, back.objection.twistPage);
        assertEquals("Marx", back.objection.pushedWho);
        assertNull(UpdatedChapter.fromJson(null));
    }

    @Test
    public void filtersByKind() {
        final UpdatedChapter c = UpdatedChapter.parse(ANSWER);
        final List<Integer> pages = new ArrayList<>();
        c.place(words(pages), pages);
        assertEquals(2, c.notes(null).size());
        assertEquals(1, c.count(UpdatedKind.SOURCE));
        assertEquals(1, c.count(UpdatedKind.MISSED));
        assertEquals(0, c.count(UpdatedKind.DOUBTFUL));
        assertEquals(UpdatedKind.MISSED, c.notes(UpdatedKind.MISSED).get(0).kind);
        // a tap chooses a kind, a second tap shows all again, a kind without notes changes nothing
        assertEquals(UpdatedKind.SOURCE, c.toggle(null, UpdatedKind.SOURCE));
        assertNull(c.toggle(UpdatedKind.SOURCE, UpdatedKind.SOURCE));
        assertEquals(UpdatedKind.MISSED, c.toggle(UpdatedKind.SOURCE, UpdatedKind.MISSED));
        assertEquals(UpdatedKind.SOURCE, c.toggle(UpdatedKind.SOURCE, UpdatedKind.DOUBTFUL));
    }

    @Test
    public void pagesAreFoundAgainOnlyInAnotherLayout() {
        final UpdatedChapter c = UpdatedChapter.parse(ANSWER);
        c.layout = UpdatedChapter.layoutKey(false, 1080, 1920, 22);
        assertFalse(c.needsPages("1080x1920@22"));
        assertTrue(c.needsPages("1080x1920@26"));
        assertEquals("fixed", UpdatedChapter.layoutKey(true, 1, 2, 3));
        assertTrue(UpdatedChapter.onPage(Arrays.asList(WHY.split(" ")), WHY));
        assertFalse(UpdatedChapter.onPage(Arrays.asList(PINS.split(" ")), WHY));
        assertEquals("updated:ch#0", UpdatedChapter.key("ch#0"));
    }

    @Test
    public void firstPublishedMustBePlausible() {
        assertEquals(1776, UpdatedChapter.publishedYear(1776));
        assertEquals(1776, UpdatedChapter.publishedYear("1776"));
        assertEquals(-380, UpdatedChapter.publishedYear(-380));
        assertEquals(0, UpdatedChapter.publishedYear(17760));
        assertEquals(0, UpdatedChapter.publishedYear("around 1776"));
        assertEquals(0, UpdatedChapter.publishedYear(null));
        assertEquals(0, UpdatedChapter.publishedYear(JSONObject.NULL));
    }
}
