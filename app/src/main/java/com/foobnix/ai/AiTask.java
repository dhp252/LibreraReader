package com.foobnix.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * A long AI job as work the app owns (design-2026-10-25.md): what to do, on which book, in which
 * page layout. Stored by WorkManager, so the job can run again after the app was killed.
 */
public final class AiTask {

    public static final String HIGHLIGHTS = "highlights";
    public static final String MAP = "map";
    public static final String BOOK_SUMMARY = "bookSummary";
    public static final String STUDY_PREVIEW = "studyPreview";

    public final String kind;
    public final String path;
    public final String title;
    public final int width;
    public final int height;
    public final int fontSize;
    /** The page the reader showed when the task was started, 0-based. */
    public final int page;
    public final boolean wholeBook;
    /** A chapter given by the caller (a study chapter), or null for the one at {@link #page}. */
    public final BookChapters.Range chapter;
    /** Book map: only the whole-book step (Update map, Rebuild map). */
    public final boolean bookOnly;
    /** Summary: made again although one is saved. */
    public final boolean regenerate;

    private AiTask(String kind, String path, String title, int width, int height, int fontSize, int page,
                   boolean wholeBook, BookChapters.Range chapter, boolean bookOnly, boolean regenerate) {
        this.kind = kind;
        this.path = path;
        this.title = title == null ? "" : title;
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
        this.page = page;
        this.wholeBook = wholeBook;
        this.chapter = chapter;
        this.bookOnly = bookOnly;
        this.regenerate = regenerate;
    }

    public static AiTask highlights(AiJobs.Target t, boolean wholeBook, BookChapters.Range chapter) {
        return of(HIGHLIGHTS, t, wholeBook, chapter, false, false);
    }

    public static AiTask map(AiJobs.Target t, boolean bookOnly) {
        return of(MAP, t, true, null, bookOnly, false);
    }

    public static AiTask bookSummary(AiJobs.Target t, boolean regenerate) {
        return of(BOOK_SUMMARY, t, true, null, false, regenerate);
    }

    public static AiTask studyPreview(AiJobs.Target t) {
        return of(STUDY_PREVIEW, t, true, null, false, false);
    }

    private static AiTask of(String kind, AiJobs.Target t, boolean wholeBook, BookChapters.Range chapter,
                             boolean bookOnly, boolean regenerate) {
        return new AiTask(kind, t.path, t.title, t.width, t.height, t.fontSize, t.page, wholeBook, chapter,
                bookOnly, regenerate);
    }

    public static boolean isKind(String kind) {
        return HIGHLIGHTS.equals(kind) || MAP.equals(kind) || BOOK_SUMMARY.equals(kind) || STUDY_PREVIEW.equals(kind);
    }

    /** The book and the layout the task reads it in. */
    public AiJobs.Target target() {
        return new AiJobs.Target(path, title, width, height, fontSize, page);
    }

    /** The book's name, as its AI data is stored under. */
    public String book() {
        return AiHighlights.bookName(path);
    }

    /** One task per book and kind: a second start of the same one joins it. */
    public String key() {
        return key(book(), kind);
    }

    public static String key(String bookName, String kind) {
        return kind + ":" + bookName;
    }

    /** As plain values (WorkManager data holds strings, numbers and booleans). */
    public Map<String, Object> toMap() {
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("path", path);
        m.put("title", title);
        m.put("width", width);
        m.put("height", height);
        m.put("fontSize", fontSize);
        m.put("page", page);
        m.put("wholeBook", wholeBook);
        m.put("bookOnly", bookOnly);
        m.put("regenerate", regenerate);
        if (chapter != null) {
            m.put("chapterFrom", chapter.from);
            m.put("chapterTo", chapter.to);
            m.put("chapterTitle", chapter.title == null ? "" : chapter.title);
            m.put("chapterKey", chapter.key == null ? "" : chapter.key);
        }
        return m;
    }

    /** The task stored by {@link #toMap}, or null when it is not one (an unknown kind, no book). */
    public static AiTask fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        final String kind = string(m.get("kind"));
        final String path = string(m.get("path"));
        if (!isKind(kind) || path.isEmpty()) {
            return null;
        }
        BookChapters.Range chapter = null;
        if (m.get("chapterKey") != null) {
            chapter = new BookChapters.Range(number(m.get("chapterFrom")), number(m.get("chapterTo")),
                    string(m.get("chapterTitle")), string(m.get("chapterKey")));
        }
        return new AiTask(kind, path, string(m.get("title")), number(m.get("width")), number(m.get("height")),
                number(m.get("fontSize")), number(m.get("page")), bool(m.get("wholeBook")), chapter,
                bool(m.get("bookOnly")), bool(m.get("regenerate")));
    }

    private static String string(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int number(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean && (Boolean) o;
    }
}
