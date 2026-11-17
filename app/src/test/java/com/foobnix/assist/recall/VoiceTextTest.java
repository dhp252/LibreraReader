package com.foobnix.assist.recall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceTextTest {

    @Test
    public void piecesAddUpAsSentences() {
        final VoiceText t = new VoiceText();
        assertTrue(t.isEmpty());
        t.partial("splitting the job");
        assertEquals("splitting the job", t.text());
        t.partial("splitting the job into steps");
        t.add("splitting the job into  steps makes way more pins");
        assertEquals("Splitting the job into steps makes way more pins", t.text());
        t.partial("people get");
        assertEquals("Splitting the job into steps makes way more pins. people get", t.text());
        t.add("people get really good at their one task.");
        t.add("  ");
        t.add(null);
        assertEquals("Splitting the job into steps makes way more pins. People get really good at their one task.",
                t.text());
        assertFalse(t.isEmpty());
        t.clear();
        assertEquals("", t.text());
    }

    @Test
    public void aGuessInProgressIsKeptWhenRecognitionStops() {
        final VoiceText t = new VoiceText();
        t.add("and");
        t.partial("machines I think");
        t.commit();
        assertEquals("And. Machines I think", t.text());
        // nothing in progress: nothing added
        t.commit();
        assertEquals("And. Machines I think", t.text());
    }

    @Test
    public void spokenWordsJoinWhatWasTyped() {
        assertEquals("Said", VoiceText.join("", "Said"));
        assertEquals("Typed", VoiceText.join(" Typed ", null));
        assertEquals("Typed. Said", VoiceText.join("Typed", "Said"));
        assertEquals("Typed! Said", VoiceText.join("Typed!", " Said "));
        assertEquals("", VoiceText.join(null, null));
    }
}
