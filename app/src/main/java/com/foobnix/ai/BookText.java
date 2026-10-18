package com.foobnix.ai;

import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.droids.mupdf.codec.MuPdfDocument;
import org.ebookdroid.droids.mupdf.codec.MuPdfLinks;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * The text of the open book for AI requests. Reads a copy of the document of its own, laid out
 * like the reader (TTS reads the same way), so page numbers match the reader's and the reader's
 * own document is never touched from another thread.
 */
public class BookText implements Closeable {

    private final CodecDocument doc;
    private final int pageCount;

    private BookText(CodecDocument doc, int pageCount) {
        this.doc = doc;
        this.pageCount = pageCount;
    }

    /**
     * @param width    the reader's page size: EPUB, FB2... are laid out for it
     * @param fontSize the reader's font size, for the same reason
     * @return null when the book cannot be opened
     */
    public static BookText open(String path, int width, int height, int fontSize) {
        final CodecDocument doc = ImageExtractor.singleCodecContext(path, "");
        if (doc == null) {
            return null;
        }
        try {
            return new BookText(doc, doc.getPageCount(width, height, fontSize));
        } catch (Exception e) {
            LOG.e(e);
            doc.recycle();
            return null;
        }
    }

    public int pageCount() {
        return pageCount;
    }

    /** The table of contents with 0-based pages, as the reader's TOC panel resolves them. */
    public List<BookChapters.Entry> toc() {
        final List<BookChapters.Entry> entries = new ArrayList<>();
        try {
            final List<OutlineLink> outline = doc.getOutline();
            if (outline == null) {
                return entries;
            }
            for (OutlineLink link : outline) {
                if (TxtUtils.isEmpty(link.getTitle())) {
                    continue;
                }
                int page = -1;
                final String target = link.getLink();
                if (target != null && target.startsWith("#") && !target.startsWith("#0")) {
                    try {
                        page = Integer.parseInt(target.substring(1).replace(" ", "")) - 1;
                    } catch (NumberFormatException e) {
                        page = -1;
                    }
                } else if (doc instanceof MuPdfDocument) {
                    page = MuPdfLinks.getLinkPageWrapper(link.docHandle, link.linkUri);
                }
                entries.add(new BookChapters.Entry(link.getTitle().trim(), link.getLevel(), page));
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return entries;
    }

    /** The words of a page by line, with page-unit rectangles ({@link TextWord#getOriginal}). */
    public TextWord[][] text(int page) {
        if (page < 0 || page >= pageCount) {
            return new TextWord[0][0];
        }
        CodecPage codecPage = null;
        try {
            codecPage = doc.getPage(page);
            final TextWord[][] text = codecPage == null ? null : codecPage.getText();
            return text == null ? new TextWord[0][0] : text;
        } catch (Exception e) {
            LOG.e(e);
            return new TextWord[0][0];
        } finally {
            if (codecPage != null) {
                codecPage.recycle();
            }
        }
    }

    /** The annotations inside a PDF page, how they look, and the page's words (F-008 export). */
    public static final class PageAnnotations {
        public final List<org.ebookdroid.core.codec.Annotation> annotations;
        public final List<org.ebookdroid.core.codec.AnnotationLook> looks;
        public final TextWord[][] text;

        PageAnnotations(List<org.ebookdroid.core.codec.Annotation> annotations,
                        List<org.ebookdroid.core.codec.AnnotationLook> looks, TextWord[][] text) {
            this.annotations = annotations;
            this.looks = looks;
            this.text = text;
        }
    }

    /** The annotations inside a page (PDF), with their colors and lines; empty for a page without any. */
    public PageAnnotations annotations(int page) {
        final List<org.ebookdroid.core.codec.Annotation> none = new ArrayList<>();
        if (page < 0 || page >= pageCount) {
            return new PageAnnotations(none, new ArrayList<org.ebookdroid.core.codec.AnnotationLook>(), new TextWord[0][0]);
        }
        CodecPage codecPage = null;
        try {
            codecPage = doc.getPage(page);
            final List<org.ebookdroid.core.codec.Annotation> annotations = codecPage == null ? null : codecPage.getAnnotations();
            if (annotations == null || annotations.isEmpty()) {
                return new PageAnnotations(none, new ArrayList<org.ebookdroid.core.codec.AnnotationLook>(), new TextWord[0][0]);
            }
            final TextWord[][] text = codecPage.getText();
            return new PageAnnotations(annotations, codecPage.getAnnotationLooks(), text == null ? new TextWord[0][0] : text);
        } catch (Exception e) {
            LOG.e(e);
            return new PageAnnotations(none, new ArrayList<org.ebookdroid.core.codec.AnnotationLook>(), new TextWord[0][0]);
        } finally {
            if (codecPage != null) {
                codecPage.recycle();
            }
        }
    }

    /** The words of a page in reading order, as the reader views mark them. */
    public static List<String> words(TextWord[][] text) {
        final List<TextWord> words = TTSHighlight.words(text);
        final List<String> strings = new ArrayList<>(words.size());
        for (TextWord word : words) {
            strings.add(word.getWord());
        }
        return strings;
    }

    /**
     * The page as plain text for the model: words of a line joined, a word cut at a line end put
     * back together, a new paragraph where lines are further apart than usual.
     */
    public static String plain(TextWord[][] text) {
        final StringBuilder out = new StringBuilder();
        final List<String> run = new ArrayList<>();
        RectF previous = null;
        for (TextWord[] line : text) {
            if (line == null || line.length == 0) {
                continue;
            }
            final RectF bounds = new RectF(line[0]);
            for (TextWord word : line) {
                if (word != null) {
                    bounds.union(word);
                }
            }
            if (previous != null && bounds.top - previous.bottom > 0.8f * Math.max(previous.height(), bounds.height())) {
                out.append(QuoteMatcher.join(run)).append("\n\n");
                run.clear();
            }
            for (TextWord word : line) {
                if (word != null) {
                    run.add(word.getWord());
                }
            }
            previous = bounds;
        }
        out.append(QuoteMatcher.join(run));
        return out.toString().trim();
    }

    /** Lines of the given words in page units, for a PDF highlight annotation. */
    public static List<RectF> lines(List<TextWord> words) {
        final List<RectF> lines = new ArrayList<>();
        RectF line = null;
        for (TextWord word : words) {
            final RectF rect = word.getOriginal();
            if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
                continue;
            }
            if (line != null && sameLine(line, rect)) {
                line.union(rect);
            } else {
                line = new RectF(rect);
                lines.add(line);
            }
        }
        return lines;
    }

    private static boolean sameLine(RectF a, RectF b) {
        final float overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return overlap > 0.5f * Math.min(a.height(), b.height());
    }

    @Override
    public void close() {
        try {
            doc.recycle();
        } catch (Exception e) {
            LOG.e(e);
        }
    }
}
