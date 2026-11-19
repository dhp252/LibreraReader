package com.foobnix.webdav;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pulling a book's files before an AI request (F-016): at most once a minute per book. */
public class ServerFirstTest {

    @Test
    public void firstTimeIsDue() {
        assertTrue(ServerFirst.due(null, 1000));
    }

    @Test
    public void onceAMinute() {
        final long at = 1_000_000;
        assertFalse(ServerFirst.due(at, at + 1));
        assertFalse(ServerFirst.due(at, at + ServerFirst.EVERY_MS - 1));
        assertTrue(ServerFirst.due(at, at + ServerFirst.EVERY_MS));
    }

    @Test
    public void clockSetBackIsDue() {
        assertTrue(ServerFirst.due(1_000_000L, 5));
    }
}
