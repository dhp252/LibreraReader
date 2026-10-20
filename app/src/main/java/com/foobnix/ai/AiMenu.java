package com.foobnix.ai;

import android.app.Activity;
import android.view.View;

import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

/** The AI button at the top of an open book: summaries, key passages, and their settings. */
public final class AiMenu {

    private interface Scope {
        void chosen(boolean wholeBook);
    }

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
        menu.getMenu(R.drawable.ic_study, studying ? R.string.study_panel : R.string.study_this_book, () -> {
            if (studying) {
                com.foobnix.study.StudyPanel.show(a, dc);
            } else {
                com.foobnix.study.StudyStart.begin(a, dc, false);
            }
        });
        menu.getMenu().add(saved(a, R.string.ai_summary_chapter, chapterSaved))
                .setIcon(R.drawable.glyphicons_588_book_open_text)
                .setOnMenuItemClickListener(item -> {
                    AiSummaryDialog.show(a, dc, false);
                    return true;
                });
        menu.getMenu().add(saved(a, R.string.ai_summary_book, bookSaved))
                .setIcon(R.drawable.glyphicons_589_book_open)
                .setOnMenuItemClickListener(item -> {
                    AiSummaryDialog.show(a, dc, true);
                    return true;
                });
        menu.getMenu(R.drawable.ic_ai_sparkle, R.string.ai_highlight, () -> {
            final AiJobs.HighlightJob running = AiJobs.runningHighlights();
            if (running != null) {
                AiHighlightDialog.showRunning(a, running);
            } else {
                chooseScope(a, anchor, wholeBook -> AiHighlightDialog.start(a, dc, wholeBook));
            }
        });
        menu.getMenu().setIcon(R.drawable.glyphicons_25_eye)
                .addCheckbox(a.getString(R.string.ai_show_highlights), AiHighlights.get().isVisible(),
                        (button, checked) -> showHighlights(a, checked))
                .setOnMenuItemClickListener(item -> {
                    // a tap on the row beside the box switches too
                    showHighlights(a, !AiHighlights.get().isVisible());
                    return true;
                });
        menu.getMenu().setIcon(R.drawable.glyphicons_25_eye)
                .addCheckbox(a.getString(R.string.ai_show_bars), AiHighlights.get().isBarsVisible(),
                        (button, checked) -> showLayers(a, checked, AiHighlights.get().isSentencesVisible()))
                .setOnMenuItemClickListener(item -> {
                    showLayers(a, !AiHighlights.get().isBarsVisible(), AiHighlights.get().isSentencesVisible());
                    return true;
                });
        menu.getMenu().setIcon(R.drawable.glyphicons_25_eye)
                .addCheckbox(a.getString(R.string.ai_show_sentences), AiHighlights.get().isSentencesVisible(),
                        (button, checked) -> showLayers(a, AiHighlights.get().isBarsVisible(), checked))
                .setOnMenuItemClickListener(item -> {
                    showLayers(a, AiHighlights.get().isBarsVisible(), !AiHighlights.get().isSentencesVisible());
                    return true;
                });
        menu.getMenu(R.drawable.glyphicons_588_book_open_text, R.string.ai_ideas_chapter, () -> AiIdeasDialog.showHere(a, dc));
        menu.getMenu(R.drawable.glyphicons_92_contrast, R.string.ai_colors, () -> AiLegend.show(a));
        menu.getMenu(R.drawable.glyphicons_66_tag, R.string.ai_goal, () -> {
            final AiHighlights highlights = AiHighlights.get();
            final String current = target.name.equals(highlights.getBook()) ? highlights.data().goal : null;
            AiLegend.askGoal(a, target.name, current, () -> com.foobnix.study.StudyStart.goalChanged(a, dc));
        });
        menu.getMenu(R.drawable.ic_annot_eraser, R.string.ai_remove_highlights, () ->
                chooseScope(a, anchor, wholeBook -> new AiJobs.RemoveJob(a, AiJobs.Target.of(dc), wholeBook).start()));
        if (target.pdf && AppsConfig.isPDF_DRAW_ENABLE() && !dc.isPasswordProtected()) {
            menu.getMenu(R.drawable.glyphicons_695_text_background, R.string.ai_save_into_pdf, () ->
                    new AiJobs.SaveIntoPdfJob(a, dc).start());
        }
        menu.getMenu(R.drawable.glyphicons_137_cogwheel, R.string.ai_settings, () -> AiSettingsDialog.show(a, null));
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

    private static void chooseScope(Activity a, View anchor, Scope then) {
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        menu.getMenu(R.drawable.glyphicons_588_book_open_text, R.string.ai_this_chapter, () -> then.chosen(false));
        menu.getMenu(R.drawable.glyphicons_589_book_open, R.string.ai_whole_book, () -> then.chosen(true));
        menu.show();
    }
}
