package com.foobnix.annotation;

import android.graphics.RectF;

import com.foobnix.ai.QuoteMatcher;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Marks made from what the reader does: the selected text (toolbar, stylus highlighter, selection
 * menu) and pen strokes. A fixed layout keeps the lines where they are; a reflowing book keeps the
 * words and the letters around them ({@link TextAnchor}).
 */
public final class MarkMaker {

    private MarkMaker() {
    }

    /**
     * A mark of the text selected now; null when nothing is selected, or in a reflowing book when
     * the selection holds no letters (a picture).
     */
    public static Mark fromSelection(DocumentController dc, String kind, String color) {
        final SortedMap<Integer, List<TextWord>> selected = dc.selectedWords();
        if (selected.isEmpty()) {
            return null;
        }
        final boolean reflowing = dc.isTextFormat();
        final Mark mark = Mark.create(kind, color);
        final List<String> quote = new ArrayList<>();
        final int firstPage = selected.firstKey();
        final int lastPage = selected.lastKey();
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        for (Map.Entry<Integer, List<TextWord>> entry : selected.entrySet()) {
            final int page = entry.getKey();
            final List<TextWord> pageWords = TTSHighlight.words(dc.pageText(page));
            final List<TextWord> chosen = inReadingOrder(entry.getValue(), pageWords);
            if (chosen.isEmpty()) {
                continue;
            }
            for (TextWord word : chosen) {
                quote.add(word.getWord());
            }
            if (!reflowing) {
                final List<RectF> lines = TTSHighlight.toBands(chosen);
                final float[] rects = new float[lines.size() * 4];
                for (int i = 0; i < lines.size(); i++) {
                    final RectF line = lines.get(i);
                    rects[i * 4] = line.left;
                    rects[i * 4 + 1] = line.top;
                    rects[i * 4 + 2] = line.right;
                    rects[i * 4 + 3] = line.bottom;
                }
                if (rects.length > 0) {
                    mark.parts.add(new Mark.Part(page, rects));
                }
                continue;
            }
            if (page == firstPage) {
                final int first = indexOf(pageWords, chosen.get(0));
                before = strings(pageWords.subList(0, Math.max(0, first)));
                if (letters(before) < TextAnchor.CONTEXT) {
                    // the end of the page before, when the view has it
                    final List<String> earlier = strings(TTSHighlight.words(dc.pageText(page - 1)));
                    earlier.addAll(before);
                    before = earlier;
                }
            }
            if (page == lastPage) {
                final int last = indexOf(pageWords, chosen.get(chosen.size() - 1));
                after = last < 0 ? new ArrayList<String>() : strings(pageWords.subList(last + 1, pageWords.size()));
                if (letters(after) < TextAnchor.CONTEXT) {
                    after.addAll(strings(TTSHighlight.words(dc.pageText(page + 1))));
                }
            }
        }
        mark.quote = QuoteMatcher.join(quote);
        if (reflowing) {
            if (TextAnchor.key(mark.quote).isEmpty()) {
                return null;
            }
            mark.anchor = TextAnchor.of(before, after, firstPage, dc.docPageCount());
            return mark;
        }
        return mark.parts.isEmpty() ? null : mark;
    }

    /**
     * A pen stroke as one mark: runs of points on one page drawn at one width.
     *
     * @param onPages  the stroke's points on the pages (DocumentController.toPageUnits), null off a page
     * @param steps    the width step of each point, or null for one width
     * @param widthAt  the line width of a step, in page units
     */
    public static Mark fromStroke(DocumentController dc, List<DocumentController.PagePoint> onPages, int[] steps,
                                  StepWidth widthAt, String color) {
        final Mark mark = Mark.create(Mark.INK, color);
        int start = 0;
        while (start < onPages.size()) {
            final DocumentController.PagePoint first = onPages.get(start);
            final int step = steps == null ? 0 : steps[start];
            int end = start;
            // a run: the same page and the same width
            while (end + 1 < onPages.size() && samePage(first, onPages.get(end + 1))
                    && (steps == null || steps[end + 1] == step)) {
                end++;
            }
            if (first != null) {
                final float[] size = dc.pageSizeUnits(first.page);
                // the next run starts where this one ended, so the line has no gap
                final int to = end + 1 < onPages.size() && samePage(first, onPages.get(end + 1)) ? end + 1 : end;
                if (size != null && size[0] > 0 && size[1] > 0 && to > start) {
                    final float[] points = new float[(to - start + 1) * 2];
                    for (int i = start; i <= to; i++) {
                        points[(i - start) * 2] = onPages.get(i).point.x / size[0];
                        points[(i - start) * 2 + 1] = onPages.get(i).point.y / size[1];
                    }
                    mark.ink.add(new Mark.Ink(first.page, widthAt.width(step) / size[0], points));
                }
            }
            start = end + 1;
        }
        return mark.ink.isEmpty() ? null : mark;
    }

    /** The line width of a pressure step, in page units. */
    public interface StepWidth {
        float width(int step);
    }

    private static boolean samePage(DocumentController.PagePoint a, DocumentController.PagePoint b) {
        return a == null ? b == null : b != null && a.page == b.page;
    }

    /** The selected words in the page's reading order (the selection may hold them in tap order). */
    static List<TextWord> inReadingOrder(List<TextWord> selected, List<TextWord> pageWords) {
        final List<TextWord> sorted = new ArrayList<>(selected);
        if (pageWords.isEmpty()) {
            return sorted;
        }
        final java.util.IdentityHashMap<TextWord, Integer> order = new java.util.IdentityHashMap<>();
        for (TextWord word : sorted) {
            final int at = indexOf(pageWords, word);
            order.put(word, at < 0 ? Integer.MAX_VALUE : at);
        }
        Collections.sort(sorted, (a, b) -> Integer.compare(order.get(a), order.get(b)));
        return sorted;
    }

    /** The word's place among the page's words: the same object, else the same word at the same place. */
    static int indexOf(List<TextWord> pageWords, TextWord word) {
        for (int i = 0; i < pageWords.size(); i++) {
            if (pageWords.get(i) == word) {
                return i;
            }
        }
        for (int i = 0; i < pageWords.size(); i++) {
            final TextWord w = pageWords.get(i);
            if (w.left == word.left && w.top == word.top && w.right == word.right && w.bottom == word.bottom
                    && w.getWord() != null && w.getWord().equals(word.getWord())) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> strings(List<TextWord> words) {
        final List<String> out = new ArrayList<>(words.size());
        for (TextWord word : words) {
            out.add(word.getWord());
        }
        return out;
    }

    private static int letters(List<String> words) {
        int n = 0;
        for (String w : words) {
            n += TextAnchor.key(w).length();
        }
        return n;
    }
}
