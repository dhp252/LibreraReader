package com.foobnix.assist.edition;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.skeleton.SkeletonParts;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A book's edition as read from the store: the items of the book's assist file and the library's
 * connections go through {@link EditionBuilder}, then {@link EditionPages}. Nothing is asked of
 * the AI and nothing is written here. Every call reads files: call them on {@link #IO}.
 */
final class EditionData {

    /** One thread for the edition's reads and writes, so a save and the load after it keep their order. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    final String bookName;
    final EditionModel model;
    final EditionPages pages;
    final EditionState state;
    final EditionWords words;
    final String today;

    private EditionData(String bookName, EditionModel model, EditionPages pages, EditionState state, EditionWords words,
                        String today) {
        this.bookName = bookName;
        this.model = model;
        this.pages = pages;
        this.state = state;
        this.words = words;
        this.today = today;
    }

    /** The same edition with its state as just stored. */
    EditionData with(EditionState newState) {
        return new EditionData(bookName, model, pages, newState, words, today);
    }

    /**
     * @param outline also read the book's table of contents (it lays a reflowing book out once
     *                more): chapters then stand in the book's order under the book's own titles.
     *                Without it they are named by the titles their items carry, which is enough
     *                to know whether there is an edition and how long it is.
     */
    static EditionData load(Context c, AiJobs.Target t, boolean outline) {
        final EditionWords words = EditionLook.words(c);
        final String today = EditionDates.today();
        final Map<String, JSONObject> book = AssistStore.get().items(t.name, null);
        final Map<String, JSONObject> library = AssistStore.get().items(AssistStore.LIBRARY, EditionBuilder.LINK);
        List<SkeletonParts.Part> parts = null;
        if (outline) {
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    parts = SkeletonParts.group(AiJobs.toc(t, text), text.pageCount());
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }
        final EditionModel model = EditionBuilder.build(t.name, t.title, book, library, EditionOutline.of(parts), words,
                today, TimeZone.getDefault());
        return new EditionData(t.name, model, EditionPages.of(model), EditionState.fromJson(book.get(EditionState.KEY)),
                words, today);
    }

    /** Changes the book's {@code edition:book} item: read, changed by {@code change}, written when it says so. */
    static EditionState update(String bookName, Change change) {
        final EditionState state = EditionState.fromJson(AssistStore.get().item(bookName, EditionState.KEY));
        try {
            if (change.apply(state)) {
                AssistStore.get().put(bookName, EditionState.KEY, state.toJson());
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return state;
    }

    /** A change of the edition's state; returns whether it has to be stored. */
    interface Change {
        boolean apply(EditionState state);
    }
}
