package com.foobnix.assist.xray;

import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A chapter's stored key passages as the X-ray reads them, and its ratings in the assist store
 * ({@code xray:<chapter key>} in the book's file).
 */
final class XrayChapter {

    static final String PREFIX = "xray:";

    private XrayChapter() {
    }

    /** The chapter's ideas, each with its marks' words and the mark a tap goes to (the first in the book). */
    static List<XrayIdea> ideas(AiStore.Chapter chapter) {
        final List<XrayIdea> out = new ArrayList<>();
        for (AiStore.Idea idea : chapter.ideas) {
            final List<String> quotes = new ArrayList<>();
            AiStore.Quote first = null;
            for (List<AiStore.Quote> list : java.util.Arrays.asList(chapter.quotes, chapter.paragraphs)) {
                for (AiStore.Quote q : list) {
                    if (q.idea != idea.id) {
                        continue;
                    }
                    quotes.add(q.text);
                    if (first == null || q.page < first.page) {
                        first = q;
                    }
                }
            }
            out.add(new XrayIdea(idea.id, idea.text, idea.category, idea.parent, idea.marked, idea.page, quotes,
                    first == null ? null : first.text, first == null ? idea.page : first.page));
        }
        return out;
    }

    /** The stored ratings of a chapter, for the ideas it still has; null when it has none. Off the main thread. */
    static XrayRatings load(String bookName, AiStore.Chapter chapter, XrayTree tree) {
        try {
            final XrayRatings r = XrayRatings.fromJson(AssistStore.get().item(bookName, PREFIX + chapter.key));
            return r == null ? null : r.keep(tree.ids());
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    static void save(String bookName, String chapterKey, XrayRatings ratings) throws IOException {
        try {
            AssistStore.get().put(bookName, PREFIX + chapterKey, ratings.toJson());
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }
}
