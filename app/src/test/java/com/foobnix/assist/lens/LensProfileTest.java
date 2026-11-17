package com.foobnix.assist.lens;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LensProfileTest {

    @Test
    public void aNewLensHasTheCanvasDefaultsAndNoField() {
        final LensProfile p = LensProfile.fromJson(null);
        assertFalse(p.isSet());
        assertTrue(p.analogies);
        assertTrue(p.plainWords);
        assertFalse(p.keepTerms);
        assertEquals("", p.fieldsText());
        assertEquals("", p.shortName());
        assertEquals("", p.through());
    }

    @Test
    public void presetsStayInTheCanvasOrderHoweverTheyArePicked() {
        final LensProfile p = new LensProfile();
        p.toggle("Finance");
        p.toggle("Machine learning");
        p.toggle("Software engineering");
        assertEquals(Arrays.asList("Machine learning", "Software engineering", "Finance"), p.fields);
        p.toggle("Software engineering");
        assertEquals(Arrays.asList("Machine learning", "Finance"), p.fields);
        assertTrue(p.has("Finance"));
        assertFalse(p.has("Software engineering"));
    }

    @Test
    public void ownFieldsAreSplitTrimmedAndCountedOnce() {
        final LensProfile p = new LensProfile();
        p.toggle("Cooking");
        p.own = " Nursing ,  chess;nursing\n cooking ,, Bee   keeping ";
        assertEquals(Arrays.asList("Nursing", "chess", "Bee keeping"), p.ownFields());
        assertEquals("Cooking, Nursing, chess, Bee keeping", p.fieldsText());
        assertTrue(p.isSet());
    }

    @Test
    public void aLensOfOnlyOwnFieldsIsSet() {
        final LensProfile p = new LensProfile();
        p.own = "Nursing";
        assertTrue(p.isSet());
        assertEquals("Nursing", p.shortName());
        assertEquals("Nursing", p.through());
    }

    @Test
    public void atMostSixFieldsAndFortyCharactersEach() {
        final LensProfile p = new LensProfile();
        for (String preset : LensProfile.PRESETS) {
            p.toggle(preset);
        }
        p.own = "Chess";
        assertEquals(LensProfile.MAX_FIELDS, p.allFields().size());
        assertFalse(p.allFields().contains("Chess"));

        final LensProfile q = new LensProfile();
        q.own = "a very long field name that goes on and on and on and on";
        assertEquals(1, q.ownFields().size());
        assertTrue(q.ownFields().get(0).length() <= LensProfile.FIELD_CHARS);
    }

    @Test
    public void shortNamesForTheChipAndTheSwitchLine() {
        final LensProfile p = new LensProfile();
        p.toggle("Machine learning");
        assertEquals("ML", p.shortName());
        assertEquals("ML", p.through());
        p.toggle("Software engineering");
        assertEquals("ML +1", p.shortName());
        assertEquals("ML and software", p.through());
        p.toggle("Finance");
        assertEquals("ML +2", p.shortName());
        assertEquals("ML, software and finance", p.through());

        final LensProfile q = new LensProfile();
        q.toggle("Cooking");
        assertEquals("Cooking", q.shortName());
        assertEquals("cooking", q.through());
    }

    @Test
    public void survivesTheStore() throws Exception {
        final LensProfile p = new LensProfile();
        p.toggle("Statistics");
        p.toggle("Medicine");
        p.own = "Sailing";
        p.analogies = false;
        p.plainWords = false;
        p.keepTerms = true;
        final LensProfile back = LensProfile.fromJson(new JSONObject(p.toJson().toString()));
        assertEquals(Arrays.asList("Statistics", "Medicine"), back.fields);
        assertEquals("Sailing", back.own);
        assertFalse(back.analogies);
        assertFalse(back.plainWords);
        assertTrue(back.keepTerms);
        assertEquals(p.signature(LensProfile.Level.SOME), back.signature(LensProfile.Level.SOME));
    }

    @Test
    public void aStoredLensWithoutSwitchesKeepsTheDefaults() throws Exception {
        final LensProfile p = LensProfile.fromJson(new JSONObject("{\"fields\":[\"Football\",\"football\",\"\"]}"));
        assertEquals(Arrays.asList("Football"), p.fields);
        assertTrue(p.analogies);
        assertTrue(p.plainWords);
        assertFalse(p.keepTerms);
    }

    @Test
    public void theSignatureFollowsFieldsSwitchesAndLevel() {
        final LensProfile p = new LensProfile();
        p.toggle("Machine learning");
        final String base = p.signature(LensProfile.Level.NEW);
        assertEquals(base, p.signature(null));
        assertNotEquals(base, p.signature(LensProfile.Level.EXPERT));
        p.keepTerms = true;
        assertNotEquals(base, p.signature(LensProfile.Level.NEW));
        p.keepTerms = false;
        p.toggle("Finance");
        assertNotEquals(base, p.signature(LensProfile.Level.NEW));
        p.toggle("Finance");
        assertEquals(base, p.signature(LensProfile.Level.NEW));
    }

    @Test
    public void levels() throws Exception {
        assertEquals(LensProfile.Level.NEW, LensProfile.Level.of(null));
        assertEquals(LensProfile.Level.NEW, LensProfile.Level.of("nonsense"));
        assertEquals(LensProfile.Level.SOME, LensProfile.Level.of(" Some "));
        assertEquals(LensProfile.Level.EXPERT, LensProfile.Level.of("expert"));
        assertEquals(LensProfile.Level.NEW, LensProfile.Level.fromJson(null));
        assertEquals(LensProfile.Level.EXPERT,
                LensProfile.Level.fromJson(new JSONObject(LensProfile.Level.EXPERT.toJson().toString())));
    }

    @Test
    public void thePreviewFollowsTheLens() {
        final LensProfile p = new LensProfile();
        assertEquals("", LensPreview.yours(p));
        assertEquals("", LensPreview.yours(null));
        p.toggle("Machine learning");
        assertEquals("splitting a monolith into pipeline stages, each owned by one person", LensPreview.yours(p));
        p.keepTerms = true;
        assertTrue(LensPreview.yours(p).startsWith(LensPreview.PHRASE + ": "));
        p.keepTerms = false;
        p.analogies = false;
        assertEquals(LensPreview.PLAIN, LensPreview.yours(p));

        final LensProfile own = new LensProfile();
        own.own = "Beekeeping";
        assertTrue(LensPreview.yours(own).contains("Beekeeping"));
        for (String preset : LensProfile.PRESETS) {
            assertFalse(preset, LensPreview.analogy(preset).contains(preset + " is split"));
            assertFalse(preset, LensPreview.analogy(preset).startsWith("how work in"));
        }
    }
}
