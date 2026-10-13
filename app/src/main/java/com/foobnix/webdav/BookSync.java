package com.foobnix.webdav;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.tts.TTSEngine;

import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.SharedBooks;

import java.lang.ref.WeakReference;
import java.util.Set;

/**
 * The open book and the sync (.agents/planning/event-sync-design.md): a reader on screen has its
 * book's files pulled ({@link SyncEvents#bookShown}), and what came from another device is shown
 * at once: the newer position (with a note to undo it), the AI highlights.
 */
public final class BookSync {

    private static final long NOTE_MS = 6000;

    private static WeakReference<DocumentController> shown = new WeakReference<>(null);

    private BookSync() {
    }

    /** A reader shows its book (opened, or back on screen). */
    public static void shown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null) {
                return;
            }
            shown = new WeakReference<>(dc);
            SyncEvents.bookShown(dc.getCurrentBook().getPath());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** A screen left: when it was the reader, no book is shown any more. */
    static void hidden(Activity activity) {
        final DocumentController dc = shown.get();
        if (dc != null && dc.getActivity() == activity) {
            shown.clear();
            SyncEvents.bookHidden();
        }
    }

    /** On the main thread: a sync changed these files (relative to the profile) of {@code book}. */
    static void pulled(String book, Set<String> changed) {
        try {
            final DocumentController dc = shown.get();
            final boolean open = dc != null && dc.getCurrentBook() != null && book.equals(dc.getCurrentBook().getPath());
            if (changed.contains(AppProfile.APP_PROGRESS_JSON)) {
                final AppBook current = open ? SettingsManager.getBookSettings() : null;
                SharedBooks.preloadAll(true);
                if (current != null) {
                    // the reader keeps the record it saves to
                    SharedBooks.cache.put(ExtUtils.getFileName(book), current);
                    follow(dc, current, SharedBooks.loadFromFile(book));
                }
            }
            final String name = AiHighlights.bookName(book);
            if (open && changed.contains(AiStore.profilePath(name)) && name.equals(AiHighlights.get().getBook())) {
                AiHighlights.get().reload(name);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** Goes where another device read last, if that is newer than anything done here. */
    private static void follow(final DocumentController dc, final AppBook current, final AppBook synced) {
        final int pages = dc.getPageCount();
        if (pages <= 0 || synced.t <= 0) {
            return;
        }
        final int page = dc.findBookmarkPage(synced.getCurrentPage(pages).viewIndex + 1, null, synced.pt);
        final int here = dc.getCurentPageFirst1();
        final boolean busy = TTSEngine.get().isPlaying() || AppState.get().isAutoScroll || AnnotationSession.isShownFor(dc);
        final String device = TextUtils.isEmpty(synced.dv) ? dc.getActivity().getString(R.string.sync_other_device) : synced.dv;

        switch (JumpRule.decide(current.t, here, synced.t, page, busy)) {
            case JumpRule.JUMP:
                current.follow(synced, page);
                dc.onGoToPage(page);
                note(dc.getActivity(), dc.getActivity().getString(R.string.sync_moved_to, page, device),
                        dc.getActivity().getString(R.string.sync_undo), () -> dc.onGoToPage(here));
                break;
            case JumpRule.OFFER:
                note(dc.getActivity(), dc.getActivity().getString(R.string.sync_is_at, page, device),
                        dc.getActivity().getString(R.string.go), () -> {
                            current.follow(synced, page);
                            dc.onGoToPage(page);
                        });
                break;
            default:
                break;
        }
    }

    /** A short note at the bottom of the reader with one action, gone after a few seconds. */
    private static void note(Activity activity, String text, String action, Runnable onAction) {
        final FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final View old = content.findViewWithTag(BookSync.class);
        if (old != null) {
            content.removeView(old);
        }
        final int pad = Dips.dpToPx(10);
        final LinearLayout bar = new LinearLayout(activity);
        bar.setTag(BookSync.class);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad + pad / 2, pad / 2, pad / 2, pad / 2);
        final GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xe0323232);
        bubble.setCornerRadius(Dips.dpToPx(18));
        bar.setBackground(bubble);

        final TextView message = new TextView(activity);
        message.setTextColor(Color.WHITE);
        message.setTextSize(14);
        message.setText(text);
        bar.addView(message);

        final TextView button = new TextView(activity);
        button.setTextColor(0xff8ab4f8);
        button.setTextSize(14);
        button.setAllCaps(true);
        button.setText(action);
        button.setPadding(pad + pad / 2, pad, pad, pad);
        bar.addView(button);

        final Runnable hide = () -> bar.animate().alpha(0).setDuration(200).withEndAction(() -> content.removeView(bar));
        button.setOnClickListener(v -> {
            bar.removeCallbacks(hide);
            content.removeView(bar);
            onAction.run();
        });

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = Dips.dpToPx(72);
        content.addView(bar, params);
        bar.postDelayed(hide, NOTE_MS);
    }
}
