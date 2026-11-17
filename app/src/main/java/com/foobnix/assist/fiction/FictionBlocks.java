package com.foobnix.assist.fiction;

import com.foobnix.ai.BookChapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The text of a spoiler-safe request, in order: the pages of a {@link FictionShield.Window} cut
 * along the chapters and put in blocks of at most a character cap. A text under the cap is one
 * block (one request); a longer one is several, sent one after the other, each with what the
 * earlier ones gave. A chapter longer than the cap is cut between pages.
 * <p>
 * Blocks are only made from a window, and the text of a block only from pages inside it, so
 * nothing after the reader's page can get into a request.
 */
public final class FictionBlocks {

    private FictionBlocks() {
    }

    /** Pages {@code from} (inclusive) to {@code to} (exclusive) of one chapter. */
    public static final class Slice {
        /** Index of the chapter in {@link FictionChapters}. */
        public final int chapter;
        public final int from;
        public final int to;
        /** It starts inside the chapter (the chapter began in an earlier block, or before the window). */
        public final boolean continued;

        Slice(int chapter, int from, int to, boolean continued) {
            this.chapter = chapter;
            this.from = from;
            this.to = to;
            this.continued = continued;
        }
    }

    /** The text of one request. */
    public static final class Block {
        public final List<Slice> slices;

        Block(List<Slice> slices) {
            this.slices = Collections.unmodifiableList(slices);
        }

        public int from() {
            return slices.get(0).from;
        }

        /** The first page after the block. */
        public int to() {
            return slices.get(slices.size() - 1).to;
        }

        /** Index of its first chapter. */
        public int firstChapter() {
            return slices.get(0).chapter;
        }

        /** Index of its last chapter. */
        public int lastChapter() {
            return slices.get(slices.size() - 1).chapter;
        }
    }

    /**
     * The window's pages as blocks, in page order. Chapters without text are left out; an empty
     * list means there is nothing to send.
     *
     * @param lengths characters of text on each page, by page (only pages of the window are read)
     * @param cap     characters of book text one request may hold
     */
    public static List<Block> blocks(FictionChapters chapters, FictionShield.Window window, int[] lengths, int cap) {
        final int max = Math.max(1, cap);
        final List<Slice> pieces = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            final BookChapters.Range r = chapters.get(i);
            final int from = Math.max(r.from, window.from);
            final int to = Math.min(r.to, window.end);
            if (from >= to) {
                continue;
            }
            if (length(lengths, from, to) <= max) {
                pieces.add(new Slice(i, from, to, from > r.from));
                continue;
            }
            // a chapter longer than a request: cut between pages
            int start = from;
            long piece = 0;
            for (int p = from; p < to; p++) {
                final int chars = p < lengths.length ? lengths[p] : 0;
                if (p > start && piece + chars > max) {
                    pieces.add(new Slice(i, start, p, start > r.from));
                    start = p;
                    piece = 0;
                }
                piece += chars;
            }
            pieces.add(new Slice(i, start, to, start > r.from));
        }
        final List<Block> blocks = new ArrayList<>();
        List<Slice> current = new ArrayList<>();
        long currentLength = 0;
        for (Slice s : pieces) {
            final long length = length(lengths, s.from, s.to);
            if (length == 0) {
                continue;
            }
            if (!current.isEmpty() && currentLength + length > max) {
                blocks.add(new Block(current));
                current = new ArrayList<>();
                currentLength = 0;
            }
            current.add(s);
            currentLength += length;
        }
        if (!current.isEmpty()) {
            blocks.add(new Block(current));
        }
        return blocks;
    }

    /**
     * A block as the model gets it: each chapter under a header with its number ({@link
     * FictionChapters#n}) and title, its pages in order. Pages outside the window are never
     * taken, whatever {@code plain} holds.
     *
     * @param plain the text of each page, by page
     */
    public static String text(FictionChapters chapters, FictionShield.Window window, Block block, String[] plain) {
        final StringBuilder out = new StringBuilder();
        for (Slice s : block.slices) {
            final BookChapters.Range r = chapters.get(s.chapter);
            out.append("<chapter n=\"").append(FictionChapters.n(s.chapter)).append('"');
            if (r.title != null && !r.title.trim().isEmpty()) {
                out.append(" title=\"").append(attr(r.title)).append('"');
            }
            if (s.continued) {
                out.append(" continued=\"yes\"");
            }
            out.append(">\n");
            boolean first = true;
            for (int p = s.from; p < s.to; p++) {
                if (!window.holds(p) || p >= plain.length || plain[p] == null || plain[p].isEmpty()) {
                    continue;
                }
                if (!first) {
                    out.append("\n\n");
                }
                out.append(plain[p]);
                first = false;
            }
            out.append("\n</chapter>\n\n");
        }
        return out.toString();
    }

    static long length(int[] lengths, int from, int to) {
        long sum = 0;
        for (int p = Math.max(0, from); p < to && p < lengths.length; p++) {
            sum += lengths[p];
        }
        return sum;
    }

    private static String attr(String s) {
        return s.replace('"', '\'').replace('\n', ' ').replace('<', '(').replace('>', ')').trim();
    }
}
