package com.foobnix.assist.review;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJump;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.recall.RecallChapter;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A review session, full screen (canvas Review and ReviewCorrect): one card per due idea, each a
 * scenario with up to four options (ideas of the same book, with their chapters). The reader's
 * first answer counts and is saved at once; a wrong one may be tried again, once. After the
 * answer: "Right:" or "Not quite.", the "Comes back" row with the next gap, "Explain more" (to the
 * idea's passage, when it is in the open book) and "Next card". One request for the whole
 * session writes the scenarios when it opens ({@link ReviewJob}); without it the cards ask
 * "Which idea is this?".
 */
final class ReviewSessionScreen implements ReviewJob.Screen {

    private static final String LETTERS = "ABCDEFGH";

    private final Activity a;
    /** The open book, or null. */
    private final DocumentController dc;
    private final List<ReviewQueue.Entry> due;
    private final Runnable onDone;
    /** The screen under this one, closed too when "Explain more" goes to the book; or null. */
    private final Dialog parent;
    private final String today = ReviewStore.today();

    private ReviewPage page;
    private ReviewViews.Progress progress;
    private ReviewJob job;
    private List<ReviewCards.Card> cards = new ArrayList<>();
    /** Why the cards have no scenarios, shown once on the first card; or null. */
    private String note;

    private int index;
    /** The option tapped, -1 before an answer. */
    private int picked = -1;
    /** The first answer was given and saved. */
    private boolean answered;
    private boolean firstRight;
    /** "Try again" was used. */
    private boolean again;
    private int right;

    private ReviewSessionScreen(Activity a, DocumentController dc, List<ReviewQueue.Entry> due, Runnable onDone,
                                Dialog parent) {
        this.a = a;
        this.dc = dc;
        this.due = new ArrayList<>(due);
        this.onDone = onDone;
        this.parent = parent;
    }

    /**
     * @param title  the bar's title ("Today's review")
     * @param onDone runs when the session closes (main thread), or null
     */
    static void start(Activity a, DocumentController dc, List<ReviewQueue.Entry> due, String title, Runnable onDone,
                      Dialog parent) {
        if (!Assist.REVIEW.isOn() || a == null || Apps.isDestroyedActivity(a) || due == null || due.isEmpty()) {
            return;
        }
        final ReviewSessionScreen s = new ReviewSessionScreen(a, dc, due, onDone, parent);
        s.page = new ReviewPage(a, title, "", true);
        s.progress = new ReviewViews.Progress(a);
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(20), dp(4), dp(20), 0);
        s.page.top.addView(s.progress, p);
        s.page.dialog.setOnDismissListener(d -> {
            if (s.job != null) {
                s.job.cancel();
            }
            if (onDone != null) {
                onDone.run();
            }
        });
        s.waiting();
        s.page.dialog.show();
        s.prepare();
    }

    // ------------------------------------------------------------------ the request

    private void waiting() {
        page.clear();
        page.setSubtitle(ReviewWords.about(a, due.size()));
        progress.set(due.size(), -1);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(48), 0, 0);
        box.addView(new ProgressBar(a), new LinearLayout.LayoutParams(dp(32), dp(32)));
        final TextView t = ReviewPage.text(a, a.getString(R.string.canvas_t24_writing), 14, 400, CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, 0);
        box.addView(t);
        final TextView sends = ReviewPage.text(a, a.getString(R.string.canvas_t24_sends), 12, 400, CanvasLook.muted());
        sends.setGravity(Gravity.CENTER);
        sends.setLineSpacing(dp(2), 1f);
        sends.setPadding(dp(16), dp(8), dp(16), 0);
        box.addView(sends);
        page.add(box, 0);
    }

    /** The cards with their options (stored ideas, shuffled here), then the one request. */
    private void prepare() {
        final Set<String> books = new LinkedHashSet<>();
        final Map<String, String> titles = new HashMap<>();
        for (ReviewQueue.Entry e : due) {
            books.add(e.bookName);
            titles.put(e.bookName, e.bookTitle);
        }
        ReviewStore.IO.execute(() -> {
            final List<ReviewCards.Card> built = ReviewCards.build(due, ReviewStore.pools(books), titles, new Random());
            ui(() -> {
                cards = built;
                job = new ReviewJob(a, cards, this);
                job.start();
            });
        });
    }

    @Override
    public void onTexts(Map<Integer, ReviewPrompts.Texts> texts) {
        job = null;
        ReviewCards.apply(cards, texts);
        show(0);
    }

    @Override
    public void onNoTexts(String message) {
        job = null;
        note = message == null ? a.getString(R.string.canvas_t24_no_ai) : a.getString(R.string.canvas_t24_no_scenarios, message);
        show(0);
    }

    // ------------------------------------------------------------------ a card

    private void show(int i) {
        if (!page.dialog.isShowing()) {
            return;
        }
        index = i;
        picked = -1;
        answered = false;
        again = false;
        render();
    }

    private void render() {
        final ReviewCards.Card card = cards.get(index);
        final ReviewItem item = card.entry.item;
        page.clear();
        progress.set(cards.size(), index);
        page.setSubtitle(cards.size() == 1 ? card.entry.bookTitle
                : a.getString(R.string.canvas_t24_n_of, index + 1, cards.size()) + " · " + ReviewWords.about(a, cards.size()));

        if (!answered) {
            // why this idea is asked today: how it came here, and that it is fading now
            final String label = item.label();
            final boolean recall = ReviewItem.MISSED.equals(item.reason) || ReviewItem.UNSURE.equals(item.reason);
            final boolean fading = ReviewItem.FADING.equals(label);
            final String due = ReviewWords.due(a, item.due, today);
            final TextView dueChip = ReviewPage.chip(a, fading ? ReviewWords.reason(a, label) + " · " + due : due,
                    ReviewPage.CHIP_PLAIN);
            dueChip.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(7)));
            final TextView whyLabel = ReviewPage.label(a, a.getString(R.string.canvas_t24_why), false);
            final LinearLayout why = recall || !fading
                    ? ReviewPage.row(a, whyLabel, ReviewPage.chip(a, ReviewWords.reason(a, recall ? item.reason : label),
                    recall ? ReviewPage.CHIP_MISSED : ReviewPage.CHIP_PLAIN), dueChip)
                    : ReviewPage.row(a, whyLabel, dueChip);
            why.setPadding(dp(4), 0, 0, 0);
            page.add(why, 14);
        }

        // the scenario, or without one the idea with its first words hidden
        final LinearLayout scenario = new LinearLayout(a);
        scenario.setOrientation(LinearLayout.VERTICAL);
        scenario.setPadding(dp(16), dp(answered ? 14 : 16), dp(16), dp(answered ? 14 : 16));
        scenario.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, dp(18)));
        scenario.addView(ReviewPage.label(a, a.getString(card.hasScenario() ? R.string.canvas_t24_scenario
                : R.string.canvas_t24_which_idea), true));
        final TextView q = ReviewPage.text(a, card.hasScenario() ? card.scenario : ReviewCards.hidden(item.idea),
                answered ? 15 : 17, 500, CanvasLook.ink());
        q.setLineSpacing(dp(answered ? 4 : 5), 1f);
        q.setPadding(0, dp(6), 0, 0);
        scenario.addView(q);
        if (!card.hasScenario()) {
            final TextView from = ReviewPage.text(a, card.entry.bookTitle, 12, 400, CanvasLook.muted());
            from.setPadding(0, dp(6), 0, 0);
            scenario.addView(from);
        }
        page.add(scenario, 12);
        if (note != null && index == 0 && !answered) {
            final TextView n = ReviewPage.text(a, note, 12, 400, CanvasLook.muted());
            n.setPadding(dp(4), 0, dp(4), 0);
            n.setLineSpacing(dp(2), 1f);
            page.add(n, 8);
        }

        final int correct = card.correct();
        for (int o = 0; o < card.options.size(); o++) {
            page.add(option(card, o, correct), o == 0 ? 14 : 8);
        }

        if (picked >= 0) {
            page.add(feedback(card, picked == correct), 12);
        }
        if (answered && picked >= 0) {
            page.add(comesBack(item), 12);
            foot(card);
        }
    }

    /** One answer: its letter, the idea and its chapter; after an answer the right one and a wrong pick are marked. */
    private View option(ReviewCards.Card card, int o, int correct) {
        final ReviewCards.Option option = card.options.get(o);
        final boolean shown = picked >= 0;
        final boolean isRight = shown && o == correct;
        final boolean isWrong = shown && o == picked && o != correct;

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(shown ? 50 : 56));
        row.setPadding(dp(12), dp(shown ? 8 : 10), dp(12), dp(shown ? 8 : 10));
        final android.graphics.drawable.GradientDrawable box = CanvasParts.round(
                isRight ? CanvasLook.accentTint() : isWrong ? ReviewPage.missedTint() : CanvasLook.bg(),
                CanvasLook.line(), dp(14));
        box.setStroke(Math.max(2, Math.round(1.5f * a.getResources().getDisplayMetrics().density)),
                isRight ? CanvasLook.accent() : isWrong ? ReviewPage.missedBar() : CanvasLook.line());
        row.setBackground(box);

        final TextView badge = ReviewPage.text(a, String.valueOf(LETTERS.charAt(Math.min(o, LETTERS.length() - 1))), 13, 700,
                isRight ? CanvasLook.onAccent() : isWrong ? CanvasLook.bg() : CanvasLook.ink2());
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(CanvasParts.round(isRight ? CanvasLook.accent() : isWrong ? CanvasLook.sensor()
                : CanvasLook.surface2(), 0, dp(8)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(28), dp(28)));

        final TextView text = ReviewPage.text(a, option.text, shown ? 14 : 15, isRight ? 600 : 400,
                shown && !isRight && !isWrong ? CanvasLook.ink2() : CanvasLook.ink());
        text.setLineSpacing(dp(2), 1f);
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = dp(12);
        row.addView(text, tp);

        if (isRight) {
            final TextView check = ReviewPage.text(a, "✓", 12, 700, CanvasLook.onAccent());
            check.setGravity(Gravity.CENTER);
            check.setIncludeFontPadding(false);
            check.setBackground(CanvasParts.round(CanvasLook.accent(), 0, dp(10)));
            check.setContentDescription(a.getString(R.string.canvas_t24_correct));
            final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(20), dp(20));
            cp.leftMargin = dp(8);
            row.addView(check, cp);
        } else if (!shown && !option.tag.isEmpty()) {
            // the chapter the idea is from, as the canvas's "CH. 1"
            final TextView tag = ReviewPage.label(a, option.tag, false);
            tag.setSingleLine(true);
            tag.setEllipsize(TextUtils.TruncateAt.END);
            tag.setMaxWidth(dp(96));
            final LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.leftMargin = dp(8);
            row.addView(tag, gp);
        }
        row.setContentDescription(badge.getText() + ". " + option.text
                + (option.tag.isEmpty() ? "" : ". " + option.tag)
                + (isRight ? ". " + a.getString(R.string.canvas_t24_correct) : ""));
        if (!shown) {
            row.setForeground(CanvasParts.ripple(null));
            row.setOnClickListener(v -> pick(o));
        }
        return row;
    }

    private void pick(int o) {
        if (picked >= 0) {
            return;
        }
        final ReviewCards.Card card = cards.get(index);
        picked = o;
        if (!answered) {
            // the first answer is the one that counts: saved now, whatever happens next
            answered = true;
            firstRight = o == card.correct();
            if (firstRight) {
                right++;
            }
            final ReviewItem item = card.entry.item;
            ReviewSchedule.answer(item, firstRight, today);
            final String book = card.entry.bookName;
            ReviewStore.IO.execute(() -> {
                try {
                    ReviewStore.save(book, item, null);
                } catch (Exception e) {
                    LOG.e(e);
                    a.runOnUiThread(() -> Toast.makeText(a, R.string.canvas_t24_not_saved, Toast.LENGTH_LONG).show());
                }
            });
        }
        render();
    }

    /** "Right: ..." in the accent's tint, or "Not quite." in orange with "Try again" (once). */
    private View feedback(ReviewCards.Card card, boolean isRight) {
        final ReviewItem item = card.entry.item;
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(CanvasParts.round(isRight ? CanvasLook.accentTint() : ReviewPage.missedTint(), 0, dp(14)));
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final String head = isRight ? a.getString(R.string.canvas_t24_right, item.idea)
                : a.getString(R.string.canvas_t24_not_quite);
        final TextView title = ReviewPage.text(a, head, 15, 600, isRight ? CanvasLook.accent() : CanvasLook.sensor());
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        // what comes back when: the first answer decided it
        final String back = firstRight ? a.getString(R.string.canvas_t24_back_right, ReviewWords.in(a, wait(item)),
                ReviewWords.gap(a, ReviewSchedule.ahead(item, 1)[1]))
                : isRight ? a.getString(R.string.canvas_t24_back_second)
                : a.getString(R.string.canvas_t24_back_wrong);
        final String why = isRight ? card.right : card.notQuite;
        final TextView body = ReviewPage.text(a, why.isEmpty() ? back : why + " " + back, 13, 400, CanvasLook.ink());
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(0, dp(4), 0, 0);
        texts.addView(body);
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!isRight && !again) {
            final TextView retry = ReviewPage.text(a, a.getString(R.string.canvas_t24_try_again), 14, 600, CanvasLook.sensor());
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(dp(12), 0, dp(12), 0);
            retry.setBackground(CanvasParts.round(CanvasLook.bg(), ReviewPage.missedLine(), dp(12)));
            retry.setForeground(CanvasParts.ripple(null));
            retry.setOnClickListener(v -> {
                again = true;
                picked = -1;
                render();
            });
            final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.target());
            rp.leftMargin = dp(10);
            box.addView(retry, rp);
        }
        return box;
    }

    /** "Comes back   gaps grow as it sticks" over Today, the next review and the two after it. */
    private View comesBack(ReviewItem item) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(14)));
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(ReviewPage.text(a, a.getString(R.string.canvas_t24_comes_back), 13, 600, CanvasLook.ink()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(ReviewPage.text(a, a.getString(R.string.canvas_t24_gaps_grow), 12, 400, CanvasLook.muted()));
        box.addView(head);
        final int[] gaps = ReviewSchedule.ahead(item, 2);
        final String[] words = {a.getString(R.string.canvas_t24_today_word), ReviewWords.in(a, wait(item)),
                ReviewWords.gap(a, gaps[1]), ReviewWords.gap(a, gaps[2])};
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(new ReviewViews.Ladder(a, words), lp);
        return box;
    }

    /** The days until the idea comes back, counted from today (an idea looked at early keeps its day). */
    private int wait(ReviewItem item) {
        return Math.max(1, com.foobnix.assist.useit.UseDates.daysBetween(today, item.due));
    }

    /** "Explain more" (only when the idea's passage is in the open book) and "Next card" or "Done". */
    private void foot(ReviewCards.Card card) {
        final boolean last = index >= cards.size() - 1;
        final TextView next = ReviewPage.primary(a, a.getString(last ? R.string.canvas_t24_done : R.string.canvas_t24_next_card));
        next.setTextSize(14);
        next.setOnClickListener(v -> {
            if (last) {
                finish();
            } else {
                show(index + 1);
            }
        });
        final Object[] place = place(card.entry);
        if (place == null) {
            page.actionRow(next);
            return;
        }
        final TextView more = ReviewPage.secondary(a, a.getString(R.string.canvas_t24_explain_more));
        more.setOnClickListener(v -> {
            // back to the book, at the idea's passage; the cards not answered stay due
            page.dialog.dismiss();
            if (parent != null) {
                parent.dismiss();
            }
            AiJump.toQuote(dc, (String) place[0], (Integer) place[1], -1, -1);
        });
        page.actionRow(more, next);
    }

    /**
     * Where "Explain more" goes: {the passage's words, its page hint}, or null when the idea is
     * not of the open book or has no mark in it.
     */
    private Object[] place(ReviewQueue.Entry e) {
        try {
            if (dc == null || dc.getCurrentBook() == null
                    || !ReviewStore.name(dc.getCurrentBook().getPath()).equals(e.bookName)) {
                return null;
            }
            if (!e.item.quote.isEmpty()) {
                return new Object[]{e.item.quote, e.item.page};
            }
            // an idea of the chapter's key passages: its mark, as the book has it now
            final String bookName = AiHighlights.bookName(dc.getCurrentBook().getPath());
            for (Recall.Idea idea : RecallChapter.fromPassages(bookName, e.item.chapterKey)) {
                if (String.valueOf(idea.id).equals(e.item.id) && !idea.quote().isEmpty()) {
                    return new Object[]{idea.quote(), idea.page()};
                }
            }
        } catch (Exception ex) {
            LOG.e(ex);
        }
        return null;
    }

    // ------------------------------------------------------------------ the end

    /** After the last card: how it went and what is next, read again from the store. */
    private void finish() {
        if (cards.size() == 1) {
            page.dialog.dismiss();
            return;
        }
        ReviewStore.IO.execute(() -> {
            final int[] next = ReviewQueue.next(ReviewStore.all(), today);
            ui(() -> {
                page.clear();
                progress.set(cards.size(), cards.size());
                page.setSubtitle("");
                final TextView title = ReviewPage.text(a, a.getString(R.string.canvas_t24_session_done), 22, 600, CanvasLook.ink());
                title.setPadding(dp(4), 0, dp(4), 0);
                page.add(title, 24);
                final TextView score = ReviewPage.text(a, a.getString(R.string.canvas_t24_score, right, cards.size()), 15, 400,
                        CanvasLook.ink2());
                score.setPadding(dp(4), 0, dp(4), 0);
                page.add(score, 8);
                final TextView after = ReviewPage.text(a, ReviewWords.next(a, next), 13, 400, CanvasLook.muted());
                after.setPadding(dp(4), 0, dp(4), 0);
                page.add(after, 8);
                final TextView close = ReviewPage.primary(a, a.getString(R.string.close));
                close.setOnClickListener(v -> page.dialog.dismiss());
                page.actionRow(close);
            });
        });
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }

    private void ui(Runnable r) {
        a.runOnUiThread(() -> {
            if (!Apps.isDestroyedActivity(a) && page.dialog.isShowing()) {
                r.run();
            }
        });
    }
}
