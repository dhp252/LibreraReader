package com.foobnix.canvas.dialogs;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A book's info sheet ({@code dialog_file_info.xml}, FileInformationDialog) in the canvas look:
 * its facts, annotation, meta tags, bookmarks and the F-007 Reading section each on a card,
 * the names of facts as JetBrains Mono uppercase labels, the facts in ink (accent where a tap
 * searches by them), the Reading section's chapters with accent bars of their reading time.
 * Nothing happens unless {@link CanvasLook#isOn()}.
 */
public final class CanvasInfo {

    /** The cards holding the Reading section: shown when BookStatsView fills it later. */
    private static final Set<View> READING_CARDS = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private CanvasInfo() {
    }

    /** Restyles the sheet's view before the dialog is built from it. */
    public static void fileInfo(View root) {
        if (root == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            final Tone t = Tone.of(root.getContext());
            final View first = root.findViewById(R.id.rowTitle);
            if (first == null || !(first.getParent() instanceof LinearLayout)) {
                return;
            }
            final LinearLayout content = (LinearLayout) first.getParent();
            head(root, t);

            // the hairline above the annotation: the cards set the blocks apart now
            final View path = root.findViewById(R.id.rowPath);
            final int afterPath = content.indexOfChild(path) + 1;
            if (afterPath > 0 && afterPath < content.getChildCount()) {
                final View rule = content.getChildAt(afterPath);
                if (rule.getClass() == View.class) {
                    rule.setVisibility(View.GONE);
                }
            }

            final LinearLayout facts = card(content, t, R.id.rowTitle, R.id.rowPath);
            if (facts != null) {
                for (int i = 0; i < facts.getChildCount(); i++) {
                    factRow(facts.getChildAt(i), t);
                }
            }
            card(content, t, R.id.rowAnnotation, R.id.expand);
            card(content, t, R.id.metaTags, R.id.metaTagsInfo);
            card(content, t, R.id.bookmarksSection, R.id.bookmarks);
            final LinearLayout reading = card(content, t, R.id.readingStatsSection, R.id.readingStats);
            if (reading != null) {
                READING_CARDS.add(reading);
            }

            final View annotationRow = root.findViewById(R.id.rowAnnotation);
            if (annotationRow instanceof ViewGroup && ((ViewGroup) annotationRow).getChildCount() > 0
                    && ((ViewGroup) annotationRow).getChildAt(0) instanceof TextView) {
                t.label((TextView) ((ViewGroup) annotationRow).getChildAt(0), true);
            }
            for (int id : new int[]{R.id.metaTags, R.id.bookmarksSection, R.id.readingStatsSection}) {
                final View v = root.findViewById(id);
                if (v instanceof TextView) {
                    t.label((TextView) v, true);
                    v.setPadding(0, 0, 0, CanvasLook.dp(6));
                }
            }
            for (int id : new int[]{R.id.metaInfo, R.id.metaTagsInfo, R.id.bookmarks}) {
                final View v = root.findViewById(id);
                if (v instanceof TextView) {
                    t.text((TextView) v, CanvasLook.TEXT_BODY, 400, t.ink());
                    ((TextView) v).setLineSpacing(0, 1.25f);
                }
            }
            for (int id : new int[]{R.id.editTitle, R.id.editAuthor, R.id.editAnnotation, R.id.expand}) {
                final View v = root.findViewById(id);
                if (v instanceof TextView) {
                    quietLink((TextView) v, t);
                }
            }
            for (int id : new int[]{R.id.convertFile, R.id.openWith, R.id.sendFile, R.id.onUpdateMeta, R.id.delete}) {
                final View v = root.findViewById(id);
                if (v instanceof TextView) {
                    CanvasTts.link((TextView) v, t);
                    v.setMinimumHeight(CanvasLook.target());
                    ((TextView) v).setMinHeight(CanvasLook.target());
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void head(View root, Tone t) {
        final View name = root.findViewById(R.id.bookName);
        if (name instanceof TextView) {
            t.text((TextView) name, CanvasLook.TEXT_HEADING, 600, t.ink());
        }
        final View ext = root.findViewById(R.id.bookExt);
        if (ext instanceof TextView) {
            t.label((TextView) ext, false);
        }
        final View close = root.findViewById(R.id.closeInfo);
        if (close instanceof ImageView) {
            ((ImageView) close).setImageTintList(ColorStateList.valueOf(t.ink2()));
            TintUtil.setRingColor(close, t.lineStrong());
        }
    }

    /**
     * Moves the children from {@code fromId} to {@code toId} (inclusive) of {@code content} onto a
     * card standing where they stood; the card shows while any of them does.
     */
    private static LinearLayout card(LinearLayout content, Tone t, int fromId, int toId) {
        final View from = content.findViewById(fromId);
        final View to = content.findViewById(toId);
        final int start = content.indexOfChild(from);
        final int end = content.indexOfChild(to);
        if (start < 0 || end < start) {
            return null;
        }
        final LinearLayout card = new LinearLayout(content.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(t.card());
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(10), CanvasLook.dp(14), CanvasLook.dp(12));
        final List<View> moved = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            moved.add(content.getChildAt(i));
        }
        boolean shown = false;
        for (View v : moved) {
            final ViewGroup.LayoutParams lp = v.getLayoutParams();
            content.removeView(v);
            card.addView(v, lp);
            shown |= v.getVisibility() == View.VISIBLE;
        }
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(12);
        content.addView(card, start, lp);
        card.setVisibility(shown ? View.VISIBLE : View.GONE);
        return card;
    }

    /** A row of facts: its name as a mono label, the fact in ink, searchable facts in accent. */
    private static void factRow(View row, Tone t) {
        if (!(row instanceof LinearLayout) || ((LinearLayout) row).getOrientation() != LinearLayout.HORIZONTAL) {
            return;
        }
        final LinearLayout r = (LinearLayout) row;
        r.setPadding(r.getPaddingLeft(), CanvasLook.dp(6), r.getPaddingRight(), CanvasLook.dp(6));
        for (int i = 0; i < r.getChildCount(); i++) {
            final View v = r.getChildAt(i);
            if (i == 0 && v instanceof TextView) {
                t.label((TextView) v, false);
                if (v.getLayoutParams() != null && v.getLayoutParams().width > 0) {
                    v.getLayoutParams().width = CanvasLook.dp(100);
                }
            } else if (v instanceof TextView && !"textLink".equals(v.getTag())) {
                value((TextView) v, t);
            } else if (v instanceof LinearLayout) {
                // authors, genres, keywords: each one searches the library
                final LinearLayout list = (LinearLayout) v;
                for (int j = 0; j < list.getChildCount(); j++) {
                    if (list.getChildAt(j) instanceof TextView) {
                        value((TextView) list.getChildAt(j), t);
                    }
                }
            }
        }
    }

    private static void value(TextView v, Tone t) {
        t.text(v, CanvasLook.TEXT_BODY, v.hasOnClickListeners() ? 500 : 400, v.hasOnClickListeners() ? t.accent() : t.ink());
    }

    /** A small text button in accent (Edit, More). */
    private static void quietLink(TextView v, Tone t) {
        t.text(v, CanvasLook.TEXT_TITLE, 600, t.accent());
        v.setText(v.getText().toString());
        v.setBackground(t.ripple());
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(CanvasLook.dp(36));
        v.setPadding(CanvasLook.dp(10), CanvasLook.dp(4), CanvasLook.dp(10), CanvasLook.dp(4));
    }

    // ------------------------------------------------------------------ Reading (F-007)

    /** BookStatsView: a chapter's row, with its reading time for the bar. */
    public static void chapter(TextView row, long ms) {
        if (row != null && CanvasLook.isOn()) {
            row.setTag(Long.valueOf(ms));
        }
    }

    /**
     * BookStatsView filled the Reading section: its lines in ink, "Chapters" as a label, each
     * chapter with an accent bar as long as its share of the longest chapter's time.
     */
    public static void reading(View label, LinearLayout box) {
        if (label == null || box == null || !CanvasLook.isOn()) {
            return;
        }
        try {
            final Tone t = Tone.of(box.getContext());
            if (label instanceof TextView) {
                t.label((TextView) label, true);
                label.setPadding(0, 0, 0, CanvasLook.dp(6));
            }
            if (label.getParent() instanceof View && READING_CARDS.contains((View) label.getParent())) {
                ((View) label.getParent()).setVisibility(View.VISIBLE);
            }
            long longest = 0;
            int firstChapter = -1;
            for (int i = 0; i < box.getChildCount(); i++) {
                final Object tag = box.getChildAt(i).getTag();
                if (tag instanceof Long) {
                    longest = Math.max(longest, (Long) tag);
                    if (firstChapter < 0) {
                        firstChapter = i;
                    }
                }
            }
            final List<View> rows = new ArrayList<>();
            for (int i = 0; i < box.getChildCount(); i++) {
                rows.add(box.getChildAt(i));
            }
            for (int i = 0; i < rows.size(); i++) {
                final View v = rows.get(i);
                if (!(v instanceof TextView)) {
                    continue;
                }
                final TextView tv = (TextView) v;
                if (tv.getTag() instanceof Long) {
                    t.text(tv, 13, 400, t.ink());
                    bar(box, tv, longest <= 0 ? 0f : (Long) tv.getTag() / (float) longest, t);
                } else if (i == firstChapter - 1) {
                    t.label(tv, false);
                    tv.setPadding(0, CanvasLook.dp(12), 0, CanvasLook.dp(4));
                } else {
                    t.text(tv, CanvasLook.TEXT_BODY, 400, t.ink());
                    tv.setPadding(0, CanvasLook.dp(2), 0, CanvasLook.dp(2));
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void bar(LinearLayout box, TextView row, float share, Tone t) {
        final int at = box.indexOfChild(row);
        if (at < 0) {
            return;
        }
        box.removeViewAt(at);
        final LinearLayout item = new LinearLayout(box.getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(4));
        item.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout track = new LinearLayout(box.getContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setWeightSum(1f);
        track.setBackground(CanvasParts.round(t.line(), 0, CanvasLook.dp(2)));
        final View fill = new View(box.getContext());
        fill.setBackground(CanvasParts.round(t.accent(), 0, CanvasLook.dp(2)));
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(0.02f, Math.min(1f, share))));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(4));
        lp.topMargin = CanvasLook.dp(4);
        item.addView(track, lp);
        box.addView(item, at);
    }
}
