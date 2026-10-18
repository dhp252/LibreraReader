package com.foobnix.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Dips;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.List;

/**
 * After a chapter: its questions with a box each, "Anything else you remember?", and Check. The
 * feedback streams in under them; once checked it stays (✓ / ◐ / ✗ per answer, the page each
 * points to). What is typed is kept as it is typed. Without answers Check is "Add to map".
 */
public final class StudyAnswerDialog implements StudyJobs.CheckScreen {

    private static final int RIGHT = 0xff5fb236;
    private static final int PARTLY = 0xfff4a300;
    private static final int MISSED = 0xffff6666;
    private static final long SAVE_AFTER_MS = 800;

    private final Activity a;
    private final StudySession s;
    private final BookChapters.Range range;
    private final StudyBook.Chapter chapter;
    private final List<EditText> answers = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable save = this::saveDraft;
    private AlertDialog dialog;
    private EditText recall;
    private ProgressBar progress;
    private TextView status;
    private TextView feedback;
    private StudyJobs.CheckJob job;

    private StudyAnswerDialog(Activity a, StudySession s, BookChapters.Range range, StudyBook.Chapter chapter) {
        this.a = a;
        this.s = s;
        this.range = range;
        this.chapter = chapter;
    }

    public static void show(Activity a, StudySession s, BookChapters.Range range) {
        final StudyBook.Chapter c = s == null || range == null ? null : s.book().chapter(range.key);
        if (c == null) {
            return;
        }
        new StudyAnswerDialog(a, s, range, c).open();
    }

    private void open() {
        final ScrollView scroll = new ScrollView(a);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(20);
        box.setPadding(pad, Dips.dpToPx(8), pad, Dips.dpToPx(8));
        scroll.addView(box);

        final TextWatcher typed = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence t, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                handler.removeCallbacks(save);
                handler.postDelayed(save, SAVE_AFTER_MS);
                updateButton();
            }
        };
        for (int i = 0; i < chapter.questions.size(); i++) {
            final TextView q = new TextView(a);
            q.setText((i + 1) + ". " + chapter.questions.get(i));
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setPadding(0, Dips.dpToPx(8), 0, 0);
            box.addView(q);
            final EditText answer = input(i < chapter.answers.size() ? chapter.answers.get(i) : "", R.string.study_your_answer);
            answer.addTextChangedListener(typed);
            answers.add(answer);
            box.addView(answer);
        }
        final TextView more = new TextView(a);
        more.setText(R.string.study_anything_else);
        more.setTypeface(Typeface.DEFAULT_BOLD);
        more.setPadding(0, Dips.dpToPx(10), 0, 0);
        box.addView(more);
        recall = input(chapter.recall, R.string.study_optional);
        recall.addTextChangedListener(typed);
        box.addView(recall);

        final LinearLayout working = new LinearLayout(a);
        working.setOrientation(LinearLayout.HORIZONTAL);
        progress = new ProgressBar(a);
        progress.setVisibility(View.GONE);
        working.addView(progress, new LinearLayout.LayoutParams(Dips.dpToPx(24), Dips.dpToPx(24)));
        status = new TextView(a);
        status.setPadding(Dips.dpToPx(8), 0, 0, 0);
        working.addView(status);
        working.setPadding(0, Dips.dpToPx(10), 0, 0);
        box.addView(working);

        feedback = new TextView(a);
        feedback.setTextSize(15);
        feedback.setPadding(0, Dips.dpToPx(6), 0, 0);
        feedback.setMovementMethod(LinkMovementMethod.getInstance());
        box.addView(feedback);
        if (chapter.checked > 0) {
            showStored(chapter);
        }

        dialog = new AlertDialog.Builder(a)
                .setTitle(s.title(range))
                .setView(scroll)
                .setPositiveButton(R.string.study_check, null)
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> check());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (job != null) {
                    job.cancel();
                    job = null;
                    progress.setVisibility(View.GONE);
                    status.setText("");
                    updateButton();
                } else {
                    dialog.dismiss();
                }
            });
            updateButton();
        });
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(save);
            saveDraft();
            if (job != null) {
                // the check goes on and is kept; nothing more to show here
                job.setScreen(null);
            }
        });
        dialog.show();
    }

    private EditText input(String text, int hint) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(1);
        e.setMaxLines(6);
        e.setHint(hint);
        e.setText(text == null ? "" : text);
        return e;
    }

    private List<String> typedAnswers() {
        final List<String> out = new ArrayList<>();
        for (EditText e : answers) {
            out.add(e.getText().toString().trim());
        }
        return out;
    }

    private boolean anyTyped() {
        if (recall != null && !recall.getText().toString().trim().isEmpty()) {
            return true;
        }
        for (String t : typedAnswers()) {
            if (!t.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void updateButton() {
        if (dialog == null) {
            return;
        }
        final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            positive.setEnabled(job == null);
            positive.setText(anyTyped() ? (chapter.checked > 0 ? R.string.study_check_again : R.string.study_check)
                    : R.string.study_add_to_map);
        }
        if (negative != null) {
            negative.setText(job != null ? R.string.cancel : R.string.close);
        }
    }

    private void saveDraft() {
        if (job != null) {
            return;
        }
        final List<String> typed = typedAnswers();
        final String more = recall.getText().toString().trim();
        if (typed.equals(trimmed(chapter.answers)) && more.equals(chapter.recall == null ? "" : chapter.recall.trim())) {
            return;
        }
        chapter.answers.clear();
        chapter.answers.addAll(typed);
        chapter.recall = more;
        final String key = range.key;
        s.change(b -> {
            final StudyBook.Chapter c = b.chapter(key);
            if (c != null) {
                c.answers.clear();
                c.answers.addAll(typed);
                c.recall = more;
            }
        });
    }

    private static List<String> trimmed(List<String> list) {
        final List<String> out = new ArrayList<>();
        for (String t : list) {
            out.add(t == null ? "" : t.trim());
        }
        return out;
    }

    private void check() {
        handler.removeCallbacks(save);
        saveDraft();
        progress.setVisibility(View.VISIBLE);
        feedback.setText("");
        job = new StudyJobs.CheckJob(a, AiJobs.Target.of(s.controller()), range, chapter.questions, typedAnswers(),
                recall.getText().toString().trim(), this);
        updateButton();
        job.start();
    }

    // ------------------------------------------------------------------ CheckScreen

    @Override
    public void onStatus(String text) {
        status.setText(text);
    }

    @Override
    public void onText(String visible) {
        status.setText("");
        feedback.setText(visible);
    }

    @Override
    public void onDone(StudyBook.Chapter done) {
        job = null;
        progress.setVisibility(View.GONE);
        status.setText("");
        chapter.checked = done.checked;
        showStored(done);
        updateButton();
    }

    @Override
    public void onError(String message) {
        job = null;
        progress.setVisibility(View.GONE);
        status.setText(message);
        updateButton();
    }

    @Override
    public void onNeedSettings() {
        job = null;
        progress.setVisibility(View.GONE);
        updateButton();
        AiSettingsDialog.show(a, null);
    }

    /** A checked chapter: each answer's verdict and note, its page, what recall left out, the map's ideas. */
    private void showStored(StudyBook.Chapter c) {
        final SpannableStringBuilder out = new SpannableStringBuilder();
        final StudyBook.Feedback f = c.feedback;
        if (f != null && !f.items.isEmpty()) {
            for (StudyBook.Item item : f.items) {
                final String mark = StudyBook.Item.RIGHT.equals(item.verdict) ? "✓" : StudyBook.Item.MISSED.equals(item.verdict) ? "✗" : "◐";
                final int color = StudyBook.Item.RIGHT.equals(item.verdict) ? RIGHT : StudyBook.Item.MISSED.equals(item.verdict) ? MISSED : PARTLY;
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                final int start = out.length();
                out.append(mark).append(' ').append(String.valueOf(item.question + 1)).append(". ");
                out.setSpan(new ForegroundColorSpan(color), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append(item.note);
                if (item.quote != null && !item.quote.isEmpty()) {
                    out.append("\n“").append(item.quote).append("”");
                    link(out, item.quote, item.page);
                }
            }
        } else if (f != null && !f.text.isEmpty()) {
            out.append(f.text);
        }
        if (f != null && !f.recall.isEmpty()) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            final int start = out.length();
            out.append(a.getString(R.string.study_recall_left_out)).append(' ');
            out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.append(f.recall);
        }
        final int ideas = StudyBook.Node.count(c.branch);
        if (ideas > 0) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(a.getString(R.string.study_added_to_map, ideas));
        }
        feedback.setText(out);
    }

    /** " p. 14" after a quote: a tap goes there. */
    private void link(SpannableStringBuilder out, String quote, int page) {
        out.append("  ");
        final int start = out.length();
        out.append(page >= 0 ? a.getString(R.string.study_page_n, page + 1) : a.getString(R.string.study_find));
        out.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                dialog.dismiss();
                s.jumpTo(quote, page, range);
            }
        }, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
