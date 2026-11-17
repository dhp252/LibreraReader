package com.foobnix.assist.review;

import android.app.Activity;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.recall.RecallChapter;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;
import java.util.Map;

/**
 * Spaced review (Assist.REVIEW; canvas "Today's review", "Spaced review", "Review answered"):
 * the main ideas of chapters the reader worked on come back as scenario questions, in the app
 * only. Nothing is ever scheduled: no notification, alarm, background work or calendar entry;
 * what is due shows when the main screen or a book is opened. These are the feature's ways in,
 * each from one or two lines in an existing class; all of them do nothing while the switch is off.
 */
public final class Review {

    private static boolean adding;

    private Review() {
    }

    /** The ✦ menu's rows (AiMenu, only while the switch is on). */
    public static void menu(final Activity a, final DocumentController dc, MyPopupMenu menu) {
        if (!Assist.REVIEW.isOn() || a == null || dc == null || menu == null) {
            return;
        }
        menu.getMenu(R.drawable.canvas_t24_ic_review, R.string.canvas_t24_today, () -> today(a, dc));
        menu.getMenu(R.drawable.canvas_t24_ic_add, R.string.canvas_t24_add_chapter, () -> addChapter(a, dc));
        menu.getMenu(R.drawable.canvas_t25_ic_link, R.string.canvas_t25_connections, () -> ReviewLinksScreen.show(a, dc));
    }

    /** Today's review, over the open book ({@code dc}) or the main screen (null). */
    public static void today(Activity a, DocumentController dc) {
        ReviewTodayScreen.show(a, dc, dc == null ? () -> ReviewDue.libraryResumed(a) : null);
    }

    /** The main screen comes to the front (MainTabs2.onResume): the "6 ideas to review" banner. */
    public static void libraryResumed(Activity a) {
        ReviewDue.libraryResumed(a);
    }

    /** The reader shows a page (DocumentController.saveCurrentPage): once per open, what is due. */
    public static void pageShown(DocumentController dc) {
        ReviewDue.pageShown(dc);
    }

    /**
     * "Quiz me later" on a note of "The book, updated": the action that puts the note's idea in
     * the queue, due tomorrow; null while the switch is off (then the sheet has no such button).
     */
    public static Runnable quizLater(final Activity a, final DocumentController dc, final String chapterKey,
                                     final String chapterTitle, final String idea, final String quote, final int page) {
        if (!Assist.REVIEW.isOn() || a == null || dc == null || dc.getCurrentBook() == null || chapterKey == null
                || idea == null || idea.trim().isEmpty()) {
            return null;
        }
        final String book = ReviewStore.name(dc.getCurrentBook().getPath());
        final ReviewStore.Meta meta = meta(dc);
        return () -> ReviewStore.IO.execute(() -> {
            boolean added = false;
            try {
                final ReviewItem item = ReviewSources.fromNote(chapterKey, chapterTitle, idea, quote, page,
                        ReviewStore.items(book), ReviewStore.today());
                if (item != null) {
                    ReviewStore.save(book, item, meta);
                    added = true;
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final int text = added ? R.string.canvas_t24_quiz_added : R.string.canvas_t24_quiz_already;
            a.runOnUiThread(() -> {
                if (!Apps.isDestroyedActivity(a)) {
                    Toast.makeText(a, text, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Told, on the main thread, how many ideas {@link #addSpots} added and how many were in the queue already. */
    public interface SpotsAdded {
        void done(int added, int already);
    }

    /**
     * The ideas of the spots "The book reads you back" marked in a chapter enter the queue, due
     * tomorrow ({@link ReviewSources#fromSpots}). Nothing is sent to the AI and nothing is shown
     * here: the caller says what happened. False while the switch is off (then nothing is done).
     */
    public static boolean addSpots(final Activity a, final DocumentController dc, final String chapterKey,
                                   final String chapterTitle, final List<Recall.Idea> ideas, final SpotsAdded then) {
        if (!Assist.REVIEW.isOn() || a == null || dc == null || dc.getCurrentBook() == null || chapterKey == null
                || ideas == null || ideas.isEmpty()) {
            return false;
        }
        final String book = ReviewStore.name(dc.getCurrentBook().getPath());
        final ReviewStore.Meta meta = meta(dc);
        ReviewStore.IO.execute(() -> {
            int added = 0;
            try {
                final List<ReviewItem> items = ReviewSources.fromSpots(chapterKey, chapterTitle, ideas,
                        ReviewStore.items(book), ReviewStore.today());
                for (ReviewItem item : items) {
                    ReviewStore.save(book, item, meta);
                    added++;
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final int n = added;
            a.runOnUiThread(() -> {
                if (then != null && !Apps.isDestroyedActivity(a)) {
                    then.done(n, Math.max(0, ideas.size() - n));
                }
            });
        });
        return true;
    }

    /**
     * "Review this chapter's ideas": the key-passage ideas of the chapter at the reader's page
     * enter the queue, due today; then Today's review opens. Nothing is sent to the AI; a chapter
     * without key passages only says so.
     */
    static void addChapter(final Activity a, final DocumentController dc) {
        if (!Assist.REVIEW.isOn() || dc.getCurrentBook() == null || adding) {
            return;
        }
        adding = true;
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final String book = ReviewStore.name(t.path);
        final ReviewStore.Meta meta = meta(dc);
        ReviewStore.IO.execute(() -> {
            int added = -1;
            try {
                final BookChapters.Range range = AiJobs.chapterAt(t);
                final List<Recall.Idea> ideas = range == null ? null : RecallChapter.fromPassages(t.name, range.key);
                if (ideas != null && !ideas.isEmpty()) {
                    final String title = range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                            : a.getString(R.string.ai_pages, range.from + 1, range.to);
                    final Map<String, ReviewItem> existing = ReviewStore.items(book);
                    final List<ReviewItem> items = ReviewSources.fromPassages(range.key, title, ideas, existing,
                            ReviewStore.today());
                    for (ReviewItem item : items) {
                        ReviewStore.save(book, item, meta);
                    }
                    added = items.size();
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final int n = added;
            a.runOnUiThread(() -> {
                adding = false;
                if (Apps.isDestroyedActivity(a) || !Assist.REVIEW.isOn()) {
                    return;
                }
                if (n < 0) {
                    Toast.makeText(a, R.string.canvas_t24_no_passages, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(a, n == 0 ? a.getString(R.string.canvas_t24_already_in)
                        : a.getString(R.string.canvas_t24_added, ReviewWords.ideas(a, n)), Toast.LENGTH_LONG).show();
                today(a, dc);
            });
        });
    }

    /** The open book's path, title and author for the library's index; null without an open book. */
    static ReviewStore.Meta meta(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null) {
                return null;
            }
            final String path = dc.getCurrentBook().getPath();
            final com.foobnix.dao2.FileMeta m = dc.getBookFileMeta();
            final String title = dc.getTitle();
            return new ReviewStore.Meta(path, title == null || title.trim().isEmpty() ? new java.io.File(path).getName()
                    : title.trim(), m == null || m.getAuthor() == null ? "" : m.getAuthor().trim());
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }
}
