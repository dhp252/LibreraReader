package com.foobnix.ai;

import java.io.IOException;

/**
 * When a long AI task asks again (design-2026-10-25.md): a request that failed for a passing
 * reason (the network, a busy server) is asked twice more inside the task; then the task itself is
 * run again by WorkManager, a few times; then it stops and the reader is asked to continue it.
 */
public final class AiRetry {

    /** Waits before asking again inside a task: after the first failure, after the second. */
    static final long[] IN_TASK_WAITS = {5000, 15000};
    /** Runs of a task before it stops and asks to be continued. */
    public static final int TASK_ATTEMPTS = 5;

    private AiRetry() {
    }

    /** Whether the failure may pass: the network or a busy server, not a refused key or request. */
    public static boolean retryable(Throwable e) {
        if (e instanceof AiClient.AiException) {
            return ((AiClient.AiException) e).retryable;
        }
        return e instanceof IOException;
    }

    /**
     * @param failures how often this request failed so far (1: the first failure)
     * @return milliseconds to wait before asking again, or -1 to give up inside the task
     */
    public static long inTaskWait(Throwable e, int failures) {
        return retryable(e) && failures >= 1 && failures <= IN_TASK_WAITS.length ? IN_TASK_WAITS[failures - 1] : -1;
    }

    /** @param attempt the task's run so far, 0 for its first run */
    public static boolean retryTask(boolean retryable, int attempt) {
        return retryable && attempt + 1 < TASK_ATTEMPTS;
    }
}
