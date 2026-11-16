package com.foobnix.assist.debate;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DebateReplyTest {

    private static final Set<Integer> IDS = new HashSet<>(Arrays.asList(1, 2, 3));

    @Test
    public void readsTheAnswerAsAsked() {
        final DebateReply r = DebateReply.parse("{\"position\":\"It makes everyone richer\",\"reply\":\"Worse off? Ten men "
                + "making 48,000 pins a day can buy far more.\",\"quote\":\"\",\"idea\":2,\"verdict\":{\"point\":\"author\","
                + "\"why\":\"Smith answers with the numbers you left out.\",\"next\":\"Is richer the same as better off?\"},"
                + "\"crux\":\"\"}", IDS);
        assertNotNull(r);
        assertEquals("It makes everyone richer", r.position);
        assertEquals("Worse off? Ten men making 48,000 pins a day can buy far more.", r.reply);
        assertEquals("", r.quote);
        assertEquals(2, r.idea);
        assertEquals(DebateReply.Point.AUTHOR, r.point);
        assertEquals("Smith answers with the numbers you left out.", r.why);
        assertEquals("Is richer the same as better off?", r.next);
        assertEquals("", r.crux);
    }

    @Test
    public void readsAnAnswerWrappedInTextAndAFence() {
        final DebateReply r = DebateReply.parse("Here is the round:\n```json\n{\"reply\": \"There I agree\\nwith you.\", "
                + "\"quote\": \"as stupid and ignorant\", \"idea\": \"3\", \"verdict\": {\"point\": \"Reader\", \"reason\": "
                + "\"He concedes the harm.\"}, \"crux\": \"Whether wealth makes up for a dull life.\"}\n```\nDone.", IDS);
        assertNotNull(r);
        assertEquals("There I agree with you.", r.reply);
        assertEquals("as stupid and ignorant", r.quote);
        assertEquals(3, r.idea);
        assertEquals(DebateReply.Point.READER, r.point);
        assertEquals("He concedes the harm.", r.why);
        assertEquals("", r.next);
        assertEquals("Whether wealth makes up for a dull life.", r.crux);
    }

    @Test
    public void readsFlatAndNestedShapes() {
        final DebateReply flat = DebateReply.parse("{\"reply\":\"No.\",\"point\":\"draw\",\"why\":\"Neither moved.\","
                + "\"next\":\"What would change your mind?\",\"idea\":9}", IDS);
        assertEquals(DebateReply.Point.DRAW, flat.point);
        assertEquals("Neither moved.", flat.why);
        assertEquals("What would change your mind?", flat.next);
        // an idea that is not in the chapter is dropped
        assertEquals(-1, flat.idea);

        final DebateReply nested = DebateReply.parse("{\"author\":{\"reply\":\"I hold to it.\",\"quote\":\"q\",\"idea\":1},"
                + "\"referee\":{\"winner\":\"the reader\",\"sentence\":\"You found the gap.\",\"question\":\"And then?\"}}", IDS);
        assertEquals("I hold to it.", nested.reply);
        assertEquals("q", nested.quote);
        assertEquals(1, nested.idea);
        assertEquals(DebateReply.Point.READER, nested.point);
        assertEquals("You found the gap.", nested.why);
        assertEquals("And then?", nested.next);

        final DebateReply plain = DebateReply.parse("{\"author\":\"I hold to it.\",\"verdict\":\"author\"}", null);
        assertEquals("I hold to it.", plain.reply);
        assertEquals(DebateReply.Point.AUTHOR, plain.point);
    }

    @Test
    public void nothingReadableIsNull() {
        assertNull(DebateReply.parse(null, IDS));
        assertNull(DebateReply.parse("", IDS));
        assertNull(DebateReply.parse("I cannot answer that.", IDS));
        assertNull(DebateReply.parse("{\"reply\": \"cut off", IDS));
        assertNull(DebateReply.parse("{\"verdict\":{\"point\":\"reader\"}}", IDS));
        assertNull(DebateReply.parse("{\"reply\":\"  \"}", IDS));
    }

    @Test
    public void aPointThatCannotBeToldIsADraw() {
        assertEquals(DebateReply.Point.READER, DebateReply.Point.of("you"));
        assertEquals(DebateReply.Point.READER, DebateReply.Point.of(" READER "));
        assertEquals(DebateReply.Point.AUTHOR, DebateReply.Point.of("the author"));
        assertEquals(DebateReply.Point.DRAW, DebateReply.Point.of("tie"));
        assertEquals(DebateReply.Point.DRAW, DebateReply.Point.of("neither the reader nor the author"));
        assertEquals(DebateReply.Point.DRAW, DebateReply.Point.of("Smith"));
        assertEquals(DebateReply.Point.DRAW, DebateReply.Point.of(null));
        for (DebateReply.Point p : DebateReply.Point.values()) {
            assertEquals(p, DebateReply.Point.of(p.code));
        }
    }
}
