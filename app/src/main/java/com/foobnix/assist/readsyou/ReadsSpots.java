package com.foobnix.assist.readsyou;

import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.zoom.ZoomData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a marked spot (a page the reader struggled or drifted on, or asked for help on) says, from
 * what is already stored for that page: a key-passage idea marked on it, else the one line of the
 * zoom section it is in. A spot with neither has no idea; nothing is asked of the AI. No Android.
 */
public final class ReadsSpots {

    public static final class Result {
        /** The ideas of the spots that have one, each once, in page order. */
        public final List<Recall.Idea> ideas = new ArrayList<>();
        /** Spots in the chapter. */
        public int spots;
        /** Spots with no stored idea for their page. */
        public int without;
    }

    private ReadsSpots() {
    }

    /**
     * The stored idea of a page, or null.
     *
     * @param page         the page in the chapter, from 0
     * @param chapterPages the chapter's pages in the reader's layout
     * @param chapterFrom  the chapter's first page in the book, 0-based
     * @param passages     the chapter's key-passage ideas (their pages are the book's), or null
     * @param zoom         the chapter's zoom levels, or null
     * @return the idea, with the passage and the book page a tap goes to; its id is the key
     * passage's own, or -1 for a zoom line
     */
    public static Recall.Idea ideaAt(int page, int chapterPages, int chapterFrom, List<Recall.Idea> passages,
                                     ZoomData zoom) {
        final int bookPage = chapterFrom + page;
        if (passages != null) {
            for (Recall.Idea idea : passages) {
                for (int i = 0; i < idea.pages.size(); i++) {
                    if (idea.pages.get(i) == bookPage && !idea.text.isEmpty()) {
                        final Recall.Idea out = new Recall.Idea(idea.id, idea.text);
                        out.quotes.add(idea.quotes.get(i));
                        out.pages.add(bookPage);
                        return out;
                    }
                }
            }
        }
        if (zoom != null && chapterPages > 0) {
            final int at = zoom.sectionAt((float) page / chapterPages);
            if (at >= 0 && at < zoom.sections.size()) {
                final ZoomData.Section s = zoom.sections.get(at);
                // the section must start at or before the page: a later one says nothing about it
                final float starts = zoom.at(s.page);
                if (s.line != null && !s.line.trim().isEmpty() && starts >= 0
                        && starts <= (float) page / chapterPages + 0.0001f) {
                    final Recall.Idea out = new Recall.Idea(-1, s.line);
                    out.quotes.add(s.start == null ? "" : s.start.trim());
                    out.pages.add(bookPage);
                    return out;
                }
            }
        }
        return null;
    }

    /** The ideas of a chapter's spots. {@code signals} must be in the reader's layout. */
    public static Result ideas(ReadsSignals signals, int chapterFrom, List<Recall.Idea> passages, ZoomData zoom) {
        final Result r = new Result();
        if (signals == null) {
            return r;
        }
        final Set<String> seen = new HashSet<>();
        for (java.util.Map.Entry<Integer, ReadsSignals.Page> e : signals.byPage.entrySet()) {
            if (!e.getValue().isSpot()) {
                continue;
            }
            r.spots++;
            final Recall.Idea idea = ideaAt(e.getKey(), signals.pages, chapterFrom, passages, zoom);
            if (idea == null) {
                r.without++;
            } else if (seen.add(idea.id + "|" + idea.text)) {
                r.ideas.add(idea);
            }
        }
        return r;
    }
}
