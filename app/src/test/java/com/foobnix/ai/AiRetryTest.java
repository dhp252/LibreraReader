package com.foobnix.ai;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiRetryTest {

    private static final AiClient.AiException NETWORK = new AiClient.AiException("no network", false, true);
    private static final AiClient.AiException BAD_KEY = new AiClient.AiException("401: bad key", false, false);
    private static final AiClient.AiException REFUSED = new AiClient.AiException("declined", true, false);

    @Test
    public void passingFailuresAreAskedTwiceMoreInsideTheTask() {
        assertEquals(5000, AiRetry.inTaskWait(NETWORK, 1));
        assertEquals(15000, AiRetry.inTaskWait(NETWORK, 2));
        assertEquals(-1, AiRetry.inTaskWait(NETWORK, 3));
        assertEquals("a plain IO failure is the network too", 5000, AiRetry.inTaskWait(new SocketTimeoutException(), 1));
        assertEquals(-1, AiRetry.inTaskWait(BAD_KEY, 1));
        assertEquals(-1, AiRetry.inTaskWait(REFUSED, 1));
        assertEquals(-1, AiRetry.inTaskWait(new IllegalStateException(), 1));
    }

    @Test
    public void theTaskRunsAgainUpToFiveTimes() {
        assertTrue(AiRetry.retryable(NETWORK));
        assertTrue(AiRetry.retryable(new IOException()));
        assertFalse(AiRetry.retryable(BAD_KEY));
        assertFalse(AiRetry.retryable(new RuntimeException()));
        assertTrue(AiRetry.retryTask(true, 0));
        assertTrue(AiRetry.retryTask(true, 3));
        assertFalse(AiRetry.retryTask(true, 4));
        assertFalse(AiRetry.retryTask(false, 0));
    }

    @Test
    public void busyServersAndCutStreamsMayPassRefusalsDoNot() throws Exception {
        assertTrue(AiClient.retryableStatus(429));
        assertTrue(AiClient.retryableStatus(408));
        assertTrue(AiClient.retryableStatus(500));
        assertTrue(AiClient.retryableStatus(529));
        assertFalse(AiClient.retryableStatus(400));
        assertFalse(AiClient.retryableStatus(401));
        assertFalse(AiClient.retryableStatus(404));
        assertTrue(AiClient.retryableError("overloaded_error"));
        assertTrue(AiClient.retryableError("rate_limit_error"));
        assertFalse(AiClient.retryableError("invalid_request_error"));
        assertFalse(AiClient.retryableError(null));
    }
}
