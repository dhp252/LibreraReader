package com.foobnix.bookmap;

import com.foobnix.ai.AiStore;
import com.foobnix.ai.QuoteMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Anchors the model's moments to the book: each quote is looked for in the chapter's words (as
 * key passages are), stored as the book's own words with the page it starts on and where it
 * stands in the chapter. A moment whose quote is not in the chapter is left out.
 */
public final class MapPlace {

    private MapPlace() {
    }

    /** The placed moments, and how many were left out. */
    public static final class Placed {
        public final List<MapBook.Moment> moments = new ArrayList<>();
        public int dropped;
    }

    /**
     * @param words    the chapter's words in reading order
     * @param wordPage the 0-based page of each word
     */
    public static Placed place(List<String> words, List<Integer> wordPage, List<MapPrompts.Draft> drafts) {
        final Placed out = new Placed();
        final QuoteMatcher.Words prepared = new QuoteMatcher.Words(words);
        final int count = Math.max(1, words.size());
        for (MapPrompts.Draft d : drafts) {
            final int[] at = d.quote.isEmpty() ? null : QuoteMatcher.locate(prepared, d.quote);
            if (at == null) {
                out.dropped++;
                continue;
            }
            out.moments.add(new MapBook.Moment(d.threads, d.kind, d.label,
                    QuoteMatcher.join(words.subList(at[0], at[1] + 1)), wordPage.get(at[0]), round(at[0] / (double) count)));
        }
        // reading order, so moment ids follow the chapter (a stable sort keeps ties as given)
        Collections.sort(out.moments, (a, b) -> Double.compare(a.at, b.at));
        return out;
    }

    /**
     * An ideas answer as drafts with quotes: each moment takes the first key sentence stored for
     * its idea, so it is placed (and checked) like a chapter's own. A moment of an idea without a
     * stored sentence gets none and is left out by {@link #place}.
     */
    public static List<MapPrompts.Draft> fromIdeas(AiStore.Chapter chapter, List<MapPrompts.Draft> drafts) {
        final List<MapPrompts.Draft> out = new ArrayList<>();
        for (MapPrompts.Draft d : drafts) {
            String quote = "";
            for (AiStore.Quote q : chapter.quotes) {
                if (q.idea == d.idea) {
                    quote = q.text;
                    break;
                }
            }
            out.add(new MapPrompts.Draft(d.threads, d.kind, d.label, quote, d.idea));
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
