package com.foobnix.assist.pictures;

import com.foobnix.ai.AiStore;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A chapter as "Prose to pictures" reads and keeps it: its stored key passages (F-004) as the
 * ideas a request sends, and its pictures in the assist store ({@code picture:<chapter key>} in
 * the book's file).
 */
final class PictureChapter {

    private PictureChapter() {
    }

    /**
     * The chapter's stored ideas, each with the book's words that mark it in page order; empty
     * when the chapter has none or they carry too little of the book's words to draw from (the
     * request then reads the chapter's text).
     */
    static List<PictureIdea> ideas(AiStore.Chapter chapter) {
        final List<PictureIdea> out = new ArrayList<>();
        if (chapter == null) {
            return out;
        }
        for (AiStore.Idea idea : chapter.ideas) {
            if (idea.text == null || idea.text.trim().isEmpty()) {
                continue;
            }
            final PictureIdea i = new PictureIdea(idea.text, idea.category == null ? "" : idea.category.code);
            final List<AiStore.Quote> marks = new ArrayList<>();
            for (List<AiStore.Quote> list : Arrays.asList(chapter.quotes, chapter.paragraphs)) {
                for (AiStore.Quote q : list) {
                    if (q.idea == idea.id && q.text != null) {
                        marks.add(q);
                    }
                }
            }
            Collections.sort(marks, (a, b) -> Integer.compare(a.page, b.page));
            for (AiStore.Quote q : marks) {
                i.add(q.text, q.page);
            }
            out.add(i);
        }
        return PicturePrompts.enoughMarks(out) ? out : new ArrayList<PictureIdea>();
    }

    /** The chapter's stored pictures, or null. Off the main thread. */
    static PictureSet load(String bookName, String chapterKey) {
        try {
            return PictureSet.fromJson(AssistStore.get().item(bookName, PictureSet.key(chapterKey)));
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    /** Off the main thread. */
    static void save(String bookName, String chapterKey, PictureSet set) throws IOException {
        try {
            AssistStore.get().put(bookName, PictureSet.key(chapterKey), set.toJson());
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
    }
}
