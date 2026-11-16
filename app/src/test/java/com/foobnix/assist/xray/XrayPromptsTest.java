package com.foobnix.assist.xray;

import com.foobnix.ai.AiCategory;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XrayPromptsTest {

    @Test
    public void sendsOnlyTheIdeasAndTwoMarksEach() {
        final String longQuote = "word " + new String(new char[500]).replace("\0", "x ");
        final String user = XrayPrompts.user("The Wealth of Nations", "Of the Division of Labour", Arrays.asList(
                new XrayIdea(1, "Splitting work multiplies output", AiCategory.MAIN, -1, true, 3,
                        Arrays.asList("The greatest improvement\n in the productive powers", "second", "third"), null, -1),
                new XrayIdea(2, "Pin shop", AiCategory.EVIDENCE, 1, true, 4, Collections.singletonList(longQuote), null, -1),
                new XrayIdea(3, "Not located", AiCategory.CAVEAT, 1, false, -1, Collections.<String>emptyList(), null, -1)));
        assertTrue(user.contains("Book: The Wealth of Nations\nChapter: Of the Division of Labour\n"));
        assertTrue(user.contains("1 [main]: Splitting work multiplies output\n   \"The greatest improvement in the productive powers\"\n   \"second\"\n"));
        assertFalse(user.contains("third"));
        assertTrue(user.contains("2 [evidence] supports 1: Pin shop\n"));
        assertTrue(user.contains("3 [caveat] supports 1: Not located\n"));
        assertTrue(user.contains("…\"\n"));
        for (String line : user.split("\n")) {
            assertTrue(line, line.length() <= XrayPrompts.QUOTE_CHARS + 10);
        }
    }

    @Test
    public void systemNamesEveryRatingAndTheLanguage() {
        final String system = XrayPrompts.system("Vietnamese");
        for (XrayRatings.Rating r : XrayRatings.Rating.values()) {
            assertTrue(r.code, system.contains("- " + r.code + ":"));
        }
        assertTrue(system.contains("in Vietnamese."));
        assertTrue(XrayPrompts.system("").contains("the language the ideas are written in"));
    }

    @Test
    public void cutsAtASpace() {
        assertEquals("one two…", XrayPrompts.cut("one two three", 10));
        assertEquals("short", XrayPrompts.cut("short", 10));
    }
}
