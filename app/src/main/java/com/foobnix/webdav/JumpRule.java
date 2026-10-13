package com.foobnix.webdav;

/**
 * Whether an open book follows the position another device synced
 * (.agents/planning/event-sync-design.md, E3, E9, E10). Pure, JVM-tested.
 */
final class JumpRule {

    static final int STAY = 0;
    /** Go to the other device's page, with a note to undo it. */
    static final int JUMP = 1;
    /** Only say where the other device is: something here must not be interrupted. */
    static final int OFFER = 2;

    private JumpRule() {
    }

    /**
     * @param localTime  when this device last moved in the book
     * @param localPage  the page shown (1-based)
     * @param remoteTime when the synced record was last moved, on whichever device
     * @param remotePage the page of the synced record in this device's layout (1-based)
     * @param busy       reading aloud, scrolling by itself or drawing: no page change under it
     */
    static int decide(long localTime, int localPage, long remoteTime, int remotePage, boolean busy) {
        // a page turned here after the other device's is the newer read: it stays and goes up
        if (remoteTime <= localTime || remotePage <= 0 || remotePage == localPage) {
            return STAY;
        }
        return busy ? OFFER : JUMP;
    }
}
