package com.foobnix.assist.zoom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZoomPromptsTest {

    @Test
    public void theRequestAsksForEveryLevelAsOneObject() {
        final String s = ZoomPrompts.system("");
        for (String key : new String[]{"\"line\"", "\"sections\"", "\"lean\"", "\"sentences\"", "\"quote\"", "\"end\"",
                "\"folded\"", "\"kept\"", "\"claims\"", "\"kind\"", "\"example\"", "\"start\"", "\"question\""}) {
            assertTrue(key, s.contains(key));
        }
        for (ZoomKind k : ZoomKind.values()) {
            assertTrue(k.id, s.contains(k.id));
        }
        assertTrue(s.contains("the language the text is written in"));
        assertTrue(s.contains("copied exactly"));
        assertTrue(s.contains("only a JSON object"));
    }

    @Test
    public void theAnswerLanguageIsTheReaders() {
        final String s = ZoomPrompts.system(" Vietnamese ");
        assertTrue(s.contains("in Vietnamese."));
        assertFalse(s.contains("the language the text is written in"));
    }

    @Test
    public void theExampleInTheRequestIsReadable() {
        final String s = ZoomPrompts.system("");
        final String example = s.substring(s.indexOf("{\"line\""), s.indexOf('\n', s.indexOf("{\"line\"")));
        final ZoomData d = ZoomData.parse(example);
        assertTrue(d.hasLean());
        assertTrue(d.sections.get(0).blocks.get(1).fold);
        assertFalse(d.claims.isEmpty());
    }

    @Test
    public void theTextComesWithItsBookAndChapter() {
        final String u = ZoomPrompts.user("The Wealth of Nations", "CHAPTER I", "The text.", false);
        assertTrue(u.startsWith("Book: The Wealth of Nations\nChapter: CHAPTER I\n\nThe text."));
        final String part = ZoomPrompts.user(null, null, "The text.", true);
        assertTrue(part.contains("Only the first part"));
        assertTrue(part.endsWith("The text."));
    }
}
