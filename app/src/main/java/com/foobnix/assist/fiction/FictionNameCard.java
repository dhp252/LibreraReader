package com.foobnix.assist.fiction;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSheet;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.List;

/**
 * "Tap a name" (canvas artboard FictionName): a name selected in the text that the stored who's
 * who knows gets a chip "Who is Aouda?" in the selection box, and the chip opens a sheet: who
 * they are as of the chapter the cast was built for, what they are doing right now, the spoiler
 * shield's line with the number of chapters it hides, "Her story so far" and "Who's who".
 * <p>
 * Nothing is asked of the AI here: the card shows stored data, always under its own "AS OF". A
 * reader who went back gets no chip for someone met later, and the descriptions (written at a
 * later chapter) stay closed until asked for.
 */
public final class FictionNameCard {

    private final Activity a;
    private final DocumentController dc;
    private final FictionCast cast;
    private final FictionChapters chapters;
    private final FictionWhere where;
    private final int readerEnd;
    private final FictionCast.Person person;
    /** Opened from the who's who screen, which is still under the card. */
    private final boolean fromScreen;

    private Dialog dialog;
    private LinearLayout content;
    private boolean storyOpen;
    /** The reader went back and asked to see what was written at the later chapter. */
    private boolean revealed;

    private FictionNameCard(Activity a, DocumentController dc, FictionCast cast, FictionChapters chapters,
                            FictionWhere where, int readerEnd, FictionCast.Person person, boolean fromScreen) {
        this.a = a;
        this.dc = dc;
        this.cast = cast;
        this.chapters = chapters;
        this.where = where;
        this.readerEnd = readerEnd;
        this.person = person;
        this.fromScreen = fromScreen;
    }

    // ------------------------------------------------------------------ the chip

    /**
     * The one entry of the text-selection box: a chip "Who is …?" in its row of dictionaries,
     * after the Lens chip, when the selected words are a name the stored cast holds and the reader
     * may see. Nothing is added with {@link Assist#FICTION} off, without a cast, or for other words.
     *
     * @param line     the box's row of dictionary apps
     * @param selected holds the selected text
     * @param close    closes the selection box
     */
    public static void addTo(final LinearLayout line, final DocumentController dc, final TextView selected,
                             final Runnable close) {
        if (!Assist.FICTION.isOn() || line == null || dc == null || selected == null || dc.getCurrentBook() == null) {
            return;
        }
        final String words = selected.getText() == null ? "" : selected.getText().toString();
        if (words.trim().isEmpty() || words.length() > 200) {
            return;
        }
        final Activity a = dc.getActivity();
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final int page = FictionBook.readerPage(dc);
        final int pages = FictionBook.readerPages(dc);
        new Thread(() -> {
            try {
                final FictionCast cast = FictionStore.cast(target.name);
                if (cast.isEmpty()) {
                    return;
                }
                // a quick no for words that are nobody's name at all
                if (FictionNames.match(cast.people, words) == null) {
                    return;
                }
                final FictionChapters chapters = FictionBook.chapters(target);
                if (chapters == null) {
                    return;
                }
                final int readerEnd = FictionShield.end(page, pages, chapters.pages());
                final FictionWhere where = FictionWhere.of(chapters, readerEnd, cast.mark);
                // only people the reader has met where they are now
                final FictionCast.Person person = FictionNames.match(FictionView.build(cast, chapters, where).visible, words);
                if (person == null || a == null) {
                    return;
                }
                a.runOnUiThread(() -> {
                    if (Apps.isDestroyedActivity(a) || !line.isAttachedToWindow() || !Assist.FICTION.isOn()) {
                        return;
                    }
                    line.addView(chip(line, dc, cast, chapters, where, readerEnd, person, close),
                            Math.min(line.getChildCount(), Assist.LENS.isOn() ? 1 : 0));
                });
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T Fiction name").start();
    }

    private static View chip(LinearLayout line, DocumentController dc, FictionCast cast, FictionChapters chapters,
                             FictionWhere where, int readerEnd, FictionCast.Person person, Runnable close) {
        final TextView chip = FictionUi.text(line.getContext(),
                line.getContext().getString(R.string.canvas_t29_who_is, person.name), 13, 600, CanvasLook.accent());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        chip.setBackground(CanvasParts.ripple(CanvasParts.chip(true)));
        chip.setOnClickListener(v -> {
            if (close != null) {
                close.run();
            }
            dc.clearSelectedText();
            show(dc.getActivity(), dc, cast, chapters, where, readerEnd, person, false);
        });
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                CanvasLook.target());
        lp.rightMargin = CanvasLook.dp(8);
        chip.setLayoutParams(lp);
        return chip;
    }

    // ------------------------------------------------------------------ the card

    /** The card of one person of the stored cast, for a reader at {@code where}. */
    static void show(Activity a, DocumentController dc, FictionCast cast, FictionChapters chapters, FictionWhere where,
                     int readerEnd, FictionCast.Person person, boolean fromScreen) {
        if (!Assist.FICTION.isOn() || a == null || Apps.isDestroyedActivity(a) || person == null || chapters == null
                || where == null) {
            return;
        }
        new FictionNameCard(a, dc, cast, chapters, where, readerEnd, person, fromScreen).open();
    }

    private void open() {
        content = FictionUi.column(a);
        fill();
        // a long story so far scrolls inside the sheet
        final ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        dialog = CanvasSheet.show(a, scroll);
    }

    private void fill() {
        content.removeAllViews();
        final boolean back = where.state == FictionWhere.BEFORE;
        final String built = chapters.label(Math.max(0, where.stored));

        // (A)  Aouda / First met in Ch. 12
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(FictionUi.avatar(a, person.initials(), 44));
        final LinearLayout names = FictionUi.column(a);
        names.setPadding(CanvasLook.dp(12), 0, 0, 0);
        names.addView(FictionUi.text(a, person.name, 18, 600, CanvasLook.ink()));
        final String met = FictionView.firstMet(cast, chapters, person);
        if (!met.isEmpty()) {
            names.addView(FictionUi.text(a, a.getString(R.string.canvas_t29_first_met, met), CanvasLook.TEXT_SMALL, 400,
                    CanvasLook.muted()));
        }
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(head, FictionUi.row(0, 4, 0, 0));

        if (back && !revealed) {
            // written with later chapters in mind: closed until the reader asks
            final TextView note = FictionUi.text(a, a.getString(R.string.canvas_t29_written_ahead, built,
                    chapters.label(where.reader)), CanvasLook.TEXT_TITLE, 400, CanvasLook.ink2());
            note.setLineSpacing(CanvasLook.dp(3), 1f);
            content.addView(note, FictionUi.row(0, 14, 0, 0));
            final TextView show = FictionUi.link(a, a.getString(R.string.canvas_t29_show_anyway, built));
            show.setOnClickListener(v -> {
                revealed = true;
                fill();
            });
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.target());
            content.addView(show, lp);
        } else {
            if (!person.asOf.isEmpty() || !person.role.isEmpty()) {
                // the chapter's label may be a title of the book: never put in capitals
                content.addView(FictionUi.mono(a, a.getString(R.string.canvas_t29_as_of, built), CanvasLook.TEXT_LABEL,
                        CanvasLook.accent(), true), FictionUi.row(0, 14, 0, 0));
                final TextView asOf = FictionUi.text(a, person.asOf.isEmpty() ? person.role : person.asOf,
                        CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
                asOf.setLineSpacing(CanvasLook.dp(4), 1f);
                asOf.setTextIsSelectable(true);
                content.addView(asOf, FictionUi.row(0, 4, 0, 0));
            }
            if (!person.now.isEmpty()) {
                // "right now" is only now for a reader at the place the cast was built for
                content.addView(FictionUi.mono(a, where.state == FictionWhere.AT
                                ? a.getString(R.string.canvas_t29_right_now)
                                : a.getString(R.string.canvas_t29_then, built), CanvasLook.TEXT_LABEL, CanvasLook.accent(), true),
                        FictionUi.row(0, 12, 0, 0));
                final TextView now = FictionUi.text(a, person.now, CanvasLook.TEXT_BODY, 400, CanvasLook.ink());
                now.setLineSpacing(CanvasLook.dp(4), 1f);
                content.addView(now, FictionUi.row(0, 4, 0, 0));
            }
            if (where.state == FictionWhere.PAST) {
                content.addView(FictionUi.text(a, a.getString(R.string.canvas_t29_card_behind, built),
                        CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()), FictionUi.row(0, 10, 0, 0));
            }
        }

        // Spoiler shield: nothing past your page. 23 chapters hidden.
        final TextView shield = FictionUi.text(a, shieldLine(), CanvasLook.TEXT_SMALL, 500, CanvasLook.ink2());
        shield.setPadding(CanvasLook.dp(12), CanvasLook.dp(8), CanvasLook.dp(12), CanvasLook.dp(8));
        shield.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP)));
        content.addView(shield, FictionUi.row(0, 14, 0, 0));

        if (storyOpen) {
            story();
        }

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView story = FictionUi.button(a, a.getString(FictionCast.FEMALE.equals(person.gender)
                ? R.string.canvas_t29_her_story : FictionCast.MALE.equals(person.gender)
                ? R.string.canvas_t29_his_story : R.string.canvas_t29_their_story), false);
        story.setOnClickListener(v -> {
            storyOpen = !storyOpen;
            fill();
        });
        buttons.addView(story, new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1));
        final TextView whosWho = FictionUi.button(a, a.getString(R.string.canvas_t29_whos_who), true);
        whosWho.setOnClickListener(v -> {
            dialog.dismiss();
            if (!fromScreen) {
                FictionScreen.show(a, dc);
            }
        });
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(whosWho, right);
        content.addView(buttons, FictionUi.row(0, 14, 0, 0));
    }

    /** The person's stored moments, each under its chapter; only those the reader may see. */
    private void story() {
        final List<FictionCast.Moment> moments = FictionView.moments(cast, chapters, where, person);
        if (moments.isEmpty()) {
            content.addView(FictionUi.text(a, a.getString(R.string.canvas_t29_no_story), CanvasLook.TEXT_SMALL, 400,
                    CanvasLook.muted()), FictionUi.row(0, 12, 0, 0));
            return;
        }
        content.addView(FictionUi.mono(a, a.getString(R.string.canvas_t29_story_so_far), CanvasLook.TEXT_LABEL,
                CanvasLook.accent(), true), FictionUi.row(0, 14, 0, 0));
        String before = null;
        for (FictionCast.Moment m : moments) {
            final String chapter = chapters.label(chapters.indexOf(m.key, m.ch, cast.mark.chapters));
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            // the chapter once, beside the first of its lines
            final TextView ch = FictionUi.mono(a, chapter.equals(before) ? "" : chapter, 10, CanvasLook.muted(), false);
            ch.setMaxLines(2);
            row.addView(ch, new LinearLayout.LayoutParams(CanvasLook.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            final TextView line = FictionUi.text(a, m.line, CanvasLook.TEXT_TITLE, 400, CanvasLook.ink());
            line.setLineSpacing(CanvasLook.dp(2), 1f);
            row.addView(line, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            content.addView(row, FictionUi.row(0, 6, 0, 0));
            before = chapter;
        }
    }

    private String shieldLine() {
        final int page = Math.max(0, readerEnd - 1);
        final String hidden;
        if (chapters.hasToc()) {
            final int n = chapters.hiddenAfter(page);
            hidden = n <= 0 ? "" : a.getResources().getQuantityString(R.plurals.canvas_t29_chapters_hidden, n, n);
        } else {
            final int n = Math.max(0, chapters.pages() - readerEnd);
            hidden = n <= 0 ? "" : a.getResources().getQuantityString(R.plurals.canvas_t29_pages_hidden, n, n);
        }
        final String shield = a.getString(R.string.canvas_t29_shield);
        return hidden.isEmpty() ? shield : shield + " " + hidden;
    }
}
