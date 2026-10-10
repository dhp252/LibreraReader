package org.ebookdroid.core;

import org.ebookdroid.droids.mupdf.codec.TextWord;

/** Read access to the words of a page for code outside this package (read-aloud double tap). */
public final class PageText {

    private PageText() {
    }

    /** The words of the page by line, or null until the page has been decoded. */
    public static TextWord[][] of(Page page) {
        return page == null ? null : page.texts;
    }
}
