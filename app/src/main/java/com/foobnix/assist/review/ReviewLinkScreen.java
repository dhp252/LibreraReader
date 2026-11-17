package com.foobnix.assist.review;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * A connection card (canvas ReviewLink): "FROM YOUR LIBRARY · Same idea, 240 years apart", the
 * two ideas of two books with what joins them, the reader's one sentence, and "Not the same" /
 * "Add to my map". Both ideas are shown as the stores keep them, never as quotes. Works that are
 * not in the library ("ALSO LINKED TO THIS IDEA") come from the model's own knowledge: they show
 * only under a line that says so. The card fills a page: the last card of a session, or a
 * screen of its own from the Connections list.
 */
final class ReviewLinkScreen {

    private ReviewLinkScreen() {
    }

    /** A screen of its own for one connection. */
    static void show(Activity a, ReviewLink link, Runnable onDecided) {
        if (!Assist.REVIEW.isOn() || a == null || Apps.isDestroyedActivity(a) || link == null) {
            return;
        }
        final ReviewPage page = new ReviewPage(a, a.getString(R.string.canvas_t25_connections),
                a.getString(R.string.canvas_t25_a_connection), true);
        fill(page, link, () -> {
            page.dialog.dismiss();
            if (onDecided != null) {
                onDecided.run();
            }
        });
        page.dialog.show();
    }

    /**
     * Fills {@code page} with the card.
     *
     * @param onDecided runs after the reader's choice is saved (main thread)
     */
    static void fill(final ReviewPage page, final ReviewLink link, final Runnable onDecided) {
        final Activity a = page.a;
        page.clear();

        final TextView from = ReviewPage.label(a, a.getString(R.string.canvas_t25_from_library), true);
        from.setPadding(dp(4), 0, dp(4), 0);
        page.add(from, 4);
        final boolean same = ReviewLink.SAME.equals(link.relation);
        final boolean disagrees = ReviewLink.DISAGREES.equals(link.relation);
        final int years = link.yearsApart();
        final String headline = same ? (years > 0 ? a.getString(R.string.canvas_t25_same_years, years)
                : a.getString(R.string.canvas_t25_same_two)) : disagrees ? a.getString(R.string.canvas_t25_disagree_head)
                : a.getString(R.string.canvas_t25_extends_head);
        final TextView head = ReviewPage.text(a, headline, 22, 600, CanvasLook.ink());
        head.setPadding(dp(4), 0, dp(4), 0);
        page.add(head, 4);

        // the two ideas, and what joins them
        page.add(side(a, link.a, 0), 16);
        final String word = a.getString(same ? R.string.canvas_t25_word_same : disagrees ? R.string.canvas_t25_word_disagrees
                : R.string.canvas_t25_word_extends);
        page.add(new ReviewViews.Connector(a, word), 0);
        page.add(side(a, link.b, 2), 0);
        if (!link.why.isEmpty()) {
            // the model's line: its reading of two stored ideas, said to be the AI's
            final TextView why = ReviewPage.text(a, a.getString(R.string.canvas_t25_ai_says, link.why), 12, 400,
                    CanvasLook.muted());
            why.setLineSpacing(dp(2), 1f);
            why.setPadding(dp(4), 0, dp(4), 0);
            page.add(why, 8);
        }

        // the reader's own sentence
        final TextView ask = ReviewPage.text(a, a.getString(same ? R.string.canvas_t25_ask_same : disagrees
                ? R.string.canvas_t25_ask_disagrees : R.string.canvas_t25_ask_extends), 14, 600, CanvasLook.ink());
        ask.setPadding(dp(4), 0, dp(4), 0);
        page.add(ask, 18);
        final EditText field = new EditText(a);
        CanvasParts.text(field, 14, 400);
        field.setHintTextColor(CanvasLook.muted());
        final android.graphics.drawable.GradientDrawable box = CanvasParts.round(CanvasLook.bg(), CanvasLook.accent(), dp(12));
        box.setStroke(Math.max(2, Math.round(1.5f * a.getResources().getDisplayMetrics().density)), CanvasLook.accent());
        field.setBackground(box);
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        field.setMinHeight(dp(48));
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setMaxLines(4);
        field.setHint(a.getString(same ? R.string.canvas_t25_hint_same : disagrees ? R.string.canvas_t25_hint_disagrees
                : R.string.canvas_t25_hint_extends));
        field.setText(link.sentence);
        field.setContentDescription(ask.getText());
        page.add(field, 6);

        if (!link.also.isEmpty()) {
            // not from the library: the model's own knowledge, labelled, never a quote
            final TextView also = ReviewPage.label(a, a.getString(R.string.canvas_t25_also), false);
            also.setPadding(dp(4), 0, dp(4), 0);
            page.add(also, 18);
            final TextView honest = ReviewPage.text(a, a.getString(R.string.canvas_t25_also_honest), 12, 400, CanvasLook.muted());
            honest.setLineSpacing(dp(2), 1f);
            honest.setPadding(dp(4), 0, dp(4), 0);
            page.add(honest, 4);
            for (int i = 0; i < link.also.size(); i++) {
                page.add(outside(a, link.also.get(i)), i == 0 ? 8 : 6);
            }
        }

        final TextView no = ReviewPage.secondary(a, a.getString(same ? R.string.canvas_t25_not_same : R.string.canvas_t25_not_linked));
        final TextView yes = ReviewPage.primary(a, a.getString(R.string.canvas_t25_add_to_map));
        yes.setTextSize(14);
        ReviewPage.enable(yes, !link.sentence.isEmpty());
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // the map takes a connection only with the reader's own sentence
                ReviewPage.enable(yes, !s.toString().trim().isEmpty());
            }
        });
        no.setOnClickListener(v -> decide(page, link, ReviewLink.REJECTED, field.getText().toString(), onDecided));
        yes.setOnClickListener(v -> decide(page, link, ReviewLink.ADDED, field.getText().toString(), onDecided));
        page.actionRow(no, yes);
    }

    private static void decide(ReviewPage page, ReviewLink link, String state, String sentence, Runnable onDecided) {
        final Activity a = page.a;
        link.state = state;
        link.sentence = sentence == null ? "" : sentence.trim().replaceAll("\\s+", " ");
        ReviewStore.IO.execute(() -> {
            boolean saved = true;
            try {
                ReviewStore.saveLink(link);
            } catch (Exception e) {
                LOG.e(e);
                saved = false;
            }
            final boolean ok = saved;
            a.runOnUiThread(() -> {
                if (Apps.isDestroyedActivity(a)) {
                    return;
                }
                if (!ok) {
                    Toast.makeText(a, R.string.canvas_t25_not_saved, Toast.LENGTH_LONG).show();
                } else if (ReviewLink.ADDED.equals(state)) {
                    Toast.makeText(a, R.string.canvas_t25_added, Toast.LENGTH_SHORT).show();
                }
                if (onDecided != null) {
                    onDecided.run();
                }
            });
        });
    }

    /** One idea of one book: the book's square, "Smith · 1776 · chapter" and the idea as stored. */
    private static View side(Activity a, ReviewLink.Side s, int tone) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(84));
        row.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), dp(14)));
        row.addView(ReviewPage.square(a, ReviewText.letter(s.author, s.bookTitle), tone, 32),
                new LinearLayout.LayoutParams(dp(32), dp(32)));
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final StringBuilder caption = new StringBuilder(ReviewText.who(s.author, s.bookTitle));
        if (s.year > 0) {
            caption.append(" · ").append(s.year);
        }
        if (!s.chapterTitle.isEmpty()) {
            caption.append(" · ").append(s.chapterTitle);
        }
        final TextView cap = ReviewPage.label(a, caption, false);
        // names and titles stay as they are written
        cap.setAllCaps(false);
        cap.setTextSize(10);
        cap.setMaxLines(2);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(cap);
        final TextView idea = ReviewPage.text(a, s.idea, 14, 600, CanvasLook.ink());
        idea.setLineSpacing(dp(3), 1f);
        idea.setPadding(0, dp(3), 0, 0);
        texts.addView(idea);
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = dp(12);
        row.addView(texts, tp);
        return row;
    }

    /** "DISAGREES  work: its point" in orange, "EXTENDS  ..." in the accent. */
    private static View outside(Activity a, ReviewLink.Outside o) {
        final boolean disagrees = ReviewLink.DISAGREES.equals(o.relation);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(CanvasParts.round(disagrees ? ReviewPage.missedTint() : CanvasLook.accentTint(),
                disagrees ? ReviewPage.missedLine() : CanvasLook.accentLine(), dp(12)));
        final TextView tag = ReviewPage.text(a, a.getString(disagrees ? R.string.canvas_t25_tag_disagrees
                : R.string.canvas_t25_tag_extends), 11, 600, disagrees ? CanvasLook.sensor() : CanvasLook.accent());
        tag.setAllCaps(true);
        row.addView(tag, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView text = ReviewPage.text(a, o.text, 13, 400, CanvasLook.ink());
        text.setLineSpacing(dp(2), 1f);
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = dp(10);
        row.addView(text, tp);
        return row;
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
