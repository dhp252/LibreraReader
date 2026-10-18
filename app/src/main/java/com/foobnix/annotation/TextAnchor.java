package com.foobnix.annotation;

import com.foobnix.ai.QuoteMatcher;
import com.foobnix.tts.TtsTextLocator;

import java.util.List;

/**
 * Finds a mark of a reflowing book (EPUB, FB2, MOBI...) on a page after any layout: its quote
 * among the page's letters, told apart from other occurrences by the letters right around it, and
 * only on pages near where it was made. Letters are compared as {@link TtsTextLocator#normalize}
 * gives them, so spacing, punctuation and words cut at line ends do not matter.
 * <p>
 * A mark cut by a page edge in this layout is found as a head of its quote ending one page and a
 * tail starting the next.
 */
public final class TextAnchor {

    /** Letters kept on each side of a quote. */
    static final int CONTEXT = 32;
    /** Pages searched on each side of where a mark should be, at least... */
    static final int WINDOW_PAGES = 3;
    /** ...or this share of the book, for books whose pages moved more. */
    static final float WINDOW_SHARE = 0.05f;
    /** Letters (of the quote and its context) a match needs: a short quote alone turns up by chance. */
    static final int MIN_EVIDENCE = 12;
    /** A page lying wholly inside a quote (a mark over three pages) needs this many letters. */
    static final int MIN_WHOLE_PAGE = 40;

    /** How a quote lies on a page. */
    static final int WHOLE = 0;
    static final int HEAD = 1;
    static final int TAIL = 2;
    static final int INSIDE = 3;

    /** The words of pages, prepared once (a page is searched for many marks). */
    public interface Pages {
        /** The words of a 0-based page, or null when it has none. */
        QuoteMatcher.Words words(int page);
    }

    /** Where a quote lies on a page: words {@code first..last}, and how ({@link #WHOLE}...). */
    static final class Match {
        final int first;
        final int last;
        final int how;

        Match(int[] range, int how) {
            this.first = range[0];
            this.last = range[1];
            this.how = how;
        }
    }

    private TextAnchor() {
    }

    /** The letters a quote is compared by. */
    public static String key(String quote) {
        return TtsTextLocator.normalize(quote);
    }

    /**
     * The anchor of marked words.
     *
     * @param before    words right before the quote (its page, possibly the end of the page before)
     * @param after     words right after it
     * @param page      the 0-based page the quote starts on
     * @param pageCount the pages of the layout it was made in
     */
    public static Mark.Anchor of(List<String> before, List<String> after, int page, int pageCount) {
        final StringBuilder prefix = new StringBuilder();
        for (int i = before == null ? -1 : before.size() - 1; i >= 0 && prefix.length() < CONTEXT; i--) {
            prefix.insert(0, TtsTextLocator.normalize(before.get(i)));
        }
        final StringBuilder suffix = new StringBuilder();
        for (int i = 0; after != null && i < after.size() && suffix.length() < CONTEXT; i++) {
            suffix.append(TtsTextLocator.normalize(after.get(i)));
        }
        final String pre = prefix.length() > CONTEXT ? prefix.substring(prefix.length() - CONTEXT) : prefix.toString();
        final String suf = suffix.length() > CONTEXT ? suffix.substring(0, CONTEXT) : suffix.toString();
        return new Mark.Anchor(pre, suf, page, pageCount);
    }

    /** Where a mark should be in a layout of {@code pageCount} pages. */
    public static int expectedPage(Mark.Anchor a, int pageCount) {
        if (a.pageCount <= 0 || pageCount <= 0) {
            return Math.max(0, a.page);
        }
        final int page = Math.round((float) a.page * pageCount / a.pageCount);
        return Math.max(0, Math.min(pageCount - 1, page));
    }

    /** Whether a page of a layout of {@code pageCount} pages is near enough to hold the mark. */
    public static boolean near(Mark.Anchor a, int page, int pageCount) {
        if (a.pageCount <= 0 || pageCount <= 0) {
            return true;
        }
        final float expected = (float) a.page * pageCount / a.pageCount;
        final float slack = Math.max(WINDOW_PAGES, pageCount * WINDOW_SHARE);
        return Math.abs(page - expected) <= slack;
    }

    /**
     * The words of a page the mark covers.
     *
     * @param key the quote's letters ({@link #key})
     * @return {first, last} word indexes, inclusive, or null when the mark is not on the page
     */
    public static int[] find(QuoteMatcher.Words page, String key, Mark.Anchor a) {
        final Match match = match(page, key, a);
        return match == null ? null : new int[]{match.first, match.last};
    }

    static Match match(QuoteMatcher.Words page, String key, Mark.Anchor a) {
        if (page == null || key == null || key.isEmpty() || a == null) {
            return null;
        }
        final String text = page.letters();
        final int n = text.length();
        final int k = key.length();
        if (n == 0) {
            return null;
        }
        final String pre = a.prefix;
        final String suf = a.suffix;

        // whole occurrences: the one with the most context agreeing
        int best = -1;
        int bestScore = -1;
        for (int at = text.indexOf(key); at >= 0; at = text.indexOf(key, at + 1)) {
            if (!page.startsWord(at) || !page.endsWord(at + k)) {
                continue;
            }
            final int before = Math.min(at, pre.length());
            final int after = Math.min(n - at - k, suf.length());
            if (!text.regionMatches(at - before, pre, pre.length() - before, before)
                    || !text.regionMatches(at + k, suf, 0, after)) {
                continue;
            }
            final int score = before + after;
            if (score > bestScore && k + score >= MIN_EVIDENCE) {
                best = at;
                bestScore = score;
            }
        }
        if (best >= 0) {
            return new Match(page.range(best, best + k), WHOLE);
        }

        // the start of the quote ends the page
        for (int h = Math.min(k - 1, n); h >= 1; h--) {
            final int at = n - h;
            if (!page.startsWord(at) || !text.regionMatches(at, key, 0, h)) {
                continue;
            }
            final int before = Math.min(at, pre.length());
            if (text.regionMatches(at - before, pre, pre.length() - before, before) && h + before >= MIN_EVIDENCE) {
                return new Match(page.range(at, n), HEAD);
            }
        }
        // the end of the quote starts the page
        for (int t = Math.min(k - 1, n); t >= 1; t--) {
            if (!page.endsWord(t) || !text.regionMatches(0, key, k - t, t)) {
                continue;
            }
            final int after = Math.min(n - t, suf.length());
            if (text.regionMatches(t, suf, 0, after) && t + after >= MIN_EVIDENCE) {
                return new Match(page.range(0, t), TAIL);
            }
        }
        // a page wholly inside a long quote
        if (n >= MIN_WHOLE_PAGE && n < k && key.contains(text)) {
            return new Match(page.range(0, n), INSIDE);
        }
        return null;
    }

    /**
     * The page of a layout where the mark starts, searched outward from where it should be; -1 when
     * it is on none of the pages near there.
     */
    public static int findPage(Mark.Anchor a, String key, int pageCount, Pages pages) {
        if (pageCount <= 0 || a == null) {
            return -1;
        }
        final int expected = expectedPage(a, pageCount);
        final int slack = (int) Math.ceil(Math.max(WINDOW_PAGES, pageCount * WINDOW_SHARE));
        for (int d = 0; d <= slack; d++) {
            for (int page : d == 0 ? new int[]{expected} : new int[]{expected - d, expected + d}) {
                if (page < 0 || page >= pageCount || !near(a, page, pageCount)) {
                    continue;
                }
                final Match match = match(pages.words(page), key, a);
                if (match != null && (match.how == WHOLE || match.how == HEAD)) {
                    return page;
                }
            }
        }
        return -1;
    }
}
