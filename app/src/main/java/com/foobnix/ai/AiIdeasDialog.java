package com.foobnix.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ideas of a chapter, the summary layer of its marks (design-2026-10-21-layered-marks.md):
 * indented under the idea they support, in their category's color; a tap goes to the idea's first
 * mark. Ideas whose words were not found in the book are listed as not located.
 */
public final class AiIdeasDialog {

    /** Deepest indentation: deeper ideas line up with it. */
    private static final int MAX_DEPTH = 3;

    private AiIdeasDialog() {
    }

    /** The ideas of the chapter at the reader's page. */
    public static void showHere(Activity a, DocumentController dc) {
        if (dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final BookChapters.Range known = AiJobs.knownChapter(t);
        if (known != null) {
            showKey(a, dc, known.key);
            return;
        }
        new Thread(() -> {
            String key = null;
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    key = BookChapters.chapterAt(AiJobs.toc(t, text), t.page, text.pageCount(), BookChapters.BLOCK_PAGES).key;
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final String found = key;
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> showKey(a, dc, found));
            }
        }, "@T AI ideas").start();
    }

    /** The ideas of a chapter by its key (a study chapter's key, or the key passages' one). */
    public static void showKey(Activity a, DocumentController dc, String key) {
        final AiStore.Chapter chapter = key == null ? null : AiHighlights.get().data().chapter(key);
        if (chapter == null || chapter.ideas.isEmpty()) {
            Toast.makeText(a, R.string.ai_ideas_none, Toast.LENGTH_LONG).show();
            return;
        }
        show(a, dc, chapter);
    }

    public static void show(Activity a, DocumentController dc, AiStore.Chapter chapter) {
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = Dips.dpToPx(12);
        list.setPadding(pad, pad / 2, pad, pad);

        final TextView counts = text(a, 13);
        counts.setAlpha(0.75f);
        final int unmarked = chapter.notLocated();
        counts.setText(a.getString(R.string.ai_ideas_counts, chapter.ideas.size(), unmarked)
                + (chapter.incomplete ? "\n" + a.getString(R.string.ai_ideas_incomplete) : ""));
        list.addView(counts);

        final AlertDialog[] dialog = new AlertDialog[1];
        final Map<Integer, Integer> depth = new HashMap<>();
        for (AiStore.Idea idea : chapter.ideas) {
            final Integer up = idea.parent >= 0 ? depth.get(idea.parent) : null;
            final int d = up == null ? 0 : Math.min(MAX_DEPTH, up + 1);
            depth.put(idea.id, d);

            final TextView row = text(a, 15);
            row.setPadding(Dips.dpToPx(4 + 16 * d), Dips.dpToPx(6), Dips.dpToPx(4), Dips.dpToPx(6));
            final SpannableStringBuilder s = new SpannableStringBuilder("● ");
            s.setSpan(new ForegroundColorSpan(idea.category.color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append(idea.text);
            if (!idea.marked) {
                s.append("  (").append(a.getString(R.string.ai_not_located)).append(')');
                row.setAlpha(0.55f);
            } else {
                final AiStore.Quote first = firstMark(chapter, idea.id);
                if (first != null) {
                    row.setBackgroundResource(R.drawable.bg_round_press);
                    row.setOnClickListener(v -> {
                        if (dialog[0] != null) {
                            dialog[0].dismiss();
                        }
                        AiJump.toQuote(dc, first.text, first.page, -1, -1);
                    });
                }
            }
            row.setText(s);
            list.addView(row);
        }

        final ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        dialog[0] = new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.ai_ideas_title, chapter.title))
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    /** The idea's mark nearest the chapter's start: a sentence or a paragraph, whichever comes first. */
    static AiStore.Quote firstMark(AiStore.Chapter chapter, int idea) {
        AiStore.Quote first = null;
        for (List<AiStore.Quote> quotes : java.util.Arrays.asList(chapter.quotes, chapter.paragraphs)) {
            for (AiStore.Quote q : quotes) {
                if (q.idea == idea && (first == null || q.page < first.page)) {
                    first = q;
                }
            }
        }
        return first;
    }

    private static TextView text(Activity a, int sp) {
        final TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.1f);
        t.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        t.setVisibility(View.VISIBLE);
        return t;
    }
}
