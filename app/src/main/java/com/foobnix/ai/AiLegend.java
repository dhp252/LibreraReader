package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;

/**
 * What the AI highlight colors mean (the legend of the ✦ menu, the name shown on a tap) and the
 * reader's goal for a book, which the "apply" category is judged against.
 */
public final class AiLegend {

    private AiLegend() {
    }

    public static int nameOf(AiCategory category) {
        switch (category) {
            case EVIDENCE:
                return R.string.ai_cat_evidence;
            case CONCEPT:
                return R.string.ai_cat_concept;
            case CAVEAT:
                return R.string.ai_cat_caveat;
            case APPLY:
                return R.string.ai_cat_apply;
            case EXAMPLE:
                return R.string.ai_cat_example;
            default:
                return R.string.ai_cat_main;
        }
    }

    private static int meaningOf(AiCategory category) {
        switch (category) {
            case EVIDENCE:
                return R.string.ai_cat_evidence_desc;
            case CONCEPT:
                return R.string.ai_cat_concept_desc;
            case CAVEAT:
                return R.string.ai_cat_caveat_desc;
            case APPLY:
                return R.string.ai_cat_apply_desc;
            case EXAMPLE:
                return R.string.ai_cat_example_desc;
            default:
                return R.string.ai_cat_main_desc;
        }
    }

    /** The colors: the reader's yellow first, then the AI's categories. */
    public static void show(Activity a) {
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(16);
        list.setPadding(pad, pad / 2, pad, 0);
        addRow(list, AiCategory.READER_YELLOW, a.getString(R.string.ai_cat_yours), a.getString(R.string.ai_cat_yours_desc));
        for (AiCategory category : AiCategory.values()) {
            addRow(list, category.color, a.getString(nameOf(category)), a.getString(meaningOf(category)));
        }
        new AlertDialog.Builder(a)
                .setTitle(R.string.ai_colors)
                .setView(list)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private static void addRow(LinearLayout list, int color, String name, String meaning) {
        final LinearLayout row = new LinearLayout(list.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Dips.dpToPx(6), 0, Dips.dpToPx(6));

        final View swatch = new View(list.getContext());
        final GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(Dips.dpToPx(3));
        fill.setColor(color);
        swatch.setBackground(fill);
        final int size = Dips.dpToPx(22);
        row.addView(swatch, new LinearLayout.LayoutParams(size, size));

        final TextView text = new TextView(list.getContext());
        text.setPadding(Dips.dpToPx(12), 0, 0, 0);
        text.setText(name + " — " + meaning);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        list.addView(row);
    }

    /**
     * Asks what the reader wants from the book, keeps the answer (empty when skipped) with the
     * book's AI data, then runs {@code then} (null: nothing more).
     */
    public static void askGoal(final Activity a, final String bookName, String current, final Runnable then) {
        final EditText input = new EditText(a);
        input.setHint(R.string.ai_goal_hint);
        input.setText(current == null ? "" : current);
        input.setSelection(input.length());
        final LinearLayout box = new LinearLayout(a);
        final int pad = Dips.dpToPx(20);
        box.setPadding(pad, Dips.dpToPx(8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(a)
                .setTitle(R.string.ai_goal_question)
                .setMessage(R.string.ai_goal_why)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> saveGoal(a, bookName, input.getText().toString().trim(), then))
                .setNegativeButton(R.string.ai_skip, (d, w) -> saveGoal(a, bookName, current == null ? "" : current, then))
                .show();
    }

    private static void saveGoal(final Activity a, final String bookName, final String goal, final Runnable then) {
        new Thread(() -> {
            try {
                final AiStore.Book book = AiStore.get().update(bookName, b -> b.goal = goal);
                AiHighlights.get().set(bookName, book);
            } catch (Exception e) {
                LOG.e(e);
            }
            if (then != null) {
                a.runOnUiThread(then);
            }
        }, "@T AI goal").start();
    }
}
