package com.foobnix.assist.edition;

import com.foobnix.assist.edition.EditionModel.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An edition cut into the pages of the page reader. A page holds a budget of text (characters,
 * with a fixed cost for every heading and block, since those take room too): little material
 * gives few pages. Every section starts a page; a chapter's heading never stands alone at the
 * foot of a page; a long passage runs on over the page break, cut at a paragraph's, a sentence's
 * or a word's end.
 */
public final class EditionPages {

    /** The budget of a page of the page reader. */
    public static final int PAGE_CHARS = 1100;
    /** What a chapter heading costs. */
    static final int HEADING_COST = 70;
    /** What a block costs besides its text (label, room around it). */
    static final int BLOCK_COST = 50;
    /** A piece of a passage shorter than this is not left alone on a page. */
    static final int MIN_PIECE = 160;

    /** A chapter's blocks on one page. */
    public static final class Chunk {
        /** The chapter's title, or empty for no heading. */
        public final String heading;
        /** The chapter started on an earlier page. */
        public final boolean continued;
        public final List<Block> blocks = new ArrayList<>();

        Chunk(String heading, boolean continued) {
            this.heading = heading;
            this.continued = continued;
        }
    }

    public static final class Page {
        /** From 1. */
        public final int number;
        /** The index of its section in {@link EditionModel#sections}. */
        public final int section;
        public final List<Chunk> chunks = new ArrayList<>();
        int used;

        Page(int number, int section) {
            this.number = number;
            this.section = section;
        }
    }

    public final List<Page> pages;
    /** Per section: its first page (from 1) and how many pages it has. */
    private final int[] first;
    private final int[] count;

    private EditionPages(List<Page> pages, int sections) {
        this.pages = Collections.unmodifiableList(pages);
        first = new int[sections];
        count = new int[sections];
        for (Page p : pages) {
            if (count[p.section] == 0) {
                first[p.section] = p.number;
            }
            count[p.section]++;
        }
    }

    public int size() {
        return pages.size();
    }

    /** The first page (from 1) of a section. */
    public int firstPage(int section) {
        return section >= 0 && section < first.length ? first[section] : 1;
    }

    /** The pages a section fills. */
    public int pageCount(int section) {
        return section >= 0 && section < count.length ? count[section] : 0;
    }

    /** The first page (from 1) holding a block of a kind, or 0. */
    public int firstPageOf(EditionModel.Kind kind) {
        for (Page p : pages) {
            for (Chunk c : p.chunks) {
                for (Block b : c.blocks) {
                    if (b.kind == kind) {
                        return p.number;
                    }
                }
            }
        }
        return 0;
    }

    public static EditionPages of(EditionModel model) {
        return of(model, PAGE_CHARS);
    }

    /** @param budget the characters a page holds */
    public static EditionPages of(EditionModel model, int budget) {
        final int room = Math.max(MIN_PIECE * 2, budget);
        final List<Page> pages = new ArrayList<>();
        for (int s = 0; s < model.sections.size(); s++) {
            Page page = new Page(pages.size() + 1, s);
            pages.add(page);
            for (EditionModel.Group g : model.sections.get(s).groups) {
                Chunk chunk = null;
                for (Block block : g.blocks) {
                    Block rest = block;
                    boolean firstPiece = true;
                    while (rest != null) {
                        final int head = chunk == null && !g.title.isEmpty() ? HEADING_COST : 0;
                        final int left = room - page.used - head - BLOCK_COST - rest.label.length()
                                - rest.title.length() - rest.caption.length();
                        Block piece = rest;
                        Block after = null;
                        if (rest.text.length() > left) {
                            final int at = left >= MIN_PIECE ? breakAt(rest.text, left) : 0;
                            if (at >= MIN_PIECE && rest.text.length() - at >= MIN_PIECE / 2) {
                                // it runs on over the page break
                                piece = new Block(rest.kind, rest.label, rest.title, rest.text.substring(0, at).trim(), "");
                                after = new Block(rest.kind, rest.label, "", rest.text.substring(at).trim(), rest.caption);
                            } else if (page.used > 0) {
                                // it starts the next page whole (or is cut there)
                                page = new Page(pages.size() + 1, s);
                                pages.add(page);
                                chunk = null;
                                continue;
                            }
                            // else: alone on a page and still too long for a clean cut: it stays whole
                        }
                        if (chunk == null) {
                            chunk = new Chunk(g.title, !firstPiece || g.blocks.indexOf(block) > 0);
                            page.chunks.add(chunk);
                            page.used += g.title.isEmpty() ? 0 : HEADING_COST;
                        }
                        chunk.blocks.add(piece);
                        page.used += BLOCK_COST + piece.length();
                        rest = after;
                        firstPiece = false;
                        if (after != null) {
                            page = new Page(pages.size() + 1, s);
                            pages.add(page);
                            chunk = null;
                        }
                    }
                }
            }
        }
        return new EditionPages(pages, model.sections.size());
    }

    /**
     * Where a text is cut so that at most {@code max} characters stay: after the last paragraph,
     * else after the last sentence, else at the last space; 0 when there is no place.
     */
    static int breakAt(String text, int max) {
        final int limit = Math.min(max, text.length());
        final int paragraph = text.lastIndexOf("\n\n", limit - 1);
        if (paragraph >= MIN_PIECE) {
            return paragraph + 2;
        }
        int sentence = -1;
        for (int i = limit - 1; i >= MIN_PIECE; i--) {
            final char c = text.charAt(i - 1);
            if ((c == '.' || c == '!' || c == '?' || c == '…' || c == '\n') && Character.isWhitespace(text.charAt(i))) {
                sentence = i + 1;
                break;
            }
        }
        if (sentence > 0) {
            return sentence;
        }
        final int space = text.lastIndexOf(' ', limit - 1);
        return space >= MIN_PIECE ? space + 1 : 0;
    }
}
