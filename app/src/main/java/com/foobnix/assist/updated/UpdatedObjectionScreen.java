package com.foobnix.assist.updated;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;

/**
 * "Strongest objection" (canvas UpdatedObjection): the author's claim, the objection with how
 * strong it is, the twist (the author's own answer, with the book's words when the model gave
 * them), who pushed it further, and where the two really disagree. It always carries the line
 * that the AI wrote it from its own knowledge; words of the book that were not found in the
 * chapter are marked as quoted from memory. The canvas's "Both passages" and "Debate it" are not
 * here: Debate is a feature of its own.
 */
final class UpdatedObjectionScreen {

    private UpdatedObjectionScreen() {
    }

    /** @param goToTwist goes to the twist's words when they are in the chapter, else null */
    static void show(Activity a, UpdatedChapter chapter, String chapterTitle, Runnable goToTwist) {
        final UpdatedObjection o = chapter.objection;
        if (o == null) {
            return;
        }
        final boolean dark = CanvasLook.isNight();
        final Dialog dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = UpdatedParts.column(a);
        page.setBackgroundColor(CanvasLook.bg());
        page.addView(UpdatedScreen.topBar(a, a.getString(R.string.updated_objection),
                a.getString(R.string.updated_objection_sub, chapterTitle), dialog::dismiss),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));

        final ScrollView scroll = new ScrollView(a);
        final LinearLayout body = UpdatedParts.column(a);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // the author's claim
        final String author = UpdatedText.labelAuthor(chapter.author);
        final LinearLayout claim = UpdatedParts.column(a);
        claim.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
        claim.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        claim.addView(UpdatedParts.mono(a, author.isEmpty() ? a.getString(R.string.updated_claim_author)
                : a.getString(R.string.updated_claim_of, author), 10, CanvasLook.accent()));
        claim.addView(strong(a, o.claim));
        body.addView(claim);

        // the objection
        final LinearLayout objection = UpdatedParts.column(a);
        final GradientDrawable border = CanvasParts.round(UpdatedParts.warnCard(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP));
        border.setStroke(Math.max(1, CanvasLook.dp(3) / 2), UpdatedParts.warnLine());
        objection.setBackground(border);
        objection.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
        final LinearLayout head = UpdatedParts.row(a);
        head.addView(UpdatedParts.mono(a, a.getString(R.string.updated_the_objection), 10, CanvasLook.sensor()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (o.strength.bars > 0) {
            head.addView(UpdatedParts.bars(a, o.strength.bars, UpdatedParts.warnBar()));
            final TextView tag = UpdatedParts.text(a, a.getString(strengthName(o.strength)), CanvasLook.TEXT_SMALL, 600,
                    CanvasLook.sensor());
            tag.setPadding(CanvasLook.dp(6), 0, 0, 0);
            head.addView(tag);
        }
        objection.addView(head);
        objection.addView(strong(a, o.objection));
        body.addView(objection, UpdatedParts.wide(10));

        // the twist
        if (o.hasTwist()) {
            final LinearLayout twist = UpdatedParts.column(a);
            twist.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(4), 0);
            twist.addView(UpdatedParts.mono(a, o.twistHead.isEmpty() ? a.getString(R.string.updated_twist)
                    : a.getString(R.string.updated_twist_head, o.twistHead), CanvasLook.TEXT_LABEL, CanvasLook.muted()));
            if (!o.twistQuote.isEmpty()) {
                final TextView quote = UpdatedParts.text(a, "“" + o.twistQuote + "”", CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
                quote.setTypeface(Typeface.SERIF, Typeface.ITALIC);
                quote.setLineSpacing(0, 1.4f);
                quote.setPadding(0, CanvasLook.dp(8), 0, 0);
                twist.addView(quote);
                if (!o.twistInChapter) {
                    // the model's memory of the book, not the book: said so
                    final TextView memory = UpdatedParts.text(a, a.getString(R.string.updated_twist_memory),
                            CanvasLook.TEXT_SMALL, 400, CanvasLook.sensor());
                    memory.setPadding(0, CanvasLook.dp(6), 0, 0);
                    twist.addView(memory);
                }
            }
            if (!o.twistNote.isEmpty()) {
                final TextView note = UpdatedParts.text(a, o.twistNote, 13, 400, CanvasLook.ink2());
                note.setLineSpacing(0, 1.3f);
                note.setPadding(0, CanvasLook.dp(8), 0, 0);
                twist.addView(note);
            }
            body.addView(twist, UpdatedParts.wide(18));
        }

        // pushed further
        if (o.hasPushed()) {
            final LinearLayout pushed = UpdatedParts.column(a);
            pushed.setPadding(CanvasLook.dp(4), 0, CanvasLook.dp(4), 0);
            final String by = UpdatedText.pushedLine(o);
            pushed.addView(UpdatedParts.mono(a, by.isEmpty() ? a.getString(R.string.updated_pushed)
                    : a.getString(R.string.updated_pushed_by, by), CanvasLook.TEXT_LABEL, CanvasLook.muted()));
            final TextView text = UpdatedParts.text(a, o.pushedText, 14, 400, CanvasLook.ink());
            text.setLineSpacing(0, 1.3f);
            text.setPadding(0, CanvasLook.dp(6), 0, 0);
            pushed.addView(text);
            body.addView(pushed, UpdatedParts.wide(16));
        }

        // where they really disagree
        if (o.hasDisagree()) {
            final LinearLayout box = UpdatedParts.column(a);
            box.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
            box.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(12));
            box.addView(UpdatedParts.mono(a, a.getString(R.string.updated_disagree), CanvasLook.TEXT_LABEL, CanvasLook.muted()));
            if (!o.authorSide.isEmpty() && !o.otherSide.isEmpty()) {
                final LinearLayout sides = new LinearLayout(a);
                sides.setOrientation(LinearLayout.HORIZONTAL);
                sides.addView(side(a, author.isEmpty() ? a.getString(R.string.updated_side_author) : author, o.authorSide),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                right.leftMargin = CanvasLook.dp(12);
                sides.addView(side(a, o.pushedWho.isEmpty() ? a.getString(R.string.updated_side_other) : o.pushedWho,
                        o.otherSide), right);
                box.addView(sides, UpdatedParts.wide(8));
            } else {
                final TextView text = UpdatedParts.text(a, o.disagree, 13, 400, CanvasLook.ink());
                text.setLineSpacing(0, 1.3f);
                box.addView(text, UpdatedParts.wide(8));
            }
            body.addView(box, UpdatedParts.wide(16));
        }

        // the honesty rule stays in sight, below whatever scrolls
        final LinearLayout footer = UpdatedParts.column(a);
        footer.setPadding(gutter, CanvasLook.dp(10), gutter, CanvasLook.dp(14));
        footer.addView(UpdatedParts.honesty(a));
        if (goToTwist != null && o.twistInChapter) {
            final TextView go = UpdatedParts.button(a, a.getString(R.string.updated_go_to_passage), false);
            go.setOnClickListener(v -> {
                dialog.dismiss();
                goToTwist.run();
            });
            final LinearLayout.LayoutParams lp = UpdatedParts.wide(10);
            lp.height = CanvasLook.dp(52);
            footer.addView(go, lp);
        }
        page.addView(footer);

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.show();
    }

    private static TextView strong(Activity a, String s) {
        final TextView t = UpdatedParts.text(a, s, CanvasLook.TEXT_BODY, 600, CanvasLook.ink());
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, CanvasLook.dp(4), 0, 0);
        return t;
    }

    private static View side(Activity a, String who, String text) {
        final LinearLayout col = UpdatedParts.column(a);
        final TextView name = UpdatedParts.text(a, who, CanvasLook.TEXT_SMALL, 600, CanvasLook.ink());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        final TextView t = UpdatedParts.text(a, text, 13, 400, CanvasLook.ink());
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, CanvasLook.dp(4), 0, 0);
        col.addView(t);
        col.setGravity(Gravity.TOP);
        return col;
    }

    static int strengthName(UpdatedObjection.Strength s) {
        switch (s) {
            case STRONG:
                return R.string.updated_strength_strong;
            case MODERATE:
                return R.string.updated_strength_moderate;
            default:
                return R.string.updated_strength_weak;
        }
    }
}
