package com.foobnix.assist.edition;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.pdf.info.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * "Add to my edition" on a note of "The book, updated" (canvas UpdatedNote; only while
 * Assist.EDITION is on): a tap stores the note's title, body and how settled it is as
 * {@code edition:note:<chapter key>:<n>} in the book's assist file, and the edition lists it under
 * Connections; a second tap takes it out again. Nothing is sent to the AI, and the notes of "The
 * book, updated" are only read.
 */
public final class EditionNotes {

    /** The item prefix of a chapter's notes of "The book, updated" (assist/updated/UpdatedChapter). */
    private static final String UPDATED = "updated:";

    private EditionNotes() {
    }

    /** Where a note of "The book, updated" sits: its chapter and its place among the chapter's notes. */
    private static final class Place {
        String chapterKey;
        String chapterTitle = "";
        int n;
        /** The item key it is stored under in the edition, or null. */
        String stored;
    }

    /**
     * Makes {@code button} the "Add to my edition" / "In my edition" button of a note of the open
     * book. The note's chapter is found among the book's stored notes by its sentence and title;
     * a note that is not stored (or no open book) gets no button.
     *
     * @param quote       the sentence of the book the note attaches to
     * @param settled     how settled as stored ("well established", "debated", "speculative" or empty)
     * @param settledNote a few words after the rating; may be empty
     */
    public static void bind(Activity a, TextView button, String quote, String title, String body, String settled,
                            String settledNote) {
        button.setVisibility(View.GONE);
        final String bookName = AiHighlights.get().getBook();
        if (!Assist.EDITION.isOn() || bookName == null) {
            return;
        }
        final Place[] place = new Place[1];
        EditionData.IO.execute(() -> {
            final Place found = place(bookName, quote, title, body);
            a.runOnUiThread(() -> {
                if (found == null || Apps.isDestroyedActivity(a)) {
                    return;
                }
                place[0] = found;
                button.setText(found.stored == null ? R.string.canvas_t26_add_note : R.string.canvas_t26_note_added);
                button.setVisibility(View.VISIBLE);
            });
        });
        button.setOnClickListener(v -> {
            final Place p = place[0];
            if (p == null || !Assist.EDITION.isOn()) {
                return;
            }
            button.setEnabled(false);
            final String had = p.stored;
            EditionData.IO.execute(() -> {
                String now = had;
                boolean failed = false;
                try {
                    if (had != null) {
                        AssistStore.get().remove(bookName, had);
                        now = null;
                    } else {
                        now = EditionNote.keyFor(p.chapterKey, p.n, title, body, notes(bookName, p.chapterKey));
                        AssistStore.get().put(bookName, now, new EditionNote(title, body, settled, settledNote,
                                p.chapterTitle, System.currentTimeMillis()).toJson());
                    }
                } catch (Exception e) {
                    LOG.e(e);
                    failed = true;
                }
                final String key = now;
                final boolean error = failed;
                a.runOnUiThread(() -> {
                    if (Apps.isDestroyedActivity(a)) {
                        return;
                    }
                    p.stored = key;
                    button.setText(key == null ? R.string.canvas_t26_add_note : R.string.canvas_t26_note_added);
                    button.setEnabled(true);
                    if (error) {
                        Toast.makeText(a, R.string.canvas_t26_note_failed, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    /** The note's chapter and number, from the book's {@code updated:<chapter key>} items; null when it is in none. */
    private static Place place(String bookName, String quote, String title, String body) {
        try {
            final String q = clean(quote);
            final String t = clean(title);
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(bookName, UPDATED).entrySet()) {
                final JSONArray list = e.getValue().optJSONArray("notes");
                for (int i = 0; list != null && i < list.length(); i++) {
                    final JSONObject o = list.optJSONObject(i);
                    if (o != null && q.equals(clean(o.optString("quote", ""))) && t.equals(clean(o.optString("title", "")))) {
                        final Place p = new Place();
                        p.chapterKey = e.getKey().substring(UPDATED.length());
                        p.chapterTitle = e.getValue().optString("title", "");
                        p.n = i;
                        p.stored = find(bookName, p.chapterKey, title, body);
                        return p;
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return null;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** The key the note is stored under, or null when it is not in the edition. */
    private static String find(String bookName, String chapterKey, String title, String body) {
        for (Map.Entry<String, EditionNote> e : notes(bookName, chapterKey).entrySet()) {
            if (e.getValue().same(title, body)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The chapter's notes in the edition, by item key. */
    private static Map<String, EditionNote> notes(String bookName, String chapterKey) {
        final Map<String, EditionNote> out = new HashMap<>();
        try {
            for (Map.Entry<String, JSONObject> e : AssistStore.get().items(bookName, EditionNote.prefix(chapterKey)).entrySet()) {
                final EditionNote note = EditionNote.fromJson(e.getValue());
                // a chapter key may itself hold colons: only this chapter's notes
                if (note != null && chapterKey.equals(EditionNote.chapterOf(e.getKey()))) {
                    out.put(e.getKey(), note);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return out;
    }
}
