package com.foobnix.assist.useit;

import android.app.Activity;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;

/**
 * "Use it" (Assist.USE_IT, canvas "Use it this month" and "Experiment check-in"): one idea of a
 * chapter becomes a 1, 2 or 4 week experiment in the reader's own work, checked in when it is due.
 * These are the feature's three ways in, each from one line in an existing class; all of them do
 * nothing while the switch is off.
 */
public final class UseIt {

    private UseIt() {
    }

    /**
     * The ✦ menu's "Use it" row: the book's experiments when it has any (with "New experiment"),
     * else straight to a new one for the chapter at the reader's page.
     */
    public static void open(final Activity a, final DocumentController dc) {
        if (!Assist.USE_IT.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final String bookName = AiHighlights.bookName(dc.getCurrentBook().getPath());
        UseStore.IO.execute(() -> {
            final List<UseExperiment> list = UseStore.list(bookName, UseStore.today());
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a)) {
                    return;
                }
                if (list.isEmpty()) {
                    UseItScreen.show(a, dc, null);
                } else {
                    UseListScreen.show(a, dc, list);
                }
            });
        });
    }

    /** The reader shows a page (DocumentController.saveCurrentPage): a due experiment's card, once per open. */
    public static void pageShown(DocumentController dc) {
        UseDue.pageShown(dc);
    }

    /** The main screen comes to the front (MainTabs2.onResume): the "to check in" banner. */
    public static void libraryResumed(Activity a) {
        UseDue.libraryResumed(a);
    }
}
