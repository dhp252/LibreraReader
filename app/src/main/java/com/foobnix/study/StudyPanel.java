package com.foobnix.study;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiLegend;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Dips;
import com.foobnix.annotation.MarksExport;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.view.DragingPopup;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;

import java.util.List;

/**
 * The Study panel, floating over the book like the TOC: the book's thesis and goal, how the
 * chapters are labelled and the time that saves, and every chapter with its label (a tap on it
 * changes it), time and state; a tap on a chapter shows its card and actions.
 */
public final class StudyPanel {

    private static final String TAG = "StudyPanel";

    private final Activity a;
    private final DocumentController dc;
    private final StudySession s;
    private DragingPopup popup;
    private final Runnable listener = this::refresh;
    private ScrollView scroll;
    /** The chapter whose details are open. */
    private String expanded;

    private StudyPanel(Activity a, DocumentController dc, StudySession s) {
        this.a = a;
        this.dc = dc;
        this.s = s;
    }

    /** The panel of the open book; a book not studied yet starts its preview instead. */
    public static void show(Activity a, DocumentController dc) {
        final StudySession s = StudySession.of(dc);
        if (s == null || !s.book().on || !s.book().hasPreview()) {
            StudyStart.begin(a, dc, false);
            return;
        }
        final FrameLayout anchor = a.findViewById(R.id.anchor);
        if (anchor == null) {
            return;
        }
        final StudyPanel panel = new StudyPanel(a, dc, s);
        final BookChapters.Range here = s.currentChapter();
        panel.expanded = here == null ? null : here.key;
        panel.popup = new DragingPopup(R.string.study_panel, anchor, 330, 520) {
            @Override
            public View getContentView(LayoutInflater inflater) {
                return panel.build();
            }
        };
        s.addListener(panel.listener);
        panel.popup.setOnCloseListener(() -> s.removeListener(panel.listener));
        panel.popup.show(TAG, true);
    }

    private void refresh() {
        if (popup == null) {
            return;
        }
        if (!popup.isVisible() || !popup.getView().isAttachedToWindow()) {
            // closed, or another panel took the place (they share it)
            s.removeListener(listener);
            return;
        }
        final int y = scroll == null ? 0 : scroll.getScrollY();
        popup.realod();
        if (scroll != null) {
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    private View build() {
        final StudyBook book = s.book();
        scroll = new ScrollView(a);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(12);
        list.setPadding(pad, pad / 2, pad, pad);
        scroll.addView(list);

        if (!book.thesis.isEmpty()) {
            final TextView thesis = text(book.thesis, 15, true);
            list.addView(thesis);
        }
        final TextView goal = text(a.getString(R.string.study_goal, book.goal.isEmpty() ? a.getString(R.string.study_no_goal) : book.goal) + "  ✎", 14, false);
        goal.setPadding(0, Dips.dpToPx(6), 0, 0);
        goal.setOnClickListener(v -> editGoal());
        list.addView(goal);

        final StudyTime.Plan plan = StudyTime.plan(chapters(book), ReadingSpeed.wpm(s.bookName()));
        list.addView(text(a.getString(R.string.study_counts, plan.read, plan.skim, plan.skip), 14, false));
        list.addView(text(a.getString(R.string.study_time_saved, s.duration(plan.minutes), s.duration(plan.fullMinutes)), 14, false));

        final LinearLayout actions = row();
        actions.addView(action(a.getString(R.string.study_map), () -> {
            popup.closeDialog();
            StudyMap.show(a, s, false);
        }));
        actions.addView(action(a.getString(R.string.study_more), this::more));
        list.addView(actions);

        final List<BookChapters.Range> chapters = s.studyChapters();
        final BookChapters.Range here = s.currentChapter();
        for (int i = 0; i < chapters.size(); i++) {
            final BookChapters.Range range = chapters.get(i);
            final StudyBook.Chapter c = book.chapter(range.key);
            if (c != null) {
                list.addView(chapterRow(i + 1, range, c, here != null && here.key.equals(range.key)));
                if (range.key.equals(expanded)) {
                    list.addView(details(range, c, here != null && here.key.equals(range.key)));
                }
            }
        }
        if (chapters.isEmpty()) {
            list.addView(text(a.getString(R.string.study_loading_chapters), 14, false));
        }
        return scroll;
    }

    private java.util.Collection<StudyBook.Chapter> chapters(StudyBook book) {
        return book.chapters.values();
    }

    private View chapterRow(int n, BookChapters.Range range, StudyBook.Chapter c, boolean here) {
        final LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Dips.dpToPx(4), 0, Dips.dpToPx(4));
        ripple(row);
        row.setOnClickListener(v -> {
            expanded = range.key.equals(expanded) ? null : range.key;
            refresh();
        });

        final TextView tag = text(StudyCardView.mark(c.label), 20, false);
        tag.setTextColor(StudyCardView.color(c.label));
        tag.setPadding(0, 0, Dips.dpToPx(10), 0);
        tag.setContentDescription(labelName(c.label));
        tag.setOnClickListener(v -> s.setLabel(range.key, nextLabel(c.label)));
        row.addView(tag);

        final TextView title = text(n + ". " + s.title(range), 14, here);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final int minutes = s.minutes(c);
        final TextView time = text(minutes == 0 ? "—" : s.duration(minutes), 13, false);
        time.setPadding(Dips.dpToPx(8), 0, Dips.dpToPx(8), 0);
        row.addView(time);

        // ✓ checked, ▸ being read, ? done but not answered yet
        final String state = c.checked > 0 ? "✓" : here ? "▸" : c.afterShown > 0 && !StudyBook.SKIP.equals(c.label) ? "?" : "";
        final TextView st = text(state, 14, true);
        st.setMinWidth(Dips.dpToPx(16));
        row.addView(st);
        return row;
    }

    private View details(BookChapters.Range range, StudyBook.Chapter c, boolean here) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Dips.dpToPx(28), 0, 0, Dips.dpToPx(8));
        if (!c.line.isEmpty()) {
            final TextView line = text(c.line, 14, false);
            line.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
            box.addView(line);
        }
        if (!c.reason.isEmpty()) {
            box.addView(text(labelName(c.label) + " · " + c.reason, 13, false));
        }
        if (!c.questions.isEmpty()) {
            box.addView(text(a.getString(R.string.study_read_for), 14, true));
            for (int i = 0; i < c.questions.size(); i++) {
                box.addView(text((i + 1) + ". " + c.questions.get(i), 14, false));
            }
        }
        final LinearLayout buttons = row();
        if (!here) {
            buttons.addView(action(a.getString(R.string.study_go_to_chapter), () -> s.goTo(range)));
        }
        if (c.feedback != null || !StudyBook.SKIP.equals(c.label) && !c.questions.isEmpty()) {
            buttons.addView(action(a.getString(c.feedback != null ? R.string.study_answers : R.string.study_answer),
                    () -> s.answer(range)));
        }
        if (c.checked > 0) {
            buttons.addView(action(a.getString(R.string.study_rebuild_branch), () -> s.rebuild(range)));
        } else if (StudyBook.SKIP.equals(c.label) || c.questions.isEmpty()) {
            buttons.addView(action(a.getString(R.string.study_add_to_map), () -> s.rebuild(range)));
        }
        buttons.addView(action(a.getString(R.string.study_summary), () -> s.summary(range)));
        buttons.addView(action(a.getString(R.string.ai_key_passages), () -> s.keyPassages(range)));
        final HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(buttons);
        box.addView(hs);
        return box;
    }

    private void more(View anchor) {
        final MyPopupMenu menu = new MyPopupMenu(a, anchor);
        menu.getMenu(R.drawable.glyphicons_193_circle_empty_remove, R.string.study_stop, () -> {
            s.stopStudying();
            popup.closeDialog();
        });
        menu.getMenu(R.drawable.ic_study, R.string.study_redo_preview, () -> {
            popup.closeDialog();
            StudyStart.begin(a, dc, true);
        });
        menu.getMenu(R.drawable.glyphicons_578_share, R.string.study_export_notes,
                () -> MarksExport.start(a, dc.getCurrentBook().getPath(), dc));
        menu.show();
    }

    private void editGoal() {
        final String name = s.bookName();
        new Thread(() -> {
            final String current = AiStore.get().load(name).goal;
            a.runOnUiThread(() -> AiLegend.askGoal(a, name, current, () -> StudyStart.goalChanged(a, dc)));
        }, "@T Study goal").start();
    }

    private String labelName(String label) {
        if (StudyBook.SKIP.equals(label)) {
            return a.getString(R.string.study_label_skip);
        }
        return a.getString(StudyBook.SKIM.equals(label) ? R.string.study_label_skim : R.string.study_label_read);
    }

    private static String nextLabel(String label) {
        if (StudyBook.READ.equals(label)) {
            return StudyBook.SKIM;
        }
        return StudyBook.SKIM.equals(label) ? StudyBook.SKIP : StudyBook.READ;
    }

    private LinearLayout row() {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private interface Action {
        void run(View v);
    }

    private TextView action(String label, Runnable run) {
        return action(label, v -> run.run());
    }

    private TextView action(String label, Action run) {
        final TextView b = text(label, 14, true);
        b.setTextColor(TintUtil.color);
        b.setPadding(0, Dips.dpToPx(8), Dips.dpToPx(18), Dips.dpToPx(8));
        ripple(b);
        b.setOnClickListener(run::run);
        return b;
    }

    private void ripple(View v) {
        final TypedValue ripple = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        v.setBackgroundResource(ripple.resourceId);
    }

    private TextView text(String s, int sp, boolean bold) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }
}
