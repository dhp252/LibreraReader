package com.foobnix.assist.skeleton;

import com.foobnix.ai.BookChapters;
import com.foobnix.study.StudyBook;
import com.foobnix.study.StudyTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the skeleton screen shows: the book's parts in the reader's layout (pages from its table of
 * contents) with the skeleton's lines, flags and totals laid on them by key.
 */
public final class SkeletonModel {

    public final String line;
    public final String paragraph;
    /** Where the conclusion is ("Smith saves his conclusion for Book IV."), or empty. */
    public final String note;
    public final List<PartView> parts;
    /** Index in {@link #parts} of the conclusion's part, or -1. */
    public final int conclusion;
    /** "5 books", "32 chapters", "about 27½ h in full". */
    public final List<String> totals;
    public final boolean grouped;

    private SkeletonModel(String line, String paragraph, String note, List<PartView> parts, int conclusion,
                          List<String> totals, boolean grouped) {
        this.line = line;
        this.paragraph = paragraph;
        this.note = note;
        this.parts = parts;
        this.conclusion = conclusion;
        this.totals = totals;
        this.grouped = grouped;
    }

    /** One card of the chapter map. */
    public static final class PartView {
        public final String key;
        public final String title;
        /** "Book I", or the whole title when it has no number. */
        public final String label;
        public final String line;
        /** Chapters of a part holding them; 0 for a chapter standing alone. */
        public final int chapterCount;
        public final boolean here;
        public final boolean conclusion;
        public final boolean optional;
        /** First page and the page after the last, 0-based, in the reader's layout. */
        public final int from;
        public final int to;
        public final List<ChapterView> chapters;

        PartView(String key, String title, String label, String line, int chapterCount, boolean here,
                 boolean conclusion, boolean optional, int from, int to, List<ChapterView> chapters) {
            this.key = key;
            this.title = title;
            this.label = label;
            this.line = line;
            this.chapterCount = chapterCount;
            this.here = here;
            this.conclusion = conclusion;
            this.optional = optional;
            this.from = from;
            this.to = to;
            this.chapters = chapters;
        }
    }

    /** A chapter inside an opened card. */
    public static final class ChapterView {
        public final String key;
        public final String title;
        public final String line;
        public final int from;
        public final int to;
        public final boolean here;

        ChapterView(String key, String title, String line, int from, int to, boolean here) {
            this.key = key;
            this.title = title;
            this.line = line;
            this.from = from;
            this.to = to;
            this.here = here;
        }
    }

    /**
     * @param parts the book's parts in the reader's layout ({@link SkeletonParts#group})
     * @param page  the page the reader shows, 0-based, in the same layout
     * @param wpm   the reader's speed (0 = not known yet)
     */
    public static SkeletonModel build(List<SkeletonParts.Part> parts, SkeletonData data, int page, int wpm) {
        final boolean grouped = SkeletonParts.hasGroups(parts);
        final int herePart = SkeletonParts.partAt(parts, page);
        final List<PartView> views = new ArrayList<>();
        int conclusion = -1;
        int chapterCount = 0;
        long minutes = 0;
        for (int i = 0; i < parts.size(); i++) {
            final SkeletonParts.Part p = parts.get(i);
            final BookChapters.Range hereChapter = i == herePart ? SkeletonParts.chapterAt(p, page) : null;
            final List<ChapterView> chapters = new ArrayList<>();
            long partMinutes = 0;
            for (BookChapters.Range c : p.chapters) {
                chapters.add(new ChapterView(c.key, title(c), lineOf(data, c.key), c.from, c.to,
                        hereChapter != null && hereChapter.key.equals(c.key)));
                partMinutes += minutes(data, c.key, wpm);
            }
            if (partMinutes == 0) {
                // a study preview that kept the part whole knows its length as one chapter
                partMinutes = minutes(data, p.range.key, wpm);
            }
            minutes += partMinutes;
            chapterCount += p.chapters.size();
            final boolean group = p.isGroup();
            String line = data.partLines.get(p.range.key);
            if (line == null || line.isEmpty()) {
                // a chapter standing alone (or a part a study preview kept whole) carries its own line
                line = lineOf(data, p.range.key);
            }
            if (line.isEmpty() && group) {
                // else what the part's title says after its number
                line = SkeletonText.afterLabel(p.range.title);
            }
            final boolean isConclusion = !data.conclusion.isEmpty() && (data.conclusion.equals(p.range.key)
                    || (!group && p.chapters.size() == 1 && data.conclusion.equals(p.chapters.get(0).key)));
            if (isConclusion && conclusion < 0) {
                conclusion = i;
            }
            final boolean optional = !isConclusion && (data.optional.contains(p.range.key)
                    || (!group && p.chapters.size() == 1 && data.optional.contains(p.chapters.get(0).key)));
            final String title = title(p.range);
            views.add(new PartView(p.range.key, title, SkeletonText.shortLabel(title), line,
                    group ? p.chapters.size() : 0, i == herePart, isConclusion, optional, p.range.from, p.range.to,
                    Collections.unmodifiableList(chapters)));
        }
        int groups = 0;
        for (SkeletonParts.Part p : parts) {
            if (p.isGroup()) {
                groups++;
            }
        }
        final List<String> totals = SkeletonText.totals(grouped ? groups : 0,
                grouped ? SkeletonText.partNoun(groupTitles(parts)) : null, chapterCount,
                (int) Math.min(Integer.MAX_VALUE, minutes));
        return new SkeletonModel(data.line, data.paragraph, conclusion >= 0 ? data.note : "",
                Collections.unmodifiableList(views), conclusion, totals, grouped);
    }

    private static List<String> groupTitles(List<SkeletonParts.Part> parts) {
        final List<String> out = new ArrayList<>();
        for (SkeletonParts.Part p : parts) {
            if (p.isGroup()) {
                out.add(p.range.title == null ? "" : p.range.title.trim());
            }
        }
        return out;
    }

    private static long minutes(SkeletonData data, String key, int wpm) {
        final Long chars = data.chars.get(key);
        return chars == null || chars <= 0 ? 0 : StudyTime.minutes(chars, StudyBook.READ, wpm);
    }

    private static String lineOf(SkeletonData data, String key) {
        final String s = data.chapterLines.get(key);
        return s == null ? "" : s;
    }

    /** The title, or "" for a page block of a book without a table of contents (the screen names its pages). */
    private static String title(BookChapters.Range r) {
        return r.title == null ? "" : r.title.trim().replaceAll("\\s+", " ");
    }
}
