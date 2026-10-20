package com.foobnix.ai;

import android.app.Activity;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingTracker;

import java.util.Collections;

/**
 * Goes to the page holding a stored quote. Fixed layouts (PDF, DjVu) use the page it was found on;
 * a reflowing book may have a new layout since, so the quote is looked for around that page.
 */
public final class AiJump {

    /** Pages either side of the hint a quote is looked for in when no range is given. */
    static final int AROUND = 15;

    private AiJump() {
    }

    /**
     * @param page the 0-based page the quote was found on when stored (a hint), or -1
     * @param lo   first 0-based page to look in, or -1 for {@link #AROUND} the hint
     * @param hi   last 0-based page to look in, or -1 for {@link #AROUND} the hint
     */
    public static void toQuote(DocumentController dc, String quote, int page, int lo, int hi) {
        final String key = quote == null ? null : QuoteMatcher.key(quote);
        if (key == null || !dc.isTextFormat()) {
            goToPage(dc, page);
            return;
        }
        final Activity a = dc.getActivity();
        final AiJobs.Target t = AiJobs.Target.of(dc);
        new Thread(() -> {
            int found = -1;
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    found = find(text, key, page, lo, hi);
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final int go = found >= 0 ? found : page;
            if (a != null && !Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> goToPage(dc, go));
            }
        }, "@T AI quote").start();
    }

    /** The page holding {@code key}, nearest to the hint first, then outwards; -1 when none. Off the main thread. */
    static int find(BookText text, String key, int page, int lo, int hi) {
        final int pages = text.pageCount();
        final int hint = Math.max(0, Math.min(pages - 1, page));
        final int from = Math.max(0, lo >= 0 ? lo : hint - AROUND);
        final int to = Math.min(pages - 1, hi >= 0 ? hi : hint + AROUND);
        for (int d = 0; d <= to - from; d++) {
            for (int p : d == 0 ? new int[]{hint} : new int[]{hint + d, hint - d}) {
                if (p >= from && p <= to && !QuoteMatcher.onPage(new QuoteMatcher.Words(BookText.words(text.text(p))),
                        Collections.singletonList(key)).isEmpty()) {
                    return p;
                }
            }
        }
        return -1;
    }

    private static void goToPage(DocumentController dc, int bookPage) {
        if (dc != null && bookPage >= 0) {
            dc.onGoToPage(ReadingTracker.viewPage(dc, bookPage + 1));
        }
    }
}
