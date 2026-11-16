package com.foobnix.assist.lens;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiStore;
import com.foobnix.assist.AssistStore;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the lens keeps its data, all of it in {@link AssistStore}: the lens itself in the library
 * file ({@code lens:profile}), and per book the reader's level ({@code lens:level}) and each
 * chapter's bridge with its ladders ({@code lens:bridge:<chapter key>}). Reads and writes touch
 * files: call them off the main thread, except the small saves a tap makes.
 */
final class LensStore {

    private LensStore() {
    }

    /** The reader's lens; the defaults without a field when none was saved. */
    static LensProfile profile() {
        return LensProfile.fromJson(AssistStore.get().item(AssistStore.LIBRARY, LensProfile.KEY));
    }

    static void saveProfile(LensProfile profile) throws IOException {
        try {
            AssistStore.get().put(AssistStore.LIBRARY, LensProfile.KEY, profile.toJson());
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** The reader's level in a book's subject; "new to it" until they say otherwise. */
    static LensProfile.Level level(String bookName) {
        return bookName == null ? LensProfile.Level.NEW
                : LensProfile.Level.fromJson(AssistStore.get().item(bookName, LensProfile.LEVEL_KEY));
    }

    static void saveLevel(String bookName, LensProfile.Level level) throws IOException {
        try {
            AssistStore.get().put(bookName, LensProfile.LEVEL_KEY, level.toJson());
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** A chapter's stored bridge, or null. */
    static LensBridge bridge(String bookName, String chapterKey) {
        return LensBridge.fromJson(AssistStore.get().item(bookName, LensBridge.key(chapterKey)));
    }

    static void saveBridge(String bookName, String chapterKey, LensBridge bridge) throws IOException {
        try {
            AssistStore.get().put(bookName, LensBridge.key(chapterKey), bridge.toJson());
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Puts a ladder into its row of the stored bridge (read again, so a bridge mapped anew in
     * between is not written over with the old one: the ladder is then dropped).
     */
    static void saveLadder(String bookName, String chapterKey, int row, String idea, LensLadder ladder)
            throws IOException {
        final LensBridge stored = bridge(bookName, chapterKey);
        if (stored == null || row < 0 || row >= stored.rows.size() || !stored.rows.get(row).idea.equals(idea)) {
            return;
        }
        stored.rows.get(row).ladder = ladder;
        saveBridge(bookName, chapterKey, stored);
    }

    /** The chapter's stored key-passage ideas, each with the mark a tap goes to (its first in the book). */
    static List<LensIdea> ideas(AiStore.Chapter chapter) {
        final List<LensIdea> out = new ArrayList<>();
        if (chapter == null) {
            return out;
        }
        for (AiStore.Idea idea : chapter.ideas) {
            AiStore.Quote first = null;
            for (List<AiStore.Quote> list : Arrays.asList(chapter.quotes, chapter.paragraphs)) {
                for (AiStore.Quote q : list) {
                    if (q.idea == idea.id && (first == null || q.page < first.page)) {
                        first = q;
                    }
                }
            }
            final AiCategory category = idea.category;
            out.add(new LensIdea(idea.id, idea.text, category == null ? "" : category.code,
                    first == null ? null : first.text, first == null ? idea.page : first.page));
        }
        return out;
    }
}
