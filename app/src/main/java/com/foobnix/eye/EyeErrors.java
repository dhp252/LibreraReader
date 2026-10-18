package com.foobnix.eye;

/** How Eye mode tells the reader why it cannot start. */
public final class EyeErrors {

    private EyeErrors() {
    }

    /**
     * The throwable and its causes, outermost first ("ExceptionInInitializerError ←
     * IllegalStateException: No logging platforms found"): the outermost one alone often names
     * only the class that failed, not why.
     */
    public static String describe(Throwable t) {
        final StringBuilder b = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 4; depth++) {
            if (b.length() > 0) {
                b.append(" ← ");
            }
            b.append(c.getClass().getSimpleName());
            if (c.getMessage() != null) {
                b.append(": ").append(c.getMessage());
            }
            c = c.getCause() == c ? null : c.getCause();
        }
        return b.toString();
    }
}
