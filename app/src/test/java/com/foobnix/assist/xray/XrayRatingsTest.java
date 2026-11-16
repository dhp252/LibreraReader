package com.foobnix.assist.xray;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class XrayRatingsTest {

    private static final List<Integer> KNOWN = Arrays.asList(1, 2, 3, 4, 5, 7);

    @Test
    public void readsRatingsAndTheWeakestSpot() {
        final XrayRatings r = XrayRatings.parse("Here:\n```json\n{\"ratings\":[{\"id\":2,\"rating\":\"source questioned\","
                + "\"label\":\"Pin shop\",\"why\":\"one shop\"},{\"id\":5,\"rating\":\"Strong\",\"label\":\"Practice\","
                + "\"why\":\"numbers\"},{\"id\":7,\"rating\":\"weak\"},{\"id\":42,\"rating\":\"weak\"},"
                + "{\"id\":3,\"rating\":\"great\"},{\"id\":5,\"rating\":\"weak\"}],"
                + "\"weakest\":{\"id\":7,\"title\":\"One story holds up this cause\",\"explanation\":\"A. B.\"}}\n```", KNOWN);
        assertEquals(3, r.entries.size());
        assertEquals(XrayRatings.Rating.QUESTIONED, r.rating(2));
        assertEquals("Pin shop", r.get(2).label);
        assertEquals(XrayRatings.Rating.STRONG, r.rating(5));
        assertEquals(XrayRatings.Rating.WEAK, r.rating(7));
        assertNull(r.rating(42));
        assertEquals(7, r.weakestId());
        assertEquals("One story holds up this cause", r.weakTitle);
        assertEquals("A. B.", r.weakWhy);
        assertEquals(1, r.count(XrayRatings.Rating.WEAK));
        assertEquals(1, r.count(XrayRatings.Rating.QUESTIONED));
    }

    @Test
    public void readsABareArrayAndFindsTheWeakestItself() {
        final XrayRatings r = XrayRatings.parse("[{\"id\":1,\"rating\":\"an observation\"},{\"id\":4,\"rating\":\"questioned\"},"
                + "{\"id\":5,\"rating\":\"weak spot\"}]", KNOWN);
        assertEquals(XrayRatings.Rating.OBSERVATION, r.rating(1));
        assertEquals(-1, r.weakest);
        assertEquals(5, r.weakestId());
    }

    @Test
    public void aWeakestNotRatedCountsAsWeak() {
        final XrayRatings r = XrayRatings.parse("{\"ratings\":[{\"id\":1,\"rating\":\"fair\"}],\"weakest\":{\"id\":3}}", KNOWN);
        assertEquals(XrayRatings.Rating.WEAK, r.rating(3));
        assertEquals(3, r.weakestId());
    }

    @Test
    public void nothingReadableIsNull() {
        assertNull(XrayRatings.parse(null, KNOWN));
        assertNull(XrayRatings.parse("I cannot rate this.", KNOWN));
        assertNull(XrayRatings.parse("{\"ratings\":[{\"id\":99,\"rating\":\"weak\"}]}", KNOWN));
        assertNull(XrayRatings.parse("{\"ratings\": [", KNOWN));
    }

    @Test
    public void everyIdeaHoldingUpHasNoWeakest() {
        final XrayRatings r = XrayRatings.parse("[{\"id\":1,\"rating\":\"strong\"},{\"id\":2,\"rating\":\"fair\"}]", null);
        assertEquals(-1, r.weakestId());
    }

    @Test
    public void storedAndReadBack() throws Exception {
        final XrayRatings r = XrayRatings.parse("{\"ratings\":[{\"id\":2,\"rating\":\"strong\",\"label\":\"A\",\"why\":\"b\"},"
                + "{\"id\":7,\"rating\":\"weak\"}],\"weakest\":{\"id\":7,\"title\":\"T\",\"explanation\":\"E.\"}}", KNOWN);
        r.ideasTime = 123L;
        r.model = "m";
        final XrayRatings back = XrayRatings.fromJson(new org.json.JSONObject(r.toJson().toString()));
        assertEquals(123L, back.ideasTime);
        assertEquals("m", back.model);
        assertEquals(XrayRatings.Rating.STRONG, back.rating(2));
        assertEquals("A", back.get(2).label);
        assertEquals("b", back.get(2).why);
        assertEquals(7, back.weakestId());
        assertEquals("T", back.weakTitle);
        assertEquals("E.", back.weakWhy);
        assertNull(XrayRatings.fromJson(null));
    }

    @Test
    public void keepsOnlyIdeasStillThere() {
        final XrayRatings r = XrayRatings.parse("{\"ratings\":[{\"id\":2,\"rating\":\"strong\"},{\"id\":7,\"rating\":\"weak\"}],"
                + "\"weakest\":{\"id\":7,\"title\":\"T\"}}", KNOWN);
        final XrayRatings kept = r.keep(Collections.singletonList(2));
        assertEquals(1, kept.entries.size());
        assertEquals(-1, kept.weakestId());
        assertEquals("", kept.weakTitle);
    }

    @Test
    public void ratingsOrderAndBars() {
        assertTrue(XrayRatings.Rating.WEAK.weakerThan(XrayRatings.Rating.QUESTIONED));
        assertTrue(XrayRatings.Rating.OBSERVATION.weakerThan(XrayRatings.Rating.FAIR));
        assertTrue(XrayRatings.Rating.STRONG.weakerThan(null));
        assertEquals(3, XrayRatings.Rating.STRONG.bars);
        assertEquals(1, XrayRatings.Rating.WEAK.bars);
        assertEquals(XrayRatings.Rating.FAIR, XrayRatings.Rating.of("moderate"));
        assertNull(XrayRatings.Rating.of(" "));
    }
}
