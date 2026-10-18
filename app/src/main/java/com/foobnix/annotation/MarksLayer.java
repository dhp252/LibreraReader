package com.foobnix.annotation;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.LOG;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The marks of the open book for the reader views to draw, as the AI highlights are drawn
 * ({@link AiHighlights}): each page works out its shapes once per change (bands of text marks,
 * pen strokes) and a frame only paints them. Marks of a fixed layout are drawn where they were
 * made; marks of a reflowing book are found by their words on the page ({@link TextAnchor}).
 */
public class MarksLayer {

    private static final int HIGHLIGHT_ALPHA = 90;
    private static final int CACHED_PAGES = 12;

    private static final MarksLayer instance = new MarksLayer();

    /** A line of a text mark on a page, or a piece of a pen stroke; page-relative (0..1). */
    public static final class Shape {
        public final Mark mark;
        /** A line of a text mark; null for a pen stroke. */
        public final RectF band;
        /** A piece of a pen stroke; null for a line. */
        public final Mark.Ink ink;
        /** The mark's last line on this page: its note sign sits at the end. */
        public final boolean last;

        Shape(Mark mark, RectF band, Mark.Ink ink, boolean last) {
            this.mark = mark;
            this.band = band;
            this.ink = ink;
            this.last = last;
        }
    }

    private static final class Cached {
        final TextWord[][] text;
        final int version;
        final int pageCount;
        final List<Shape> shapes;

        Cached(TextWord[][] text, int version, int pageCount, List<Shape> shapes) {
            this.text = text;
            this.version = version;
            this.pageCount = pageCount;
            this.shapes = shapes;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF scratch = new RectF();
    private final Map<Integer, Cached> cache = new LinkedHashMap<Integer, Cached>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Cached> eldest) {
            return size() > CACHED_PAGES;
        }
    };

    private Activity owner;
    private String book;
    private int pageCount;
    private Map<String, Mark> marks = Collections.emptyMap();
    /** The quote letters of each text mark of a reflowing book, by mark id. */
    private Map<String, String> keys = Collections.emptyMap();
    private int version;

    public static MarksLayer get() {
        return instance;
    }

    /** File name of a book: what its marks are stored under (as its AI data). */
    public static String bookName(String path) {
        return AiHighlights.bookName(path);
    }

    /**
     * A book was opened: its marks are read in the background and drawn when ready.
     *
     * @param owner     the reader screen; only it closes the book again
     * @param pageCount the document pages of the layout shown (where marks of a reflowing book are looked for)
     */
    public void open(Activity owner, String bookPath, int pageCount) {
        final String name = bookName(bookPath);
        synchronized (this) {
            this.owner = owner;
            this.pageCount = pageCount;
            book = name;
            marks = Collections.emptyMap();
            keys = Collections.emptyMap();
            version++;
            cache.clear();
        }
        reload(name);
    }

    /** The reader screen is gone: its book is no longer drawn. */
    public void close(Activity owner) {
        synchronized (this) {
            if (this.owner != owner) {
                return;
            }
            this.owner = null;
            book = null;
            marks = Collections.emptyMap();
            keys = Collections.emptyMap();
            version++;
            cache.clear();
        }
    }

    /** Reads the stored marks of the book again, on a thread of its own. */
    public void reload(final String bookName) {
        new Thread(() -> {
            try {
                set(bookName, MarksStore.get().load(bookName));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T marks").start();
    }

    /** The marks of a book, as just read or written. */
    public void set(String bookName, Map<String, Mark> loaded) {
        final Map<String, String> found = new HashMap<>();
        for (Mark mark : loaded.values()) {
            if (mark.anchor != null) {
                found.put(mark.id, TextAnchor.key(mark.quote));
            }
        }
        synchronized (this) {
            if (bookName == null || !bookName.equals(book)) {
                return;
            }
            marks = new LinkedHashMap<>(loaded);
            keys = found;
            version++;
            cache.clear();
        }
        EventBus.getDefault().post(new MessageMarks());
    }

    public synchronized String getBook() {
        return book;
    }

    /** The document pages of the layout shown. */
    public synchronized int pageCount() {
        return pageCount;
    }

    /** What to draw on a page of the layout shown. */
    public synchronized List<Shape> shapesFor(int docPage, TextWord[][] pageText) {
        return shapesFor(docPage, pageText, pageCount);
    }

    public synchronized int count() {
        return marks.size();
    }

    public synchronized Mark mark(String id) {
        return marks.get(id);
    }

    /** The marks of the open book, oldest first. */
    public synchronized List<Mark> marks() {
        return new ArrayList<>(marks.values());
    }

    /**
     * What to draw on a page.
     *
     * @param docPage   0-based document page
     * @param pageText  the page's words (needed for marks of a reflowing book)
     * @param pageCount the pages of the layout shown
     */
    public synchronized List<Shape> shapesFor(int docPage, TextWord[][] pageText, int pageCount) {
        if (marks.isEmpty()) {
            return Collections.emptyList();
        }
        final Cached cached = cache.get(docPage);
        if (cached != null && cached.text == pageText && cached.version == version && cached.pageCount == pageCount) {
            return cached.shapes;
        }
        List<Shape> shapes = Collections.emptyList();
        try {
            shapes = shapes(docPage, pageText, pageCount);
        } catch (Exception e) {
            LOG.e(e);
        }
        cache.put(docPage, new Cached(pageText, version, pageCount, shapes));
        return shapes;
    }

    private List<Shape> shapes(int docPage, TextWord[][] pageText, int pageCount) {
        final List<Shape> shapes = new ArrayList<>();
        List<TextWord> words = null;
        QuoteMatcher.Words letters = null;
        for (Mark mark : marks.values()) {
            for (Mark.Part part : mark.parts) {
                if (part.page != docPage) {
                    continue;
                }
                for (int i = 0; i + 3 < part.rects.length; i += 4) {
                    final RectF band = new RectF(part.rects[i], part.rects[i + 1], part.rects[i + 2], part.rects[i + 3]);
                    shapes.add(new Shape(mark, band, null, i + 4 >= part.rects.length));
                }
            }
            for (Mark.Ink run : mark.ink) {
                if (run.page == docPage) {
                    shapes.add(new Shape(mark, null, run, false));
                }
            }
            if (mark.anchor == null || pageText == null || !TextAnchor.near(mark.anchor, docPage, pageCount)) {
                continue;
            }
            if (words == null) {
                words = TTSHighlight.words(pageText);
                final List<String> strings = new ArrayList<>(words.size());
                for (TextWord word : words) {
                    strings.add(word.getWord());
                }
                letters = new QuoteMatcher.Words(strings);
            }
            final int[] range = TextAnchor.find(letters, keys.get(mark.id), mark.anchor);
            if (range == null) {
                continue;
            }
            final List<RectF> bands = TTSHighlight.toBands(words.subList(range[0], Math.min(range[1] + 1, words.size())));
            for (int i = 0; i < bands.size(); i++) {
                shapes.add(new Shape(mark, bands.get(i), null, i == bands.size() - 1));
            }
        }
        return shapes;
    }

    /**
     * The mark under a page-relative point, the newest when marks overlap; null when none.
     *
     * @param slopX how far off a tap may be, as a part of the page width
     * @param slopY the same as a part of the page height
     */
    public synchronized Mark hit(int docPage, TextWord[][] pageText, int pageCount, float x, float y, float slopX, float slopY) {
        Mark found = null;
        for (Shape shape : shapesFor(docPage, pageText, pageCount)) {
            if (hits(shape, x, y, slopX, slopY) && (found == null || shape.mark.created >= found.created)) {
                found = shape.mark;
            }
        }
        return found;
    }

    static boolean hits(Shape shape, float x, float y, float slopX, float slopY) {
        if (shape.band != null) {
            final RectF b = shape.band;
            // the note sign stands just past the end of the last line
            final float right = b.right + (shape.last && shape.mark.hasNote() ? b.height() : 0);
            return x >= b.left - slopX && x <= right + slopX && y >= b.top - slopY && y <= b.bottom + slopY;
        }
        final float[] p = shape.ink.points;
        final float reach = 1 + shape.ink.width / (2 * Math.max(slopX, 0.0001f));
        for (int i = 0; i + 3 < p.length; i += 2) {
            if (segmentDistance(x / slopX, y / slopY, p[i] / slopX, p[i + 1] / slopY, p[i + 2] / slopX, p[i + 3] / slopY) <= reach) {
                return true;
            }
        }
        return p.length == 2 && Math.hypot((x - p[0]) / slopX, (y - p[1]) / slopY) <= reach;
    }

    private static double segmentDistance(float px, float py, float ax, float ay, float bx, float by) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float length = dx * dx + dy * dy;
        float t = length == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / length;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /**
     * Paints shapes of a page whose page-relative point (x, y) is at ({@code x0 + x * sx},
     * {@code y0 + y * sy}) on the canvas.
     */
    public void draw(Canvas canvas, List<Shape> shapes, float x0, float y0, float sx, float sy) {
        if (shapes.isEmpty()) {
            return;
        }
        synchronized (paint) {
            // fills under lines, pen strokes and note signs
            for (Shape shape : shapes) {
                if (shape.band != null && Mark.HIGHLIGHT.equals(shape.mark.kind)) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color(shape.mark));
                    paint.setAlpha(HIGHLIGHT_ALPHA);
                    canvas.drawRect(map(shape.band, x0, y0, sx, sy), paint);
                }
            }
            for (Shape shape : shapes) {
                if (shape.band != null && !Mark.HIGHLIGHT.equals(shape.mark.kind)) {
                    final RectF r = map(shape.band, x0, y0, sx, sy);
                    final float thick = Math.max(1.5f, r.height() * 0.09f);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color(shape.mark));
                    if (Mark.STRIKE.equals(shape.mark.kind)) {
                        canvas.drawRect(r.left, r.centerY() - thick / 2, r.right, r.centerY() + thick / 2, paint);
                    } else {
                        canvas.drawRect(r.left, r.bottom - thick, r.right, r.bottom, paint);
                    }
                } else if (shape.ink != null) {
                    drawInk(canvas, shape, x0, y0, sx, sy);
                }
            }
            for (Shape shape : shapes) {
                if (shape.band != null && shape.last && shape.mark.hasNote()) {
                    drawNoteSign(canvas, map(shape.band, x0, y0, sx, sy), color(shape.mark));
                }
            }
        }
    }

    private RectF map(RectF band, float x0, float y0, float sx, float sy) {
        scratch.set(x0 + band.left * sx, y0 + band.top * sy, x0 + band.right * sx, y0 + band.bottom * sy);
        return scratch;
    }

    private void drawInk(Canvas canvas, Shape shape, float x0, float y0, float sx, float sy) {
        final float[] p = shape.ink.points;
        if (p.length < 2) {
            return;
        }
        path.reset();
        path.moveTo(x0 + p[0] * sx, y0 + p[1] * sy);
        for (int i = 2; i + 1 < p.length; i += 2) {
            path.lineTo(x0 + p[i] * sx, y0 + p[i + 1] * sy);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1f, shape.ink.width * Math.abs(sx)));
        paint.setColor(color(shape.mark));
        canvas.drawPath(path, paint);
    }

    /** A small round sign in the mark's color with a white dot, standing at the line's top right. */
    private void drawNoteSign(Canvas canvas, RectF line, int color) {
        final float radius = Math.max(4f, line.height() * 0.32f);
        final float cx = line.right + radius * 0.9f;
        final float cy = line.top + radius * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius * 0.38f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, radius * 0.18f));
        paint.setColor(0xff404040);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    /** The mark's color, opaque. */
    public static int color(Mark mark) {
        try {
            return Color.parseColor(mark.color) | 0xff000000;
        } catch (Exception e) {
            return 0xffffd400;
        }
    }
}
