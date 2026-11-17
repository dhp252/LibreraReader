package com.foobnix.assist.edition;

import com.foobnix.ai.BookChapters;
import com.foobnix.assist.skeleton.SkeletonParts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The book's parts and chapters in reading order, by key, with their titles as the table of
 * contents has them: what puts an edition's chapters in the book's order and names them. Empty
 * when the table of contents could not be read; the edition then names a chapter by the title
 * its items carry.
 */
public final class EditionOutline {

    public static final class Entry {
        public final String key;
        public final String title;
        /** A part holding chapters of its own (Book I), not a chapter. */
        public final boolean part;

        public Entry(String key, String title, boolean part) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title.trim();
            this.part = part;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    private final Map<String, Integer> order = new HashMap<>();

    public EditionOutline add(String key, String title, boolean part) {
        final Entry e = new Entry(key, title, part);
        if (!order.containsKey(e.key)) {
            order.put(e.key, entries.size());
            entries.add(e);
        }
        return this;
    }

    /** From the book's parts in the reader's layout ({@link SkeletonParts#group}); null gives an empty outline. */
    public static EditionOutline of(List<SkeletonParts.Part> parts) {
        final EditionOutline o = new EditionOutline();
        if (parts == null) {
            return o;
        }
        for (SkeletonParts.Part p : parts) {
            final boolean group = p.isGroup();
            o.add(p.range.key, p.range.title, group);
            if (group) {
                for (BookChapters.Range c : p.chapters) {
                    o.add(c.key, c.title, false);
                }
            }
        }
        return o;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The place of a key in reading order, or -1 when the outline does not have it. */
    public int order(String key) {
        final Integer i = order.get(key);
        return i == null ? -1 : i;
    }

    /** The title of a key, or empty. */
    public String title(String key) {
        final int i = order(key);
        return i < 0 ? "" : entries.get(i).title;
    }
}
