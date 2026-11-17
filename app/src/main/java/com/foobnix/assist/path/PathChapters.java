package com.foobnix.assist.path;

import com.foobnix.ai.BookChapters;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.skeleton.SkeletonParts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The book's chapter list a path is picked from: the chapters of its table of contents in order
 * (as the skeleton cuts them, {@link SkeletonParts}), each with the one line the skeleton (or the
 * study preview it was read from) holds for its key. A part whose chapters have no line of their
 * own but which has one itself (a study preview that kept the part whole) stands as one chapter.
 */
public final class PathChapters {

    private PathChapters() {
    }

    /** One chapter of the list. */
    public static final class Entry {
        public final String key;
        /** The title as the table of contents gives it (spaces collapsed), or "" for a page block. */
        public final String title;
        /** The title of the part holding it, or "". */
        public final String partTitle;
        /** "I.7", or "". */
        public final String code;
        public final String line;
        /** Characters of text, 0 when not known. */
        public final long chars;
        /** First page and the page after the last, 0-based, in the layout the list was read in. */
        public final int from;
        public final int to;

        public Entry(String key, String title, String partTitle, String line, long chars, int from, int to) {
            this.key = key;
            this.title = title;
            this.partTitle = partTitle;
            this.code = PathText.code(partTitle, title);
            this.line = line;
            this.chars = chars;
            this.from = from;
            this.to = to;
        }
    }

    /** The chapters in book order. */
    public static List<Entry> list(List<SkeletonParts.Part> parts, SkeletonData data) {
        final List<Entry> out = new ArrayList<>();
        if (parts == null || data == null) {
            return out;
        }
        for (SkeletonParts.Part p : parts) {
            final boolean group = p.isGroup();
            boolean anyLine = false;
            for (BookChapters.Range c : p.chapters) {
                anyLine |= !line(data, c.key).isEmpty();
            }
            if (group && !anyLine && (!line(data, p.range.key).isEmpty() || !line(data.partLines, p.range.key).isEmpty())) {
                String line = line(data, p.range.key);
                if (line.isEmpty()) {
                    line = line(data.partLines, p.range.key);
                }
                long chars = chars(data, p.range.key);
                if (chars == 0) {
                    for (BookChapters.Range c : p.chapters) {
                        chars += chars(data, c.key);
                    }
                }
                out.add(new Entry(p.range.key, title(p.range), "", line, chars, p.range.from, p.range.to));
                continue;
            }
            final String partTitle = group ? title(p.range) : "";
            for (BookChapters.Range c : p.chapters) {
                out.add(new Entry(c.key, title(c), partTitle, line(data, c.key), chars(data, c.key), c.from, c.to));
            }
        }
        return out;
    }

    /** Whether any chapter has its line: without one there is nothing to pick a path from. */
    public static boolean hasLines(List<Entry> list) {
        for (Entry e : list) {
            if (!e.line.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Where each chapter and each part is in a layout: key = {first page, page after the last}, 0-based. */
    public static Map<String, int[]> ranges(List<SkeletonParts.Part> parts) {
        final Map<String, int[]> out = new LinkedHashMap<>();
        if (parts != null) {
            for (SkeletonParts.Part p : parts) {
                out.put(p.range.key, new int[]{p.range.from, p.range.to});
                for (BookChapters.Range c : p.chapters) {
                    out.put(c.key, new int[]{c.from, c.to});
                }
            }
        }
        return out;
    }

    private static String line(SkeletonData data, String key) {
        return line(data.chapterLines, key);
    }

    private static String line(Map<String, String> lines, String key) {
        final String s = lines.get(key);
        return s == null ? "" : s.trim();
    }

    private static long chars(SkeletonData data, String key) {
        final Long c = data.chars.get(key);
        return c == null || c < 0 ? 0 : c;
    }

    /** As the skeleton shows titles: spaces collapsed, nothing else touched. */
    private static String title(BookChapters.Range r) {
        return r.title == null ? "" : r.title.trim().replaceAll("\\s+", " ");
    }
}
