package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.recall.RecallChapter;
import com.foobnix.assist.recall.RecallScreen;
import com.foobnix.assist.review.Review;
import com.foobnix.assist.zoom.ZoomData;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;

/**
 * "How you read it" (canvas artboard "How you read the chapter"): a chapter's stored page
 * counters as numbers (reading time, help offered and taken, spots to review) and "Attention,
 * section by section", each section rated from page times against the reader's own pace
 * ({@link ReadsSections}). It only reads what is stored; nothing is sent to the AI. With "Spaced
 * review" on, the ideas already stored for the marked spots enter the review queue.
 */
final class ReadsMapScreen {

    private final Activity a;
    private final DocumentController dc;
    private final String book;
    private Dialog dialog;
    private LinearLayout body;

    private BookChapters.Range range;
    private ReadsSignals signals;
    private List<ReadsSections.Section> sections;
    private ReadsSpots.Result spots;
    private double pace;

    private ReadsMapScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.book = AiHighlights.bookName(dc.getCurrentBook().getPath());
    }

    /**
     * Opens the screen for the chapter the book's page {@code page} (0-based) is in; a chapter
     * with nothing stored only says so.
     *
     * @param sessionPace the running session's pace in ms a word, used while the book has none stored
     */
    static void show(Activity a, DocumentController dc, int page, double sessionPace) {
        if (a == null || Apps.isDestroyedActivity(a) || dc == null || dc.getCurrentBook() == null
                || !Assist.READS_YOU.isOn()) {
            return;
        }
        new ReadsMapScreen(a, dc).load(AiJobs.Target.of(dc), page, sessionPace);
    }

    private void load(final AiJobs.Target t, final int page, final double sessionPace) {
        ReadsStore.IO.execute(() -> {
            boolean ok = false;
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    final List<BookChapters.Entry> toc = AiJobs.toc(t, text);
                    range = ReadsStore.chapterAt(BookChapters.chaptersOrBlocks(toc, text.pageCount(),
                            BookChapters.BLOCK_PAGES), page);
                    if (range != null) {
                        signals = ReadsStore.signals(book, range.key);
                        signals.fit(range.pages());
                        final double stored = ReadsStore.state(book).pace;
                        pace = stored > 0 ? stored : sessionPace;
                        sections = ReadsSections.rate(ReadsSections.split(toc, range.from, range.to), signals, pace);
                        ZoomData zoom = null;
                        try {
                            zoom = ZoomData.fromJson(AssistStore.get().item(book, ZoomData.key(range.key)));
                        } catch (Exception e) {
                            LOG.e(e);
                        }
                        spots = ReadsSpots.ideas(signals, range.from, RecallChapter.fromPassages(book, range.key), zoom);
                        ok = !signals.isEmpty();
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final boolean found = ok;
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a) || !Assist.READS_YOU.isOn()) {
                    return;
                }
                if (!found) {
                    Toast.makeText(a, R.string.canvas_t32_nothing, Toast.LENGTH_LONG).show();
                    return;
                }
                open();
            });
        });
    }

    private String chapterTitle() {
        return range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                : a.getString(R.string.ai_pages, range.from + 1, range.to);
    }

    private void open() {
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(ReadsUi.topBar(a, a.getString(R.string.canvas_t32_title), dc.getTitle(), () -> dialog.dismiss()));
        final ScrollView scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(8), gutter, CanvasLook.dp(24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog = ReadsUi.fullScreen(a, page);
        render();
        dialog.show();
    }

    private void render() {
        final ReadsSignals.Page total = signals.total();
        body.addView(ReadsUi.mono(a, chapterTitle(), CanvasLook.muted()));
        final TextView head = ReadsUi.text(a, a.getString(R.string.canvas_t32_title), 22, 600, CanvasLook.ink());
        body.addView(head, ReadsUi.matchWrap(4));

        final LinearLayout tiles = new LinearLayout(a);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        final int minutes = ReadsRules.minutes(total.seconds * 1000L);
        tile(tiles, minutes > 0 ? a.getString(R.string.canvas_t32_min, minutes) : a.getString(R.string.canvas_t32_under_min),
                a.getString(R.string.canvas_t32_time), false);
        tile(tiles, String.valueOf(total.offers), a.getString(R.string.canvas_t32_help, total.helps), false);
        tile(tiles, String.valueOf(spots.spots), a.getString(R.string.canvas_t32_spots), true);
        body.addView(tiles, ReadsUi.matchWrap(14));

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ReadsUi.text(a, a.getString(R.string.canvas_t32_attention), CanvasLook.TEXT_TITLE, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(ReadsUi.mono(a, a.getString(sourceLabel(total)), CanvasLook.sensor()));
        body.addView(row, ReadsUi.matchWrap(22));

        for (ReadsSections.Section s : sections) {
            body.addView(sectionRow(s), ReadsUi.matchWrap(8));
        }

        final int wpm = ReadsRules.wpm(pace);
        final TextView legend = ReadsUi.text(a, a.getString(R.string.canvas_t32_legend) + " " + (wpm > 0
                ? a.getString(R.string.canvas_t32_legend_pace, wpm) : a.getString(R.string.canvas_t32_legend_no_pace))
                + legendMore(total), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        legend.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(legend, ReadsUi.matchWrap(14));

        final TextView review = ReadsUi.text(a, "", 13, 400, CanvasLook.ink2());
        review.setLineSpacing(CanvasLook.dp(2), 1f);
        body.addView(review, ReadsUi.matchWrap(14));
        review(review);

        if (Assist.RECALL.isOn()) {
            final TextView recall = ReadsUi.button(a, a.getString(R.string.canvas_t32_recall), true);
            recall.setOnClickListener(v -> {
                dialog.dismiss();
                RecallScreen.show(a, dc, range);
            });
            body.addView(recall, ReadsUi.matchWrap(18));
        }
        final TextView stored = ReadsUi.text(a, a.getString(R.string.canvas_t31_stored_title), 13, 600, CanvasLook.accent());
        stored.setGravity(Gravity.CENTER);
        stored.setMinHeight(CanvasLook.target());
        stored.setOnClickListener(v -> ReadsYou.showStored(a, dc));
        body.addView(stored, ReadsUi.matchWrap(6));
    }

    /** "PAGE TIMES": what the ratings come from. The camera is named only when its counters are in this chapter. */
    private int sourceLabel(ReadsSignals.Page total) {
        return R.string.canvas_t32_source_times;
    }

    /** More of the legend when the chapter has more than page times. */
    private String legendMore(ReadsSignals.Page total) {
        return "";
    }

    /**
     * The line on the review queue: the ideas stored for the marked spots enter it when "Spaced
     * review" is on, and the line says what was done, with the numbers that are true.
     */
    private void review(final TextView line) {
        if (spots.spots == 0) {
            line.setText(R.string.canvas_t32_review_none);
            return;
        }
        final String without = spots.without > 0 ? " " + a.getString(R.string.canvas_t32_review_without, spots.without) : "";
        if (spots.ideas.isEmpty()) {
            line.setText(a.getString(R.string.canvas_t32_review_no_ideas, spots.spots));
            return;
        }
        if (!Assist.REVIEW.isOn()) {
            line.setText(a.getString(R.string.canvas_t32_review_off, spots.ideas.size()) + without);
            return;
        }
        line.setText(R.string.canvas_t32_review_adding);
        final List<Recall.Idea> ideas = spots.ideas;
        final boolean started = Review.addSpots(a, dc, range.key, chapterTitle(), ideas, (added, already) -> {
            if (dialog.isShowing()) {
                line.setText(a.getString(R.string.canvas_t32_review_done, added, already) + without);
            }
        });
        if (!started) {
            line.setText(a.getString(R.string.canvas_t32_review_off, ideas.size()) + without);
        }
    }

    private void tile(LinearLayout row, String number, String label, boolean last) {
        final LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        final int pad = CanvasLook.dp(12);
        tile.setPadding(pad, pad, pad, pad);
        tile.setBackground(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_DP)));
        tile.addView(ReadsUi.text(a, number, 20, 600, CanvasLook.ink()));
        final TextView l = ReadsUi.text(a, label, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        l.setPadding(0, CanvasLook.dp(2), 0, 0);
        tile.addView(l);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.rightMargin = last ? 0 : CanvasLook.dp(8);
        row.addView(tile, lp);
    }

    /** One section: its title (or its pages), its time, how it was read, and its review marks. A tap goes to it. */
    private View sectionRow(final ReadsSections.Section s) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int pad = CanvasLook.dp(12);
        row.setPadding(pad, CanvasLook.dp(8), pad, CanvasLook.dp(8));
        row.setMinimumHeight(CanvasLook.target());
        row.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.surface(), CanvasLook.line(),
                CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP))));
        final String pages = a.getString(R.string.ai_pages, range.from + s.from + 1, range.from + s.to);

        final LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(ReadsUi.text(a, s.title.isEmpty() ? pages : s.title, CanvasLook.TEXT_BODY, 500, CanvasLook.ink()));
        final StringBuilder sub = new StringBuilder();
        if (!s.title.isEmpty()) {
            sub.append(pages);
        }
        if (s.seconds > 0) {
            sub.append(sub.length() > 0 ? " · " : "").append(ReadsUi.clock(s.seconds * 1000L));
        }
        if (s.spots > 0) {
            sub.append(sub.length() > 0 ? " · " : "").append(a.getString(R.string.canvas_t32_marks, s.spots));
        }
        if (sub.length() > 0) {
            names.addView(ReadsUi.text(a, sub.toString(), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()));
        }
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final TextView rating = ReadsUi.text(a, a.getString(word(s.rating)), CanvasLook.TEXT_SMALL, 600, color(s.rating));
        rating.setGravity(Gravity.CENTER);
        rating.setPadding(CanvasLook.dp(10), CanvasLook.dp(4), CanvasLook.dp(10), CanvasLook.dp(4));
        rating.setBackground(CanvasParts.round(fill(s.rating), 0, CanvasLook.dp(12)));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CanvasLook.dp(8);
        row.addView(rating, lp);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            dc.onGoToPage(range.from + s.from + 1);
        });
        return row;
    }

    private static int word(ReadsSections.Rating r) {
        switch (r) {
            case STEADY:
                return R.string.canvas_t32_steady;
            case SKIMMED:
                return R.string.canvas_t32_skimmed;
            case STRUGGLED:
                return R.string.canvas_t32_struggled;
            case DRIFTED:
                return R.string.canvas_t32_drifted;
            default:
                return R.string.canvas_t32_not_timed;
        }
    }

    private static int color(ReadsSections.Rating r) {
        switch (r) {
            case STRUGGLED:
                return CanvasLook.accent();
            case DRIFTED:
                return CanvasLook.sensor();
            case STEADY:
                return CanvasLook.ink();
            default:
                return CanvasLook.muted();
        }
    }

    private static int fill(ReadsSections.Rating r) {
        switch (r) {
            case STRUGGLED:
                return CanvasLook.accentTint();
            case DRIFTED:
                return CanvasLook.sensorTint();
            case NONE:
                return 0;
            default:
                return CanvasLook.surface2();
        }
    }
}
