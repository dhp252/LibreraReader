package com.foobnix.assist.recap;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.Mark;
import com.foobnix.annotation.MarksStore;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Re-entry recap (canvas "Recap · back after 2 days / 3 weeks", switch {@link Assist#RECAP}):
 * opening a book 2+ days after it was last read (any device: the same time Study's welcome back
 * uses, {@code AppBook.t} read before the page is saved) shows a recap sheet. 2–6 days: the
 * 20-second recap; 7+ days: the longer one with its 30 sec / 2 min / 5 min choice.
 * <p>
 * One book at a time, like the study session: created on the first page of a book, kept through a
 * rotation, gone when its reader finishes. At most one request per opening ({@link RecapJob}).
 */
public final class Recap {

    /** The book may jump to its saved (or another device's) page right after it opens. */
    private static final long SETTLE_MS = 1200;

    private static Recap current;
    private static boolean watching;

    final String path;
    final String name;
    DocumentController dc;
    Activity owner;
    private final long lastRead;
    /** {@link RecapLogic#SHORT}, {@link RecapLogic#LONG}, or null: no recap this time. */
    final String bucket;
    final int days;

    // what the sheet shows
    /** The longer recap is shown (always for {@link RecapLogic#LONG}; after "Longer recap" for a short one). */
    boolean longView;
    int level;
    RecapPrompts.Recap recap;
    boolean loading;
    boolean needSettings;
    String error;
    RecapLogic.MarkInfo mark;
    String chapterTitle = "";
    /** The reader's page (0-based) and page count when the recap was made. */
    int page = -1;
    int pages;
    boolean warmupOpen;
    /** Study's welcome back handed over: its "look back" (the map), shown as "Open map". */
    Runnable studyMap;

    private boolean settled;
    /** The sheet was opened (it comes back after a rotation until the reader closes it). */
    private boolean shown;
    /** Nothing to recap (the first page): Study's own welcome back may show. */
    private boolean skipped;
    /** The reader closed the sheet: it does not come again for this opening. */
    private boolean closed;
    private boolean requested;
    private long today;
    private RecapJob job;
    private RecapSheet sheet;

    private Recap(DocumentController dc, String path, long lastRead) {
        this.dc = dc;
        this.owner = dc.getActivity();
        this.path = path;
        this.name = AiHighlights.bookName(path);
        this.lastRead = lastRead;
        final long now = System.currentTimeMillis();
        this.bucket = RecapLogic.bucket(lastRead, now);
        this.days = RecapLogic.daysAway(lastRead, now);
        this.longView = RecapLogic.LONG.equals(bucket);
        this.level = RecapLogic.level(days);
    }

    // ------------------------------------------------------------------ hooks

    /**
     * The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on).
     *
     * @param lastRead when the book was read last before this page, on any device
     */
    public static void pageShown(DocumentController dc, long lastRead) {
        try {
            if (!Assist.RECAP.isOn() || dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc, lastRead));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            final Recap r = current;
            if (r != null && r.path.equals(path)) {
                if (r.dc != dc) {
                    r.moved(dc);
                }
                return;
            }
            if (r != null) {
                r.close();
            }
            current = new Recap(dc, path, lastRead);
            current.begin();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * Study's welcome back (F-009) is about to show for this book: the recap takes its place, with
     * an "Open map" button running {@code openMap}, so the reader gets one prompt, not two.
     *
     * @return true when the recap took it (Study then shows no welcome card of its own)
     */
    public static boolean takeWelcome(String path, Runnable openMap) {
        try {
            final Recap r = current;
            if (!Assist.RECAP.isOn() || r == null || path == null || !r.path.equals(path) || r.bucket == null || r.skipped) {
                return false;
            }
            r.studyMap = openMap;
            if (r.sheet != null && !r.closed) {
                r.sheet.render();
            }
            return true;
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }
    }

    // ------------------------------------------------------------------ inside

    private void begin() {
        if (bucket == null) {
            return;
        }
        watch(owner.getApplication());
        owner.getWindow().getDecorView().postDelayed(this::settle, SETTLE_MS);
    }

    /** The book is on its page: what to show is found off the main thread, then the sheet opens. */
    private void settle() {
        if (current != this || settled || closed || Apps.isDestroyedActivity(owner)) {
            return;
        }
        settled = true;
        final AiJobs.Target target = AiJobs.Target.of(dc);
        page = target.page;
        pages = dc.getPageCount();
        if (page <= 0 && studyMap == null) {
            // nothing read before this page: nothing to recap
            skipped = true;
            return;
        }
        final int bookPage = ReadingTracker.realPages(dc, page + 1)[0] - 1;
        final long now = System.currentTimeMillis();
        today = RecapLogic.day(now, TimeZone.getDefault().getOffset(now));
        final android.content.Context app = owner.getApplicationContext();
        new Thread(() -> {
            RecapLogic.MarkInfo m = null;
            String title = "";
            boolean ready = false;
            try {
                m = RecapLogic.pickMark(marks(name), bookPage);
                final BookChapters.Range at = AiJobs.chapterAt(target);
                title = at == null || at.title == null ? "" : at.title.trim();
                ready = AiSettings.load(app).isReady();
            } catch (Exception e) {
                LOG.e(e);
            }
            final RecapLogic.MarkInfo mark = m;
            final String chapter = title;
            final boolean aiReady = ready;
            main(() -> {
                if (current != this || closed) {
                    return;
                }
                this.mark = mark;
                this.chapterTitle = chapter;
                if (page > 0) {
                    if (aiReady) {
                        request(target);
                    } else {
                        needSettings = true;
                    }
                }
                show();
            });
        }, "@T Recap").start();
    }

    /** The reader's marks before the page, as far as the recap needs them. */
    private static List<RecapLogic.MarkInfo> marks(String name) {
        final List<RecapLogic.MarkInfo> out = new ArrayList<>();
        for (Mark m : MarksStore.get().load(name).values()) {
            if (!m.isInk()) {
                out.add(new RecapLogic.MarkInfo(m.firstPage(), Math.max(m.created, m.time), m.quote, m.note));
            }
        }
        return out;
    }

    /** The one request of this opening (a saved recap of today is used instead; see {@link RecapJob}). */
    private void request(AiJobs.Target target) {
        if (requested) {
            return;
        }
        requested = true;
        loading = true;
        error = null;
        needSettings = false;
        job = new RecapJob(owner.getApplicationContext(), target, bucket, days, pages, today, new RecapJob.Screen() {
            @Override
            public void onRecap(RecapPrompts.Recap r) {
                if (current != Recap.this) {
                    return;
                }
                loading = false;
                recap = r;
                render();
            }

            @Override
            public void onError(String message) {
                if (current != Recap.this) {
                    return;
                }
                loading = false;
                error = message;
                render();
            }

            @Override
            public void onNeedSettings() {
                if (current != Recap.this) {
                    return;
                }
                loading = false;
                needSettings = true;
                render();
            }
        });
        job.start();
    }

    /** Asked by a tap (Try again, or AI settings saved): one more request. */
    void askAgain() {
        if (dc == null || Apps.isDestroyedActivity(owner)) {
            return;
        }
        requested = false;
        request(AiJobs.Target.of(dc));
        render();
    }

    void openAiSettings() {
        com.foobnix.ai.AiSettingsDialog.show(owner, () -> {
            if (current == this && !closed && AiSettings.load(owner).isReady()) {
                askAgain();
            }
        });
    }

    private void show() {
        if (closed || Apps.isDestroyedActivity(owner)) {
            return;
        }
        shown = true;
        sheet = new RecapSheet(owner, this);
        sheet.show();
    }

    private void render() {
        if (sheet != null && !closed) {
            sheet.render();
        }
    }

    /** A rotation or a new reader for the same book: the sheet moves to it. */
    private void moved(DocumentController dc) {
        this.dc = dc;
        this.owner = dc.getActivity();
        if (sheet != null) {
            sheet.dismissQuietly();
            sheet = null;
        }
        if (shown && !closed) {
            show();
        } else if (!settled && !closed && bucket != null) {
            owner.getWindow().getDecorView().postDelayed(this::settle, SETTLE_MS);
        }
    }

    /** The sheet was closed by the reader (back, outside, Continue, Open map). */
    void sheetClosed() {
        closed = true;
        sheet = null;
        if (job != null && loading) {
            // no longer wanted
            job.cancel();
        }
    }

    /** Continue: reading goes on at the page the recap was made for. */
    void continueReading() {
        try {
            if (dc != null && page >= 0 && dc.getCurentPageFirst1() - 1 != page) {
                dc.onGoToPage(page + 1);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    void openMap() {
        final Runnable map = studyMap;
        if (map != null) {
            map.run();
        }
    }

    private void close() {
        closed = true;
        if (job != null) {
            job.cancel();
        }
        if (sheet != null) {
            sheet.dismissQuietly();
            sheet = null;
        }
    }

    private static void main(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    /** Takes the sheet away with its reader; a finished reader ends the opening. */
    private static void watch(Application app) {
        if (watching || app == null) {
            return;
        }
        watching = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityDestroyed(Activity a) {
                final Recap r = current;
                if (r == null || r.owner != a) {
                    return;
                }
                if (r.sheet != null) {
                    r.sheet.dismissQuietly();
                    r.sheet = null;
                }
                if (a.isFinishing()) {
                    r.close();
                    current = null;
                }
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityStarted(Activity a) {
            }

            @Override
            public void onActivityResumed(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }
        });
    }
}
