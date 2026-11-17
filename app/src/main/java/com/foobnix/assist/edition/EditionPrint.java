package com.foobnix.assist.edition;

import com.foobnix.assist.edition.EditionModel.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The printed edition (Export PDF), the part without Android: the page's measures in points, the
 * edition as a run of styled paragraphs ({@link #runs}: the same sections, chapters and blocks as
 * on screen), and the page breaks ({@link #layout}) once each paragraph's lines are counted. The
 * Android part ({@code EditionPdf}) only measures the paragraphs and draws the slices it is given.
 */
public final class EditionPrint {

    /** A5 in points (1/72 inch). */
    public static final int PAGE_WIDTH = 420;
    public static final int PAGE_HEIGHT = 595;
    public static final int MARGIN_X = 44;
    public static final int MARGIN_TOP = 60;
    public static final int MARGIN_BOTTOM = 46;
    /** The baseline of the running head (book title, "Your edition", page number). */
    public static final int HEAD_Y = 36;
    /** Room a coloured bar and its gap take at the left of a marked paragraph. */
    public static final int BAR_INDENT = 10;
    /** Lines kept together at the top and the foot of a page when a paragraph is cut. */
    static final int KEEP_LINES = 2;

    /** How a paragraph is set; sizes and line heights in points. */
    public enum Style {
        /** A section's title, on a new page. */
        SECTION(15f, 20f, 0f, 12f),
        /** A chapter's title. */
        CHAPTER(13f, 17f, 16f, 4f),
        /** A small capital label. */
        LABEL(7f, 10f, 9f, 3f),
        /** A title line inside a block. */
        TITLE(9.5f, 13f, 6f, 2f),
        /** The book's one line. */
        LINE(13f, 19f, 0f, 6f),
        /** Reading text: the reader's words. */
        BODY(10f, 15f, 0f, 4f),
        /** A question, in italics. */
        QUESTION(10.5f, 15.5f, 0f, 3f),
        /** Interface text: a fix, an experiment, a note. */
        PLAIN(9.5f, 14f, 0f, 4f),
        /** A small grey line. */
        SMALL(8f, 11.5f, 0f, 4f);

        public final float size;
        public final float lineHeight;
        /** Room above it (not at the top of a page) and below it. */
        public final float before;
        public final float after;

        Style(float size, float lineHeight, float before, float after) {
            this.size = size;
            this.lineHeight = lineHeight;
            this.before = before;
            this.after = after;
        }
    }

    /** The colour of a paragraph's label and bar. */
    public enum Tone {
        NONE, WARN, ACCENT
    }

    /** One paragraph of the printed edition. */
    public static final class Run {
        public final Style style;
        public final String text;
        public final Tone tone;
        /** A bar in the tone's colour runs down its left side. */
        public final boolean bar;
        /** It starts a new page. */
        public final boolean breakBefore;
        /** It never stands at the foot of a page without the start of the next paragraph. */
        public final boolean keepWithNext;

        Run(Style style, String text, Tone tone, boolean bar, boolean breakBefore, boolean keepWithNext) {
            this.style = style;
            this.text = text;
            this.tone = tone;
            this.bar = bar;
            this.breakBefore = breakBefore;
            this.keepWithNext = keepWithNext;
        }

        /** The width its text is set in, of the page's text width. */
        public float width(float textWidth) {
            return bar ? textWidth - BAR_INDENT : textWidth;
        }
    }

    /** A measured paragraph: what {@link #layout} needs of it. */
    public static final class Piece {
        public final int lines;
        public final float lineHeight;
        public final float before;
        public final float after;
        public final boolean breakBefore;
        public final boolean keepWithNext;

        public Piece(int lines, float lineHeight, float before, float after, boolean breakBefore, boolean keepWithNext) {
            this.lines = Math.max(0, lines);
            this.lineHeight = Math.max(1f, lineHeight);
            this.before = Math.max(0f, before);
            this.after = Math.max(0f, after);
            this.breakBefore = breakBefore;
            this.keepWithNext = keepWithNext;
        }

        /** A run with its counted lines. */
        public static Piece of(Run run, int lines) {
            return new Piece(lines, run.style.lineHeight, run.style.before, run.style.after, run.breakBefore,
                    run.keepWithNext);
        }
    }

    /** Lines {@code from} (inclusive) to {@code to} (exclusive) of a paragraph, {@code top} points down the text area. */
    public static final class Slice {
        public final int piece;
        public final int from;
        public final int to;
        public final float top;

        Slice(int piece, int from, int to, float top) {
            this.piece = piece;
            this.from = from;
            this.to = to;
            this.top = top;
        }
    }

    private EditionPrint() {
    }

    /** The width of the text area. */
    public static float textWidth() {
        return PAGE_WIDTH - 2f * MARGIN_X;
    }

    /** The height of the text area. */
    public static float textHeight() {
        return PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;
    }

    /** Whole lines of a height that fit a text area. */
    public static int linesPerPage(float height, float lineHeight) {
        return lineHeight <= 0 ? 0 : (int) Math.floor((height + 0.01f) / lineHeight);
    }

    /** The running head: "THE WEALTH OF NATIONS · YOUR EDITION". */
    public static String head(EditionModel m, EditionWords w) {
        final String title = m.bookTitle.replaceAll("\\s+", " ").trim();
        return (title.isEmpty() ? w.yourEdition : title + " · " + w.yourEdition).toUpperCase(java.util.Locale.ROOT);
    }

    /** The PDF's file name from the book's title: no characters a file system refuses, not too long. */
    public static String fileName(String bookTitle, String yourEdition) {
        String name = (bookTitle == null ? "" : bookTitle).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > 100) {
            name = name.substring(0, 100).trim();
        }
        return (name.isEmpty() ? yourEdition : name + " - " + yourEdition) + ".pdf";
    }

    // ------------------------------------------------------------------ the paragraphs

    /** The edition as paragraphs, in reading order: each section on a new page, its chapters, their blocks. */
    public static List<Run> runs(EditionModel m) {
        final List<Run> out = new ArrayList<>();
        for (EditionModel.Section s : m.sections) {
            out.add(new Run(Style.SECTION, s.title, Tone.NONE, false, true, true));
            for (EditionModel.Group g : s.groups) {
                if (!g.title.isEmpty()) {
                    out.add(new Run(Style.CHAPTER, g.title, Tone.NONE, false, false, true));
                }
                String label = "";
                for (Block b : g.blocks) {
                    block(out, b, !b.label.isEmpty() && !b.label.equals(label));
                    label = b.label;
                }
            }
        }
        return out;
    }

    private static void block(List<Run> out, Block b, boolean withLabel) {
        final Tone tone = b.kind == EditionModel.Kind.WRONG ? Tone.WARN
                : b.kind == EditionModel.Kind.TRIED ? Tone.ACCENT : Tone.NONE;
        final boolean bar = tone != Tone.NONE;
        if (withLabel) {
            out.add(new Run(Style.LABEL, b.label.toUpperCase(java.util.Locale.ROOT), tone, false, false, true));
        }
        if (!b.title.isEmpty()) {
            out.add(new Run(Style.TITLE, b.title, Tone.NONE, false, false, !b.text.isEmpty()));
        }
        if (!b.text.isEmpty()) {
            final Style style;
            switch (b.kind) {
                case LINE:
                    style = Style.LINE;
                    break;
                case PARAGRAPH:
                case PART:
                case CHAPTER_LINE:
                case WORDS:
                    style = Style.BODY;
                    break;
                case OPEN:
                case GUESS:
                    style = Style.QUESTION;
                    break;
                default:
                    style = Style.PLAIN;
                    break;
            }
            out.add(new Run(style, b.text, tone, bar, false, false));
        }
        if (!b.caption.isEmpty()) {
            out.add(new Run(Style.SMALL, b.caption, Tone.NONE, false, false, false));
        }
    }

    // ------------------------------------------------------------------ the page breaks

    /**
     * Lays measured paragraphs on pages of {@code height} points. A paragraph that does not fit
     * runs on over the break, with at least {@link #KEEP_LINES} lines on each side (a shorter
     * one moves whole); a paragraph that keeps with the next moves with it; room above a
     * paragraph is dropped at the top of a page.
     *
     * @return per page, its slices from top to bottom
     */
    public static List<List<Slice>> layout(List<Piece> pieces, float height) {
        final List<List<Slice>> pages = new ArrayList<>();
        List<Slice> page = new ArrayList<>();
        pages.add(page);
        float y = 0;
        for (int i = 0; i < pieces.size(); i++) {
            final Piece p = pieces.get(i);
            if (p.lines == 0) {
                continue;
            }
            if (!page.isEmpty() && (p.breakBefore || y + p.before + needWithNext(pieces, i) > height + 0.01f)) {
                page = new ArrayList<>();
                pages.add(page);
                y = 0;
            }
            int from = 0;
            while (from < p.lines) {
                final float gap = page.isEmpty() || from > 0 ? 0 : p.before;
                final int left = p.lines - from;
                int fit = linesPerPage(height - y - gap, p.lineHeight);
                if (fit >= left) {
                    fit = left;
                } else if (left - fit < KEEP_LINES) {
                    // never a lone last line on the next page
                    fit = left - KEEP_LINES;
                }
                if (fit < Math.min(KEEP_LINES, left)) {
                    if (page.isEmpty()) {
                        // a page shorter than two lines: one line at least, so the layout always ends
                        fit = Math.max(1, Math.min(left, linesPerPage(height, p.lineHeight)));
                    } else {
                        page = new ArrayList<>();
                        pages.add(page);
                        y = 0;
                        continue;
                    }
                }
                page.add(new Slice(i, from, from + fit, y + gap));
                y += gap + fit * p.lineHeight;
                from += fit;
                if (from < p.lines) {
                    page = new ArrayList<>();
                    pages.add(page);
                    y = 0;
                }
            }
            y += p.after;
        }
        if (page.isEmpty() && pages.size() > 1) {
            pages.remove(pages.size() - 1);
        }
        return pages;
    }

    /**
     * The room paragraph {@code i} needs to start on a page: its own first lines, and when it
     * keeps with the next, the whole of it and the start of the paragraphs it is tied to.
     */
    private static float needWithNext(List<Piece> pieces, int i) {
        final Piece p = pieces.get(i);
        if (!p.keepWithNext) {
            return Math.min(p.lines, KEEP_LINES) * p.lineHeight;
        }
        float need = p.lines * p.lineHeight + p.after;
        for (int n = i + 1; n < pieces.size(); n++) {
            final Piece next = pieces.get(n);
            if (next.lines == 0) {
                continue;
            }
            if (next.breakBefore) {
                break;
            }
            need += next.before;
            if (!next.keepWithNext) {
                return need + Math.min(next.lines, KEEP_LINES) * next.lineHeight;
            }
            need += next.lines * next.lineHeight + next.after;
        }
        return need;
    }
}
