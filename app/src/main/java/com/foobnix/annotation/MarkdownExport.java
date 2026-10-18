package com.foobnix.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A book's marks and notes as Markdown for Obsidian or a Zotero note, the layout "headings +
 * quotes" (.agents/features/F-008-separate-marks/design-2026-10-18.md): front matter, the title,
 * the marks in book order under the chapter they fall in, each as a quote with a
 * {@code p. 21 · 🟨 yellow · highlight} line and its note; then the bookmarks, then the AI key
 * passages. Sections with nothing in them are left out.
 */
public final class MarkdownExport {

    /** What the export is about. */
    public static final class Book {
        public final String title;
        public final String author;
        public final String file;
        /** yyyy-MM-dd. */
        public final String date;
        /** Chapters in book order. */
        public final List<Chapter> chapters;

        public Book(String title, String author, String file, String date, List<Chapter> chapters) {
            this.title = title == null ? "" : title.trim();
            this.author = author == null ? "" : author.trim();
            this.file = file == null ? "" : file;
            this.date = date == null ? "" : date;
            this.chapters = chapters == null ? Collections.<Chapter>emptyList() : chapters;
        }
    }

    /** A table-of-contents entry: its title and 1-based page. */
    public static final class Chapter {
        public final String title;
        public final int page;

        public Chapter(String title, int page) {
            this.title = title;
            this.page = page;
        }
    }

    /** A mark or note: 1-based page, place on it, words, note, color and kind. */
    public static final class Item {
        public final int page;
        /** Where on the page (top, 0..1) or the order of words: sorts marks of one page. */
        public final float order;
        public final String quote;
        public final String note;
        public final String color;
        /** "highlight", "underline", "strikethrough", "note"... */
        public final String kind;

        public Item(int page, float order, String quote, String note, String color, String kind) {
            this.page = page;
            this.order = order;
            this.quote = quote == null ? "" : quote.trim();
            this.note = note == null ? "" : note.trim();
            this.color = color;
            this.kind = kind;
        }
    }

    /** A Librera bookmark: 1-based page and its text. */
    public static final class Bookmark {
        public final int page;
        public final String text;

        public Bookmark(int page, String text) {
            this.page = page;
            this.text = text == null ? "" : text.trim();
        }
    }

    /** An AI key passage: the AI chapter it belongs to, its page, words and label ("🟥 Main idea"). */
    public static final class Passage {
        public final String chapter;
        public final int page;
        public final String quote;
        public final String label;

        public Passage(String chapter, int page, String quote, String label) {
            this.chapter = chapter == null ? "" : chapter.trim();
            this.page = page;
            this.quote = quote == null ? "" : quote.trim();
            this.label = label;
        }
    }

    private MarkdownExport() {
    }

    public static boolean isEmpty(List<Item> items, List<Bookmark> bookmarks, List<Passage> passages) {
        return (items == null || items.isEmpty()) && (bookmarks == null || bookmarks.isEmpty())
                && (passages == null || passages.isEmpty());
    }

    public static String build(Book book, List<Item> items, List<Bookmark> bookmarks, List<Passage> passages) {
        final StringBuilder md = new StringBuilder();
        md.append("---\n");
        md.append("title: ").append(yaml(book.title)).append('\n');
        if (!book.author.isEmpty()) {
            md.append("author: ").append(yaml(book.author)).append('\n');
        }
        md.append("file: ").append(yaml(book.file)).append('\n');
        md.append("exported: ").append(book.date).append('\n');
        md.append("tags: [book, librera]\n");
        md.append("---\n");
        md.append("# ").append(oneLine(book.title)).append('\n');
        if (!book.author.isEmpty()) {
            md.append(oneLine(book.author)).append('\n');
        }

        final List<Item> sorted = new ArrayList<>(items == null ? Collections.<Item>emptyList() : items);
        Collections.sort(sorted, (a, b) -> a.page != b.page ? Integer.compare(a.page, b.page) : Float.compare(a.order, b.order));
        Chapter current = null;
        for (Item item : sorted) {
            final Chapter chapter = chapterOf(book.chapters, item.page);
            if (chapter != null && chapter != current) {
                md.append("\n## ").append(oneLine(chapter.title)).append('\n');
                current = chapter;
            }
            md.append('\n');
            if (!item.quote.isEmpty()) {
                quote(md, item.quote);
                md.append('\n');
            }
            md.append("p. ").append(item.page).append(" · ").append(ColorNames.label(item.color)).append(" · ")
                    .append(item.kind).append('\n');
            if (!item.note.isEmpty()) {
                md.append("\n**Note:** ").append(item.note.replace("\r", "")).append('\n');
            }
        }

        if (bookmarks != null && !bookmarks.isEmpty()) {
            final List<Bookmark> marks = new ArrayList<>(bookmarks);
            Collections.sort(marks, (a, b) -> Integer.compare(a.page, b.page));
            md.append("\n## Bookmarks\n\n");
            for (Bookmark b : marks) {
                md.append("- p. ").append(b.page);
                if (!b.text.isEmpty()) {
                    md.append(" · ").append(oneLine(b.text));
                }
                md.append('\n');
            }
        }

        if (passages != null && !passages.isEmpty()) {
            md.append("\n## AI key passages\n");
            String chapter = null;
            for (Passage p : passages) {
                if (!p.chapter.isEmpty() && !p.chapter.equals(chapter)) {
                    md.append("\n### ").append(oneLine(p.chapter)).append('\n');
                    chapter = p.chapter;
                }
                md.append('\n');
                quote(md, p.quote);
                md.append("\np. ").append(p.page).append(" · ").append(p.label).append('\n');
            }
        }
        return md.toString();
    }

    /** The last chapter starting at or before a page (null before the first one). */
    static Chapter chapterOf(List<Chapter> chapters, int page) {
        Chapter found = null;
        for (Chapter c : chapters) {
            if (c.page >= 1 && c.page <= page && (found == null || c.page >= found.page)) {
                found = c;
            }
        }
        return found;
    }

    private static void quote(StringBuilder md, String text) {
        for (String line : text.replace("\r", "").split("\n")) {
            md.append("> ").append(line.trim()).append('\n');
        }
    }

    private static String oneLine(String text) {
        return text.replace("\r", " ").replace("\n", " ").trim();
    }

    /** A YAML string in double quotes. */
    static String yaml(String text) {
        return "\"" + oneLine(text).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
