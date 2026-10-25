package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;

import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * The AI button at the top of an open book: Study, the book map, summaries, and key passages one
 * popup down. Settings for every book are in AI settings (design-2026-10-25-ai-menu.md).
 */
public final class AiMenu {

    private AiMenu() {
    }

    public static void show(final Activity a, final View anchor, final DocumentController dc) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final AiStore.Book data = AiHighlights.get().data();
        final BookChapters.Range chapter = AiJobs.knownChapter(target);
        final boolean chapterSaved = chapter != null && data.summary(chapter.key) != null;
        final boolean bookSaved = data.summary(AiStore.BOOK) != null;

        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        // study mode (F-009): its panel once the book is studied
        final com.foobnix.study.StudySession study = com.foobnix.study.StudySession.of(dc);
        final boolean studying = study != null && study.book().on && study.book().hasPreview();
        // ✓ once the book has its preview
        final boolean previewed = study != null && study.book().hasPreview();
        final boolean previewBusy = AiBusyButton.isBusy(dc, AiTask.STUDY_PREVIEW);
        menu.getMenu().add(mark(a, studying ? R.string.study_panel : R.string.study_this_book, previewed, previewBusy))
                .setIcon(R.drawable.ic_study)
                .setOnMenuItemClickListener(item -> {
                    if (studying && !previewBusy) {
                        com.foobnix.study.StudyPanel.show(a, dc);
                    } else {
                        // a preview going on shows its progress
                        com.foobnix.study.StudyStart.begin(a, dc, false);
                    }
                    return true;
                });
        // the book map (F-013), for every book
        menu.getMenu().add(mark(a, R.string.book_map, com.foobnix.bookmap.MapStore.get().has(target.name),
                        AiBusyButton.isBusy(dc, AiTask.MAP)))
                .setIcon(R.drawable.ic_book_map)
                .setOnMenuItemClickListener(item -> {
                    com.foobnix.bookmap.BookMapView.show(a, dc);
                    return true;
                });
        menu.getMenu().add(saved(a, R.string.ai_summary_chapter, chapterSaved))
                .setIcon(R.drawable.glyphicons_588_book_open_text)
                .setOnMenuItemClickListener(item -> {
                    AiSummaryDialog.show(a, dc, false);
                    return true;
                });
        menu.getMenu().add(mark(a, R.string.ai_summary_book, bookSaved, AiBusyButton.isBusy(dc, AiTask.BOOK_SUMMARY)))
                .setIcon(R.drawable.glyphicons_589_book_open)
                .setOnMenuItemClickListener(item -> {
                    AiSummaryDialog.show(a, dc, true);
                    return true;
                });
        // everything about the book's key passages is one popup down (design-2026-10-25-ai-menu.md)
        final boolean highlightsBusy = AiHighlightDialog.isBusy(dc);
        menu.getMenu().add(mark(a, R.string.ai_key_passages, data.allMarked(), highlightsBusy) + "  ›")
                .setIcon(R.drawable.ic_ai_sparkle)
                .setOnMenuItemClickListener(item -> {
                    showKeyPassages(a, anchor, dc, target, data, chapter, highlightsBusy);
                    return true;
                });
        menu.show();
    }

    /** One switch for every book on this device. */
    private static void showHighlights(Activity a, boolean show) {
        final AiSettings settings = AiSettings.load(a);
        settings.highlightsVisible = show;
        settings.save(a);
        AiHighlights.get().setVisible(show);
    }

    /** Which layers of the AI highlights are drawn, for every book on this device. */
    public static void showLayers(Activity a, boolean bars, boolean sentences) {
        final AiSettings settings = AiSettings.load(a);
        settings.barsVisible = bars;
        settings.sentencesVisible = sentences;
        settings.save(a);
        AiHighlights.get().setLayers(bars, sentences);
    }

    private static String saved(Activity a, int text, boolean saved) {
        return saved ? a.getString(text) + "  ✓" : a.getString(text);
    }

    /** ✓ when done, "running…" while its task is queued or runs (a tap shows its progress). */
    private static String mark(Activity a, int text, boolean saved, boolean busy) {
        return busy ? a.getString(R.string.ai_task_running, a.getString(text)) : saved(a, text, saved);
    }

    /** Before an AI task already done is done again: it costs a new request. */
    public static void confirmRedo(Activity a, int message, Runnable redo) {
        new AlertDialog.Builder(a)
                .setMessage(message)
                .setPositiveButton(R.string.ai_redo, (d, w) -> redo.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Key passages: their ideas, running, showing and removing them, the goal they follow. */
    private static void showKeyPassages(Activity a, View anchor, DocumentController dc, AiJobs.Target target,
                                        AiStore.Book data, BookChapters.Range chapter, boolean busy) {
        final boolean pdfDrawable = target.pdf && AppsConfig.isPDF_DRAW_ENABLE() && !dc.isPasswordProtected();
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        for (AiMenuRows.Row row : AiMenuRows.keyPassages(busy, !data.highlights.isEmpty(), pdfDrawable)) {
            switch (row) {
                case IDEAS:
                    menu.getMenu(R.drawable.glyphicons_115_list, R.string.ai_ideas_chapter, () -> AiIdeasDialog.showHere(a, dc));
                    break;
                case HIGHLIGHT_CHAPTER:
                    // ✓ only for a chapter already known: the menu never reads the book
                    menu.getMenu().add(saved(a, R.string.ai_highlight_chapter, chapter != null && data.isDone(chapter.key)))
                            .setIcon(R.drawable.glyphicons_588_book_open_text)
                            .setOnMenuItemClickListener(item -> {
                                AiHighlightDialog.start(a, dc, false);
                                return true;
                            });
                    break;
                case HIGHLIGHT_BOOK:
                    menu.getMenu().add(saved(a, R.string.ai_highlight_book, data.allMarked()))
                            .setIcon(R.drawable.glyphicons_589_book_open)
                            .setOnMenuItemClickListener(item -> {
                                AiHighlightDialog.start(a, dc, true);
                                return true;
                            });
                    break;
                case PROGRESS:
                    menu.getMenu().add(a.getString(R.string.ai_task_running, a.getString(R.string.ai_show_progress)))
                            .setIcon(R.drawable.ic_ai_sparkle)
                            .setOnMenuItemClickListener(item -> {
                                AiHighlightDialog.showRunning(a, dc);
                                return true;
                            });
                    break;
                case SHOW:
                    menu.getMenu().setIcon(R.drawable.glyphicons_25_eye)
                            .addCheckbox(a.getString(R.string.ai_show_highlights), AiHighlights.get().isVisible(),
                                    (button, checked) -> showHighlights(a, checked))
                            .setOnMenuItemClickListener(item -> {
                                // a tap on the row beside the box switches too
                                showHighlights(a, !AiHighlights.get().isVisible());
                                return true;
                            });
                    break;
                case GOAL:
                    menu.getMenu(R.drawable.glyphicons_66_tag, R.string.ai_goal, () -> {
                        final AiHighlights highlights = AiHighlights.get();
                        final String current = target.name.equals(highlights.getBook()) ? highlights.data().goal : null;
                        AiLegend.askGoal(a, target.name, current, () -> com.foobnix.study.StudyStart.goalChanged(a, dc));
                    });
                    break;
                case REMOVE_CHAPTER:
                    menu.getMenu(R.drawable.ic_annot_eraser, R.string.ai_remove_chapter, () -> confirmRemove(a, dc, false));
                    break;
                case REMOVE_BOOK:
                    menu.getMenu(R.drawable.ic_annot_eraser, R.string.ai_remove_book, () -> confirmRemove(a, dc, true));
                    break;
                case SAVE_INTO_PDF:
                    menu.getMenu(R.drawable.glyphicons_695_text_background, R.string.ai_save_into_pdf, () ->
                            new AiJobs.SaveIntoPdfJob(a, dc).start());
                    break;
            }
        }
        menu.show();
    }

    /** One row removes them, so it asks: they cost requests to get back. */
    private static void confirmRemove(Activity a, DocumentController dc, boolean wholeBook) {
        new AlertDialog.Builder(a)
                .setMessage(wholeBook ? R.string.ai_remove_book_confirm : R.string.ai_remove_chapter_confirm)
                .setPositiveButton(R.string.ai_remove, (d, w) -> new AiJobs.RemoveJob(a, AiJobs.Target.of(dc), wholeBook).start())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
