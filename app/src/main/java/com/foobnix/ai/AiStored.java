package com.foobnix.ai;

import java.util.Set;

/**
 * Whether the ✦ menu's AI features already have a result stored for this book (made here, synced
 * from another device, or written into the synced folder by anything else): the row's icon is then
 * blue, else grey (opening it asks the API). Null when the menu cannot tell without reading the
 * book (its chapter is not known yet): the row keeps the usual colour. No Android here.
 */
public final class AiStored {

    /** Blue: the result is here, opening it costs no request. */
    public static final int STORED = 0xff1e88e5;
    /** Grey: opening it asks the API. */
    public static final int NOT_STORED = 0xff9e9e9e;

    private AiStored() {
    }

    public static Integer color(Boolean stored) {
        return stored == null ? null : stored ? STORED : NOT_STORED;
    }

    /** An assist result of the chapter ({@code <prefix><chapter key>}); null when the chapter is not known. */
    public static Boolean chapterItem(Set<String> assistKeys, String prefix, String chapterKey) {
        return chapterKey == null ? null : assistKeys.contains(prefix + chapterKey);
    }

    /** Key passages of the chapter (or nothing to mark in it); null when the chapter is not known. */
    public static Boolean keyPassages(AiStore.Book data, String chapterKey) {
        return chapterKey == null ? null : data.isDone(chapterKey);
    }

    /** The chapter's summary; null when the chapter is not known. */
    public static Boolean chapterSummary(AiStore.Book data, String chapterKey) {
        return chapterKey == null ? null : data.summary(chapterKey) != null;
    }
}
