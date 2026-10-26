package com.foobnix.ai;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Side matter: the pages around a book's text that hold none of its ideas (copyright, contents,
 * index, about the author, acknowledgments, bibliography, glossary, ads for other books, calls to
 * join, review or download). Key passages, Study and the book map leave it out. Notes, prefaces,
 * introductions and appendices are the book's text.
 */
public final class SideMatter {

    /** Titles that are side matter as a whole, in English and Vietnamese, as {@link #normalize} leaves them. */
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            // copyright, credits, cover and title pages
            "copyright", "copyrights", "copyright page", "copyright and credits", "copyright information",
            "colophon", "imprint", "legal notice", "permissions", "credits", "photo credits", "image credits",
            "picture credits", "illustration credits", "text credits", "cover", "front cover", "back cover",
            "title", "title page", "half title", "half title page", "series page", "frontispiece",
            "front matter", "frontmatter", "back matter", "backmatter", "end matter", "endmatter",
            // contents and lists
            "contents", "table of contents", "brief contents", "contents in brief", "detailed contents",
            "contents at a glance", "toc", "list of figures", "list of tables", "list of illustrations",
            "list of maps", "list of plates", "list of abbreviations", "list of acronyms", "list of symbols",
            "abbreviations", "acronyms", "figures", "tables", "illustrations",
            // index
            "index", "subject index", "general index", "author index", "name index", "names index",
            // people
            "contributors", "list of contributors", "the contributors", "reviewers", "the reviewers",
            "the author", "the authors", "authors",
            // thanks
            "acknowledgments", "acknowledgements", "acknowledgment", "acknowledgement", "thanks",
            "dedication", "dedications",
            // sources
            "bibliography", "selected bibliography", "references", "works cited", "literature cited",
            "sources", "selected sources", "citations", "further reading", "suggested reading",
            "recommended reading", "suggested readings",
            // terms
            "glossary", "glossary of terms", "list of terms", "key terms",
            // praise, ads and calls to action
            "praise", "advance praise", "endorsements", "what people are saying",
            "other books you may enjoy", "other books", "also available", "you may also like",
            "you might also like", "you may also enjoy", "recommended books", "why subscribe",
            "subscribe", "newsletter", "stay in touch", "stay connected", "connect with us", "follow us",
            "thank you for reading", "thanks for reading", "before you go", "one last thing",
            "review this book", "please leave a review", "free gift", "a free gift for you", "your free gift",
            "get your free gift",
            // Vietnamese
            "mục lục", "bản quyền", "thông tin xuất bản", "lời cảm ơn", "lời cám ơn", "cảm ơn", "lời tri ân",
            "tri ân", "đề tặng", "lời đề tặng", "về tác giả", "giới thiệu tác giả", "tác giả", "về các tác giả",
            "tài liệu tham khảo", "thư mục", "thư mục tham khảo", "chỉ mục", "bảng chỉ mục", "sách dẫn",
            "thuật ngữ", "bảng thuật ngữ", "giải thích thuật ngữ", "từ điển thuật ngữ", "bìa", "trang bìa",
            "bìa sách", "bìa trước", "bìa sau", "sách cùng tác giả", "đón đọc"));

    /** Beginnings of side matter titles; a whole word must follow, or nothing. */
    private static final List<String> STARTS = Arrays.asList(
            "about the author", "about the authors", "about the editor", "about the editors",
            "about the translator", "about the illustrator", "about the publisher", "about the contributors",
            "about the reviewer", "about the reviewers", "meet the author", "author bio", "also by",
            "other books by", "more books by", "other titles by", "praise for", "advance praise for",
            "index of", "join our", "share your thoughts", "leave a review", "leave us a review",
            "download a free", "download the free", "download your free", "get your free",
            "về tác giả", "lời cảm ơn", "lời cám ơn", "sách cùng tác giả", "đón đọc", "tài liệu tham khảo");

    /** "Chapter 12:", "IV.", "3 -", "B.": numbering before a title. */
    private static final Pattern NUMBERING = Pattern.compile(
            "^(?:(?:chapter|part|section|chương|phần)\\s+)?(?:\\d+|[ivxlcdm]+|[a-z](?=[.)]))(?:\\s*[.:)\\-–—]+\\s*|\\s+)");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}' ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** "All rights reserved", an ISBN, a cataloguing record, a Vietnamese colophon. */
    private static final Pattern COPYRIGHT = Pattern.compile(
            "all rights reserved|isbn(?:[-\\s]?1[03])?[:\\s]*\\d[\\d\\s-]{8,}[\\dx]"
                    + "|library of congress cataloging|british library cataloguing|chịu trách nhiệm xuất bản"
                    + "|giữ bản quyền|bản quyền tiếng việt");
    /** A page number or range at a line's end: "12", "45–47", "xii". */
    private static final Pattern PAGE_REF = Pattern.compile("^(?:\\d{1,4}(?:[–-]\\d{1,4})?|[ivxlcdm]{1,7})[,.;]?$");

    /** Share of the book at either end where copyright, contents and index pages are looked for. */
    static final double FRONT_SHARE = 0.15;
    static final double BACK_SHARE = 0.25;
    /** A short book's front is at least this many pages. */
    static final int FRONT_PAGES = 12;
    /** A page of references has at least this many lines, at least half of them ending in a page number. */
    static final int REF_LINES = 8;

    private SideMatter() {
    }

    /** A chapter whose TOC title names side matter ("Copyright", "About the Author", "Index"...). */
    public static boolean isSideTitle(String title) {
        final String t = normalize(title);
        if (t.isEmpty()) {
            return false;
        }
        if (TITLES.contains(t) || t.startsWith("acknowledg")) {
            return true;
        }
        for (String start : STARTS) {
            if (t.equals(start) || t.startsWith(start + " ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A page near either end of the book that is copyright details, or a list of page references
     * (contents, index): the side matter a TOC does not name.
     *
     * @param lines the page's lines, top to bottom
     */
    public static boolean isSidePage(List<String> lines, int page, int pageCount) {
        final boolean front = page < Math.max(FRONT_PAGES, pageCount * FRONT_SHARE);
        final boolean back = page >= pageCount * (1 - BACK_SHARE);
        if (!front && !back || lines == null || lines.isEmpty()) {
            return false;
        }
        final String all = String.join(" ", lines).toLowerCase(Locale.ROOT);
        if (COPYRIGHT.matcher(all).find()) {
            return true;
        }
        int refs = 0;
        for (String line : lines) {
            final String[] tokens = SPACES.split(line.trim());
            if (tokens.length >= 2 && PAGE_REF.matcher(tokens[tokens.length - 1].toLowerCase(Locale.ROOT)).matches()) {
                refs++;
            }
        }
        return lines.size() >= REF_LINES && refs * 2 >= lines.size();
    }

    /** Lower case, numbering and punctuation gone, spaces single. */
    static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String t = Normalizer.normalize(title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace(' ', ' ').trim();
        t = NUMBERING.matcher(t).replaceFirst("");
        t = NOT_WORD.matcher(t).replaceAll(" ");
        return SPACES.matcher(t).replaceAll(" ").trim();
    }
}
