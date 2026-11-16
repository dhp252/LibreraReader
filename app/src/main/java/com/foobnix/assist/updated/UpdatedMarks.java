package com.foobnix.assist.updated;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.foobnix.ai.MessageAiHighlights;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.model.AppState;
import com.foobnix.tts.TTSHighlight;

import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The margin marks of "The book, updated" (canvas UpdatedMargin): a small round mark in the page's
 * right margin beside the line where a note's sentence starts, in the accent, orange for a
 * doubtful story. Both reader views ask while drawing a page; the sentences are found by the
 * page's words, so the marks survive a new layout. Nothing when Assist.UPDATED is off.
 */
public final class UpdatedMarks {

    private static final class Entry {
        final String key;
        final boolean doubtful;

        Entry(String key, boolean doubtful) {
            this.key = key;
            this.doubtful = doubtful;
        }
    }

    /** A mark in page-relative units (0..1, like {@link TextWord}). */
    private static final class Mark {
        /** The middle and the height of the line the sentence starts on. */
        final float y;
        final float lineHeight;
        /** Where the page's text ends on the right. */
        final float textRight;
        final boolean doubtful;

        Mark(float y, float lineHeight, float textRight, boolean doubtful) {
            this.y = y;
            this.lineHeight = lineHeight;
            this.textRight = textRight;
            this.doubtful = doubtful;
        }
    }

    private static final class Cached {
        final TextWord[][] text;
        final int version;
        final List<Mark> marks;

        Cached(TextWord[][] text, int version, List<Mark> marks) {
            this.text = text;
            this.version = version;
            this.marks = marks;
        }
    }

    private static volatile List<Entry> entries = Collections.emptyList();
    private static volatile int version;
    private static final Map<Integer, Cached> cache = new HashMap<>();
    private static Paint fill;
    private static Paint stroke;

    private UpdatedMarks() {
    }

    /** The notes of the open book whose sentences get a mark; the reader views draw again. */
    static void set(List<UpdatedNote> notes) {
        final List<Entry> list = new ArrayList<>();
        if (notes != null) {
            for (UpdatedNote n : notes) {
                final String key = QuoteMatcher.key(n.quote);
                if (key != null) {
                    list.add(new Entry(key, n.kind == UpdatedKind.DOUBTFUL));
                }
            }
        }
        final boolean changed = !(list.isEmpty() && entries.isEmpty());
        entries = list;
        synchronized (cache) {
            version++;
            cache.clear();
        }
        if (changed) {
            // the message both reader views redraw their marks on
            EventBus.getDefault().post(new MessageAiHighlights());
        }
    }

    /**
     * Draws the marks of a page.
     *
     * @param unit where the whole page is on the canvas (the unit square of its words)
     */
    public static void draw(Canvas canvas, int docPage, TextWord[][] pageText, RectF unit) {
        if (entries.isEmpty() || pageText == null || unit == null || unit.width() <= 0 || !Assist.UPDATED.isOn()) {
            return;
        }
        try {
            final List<Mark> marks = marks(docPage, pageText);
            if (marks.isEmpty()) {
                return;
            }
            final Rect clip = new Rect();
            final float edge = canvas.getClipBounds(clip) ? Math.min(unit.right, clip.right) : unit.right;
            final boolean day = AppState.get().isDayNotInvert;
            paint(canvas, marks, unit, edge, day);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** One painter at a time: the two reader views may draw from different threads. */
    private static synchronized void paint(Canvas canvas, List<Mark> marks, RectF unit, float edge, boolean day) {
        if (fill == null) {
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
        }
        for (Mark m : marks) {
            final float line = m.lineHeight * unit.height();
            final float r = Math.max(CanvasLook.dp(4), Math.min(CanvasLook.dp(8), line * 0.32f));
            final float gap = CanvasLook.dp(3);
            final float x = Math.min(unit.left + m.textRight * unit.width() + gap + r, edge - r - CanvasLook.dp(1));
            final float y = unit.top + m.y * unit.height();
            // the page's own day or night, not the interface's
            final int ink = m.doubtful ? (day ? CanvasLook.SENSOR : CanvasLook.SENSOR_N)
                    : (day ? CanvasLook.ACCENT : CanvasLook.ACCENT_N);
            final int tint = m.doubtful ? (day ? UpdatedParts.WARN_TINT : CanvasLook.SENSOR_TINT_N)
                    : (day ? CanvasLook.ACCENT_TINT : CanvasLook.ACCENT_TINT_N);
            fill.setColor(tint);
            canvas.drawCircle(x, y, r, fill);
            stroke.setColor(ink);
            stroke.setStrokeWidth(Math.max(1f, r * 0.22f));
            canvas.drawCircle(x, y, r, stroke);
            fill.setColor(ink);
            canvas.drawCircle(x, y, r * 0.36f, fill);
        }
    }

    private static List<Mark> marks(int docPage, TextWord[][] pageText) {
        final int v = version;
        synchronized (cache) {
            final Cached c = cache.get(docPage);
            if (c != null && c.text == pageText && c.version == v) {
                return c.marks;
            }
        }
        final List<Mark> marks = find(pageText, entries);
        synchronized (cache) {
            if (cache.size() > 12) {
                cache.clear();
            }
            cache.put(docPage, new Cached(pageText, v, marks));
        }
        return marks;
    }

    private static List<Mark> find(TextWord[][] pageText, List<Entry> list) {
        final List<TextWord> words = TTSHighlight.words(pageText);
        if (words.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> strings = new ArrayList<>(words.size());
        float textRight = 0;
        for (TextWord w : words) {
            strings.add(w.getWord());
            if (w.width() > 0 && w.right <= 1f) {
                textRight = Math.max(textRight, w.right);
            }
        }
        final QuoteMatcher.Words page = new QuoteMatcher.Words(strings);
        final List<Mark> out = new ArrayList<>();
        for (Entry e : list) {
            for (int[] range : QuoteMatcher.onPage(page, Collections.singletonList(e.key))) {
                final int from = Math.max(0, range[0]);
                final int to = Math.min(words.size(), range[1] + 1);
                if (from >= to) {
                    continue;
                }
                final List<RectF> bands = TTSHighlight.toBands(words.subList(from, to));
                if (bands.isEmpty()) {
                    continue;
                }
                final RectF first = bands.get(0);
                float y = first.centerY();
                // two notes starting on one line: the second sits a line lower
                for (Mark m : out) {
                    if (Math.abs(m.y - y) < first.height() * 0.5f) {
                        y += first.height();
                    }
                }
                out.add(new Mark(y, first.height(), textRight, e.doubtful));
                // one mark per sentence on a page
                break;
            }
        }
        return out;
    }
}
