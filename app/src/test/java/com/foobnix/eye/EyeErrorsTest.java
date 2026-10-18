package com.foobnix.eye;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EyeErrorsTest {

    @Test
    public void causesOutermostFirst() {
        final Throwable t = new ExceptionInInitializerError(new IllegalStateException("No logging platforms found"));
        assertEquals("ExceptionInInitializerError ← IllegalStateException: No logging platforms found", EyeErrors.describe(t));
    }

    @Test
    public void plainMessage() {
        assertEquals("UnsatisfiedLinkError: no lib", EyeErrors.describe(new UnsatisfiedLinkError("no lib")));
    }

    @Test
    public void stopsAfterFourCauses() {
        Throwable t = new RuntimeException("0");
        for (int i = 1; i < 8; i++) {
            t = new RuntimeException(String.valueOf(i), t);
        }
        assertEquals(4, EyeErrors.describe(t).split(" ← ").length);
    }
}
